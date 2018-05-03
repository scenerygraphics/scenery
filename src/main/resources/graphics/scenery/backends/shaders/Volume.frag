#version 450 core
#extension GL_ARB_separate_shader_objects: enable
#extension GL_EXT_control_flow_attributes : enable

layout(set = 5, binding = 0) uniform sampler2D InputZBuffer;

layout(location = 0) in VertexData {
    vec2 textureCoord;
    mat4 inverseProjection;
    mat4 inverseModelView;
    mat4 MVP;
} Vertex;

layout(location = 0) out vec4 FragColor;

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 6;
const int MAX_NUM_LIGHTS = 1024;

struct Light {
	float Linear;
	float Quadratic;
	float Intensity;
	float Radius;
	vec4 Position;
  	vec4 Color;
};

layout(set = 0, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
    mat4 inverseProjectionMatrices[2];
    mat4 headShift;
    float IPD;
    int stereoEnabled;
} vrParameters;

layout(set = 1, binding = 0) uniform LightParameters {
    mat4 ViewMatrices[2];
    mat4 InverseViewMatrices[2];
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec3 CamPosition;
};

layout(set = 2, binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 NormalMatrix;
	int isBillboard;
} ubo;

layout(set = 3, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];
layout(set = 3, binding = 1) uniform usampler3D VolumeTextures;

layout(set = 4, binding = 0) uniform ShaderProperties {
    float voxelSizeX;
    float voxelSizeY;
    float voxelSizeZ;
    int sizeX;
    int sizeY;
    int sizeZ;
    float trangemin;
    float trangemax;
    float boxMin_x;
    float boxMin_y;
    float boxMin_z;
    float boxMax_x;
    float boxMax_y;
    float boxMax_z;
    int maxsteps;
    float alpha_blending;
    float gamma;
};

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

float PI_r = 0.3183098;

struct Ray {
    vec3 Origin;
    vec3 Dir;
};

struct AABB {
    vec3 Min;
    vec3 Max;
};

struct Intersection {
    bool hit;
    float tnear;
    float tfar;
};

Intersection intersectBox(vec4 r_o, vec4 r_d, vec4 boxmin, vec4 boxmax)
{
    // compute intersection of ray with all six bbox planes
    vec4 invR = vec4(1.0f,1.0f,1.0f,1.0f) / r_d;
    vec4 tbot = invR * (boxmin - r_o);
    vec4 ttop = invR * (boxmax - r_o);

    // re-order intersections to find smallest and largest on each axis
    vec4 tmin = min(ttop, tbot);
    vec4 tmax = max(ttop, tbot);

    // find the largest tmin and the smallest tmax
    float largest_tmin = max(max(tmin.x, tmin.y), max(tmin.x, tmin.z));
    float smallest_tmax = min(min(tmax.x, tmax.y), min(tmax.x, tmax.z));

	return Intersection(smallest_tmax > largest_tmin, largest_tmin, smallest_tmax);
}

vec3 posFromDepth(vec2 textureCoord) {
    float z = texture(InputZBuffer, textureCoord).r;
    float x = textureCoord.x * 2.0 - 1.0;
    float y = (1.0 - textureCoord.y) * 2.0 - 1.0;
    vec4 projectedPos = Vertex.inverseProjection * vec4(x, y, z, 1.0);

    return projectedPos.xyz/projectedPos.w;
}

vec3 viewFromDepth(float depth, vec2 texcoord) {
    vec2 uv = (vrParameters.stereoEnabled ^ 1) * texcoord + vrParameters.stereoEnabled * vec2((texcoord.x - 0.5 * currentEye.eye) * 2.0, texcoord.y);

	mat4 invProjection = (vrParameters.stereoEnabled ^ 1) * InverseProjectionMatrix + vrParameters.stereoEnabled * vrParameters.inverseProjectionMatrices[currentEye.eye];
	mat4 invView = (vrParameters.stereoEnabled ^ 1) * InverseViewMatrices[0] + vrParameters.stereoEnabled * InverseViewMatrices[currentEye.eye];

#ifndef OPENGL
    vec4 clipSpacePosition = vec4(uv * 2.0 - 1.0, depth, 1.0);
#else
    vec4 clipSpacePosition = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
#endif
    vec4 viewSpacePosition = invProjection * clipSpacePosition;

    viewSpacePosition /= viewSpacePosition.w;
    return viewSpacePosition.xyz;
}

vec3 worldFromDepth(float depth, vec2 texcoord) {
    vec2 uv = (vrParameters.stereoEnabled ^ 1) * texcoord + vrParameters.stereoEnabled * vec2((texcoord.x - 0.5 * currentEye.eye) * 2.0, texcoord.y);

	mat4 invProjection = (vrParameters.stereoEnabled ^ 1) * InverseProjectionMatrix + vrParameters.stereoEnabled * vrParameters.inverseProjectionMatrices[currentEye.eye];
	mat4 invView = (vrParameters.stereoEnabled ^ 1) * InverseViewMatrices[0] + vrParameters.stereoEnabled * InverseViewMatrices[currentEye.eye];

    vec4 clipSpacePosition = vec4(uv * 2.0 - 1.0, depth, 1.0);
    vec4 viewSpacePosition = invProjection * clipSpacePosition;

    viewSpacePosition /= viewSpacePosition.w;
    vec4 world = invView * viewSpacePosition;
    return world.xyz;
}

void main()
{
    // convert range bounds to linear map:
    const float ta = 1.f/(trangemax-trangemin);
    const float tb = trangemin/(trangemin-trangemax);

    // box bounds using the clipping box
    const vec4 boxMin = vec4(boxMin_x,boxMin_y,boxMin_z,1.f);
    const vec4 boxMax = vec4(boxMax_x,boxMax_y,boxMax_z,1.f);

    // thread float coordinates:
    const float u = Vertex.textureCoord.s*2.0 - 1.0;
    const float v = Vertex.textureCoord.t*2.0 - 1.0;

    vec2 depthUV = (vrParameters.stereoEnabled ^ 1) * Vertex.textureCoord + vrParameters.stereoEnabled * vec2((Vertex.textureCoord.x/2.0 + currentEye.eye * 0.5), Vertex.textureCoord.y);
//    vec2 depthUV = Vertex.textureCoord;
    const float depth = texture(InputZBuffer, depthUV).r;
    // front and back:
    const vec4 front = vec4(u,v,0.0f,1.f);
#ifndef OPENGL
    const vec4 back = vec4(u,v,min(1.0f, depth),1.f);
#else
    const vec4 back = vec4(u,v,min(1.0f, depth*2.0 - 1.0),1.f);
#endif

    // calculate eye ray in world space
    vec4 orig0, orig;
    vec4 direc0, direc;

    orig0 = Vertex.inverseProjection * front;
    orig0 *= 1.f/orig0.w;

    orig = Vertex.inverseModelView * orig0;
    orig *= 1.f/orig.w;

    direc0 = Vertex.inverseProjection * back;
    direc0 *= 1.f/direc0.w;

    direc = Vertex.inverseModelView * normalize(direc0-orig0);
    direc.w = 0.0f;

    // find intersection with box
    const Intersection inter = intersectBox(orig, direc, boxMin, boxMax);

    if (!inter.hit || inter.tfar <= 0) {
        FragColor = vec4(0.0f, 0.0f, 0.0f, 0.0f);
        gl_FragDepth = texture(InputZBuffer, depthUV).r;
        return;
    }

    const float tnear = max(inter.tnear, 0.0f);
    const float tfar = min(inter.tfar, length(direc0 - orig0));

    const float tstep = abs(tnear-tfar)/(maxsteps);

    // precompute vectors:
    const vec3 vecstep = 0.5 * tstep * direc.xyz;
    vec3 pos = 0.5 * (1.0 + orig.xyz + tnear * direc.xyz);
    vec3 stop = 0.5 * (1.0 + orig.xyz + tfar * direc.xyz);

    vec4 startNDC = Vertex.MVP * vec4(orig.xyz + tnear * direc.xyz, 1.0);
    startNDC *= 1.0/startNDC.w;

#ifndef OPENGL
    float currentSceneDepth = texture(InputZBuffer, depthUV).r;
#else
    float currentSceneDepth = texture(InputZBuffer, depthUV).r * 2.0 - 1.0;
#endif

    if(startNDC.z > currentSceneDepth && tnear > 0.0f) {
        // for debugging, green = occluded by existing scene geometry
        FragColor = vec4(0.0, 0.0, 0.0, 0.0);
        gl_FragDepth = currentSceneDepth;
        discard;
    }

    vec3 origin = pos;

    // raycasting loop:
    float maxp = 0.0f;
    float mappedVal = 0.0f;


    float colVal = 0.0;
    float alphaVal = 0.0;
    float newVal = 0.0;

    if (alpha_blending <= 0.f){
      gl_FragDepth = 0.0;
      // nop alpha blending
      [[unroll]] for(int i = 0; i < maxsteps; ++i, pos += vecstep) {
        float volume_sample = texture(VolumeTextures, pos.xyz).r;
        maxp = max(maxp,volume_sample);
      }

      colVal = clamp(pow(ta*maxp + tb,gamma),0.f,1.f);
    }
    else{
      // alpha blending:
      float opacity = 1.0f;
      for(int i = 0; i < maxsteps; ++i, pos += vecstep) {
           float volume_sample = texture(VolumeTextures, pos.xyz).r;
           newVal = clamp(ta*volume_sample + tb,0.f,1.f);
           colVal = max(colVal,opacity*newVal);

           opacity  *= (1.f-alpha_blending*clamp(newVal,0.f,1.f));

           if (opacity<=0.02f) {
                break;
           }
      }
    }

    gl_FragDepth = 0.0;

    alphaVal = clamp(colVal, 0.0, 1.0);

    // FIXME: this is a workaround for grey lines appearing at borders
    alphaVal = alphaVal<0.01?0.0f:alphaVal;
    if(alphaVal < 0.01) {
        colVal = 0.01;
    }

    // Mapping to transfer function range and gamma correction:
    FragColor = vec4(texture(ObjectTextures[3], vec2(colVal, 0.5f)).rgb * alphaVal, alphaVal);
}

