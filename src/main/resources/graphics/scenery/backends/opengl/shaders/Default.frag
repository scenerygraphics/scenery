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

vec4 BlinnPhong(vec3 FragPos, vec3 viewPos, vec3 Normal) {
      bool blinn = true;
      vec3 color = Material.Kd;
      // Ambient
      vec3 ambient = 0.05 * Material.Ka;

      // Diffuse
      vec3 lightDir = normalize(Light.Position - FragPos);
      vec3 normal = normalize(Normal);
      float diff = max(dot(lightDir, Normal), 0.0);
      vec3 diffuse = diff * color;

      // Specular
      vec3 viewDir = normalize(viewPos - FragPos);
      vec3 reflectDir = reflect(-lightDir, Normal);
      float spec = 0.0;

      if(blinn)
      {
          vec3 halfwayDir = normalize(lightDir + viewDir);
          spec = pow(max(dot(normal, halfwayDir), 0.0), 16.0);
      }

      else
      {
          vec3 reflectDir = reflect(-lightDir, normal);
          spec = pow(max(dot(viewDir, reflectDir), 0.0), 8.0);
      }

      vec3 specular = Light.Ls * spec; // assuming bright white light color
      return vec4(ambient + diffuse + specular, 1.0f);
}

void main() {
    FragColor = BlinnPhong(VertexIn.FragPosition, CameraPosition, VertexIn.Normal);
}
