#version 450 core
#extension GL_ARB_separate_shader_objects: enable

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 6;

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
    float Roughness;
    float Metallic;
    float Opacity;
};

layout(location = 0) in VertexData {
    vec4 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
} Vertex;


const int MATERIAL_HAS_DIFFUSE =  0x0001;
const int MATERIAL_HAS_AMBIENT =  0x0002;
const int MATERIAL_HAS_SPECULAR = 0x0004;
const int MATERIAL_HAS_NORMAL =   0x0008;
const int MATERIAL_HAS_ALPHAMASK = 0x0010;

layout(set = 3, binding = 0) uniform MaterialProperties {
    int materialType;
    MaterialInfo Material;
};

layout(set = 4, binding = 0) uniform ShaderProperties {
    int numLines;
    float lineWidth;
    int ticksOnly;
    vec3 gridColor;
    vec3 boundingBoxSize;
};

layout(location = 0) out vec4 FragColor;

void main()
{
    vec3 coord = Vertex.FragPosition.xyz;
    vec2 uv = Vertex.TexCoord;
    vec3 grid = abs(fract(coord*numLines) - 0.5);
    vec3 df = fwidth(coord * numLines);
    vec3 grid3D = clamp((grid - df * (lineWidth - 1.0)) / df, 0.0, 1.0);
    vec3 axis = vec3(1.0, 1.0, 1.0);
    float line = float(length(axis) > 0.0) * pow(grid3D.x, axis.x) * pow(grid3D.y, axis.y) * pow(grid3D.z, axis.z);

    // if only ticks should be display, this'll discard the interior
    // of the bounding box quad completely, apart from the ticks.

    // mix together line colors and black background.
    // everything apart from the lines should be transparent.
    float alpha = 1.0 - line;

    if(ticksOnly > 0) {
        vec2 bl = step(vec2(0.01), uv);
        vec2 tr = step(vec2(0.01), 1.0 - uv);
        alpha *= (1.0 - bl.x * bl.y * tr.x * tr.y);
    }


    if(alpha < 0.0001f) {
        discard;
    }
    FragColor = vec4(gridColor * alpha, Material.Opacity * alpha);
}
