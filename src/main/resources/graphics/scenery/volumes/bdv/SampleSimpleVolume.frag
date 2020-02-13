uniform mat4 im;
uniform vec3 sourcemax;

void intersectBoundingBox( vec4 wfront, vec4 wback, out float tnear, out float tfar )
{
    vec4 mfront = im * wfront;
    vec4 mback = im * wback;
    intersectBox( mfront.xyz, (mback - mfront).xyz, vec3( 0, 0, 0 ), sourcemax, tnear, tfar );
}

uniform sampler3D volume;
uniform sampler2D transferFunction;

vec2 sampleVolume( vec4 wpos )
{
    vec3 pos = (im * wpos).xyz + 0.5;
    float vsample = texture( volume, pos / textureSize( volume, 0 ) ).r;
    return vec2(vsample, texture(transferFunction, vec2(vsample + 0.001f, 0.5f)).r);
}
