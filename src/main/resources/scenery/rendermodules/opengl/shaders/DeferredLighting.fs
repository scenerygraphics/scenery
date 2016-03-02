#version 400 core
out vec4 FragColor;
in vec2 textureCoord;

uniform sampler2D gPosition;
uniform sampler2D gNormal;
uniform sampler2D gAlbedoSpec;
uniform sampler2D gDepth;

struct Light {
    vec3 Position;
    vec3 Color;
};

const int NR_LIGHTS = 32;
uniform Light lights[NR_LIGHTS];
uniform vec3 viewPos;

uniform int debugDeferredBuffers = 0;

void main()
{
    // Retrieve data from G-buffer
    vec3 FragPos = texture(gPosition, textureCoord).rgb;
    vec3 Normal = texture(gNormal, textureCoord).rgb;
    vec4 Albedo = texture(gAlbedoSpec, textureCoord).rgba;
    vec3 Depth = texture(gDepth, textureCoord).rrr;

    if(debugDeferredBuffers == 0) {
        float Specular = texture(gAlbedoSpec, textureCoord).a;

        // Then calculate lighting as usual
        vec3 lighting = Albedo.rgb * 0.1; // hard-coded ambient component
        vec3 viewDir = normalize(viewPos - FragPos);

        for(int i = 0; i < NR_LIGHTS; ++i)
        {
            // Diffuse
            vec3 lightDir = normalize(lights[i].Position - FragPos);
            vec3 diffuse = max(dot(Normal, lightDir), 0.0) * Albedo.rgb * 0.1 * lights[i].Color;
            lighting += diffuse;
        }

        FragColor = vec4(lighting, 1.0);
    } else {
        vec2 newTexCoord;
        // color
        if(textureCoord.x < 0.5 && textureCoord.y < 0.5 ) {
            FragColor = Albedo;
        }
        // depth
        if(textureCoord.x > 0.5 && textureCoord.y < 0.5) {
            FragColor = vec4(exp(1.0/Depth), 1.0f);
        }
        // normal
        if(textureCoord.x > 0.5 && textureCoord.y > 0.5) {
            FragColor = vec4(Normal, 1.0f);
        }
        // position
        if(textureCoord.x < 0.5 && textureCoord.y > 0.5) {
            FragColor = vec4(FragPos, 1.0f);
        }
    }
}