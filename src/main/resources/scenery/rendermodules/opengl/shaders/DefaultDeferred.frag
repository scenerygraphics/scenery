#version 400 core

layout (location = 0) out vec3 gPosition;
layout (location = 1) out vec3 gNormal;
layout (location = 2) out vec4 gAlbedoSpec;

in VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
    vec4 Color;
} VertexIn;

uniform mat4 ModelViewMatrix;
uniform mat4 MVP;

uniform vec3 CameraPosition;

float PI = 3.14159265358979323846264;

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Shinyness;
};
uniform MaterialInfo Material;

const int MAX_TEXTURES = 8;
const int MATERIAL_TYPE_STATIC = 0;
const int MATERIAL_TYPE_TEXTURED = 1;
const int MATERIAL_TYPE_MAT = 2;
uniform int materialType = MATERIAL_TYPE_MAT;
uniform sampler2D ObjectTextures[MAX_TEXTURES];

void main() {
    // Store the fragment position vector in the first gbuffer texture
    gPosition = VertexIn.FragPosition;
    // Also store the per-fragment normals into the gbuffer
    gNormal = normalize(VertexIn.Normal);
    // And the diffuse per-fragment color
    if(materialType == MATERIAL_TYPE_MAT) {
        gAlbedoSpec.rgb = Material.Ka;
    } else if(materialType == MATERIAL_TYPE_STATIC) {
        gAlbedoSpec.rgb = VertexIn.Color.rgb;
        gAlbedoSpec.a = Material.Ks.r * Material.Shinyness;
    } else {
        gAlbedoSpec.rgb = texture(ObjectTextures[0], VertexIn.TexCoord).rgb;
        gAlbedoSpec.a = texture(ObjectTextures[1], VertexIn.TexCoord).r;
    }
    // Store specular intensity in gAlbedoSpec's alpha component
}
