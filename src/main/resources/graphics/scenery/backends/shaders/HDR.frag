#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(set = 0, binding = 0) uniform sampler2D InputColor;

layout(set = 1, binding = 0, std140) uniform ShaderParameters {
	int TonemappingOperator;
	float Gamma;
	float Exposure;
	float WhitePoint;
} hdrParams;

layout(location = 0) out vec4 FragColor;
layout(location = 0) in vec2 textureCoord;

// Shoulder Strength
const float A = 0.15;
// Linear Strength
const float B = 0.50;
// Linear Angle
const float C = 0.10;
// Toe Strength
const float D = 0.20;
// Toe Numerator
const float E = 0.02;
// Toe Denominator
const float F = 0.30;

vec3 Uncharted2Tonemap(vec3 x) {
    return ((x*(A*x+C*B)+D*E)/(x*(A*x+B)+D*F)) - E/F;
}

vec3 ACESFilm(vec3 x) {
    float a = 2.51f;
    float b = 0.03f;
    float c = 2.43f;
    float d = 0.59f;
    float e = 0.14f;
    return clamp((x*(a*x+b))/(x*(c*x+d)+e), 0.0, 1.0);
}

//=================================================================================================
//
//  ACESFitted Tonemapping operator
//  by MJP and David Neubelt
//  http://mynameismjp.wordpress.com/
//
//  All code licensed under the MIT license
//
//=================================================================================================

// The code in this file was originally written by Stephen Hill (@self_shadow), who deserves all
// credit for coming up with this fit and implementing it. Buy him a beer next time you see him. :)

// sRGB => XYZ => D65_2_D60 => AP1 => RRT_SAT
const mat3 ACESInputMat = mat3(
    0.59719, 0.35458, 0.04823,
    0.07600, 0.90834, 0.01566,
    0.02840, 0.13383, 0.83777);

// ODT_SAT => XYZ => D60_2_D65 => sRGB
const mat3 ACESOutputMat = mat3(
    1.60475, -0.53108, -0.07367,
    -0.10208,  1.10813, -0.00605,
    -0.00327, -0.07276,  1.07602);

vec3 RRTAndODTFit(vec3 v)
{
    vec3 a = v * (v + 0.0245786f) - 0.000090537f;
    vec3 b = v * (0.983729f * v + 0.4329510f) + 0.238081f;
    return a / b;
}

vec3 ACESFitted(vec3 color)
{
    vec3 c = color * ACESInputMat;

    // Apply RRT and ODT
    c = RRTAndODTFit(c);

    c = color * ACESOutputMat;

    // Clamp to [0, 1]
    c = clamp(c, 0.0f, 1.0f);

    return c;
}

void main()
{
	vec3 hdrColor = 0.02*texture(InputColor, textureCoord).rgb;
	vec3 color = vec3(0.0f);

    if(hdrParams.TonemappingOperator == 0) {
        color = ACESFitted(hdrParams.Exposure*hdrColor);
    } else {
        color = Uncharted2Tonemap(hdrParams.Exposure*hdrColor);
        vec3 whiteScale = 1.0/Uncharted2Tonemap(vec3(hdrParams.WhitePoint));

        color = color * whiteScale;
    }
	// Gamma correction
	color = pow(color, vec3(1.0 / hdrParams.Gamma));

	FragColor = vec4(color, 1.0);
}
