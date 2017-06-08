#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(set = 0, binding = 0) uniform sampler2D hdrBuffer;

layout(set = 1, binding = 0, std140) uniform ShaderParameters {
	float Gamma;
	float Exposure;
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
// White Point
const float W = 11.2;

const float ExposureBias = 1.0;
const float GammaBias = 2.2;

vec3 Uncharted2Tonemap(vec3 x) {
    return ((x*(A*x+C*B)+D*E)/(x*(A*x+B)+D*F)) - E/F;
}

void main()
{
	vec3 hdrColor = 0.02*texture(hdrBuffer, textureCoord).rgb;

	// Exposure tone mapping
    vec3 mapped = Uncharted2Tonemap(hdrParams.Exposure*hdrColor);
    vec3 whiteScale = 1.0/Uncharted2Tonemap(vec3(W));

    vec3 color = mapped*whiteScale;
	// Gamma correction
	color = pow(color, vec3(1.0 / hdrParams.Gamma));

	FragColor = vec4(color, 1.0);
}
