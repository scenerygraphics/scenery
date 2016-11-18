#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
} VertexIn;

layout(location = 0) out vec3 gPosition;
layout(location = 1) out vec3 gNormal;
layout(location = 2) out vec4 gAlbedoSpec;

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 5;

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Shininess;
};

const int MATERIAL_TYPE_STATIC = 0;
const int MATERIAL_TYPE_TEXTURED = 1;
const int MATERIAL_TYPE_MAT = 2;
const int MATERIAL_TYPE_TEXTURED_NORMAL = 3;

layout(binding = 0) uniform Matrices {
	mat4 ModelViewMatrix;
	mat4 ModelMatrix;
	mat4 ProjectionMatrix;
	mat4 MVP;
	vec3 CamPosition;
	int isBillboard;
} ubo;

layout(binding = 1) uniform MaterialProperties {
    MaterialInfo Material;
    int materialType;
};

/*
    ObjectTextures[0] - ambient
    ObjectTextures[1] - diffuse
    ObjectTextures[2] - specular
    ObjectTextures[3] - normal
    ObjectTextures[4] - displacement
*/

layout(set = 1, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];

void main() {
    // Store the fragment position vector in the first gbuffer texture
    gPosition = VertexIn.FragPosition;

    // Also store the per-fragment normals into the gbuffer
    if(materialType == MATERIAL_TYPE_TEXTURED_NORMAL) {
        gNormal = normalize(texture(ObjectTextures[3], VertexIn.TexCoord).rgb*2.0 - 1.0);
    } else {
        gNormal = normalize(VertexIn.Normal);
    }
    // And the diffuse per-fragment color
//    if(materialType == MATERIAL_TYPE_MAT) {
//        gAlbedoSpec.rgb = Material.Kd;
//        gAlbedoSpec.a = Material.Ka.r*Material.Shininess;
//    } else if(materialType == MATERIAL_TYPE_STATIC) {
//        gAlbedoSpec.rgb = Material.Kd;
//        gAlbedoSpec.a = Material.Ks.r * Material.Shininess;
//    } else {
        gAlbedoSpec.rgb = texture(ObjectTextures[1], VertexIn.TexCoord).rgb;
        gAlbedoSpec.a = texture(ObjectTextures[2], VertexIn.TexCoord).r;
//    }
}
