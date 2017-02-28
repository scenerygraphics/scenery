#version 400 core

uniform float Gamma = 2.2f;
uniform float Exposure = 1.0f;
uniform sampler2D hdrBuffer;

out vec4 FragColor;
in vec2 textureCoord;

const float A = 0.15;
const float B = 0.50;
const float C = 0.10;
const float D = 0.20;
const float E = 0.02;
const float F = 0.30;
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
    vec3 mapped = Uncharted2Tonemap(Exposure*hdrColor);
    vec3 whiteScale = 1.0/Uncharted2Tonemap(vec3(W));

    vec3 color = mapped*whiteScale;
	// Gamma correction
	color = pow(color, vec3(1.0 / Gamma));

	FragColor = vec4(color, 1.0);
}
