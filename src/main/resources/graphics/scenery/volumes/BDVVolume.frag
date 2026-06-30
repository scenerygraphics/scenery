#extension GL_EXT_control_flow_attributes : enable
#extension GL_EXT_debug_printf : enable
#extension SPV_KHR_non_semantic_info : enable
out vec4 FragColor;
uniform vec2 viewportSize;
uniform float shuffleDegree;
uniform float maxOcclusionDistance;
uniform float kernelSize;
uniform int occlusionSteps;
uniform int aoDebug;
uniform vec2 dsp;
uniform float fwnw;
uniform float nw;

uniform sampler3D volumeCache;

// -- comes from CacheSpec -----
uniform vec3 blockSize;
uniform vec3 paddedBlockSize;
uniform vec3 cachePadOffset;

// -- comes from TextureCache --
uniform vec3 cacheSize; // TODO: get from texture!?
uniform mat4 transform;

// BVV combined inverse projection-view matrix, set by setProjectionViewMatrix().
// Used ONLY for per-fragment xf computation — must match the matrix that was
// used to derive the global `xf` un1form. Ray reconstruction for volume sampling
// uses Vertex.inverseView * Vertex.inverseProjection (scenery's matrices) as
// before, because the volume transform pipeline is rooted in scenery's world space.
uniform mat4 ipv;

#pragma scenery verbatim
layout(location = 0) in VertexData {
    vec2 textureCoord;
    mat4 inverseProjection;
    mat4 inverseView;
    mat4 MVP;
} Vertex;

layout(set = 0, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
    mat4 inverseProjectionMatrices[2];
    mat4 headShift;
    float IPD;
    int stereoEnabled;
} vrParameters;

const int MAX_NUM_LIGHTS = 1024;

layout(set = 1, binding = 0) uniform LightParameters {
    mat4 ViewMatrices[2];
    mat4 InverseViewMatrices[2];
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec3 CamPosition;
};

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;
#pragma scenery endverbatim

#extension GL_EXT_debug_printf : enable

// intersect ray with a box
// http://www.siggraph.org/education/materials/HyperGraph/raytrace/rtinter3.htm
void intersectBox( vec3 r_o, vec3 r_d, vec3 boxmin, vec3 boxmax, out float tnear, out float tfar )
{
    // compute intersection of ray with all six bbox planes
    vec3 invR = 1 / r_d;
    vec3 tbot = invR * ( boxmin - r_o );
    vec3 ttop = invR * ( boxmax - r_o );

    // re-order intersections to find smallest and largest on each axis
    vec3 tmin = min(ttop, tbot);
    vec3 tmax = max(ttop, tbot);

    // find the largest tmin and the smallest tmax
    tnear = max( max( tmin.x, tmin.y ), max( tmin.x, tmin.z ) );
    tfar = min( min( tmax.x, tmax.y ), min( tmax.x, tmax.z ) );
}

float adjustOpacity(float a, float modifiedStepLength) {
    return 1.0 - pow((1.0 - a), modifiedStepLength);
}

uniform bool fixedStepSize;
uniform float stepsPerVoxel;

float rand(vec2 co){
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

#define PI 3.14159265359
vec3 randomSpherePoint(vec3 rand) {
    float ang1 = (rand.x + 1.0) * PI; // [-1..1) -> [0..2*PI)
    float u = rand.y; // [-1..1), cos and acos(2v-1) cancel each other out, so we arrive at [-1..1)
    float u2 = u * u;
    float sqrt1MinusU2 = sqrt(1.0 - u2);
    float x = sqrt1MinusU2 * cos(ang1);
    float y = sqrt1MinusU2 * sin(ang1);
    float z = u;
    return vec3(x, y, z);
}

// ---------------------
// $insert{Convert}
// $insert{SampleVolume}
// ---------------------

// Recomputes xf for this fragment's ray using the BVV `ipv` un1form.
//
// The global `xf` un1form is computed in setProjectionViewMatrix() from rays
// through screen centre (NDC xy = 0,0). Under a symmetric desktop frustum this
// is identical for every pixel. Under an asymmetric VR per-eye frustum the
// near/far world-space ratio varies across the panel, so the centre-pixel `xf`
// is wrong for off-axis rays. tw() then returns a slightly incorrect t value,
// and that error oscillates across the true mesh depth as the camera moves by
// even tiny amounts — producing the distance-dependent flicker.
//
// Substituting this fragment's own NDC xy makes tw() exact per-pixel.
// We use the BVV `ipv` un1form (not scenery's separate matrices) because it
// must be consistent with the matrix that setProjectionViewMatrix() used when
// it derived `xf`.
float computeXfForFragment(vec2 fragNdcXY)
{
    // Mirrors setProjectionViewMatrix():
    //   a = ipv * (xy, -1, 1)  near plane
    //   b = ipv * (xy,  0, 1)  mid  plane
    //   c = ipv * (xy,  1, 1)  far  plane
    //   xf = length(b-a) / length(c-a)
    vec4 a = ipv * vec4(fragNdcXY, -1.0, 1.0);  a /= a.w;
    vec4 b = ipv * vec4(fragNdcXY,  0.0, 1.0);  b /= b.w;
    vec4 c = ipv * vec4(fragNdcXY,  1.0, 1.0);  c /= c.w;

    float ac = length(c.xyz - a.xyz);
    float ab = length(b.xyz - a.xyz);

    if (ac < 1e-10) return 0.5;
    return ab / ac;
}

// Same Mobius transform as tw() in MaxDepth.frag, but accepts an explicit xf
// so we can pass the per-fragment value rather than the global centre-pixel one.
float twFrag(float zd, float xfFrag)
{
    float denom = 2.0 * xfFrag * zd - xfFrag - zd + 1.0;
    if (abs(denom) < 1e-10) return 1.0;
    return (xfFrag * zd) / denom;
}

void main()
{
    // Ray reconstruction uses scenery's matrices (Vertex.inverseView *
    // Vertex.inverseProjection), exactly as the original shader did. This is
    // correct for volume sampling because the volume/mesh transforms are all
    // in scenery's world space. Switching to BVV's ipv here caused the Y-axis
    // mirroring because BVV and scenery use different camera transform paths.
    mat4 sceneryIpv = Vertex.inverseView * Vertex.inverseProjection;

    vec2 uv = Vertex.textureCoord * 2.0 - vec2(1.0);

    // Seed rand() from stable integer pixel coordinates, not camera-dependent uv.
    vec2 fragSeed = gl_FragCoord.xy;
    vec3 shuffle = vec3(
    rand(fragSeed),
    rand(fragSeed.yx),
    rand(fragSeed / (fragSeed + 1.0))
    );

    // depthTexUV in [0,1] for direct texture() calls.
    // depthUV in NDC [-1,1] for the currentSceneDepth read.
    vec2 depthTexUV = (vrParameters.stereoEnabled ^ 1) * Vertex.textureCoord
    + vrParameters.stereoEnabled * vec2(
    (Vertex.textureCoord.x / 2.0 + currentEye.eye * 0.5),
    Vertex.textureCoord.y);
    vec2 depthUV = depthTexUV * 2.0 - vec2(1.0);

    vec2 vp = viewportSize - dsp;
    mat4 t = transform;

    // NDC of frag on near and far plane
    vec4 front = vec4( uv, -1, 1 );
    vec4 back  = vec4( uv,  1, 1 );

    // Eye ray in world space, using scenery's combined inverse PV.
    vec4 wfront = sceneryIpv * front;  wfront *= 1.0 / wfront.w;
    vec4 wback  = sceneryIpv * back;   wback  *= 1.0 / wback.w;

    // -- bounding box intersection for all volumes ----------

    // Per-fragment xf so twFrag() linearizes depth correctly for this ray.
    // Uses BVV's ipv (declared above) to stay consistent with how `xf` was derived.
    float xfFrag = computeXfForFragment(uv);

    // Sample raw hardware depth in [0,1] directly — no NDC remap needed,
    // tw() operates on the native [0,1] depth buffer value.
    float hwDepth = texture(InputZBuffer, depthTexUV).x;
    float tmax = twFrag(hwDepth, xfFrag);

    float tnear = 1, tfar = 0;
    float n, f;

    // $repeat:{vis,localNear,localFar,intersectBoundingBox|
    bool vis = false;
    float localNear = 0.0f;
    float localFar  = 0.0f;
    intersectBoundingBox( wfront, wback, n, f );
    f = min( tmax, f );
    if ( n < f )
    {
        localNear = n;
        localFar  = f;
        tnear = min(tnear, max(0, n));
        tfar  = max(tfar, f);
        vis   = true;
    }
    // }$

    // -------------------------------------------------------

    vec4 startNDC = Vertex.MVP * vec4(wfront.xyz, 1.0);

    #ifndef OPENGL
	float currentSceneDepth = texture(InputZBuffer, depthUV).r;
    #else
	float currentSceneDepth = texture(InputZBuffer, depthUV).r * 2.0 - 1.0;
    #endif

    gl_FragDepth = startNDC.w;

    if ( tnear < tfar )
    {
        vec4 fb = wback - wfront;
        int numSteps =
        ( fwnw > 0.00001 )
        ? int( log( ( tfar * fwnw + nw ) / ( tnear * fwnw + nw ) ) / log( 1 + fwnw ) )
        : int( trunc( ( tfar - tnear ) / nw + 1 ) );

        float stepWidth = nw;

        if (fixedStepSize) {
            stepWidth = (2 * nw) / stepsPerVoxel;
            numSteps  = int( trunc( ( tfar - tnear ) / stepWidth + 1 ) );
        }

        // Dither ray start in step-parameter space, not UV space.
        float step = tnear + shuffle.x * shuffleDegree * 0.5 * nw;

        vec4 w_entry = mix(wfront, wback, step);
        float standardStepSize = distance(mix(wfront, wback, step + nw), w_entry);

        // Initialise wprev to the actual first sample world position.
        vec4 wprev = mix(wfront, wback, tnear);

        vec4 v        = vec4(0);
        vec4 previous = vec4(0.0f);

        for ( int i = 0; i < numSteps; ++i )
        {
            vec4 wpos = mix( wfront, wback, step );

            // $insert{Accumulate}
        /*
			inserts something like the following (keys: vis,localNear,localFar,blockTexture,convert)

			if (vis)
			{
				float x = blockTexture(wpos, volumeCache, cacheSize, blockSize, paddedBlockSize, cachePadOffset);
				v = max(v, convert(x));
			}
			*/
            wprev = wpos;

            if (fixedStepSize) {
                step += stepWidth * (1.0f + shuffleDegree * shuffle.x / 2.0f);
            } else {
                step += nw + step * fwnw * (1.0f + shuffleDegree * shuffle.x / 2.0f);
            }
        }
        FragColor = v;

        if (v.w < 0.001f) {
            discard;
        }
    }
    else {
        discard;
    }
}
