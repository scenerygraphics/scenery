#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in VertexData {
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
} VertexIn;

layout(location = 0) out vec3 gPosition;
layout(location = 1) out vec3 gNormal;
layout(location = 2) out vec4 gAlbedoSpec;

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 5;

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Shininess;
};

const int MATERIAL_HAS_DIFFUSE =  0x0001;
const int MATERIAL_HAS_AMBIENT =  0x0002;
const int MATERIAL_HAS_SPECULAR = 0x0004;
const int MATERIAL_HAS_NORMAL =   0x0008;

layout(binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 ViewMatrix;
	mat4 ProjectionMatrix;
	vec3 CamPosition;
	int isBillboard;
} ubo;

layout(binding = 1) uniform MaterialProperties {
    MaterialInfo Material;
    int materialType;
};

/*
    ObjectTextures[0] - ambient
    ObjectTextures[1] - diffuse
    ObjectTextures[2] - specular
    ObjectTextures[3] - normal
    ObjectTextures[4] - displacement
*/

layout(set = 1, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];

mat3 TBN(vec3 N, vec3 position, vec2 uv) {
    vec3 dp1 = dFdx(position);
    vec3 dp2 = dFdy(position);
    vec2 duv1 = dFdx(uv);
    vec2 duv2 = dFdy(uv);

    vec3 dp2Perpendicular = cross(dp2, N);
    vec3 dp1Perpendicular = cross(N, dp1);

    vec3 T = dp2Perpendicular * duv1.x + dp1Perpendicular * duv2.x;
    vec3 B = dp2Perpendicular * duv1.y + dp1Perpendicular * duv2.y;

    float invmax = inversesqrt(max(dot(T, T), dot(B, B)));

    return mat3(T * invmax, B * invmax, N);
}

void main() {
    // Store the fragment position vector in the first gbuffer texture
    gPosition = VertexIn.FragPosition;
    gAlbedoSpec.rgb = vec3(0.0f, 0.0f, 0.0f);

    gAlbedoSpec.rgb = Material.Kd;
    gAlbedoSpec.a = Material.Ka.r*Material.Shininess;

    // Also store the per-fragment normals into the gbuffer
    if((materialType & MATERIAL_HAS_AMBIENT) == MATERIAL_HAS_AMBIENT) {
        //gAlbedoSpec.rgb = texture(ObjectTextures[0], VertexIn.TexCoord).rgb;
    }

    if((materialType & MATERIAL_HAS_DIFFUSE) == MATERIAL_HAS_DIFFUSE) {
        gAlbedoSpec.rgb = texture(ObjectTextures[1], VertexIn.TexCoord).rgb;
    }

    if((materialType & MATERIAL_HAS_SPECULAR) == MATERIAL_HAS_SPECULAR) {
        gAlbedoSpec.a = texture(ObjectTextures[2], VertexIn.TexCoord).r;
    }

    if((materialType & MATERIAL_HAS_NORMAL) == MATERIAL_HAS_NORMAL) {
        vec3 normal = texture(ObjectTextures[3], VertexIn.TexCoord).rgb*(255.0/127.0) - (128.0/127.0);
//        normal = TBN(VertexIn.Normal, -VertexIn.FragPosition, VertexIn.TexCoord)*normal;

        gNormal = normalize(normal);// * 0.5 + vec3(0.5);
    } else {
        gNormal = VertexIn.Normal;
    }
}
