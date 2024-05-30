#version 450
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in VertexData {
    vec3 FragPosition;
    vec3 Normal;
    vec2 TexCoord;
} Vertex;

layout(location = 0) out vec4 NormalsMaterial;
layout(location = 1) out vec4 DiffuseAlbedo;
layout(location = 2) out vec4 Emission;

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 6;
#define iSteps 16
#define jSteps 2

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

layout(set = 5, binding = 0) uniform ShaderProperties {
    vec3 sunDirection;
};

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


// Courtesy of Rye Terrell, https://github.com/wwwtyro/glsl-atmosphere

//varying vec3 vPosition;

vec2 rsi(vec3 r0, vec3 rd, float sr) {
    // ray-sphere intersection that assumes
    // the sphere is centered at the origin.
    // No intersection when result.x > result.y
    float a = dot(rd, rd);
    float b = 2.0 * dot(rd, r0);
    float c = dot(r0, r0) - (sr * sr);
    float d = (b*b) - 4.0*a*c;
    if (d < 0.0) return vec2(1e5,-1e5);
    return vec2((-b - sqrt(d))/(2.0*a), (-b + sqrt(d))/(2.0*a));
}

vec3 atmosphere(vec3 r, vec3 r0, vec3 pSun, float iSun, float rPlanet, float rAtmos, vec3 kRlh, float kMie, float shRlh, float shMie, float g) {
    // Normalize the sun and view directions.
    pSun = normalize(pSun);
    r = normalize(r);

    // Calculate the step size of the primary ray.
    vec2 p = rsi(r0, r, rAtmos);
    if (p.x > p.y) return vec3(0,0,0);
    p.y = min(p.y, rsi(r0, r, rPlanet).x);
    float iStepSize = (p.y - p.x) / float(iSteps);

    // Initialize the primary ray time.
    float iTime = 0.0;

    // Initialize accumulators for Rayleigh and Mie scattering.
    vec3 totalRlh = vec3(0,0,0);
    vec3 totalMie = vec3(0,0,0);

    // Initialize optical depth accumulators for the primary ray.
    float iOdRlh = 0.0;
    float iOdMie = 0.0;

    // Calculate the Rayleigh and Mie phases.
    float mu = dot(r, pSun);
    float mumu = mu * mu;
    float gg = g * g;
    float pRlh = 3.0 / (16.0 * PI) * (1.0 + mumu);
    float pMie = 3.0 / (8.0 * PI) * ((1.0 - gg) * (mumu + 1.0)) / (pow(1.0 + gg - 2.0 * mu * g, 1.5) * (2.0 + gg));

    // Sample the primary ray.
    for (int i = 0; i < iSteps; i++) {

        // Calculate the primary ray sample position.
        vec3 iPos = r0 + r * (iTime + iStepSize * 0.5);

        // Calculate the height of the sample.
        float iHeight = length(iPos) - rPlanet;

        // Calculate the optical depth of the Rayleigh and Mie scattering for this step.
        float odStepRlh = exp(-iHeight / shRlh) * iStepSize;
        float odStepMie = exp(-iHeight / shMie) * iStepSize;

        // Accumulate optical depth.
        iOdRlh += odStepRlh;
        iOdMie += odStepMie;

        // Calculate the step size of the secondary ray.
        float jStepSize = rsi(iPos, pSun, rAtmos).y / float(jSteps);

        // Initialize the secondary ray time.
        float jTime = 0.0;

        // Initialize optical depth accumulators for the secondary ray.
        float jOdRlh = 0.0;
        float jOdMie = 0.0;

        // Sample the secondary ray.
        for (int j = 0; j < jSteps; j++) {

            // Calculate the secondary ray sample position.
            vec3 jPos = iPos + pSun * (jTime + jStepSize * 0.5);

            // Calculate the height of the sample.
            float jHeight = length(jPos) - rPlanet;

            // Accumulate the optical depth.
            jOdRlh += exp(-jHeight / shRlh) * jStepSize;
            jOdMie += exp(-jHeight / shMie) * jStepSize;

            // Increment the secondary ray time.
            jTime += jStepSize;
        }

        // Calculate attenuation.
        vec3 attn = exp(-(kMie * (iOdMie + jOdMie) + kRlh * (iOdRlh + jOdRlh)));

        // Accumulate scattering.
        totalRlh += odStepRlh * attn;
        totalMie += odStepMie * attn;

        // Increment the primary ray time.
        iTime += iStepSize;

    }

    // Calculate and return the final color.
    return iSun * (pRlh * kRlh * totalRlh + pMie * kMie * totalMie);
}



void main() {
    DiffuseAlbedo.rgb = vec3(0.0f, 0.0f, 0.0f);

    DiffuseAlbedo.rgb = Material.Kd;
    DiffuseAlbedo.a = 0.0f;

    NormalsMaterial.ba = vec2(Material.Roughness, Material.Metallic);

    //if((materialType & MATERIAL_HAS_AMBIENT) == MATERIAL_HAS_AMBIENT) {
    //    //DiffuseAlbedo.rgb = texture(ObjectTextures[0], VertexIn.TexCoord).rgb;
    //}
//
    //if((materialType & MATERIAL_HAS_DIFFUSE) == MATERIAL_HAS_DIFFUSE) {
    //    DiffuseAlbedo.rgb = texture(ObjectTextures[1], Vertex.TexCoord).rgb;
    //}
//
    //if((materialType & MATERIAL_HAS_SPECULAR) == MATERIAL_HAS_SPECULAR) {
    //    DiffuseAlbedo.a = texture(ObjectTextures[2], Vertex.TexCoord).r;
    //    NormalsMaterial.b = texture(ObjectTextures[2], Vertex.TexCoord).r;
    //}
//
    //if((materialType & MATERIAL_HAS_ALPHAMASK) == MATERIAL_HAS_ALPHAMASK) {
    //    if(texture(ObjectTextures[4], Vertex.TexCoord).r < 0.1f) {
    //        discard;
    //    }
    //}
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

    vec3 color = atmosphere(
        normalize(Vertex.FragPosition), // normalized ray direction
        vec3(0,6372e3,0),               // ray origin
        sunDirection,                         // position of the sun
        22.0,                           // intensity of the sun
        6371e3,                         // radius of the planet in meters
        6471e3,                         // radius of the atmosphere in meters
        vec3(5.5e-6, 13.0e-6, 22.4e-6), // Rayleigh scattering coefficient
        21e-6,                          // Mie scattering coefficient
        8e3,                            // Rayleigh scale height
        1.2e3,                          // Mie scale height
        0.758                           // Mie preferred scattering direction
    );

    // Apply exposure.
    color = 1.0 - exp(-1.0 * color);

    //float EmissionStrength = 1.0;

    //DiffuseAlbedo = vec4(0.5, 0.5, 1.0, 1.0);
    DiffuseAlbedo = vec4(color, 1.0);
    Emission.rgb = color;
    Emission.a = Material.Emissive.a;
    //emissive = EmissionStrength; //vec4(color, EmissionStrength);
    NormalsMaterial.rg = EncodedNormal;
}




// https://github.com/wwwtyro/glsl-atmosphere/blob/master/example/example.frag
