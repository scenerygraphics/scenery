#version 400 core

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

out VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
} VertexOut;

uniform mat4 ModelViewMatrix;
uniform mat3 NormalMatrix;
uniform mat4 ProjectionMatrix;
uniform mat4 MVP;
uniform vec3 CamPosition;
uniform vec3 offset;

void main()
{
    VertexOut.Normal = vertexNormal;
    VertexOut.Position = vec3( ModelViewMatrix * vec4(vertexPosition, 1.0));
    VertexOut.Position.z = VertexOut.Position.z;
    VertexOut.TexCoord = vertexTexCoord;

    gl_Position = MVP * vec4(vertexPosition + offset, 1.0);
}
