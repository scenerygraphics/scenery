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

vec3 getUVW(vec3 pos,
    vec3 cacheSize,
    vec3 blockSize,
    vec3 paddedBlockSize,
    vec3 padOffset
) {

    vec3 q = floor( pos / blockSize ) - lutOffset + 0.5;

    uvec4 lutv = texture( lutSampler, q / lutSize );
    vec3 B0 = lutv.xyz * paddedBlockSize + padOffset;
    vec3 sj = blockScales[ lutv.w ];

    vec3 c0 = B0 + mod( pos * sj, blockSize ) + 0.5 * sj;
    // + 0.5 ( sj - 1 )   + 0.5 for tex coord offset

    return c0/cacheSize;
}


vec3 getGradient(float v, sampler3D volumeCache, vec3 pos, float kernelSize,
    vec3 cacheSize,
    vec3 blockSize,
    vec3 paddedBlockSize,
    vec3 padOffset
) {

//    const vec3 offset = vec3(kernelSize)/textureSize(volumeCache, 0);
    const vec3 offset = vec3(kernelSize);///(sourcemax-sourcemin);

    vec3 uvw0 = getUVW(pos + vec3(offset.x, 0.0f, 0.0f), cacheSize, blockSize, paddedBlockSize, padOffset);
    vec3 uvw1 = getUVW(pos + vec3(0.0f, offset.y, 0.0f), cacheSize, blockSize, paddedBlockSize, padOffset);
    vec3 uvw2 = getUVW(pos + vec3(0.0f, 0.0f, offset.z), cacheSize, blockSize, paddedBlockSize, padOffset);

//    debugPrintfEXT("offset=%f, uv0 = %v3f, uv1 = %v3f, uv2 = %v3f", offset, uvw0, uvw1, uvw2);

    float raw = 0.0f;
    raw = convert(texture( volumeCache, uvw0).r);
    float v0 = texture(transferFunction, vec2(raw + 0.000001f, 0.5f)).r;
    raw = convert(texture( volumeCache, uvw1).r);
    float v1 = texture(transferFunction, vec2(raw + 0.000001f, 0.5f)).r;
    raw = convert(texture( volumeCache, uvw2).r);
    float v2 = texture(transferFunction, vec2(raw + 0.000001f, 0.5f)).r;

    return vec3(v - v0, v - v1, v - v2);
}


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
    vec3 uvw = getUVW(pos, cacheSize, blockSize, paddedBlockSize, padOffset);
    float rawsample = convert(texture( volumeCache, uvw ).r);
    float tf = texture(transferFunction, vec2(rawsample + 0.001f, 0.5f)).r;
    vec3 cmapplied = tf * texture(colorMap, vec2(rawsample + 0.001f, 0.5f)).rgb;

    float shadowing = 0.0f;
    float shadowDist = 0.0f;

    if(tf > 0.0f && occlusionSteps > 0) {
        [[unroll]] for(int s = 0; s < occlusionSteps; s++) {
            vec3 lpos = pos + vec3(poisson16[s], (poisson16[s].x + poisson16[s].y)/2.0) * kernelSize;
            vec3 N = normalize(getGradient(tf, volumeCache, lpos, kernelSize, cacheSize,
                blockSize,
                paddedBlockSize,
                padOffset
            ));
            vec3 sampleDir = normalize(lpos - pos);

            float NdotS = max(dot(N, sampleDir), 0.0);
            float dist = distance(pos, lpos);

            float a = smoothstep(0.0f, maxOcclusionDistance*2.0, dist);

            shadowDist += a * NdotS/occlusionSteps;
            if(s == 3) {
//                debugPrintfEXT("N=%v3f NdotS=%f dist=%f lpos=%v3f wpos=%v3f", N, NdotS, dist, lpos, pos);
            }
        }

        shadowing = clamp(shadowDist, 0.0, 1.0);
//            debugPrintfEXT("sd=%f s=%f", shadowDist, shadowing);
    }


//    vec3 color = mix(cmapplied * (1.0f-shadowing), vec3(1.0f-shadowing), float(aoDebug));
    int intransparent = int( slicing && isInSlice) ;
//    return vec4(cmapplied*tf,1) * intransparent + vec4(cmapplied * (1.0f - shadowing), tf * (1.0f-shadowing)) * (1-intransparent);
    return vec4(cmapplied*tf,1) * intransparent + vec4(vec3(1.0f - shadowing), tf * (1.0f-shadowing)) * (1-intransparent);
}
