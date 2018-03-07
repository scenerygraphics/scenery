#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

layout(location = 0) out VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
} Vertex;

layout(set = 2, binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 NormalMatrix;
	int isBillboard;
} ubo;

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

void main()
{
	mat4 mv;
	mat4 nMVP;
	mat4 projectionMatrix;

    mat4 headToEye = vrParameters.headShift;
	headToEye[3][0] -= currentEye.eye * vrParameters.IPD;

    mv = (vrParameters.stereoEnabled ^ 1) * ViewMatrix * ubo.ModelMatrix + (vrParameters.stereoEnabled * headToEye * ViewMatrix * ubo.ModelMatrix);
	projectionMatrix = (vrParameters.stereoEnabled ^ 1) * ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];

	if(ubo.isBillboard > 0) {
		mv[0][0] = 1.0f;
		mv[0][1] = .0f;
		mv[0][2] = .0f;

		mv[1][0] = .0f;
		mv[1][1] = 1.0f;
		mv[1][2] = .0f;

		mv[2][0] = .0f;
		mv[2][1] = .0f;
		mv[2][2] = 1.0f;
	}

	nMVP = projectionMatrix*mv;

    Vertex.FragPosition = vec3(ubo.ModelMatrix * vec4(vertexPosition, 1.0));
    Vertex.Normal = mat3(ubo.NormalMatrix) * normalize(vertexNormal);
    Vertex.TexCoord = vertexTexCoord;

    vec4 pos = mv*vec4(vertexPosition, 1.0);
    float near = 0.05;
    pos.z = min(pos.z, -near - 0.0001);

	gl_Position = projectionMatrix * pos;
}
