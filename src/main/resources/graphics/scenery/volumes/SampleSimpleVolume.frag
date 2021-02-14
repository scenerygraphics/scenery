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
uniform sampler2D colorMap;
uniform vec4 slicingPlane;

vec4 sampleVolume( vec4 wpos )
{
    vec3 pos = (im * wpos).xyz + 0.5;

    float rawsample = convert(texture( volume, pos / textureSize( volume, 0 ) ).r);
    float tf = texture(transferFunction, vec2(rawsample + 0.001f, 0.5f)).r;
    vec3 cmapplied = texture(colorMap, vec2(rawsample + 0.001f, 0.5f)).rgb;
    return vec4(cmapplied, tf);
}
