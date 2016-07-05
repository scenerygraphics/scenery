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
uniform vec3 fontColor;

float aastep (float threshold , float value) {
  float afwidth = 0.7 * length ( vec2(dFdx(value), dFdy(value)));
  // GLSL 's fwidth(value) is abs(dFdx(value)) + abs(dFdy(value))
  return smoothstep (threshold-afwidth, threshold+afwidth, value );
}


void main() {
    // Store the fragment position vector in the first gbuffer texture
    gPosition = VertexIn.FragPosition;
    gNormal = VertexIn.Normal;
    bool debug = false;
    vec3 rgb = vec3(0.0f, 0.0f, 0.0f);
    vec3 bgColor = vec3(1.0f, 1.0f, 1.0f);
    float pattern = 0.0f;
    float texw = 1024.0f;
    float texh = texw;

    float oneu = 1.0f/texw;
    float onev = 1.0f/texh;

    vec2 uv = VertexIn.TexCoord * vec2 ( texw , texh ) ; // Scale to texture rect coords
    vec2 uv00 = floor ( uv - vec2 (0.5) ); // Lower left of lower left texel
    vec2 uvlerp = uv - uv00 - vec2 (0.5) ; // Texel - local blends [0 ,1]
//    uvlerp = uv - 0.5*vec2(oneu, onev);
    // Perform explicit texture interpolation of distance value D.
    // If hardware interpolation is OK , use D = texture2D ( disttex , st).
    // Center st00 on lower left texel and rescale to [0 ,1] for lookup
    vec2 st00 = ( uv00 + vec2 (0.5) ) * vec2 ( oneu , onev );
    // Sample distance D from the centers of the four closest texels
            float aascale = 0.5;
            float D00 = texture ( ObjectTextures[1], st00 ).r ;
            float D10 = texture ( ObjectTextures[1], st00 + vec2 (aascale*oneu , 0.0) ).r;
            float D01 = texture ( ObjectTextures[1], st00 + vec2 (0.0 , aascale*onev )).r;
            float D11 = texture ( ObjectTextures[1], st00 + vec2 (aascale*oneu, aascale*onev )).r;

    if(!debug) {
        if(D00 > 900.0f) {
            discard;
        }

        vec2 D00_10 = vec2 ( D00 , D10 );
        vec2 D01_11 = vec2 ( D01 , D11 );

        vec2 D0_1 = mix ( D00_10 , D01_11 , uvlerp.y) ; // Interpolate along v
        float D = mix( D0_1.x , D0_1.y , uvlerp.x) ; // Interpolate along u

        // Perform anisotropic analytic antialiasing
        // ✬pattern ✬ is 1 where D >0 , 0 where D <0 , with proper AA around D =0.
        float pattern = aastep(0.5, D);
        rgb = vec3(pattern);
        rgb = mix(rgb, fontColor, pattern);
        rgb = mix(bgColor, rgb, pattern);

        if(pattern <= 0.01) {
            discard;
        }
    }
    else {
//        float D = texture ( ObjectTextures[1], st00 ).r;
//        float aastep = fwidth (D);
//        pattern = smoothstep (0.5 - aastep , 0.5+ aastep , D);
//        rgb = vec3(pattern);
//            rgb = vec3(VertexIn.TexCoord.x, VertexIn.TexCoord.y, 0.0f);
           rgb = vec3(texture(ObjectTextures[1], VertexIn.TexCoord).r);
    }

    gAlbedoSpec.rgb = rgb;
}
