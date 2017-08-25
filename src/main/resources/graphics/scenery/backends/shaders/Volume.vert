#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

layout(location = 0) out VertexData {
    vec2 textureCoord;
    mat4 inverseProjection;
    mat4 inverseModelView;
    mat4 MVP;
} Vertex;

layout(set = 0, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
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
    mat4 ViewMatrix;
    vec3 CamPosition;
    int numLights;
	Light lights[MAX_NUM_LIGHTS];
};

layout(set = 2, binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 NormalMatrix;
	mat4 ProjectionMatrix;
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
    int maxsteps;
    float alpha_blending;
    float gamma;
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

    mat4 headToEye = vrParameters.headShift;
	headToEye[3][0] -= currentEye.eye * vrParameters.IPD;

    mv = (vrParameters.stereoEnabled ^ 1) * ViewMatrix * ubo.ModelMatrix + (vrParameters.stereoEnabled * headToEye * ViewMatrix * ubo.ModelMatrix);
	projectionMatrix = (vrParameters.stereoEnabled ^ 1) * ubo.ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];

	vec3 L = vec3(sizeX, sizeY, sizeZ) * vec3(voxelSizeX, voxelSizeY, voxelSizeZ);

	float Lmax = max3(L);

	mat4 invScale = mat4(1.0);
	invScale[0][0] = Lmax/L.x;
	invScale[1][1] = Lmax/L.y;
	invScale[2][2] = Lmax/L.z;

	mat4 scale = mat4(1.0);
	scale[0][0] = L.x/Lmax;
	scale[1][1] = L.y/Lmax;
	scale[2][2] = L.z/Lmax;

    Vertex.inverseProjection = inverse(projectionMatrix);
    Vertex.inverseModelView = invScale * inverse(mv);
    Vertex.MVP = projectionMatrix * mv;

    Vertex.textureCoord = vertexTexCoord;
	gl_Position = vec4(vertexPosition, 1.0);
}
