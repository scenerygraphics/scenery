#version 450 core

layout (location = 0) out vec3 gPosition;
layout (location = 1) out vec3 gNormal;
layout (location = 2) out vec4 gAlbedoSpec;
//layout (location = 3) out vec3 gTangent;

in VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
    vec4 Color;
} VertexIn;

layout(binding = 0) uniform Matrices {
	mat4 ModelViewMatrix;
	mat4 MVP;
	vec3 CameraPosition;
};

float PI = 3.14159265358979323846264;

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Shininess;
};

const int MAX_TEXTURES = 8;
const int MATERIAL_TYPE_STATIC = 0;
const int MATERIAL_TYPE_TEXTURED = 1;
const int MATERIAL_TYPE_MAT = 2;
const int MATERIAL_TYPE_TEXTURED_NORMAL = 3;

/*
    ObjectTextures[0] - ambient
    ObjectTextures[1] - diffuse
    ObjectTextures[2] - specular
    ObjectTextures[3] - normal
    ObjectTextures[4] - displacement
*/

layout(binding = 1) uniform UBO {
	MaterialInfo Material;
	int materialType;
};

layout(binding = 2) uniform sampler2D ObjectTextures[MAX_TEXTURES];

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
    if(materialType == MATERIAL_TYPE_MAT) {
        gAlbedoSpec.rgb = Material.Kd;
        gAlbedoSpec.a = Material.Ka.r*Material.Shininess;
    } else if(materialType == MATERIAL_TYPE_STATIC) {
        gAlbedoSpec.rgb = VertexIn.Color.rgb;
        gAlbedoSpec.a = Material.Ks.r * Material.Shininess;
    } else {
        gAlbedoSpec.rgb = texture(ObjectTextures[1], VertexIn.TexCoord).rgb;
        gAlbedoSpec.a = texture(ObjectTextures[2], VertexIn.TexCoord).r;
    }

//    gTangent = vec3(gAlbedoSpec.a, gNormal.x, gPosition.y);
}
