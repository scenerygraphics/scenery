#version 450 core
#extension GL_ARB_separate_shader_objects: enable
#extension GL_EXT_control_flow_attributes : enable

layout(location = 0) in VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
} Vertex;

layout(location = 0) out vec4 FragColor;

struct SSBO {
    vec4 Color1;
};

layout(std140, set = 3, binding = 0) readonly buffer ssboUpload{
    SSBO ssboData[];
}ssboUploadBuffer;


void main() {
    //FragColor = ssboUploadBuffer.ssboData[0].Color1;
    FragColor = vec4(1.0, 1.0, 1.0, 1.0);
}
