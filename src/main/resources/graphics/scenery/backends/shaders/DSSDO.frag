#version 450 core
#extension GL_ARB_separate_shader_objects: enable
#extension GL_EXT_control_flow_attributes : enable

// DSSDO method adapted from DSSDO RenderMonkey example, https://github.com/kayru/dssdo
// MIT License, Copyright (c) 2011 Yuriy O'Donnell
// Original reference for SSDO: Ritschel, et al.
// https://people.mpi-inf.mpg.de/~ritschel/Papers/SSDO.pdf

#define PI 3.14159265359

layout(set = 3, binding = 0) uniform sampler2D InputNormalsMaterial;
layout(set = 3, binding = 1) uniform sampler2D InputDiffuseAlbedo;
layout(set = 3, binding = 2) uniform sampler2D InputEmission;
layout(set = 3, binding = 3) uniform sampler2D InputZBuffer;

layout(location = 0) out vec4 FragColor;
layout(location = 0) in VertexData {
    vec2 textureCoord;
    mat4 projectionMatrix;
    mat4 viewMatrix;
    mat4 frustumVectors;
} Vertex;

layout(set = 0, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
    mat4 inverseProjectionMatrices[2];
    mat4 headShift;
    float IPD;
    int stereoEnabled;
} vrParameters;

layout(set = 1, binding = 0) uniform LightParameters {
    mat4 ViewMatrices[2];
    mat4 InverseViewMatrices[2];
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec3 CamPosition;
};

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

layout(set = 2, binding = 0, std140) uniform ShaderParameters {
	int displayWidth;
	int displayHeight;
	float occlusionRadius;
	int occlusionSamples;
    float maxDistance;
    int algorithm;
};

vec3 worldFromDepth(float depth, vec2 texcoord, const mat4 invProjection, const mat4 invView) {
    #ifndef OPENGL
    vec3 clipSpacePosition = vec3(texcoord * 2.0 - 1.0, depth);
    #else
    vec3 clipSpacePosition = vec3(texcoord * 2.0 - 1.0, depth * 2.0 - 1.0);
    #endif
    vec4 viewSpacePosition = vec4(
    invProjection[0][0] * clipSpacePosition.x + invProjection[3][0],
    invProjection[1][1] * clipSpacePosition.y + invProjection[3][1],
    -1.0,
    invProjection[2][3] * clipSpacePosition.z + invProjection[3][3]);

    viewSpacePosition /= viewSpacePosition.w;
    vec4 world = invView * viewSpacePosition;
    return world.xyz;
}

// McGuire Noise -- https://www.shadertoy.com/view/4dS3Wd
float hash(float n) { return fract(sin(n) * 1e4); }
float hash(vec2 p) { return fract(1e4 * sin(17.0 * p.x + p.y * 0.1) * (0.1 + abs(sin(p.y * 13.0 + p.x)))); }

float noise1D(float x) {
    float i = floor(x);
    float f = fract(x);
    float u = f * f * (3.0 - 2.0 * f);
    return mix(hash(i), hash(i + 1.0), u);
}

float noise1D(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);

    // Four corners in 2D of a tile
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    // Simple 2D lerp using smoothstep envelope between the values.
    // return vec3(mix(mix(a, b, smoothstep(0.0, 1.0, f.x)),
    //			mix(c, d, smoothstep(0.0, 1.0, f.x)),
    //			smoothstep(0.0, 1.0, f.y)));

    // Same code, with the clamps in smoothstep and common subexpressions
    // optimized away.
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float noise2D(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);

    // Four corners in 2D of a tile
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    // Simple 2D lerp using smoothstep envelope between the values.
    // return vec3(mix(mix(a, b, smoothstep(0.0, 1.0, f.x)),
    //			mix(c, d, smoothstep(0.0, 1.0, f.x)),
    //			smoothstep(0.0, 1.0, f.y)));

    // Same code, with the clamps in smoothstep and common subexpressions
    // optimized away.
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

#define NUM_NOISE_OCTAVES 5

float fbm(vec2 x) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100);
    // Rotate to reduce axial bias
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.50));
    for (int i = 0; i < NUM_NOISE_OCTAVES; ++i) {
        v += a * noise1D(x);
        x = rot * x * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

vec2 OctWrap( vec2 v )
{
    vec2 ret;
    ret.x = (1-abs(v.y)) * (v.x >= 0 ? 1.0 : -1.0);
    ret.y = (1-abs(v.x)) * (v.y >= 0 ? 1.0 : -1.0);
    return ret.xy;
}

/*
Decodes the octahedron normal vector from it's two component form to return the normal with its three components. Uses the
property |x| + |y| + |z| = 1 and reverses the orthogonal projection performed while encoding.
*/
vec3 DecodeOctaH( vec2 encN )
{
    encN = encN * 2.0 - 1.0;
    vec3 n;
    n.z = 1.0 - abs( encN.x ) - abs( encN.y );
    n.xy = n.z >= 0.0 ? encN.xy : OctWrap( encN.xy );
    n = normalize( n );
    return n;
}

const vec3 points[] =
	{
		vec3(-0.134, 0.044, -0.825),
		vec3(0.045, -0.431, -0.529),
		vec3(-0.537, 0.195, -0.371),
		vec3(0.525, -0.397, 0.713),
		vec3(0.895, 0.302, 0.139),
		vec3(-0.613, -0.408, -0.141),
		vec3(0.307, 0.822, 0.169),
		vec3(-0.819, 0.037, -0.388),
		vec3(0.376, 0.009, 0.193),
		vec3(-0.006, -0.103, -0.035),
		vec3(0.098, 0.393, 0.019),
		vec3(0.542, -0.218, -0.593),
		vec3(0.526, -0.183, 0.424),
		vec3(-0.529, -0.178, 0.684),
		vec3(0.066, -0.657, -0.570),
		vec3(-0.214, 0.288, 0.188),
		vec3(-0.689, -0.222, -0.192),
		vec3(-0.008, -0.212, -0.721),
		vec3(0.053, -0.863, 0.054),
		vec3(0.639, -0.558, 0.289),
		vec3(-0.255, 0.958, 0.099),
		vec3(-0.488, 0.473, -0.381),
		vec3(-0.592, -0.332, 0.137),
		vec3(0.080, 0.756, -0.494),
		vec3(-0.638, 0.319, 0.686),
		vec3(-0.663, 0.230, -0.634),
		vec3(0.235, -0.547, 0.664),
		vec3(0.164, -0.710, 0.086),
		vec3(-0.009, 0.493, -0.038),
		vec3(-0.322, 0.147, -0.105),
		vec3(-0.554, -0.725, 0.289),
		vec3(0.534, 0.157, -0.250),
};

void main() {
    if(occlusionSamples == 0) {
        FragColor = vec4(0.0);
        return;
    }

    vec2 textureCoord = gl_FragCoord.xy/vec2(displayWidth, displayHeight);
    vec2 uv = (vrParameters.stereoEnabled ^ 1) * textureCoord + vrParameters.stereoEnabled * vec2((textureCoord.x - 0.5 * currentEye.eye) * 2.0, textureCoord.y);
//    vec2 uv = textureCoord;

	vec3 N = DecodeOctaH(texture(InputNormalsMaterial, textureCoord).rg);
	float Depth = texture(InputZBuffer, textureCoord).r;
    vec3 FragPos = worldFromDepth(Depth, uv, vrParameters.inverseProjectionMatrices[currentEye.eye], InverseViewMatrices[currentEye.eye]);

    vec4 occlusion = vec4(0.0f);

    float dist = distance(CamPosition, FragPos);
    float radius = occlusionRadius / dist;
    float maxInvDistance = 1.0f / maxDistance;

    float attenuationAngleThreshold = 0.1;

    vec2 noise = vec2(fbm(gl_FragCoord.xy), fbm(gl_FragCoord.yx)) * 2.0f - 1.0f;

    const float fudge_factor_l0 = 2.0;
    const float fudge_factor_l1 = 10.0;

    const float sh2_weight_l0 = fudge_factor_l0 * 0.28209;
    const vec3 sh2_weight_l1 = vec3(fudge_factor_l1 * 0.48860);

    const vec4 sh2_weight = vec4(sh2_weight_l1, sh2_weight_l0)/occlusionSamples;

    [[unroll]] for (int i = 0; i < occlusionSamples; ++i) {
        vec2 offset = reflect(points[i].xy, noise.xy).xy * radius;
        vec2 texcoord = textureCoord + offset;
        vec2 uvs = (vrParameters.stereoEnabled ^ 1) * texcoord + vrParameters.stereoEnabled * vec2((texcoord.x - 0.5 * currentEye.eye) * 2.0, texcoord.y);

        vec3 pos = worldFromDepth(texture(InputZBuffer, texcoord).r, uvs, vrParameters.inverseProjectionMatrices[currentEye.eye], InverseViewMatrices[currentEye.eye]);
        vec3 center_to_pos = pos - FragPos;

        float dist = length(center_to_pos);
        vec3 center_to_pos_normalized = center_to_pos/dist;

        float attenuation = 1.0f - clamp(dist * maxInvDistance, 0.0, 1.0);
        float dp = dot(N, center_to_pos_normalized);

        attenuation = attenuation * attenuation * step(attenuationAngleThreshold, dp);

        occlusion += attenuation * sh2_weight * vec4(center_to_pos_normalized, 1.0);
    }

    FragColor = occlusion;
}
