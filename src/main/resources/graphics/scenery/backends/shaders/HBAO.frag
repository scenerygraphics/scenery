#version 450 core
#extension GL_ARB_separate_shader_objects: enable

#define PI 3.14159265359

layout(set = 3, binding = 0) uniform sampler2D InputNormalsMaterial;
layout(set = 4, binding = 0) uniform sampler2D InputZBuffer;

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

const float strengthPerRay = 0.1875;
const uint numRays = 8;
const uint maxStepsPerRay = 5;
const float halfSampleRadius = 0.25;
const float fallOff = 2.0;
const float bias = 0.03;

const vec2 sampleDirections[] = vec2[](
    vec2(0.0988498, 0.229627),
    vec2(0.232268, 0.0924736),
    vec2(0.229627, -0.0988498),
    vec2(0.0924736, -0.232268),
    vec2(-0.0988498, -0.229627),
    vec2(-0.232268, -0.0924736),
    vec2(-0.229627, 0.0988498),
    vec2(-0.0924736, 0.232268),
    vec2(0.1977, 0.459255),
    vec2(0.464537, 0.184947),
    vec2(0.459255, -0.1977),
    vec2(0.184947, -0.464537),
    vec2(-0.1977, -0.459255),
    vec2(-0.464537, -0.184947),
    vec2(-0.459255, 0.1977),
    vec2(-0.184947, 0.464537),
    vec2(0.29655, 0.688882),
    vec2(0.696805, 0.277421),
    vec2(0.688882, -0.29655),
    vec2(0.277421, -0.696805),
    vec2(-0.29655, -0.688882),
    vec2(-0.696805, -0.277421),
    vec2(-0.688882, 0.29655),
    vec2(-0.277421, 0.696805),
    vec2(0.395399, 0.918509),
    vec2(0.929074, 0.369895),
    vec2(0.918509, -0.395399),
    vec2(0.369895, -0.929074),
    vec2(-0.395399, -0.918509),
    vec2(-0.929074, -0.369895),
    vec2(-0.918509, 0.395399),
    vec2(-0.369895, 0.929074)
);

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

float random (vec2 st) {
    return fract(sin(dot(st.xy,
        vec2(12.9898,78.233)))*
            43758.5453123);
}

vec2 Rotate(vec2 v, vec2 rotationX, vec2 rotationY) {
    vec2 rotated;

    vec3 expanded = vec3(v, 0.0);
    rotated.x = dot(expanded.xyz, rotationX.xyy);
    rotated.y = dot(expanded.xyz, rotationY.xyy);

    return rotated;
}

vec2 snapToTexel(vec2 uv, vec2 maxScreenCoords) {
    return round(uv * maxScreenCoords) / maxScreenCoords;
}

float DepthToViewZ(float depth) {
	mat4 projectionMatrix = (vrParameters.stereoEnabled ^ 1) * ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];
    return projectionMatrix[3][2] / (depth - projectionMatrix[2][2]);
}

/**
    sampleOcclusionOnRay - samples HBAO occlusion along a given ray

    [uv] - center coordinate of the kernel.
    [frustumVector] - Frustum vector of the sample point.
    [centerViewPos] - The view-space position of the center point.
    [centerNormal] - The normal at the center point.
    [tangent] - Tangent vector in the sampling direction at the center point.
    [topOcclusion] - The maximum cos(angle) found sofar, will be updated when new
        occluding sample has been found.
*/
float sampleOcclusionOnRay(vec2 uv, vec3 frustumVector, vec3 centerViewPos,
    vec3 centerNormal, vec3 tangent, inout float topOcclusion) {

    float sampleDepth = texture(InputZBuffer, uv).r;
    vec3 sampleViewPos = frustumVector * DepthToViewZ(sampleDepth);

    vec3 horizonVector = sampleViewPos - centerViewPos;
    float horizonVectorLength = length(horizonVector);

    float occlusion = 0.0f;

    if(dot(tangent, horizonVector) < 0.0) {
        return 0.5f;
    } else {
        occlusion = dot(centerNormal, horizonVector) / horizonVectorLength;
    }

    float diff = max(occlusion - topOcclusion, 0.0);
    topOcclusion = max(occlusion, topOcclusion);

    float distanceFactor = clamp(horizonVectorLength / fallOff, 0.0, 1.0);
    distanceFactor = 1.0 - distanceFactor * distanceFactor;

    return diff * distanceFactor;

}

/**
    sampleHBAO - get HBAO occlusion for given sample

    [origin] - origin for a given ray.
    [direction] - direction of the ray.
    [jitter] - Random jitter for start offset.
    [maxScreenCoords] - Maximum screen space position corresponding to uv = 1.
    [projectedRadii] - Sample radius in UV space.
    [numStepsPerRay] - Steps to take per single ray.
    [centerViewPos] - View-space position of the center point.
    [centerNormal] - Normal of the center point.
    [frustumDiff] - Differences of the frustum vectors, horizontally and vertically,
        for frustum vector interpolation.
*/
float sampleHBAO(vec2 origin, vec2 direction, float jitter, vec2 maxScreenCoords,
    vec2 projectedRadii, uint numStepsPerRay, vec3 centerViewPos, vec3 centerNormal,
    vec2 frustumDiff) {

    vec2 texelSizedStep = direction / vec2(displayWidth, displayHeight);
    direction *= projectedRadii;

//    vec3 tangent = GetViewPosition(origin + texelSizedStep, frustumDiff) - centerViewPos;
    float depth = texture(InputZBuffer, origin + texelSizedStep).r;
    vec3 tangent = viewFromDepth(depth, origin + texelSizedStep) - centerViewPos;
    tangent -= dot(centerNormal, tangent) * centerNormal;

    vec2 stepUV = snapToTexel(direction.xy / (numStepsPerRay - 1), maxScreenCoords);

    vec2 jitteredOffset = mix(texelSizedStep, stepUV, jitter);
    vec2 uv = snapToTexel(origin + jitteredOffset, maxScreenCoords);

    vec3 frustumVector = vec3(Vertex.frustumVectors[3].xy + uv * frustumDiff, 1.0f);
    vec2 frustumVectorStep = stepUV * frustumDiff;

    float topOcclusion = bias;
    float occlusion = 0.0f;

    for(uint step = 0; step < numStepsPerRay; ++step) {
        occlusion += sampleOcclusionOnRay(uv, frustumVector, centerViewPos, centerNormal, tangent, topOcclusion);

        uv += stepUV;
        frustumVector.xy += frustumVectorStep.xy;
    }

    return occlusion;
}

void main() {
    vec2 screenSize = vec2(displayWidth, displayHeight);
    vec2 textureCoord = gl_FragCoord.xy/screenSize;
    textureCoord = (vrParameters.stereoEnabled ^ 1) * textureCoord + vrParameters.stereoEnabled * vec2((textureCoord.x - 0.5 * currentEye.eye) * 2.0, textureCoord.y);

    vec2 maxScreenCoords = screenSize - vec2(1.0);

	float centerDepth = texture(InputZBuffer, textureCoord).r;
    vec3 centerViewPos = viewFromDepth(centerDepth, textureCoord);
	vec3 centerNormal = DecodeOctaH(texture(InputNormalsMaterial, textureCoord).rg);

	vec3 randomFactors = vec3(random(textureCoord.xy), random(textureCoord.yx), 0.0);
	vec2 rotationX = normalize(randomFactors.xy - vec2(0.5f));
	vec2 rotationY = rotationX.yx * vec2(-1.0f, 1.0f);

	vec2 frustumDiff = vec2(Vertex.frustumVectors[2].x - Vertex.frustumVectors[3].x,
	    Vertex.frustumVectors[0].y - Vertex.frustumVectors[3].y);

	mat4 projectionMatrix = (vrParameters.stereoEnabled ^ 1) * ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];
	float w = centerViewPos.z * projectionMatrix[2][3] + projectionMatrix[3][3];
	vec2 projectedRadii = halfSampleRadius * vec2(projectionMatrix[1][1], projectionMatrix[2][2]) / w;
	float screenRadius = projectedRadii.x * displayWidth;

	if(screenRadius < 1.0) {
	    FragColor = vec4(1.0);
	    return;
	}

	uint stepsPerRay = min(maxStepsPerRay, uint(screenRadius));

    float ambientOcclusion = 0.0f;
    float A = 0.0f;

    if(occlusionSamples > 0) {
        for (uint i = 0; i < occlusionSamples; ++i) {
            vec2 sampleDir = Rotate(sampleDirections[i].xy, rotationX, rotationY);
            A += sampleHBAO(textureCoord, sampleDir, randomFactors.z, screenSize,
                projectedRadii, stepsPerRay, centerViewPos, centerNormal, frustumDiff);
        }
    }

    ambientOcclusion = 1.0 - clamp(strengthPerRay * A, 0.0, 1.0);
    FragColor = vec4(ambientOcclusion);
}
