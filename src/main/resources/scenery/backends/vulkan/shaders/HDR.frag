#version 450 core

layout(binding = 0) uniform UBO {
	float Gamma;
	float Exposure;
};

layout(binding = 1) uniform sampler2D hdrBuffer;

out vec4 color;
in vec2 textureCoord;

void main()
{
	vec3 hdrColor = texture(hdrBuffer, textureCoord).rgb;

	// Exposure tone mapping
	vec3 mapped = vec3(1.0) - exp(-hdrColor * Exposure);
	// Gamma correction
	mapped = pow(mapped, vec3(1.0 / Gamma));

	color = vec4(mapped, 1.0);
}
