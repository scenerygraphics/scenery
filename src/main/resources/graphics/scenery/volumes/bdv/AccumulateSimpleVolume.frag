if (vis)
{
    vec2 x = sampleVolume(wpos);
    float newAlpha = x.y;
    vec3 newColor = convert(x.x).rgb;

    v.rgb = v.rgb + (1.0f - v.a) * newColor * newAlpha;
    v.a = v.a + (1.0f - v.a) * newAlpha;

    if(v.a >= 1.0f) {
        break;
    }
}
