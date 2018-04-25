#version 450 core
#extension GL_ARB_separate_shader_objects: enable

/**
 * Simple FXAA implementation
 * inspired by:
 *  - http://developer.download.nvidia.com/assets/gamedev/files/sdk/11/FXAA_WhitePaper.pdf
 *  - https://github.com/McNopper/OpenGL/blob/master/Example42/shader/fxaa.frag.glsl
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

layout(set = 0, binding = 0) uniform sampler2D InputColor;

layout(set = 1, binding = 0, std140) uniform ShaderParameters {
    int activateFXAA;
    int showEdges;

    float lumaThreshold;
    float minLumaThreshold;
    float mulReduce;
    float minReduce;
    float maxSpan;
    int displayWidth;
    int displayHeight;
} params;

layout(location = 0) out vec4 FragColor;
layout(location = 0) in vec2 textureCoord;

void main()
{

    vec3 sampleCenter = texture(InputColor, textureCoord).rgb;

    if(params.activateFXAA == 0) {
	    FragColor = vec4(sampleCenter, 1.0);
	    return;
    }

    // sample 4-neighborhood of current texture coord
    vec2 texelStep = vec2(1.0/float(params.displayWidth), 1.0/float(params.displayHeight));
    vec3 sampleNW = texture(InputColor, textureCoord + vec2(-1.0, -1.0)*texelStep).rgb;
    vec3 sampleNE = texture(InputColor, textureCoord + vec2(1.0, -1.0)*texelStep).rgb;
    vec3 sampleSW = texture(InputColor, textureCoord + vec2(1.0, -1.0)*texelStep).rgb;
    vec3 sampleSE = texture(InputColor, textureCoord + vec2(1.0, 1.0)*texelStep).rgb;

    // convert all samples to luma representation
    const vec3 rgbToLuma = vec3(0.299, 0.587, 0.114);
    float lumaNW = dot(sampleNW, rgbToLuma);
    float lumaNE = dot(sampleNE, rgbToLuma);
    float lumaSW = dot(sampleSW, rgbToLuma);
    float lumaSE = dot(sampleSE, rgbToLuma);
    float lumaCenter = dot(sampleCenter, rgbToLuma);

    float lumaMin = min(lumaCenter, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    float lumaMax = max(lumaCenter, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));

    // if contrast is lower than maximum local luma times a set threshold, use center sample and return
    if(lumaMax - lumaMin < max(params.minLumaThreshold, lumaMax * params.lumaThreshold)) {
        FragColor = vec4(sampleCenter, 1.0);
        return;
    }

    vec2 sampleDirection;
    sampleDirection.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));
    sampleDirection.y = ((lumaNW + lumaSW) - (lumaNE + lumaSE));

    float sampleDirectionReduced = max((lumaNW + lumaNE + lumaSW + lumaSE) * 0.25 * params.mulReduce, params.minReduce);
    float minSamplingDirectionFactor = 1.0 / (min(abs(sampleDirection.x), abs(sampleDirection.y)) + sampleDirectionReduced);

    // clamp samples to maximum distance, adjust to current texel size
    sampleDirection = clamp(sampleDirection * minSamplingDirectionFactor,
        vec2(-params.maxSpan, -params.maxSpan),
        vec2(params.maxSpan, params.maxSpan)) * texelStep;

    vec3 sampleNegative = texture(InputColor, textureCoord + sampleDirection * (1.0/3.0 - 0.5)).rgb;
    vec3 samplePositive = texture(InputColor, textureCoord + sampleDirection * (2.0/3.0 - 0.5)).rgb;

    vec3 twoTab = (samplePositive + sampleNegative) * 0.5;

    vec3 sampleNegativeOuter = texture(InputColor, textureCoord + sampleDirection * (-0.5)).rgb;
    vec3 samplePositiveOuter = texture(InputColor, textureCoord + sampleDirection * 0.5).rgb;

    vec3 fourTab = (samplePositiveOuter + sampleNegativeOuter) * 0.25 + twoTab * 0.5;

    float lumaFourTab = dot(fourTab, rgbToLuma);

    if(lumaFourTab < lumaMin || lumaFourTab > lumaMax) {
        FragColor = vec4(twoTab, 1.0);
    } else {
        FragColor = vec4(fourTab, 1.0);
    }

    if(params.showEdges != 0) {
        FragColor.r = 1.0;
    }
}
