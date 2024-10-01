// sceneGraphVisibility should be in main BDVVolume.frag but doing per
// volume uniforms there is wonky and doing them here in a shader segment works better
uniform int sceneGraphVisibility;

vis = vis && bool(sceneGraphVisibility);
if (vis && step > localNear && step < localFar)
{
    vec4 x = sampleVolume(wpos);

    #if AMBIENT_OCCLUSION
    if(ambientOcclusion && x.a != 0.) {
        float aoRays[numAORays];
        float totalAO = 0;
        for(int ray = 0; ray < numAORays; ray++) {
            aoRays[ray] = 0.;

            aoRays[ray] = (1. - sampleTransferFunction(vec4(wpos.xyz + dirs[ray] * 3 * minVoxelSize, 1)));

            for(int aoStep = 1; aoStep < numAOSteps; aoStep++) {
                aoRays[ray] = (aoRays[ray] + ((aoRays[ray] / aoStep) * (1. - sampleTransferFunction(vec4(wpos.xyz + dirs[ray] * (aoStep + 3) * minVoxelSize, 1)))));
            }

            totalAO += (aoRays[ray] / numAOSteps);
        }

        totalAO /= numAORays;

        #if USE_PRINTF
        if(pixel_coords.xy == debug_pixel) {
            debugPrintfEXT("Step: %d, totalAO: %f", i, totalAO);
        }
            #endif

        x.rgb *= (totalAO);
        //        x.rgb += vec3(totalAO);
        //        x.rgb /= (totalAO);
    }
        #endif
    //    #if USE_PRINTF
    //    if(pixel_coords.xy == debug_pixel) {
    //        debugPrintfEXT("Step: %d, color of sample: (%f, %f, %f, %f) at position: (%f, %f, %f, %f)", i, x.rgba, wpos.xyzw);
    //    }
    //    #endif
    //    if(x != vec4(-1)) {
    float newAlpha = x.a;
    vec3 newColor = x.rgb;

    float adjusted_alpha = adjustOpacity(newAlpha, (distance(wpos, wprev)/standardStepSize));

    v.rgb = v.rgb + (1.0f - v.a) * newColor * adjusted_alpha;
    v.a = v.a + (1.0f - v.a) * adjusted_alpha;

    if(v.a >= 1.0f) {
        break;
    }
}
