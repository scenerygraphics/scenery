#version 450 core

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

out VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
} VertexOut;

layout(binding = 0) uniform Matrices {
	mat4 ModelViewMatrix;
	mat4 ModelMatrix;
	mat4 ProjectionMatrix;
	mat4 MVP;
	vec3 CamPosition;
	int isBillboard;
} ubo;

void main()
{
    VertexOut.Normal = mat3(transpose(inverse(ubo.ModelMatrix)))*vertexNormal;
    VertexOut.Position = vec3( ubo.ModelViewMatrix * vec4(vertexPosition, 1.0));
    VertexOut.TexCoord = vertexTexCoord;
    VertexOut.FragPosition = vec3(ubo.ModelMatrix * vec4(vertexPosition, 1.0));

    gl_Position = ubo.MVP * vec4(vertexPosition, 1.0);
}
