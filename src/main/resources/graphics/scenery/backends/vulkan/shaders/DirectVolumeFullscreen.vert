#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

layout(location = 0) out vec2 textureCoord;
layout(location = 1) out mat4 inverseProjection;
layout(location = 5) out mat4 inverseModelView;

layout(binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 ViewMatrix;
	mat4 NormalMatrix;
	mat4 ProjectionMatrix;
	vec3 CamPosition;
	int isBillboard;
} ubo;

layout(set = 2, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
    mat4 headShift;
    float IPD;
    int stereoEnabled;
} vrParameters;

layout(set = 3, binding = 0) uniform ShaderProperties {
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
    mat4 headToEye = vrParameters.headShift;
	headToEye[3][0] -= currentEye.eye * vrParameters.IPD;

    mat4 mv = (vrParameters.stereoEnabled ^ 1) * ubo.ViewMatrix * ubo.ModelMatrix + (vrParameters.stereoEnabled * headToEye * ubo.ViewMatrix * ubo.ModelMatrix);
	mat4 projectionMatrix = (vrParameters.stereoEnabled ^ 1) * ubo.ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];

	vec3 L = vec3(sizeX, sizeY, sizeZ) * vec3(voxelSizeX, voxelSizeY, voxelSizeZ);

//    vec4 voxelSize = vec4(1.0f,1.0f,3.0f,0.0f);
//	vec3 L = vec3(200, 200, 40) * voxelSize.rgb;

	float Lmax = max3(L);

	mat4 invScale = mat4(1.0);
	invScale[0][0] = Lmax/L.x;
	invScale[1][1] = Lmax/L.y;
	invScale[2][2] = Lmax/L.z;

    inverseProjection = inverse(projectionMatrix);
    inverseModelView = invScale*inverse(mv);

    textureCoord = vertexTexCoord;
	gl_Position = vec4(vertexPosition, 1.0);
}
