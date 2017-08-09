#version 450 core
#extension GL_ARB_separate_shader_objects: enable

#define PI 3.14159265359

layout(location = 0) in vec2 textureCoord;
layout(location = 0) out vec4 FragColor;

layout(set = 2, binding = 0) uniform sampler2D Position;
layout(set = 2, binding = 1) uniform sampler2D Normal;
layout(set = 2, binding = 2) uniform sampler2D DiffuseAlbedo;
layout(set = 2, binding = 3) uniform sampler2D ZBuffer;

struct Light {
	float Linear;
	float Quadratic;
	float Intensity;
	float Radius;
	vec4 Position;
  	vec4 Color;
};

const int MAX_NUM_LIGHTS = 1024;

layout(set = 1, binding = 0) uniform LightParameters {
    mat4 ViewMatrix;
    vec3 CamPosition;
    int numLights;
	Light lights[MAX_NUM_LIGHTS];
};

layout(set = 5, binding = 0, std140) uniform ShaderParameters {
	int debugBuffers;
	int SSAO_Options;
	int reflectanceModel;
	float ssaoDistanceThreshold;
	float ssaoRadius;
	int displayWidth;
	int displayHeight;
    float IntensityScale;
    float Epsilon;
    float BiasDistance;
    float Contrast;
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

float GGXDistribution(vec3 normal, vec3 halfway, float roughness) {
    float a = roughness*roughness;
    float aSquared = a*a;
    float NdotH = max(dot(normal, halfway), 0.0);
    float NdotH2 = NdotH*NdotH;

    float denom = ((NdotH2 * (aSquared - 1.0) + 1.0));
    return aSquared/(denom*denom*PI);
}

float GeometrySchlick(float NdotV, float roughness) {
    float r = (roughness + 1.0);
    float k = (r*r) / 8.0;

    return (NdotV)/(NdotV*(1.0 - k) + k);
}

float GeometrySmith(vec3 normal, vec3 view, vec3 light, float roughness) {
    float NdotV = max(dot(normal, view), 0.0);
    float NdotL = max(dot(normal, light), 0.0);

    return GeometrySchlick(NdotV, roughness) * GeometrySchlick(NdotL, roughness);
}

vec3 FresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
}

float rand(vec2 co){
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}

vec3 DecodeSpherical(vec2 In)
{
    vec2 ang = In.xy * 2.0 - 1.0;
    vec2 scth;
    scth.x = sin(ang.x * PI);
    scth.y = cos(ang.x * PI);
    vec2 scphi = vec2(sqrt(1 - ang.y*ang.y), ang.y);

    vec3 dec;
    dec.x = scth.y * scphi.x;
    dec.y = scth.x * scphi.x;
    dec.z = scphi.y;
    return dec;
}

vec2 OctWrap( vec2 v )
{
    vec2 ret;
    ret.x = (1-abs(v.y)) * (v.x >= 0 ? 1.0 : -1.0);
    ret.y = (1-abs(v.x)) * (v.y >= 0 ? 1.0 : -1.0);
    return ret.xy;
}

/*
Decodes the octahedron normal vector from it's two component form to return the normal with its three components. Uses the
property |x| + |y| + |z| = 1 and reverses the orthogonal projection performed while encoding.
*/
vec3 DecodeOctaH( vec2 encN )
{
    encN = encN * 2.0 - 1.0;
    vec3 n;
    n.z = 1.0 - abs( encN.x ) - abs( encN.y );
    n.xy = n.z >= 0.0 ? encN.xy : OctWrap( encN.xy );
    n = normalize( n );
    return n;
}


void main()
{
	// Retrieve data from G-buffer
	vec3 FragPos = texture(Position, textureCoord).rgb;
    vec3 DecodedNormal = DecodeOctaH(texture(Normal, textureCoord).rg);
//	vec3 DecodedNormal = DecodeSpherical(texture(gNormal, textureCoord).rg);
	vec3 N = DecodedNormal;
	vec4 Albedo = texture(DiffuseAlbedo, textureCoord).rgba;
	float Specular = texture(DiffuseAlbedo, textureCoord).a;
	float Depth = texture(ZBuffer, textureCoord).r;

	vec2 ssaoFilterRadius = vec2(ssaoRadius/displayWidth, ssaoRadius/displayHeight);
	vec3 viewSpacePos = (ViewMatrix * (vec4(FragPos, 1.0) - vec4(CamPosition, 1.0))).rgb;

	float fragDist = length(FragPos - CamPosition);

	vec3 lighting = vec3(0.0);

	if(debugBuffers == 0) {
		float ambientOcclusion = 0.0f;

		if(SSAO_Options == 1) {

			int sample_count = 8;
			for (int i = 0; i < sample_count;  ++i) {
				// sample at an offset specified by the current Poisson-Disk sample and scale it by a radius (has to be in Texture-Space)
				vec2 sampleTexCoord = textureCoord + (poisson16[i] * ssaoFilterRadius);
				float sampleDepth = texture(ZBuffer, sampleTexCoord).r;
				vec3 samplePos = texture(Position, sampleTexCoord).rgb;

				vec3 sampleDir = normalize(samplePos - FragPos);

				float NdotS = max(dot(N, sampleDir), 0.0);
				float VPdistSP = distance(FragPos, samplePos);

				float a = 1.0 - smoothstep(ssaoDistanceThreshold, ssaoDistanceThreshold * 2, VPdistSP);

				ambientOcclusion += a * NdotS;
			}

		    ambientOcclusion /= float(sample_count);
		}

		else if (SSAO_Options == 2) {
		//Alchemy SSAO algorithm
		    float A = 0.0f;
		    int sample_count = 8;
            for (int i = 0; i < sample_count;  ++i) {
                vec2 sampleTexCoord = textureCoord + (poisson16[i] * (ssaoFilterRadius));
                //                       float sampleDepth = texture(gDepth, sampleTexCoord).r;
                vec3 samplePos = texture(Position, sampleTexCoord).rgb;

                vec3 sampleDir = samplePos - FragPos;


                float NdotV = max(dot(N, sampleDir), 0);
                float VdotV = max(dot(sampleDir, sampleDir), 0);
                float temp = max(0, NdotV + viewSpacePos.z*BiasDistance);
                temp /= (VdotV + Epsilon);
                A+=temp;
             }

             A/=sample_count;
             A*= (2*IntensityScale);
             A = max(0, 1-A);
             A = pow(A, Contrast);
             ambientOcclusion = 1- A;

		}

		vec3 viewDir = normalize(CamPosition - FragPos);

		for(int i = 0; i < numLights; ++i)
		{
		    vec3 lightPos = lights[i].Position.xyz;
            vec3 L = (lightPos - FragPos);
            vec3 V = normalize(CamPosition - FragPos);
            vec3 H = normalize(L + V);
            float distance = length(L);
            L = normalize(L);

//            if(distance > 5.0f * lights[i].Radius) {
//                continue;
//            }

            float lightAttenuation = 1.0 / (1.0 + lights[i].Linear * distance + lights[i].Quadratic * distance * distance);

		    if(reflectanceModel == 0) {
		        // Diffuse
		        float NdotL = max(0.0, dot(N, L));
		        vec3 specular = vec3(0.0f);

             	vec3 R = reflect(-L, N);
             	float NdotR = max(0.0, dot(R, V));
             	float NdotH = max(0.0, dot(N, H));

             	vec3 diffuse = NdotL * lights[i].Intensity * Albedo.rgb * lights[i].Color.rgb * (1.0f - ambientOcclusion);

             	if(NdotL > 0) {
             	    specular = pow(NdotH, (1.0-Specular)*4.0) * Albedo.rgb * lights[i].Color.rgb * lights[i].Intensity;
             	}

             	lighting += (diffuse + specular) * lightAttenuation;
		    }
		    // Oren-Nayar model
		    else if(reflectanceModel == 1) {

            	float NdotL = dot(N, L);
            	float NdotV = dot(N, V);

            	float angleVN = acos(NdotV);
            	float angleLN = acos(NdotL);

            	float alpha = max(angleVN, angleLN);
            	float beta = min(angleVN, angleLN);
            	float gamma = dot(viewDir - N*dot(V, N), L - N*dot(L, N));

            	float roughness = 0.75;

            	float roughnessSquared = roughness*roughness;

            	float A = 1.0 - 0.5 * ( roughnessSquared / (roughnessSquared + 0.57));
            	float B = 0.45 * (roughnessSquared / (roughnessSquared + 0.09));
            	float C = sin(alpha)*tan(beta);

            	float L1 = max(0.0, NdotL) * (A + B * max(0.0, gamma) * C);

                vec3 inputColor = lightAttenuation*lights[i].Intensity * lights[i].Color.rgb * Albedo.rgb * (1.0f - ambientOcclusion);

            	vec3 diffuse = inputColor * L1;
            	vec3 specular = vec3(0.0);

            	specular *= lightAttenuation*specular;
            	lighting += diffuse + specular;
            }
            // Cook-Torrance
            else if(reflectanceModel == 2) {
                float metallic = 1.0;
                vec3 F0 = vec3(0.04);
                F0 = mix(F0, Albedo.rgb, metallic);

                float roughness = 1.0 - Specular;

                float NDF = GGXDistribution(N, H, roughness);
                float G = GeometrySmith(N, V, L, roughness);
                vec3 F = FresnelSchlick(max(dot(H, viewDir), 0.0), F0);

                vec3 BRDF = (NDF * G * F)/(4 * max(dot(V, N), 0.0) * max(dot(L, N), 0.0) + 0.001);

                vec3 kS = F;
                vec3 kD = (vec3(1.0) - kS);
                kD *= 1.0 - metallic;

                float NdotL = max(dot(N, L), 0.0);
                vec3 radiance = lights[i].Intensity * lights[i].Color.rgb * lightAttenuation;

                lighting += (kD * Albedo.rgb / PI + BRDF) * radiance * NdotL;
            }
		}

		FragColor = vec4(lighting, 1.0);
        gl_FragDepth = Depth;
	} else {
		vec2 newTexCoord;
		// color
        if(textureCoord.x < 0.25 && textureCoord.y < 0.5 ) {
            FragColor = Albedo;
        }
        // specular
        if(textureCoord.x > 0.25 && textureCoord.x < 0.5 && textureCoord.y < 0.5) {
            FragColor = vec4(Specular, Specular, Specular, 1.0);
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
            FragColor = vec4(N, 1.0f);
        }
        // position
        if(textureCoord.x < 0.5 && textureCoord.y > 0.5) {
            FragColor = vec4(FragPos, 1.0f);
        }

        gl_FragDepth = Depth;
	}
}
