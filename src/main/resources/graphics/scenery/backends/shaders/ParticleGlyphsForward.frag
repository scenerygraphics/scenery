#version 450
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in SilhouetteData {
    vec3 Position;
    vec2 TexCoord;
    flat vec3 Center;
    flat vec2 Properties;
    flat vec3 Color;
} SilhouetteCorner;

layout(location = 5) in CameraDataOut {
    mat4 VP;
} Camera;


struct Light {
    float Linear;
    float Quadratic;
    float Intensity;
    float Radius;
    vec4 Position;
    vec4 Color;
};

const int MAX_NUM_LIGHTS = 1024;

layout(set = 0, binding = 0) uniform LightParameters {
    mat4 ViewMatrices[2];
    mat4 InverseViewMatrices[2];
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec3 CamPosition;
    int numLights;
    Light lights[MAX_NUM_LIGHTS];
};

layout(set = 1, binding = 0) uniform Matrices {
    mat4 ModelMatrix;
    mat4 NormalMatrix;
    int isBillboard;
} ubo;

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Roughness;
    float Metallic;
    float Opacity;
};

layout(location = 0) out vec4 FragColor;

/*
The rasterizer interpolates the uv coordinates
this way, the function can calculate a ray between the eye and the frag pos (which is the fragment position at the billboard)
and together with the center of the actual particle and its radius calculate the position, where the ray would hit the sphere

The algorithm was produced by Prof. Gumhold / Tu-Dresden
Pages 26-29: https://tu-dresden.de/ing/informatik/smt/cgv/ressourcen/dateien/lehre/ss-2020/scivis/02_SciVis_Particles.pdf?lang=en (11.2021)
*/
vec3 RaySphereIntersection(in vec3 eye, in vec3 fragPos, in vec3 center, in float radius) {
    float beta = (radius * sqrt(1 - length(SilhouetteCorner.TexCoord) * length(SilhouetteCorner.TexCoord))) / length(eye - center);
    float lambda = 1 / (1 + beta);
    return eye + lambda * (fragPos - eye);
}

void main() {
    //First: Determine if the pixel lies within the radius of the circle, if not, discard
    if(!(length(SilhouetteCorner.TexCoord) * length(SilhouetteCorner.TexCoord) <= 1))
    {
        discard;
    }
    //Second: Color pixel according to lighting (normal calculation + light parameters from scenery's lighting system
    else
    {
        vec3 intersection = RaySphereIntersection(CamPosition, SilhouetteCorner.Position, SilhouetteCorner.Center, SilhouetteCorner.Properties.x);
        vec3 normal = normalize((intersection - SilhouetteCorner.Center));

        //recalculate the depth buffer (quad fragment position -> sphere fragment position), by appliying the view projection matrix and bringing it to clip space
        vec4 intersectionVP = Camera.VP * vec4(intersection, 1.0);
        float depth = (intersectionVP.z / intersectionVP.w);
        gl_FragDepth = depth;

        // lighting calculations (Phonng lighting model)
        // Light pos is hardcoded atm, but should get the information about the lights from scenery and the example itself
        vec3 lightPos = vec3(0.0, 0.0, 0.0);
        vec3 lightColor = vec3(1.0, 1.0, 1.0);
        vec3 lightDir = normalize(lightPos - (normal + SilhouetteCorner.Center));

        float ambientStrength = 0.2;
        vec3 ambient = ambientStrength * lightColor;

        float diff = max(dot(lightDir, normal), 0.0);
        vec3 diffuse = diff * lightColor;

        vec3 viewDir = normalize(CamPosition - intersection);
        vec3 reflectDir = reflect(-lightDir, normal);
        float specularStrength = 0.5;
        float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32);
        vec3 specular = specularStrength * spec * lightColor;

        vec3 result = (ambient + diffuse + specular) * SilhouetteCorner.Color;
        FragColor = vec4(result, 0.1);
    }
}

