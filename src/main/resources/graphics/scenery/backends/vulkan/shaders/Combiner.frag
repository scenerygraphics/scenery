#version 450 core
out vec4 FragColor;
in vec2 textureCoord;

layout(binding = 0) uniform UBO {
	int vrActive;
	int anaglyphActive;
};

layout(binding = 1) uniform sampler2D leftEye;
layout(binding = 2) uniform sampler2D rightEye;

void main() {
	vec3 color;
	if(vrActive < 1) {
		color = texture(leftEye, textureCoord).rgb;
		if (anaglyphActive > 0) {
			color = mix(texture(leftEye, textureCoord).rgb, texture(rightEye, textureCoord).rgb, 0.5f);
		}
	}
	else {
		if(textureCoord.x < 0.5 ) {
			vec2 newTexCoord = vec2(textureCoord.x*2, textureCoord.y);
			color = texture(leftEye, newTexCoord).rgb;
		} else {
			vec2 newTexCoord = vec2((textureCoord.x-0.5)*2, textureCoord.y);
			color = texture(rightEye, newTexCoord).rgb;
		}
	}

	FragColor = vec4(color, 1.0);
}
