#version 450
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in SilhouetteData {
    vec3 Position;
    vec2 TexCoord;
    vec3 Color;
    flat vec3 Center;
    flat vec3 Properties;
} SilhouetteCorner;

struct Light {
    float Linear;
    float Quadratic;
    float Intensity;
    float Radius;
    vec4 Position;
    vec4 Color;
};

const int MAX_NUM_LIGHTS = 1024;

layout(set = 1, binding = 0) uniform LightParameters {
    mat4 ViewMatrices[2];
    mat4 InverseViewMatrices[2];
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec3 CamPosition;
    int numLights;
    Light lights[MAX_NUM_LIGHTS];
};

layout(location = 0) out vec4 FragColor;


vec3 RaySphereIntersection(in vec3 eye, in vec3 fragPos, in vec3 center, in float radius)
{
    vec3 direction = normalize(fragPos - eye);
    float beta = (radius * sqrt(1 - length(SilhouetteCorner.TexCoord) * length(SilhouetteCorner.TexCoord))) / length(eye - center);
    float lambda = 1 / (1 + beta);
    return eye + lambda * fragPos;
}

void main() {
    vec3 objColor = vec3(0.2, 0.6, 0.8);
    //First: Check if ray hits sphere
    if(!(length(SilhouetteCorner.TexCoord) * length(SilhouetteCorner.TexCoord) <= 1))
    {
        discard;
    }
    //Second: Color pixel according to lighting (normal calculation + light parameters from scenery's lighting system
    else
    {
        vec3 lightColor = lights[1].Color.xyz;
        lightColor = vec3(1.0, 1.0, 1.0);

        vec3 intersection = RaySphereIntersection(CamPosition, SilhouetteCorner.Position, SilhouetteCorner.Center, SilhouetteCorner.Properties.x);
        vec3 normal = normalize((intersection - SilhouetteCorner.Center) / (length(intersection - SilhouetteCorner.Center)));

        vec3 lightDir = normalize(vec3(5.0, 5.0, 5.0) - intersection);

        float ambientStrength = 0.1;
        vec3 ambient = ambientStrength * lightColor;

        float diff = max(dot(lightDir, normal), 0.0);
        vec3 diffuse = diff * lightColor;
        //diffuse = vec3(0.5, 0.5, 0.5);
        vec3 result = (ambient + diffuse) * objColor;

        FragColor = vec4(result, 1.0);

        /*if(abs(length(intersection - Particle.Position) - Particle.Properties.x) <= 0.1)
        {
            FragColor = vec4(1.0, 1.0, 1.0, 1.0);
        }
        else
        {
            FragColor = vec4(0.2, 0.2, 0.2, 1.0);
        }*/
    }
}

