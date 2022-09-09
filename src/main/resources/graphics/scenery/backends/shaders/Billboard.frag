#version 450 core
#extension GL_ARB_separate_shader_objects: enable
#extension GL_EXT_control_flow_attributes : enable

layout(location = 0) in VertexDataIn {
    vec3 Position;
    vec2 TexCoord;
    vec3 Normal;
} Vertex;

layout(location = 0) out vec4 FragColor;

const int NUM_OBJECT_TEXTURES = 6;
layout(set = 3, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];

void main()
{
    FragColor = vec4(texture(ObjectTextures[1], vec2(Vertex.TexCoord.x, Vertex.TexCoord.y)).rgb, 1.0);
    //FragColor = vec4(1.0f, 1.0f, 1.0f, 1.0f);
}

