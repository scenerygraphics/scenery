#version 400 core

uniform float Gamma = 2.2f;
uniform float Exposure = 1.0f;
uniform sampler2D hdrBuffer;

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
