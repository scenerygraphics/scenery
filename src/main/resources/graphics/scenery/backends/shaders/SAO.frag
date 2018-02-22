#version 450 core
#extension GL_ARB_separate_shader_objects: enable

#define PI 3.14159265359

layout(set = 3, binding = 0) uniform sampler2D InputNormalsMaterial;
layout(set = 3, binding = 1) uniform sampler2D InputDiffuseAlbedo;
layout(set = 3, binding = 2) uniform sampler2D InputZBuffer;

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
    mat4 ViewMatrix;
    mat4 InverseViewMatrix;
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
	float ssaoRadius;
	int ssaoSamples;
    float IntensityScale;
    float Epsilon;
    float BiasDistance;
    float Contrast;
};

const int ROTATIONS[] = {
    1, 1, 2, 3, 2, 5, 2, 3, 2,
    3, 3, 5, 5, 3, 4, 7, 5, 5, 7,
    9, 8, 5, 5, 7, 7, 7, 8, 5, 8,
    11, 12, 7, 10, 13, 8, 11, 8, 7, 14,
    11, 11, 13, 12, 13, 19, 17, 13, 11, 18,
    19, 11, 11, 14, 17, 21, 15, 16, 17, 18,
    13, 17, 11, 17, 19, 18, 25, 18, 19, 19,
    29, 21, 19, 27, 31, 29, 21, 18, 17, 29,
    31, 31, 23, 18, 25, 26, 25, 23, 19, 34,
    19, 27, 21, 25, 39, 29, 17, 21, 27
};

vec3 viewFromDepth(float depth, vec2 texcoord) {
    mat4 invHeadToEye = vrParameters.headShift;
    invHeadToEye[0][3] += currentEye.eye * vrParameters.IPD;

	mat4 invProjection = (vrParameters.stereoEnabled ^ 1) * InverseProjectionMatrix + vrParameters.stereoEnabled * vrParameters.inverseProjectionMatrices[currentEye.eye];
	mat4 invView = (vrParameters.stereoEnabled ^ 1) * InverseViewMatrix + vrParameters.stereoEnabled * InverseViewMatrix * invHeadToEye;

    vec4 clipSpacePosition = vec4(texcoord * 2.0 - 1.0, depth, 1.0);
    vec4 viewSpacePosition = invProjection * clipSpacePosition;

//    viewSpacePosition /= viewSpacePosition.w;
    return viewSpacePosition.xyz/viewSpacePosition.w;
}

vec3 tapLocation(int index, float angle) {
    float alpha = float(index + 0.5) * (1.0 / ssaoSamples);
    float a = alpha * ROTATIONS[ssaoSamples - 1] * 2 * PI + angle;

    return vec3(cos(a), sin(a), alpha);
}

vec3 getOffsetPosition(ivec2 ssC, vec2 unitOffset, float ssR) {
    ivec2 ssP = ivec2(ssR * unitOffset) + ssC;

    vec3 P = vec3(0.0);
    P.z = texelFetch(InputZBuffer, ssP, 0).r;

    P = viewFromDepth(P.z, (vec2(ssP) + vec2(0.5))/vec2(displayWidth, displayHeight));
    return P;
}

float sampleSAO(ivec2 ssC, vec3 C, vec3 N, float radius, float randomAngle, int index) {
    vec3 unitOffset = tapLocation(index, randomAngle);
    float ssR = unitOffset.z * radius;

    vec3 Q = getOffsetPosition(ssC, unitOffset.xy, ssR);
    vec3 v = Q - C;

    float vdotv = dot(v, v);
    float vdotN = dot(v, N) - BiasDistance;

    float f = max(ssaoRadius * ssaoRadius - vdotv, 0.0);
    return f * f * f * max(0.0, (vdotN - BiasDistance) / (vdotv + Epsilon));
}

// McGuire Noise -- https://www.shadertoy.com/view/4dS3Wd
float hash(float n) { return fract(sin(n) * 1e4); }
float hash(vec2 p) { return fract(1e4 * sin(17.0 * p.x + p.y * 0.1) * (0.1 + abs(sin(p.y * 13.0 + p.x)))); }

float noise(float x) {
    float i = floor(x);
    float f = fract(x);
    float u = f * f * (3.0 - 2.0 * f);
    return mix(hash(i), hash(i + 1.0), u);
}


float noise(vec2 x) {
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
    ivec2 ssC = ivec2(gl_FragCoord.xy);

	vec3 N = DecodeOctaH(texture(InputNormalsMaterial, textureCoord).rg);
	float Depth = texture(InputZBuffer, textureCoord).r;
    vec3 viewSpaceFragPos = viewFromDepth(Depth, textureCoord);
    vec3 viewSpaceCamPos = (ViewMatrix * vec4(CamPosition, 1.0)).xyz;

    float projScale = -displayHeight/(2.0 * tan(50.0 * 0.5));
    float randomAngle = (3 * ssC.x ^ ssC.y + ssC.x * ssC.y) * 10.0;
    float scaledDiskRadius = -projScale * ssaoRadius / viewSpaceFragPos.z;
    float intensityScaleDivR6 = IntensityScale / pow(ssaoRadius, 6.0);

    float ambientOcclusion = 0.0f;

    if(ssaoSamples > 0) {
        //Alchemy SSAO
        float A = 0.0f;

        for (int i = 0; i < ssaoSamples; ++i) {
            A += sampleSAO(ssC, viewSpaceFragPos, (ViewMatrix * vec4(N, 1.0)).xyz, scaledDiskRadius, randomAngle, i);
        }

//        A /= ssaoRadius * ssaoRadius * ssaoRadius;
//        A = pow(max(0, 1 - 2.0 * IntensityScale * A / ssaoSamples), Contrast);
//        A = (pow(A, 0.2) + 1.2 * A * A * A * A) / 2.2;
        A = max(0.0, 1.0 - A * intensityScaleDivR6 * (5.0/ssaoSamples));
        ambientOcclusion = A;
    }

    FragColor = ambientOcclusion;
}
