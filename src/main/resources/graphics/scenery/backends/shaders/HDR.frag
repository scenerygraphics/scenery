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

void main()
{
	vec3 hdrColor = 0.02*texture(InputColor, textureCoord).rgb;
	vec3 color = vec3(0.0f);

    if(hdrParams.TonemappingOperator == 0) {
        color = ACESFilm(hdrParams.Exposure*hdrColor);
    } else {
        color = Uncharted2Tonemap(hdrParams.Exposure*hdrColor);
        vec3 whiteScale = 1.0/Uncharted2Tonemap(vec3(hdrParams.WhitePoint));

        color = color * whiteScale;
    }
	// Gamma correction
	color = pow(color, vec3(1.0 / hdrParams.Gamma));

	FragColor = vec4(color, 1.0);
}
