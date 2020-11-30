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
};

layout(location = 0) out vec4 FragColor;

void main()
{
    // draw screen-spaced antialiased grid lines, inspired by
    // http://madebyevan.com/shaders/grid - here we scale the incoming
    // coords by the numLines factor. For correct AA, the fwidth argument
    // also has to be scaled by that factor.
    vec2 coord = Vertex.TexCoord;
    vec2 grid = abs(fract(coord*numLines - 0.5) - 0.5) / fwidth(coord*numLines);
    // line width is determined by the minimum gradient, for thicker lines, we
    // divide by lineWidth, lowering the gradient slope.
    float line = min(grid.x, grid.y)/lineWidth;

    // if only ticks should be display, this'll discard the interior
    // of the bounding box quad completely, apart from the ticks.
    if(ticksOnly > 0) {
        if(coord.x > 0.02 && coord.x < 0.98 && coord.y > 0.02 && coord.y < 0.98) {
            discard;
        }
    }

    // mix together line colors and black background.
    // everything apart from the lines should be transparent.
    FragColor = mix(vec4(0.0), vec4(gridColor, Material.Opacity), 1.0 - min(line, 1.0));
}
