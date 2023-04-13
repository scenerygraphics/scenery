#version 450 core
#extension GL_ARB_separate_shader_objects: enable

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 6;

struct Light {
    float Linear;
    float Quadratic;
    float Intensity;
    vec4 Position;
    vec4 Color;
};

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Roughness;
    float Metallic;
    float Opacity;
};

layout(location = 0) in VertexData {
    vec3 Position;
    vec3 Normal;
    vec4 Color;
} Vertex;

const int MATERIAL_HAS_DIFFUSE =  0x0001;
const int MATERIAL_HAS_AMBIENT =  0x0002;
const int MATERIAL_HAS_SPECULAR = 0x0004;
const int MATERIAL_HAS_NORMAL =   0x0008;
const int MATERIAL_HAS_ALPHAMASK = 0x0010;

layout(set = 3, binding = 0) uniform MaterialProperties {
    int materialType;
    MaterialInfo Material;
};

layout(set = 4, binding = 0) uniform ShaderProperties {
    vec4 startColor;
    vec4 endColor;
    vec4 lineColor;
    int capLength;
    int vertexCount;
    float edgeWidth;
};

layout(location = 0) out vec4 FragColor;

void main()
{
    // mix together line colors and black background.
    // everything apart from the lines should be transparent.
    FragColor = vec4(Vertex.Color);
}
