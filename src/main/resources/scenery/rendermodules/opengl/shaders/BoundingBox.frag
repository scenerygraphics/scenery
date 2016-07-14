#version 400 core

layout (location = 0) out vec3 gPosition;
layout (location = 1) out vec3 gNormal;
layout (location = 2) out vec4 gAlbedoSpec;
//layout (location = 3) out vec3 gTangent;

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

/*
    ObjectTextures[0] - ambient
    ObjectTextures[1] - diffuse
    ObjectTextures[2] - specular
    ObjectTextures[3] - normal
    ObjectTextures[4] - displacement
*/
uniform sampler2D ObjectTextures[MAX_TEXTURES];
uniform vec3 gridColor;
uniform int numLines;
uniform float lineWidth;


float aastep (float threshold , float value) {
  float afwidth = 0.7 * length ( vec2(dFdx(value), dFdy(value)));
  // GLSL 's fwidth(value) is abs(dFdx(value)) + abs(dFdy(value))
  return smoothstep (threshold-afwidth, threshold+afwidth, value );
}


void main() {
    // Store the fragment position vector in the first gbuffer texture
    gPosition = VertexIn.FragPosition;
    gNormal = VertexIn.Normal;

   bvec2 toDiscard = greaterThan(fract(VertexIn.TexCoord*numLines), vec2(lineWidth, lineWidth));

   if(all(toDiscard)) {
       discard;
   } else {
      gAlbedoSpec.rgb = gridColor;
   }
}
