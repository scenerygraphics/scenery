#version 450
#extension GL_ARB_separate_shader_objects: enable

#define PI 3.14159265359

layout(location = 0) out vec2 textureCoord;

void main()
{
    textureCoord = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(0.5f * textureCoord - 0.5f, 0.0f, 1.0f);
}
