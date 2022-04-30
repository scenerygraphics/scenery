if (vis && step > localNear && step < localFar)
{
    vec4 x = sampleVolume(wpos);
    #if USE_PRINTF
    if(pixel_coords.xy == debug_pixel) {
        debugPrintfEXT("Step: %d, color of sample: (%f, %f, %f, %f) at position: (%f, %f, %f, %f)", i, x.rgba, wpos.xyzw);
    }
    #endif
//    if(x != vec4(-1)) {
        float newAlpha = x.a;
        vec3 newColor = x.rgb;

        float w = adjustOpacity(newAlpha, length(wpos - wprev));

        v.rgb = v.rgb + (1.0f - v.a) * newColor * w;
        v.a = v.a + (1.0f - v.a) * w;

        #if USE_PRINTF
        if(pixel_coords.xy == debug_pixel) {
            debugPrintfEXT("Accumulated color so far: (%f, %f, %f, %f)", v);
        }
        #endif

        if(v.a >= 1.0f) {
            break;
        }
//    } else {
//        #if USE_PRINTF
//        if(pixel_coords.xy == debug_pixel) {
//            debugPrintfEXT("This is an error! -1 has been returned!");
//        }
//        #endif
//    }
}
