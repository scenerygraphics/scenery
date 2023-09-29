#define NUM_BLOCK_SCALES 10

uniform mat4 im;
uniform vec3 sourcemin;
uniform vec3 sourcemax;
uniform vec4 slicingPlanes[16];
uniform int slicingMode;
uniform int usedSlicingPlanes;

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
    bool cropping = slicingMode == 1 || slicingMode == 3;
    bool slicing = slicingMode == 2 || slicingMode == 3;

    bool isCropped = false;
    bool isInSlice = false;

    for(int i = 0; i < usedSlicingPlanes; i++){
        vec4 slicingPlane = slicingPlanes[i];
        float dv = slicingPlane.x * wpos.x + slicingPlane.y * wpos.y + slicingPlane.z * wpos.z;

        // compare position to slicing plane
        // negative w inverts the comparision
        isCropped = isCropped || (slicingPlane.w >= 0 && dv > slicingPlane.w) || (slicingPlane.w < 0 && dv < abs(slicingPlane.w));

        float dist = abs(dv - abs(slicingPlane.w)) / length(slicingPlane.xyz);
        isInSlice = isInSlice || dist < 0.02f;
    }

    if (   (!cropping && slicing && !isInSlice)
        || ( cropping && !slicing && isCropped)
        || ( cropping && slicing && !(!isCropped || isInSlice ))){
        return vec4(0);
    }

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

    int intransparent = int( slicing && isInSlice) ;
    return vec4(cmapplied*tf,1) * intransparent + vec4(cmapplied, tf) * (1-intransparent);
}
