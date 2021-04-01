#version 450 core
#extension GL_ARB_separate_shader_objects: enable
#extension GL_EXT_control_flow_attributes : enable

layout(location = 0) in VertexData {
    vec2 textureCoord;
    mat4 inverseProjection;
    mat4 inverseModelView;
    mat4 modelView;
    mat4 MVP;
} Vertex;

layout(location = 0) out vec4 FragColor;

const int NUM_OBJECT_TEXTURES = 6;
layout(set = 3, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];

void main()
{
    FragColor = vec4(texture(ObjectTextures[1], Vertex.textureCoord.xy).rgb, 1.0);
}

