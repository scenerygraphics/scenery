uniform sampler2D InputZBuffer;
uniform float xf;

float tw( float zd )
{
	return ( xf * zd ) / ( 2 * xf * zd - xf - zd + 1 );
}


float getMaxDepth( vec2 uv )
{
	return tw( texture( InputZBuffer, ( uv + 1 ) / 2 ).x );
	//#ifndef OPENGL
	//    float currentSceneDepth = texture(InputZBuffer, uv).r;
	//#else
	//    float currentSceneDepth = texture(InputZBuffer, uv).r * 2.0 - 1.0;
	//#endif
	//	return tw(currentSceneDepth);
}
