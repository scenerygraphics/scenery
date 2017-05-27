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

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

void main()
{
    mat4 headToEye = vrParameters.headShift;
	headToEye[3][0] -= currentEye.eye * vrParameters.IPD;

    mat4 mv = (vrParameters.stereoEnabled ^ 1) * ubo.ViewMatrix * ubo.ModelMatrix + (vrParameters.stereoEnabled * headToEye * ubo.ViewMatrix * ubo.ModelMatrix);
	mat4 projectionMatrix = (vrParameters.stereoEnabled ^ 1) * ubo.ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];

    inverseProjection = inverse(projectionMatrix);
    inverseModelView = inverse(mv);

    textureCoord = vertexTexCoord;
	gl_Position = vec4(vertexPosition, 1.0);
}
