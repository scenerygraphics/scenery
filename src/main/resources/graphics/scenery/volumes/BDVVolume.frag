#extension GL_EXT_control_flow_attributes : enable
#extension GL_EXT_debug_printf : enable
#extension SPV_KHR_non_semantic_info : enable
#extension GL_EXT_control_flow_attributes: enable

out vec4 FragColor;
uniform vec2 viewportSize;
uniform float shuffleDegree;
uniform float maxOcclusionDistance;
uniform float kernelSize;
uniform int occlusionSteps;
uniform float falloffBlend;
uniform float falloffHitThreshold;
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

// code from https://github.com/jamieowen/glsl-blend/blob/master/overlay.glsl
float blendOverlay(float base, float blend) {
    return base<0.5?(2.0*base*blend):(1.0-2.0*(1.0-base)*(1.0-blend));
}
vec3 blendOverlay(vec3 base, vec3 blend) {
    return vec3(blendOverlay(base.r,blend.r),blendOverlay(base.g,blend.g),blendOverlay(base.b,blend.b));
}
vec3 blendOverlay(vec3 base, vec3 blend, float opacity) {
    return (blendOverlay(base, blend) * opacity + base * (1.0 - opacity));
}


void main()
{
	mat4 ipv = Vertex.inverseView * Vertex.inverseProjection;
	// frag coord in NDC
	// TODO: Re-introduce dithering
	//	vec2 fragCoord = (vrParameters.stereoEnabled ^ 1) * gl_FragCoord.xy + vrParameters.stereoEnabled * vec2((gl_FragCoord.x/2.0 + currentEye.eye * gl_FragCoord.x/2.0), gl_FragCoord.y);
	//	vec2 viewportSizeActual = (vrParameters.stereoEnabled ^ 1) * viewportSize + vrParameters.stereoEnabled * vec2(viewportSize.x/2.0, viewportSize.y);
	//	vec2 uv = 2 * ( gl_FragCoord.xy + dsp ) / viewportSizeActual - 1;
	vec2 vp = viewportSize - dsp;
	mat4 t = transform;

	vec2 uv = Vertex.textureCoord * 2.0 - vec2(1.0);
	vec3 shuffle = vec3(rand(uv), rand(uv.yx), rand(uv.xy/uv.yx));
	uv = uv + (shuffle.xy * 0.001f * shuffleDegree);

	vec2 depthUV = (vrParameters.stereoEnabled ^ 1) * Vertex.textureCoord + vrParameters.stereoEnabled * vec2((Vertex.textureCoord.x/2.0 + currentEye.eye * 0.5), Vertex.textureCoord.y);
	depthUV = depthUV * 2.0 - vec2(1.0);

	// NDC of frag on near and far plane
	vec4 front = vec4( uv, -1, 1 );
	vec4 back = vec4( uv, 1, 1 );

	// calculate eye ray in world space
	vec4 wfront = ipv * front;
	wfront *= 1 / wfront.w;
	vec4 wback = ipv * back;
	wback *= 1 / wback.w;

	vec4 direc = Vertex.inverseView * normalize(wback-wfront);
	direc.w = 0.0f;

	// -- bounding box intersection for all volumes ----------
	float tnear = 1, tfar = 0, tmax = getMaxDepth( depthUV );
	float n, f;

	// $repeat:{vis,localNear,localFar,intersectBoundingBox|
	bool vis = false;
	float localNear = 0.0f;
	float localFar = 0.0f;
	intersectBoundingBox( wfront, wback, n, f );
	f = min( tmax, f );
	if ( n < f )
	{
		localNear = n;
		localFar = f;
		tnear = min( tnear, max( 0, n ) );
		tfar = max( tfar, f );
		vis = true;
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
		? int ( log( ( tfar * fwnw + nw ) / ( tnear * fwnw + nw ) ) / log ( 1 + fwnw ) )
		: int ( trunc( ( tfar - tnear ) / nw + 1 ) );

		float stepWidth = nw;

		if(fixedStepSize) {
			stepWidth = (2*nw) / stepsPerVoxel;
			numSteps = int ( trunc( ( tfar - tnear ) / stepWidth + 1 ) );
		}

		float step = tnear;
		vec4 w_entry = mix(wfront, wback, step);

		float standardStepSize = distance(mix(wfront, wback, step + nw), w_entry);

		float step_prev = step - stepWidth;
		vec4 wprev = mix(wfront, wback, step_prev);
		vec4 v = vec4( 0 );
        float shadowDist = 0.0f;
        float shadowing = 0.0f;
		vec4 previous = vec4(0.0f);
        bool isHit = false;
        vec3 foo = vec3(0.9, 0.3, 0.2);
		for ( int i = 0; i < numSteps; ++i)
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


			if(fixedStepSize) {
				step += stepWidth * (1.0f+shuffleDegree*shuffle.x/2.0f);
			} else {
				step += nw + step * fwnw * (1.0f+shuffleDegree*shuffle.x/2.0f);
			}
		}
        // use shadow squared as the opacity of the final value
        // to get rid of black artifacts from the shadows
        FragColor = vec4(blendOverlay(v.xyz, vec3(shadowing), falloffBlend), shadowing * shadowing);
		if(v.w < 0.001f) {
            discard;
		}
	}
	else {
        discard;
	}
}
