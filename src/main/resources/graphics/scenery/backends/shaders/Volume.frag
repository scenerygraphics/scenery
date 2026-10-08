#version 450 core
#extension GL_ARB_separate_shader_objects: enable
#extension GL_EXT_control_flow_attributes : enable

layout(set = 6, binding = 0) uniform sampler2D InputZBuffer;

layout(location = 0) in VertexData {
    vec2 textureCoord;
    mat4 inverseProjection;
    mat4 inverseModelView;
    mat4 modelView;
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
layout(set = 4, binding = 0) uniform sampler3D VolumeTextures;

layout(set = 5, binding = 0) uniform ShaderProperties {
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
    float stepSize;
    float alphaBlending;
    float gamma;
    int dataRangeMin;
    int dataRangeMax;
    int renderingMethod;
    float kernelSize;
    int occlusionSteps;
    float maxOcclusionDistance;
    float time;
    int numActiveBlocks;
    vec3 blockPositions[MAX_BLOCKS];
    vec3 blockDimensions;
};

layout(set = 4, binding = 0) uniform sampler3D volumeBlocks[MAX_BLOCKS];

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

const float PI_r = 0.3183098;
const int inverseBaseSamplingRate = 128;

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

uint hash( uint x ) {
    x += ( x << 10u );
    x ^= ( x >>  6u );
    x += ( x <<  3u );
    x ^= ( x >> 11u );
    x += ( x << 15u );
    return x;
}

// Compound versions of the hashing algorithm I whipped together.
uint hash( uvec2 v ) { return hash( v.x ^ hash(v.y)                         ); }
uint hash( uvec3 v ) { return hash( v.x ^ hash(v.y) ^ hash(v.z)             ); }
uint hash( uvec4 v ) { return hash( v.x ^ hash(v.y) ^ hash(v.z) ^ hash(v.w) ); }

// Construct a float with half-open range [0:1] using low 23 bits.
// All zeroes yields 0.0, all ones yields the next smallest representable value below 1.0.
float floatConstruct( uint m ) {
    const uint ieeeMantissa = 0x007FFFFFu; // binary32 mantissa bitmask
    const uint ieeeOne      = 0x3F800000u; // 1.0 in IEEE binary32

    m &= ieeeMantissa;                     // Keep only mantissa bits (fractional part)
    m |= ieeeOne;                          // Add fractional part to 1.0

    float  f = uintBitsToFloat( m );       // Range [1:2]
    return f - 1.0;                        // Range [0:1]
}

// Pseudo-random value in half-open range [0:1].
float random( float x ) { return floatConstruct(hash(floatBitsToUint(x))); }
float random( vec2  v ) { return floatConstruct(hash(floatBitsToUint(v))); }
float random( vec3  v ) { return floatConstruct(hash(floatBitsToUint(v))); }
float random( vec4  v ) { return floatConstruct(hash(floatBitsToUint(v))); }

// sample a value from the color lookup table, stored in the
// normal texture array.
vec4 sampleLUT(float coord) {
    return texture(ObjectTextures[3], vec2(coord, 0.5f));
}

// sample a value from the transfer function texture, stored in the
// diffuse texture array.
float sampleTF(float coord) {
    return texture(ObjectTextures[1], vec2(coord+0.001f, 0.5f)).r;
}

vec3 getGradient(sampler3D volume, vec3 uvw, float kernelSize) {
    const vec3 offset = vec3(kernelSize)/textureSize(volume, 0);

    float v = sampleTF(texture(volume, uvw).r);
    float v0 = sampleTF(texture(volume, uvw + vec3(offset.x, 0.0, 0.0)).r);
    float v1 = sampleTF(texture(volume, uvw + vec3(0.0, offset.y, 0.0)).r);
    float v2 = sampleTF(texture(volume, uvw + vec3(0.0, 0.0, offset.z)).r);

    return vec3(v - v0, v - v1, v - v2);
}

const vec2 poisson16[] = vec2[](
		vec2( -0.94201624,  -0.39906216 ),
		vec2(  0.94558609,  -0.76890725 ),
		vec2( -0.094184101, -0.92938870 ),
		vec2(  0.34495938,   0.29387760 ),
		vec2( -0.91588581,   0.45771432 ),
		vec2( -0.81544232,  -0.87912464 ),
		vec2( -0.38277543,   0.27676845 ),
		vec2(  0.97484398,   0.75648379 ),
		vec2(  0.44323325,  -0.97511554 ),
		vec2(  0.53742981,  -0.47373420 ),
		vec2( -0.26496911,  -0.41893023 ),
		vec2(  0.79197514,   0.19090188 ),
		vec2( -0.24188840,   0.99706507 ),
		vec2( -0.81409955,   0.91437590 ),
		vec2(  0.19984126,   0.78641367 ),
		vec2(  0.14383161,  -0.14100790 )
);

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

    if (!inter.hit || inter.tfar <= 0.0) {
        FragColor = vec4(0.0f, 0.0f, 0.0f, 0.0f);
        gl_FragDepth = texture(InputZBuffer, depthUV).r;
        return;
    }

    const float tnear = max(inter.tnear, 0.0f);
    const float tfar = min(inter.tfar, length(direc0 - orig0));

    const float tstep = stepSize;

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
        // otherwise, we just put a transparent black
        FragColor = vec4(0.0, 0.0, 0.0, 0.0);
        gl_FragDepth = currentSceneDepth;
        return;
    }

    gl_FragDepth = startNDC.z;

    vec3 origin = pos;

    // raycasting loop:
    float maxp = 0.0f;
    float mappedVal = 0.0f;


    float colVal = 0.0;
    float alphaVal = 0.0;
    float newVal = 0.0;

    vec3 lightPos = vec4(0.0, 3.5, -3.0, 1.0).xyz - vec3(0.5);

    float shadowDist = 0.0f;
    float shadowDensity = 0.1f;

    const int steps = min(int(ceil(abs(tfar - tnear)/tstep)), 1024);

    // Local Maximum Intensity Projection
    if(renderingMethod == 0) {
         float opacity = 1.0f;
         for(int i = 0; i <= steps; i++, pos += vecstep) {
              float volumeSample = sampleVolume(pos.xyz).r * dataRangeMax;
              newVal = clamp(ta * volumeSample + tb,0.f,1.f);
              colVal = max(colVal, opacity*newVal);

              opacity *= (1.0 - alphaBlending * sampleTF(clamp(newVal,0.f,1.f))/steps);

              if (opacity <= 0.0f) {
                break;
              }
         }

         alphaVal = 1.0f - opacity;

         // Mapping to transfer function range and gamma correction:
         FragColor = vec4(pow(sampleLUT(colVal).rgb, vec3(gamma)) * alphaVal, alphaVal);
    }
    // Maximum Intensity Projection
    else if(renderingMethod == 1) {
         for(int i = 0; i <= steps; i++, pos += vecstep) {
          float volumeSample = sampleVolume(pos.xyz).r * dataRangeMax;
          float newVal = clamp(ta * volumeSample + tb,0.f,1.f);
          colVal = max(colVal, newVal);
        }


        // Mapping to transfer function range and gamma correction:
        alphaVal = sampleTF(clamp(colVal, 0.0, 1.0));
        FragColor = vec4(pow(sampleLUT(colVal).rgb, vec3(gamma)) * alphaVal, alphaVal);
    } else {
        vec3 color = vec3(0.0f);
        float alpha = 0.0f;
        int osteps = min(occlusionSteps, 16);
        pos += vec3(random(vec3(Vertex.textureCoord.s, Vertex.textureCoord.t, time)))/10000.0f;

         for(int i = 0; i <= steps; i++, pos += vecstep) {
            float rawSample = sampleVolume(pos.xyz).r;
            float volumeSample = rawSample * dataRangeMax;
            float shadowing = 0.0f;
            volumeSample = clamp(ta * volumeSample + tb,0.f,1.f);

            float newAlpha = sampleTF(volumeSample);

            if(newAlpha > 0.0f && osteps > 0) {
                [[unroll]] for(int s = 0; s < osteps; s++) {
                    vec3 lpos = pos + vec3(poisson16[s], (poisson16[s].x + poisson16[s].y)/2.0) * kernelSize;
                    vec3 N = normalize(getGradient(VolumeTextures, lpos, 1.0));
                    vec3 sampleDir = normalize(lpos - pos);

                    float NdotS = max(dot(N, sampleDir), 0.0);
                    float dist = distance(pos, lpos);

                    float a = smoothstep(maxOcclusionDistance, maxOcclusionDistance*2.0, dist);

                    shadowDist += a * NdotS/occlusionSteps;
                }

                shadowing = clamp(shadowDist, 0.0, 1.0);
            }

            vec3 newColor = sampleLUT(volumeSample).rgb * (1.0 - shadowing);

            color = color + (1.0f - alpha) * newColor * newAlpha;
            alpha = alpha + (1.0f - alpha) * newAlpha;

            if(alpha >= 1.0) {
                break;
            }
        }

        // color is alpha pre-multiplied alpha, so we just pass it along here
        FragColor = vec4(color, alpha);
    }
}

vec4 sampleVolume(vec3 texCoord) {
    for (int i = 0; i < numActiveBlocks; i++) {
        vec3 blockMin = blockPositions[i];
        vec3 blockMax = blockMin + blockDimensions;
        if (all(greaterThanEqual(texCoord, blockMin)) && all(lessThan(texCoord, blockMax))) {
            vec3 localTexCoord = (texCoord - blockMin) / blockDimensions;
            return texture(volumeBlocks[i], localTexCoord);
        }
    }
    return vec4(0.0);  // Return transparent if not in any block
}
