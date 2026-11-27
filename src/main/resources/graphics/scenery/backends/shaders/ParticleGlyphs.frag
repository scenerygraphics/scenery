#version 450
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in SilhouetteData {
    vec3 Position;
    vec2 TexCoord;
    flat vec3 Center;
    flat vec2 Properties;
    flat vec3 Color;
} SilhouetteCorner;

layout(location = 5) in CameraDataOut {
    mat4 VP;
} Camera;

layout(location = 0) out vec4 NormalsMaterial;
layout(location = 1) out vec4 DiffuseAlbedo;
layout(location = 2) out vec4 ZBuffer;

struct Light {
    float Linear;
    float Quadratic;
    float Intensity;
    float Radius;
    vec4 Position;
    vec4 Color;
};

const int MAX_NUM_LIGHTS = 1024;

layout(set = 0, binding = 0) uniform LightParameters {
    mat4 ViewMatrices[2];
    mat4 InverseViewMatrices[2];
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec3 CamPosition;
    int numLights;
    Light lights[MAX_NUM_LIGHTS];
};

/*
The rasterizer interpolates the uv coordinates
this way, the function can calculate a ray between the eye and the frag pos (which is the fragment position at the billboard)
and together with the center of the actual particle and its radius calculate the position, where the ray would hit the sphere

The algorithm was produced by Prof. Gumhold / Tu-Dresden
Pages 26-29: https://tu-dresden.de/ing/informatik/smt/cgv/ressourcen/dateien/lehre/ss-2020/scivis/02_SciVis_Particles.pdf?lang=en (11.2021)
*/
vec3 RaySphereIntersection(in vec3 eye, in vec3 fragPos, in vec3 center, in float radius) {
    float beta = (radius * sqrt(1 - length(SilhouetteCorner.TexCoord) * length(SilhouetteCorner.TexCoord))) / length(eye - center);
    float lambda = 1 / (1 + beta);
    return eye + lambda * (fragPos - eye);
}

vec2 OctWrap( vec2 v ) {
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
vec2 EncodeOctaH( vec3 n ) {
    n /= ( abs( n.x ) + abs( n.y ) + abs( n.z ));
    n.xy = n.z >= 0.0 ? n.xy : OctWrap( n.xy );
    n.xy = n.xy * 0.5 + 0.5;
    return n.xy;
}

void main() {
    //First: Check if ray hits sphere
    if(!(length(SilhouetteCorner.TexCoord) * length(SilhouetteCorner.TexCoord) <= 1))
    {
        discard;
    }
    //Second: Color pixel according to lighting (normal calculation + light parameters from scenery's lighting system
    else
    {
        vec3 intersection = RaySphereIntersection(CamPosition, SilhouetteCorner.Position, SilhouetteCorner.Center, SilhouetteCorner.Properties.x);
        vec3 normal = normalize((intersection - SilhouetteCorner.Center));

        //recalculate the depth buffer (quad fragment position -> sphere fragment position), by appliying the view projection matrix and bringing it to clip space
        vec4 intersectionVP = Camera.VP * vec4(intersection, 1.0);
        float depth = (intersectionVP.z / intersectionVP.w);
        gl_FragDepth = depth;

        NormalsMaterial = vec4(EncodeOctaH(normal), 0.8, 0.2);
        DiffuseAlbedo   = vec4(SilhouetteCorner.Color, 1.0);
    }
}

