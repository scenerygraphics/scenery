#version 450
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in SilhouetteData {
    vec3 SilPosition;
    vec3 SilColor;
} SilhouetteCorner;


//layout(location = 2) in ParticleData /*This is the data for one particle*/ {
//    vec3 CenterPosition;
//    vec3 Properties; // .x = radius
//} Particle;

layout(location = 0) out vec4 FragColor;

void main() {
    FragColor = vec4(1.0, 1.0, 1.0, 1.0);
}

