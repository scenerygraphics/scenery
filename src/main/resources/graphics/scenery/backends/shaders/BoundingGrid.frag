#version 450 core
#extension GL_ARB_separate_shader_objects: enable

//layout(set = 5, binding = 0) uniform sampler2D InputHDRColor;
//layout(set = 5, binding = 1) uniform sampler2D InputDepth;

//layout(set = 1, binding = 0, std140) uniform ShaderParameters {
//	float Gamma;
//	float Exposure;
//} hdrParams;

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
    vec2 TexCoord;
    vec3 FragPosition;
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

layout(set = 4, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];

layout(location = 0) out vec4 FragColor;

void main()
{
    vec3 diffuse = Material.Kd;
    float specular = Material.Shininess;
    vec3 normal;

    FragColor = vec4(diffuse, Material.Opacity);
}
