#version 400 core

layout (location = 0) out vec3 gPosition;
layout (location = 1) out vec3 gNormal;
layout (location = 2) out vec4 gAlbedoSpec;

in VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
    vec4 Color;
} VertexIn;

uniform mat4 ModelViewMatrix;
uniform mat4 MVP;

uniform vec3 CameraPosition;

float PI = 3.14159265358979323846264;

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Shininess;
};
uniform MaterialInfo Material;

const int MAX_TEXTURES = 8;
const int MATERIAL_TYPE_STATIC = 0;
const int MATERIAL_TYPE_TEXTURED = 1;
const int MATERIAL_TYPE_MAT = 2;
const int MATERIAL_TYPE_TEXTURED_NORMAL = 3;
uniform int materialType = MATERIAL_TYPE_MAT;

uniform sampler2D ObjectTextures[MAX_TEXTURES];
uniform vec3 gridColor;
uniform int numLines;
uniform float lineWidth;
uniform int ticksOnly;

void main() {
    gPosition = VertexIn.FragPosition;
    gNormal = VertexIn.Normal;

    // draw screen-spaced antialiased grid lines, inspired by
    // http://madebyevan.com/shaders/grid - here we scale the incoming
    // coords by the numLines factor. For correct AA, the fwidth argument
    // also has to be scaled by that factor.
    vec2 coord = VertexIn.TexCoord;
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

    if(line > 0.999) {
      discard;
    }

    // lines should be rendered only with diffuse color, without specularity
    gAlbedoSpec.rgb = gridColor;
    gAlbedoSpec.a = 0.0;
}
