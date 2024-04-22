// sceneGraphVisibility should be in main BDVVolume.frag but doing per
// volume uniforms there is wonky and doing them here in a shader segment works better
uniform int sceneGraphVisibility;

vis = vis && bool(sceneGraphVisibility);
if (vis && step > localNear && step < localFar)
{
    vec4 x = sampleVolume(wpos, vec3(0.0));

    float newAlpha = x.a;
    vec3 newColor = x.rgb;

    float adjusted_alpha = adjustOpacity(newAlpha, (distance(wpos, wprev)/standardStepSize));

    v.rgb = v.rgb + (1.0f - v.a) * newColor * adjusted_alpha;
    v.a = v.a + (1.0f - v.a) * adjusted_alpha;


    if(x.a > shadowThreshold && occlusionSteps > 0 && !isHit) {

        float d = x.a;
        float d0 = sampleVolume(wpos, vec3(kernelSize, 0.0, 0.0)).a;
        float d1 = sampleVolume(wpos, vec3(0.0, -kernelSize, 0.0)).a;
        float d2 = sampleVolume(wpos, vec3(0.0, 0.0, kernelSize)).a;
        vec3 gradient = vec3(d - d0, d - d1, d - d2);
        vec3 N = normalize(gradient);

        vec3 viewNormal = normalize(ViewMatrices[0] * vec4(N, 0.0)).xyz;
        vec3 sampleDir = vec3(0, 0, 1);
        float NdotV = max(dot(viewNormal, sampleDir), 0.0);

        [[unroll]] for(int s = 0; s < occlusionSteps; s++) {
            vec3 lpos = wpos.rgb + vec3(poisson16[s], (poisson16[s].x + poisson16[s].y) / 2.0) * kernelSize;
            float dist = distance(wpos.rgb, lpos);
            float a = smoothstep(maxOcclusionDistance, maxOcclusionDistance * 2.0, dist);
            shadowDist += a * NdotV/occlusionSteps;
        }

        shadowing = clamp(shadowDist, 0.0, 1.0);
        isHit = true;
    }


    if(v.a >= 1.0f) {
        break;
    }
}
