#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(set = 5, binding = 0) uniform sampler2D InputHDRColor;
layout(set = 5, binding = 1) uniform sampler2D InputDepth;

//layout(set = 1, binding = 0, std140) uniform ShaderParameters {
//	float Gamma;
//	float Exposure;
//} hdrParams;

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
    float Shininess;
    float Opacity;
};

layout(location = 0) in VertexData {
    vec3 Position;
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

layout(set = 4, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];

layout(set = 6, binding = 0) uniform ShaderProperties {
    float atlasWidth;
    float atlasHeight;
    vec3 fontColor;
    vec3 backgroundColor;
    int transparent;
};

layout(location = 0) out vec4 FragColor;

float aastep (float threshold , float value) {
  float afwidth = 0.7 * length ( vec2(dFdx(value), dFdy(value)));
  return smoothstep (threshold-afwidth, threshold+afwidth, value );
}

void main() {
    // Store the fragment position vector in the first gbuffer texture
    vec3 rgb = vec3(0.0f, 0.0f, 0.0f);
    float pattern = 0.0f;
    float texw = atlasWidth;
    float texh = atlasHeight;

    float oneu = 1.0f/texw;
    float onev = 1.0f/texh;

    vec2 uv = Vertex.TexCoord * vec2 ( texw , texh ) ; // Scale to texture rect coords
    vec2 uv00 = floor ( uv - vec2 (0.5) ); // Lower left of lower left texel
    vec2 uvlerp = uv - uv00 - vec2 (0.5) ; // Texel - local blends [0 ,1]

    // Perform explicit texture interpolation of distance value D.
    // If hardware interpolation is OK , use D = texture2D ( disttex , st).
    // Center st00 on lower left texel and rescale to [0 ,1] for lookup
    vec2 st00 = ( uv00 + vec2 (0.5) ) * vec2 ( oneu , onev );
    // Sample distance D from the centers of the four closest texels
    float aascale = 0.5;
    float D00 = textureLod( ObjectTextures[1], st00, 0.0 ).r ;
    float D10 = textureLod( ObjectTextures[1], st00 + vec2 (aascale*oneu , 0.0), 0.0 ).r;
    float D01 = textureLod( ObjectTextures[1], st00 + vec2 (0.0 , aascale*onev ), 0.0 ).r;
    float D11 = textureLod( ObjectTextures[1], st00 + vec2 (aascale*oneu, aascale*onev ), 0.0 ).r;

    vec2 D00_10 = vec2 ( D00 , D10 );
    vec2 D01_11 = vec2 ( D01 , D11 );

    vec2 D0_1 = mix ( D00_10 , D01_11 , uvlerp.y);
    float D = mix( D0_1.x , D0_1.y , uvlerp.x);

    pattern = aastep(0.5, D);
    rgb = vec3(pattern);
    rgb = mix(rgb, fontColor, pattern);

    FragColor = vec4(rgb, pattern);
}
