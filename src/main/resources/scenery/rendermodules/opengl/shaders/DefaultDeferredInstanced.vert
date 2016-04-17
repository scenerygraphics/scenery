#version 400 core

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;
layout(location = 3) in mat4 ModelMatrix;
layout(location = 7) in mat4 ModelViewMatrix;
layout(location = 11) in mat4 MVP;

out VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
    vec4 Color;
} VertexOut;

uniform mat3 NormalMatrix;
uniform mat4 ProjectionMatrix;
uniform vec3 CamPosition;

void main()
{
    VertexOut.Normal = transpose(inverse(mat3(ModelMatrix)))*vertexNormal;
    VertexOut.Position = vec3( ModelViewMatrix * vec4(vertexPosition, 1.0));
    VertexOut.TexCoord = vertexTexCoord;
    VertexOut.FragPosition = vec3(ModelMatrix * vec4(vertexPosition, 1.0));
    VertexOut.Color = vec4(0.0f, 0.0f, 0.0f, 0.0f);

    gl_Position = MVP * vec4(vertexPosition, 1.0);
}


