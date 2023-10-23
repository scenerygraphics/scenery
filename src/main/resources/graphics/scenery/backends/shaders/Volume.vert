#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

layout(location = 0) out VertexData {
    vec2 textureCoord;
    mat4 inverseProjection;
    mat4 inverseModelView;
    mat4 modelView;
    mat4 MVP;
} Vertex;

layout(set = 0, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
    mat4 inverseProjectionMatrices[2];
    mat4 headShift;
    float IPD;
    int stereoEnabled;
} vrParameters;

struct Light {
	float Linear;
	float Quadratic;
	float Intensity;
	float Radius;
	vec4 Position;
  	vec4 Color;
};

const int MAX_NUM_LIGHTS = 1024;

layout(set = 1, binding = 0) uniform LightParameters {
    mat4 ViewMatrices[2];
    mat4 InverseViewMatrices[2];
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec3 CamPosition;
};

layout(set = 2, binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 NormalMatrix;
	int isBillboard;
} ubo;

layout(set = 5, binding = 0) uniform ShaderProperties {
    float voxelSizeX;
    float voxelSizeY;
    float voxelSizeZ;
    int sizeX;
    int sizeY;
    int sizeZ;
    float trangemin;
    float trangemax;
    float boxMin_x;
    float boxMin_y;
    float boxMin_z;
    float boxMax_x;
    float boxMax_y;
    float boxMax_z;
    float stepSize;
    float alphaBlending;
    float gamma;
    int dataRangeMin;
    int dataRangeMax;
    int renderingMethod;
    float kernelSize;
    int occlusionSteps;
    float maxOcclusionDistance;
    float time;
};

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

float max3 (vec3 v) {
  return max (max (v.x, v.y), v.z);
}

void main()
{
	mat4 mv;
	mat4 nMVP;
	mat4 projectionMatrix;

    mv = ViewMatrices[currentEye.eye] * ubo.ModelMatrix;
	projectionMatrix = vrParameters.projectionMatrices[currentEye.eye];

    Vertex.inverseProjection = (vrParameters.stereoEnabled ^ 1) * InverseProjectionMatrix + (vrParameters.stereoEnabled * vrParameters.inverseProjectionMatrices[currentEye.eye]);
    Vertex.inverseModelView = inverse(mv);
    Vertex.MVP = projectionMatrix * mv;

    Vertex.modelView = mv;

    Vertex.textureCoord = vertexTexCoord;
    gl_Position = vec4(vertexPosition, 1.0f);
}
