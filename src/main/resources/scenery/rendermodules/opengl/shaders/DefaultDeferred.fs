#version 400 core

layout (location = 0) out vec3 gPosition;
layout (location = 1) out vec3 gNormal;
layout (location = 2) out vec4 gAlbedoSpec;

in VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
} VertexIn;

layout( location = 0) out vec4 FragColor;

uniform vec3 LightIntensity = vec3(0.8);
uniform float Absorption = 0.5;

uniform mat4 ModelViewMatrix;
uniform mat4 MVP;

uniform vec3 CameraPosition;

float PI = 3.14159265358979323846264;

struct LightInfo {
    vec3 Position;
    vec3 La;
    vec3 Ld;
    vec3 Ls;
};
uniform LightInfo Light;

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Shinyness;
};
uniform MaterialInfo Material;


void main() {
    // Store the fragment position vector in the first gbuffer texture
    gPosition = VertexIn.FragPosition;
    // Also store the per-fragment normals into the gbuffer
    gNormal = normalize(VertexIn.Normal);
    // And the diffuse per-fragment color
    gAlbedoSpec.rgb = Material.Ka;
    // Store specular intensity in gAlbedoSpec's alpha component
    gAlbedoSpec.a = Material.Ks.r * Material.Shinyness;
}
