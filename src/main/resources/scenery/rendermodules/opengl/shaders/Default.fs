#version 400 core

in VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
} VertexIn;

layout( location = 0) out vec4 FragColor;

uniform vec3 LightIntensity = vec3(15.0);
uniform float Absorption = 0.5;

uniform mat4 ModelViewMatrix;
uniform mat4 MVP;

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

vec3 ads(vec3 pos, vec3 norm) {
    vec3 n = normalize(norm);
    vec3 s = normalize( vec3(Light.Position) - pos);
    vec3 v = normalize( vec3(-pos));
    vec3 r = reflect(-s, n);

    return vec3(0.5) *
        ( Material.Ka + Material.Kd * max( dot(s, n), 0.0) +
         Material.Ks * pow( max(dot(r,v), 0.0), Material.Shinyness)
        );
}

void main() {
    vec3 ad;
    vec3 spec;

    FragColor = vec4(ads(VertexIn.Position, VertexIn.Normal), 1.0);
}
