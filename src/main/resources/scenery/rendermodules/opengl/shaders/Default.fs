#version 400 core

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

vec4 Phong(vec3 FragPos, vec3 viewPos, vec3 Normal) {
    // Ambient
    float ambientStrength = 0.5f;
    vec3 ambient = ambientStrength * Light.La;

    // Diffuse
    vec3 norm = normalize(Normal);
    vec3 lightDir = normalize(Light.Position - FragPos);
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * Light.Ld;

    // Specular
    float specularStrength = 0.5f;
    vec3 viewDir = normalize(viewPos - FragPos);
    vec3 reflectDir = reflect(-lightDir, norm);
    float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32);
    vec3 specular = specularStrength * spec * Light.Ls;

    vec3 result = (ambient + diffuse + specular) * Material.Ka;
    return vec4(result, 1.0f);
}

void main() {
    FragColor = Phong(VertexIn.FragPosition, CameraPosition, VertexIn.Normal);
}
