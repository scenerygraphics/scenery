#version 450 core

in VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
} VertexIn;

layout( location = 0 ) out vec4 FragColor;

vec3 LightIntensity = vec3(0.8);
float Absorption = 0.5;

layout(binding = 0) uniform Matrices {
	mat4 ModelViewMatrix;
	mat4 ModelMatrix;
	mat4 ProjectionMatrix;
	mat4 MVP;
	vec3 CamPosition;
	int isBillboard;
} ubo;

const float PI = 3.14159265358979323846264;
const int MAX_TEXTURES = 8;

struct LightInfo {
    vec3 Position;
    vec3 La;
    vec3 Ld;
    vec3 Ls;
};

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Shinyness;
};

layout(binding = 1) uniform MaterialProperties {
    MaterialInfo Material;
};

layout(binding = 2) uniform LightProperties {
    LightInfo Light;
};

layout(binding = 3) uniform sampler2D ObjectTextures[MAX_TEXTURES];

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
    FragColor = BlinnPhong(VertexIn.Position, ubo.CamPosition, VertexIn.Normal);
}
