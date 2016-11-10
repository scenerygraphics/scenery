#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(set = 1, binding = 0) uniform sampler2D hdrBuffer;

layout(set = 2, binding = 0) uniform HDRParameters {
	float Gamma;
	float Exposure;
} hdrParams;

layout(location = 0) out vec4 color;
layout(location = 0) in vec2 textureCoord;

void main()
{
	/*vec3 hdrColor = texture(hdrBuffer, textureCoord).rgb;

	// Exposure tone mapping
	vec3 mapped = vec3(1.0) - exp(-hdrColor * hdrParams.Exposure);
	// Gamma correction
	mapped = pow(mapped, vec3(1.0 / hdrParams.Gamma));

	color = vec4(mapped, 1.0);
	*/
	color = vec4(0.0f, 1.0f, 0.0f, 1.0f);
//	color = vec4(textureCoord, 0.0f, 1.0f);
}
