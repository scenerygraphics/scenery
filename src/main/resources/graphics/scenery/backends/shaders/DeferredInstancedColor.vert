#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;
layout(location = 3) in mat4 iModelMatrix;
layout(location = 7) in vec4 iVertexColor;

layout(location = 0) out VertexData {
    vec3 FragPosition;
    vec3 Normal;
    vec2 TexCoord;
	vec4 Color;
} Vertex;

layout(set = 2, binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 NormalMatrix;
	int isBillboard;
} ubo;

struct Light {
	float Linear;
	float Quadratic;
	float Intensity;
	float Radius;
	vec4 Position;
  	vec4 Color;
};

layout(set = 1, binding = 0) uniform LightParameters {
    mat4 ViewMatrices[2];
    mat4 InverseViewMatrices[2];
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec3 CamPosition;
};

layout(set = 0, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
    mat4 inverseProjectionMatrices[2];
    mat4 headShift;
    float IPD;
    int stereoEnabled;
} vrParameters;

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

void main()
{
mat4 mv;
	mat4 nMVP;
	mat4 projectionMatrix;

    mv = (vrParameters.stereoEnabled ^ 1) * ViewMatrices[0] * iModelMatrix + (vrParameters.stereoEnabled * ViewMatrices[currentEye.eye] * iModelMatrix);
	projectionMatrix = (vrParameters.stereoEnabled ^ 1) * ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];

    // TODO Temporarily commented out to fix https://github.com/scenerygraphics/sciview/issues/620
//	if(ubo.isBillboard > 0) {
//		mv[0][0] = 1.0f;
//		mv[0][1] = .0f;
//		mv[0][2] = .0f;
//
//		mv[1][0] = .0f;
//		mv[1][1] = 1.0f;
//		mv[1][2] = .0f;
//
//		mv[2][0] = .0f;
//		mv[2][1] = .0f;
//		mv[2][2] = 1.0f;
//	}

	nMVP = projectionMatrix*mv;

    mat4 normalMatrix = transpose(inverse(iModelMatrix));
    Vertex.Normal = mat3(normalMatrix) * normalize(vertexNormal);
    Vertex.TexCoord = vertexTexCoord;
    Vertex.FragPosition = vec3(iModelMatrix * vec4(vertexPosition, 1.0));
	Vertex.Color = iVertexColor;

	gl_Position = nMVP * vec4(vertexPosition, 1.0);
}


