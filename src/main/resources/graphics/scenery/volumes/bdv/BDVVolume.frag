out vec4 FragColor;
uniform vec2 viewportSize;
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

// ---------------------
// $insert{SampleVolume}
// $insert{Convert}
// ---------------------

void main()
{
	mat4 ipv = Vertex.inverseView * Vertex.inverseProjection;
	// frag coord in NDC
	// TODO: Re-introduce dithering
	//	vec2 fragCoord = (vrParameters.stereoEnabled ^ 1) * gl_FragCoord.xy + vrParameters.stereoEnabled * vec2((gl_FragCoord.x/2.0 + currentEye.eye * gl_FragCoord.x/2.0), gl_FragCoord.y);
	//	vec2 viewportSizeActual = (vrParameters.stereoEnabled ^ 1) * viewportSize + vrParameters.stereoEnabled * vec2(viewportSize.x/2.0, viewportSize.y);
	//	vec2 uv = 2 * ( gl_FragCoord.xy + dsp ) / viewportSizeActual - 1;
	vec2 uv = Vertex.textureCoord * 2.0 - vec2(1.0);
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

	// -- bounding box intersection for all volumes ----------
	float tnear = 1, tfar = 0, tmax = getMaxDepth( depthUV );
	float n, f;

	// $repeat:{vis,intersectBoundingBox|
	bool vis = false;
	intersectBoundingBox( wfront, wback, n, f );
	f = min( tmax, f );
	if ( n < f )
	{
		tnear = min( tnear, max( 0, n ) );
		tfar = max( tfar, f );
		vis = true;
	}
	// }$

	// -------------------------------------------------------


	if ( tnear < tfar )
	{
		vec4 fb = wback - wfront;
		int numSteps =
		( fwnw > 0.00001 )
		? int ( log( ( tfar * fwnw + nw ) / ( tnear * fwnw + nw ) ) / log ( 1 + fwnw ) )
		: int ( trunc( ( tfar - tnear ) / nw + 1 ) );

		float step = tnear;
		vec4 v = vec4( 0 );
		for ( int i = 0; i < numSteps; ++i, step += nw + step * fwnw )
		{
			vec4 wpos = mix( wfront, wback, step );

			// $insert{Accumulate}
			/*
			inserts something like the following (keys: vis,blockTexture,convert)

			if (vis)
			{
				float x = blockTexture(wpos, volumeCache, cacheSize, blockSize, paddedBlockSize, cachePadOffset);
				v = max(v, convert(x));
			}
			*/
		}
		FragColor = v;
	}
	else
	FragColor = vec4( 0, 0, 0, 0 );
}
