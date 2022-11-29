uniform mat4 im;
uniform vec3 sourcemax;
uniform vec4 slicingPlanes[16];
uniform int slicingMode;
uniform int usedSlicingPlanes;
uniform int skip;

void intersectBoundingBox( vec4 wfront, vec4 wback, out float tnear, out float tfar )
{
    vec4 mfront = im * wfront;
    vec4 mback = im * wback;
    intersectBox( mfront.xyz, (mback - mfront).xyz, vec3( 0, 0, 0 ), sourcemax, tnear, tfar );
}

uniform sampler3D volume;
uniform sampler2D transferFunction;
uniform sampler2D colorMap;

vec4 sampleVolume( vec4 wpos )
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

    float rawsample = convert(texture( volume, pos / textureSize( volume, 0 ) ).r);
    float tf = texture(transferFunction, vec2(rawsample + 0.001f, 0.5f)).r;
    vec3 cmapplied = texture(colorMap, vec2(rawsample + 0.001f, 0.5f)).rgb;

    int intransparent = int( slicing && isInSlice) ;
    return vec4(cmapplied*tf,1) * intransparent + vec4(cmapplied, tf) * (1-intransparent);
}
