if (vis && step > localNear && step < localFar)
{
    vec4 x = sampleVolume(wpos);
    if(x != vec4(-1)) {
        float newAlpha = x.a;
        vec3 newColor = x.rgb;

        float w = adjustOpacity(newAlpha, length(wpos - wprev));

        v.rgb = v.rgb + (1.0f - v.a) * newColor * w;
        v.a = v.a + (1.0f - v.a) * w;

        if(v.a >= 1.0f) {
            break;
        }
    }
}
