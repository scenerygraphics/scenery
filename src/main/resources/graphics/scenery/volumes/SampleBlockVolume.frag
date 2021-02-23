#define NUM_BLOCK_SCALES 10

uniform mat4 im;
uniform vec3 sourcemin;
uniform vec3 sourcemax;

void intersectBoundingBox( vec4 wfront, vec4 wback, out float tnear, out float tfar )
{
    vec4 mfront = im * wfront;
    vec4 mback = im * wback;
    intersectBox( mfront.xyz, (mback - mfront).xyz, sourcemin, sourcemax, tnear, tfar );
}

uniform usampler3D lutSampler;
uniform sampler2D transferFunction;
uniform sampler2D colorMap;
uniform vec3 blockScales[ NUM_BLOCK_SCALES ];
uniform vec3 lutSize;
uniform vec3 lutOffset;

vec4 sampleVolume( vec4 wpos, sampler3D volumeCache, vec3 cacheSize, vec3 blockSize, vec3 paddedBlockSize, vec3 padOffset )
{
    vec3 pos = (im * wpos).xyz + 0.5;

    vec3 q = floor( pos / blockSize ) - lutOffset + 0.5;

    uvec4 lutv = texture( lutSampler, q / lutSize );
    vec3 B0 = lutv.xyz * paddedBlockSize + padOffset;
    vec3 sj = blockScales[ lutv.w ];

    vec3 c0 = B0 + mod( pos * sj, blockSize ) + 0.5 * sj;
    // + 0.5 ( sj - 1 )   + 0.5 for tex coord offset

    float rawsample = convert(texture( volumeCache, c0 / cacheSize ).r);
    float tf = texture(transferFunction, vec2(rawsample + 0.001f, 0.5f)).r;
    vec3 cmapplied = tf * texture(colorMap, vec2(rawsample + 0.001f, 0.5f)).rgb;
    return vec4(cmapplied, tf);
}
