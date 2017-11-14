#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in VertexData {
    vec3 FragPosition;
    vec3 Normal;
    vec2 TexCoord;
} Vertex;

layout(location = 0) out vec3 Position;
layout(location = 1) out vec2 Normal;
layout(location = 2) out vec4 DiffuseAlbedo;

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
	mat4 ProjectionMatrix;
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

vec2 OctWrap( vec2 v )
{
    vec2 ret;
    ret.x = (1-abs(v.y)) * (v.x >= 0 ? 1.0 : -1.0);
    ret.y = (1-abs(v.x)) * (v.y >= 0 ? 1.0 : -1.0);
    return ret.xy;
}

vec2 EncodeOctaH( vec3 n )
{
    n /= ( abs( n.x ) + abs( n.y ) + abs( n.z ));
    n.xy = n.z >= 0.0 ? n.xy : OctWrap( n.xy );
    n.xy = n.xy * 0.5 + 0.5;
    return n.xy;
}

const vec3 fontColor = vec3(0.5f, 0.5f, 0.5f);
const vec3 backgroundColor = vec3(1.0f, 1.0f, 1.0f);
const int transparent = 0;

float aastep (float threshold , float value) {
  float afwidth = 0.7 * length ( vec2(dFdx(value), dFdy(value)));
  return smoothstep (threshold-afwidth, threshold+afwidth, value );
}


void main() {
    // Store the fragment position vector in the first gbuffer texture
    Position = Vertex.FragPosition;
    Normal = EncodeOctaH(Vertex.Normal);

    bool debug = false;
    vec3 rgb = vec3(0.0f, 0.0f, 0.0f);
    float pattern = 0.0f;
    float texw = 1024.0f;
    float texh = texw;

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
    float D00 = texture ( ObjectTextures[1], st00 ).r ;
    float D10 = texture ( ObjectTextures[1], st00 + vec2 (aascale*oneu , 0.0) ).r;
    float D01 = texture ( ObjectTextures[1], st00 + vec2 (0.0 , aascale*onev )).r;
    float D11 = texture ( ObjectTextures[1], st00 + vec2 (aascale*oneu, aascale*onev )).r;

    if(!debug) {
        if(D00 > 900.0f) {
            if(transparent > 0) {
                discard;
            } else {
                DiffuseAlbedo.rgb = backgroundColor;
                return;
            }
        }

        vec2 D00_10 = vec2 ( D00 , D10 );
        vec2 D01_11 = vec2 ( D01 , D11 );

        vec2 D0_1 = mix ( D00_10 , D01_11 , uvlerp.y);
        float D = mix( D0_1.x , D0_1.y , uvlerp.x);

        float pattern = aastep(0.5, D);
        rgb = vec3(pattern);
        rgb = mix(rgb, fontColor, pattern);
        rgb = mix(backgroundColor, rgb, pattern);

        if(pattern <= 0.01 && transparent > 0) {
            discard;
        }
    }
    else {
           rgb = vec3(texture(ObjectTextures[1], Vertex.TexCoord).r);
    }

    DiffuseAlbedo.rgb = rgb;
    DiffuseAlbedo.a = 0.0;
}
