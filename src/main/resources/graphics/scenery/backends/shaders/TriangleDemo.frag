#version 450
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) out vec4 FragColor;
layout(location = 0) in vec2 textureCoord;

void main() {
	FragColor = vec4(textureCoord.x, textureCoord.y, 0.0, 1.0);
}
