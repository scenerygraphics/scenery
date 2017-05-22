#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(set = 5, binding = 0) uniform sampler2D hdrColor;
layout(set = 5, binding = 1) uniform sampler2D depth;

//layout(set = 1, binding = 0, std140) uniform ShaderParameters {
//	float Gamma;
//	float Exposure;
//} hdrParams;

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 6;
const int MAX_NUM_LIGHTS = 128;

struct Light {
	float Linear;
	float Quadratic;
	float Intensity;
	vec4 Position;
  	vec4 Color;
};

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Shininess;
};

layout(location = 0) in VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
} VertexIn;


layout(binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 ViewMatrix;
	mat4 NormalMatrix;
	mat4 ProjectionMatrix;
	vec3 CamPosition;
	int isBillboard;
} ubo;

layout(binding = 1) uniform MaterialProperties {
    MaterialInfo Material;
    int materialType;
};

layout(set = 1, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];
layout(set = 1, binding = 1) uniform sampler3D VolumeTextures;

layout(set = 3, binding = 0, std140) uniform LightParameters {
    int numLights;
	Light lights[MAX_NUM_LIGHTS];
};

layout(location = 0) out vec4 FragColor;

float PI_r = 0.3183098;

struct Ray {
    vec3 Origin;
    vec3 Dir;
};

struct AABB {
    vec3 Min;
    vec3 Max;
};

bool IntersectBox(Ray r, AABB aabb, out float t0, out float t1)
{
    vec3 invR = 1.0 / r.Dir;
    vec3 tbot = invR * (aabb.Min-r.Origin);
    vec3 ttop = invR * (aabb.Max-r.Origin);
    vec3 tmin = min(ttop, tbot);
    vec3 tmax = max(ttop, tbot);
    vec2 t = max(tmin.xx, tmin.yz);
    t0 = max(t.x, t.y);
    t = min(tmax.xx, tmax.yz);
    t1 = min(t.x, t.y);
    return t0 <= t1;
}

float Absorption = 2.5;

const float maxDist = sqrt(2.0);
const int numSamples = 256;
const float stepSize = maxDist/float(numSamples);
const int numLightSamples = 16;
const float lscale = maxDist / float(numLightSamples);
const float densityFactor = 700;

void main()
{
    vec3 rayDirection = VertexIn.FragPosition - ubo.CamPosition;

    vec3 eyePos = (ubo.ViewMatrix * vec4(ubo.CamPosition, 1.0)).rgb;
    Ray eye = Ray( ubo.CamPosition, normalize(rayDirection) );
    AABB aabb = AABB(vec3(-1.0), vec3(+1.0));

    float tnear, tfar;
    IntersectBox(eye, aabb, tnear, tfar);
    if (tnear < 0.0) tnear = 0.0;

    vec3 rayStart = eye.Origin + eye.Dir * tnear;
    vec3 rayStop = eye.Origin + eye.Dir * tfar;
    rayStart = 0.5 * (rayStart + 1.0);
    rayStop = 0.5 * (rayStop + 1.0);

    vec3 pos = rayStart;
    vec3 step = normalize(rayStop-rayStart) * stepSize;
    float travel = distance(rayStop, rayStart);
    float T = 1.0;
    vec3 Lo = vec3(0.0);

    for (int i=0; i < numSamples && travel > 0.0; ++i, pos += step, travel -= stepSize) {

        float density = texture(VolumeTextures, pos).x * densityFactor;
        if (density <= 0.0)
            continue;

        T *= 1.0-density*stepSize*Absorption;
        if (T <= 0.01)
            break;

        for(int l = 0; l < numLights; l++) {
            vec3 lightDir = normalize(lights[l].Position.rgb - pos)*lscale;
            float Tl = 1.0;
            vec3 lpos = pos + lightDir;
            float distance = distance(lights[l].Position.rgb, pos);

            for (int s=0; s < numLightSamples; ++s) {
                float ld = texture(VolumeTextures, lpos).x;
                Tl *= 1.0-Absorption*stepSize*ld;
                if (Tl <= 0.01)
                lpos += lightDir;
            }

            float lightAttenuation = 1.0 / (1.0 + lights[i].Linear * distance + lights[i].Quadratic * distance * distance);
            vec3 Li = lights[i].Color.rgb * lightAttenuation * lights[l].Intensity * Tl;
            Lo += Li*T*density*stepSize;
        }
    }

    FragColor.rgb = Lo;
    FragColor.a = 1-T;
}
