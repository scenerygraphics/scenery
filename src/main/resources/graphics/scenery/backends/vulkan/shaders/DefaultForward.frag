#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(set = 4, binding = 0) uniform sampler2D hdrColor;
layout(set = 4, binding = 1) uniform sampler2D depth;

//layout(set = 1, binding = 0, std140) uniform ShaderParameters {
//	float Gamma;
//	float Exposure;
//} hdrParams;

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 6;
const int MAX_NUM_LIGHTS = 128;

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
    float Shininess;
};

layout(location = 0) in VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
} VertexIn;


layout(binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 ViewMatrix;
	mat4 NormalMatrix;
	mat4 ProjectionMatrix;
	vec3 CamPosition;
	int isBillboard;
} ubo;

layout(binding = 1) uniform MaterialProperties {
    MaterialInfo Material;
    int materialType;
};

layout(set = 1, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];

layout(set = 3, binding = 0, std140) uniform LightParameters {
    int numLights;
	Light lights[MAX_NUM_LIGHTS];
};

layout(location = 0) out vec4 FragColor;

void main()
{
	FragColor = vec4(0.8, 0.0, 0.0, 0.2);
}
