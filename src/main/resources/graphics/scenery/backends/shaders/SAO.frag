#version 450 core
#extension GL_ARB_separate_shader_objects: enable

#define PI 3.14159265359

layout(set = 3, binding = 0) uniform sampler2D InputNormalsMaterial;
layout(set = 4, binding = 0) uniform sampler2D InputZBuffer;

layout(location = 0) out float FragColor;
layout(location = 0) in vec2 textureCoord;

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

const float IntensityScale = 1.0;
const float Epsilon = 0.01;
const float BiasDistance = 0.001;
const float Contrast = 1.0;

vec3 viewFromDepth(float depth, vec2 texcoord) {
    vec2 uv = (vrParameters.stereoEnabled ^ 1) * texcoord + vrParameters.stereoEnabled * vec2((texcoord.x - 0.5 * currentEye.eye) * 2.0, texcoord.y);

	mat4 invProjection = (vrParameters.stereoEnabled ^ 1) * InverseProjectionMatrix + vrParameters.stereoEnabled * vrParameters.inverseProjectionMatrices[currentEye.eye];
	mat4 invView = (vrParameters.stereoEnabled ^ 1) * InverseViewMatrices[0] + vrParameters.stereoEnabled * (InverseViewMatrices[currentEye.eye]);

#ifndef OPENGL
    vec4 clipSpacePosition = vec4(uv * 2.0 - 1.0, depth, 1.0);
#else
    vec4 clipSpacePosition = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
#endif
    vec4 viewSpacePosition = invProjection * clipSpacePosition;

    viewSpacePosition /= viewSpacePosition.w;
    return viewSpacePosition.xyz;
}

vec3 worldFromDepth(float depth, vec2 texcoord) {
    vec2 uv = (vrParameters.stereoEnabled ^ 1) * texcoord + vrParameters.stereoEnabled * vec2((texcoord.x - 0.5 * currentEye.eye) * 2.0, texcoord.y);

	mat4 invProjection = (vrParameters.stereoEnabled ^ 1) * InverseProjectionMatrix + vrParameters.stereoEnabled * vrParameters.inverseProjectionMatrices[currentEye.eye];
	mat4 invView = (vrParameters.stereoEnabled ^ 1) * InverseViewMatrices[0] + vrParameters.stereoEnabled * (InverseViewMatrices[currentEye.eye]);

#ifndef OPENGL
    vec4 clipSpacePosition = vec4(uv * 2.0 - 1.0, depth, 1.0);
#else
    vec4 clipSpacePosition = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
#endif
    vec4 viewSpacePosition = invProjection * clipSpacePosition;

    viewSpacePosition /= viewSpacePosition.w;
    vec4 world = invView * viewSpacePosition;
    return world.xyz;
}

const int NUM_SPIRAL_TURNS = 7;

vec3 tapLocation(int index, float angle) {
    float alpha = float(index + 0.5) * (1.0 / occlusionSamples);
    float a = alpha * NUM_SPIRAL_TURNS * 6.28 + angle;

    return vec3(cos(a), sin(a), alpha);
}

vec3 getOffsetPosition(ivec2 ssC, vec2 unitOffset, float ssR) {
    ivec2 ssP = ivec2(ssR * unitOffset) + ssC;

    vec3 P = vec3(0.0);
    P.z = texture(InputZBuffer, ssP/vec2(displayWidth, displayHeight)).r;

    P = viewFromDepth(P.z, vec2(ssP)/vec2(displayWidth, displayHeight));
    return P;
}

float sampleSAO(ivec2 ssC, vec3 C, vec3 N, float radius, float randomAngle, int index) {
    vec3 unitOffset = tapLocation(index, randomAngle);
    float ssR = unitOffset.z * radius;

    vec3 Q = getOffsetPosition(ssC, unitOffset.xy, ssR);
    vec3 v = Q - C;

    float vdotv = dot(v, v);
    float vdotN = dot(v, N);

    float f = max(occlusionRadius * occlusionRadius - vdotv, 0.0);
    return f * f * f * max(0.0, (vdotN - BiasDistance) / (vdotv + Epsilon));
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

void main() {
    vec2 textureCoord = gl_FragCoord.xy/vec2(displayWidth, displayHeight);
    textureCoord = (vrParameters.stereoEnabled ^ 1) * textureCoord + vrParameters.stereoEnabled * vec2((textureCoord.x - 0.5 * currentEye.eye) * 2.0, textureCoord.y);
    ivec2 ssC = ivec2(gl_FragCoord.xy);

    mat4 view = (vrParameters.stereoEnabled ^ 1) * ViewMatrices[0] + (vrParameters.stereoEnabled * ViewMatrices[currentEye.eye]);

	float Depth = texture(InputZBuffer, textureCoord).r;
    vec3 viewSpaceFragPos = viewFromDepth(Depth, textureCoord);
	vec3 N = normalize(cross(dFdy(viewSpaceFragPos), dFdx(viewSpaceFragPos)));

    float projScale = -displayHeight/(2.0 * tan(50.0 * 0.5));
    float randomAngle = (3 * ssC.x ^ ssC.y + ssC.x * ssC.y) * 10.0;
    float scaledDiskRadius = -projScale * occlusionRadius / viewSpaceFragPos.z;
    float intensityScaleDivR6 = IntensityScale / pow(occlusionRadius, 6.0);

    float ambientOcclusion = 0.0f;

    if(occlusionSamples > 0) {
        //Alchemy SSAO
        float A = 0.0f;

        for (int i = 0; i < occlusionSamples; ++i) {
            A += sampleSAO(ssC, viewSpaceFragPos, N, scaledDiskRadius, randomAngle, i);
        }

        A = max(0.0, 1.0 - A * intensityScaleDivR6 * (5.0/occlusionSamples));
        ambientOcclusion = A;
    }

    FragColor = ambientOcclusion;
}
