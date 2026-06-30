// sceneGraphVisibility should be in main BDVVolume.frag but doing per
// volume un1forms there is wonky and doing them here in a shader segment works better
uniform int sceneGraphVisibility;

vis = vis && bool(sceneGraphVisibility);
if (vis && step > localNear && step < localFar + nw)
{
    vec4 x = sampleVolume(wpos, volumeCache, cacheSize, blockSize, paddedBlockSize, cachePadOffset);
    float newAlpha = x.a;
    vec3 newColor = x.rgb;

    float stepDist = distance(wpos, wprev);
    float adjusted_alpha = adjustOpacity(newAlpha, (stepDist/standardStepSize));

    // Soft boundary weight: instead of a hard step < localFar cutoff, ramp the
    // last sample's contribution smoothly to zero over one step width as it
    // crosses localFar. This eliminates the frame-to-frame brightness jump that
    // occurs when a sample just barely crosses the hard boundary — which is the
    // root cause of the flickering when moving the camera.
    // The ramp is in t-space: 1.0 when step <= localFar, 0.0 when step >= localFar+nw.
    float boundaryWeight = clamp((localFar + nw - step) / nw, 0.0, 1.0);
    adjusted_alpha *= boundaryWeight;

    v.rgb = v.rgb + (1.0f - v.a) * newColor * adjusted_alpha;
    v.a = v.a + (1.0f - v.a) * adjusted_alpha;

    if(v.a >= 1.0f) {
        break;
    }
}
