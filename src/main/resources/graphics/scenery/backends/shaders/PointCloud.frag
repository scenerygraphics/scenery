#version 450
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in VertexData {
    vec3 FragPosition;
    vec3 Normal;
    vec2 TexCoord;
} Vertex;

layout(location = 0) out vec4 FragColor;

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 6;

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Shininess;
    float Opacity;
};

const int MATERIAL_HAS_DIFFUSE =  0x0001;
const int MATERIAL_HAS_AMBIENT =  0x0002;
const int MATERIAL_HAS_SPECULAR = 0x0004;
const int MATERIAL_HAS_NORMAL =   0x0008;
const int MATERIAL_HAS_ALPHAMASK = 0x0010;

layout(set = 2, binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 NormalMatrix;
	int isBillboard;
} ubo;

layout(set = 3, binding = 0) uniform MaterialProperties {
    int materialType;
    MaterialInfo Material;
};

/*
    ObjectTextures[0] - ambient
    ObjectTextures[1] - diffuse
    ObjectTextures[2] - specular
    ObjectTextures[3] - normal
    ObjectTextures[4] - alpha
    ObjectTextures[5] - displacement
*/

layout(set = 4, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];

void main() {
    if( Vertex.TexCoord.x < 10e-6 && Vertex.TexCoord.y  < 10e-6 ) {
        discard;
    }

    vec2 coord = gl_PointCoord - vec2(0.5);
    if(length(coord) > 0.5) {
        discard;
    }

    FragColor = vec4(Vertex.Normal, 1.0f);
}
