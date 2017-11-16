#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(set = 5, binding = 0) uniform sampler2D InputHDRColor;
layout(set = 5, binding = 1) uniform sampler2D InputDepth;

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
    int transparent;
    vec4 atlasSize;
    vec4 fontColor;
    vec4 backgroundColor;
};

layout(location = 0) out vec4 FragColor;

float aastep (float threshold , float value) {
  float afwidth = 0.7 * length ( vec2(dFdx(value), dFdy(value)));
  return smoothstep (threshold-afwidth, threshold+afwidth, value );
}

void main() {
    // Bilinear SDF interpolation by Stefan Gustavson, OpenGL Insights, 2011
    // see https://github.com/OpenGLInsights/OpenGLInsightsCode/tree/master/Chapter%2012%202D%20Shape%20Rendering%20by%20Distance%20Fields/demo

    vec3 rgb = vec3(0.0f, 0.0f, 0.0f);

    float pattern = 0.0f;
    float texw = atlasSize.x;
    float texh = atlasSize.y;

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
    float aascale = 1.0;
    float D00 = texture( ObjectTextures[1], st00 ).r ;
    float D10 = texture( ObjectTextures[1], st00 + vec2 (aascale*oneu , 0.0) ).r;
    float D01 = texture( ObjectTextures[1], st00 + vec2 (0.0 , aascale*onev ) ).r;
    float D11 = texture( ObjectTextures[1], st00 + vec2 (aascale*oneu, aascale*onev ) ).r;

    vec2 D00_10 = vec2 ( D00 , D10 );
    vec2 D01_11 = vec2 ( D01 , D11 );

    vec2 D0_1 = mix ( D00_10 , D01_11 , uvlerp.y);
    float D = mix( D0_1.x , D0_1.y , uvlerp.x);

    pattern = aastep(0.5, D);

    if(transparent == 1) {
        FragColor = vec4(fontColor.rgb, pattern);
    } else {
        rgb = vec3(fontColor.rgb);
        rgb = mix(rgb, backgroundColor.rgb, 1.0-pattern);

        FragColor = vec4(rgb, 1.0);
    }
}
