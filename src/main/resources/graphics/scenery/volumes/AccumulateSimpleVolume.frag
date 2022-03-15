if (vis && step > localNear && step < localFar)
{
    vec4 x = sampleVolume(wpos);
    float newAlpha = x.a;
    vec3 newColor = x.rgb;

    v.rgb = v.rgb + (1.0f - v.a) * newColor * newAlpha;
    v.a = v.a + (1.0f - v.a) * newAlpha;

    if(v.a >= 1.0f) {
        break;
    }
}
