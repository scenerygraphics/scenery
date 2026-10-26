// sceneGraphVisibility should be in main BDVVolume.frag but doing per
// volume uniforms there is wonky and doing them here in a shader segment works better
uniform int sceneGraphVisibility;

vis = vis && bool(sceneGraphVisibility);
if (vis && step > localNear && step_prev < localFar)
{
    // clamp the current segment to the volume/mesh bounds
    float stepStart = max(step_prev, localNear);
    float stepEnd = min(step, localFar);
    vec4 wstart = mix(wfront, wback, stepStart);
    vec4 wend = mix(wfront, wback, stepEnd);

    vec4 x = sampleVolume(wend, volumeCache, cacheSize, blockSize, paddedBlockSize, cachePadOffset);
    float newAlpha = x.a;
    vec3 newColor = x.rgb;

    float adjusted_alpha = adjustOpacity(newAlpha, (distance(wstart, wend)/standardStepSize));

    v.rgb = v.rgb + (1.0f - v.a) * newColor * adjusted_alpha;
    v.a = v.a + (1.0f - v.a) * adjusted_alpha;

    if(v.a >= 1.0f) {
        break;
    }
}

