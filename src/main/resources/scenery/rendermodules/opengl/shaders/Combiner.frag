#version 410 core
out vec4 FragColor;
in vec2 textureCoord;

uniform sampler2D leftEye;
uniform sampler2D rightEye;

uniform int vrActive;
uniform int anaglyphActive;

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
