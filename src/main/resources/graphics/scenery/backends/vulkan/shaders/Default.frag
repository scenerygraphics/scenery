#version 450 core
#extension GL_ARB_separate_shader_objects: enable

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 5;
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

layout(set = 1, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];

layout(set = 2, binding = 0, std140) uniform LightParameters {
    int numLights;
	Light lights[MAX_NUM_LIGHTS];
};

layout(location = 0) out vec4 FragColor;

vec4 BlinnPhong(vec3 FragPos, vec3 viewPos, vec3 Normal, vec3 a, vec3 d, vec3 s) {
      bool blinn = true;
      vec3 color = d;
      // Ambient
      vec3 ambient = 0.05 * a;
      vec3 diffuse = vec3(0.0f);
      vec3 specular = vec3(0.0f);

      for(int i = 0; i < numLights; ++i) {
          // Diffuse
          vec3 lightDir = normalize(lights[i].Position.xyz - FragPos);
          vec3 normal = normalize(Normal);
          float diff = max(dot(lightDir, Normal), 0.0);
          diffuse += diff * color;

          // Specular
          vec3 viewDir = normalize(viewPos - FragPos);
          vec3 reflectDir = reflect(-lightDir, Normal);
          float spec = s.r;

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

          specular = lights[i].Color.rgb * lights[i].Intensity * spec; // assuming bright white light color
      }

      return vec4(ambient + diffuse + specular, 1.0f);
}

void main() {
    vec3 ambient = texture(ObjectTextures[0], VertexIn.TexCoord).rgb;
    vec3 diffuse = texture(ObjectTextures[1], VertexIn.TexCoord).rgb;
    vec3 specular = texture(ObjectTextures[2], VertexIn.TexCoord).rgb;

    FragColor = BlinnPhong(VertexIn.FragPosition, ubo.CamPosition, VertexIn.Normal,
        ambient, diffuse, specular);
}
