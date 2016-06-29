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

void main() {
    // Store the fragment position vector in the first gbuffer texture
    gPosition = VertexIn.FragPosition;
    // Also store the per-fragment normals into the gbuffer
    if(materialType == MATERIAL_TYPE_TEXTURED_NORMAL) {
        gNormal = normalize(texture(ObjectTextures[3], VertexIn.TexCoord).rgb*2.0 - 1.0);
    } else {
        gNormal = normalize(VertexIn.Normal);
    }
    // And the diffuse per-fragment color
    if(materialType == MATERIAL_TYPE_MAT) {
        gAlbedoSpec.rgb = Material.Kd;
        gAlbedoSpec.a = Material.Ka.r*Material.Shininess;
    } else if(materialType == MATERIAL_TYPE_STATIC) {
        gAlbedoSpec.rgb = VertexIn.Color.rgb;
        gAlbedoSpec.a = Material.Ks.r * Material.Shininess;
    } else {
//        gAlbedoSpec.rgb = texture(ObjectTextures[1], VertexIn.TexCoord).rgb;
/*        float f_gamma = 0.5f;
        float f_buffer = 0.2f;
        vec4 color = vec4(0.01f, 0.01f, 0.01f, 1.0f);
        float dist = texture(ObjectTextures[1], VertexIn.TexCoord).r;
        float alpha = smoothstep(f_buffer - f_gamma, f_buffer + f_gamma, dist);
        if(dist < 0.75) {
            discard;
        }
        gAlbedoSpec.rgb = mix(vec4(1.0, 1.0, 1.0, 0.0), color, alpha).rgb;
        gAlbedoSpec.a = alpha;
*/
        float texw = 64.0f;
        float texh = 64.0f;

        float oneu = 1.0f/texw;
        float onev = 1.0f/texh;

        vec2 uv = VertexIn.TexCoord;// * vec2 ( texw , texh ) ; // Scale to texture rect coords
        vec2 uv00 = floor ( uv - vec2 (0.5) ); // Lower left of lower left texel
        vec2 uvlerp = uv - uv00 - vec2 (0.5); // Texel - local blends [0 ,1]
        // Perform explicit texture interpolation of distance value D.
        // If hardware interpolation is OK , use D = texture2D ( disttex , st).
        // Center st00 on lower left texel and rescale to [0 ,1] for lookup
//        vec2 st00 = ( uv00 + vec2 (0.5) ) * vec2 ( oneu , onev );
        // Sample distance D from the centers of the four closest texels
//        float D00 = texture2D ( disttex , st00 ).r;
//        float D10 = texture2D ( disttex , st00 + vec2 (0.5* oneu , 0.0) ). r;
//        float D01 = texture2D ( disttex , st00 + vec2 (0.0 , 0.5* onev )). r;
//        float D11 = texture2D ( disttex , st00 + vec2 (0.5* oneu ,0.5* onev )). r;

vec2 st00 = ( uv00 + vec2 (0.5) ) * vec2 ( oneu , onev );
st00 = uv;
// Sample distance D from the centers of the four closest texels
/*float D00 = texture ( ObjectTextures[1], st00 ).r;
float D10 = texture ( ObjectTextures[1], st00 + vec2 (0.5* oneu , 0.0) ).r;
float D01 = texture ( ObjectTextures[1], st00 + vec2 (0.0 , 0.5* onev )).r;
float D11 = texture ( ObjectTextures[1], st00 + vec2 (0.5* oneu ,0.5* onev )).r;

vec2 D00_10 = vec2 ( D00 , D10 ) - 0.75f;
vec2 D01_11 = vec2 ( D01 , D11 ) - 0.75f;*/
//        vec4 DD = textureGather(ObjectTextures[1], uv)/32.0f;
//        vec2 D00_10 = vec2(DD.x, DD.w);
//        vec2 D01_11 = vec2(DD.y, DD.z);
float D00 = texture ( ObjectTextures[1], st00 ).r;
float D10 = texture ( ObjectTextures[1], st00 + vec2 (0.5* oneu , 0.0) ).r;
float D01 = texture ( ObjectTextures[1], st00 + vec2 (0.0 , 0.5* onev )).r;
float D11 = texture ( ObjectTextures[1], st00 + vec2 (0.5* oneu ,0.5* onev )).r;

vec2 D00_10 = vec2 ( D00 , D10 )/texw;
vec2 D01_11 = vec2 ( D01 , D11 )/texh;

        vec2 D0_1 = mix ( D00_10 , D01_11 , uvlerp.y) ; // Interpolate along v
        float D = mix( D0_1.x , D0_1.y , uvlerp.x) ; // Interpolate along u
        // Perform anisotropic analytic antialiasing
        float aastep = 0.7 * length ( vec2 ( dFdx (D ) , dFdy (D))) ;
        // ✬pattern ✬ is 1 where D >0 , 0 where D <0 , with proper AA around D =0.
        float pattern = smoothstep (- aastep , aastep , D);
        if(pattern < 1.0f) {
            discard;
        }
        gAlbedoSpec.rgb = vec3 ( 0.0f);
//       float D = texture ( ObjectTextures[1], VertexIn.TexCoord ).r - 0.75f;
//       float aastep = 0.5 * fwidth (D) ;
//       float pattern = smoothstep (- aastep , aastep , D);
//       gAlbedoSpec.rgb = vec3 ( pattern );
//       gAlbedoSpec.a = 0.0f;
    }

//    gTangent = vec3(gAlbedoSpec.a, gNormal.x, gPosition.y);
}
