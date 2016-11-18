#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(set = 0, binding = 0) uniform sampler2D hdrBuffer;

layout(set = 1, binding = 0) uniform ShaderParameters {
	float Gamma;
	float Exposure;
} hdrParams;

layout(location = 0) out vec4 FragColor;
layout(location = 0) in vec2 textureCoord;

void main()
{
	vec3 hdrColor = texture(hdrBuffer, textureCoord).rgb;

	// Exposure tone mapping
	vec3 mapped = vec3(1.0) - exp(-hdrColor * 1.0);
	// Gamma correction
	mapped = pow(mapped, vec3(1.0 / 3.5));

	FragColor = vec4(mapped, 1.0);
}
