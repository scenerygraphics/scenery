#version 450 core
#extension GL_ARB_separate_shader_objects: enable
#extension GL_EXT_control_flow_attributes : enable

layout(location = 0) in VertexData {
    vec3 FragPosition;
    vec3 Normal;
    vec2 TexCoord;
} Vertex;

layout(location = 0) out vec4 NormalsMaterial;
layout(location = 1) out vec4 DiffuseAlbedo;

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 6;

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Roughness;
    float Metallic;
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
    int isBillboard;
} ubo;

layout(set = 3, binding = 0) uniform MaterialProperties {
    int materialType;
    MaterialInfo Material;
};

/*struct SSBOIn {
    vec4 Color1;
};
layout(std140, set = 4, binding = 0) readonly buffer ssbosInput
{
    SSBOIn ssboData[];
}ssboInputBuffer;*/

struct SSBOOut {
    vec4 Color1;
    vec4 Color2;
};
layout(std140, set = 4, binding = 0) readonly buffer ssbosOutput
{
    SSBOOut ssboData[];
}ssboOutputBuffer;
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
    //DiffuseAlbedo.r = ssboInputBuffer.ssboData[0].Color1.r;
    //DiffuseAlbedo.gb = ssboOutputBuffer.ssboData[0].Color2.bg;
    DiffuseAlbedo.rgba = ssboOutputBuffer.ssboData[0].Color1.rgba;

    NormalsMaterial.rg = EncodeOctaH(Vertex.Normal);
    NormalsMaterial.ba = vec2(Material.Roughness, Material.Metallic);
}

