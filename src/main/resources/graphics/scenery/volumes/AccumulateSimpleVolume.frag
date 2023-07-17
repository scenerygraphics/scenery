// sceneGraphVisibility should be in main BDVVolume.frag but doing per
// volume uniforms there is wonky and doing them here in a shader segment works better
uniform int sceneGraphVisibility;

vis = vis && bool(sceneGraphVisibility);
if (vis)
{
    vec4 x = sampleVolume(wpos);

    if(pixel_coords == ivec2(360, 360) && x.a>0) {
        debugPrintfEXT("color of sample: (%f, %f, %f, %f)", x.rgba);
    }

    float newAlpha = x.a;
    vec3 newColor = x.rgb;

    v.rgb = v.rgb + (1.0f - v.a) * newColor * newAlpha;
    v.a = v.a + (1.0f - v.a) * newAlpha;

    if(v.a >= 1.0f) {
        break;
    }
}
