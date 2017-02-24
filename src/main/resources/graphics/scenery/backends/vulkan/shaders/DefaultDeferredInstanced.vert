#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;
layout(location = 3) in mat4 ModelMatrix;
layout(location = 7) in mat4 ModelViewMatrix;
layout(location = 11) in mat4 MVP;

layout(location = 0) out VertexData {
    vec3 FragPosition;
    vec3 Normal;
    vec2 TexCoord;
} VertexOut;

layout(binding = 0) uniform Matrices {
	mat4 uModelViewMatrix;
	mat4 uModelMatrix;
	mat4 uProjectionMatrix;
	mat4 uMVP;
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
	VertexOut.Normal = transpose(inverse(mat3(ModelMatrix)))*vertexNormal;
    VertexOut.TexCoord = vertexTexCoord;
    VertexOut.FragPosition = vec3(ModelMatrix * vec4(vertexPosition, 1.0));

    gl_Position = MVP * vec4(vertexPosition, 1.0);
}


