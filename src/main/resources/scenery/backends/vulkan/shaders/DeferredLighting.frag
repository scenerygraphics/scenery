#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in vec2 textureCoord;
layout(location = 0) out vec4 FragColor;

struct Light {
	float Linear;
	float Quadratic;
	float Intensity;
	vec4 Position;
  	vec4 Color;
};

const int MAX_NUM_LIGHTS = 64;

layout(binding = 0) uniform Matrices {
	mat4 ModelViewMatrix;
	mat4 ModelMatrix;
	mat4 ProjectionMatrix;
	mat4 MVP;
	vec3 CamPosition;
	int isBillboard;
} ubo;

layout(set = 1, binding = 0) uniform sampler2D gPosition;
layout(set = 1, binding = 1) uniform sampler2D gNormal;
layout(set = 1, binding = 2) uniform sampler2D gAlbedoSpec;
layout(set = 1, binding = 3) uniform sampler2D gDepth;

layout(set = 2, binding = 0, std140) uniform LightParameters {
    int numLights;
	Light lights[MAX_NUM_LIGHTS];
};

layout(set = 3, binding = 0, std140) uniform ShaderParameters {
	int debugBuffers;
	int activateSSAO;
	float ssaoDistanceThreshold;
	vec2 ssaoFilterRadius;
};

const vec2 poisson16[] = vec2[](    // These are the Poisson Disk Samples
		vec2( -0.94201624,  -0.39906216 ),
		vec2(  0.94558609,  -0.76890725 ),
		vec2( -0.094184101, -0.92938870 ),
		vec2(  0.34495938,   0.29387760 ),
		vec2( -0.91588581,   0.45771432 ),
		vec2( -0.81544232,  -0.87912464 ),
		vec2( -0.38277543,   0.27676845 ),
		vec2(  0.97484398,   0.75648379 ),
		vec2(  0.44323325,  -0.97511554 ),
		vec2(  0.53742981,  -0.47373420 ),
		vec2( -0.26496911,  -0.41893023 ),
		vec2(  0.79197514,   0.19090188 ),
		vec2( -0.24188840,   0.99706507 ),
		vec2( -0.81409955,   0.91437590 ),
		vec2(  0.19984126,   0.78641367 ),
		vec2(  0.14383161,  -0.14100790 )
);

vec3 calculatePosition(vec2 texCoord, float depth) {
	vec4 pos = inverse(ubo.ProjectionMatrix) * vec4(texCoord.x * 2 - 1, texCoord.y * 2 - 1, depth * 2 - 1, 1);
	return pos.xyz;
}

void main()
{
	// Retrieve data from G-buffer
	vec3 FragPos = texture(gPosition, textureCoord).rgb;
	vec3 Normal = texture(gNormal, textureCoord).rgb;
	vec4 Albedo = texture(gAlbedoSpec, textureCoord).rgba;
	float Specular = texture(gAlbedoSpec, textureCoord).a;
	float Depth = texture(gDepth, textureCoord).r;

	float fragDist = length(FragPos - ubo.CamPosition);

	vec3 lighting;

	if(debugBuffers == 0) {
		if(activateSSAO > 0) {
			float ambientOcclusion = 0.0f;

			int sample_count = 8;
			for (int i = 0; i < sample_count;  ++i) {
				// sample at an offset specified by the current Poisson-Disk sample and scale it by a radius (has to be in Texture-Space)
				vec2 sampleTexCoord = textureCoord + (poisson16[i] * (ssaoFilterRadius));
				float sampleDepth = texture(gDepth, sampleTexCoord).r;
				vec3 samplePos = texture(gPosition, sampleTexCoord).rgb;//calculatePosition(sampleTexCoord, sampleDepth);

				vec3 sampleDir = normalize(samplePos - FragPos);

				// angle between SURFACE-NORMAL and SAMPLE-DIRECTION (vector from SURFACE-POSITION to SAMPLE-POSITION)
				float NdotS = max(dot(Normal, sampleDir), 0.0);
				// distance between SURFACE-POSITION and SAMPLE-POSITION
				float VPdistSP = distance(FragPos, samplePos);

				// a = distance function
				float a = 1.0 - smoothstep(ssaoDistanceThreshold, ssaoDistanceThreshold * 2, VPdistSP);
				// b = dot-Product
				float b = NdotS;

				ambientOcclusion += (a * b);
			}

			lighting = Albedo.rgb * (1.0 - (ambientOcclusion/sample_count));
		} else {
			lighting = Albedo.rgb * 1.0;
		}
		vec3 viewDir = normalize(ubo.CamPosition - FragPos);

		for(int i = 0; i < numLights; ++i)
		{
			// Diffuse
			vec3 lightDir = normalize(lights[i].Position.rgb - FragPos);
			vec3 diffuse = max(dot(Normal, lightDir), 0.0) * lights[i].Intensity * Albedo.rgb * lights[i].Color.rgb;
			float distance = length(lights[i].Position.rgb - FragPos);
			vec3 specular = lights[i].Color.rgb * Specular;

			float attenuation = 1.0 / (1.0 + lights[i].Linear * distance + lights[i].Quadratic * distance * distance);
			diffuse *= attenuation;
			specular *= attenuation;
			lighting += diffuse + specular;
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
		    float near = 0.5f;
		    float far = 1000.0f;
		    vec3 linearizedDepth = vec3((2.0f * near) / (far + near - Depth * (far - near)));
			FragColor = vec4(linearizedDepth, 1.0f);
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
