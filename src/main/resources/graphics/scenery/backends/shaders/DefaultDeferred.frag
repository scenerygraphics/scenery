#version 450
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in VertexData {
    vec4 FragPosition;
    vec3 Normal;
    vec2 TexCoord;
} Vertex;

layout(location = 0) out vec4 NormalsMaterial;
layout(location = 1) out vec4 DiffuseAlbedo;
layout(location = 3) out vec4 Emission;
layout(location = 4) out float Reveal;

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 6;

layout(set = 0, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
    mat4 inverseProjectionMatrices[2];
    mat4 headShift;
    float IPD;
    int stereoEnabled;
} vrParameters;

const int MAX_NUM_LIGHTS = 1024;

struct Light {
	float Linear;
	float Quadratic;
	float Intensity;
	float Radius;
	vec4 Position;
  	vec4 Color;
};

layout(set = 1, binding = 0) uniform LightParameters {
    mat4 ViewMatrices[2];
    mat4 InverseViewMatrices[2];
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec3 CamPosition;
};

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Roughness;
    float Metallic;
    float Opacity;
    vec4 Emissive;
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

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

/*
    ObjectTextures[0] - ambient
    ObjectTextures[1] - diffuse
    ObjectTextures[2] - specular
    ObjectTextures[3] - normal
    ObjectTextures[4] - alpha
    ObjectTextures[5] - displacement
*/

layout(set = 4, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];

// courtesy of Christian Schueler - http://www.thetenthplanet.de/archives/1180
mat3 TBN(vec3 N, vec3 position, vec2 uv) {
    vec3 dp1 = dFdx(position);
    vec3 dp2 = dFdy(position);
    vec2 duv1 = dFdx(uv);
    vec2 duv2 = dFdy(uv);

    vec3 dp2Perpendicular = cross(dp2, N);
    vec3 dp1Perpendicular = cross(N, dp1);

    vec3 T = dp2Perpendicular * duv1.x + dp1Perpendicular * duv2.x;
    vec3 B = dp2Perpendicular * duv1.y + dp1Perpendicular * duv2.y;

    float invmax = inversesqrt(max(dot(T, T), dot(B, B)));

    return transpose(mat3(T * invmax, B * invmax, N));
}

/*
Encodes a three component unit vector into a 2 component vector. The z component of the vector is stored, along with
the angle between the vector and the x axis.
*/
vec2 EncodeSpherical(vec3 In) {
    vec2 enc;
    enc.x = atan(In.y, In.x) / PI;
    enc.y = In.z;
    enc = enc * 0.5f + 0.5f;
    return enc;
}

vec2 OctWrap( vec2 v )
{
    vec2 ret;
    ret.x = (1-abs(v.y)) * (v.x >= 0 ? 1.0 : -1.0);
    ret.y = (1-abs(v.x)) * (v.y >= 0 ? 1.0 : -1.0);
    return ret.xy;
}

/*
Encodes a three component vector into a 2 component vector. First, a normal vector is projected onto one of the 8 planes
of an octahedron(|x| + |y| + |z| = 1). Then, the octahedron is orthogonally projected onto the xy plane to form a
square. The half of the octahedron where z is positive is projected directly by equating the z component to 0. The other
hemisphere is unfolded by splitting all edges adjacent to (0, 0, -1). The z component can be recovered while decoding by
using the property |x| + |y| + |z| = 1.
For more, refer to: http://www.vis.uni-stuttgart.de/~engelhts/paper/vmvOctaMaps.pdf.
 */
vec2 EncodeOctaH( vec3 n )
{
    n /= ( abs( n.x ) + abs( n.y ) + abs( n.z ));
    n.xy = n.z >= 0.0 ? n.xy : OctWrap( n.xy );
    n.xy = n.xy * 0.5 + 0.5;
    return n.xy;
}

void main() {
    DiffuseAlbedo.rgb = vec3(0.0f, 0.0f, 0.0f);

    DiffuseAlbedo.rgb = Material.Kd;
    DiffuseAlbedo.a = 0.1f;
    Emission = Material.Emissive;

    NormalsMaterial.ba = vec2(Material.Roughness, Material.Metallic);

    if((materialType & MATERIAL_HAS_AMBIENT) == MATERIAL_HAS_AMBIENT) {
        //DiffuseAlbedo.rgb = texture(ObjectTextures[0], VertexIn.TexCoord).rgb;
    }

    if((materialType & MATERIAL_HAS_DIFFUSE) == MATERIAL_HAS_DIFFUSE) {
        DiffuseAlbedo.rgb = texture(ObjectTextures[1], Vertex.TexCoord).rgb * DiffuseAlbedo.a;
    }

    if((materialType & MATERIAL_HAS_SPECULAR) == MATERIAL_HAS_SPECULAR) {
//        DiffuseAlbedo.a = texture(ObjectTextures[2], Vertex.TexCoord).r;
        NormalsMaterial.b = texture(ObjectTextures[2], Vertex.TexCoord).r;
    }

    if((materialType & MATERIAL_HAS_ALPHAMASK) == MATERIAL_HAS_ALPHAMASK) {
        if(texture(ObjectTextures[4], Vertex.TexCoord).r < 0.1f) {
            discard;
        }
    }
    const float depthZ = -Vertex.FragPosition.z * 20.0f;
    vec4 color = DiffuseAlbedo.rgba;

    const float distWeight = clamp(0.03 / (1e-5 + pow(depthZ / 200, 4.0)), 1e-2, 3e3);

    float alphaWeight = min(1.0, max(max(color.r, color.g), max(color.b, color.a)) * 40.0 + 0.01);
    alphaWeight *= alphaWeight;

    const float weight = alphaWeight * distWeight;

    // GL Blend function: GL_ONE, GL_ONE
    DiffuseAlbedo = color * weight;

    // GL blend function: GL_ZERO, GL_ONE_MINUS_SRC_ALPHA
    Reveal = color.a;
/*
Normals are encoded as Octahedron Normal Vectors, or Spherical Normal Vectors, which saves on storage as well as read/write processing of one
component. If using Spherical Encoding, do not forget to use spherical decode function in DeferredLighting shader.
*/
    vec2 EncodedNormal = EncodeOctaH(Vertex.Normal);
//    vec3 NormalizedNormal = normalize(VertexIn.Normal);
//    vec2 EncodedNormal = EncodeSpherical(NormalizedNormal);


//    if((materialType & MATERIAL_HAS_NORMAL) == MATERIAL_HAS_NORMAL) {
//        vec3 normal = texture(ObjectTextures[3], Vertex.TexCoord).rgb*(255.0/127.0) - (128.0/127.0);
//        normal = TBN(normalize(Vertex.Normal), CamPosition-Vertex.FragPosition, Vertex.TexCoord)*normal;
//
//        EncodedNormal = EncodeOctaH(normal);
//    }

    NormalsMaterial.rg = EncodedNormal;
}
