#version 450 core
#extension GL_ARB_separate_shader_objects: enable

#define PI 3.14159265359

layout(location = 0) in VertexData {
    vec3 FragPosition;
    vec3 Normal;
    vec2 TexCoord;
} Vertex;

layout(location = 0) out vec4 FragColor;

layout(set = 3, binding = 0) uniform sampler2D InputNormalsMaterial;
layout(set = 3, binding = 1) uniform sampler2D InputDiffuseAlbedo;
layout(set = 3, binding = 2) uniform sampler2D InputZBuffer;

struct Light {
	float Linear;
	float Quadratic;
	float Intensity;
	float Radius;
	vec4 Position;
  	vec4 Color;
};

const int MAX_NUM_LIGHTS = 1024;

layout(set = 0, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
    mat4 inverseProjectionMatrices[2];
    mat4 headShift;
    float IPD;
    int stereoEnabled;
} vrParameters;

layout(set = 1, binding = 0) uniform LightParameters {
    mat4 ViewMatrix;
    mat4 InverseViewMatrix;
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec3 CamPosition;
};

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

layout(set = 4, binding = 0, std140) uniform ShaderProperties {
    float intensity;
    float linear;
    float quadratic;
    float lightRadius;
    int debugMode;
    vec3 worldPosition;
    vec3 emissionColor;
};

layout(set = 5, binding = 0, std140) uniform ShaderParameters {
    int debugLights;
	int reflectanceModel;
	int displayWidth;
	int displayHeight;
	float ssaoRadius;
	int ssaoSamples;
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
    float NdotH = abs(dot(normal, halfway));
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
    float NdotV = abs(dot(normal, view));
    float NdotL = abs(dot(normal, light));

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

vec3 worldFromDepth(float depth, vec2 texcoord) {
    float z = depth;//* 2.0 - 1.0;

    mat4 invHeadToEye = vrParameters.headShift;
    invHeadToEye[0][3] += currentEye.eye * vrParameters.IPD;

	mat4 invProjection = (vrParameters.stereoEnabled ^ 1) * InverseProjectionMatrix + vrParameters.stereoEnabled * vrParameters.inverseProjectionMatrices[currentEye.eye];
	mat4 invView = (vrParameters.stereoEnabled ^ 1) * InverseViewMatrix + vrParameters.stereoEnabled * InverseViewMatrix * invHeadToEye;

    vec4 clipSpacePosition = vec4(texcoord * 2.0 - 1.0, z, 1.0);
    vec4 viewSpacePosition = invProjection * clipSpacePosition;

    viewSpacePosition /= viewSpacePosition.w;
    vec4 world = invView * viewSpacePosition;
    return world.xyz;
}

void main()
{
    vec2 textureCoord = gl_FragCoord.xy/vec2(displayWidth, displayHeight);

	vec3 N = DecodeOctaH(texture(InputNormalsMaterial, textureCoord).rg);

	vec4 Albedo = texture(InputDiffuseAlbedo, textureCoord).rgba;
	float Specular = texture(InputDiffuseAlbedo, textureCoord).a;
	vec2 MaterialParams = texture(InputNormalsMaterial, textureCoord).ba;

	float Depth = texture(InputZBuffer, textureCoord).r;

    vec3 FragPos = worldFromDepth(Depth, textureCoord);
	vec3 viewSpacePos = (ViewMatrix * (vec4(CamPosition, 1.0))).xyz;
	vec3 viewSpaceFragPos = (InverseViewMatrix * (vec4(FragPos, 1.0))).xyz;

	if(debugLights == 1) {
        FragColor = vec4(Albedo.rgb, 1.0);
        return;
	}

	vec2 ssaoFilterRadius = vec2(ssaoRadius/displayWidth, ssaoRadius/displayHeight);

	float fragDist = length(FragPos - CamPosition);
	if(debugMode == 1) {
	    FragColor = vec4(1.0, 0.0, 0.0, 1.0);
	    return;
	}

	vec3 lighting = vec3(0.0);

    vec3 L = (worldPosition.xyz - FragPos.xyz);
    float distance = length(L);
    L = normalize(L);

    vec3 V = normalize(CamPosition - FragPos);
    vec3 H = normalize(L + V);

    float ambientOcclusion = 0.0f;
    float lightAttenuation = pow(clamp(1.0 - pow(distance/lightRadius, 4.0), 0.0, 1.0), 2.0) / (distance * distance + 1.0);

    //Alchemy SSAO
	float A = 0.0f;
    for (int i = 0; i < ssaoSamples;  ++i) {
        vec2 sampleTexCoord = textureCoord + (poisson16[i] * (ssaoFilterRadius));
        vec3 samplePos = worldFromDepth(Depth, sampleTexCoord);

        vec3 sampleDir = samplePos - FragPos;

        float NdotV = max(dot(N, sampleDir), 0);
        float VdotV = max(dot(sampleDir, sampleDir), 0);
        float temp = max(0, NdotV + viewSpacePos.z*BiasDistance);
        temp /= (VdotV + Epsilon);
        A+=temp;
    }

    A /= ssaoSamples;
    A *= (2*IntensityScale);
    A = max(0, 1-A);
    A = pow(A, Contrast);
    ambientOcclusion = 1 - A;

    if(reflectanceModel == 1) {
        // Diffuse
        float NdotL = max(0.0, dot(N, L));
        vec3 specular = vec3(0.0f);

        vec3 R = reflect(-L, N);
        float NdotR = max(0.0, dot(R, V));
        float NdotH = max(0.0, dot(N, H));

        vec3 diffuse = NdotL * intensity * Albedo.rgb * emissionColor.rgb * (1.0f - ambientOcclusion);

        if(NdotL > 0.0) {
            specular = pow(NdotH, (1.0-Specular)*4.0) * Albedo.rgb * emissionColor.rgb * intensity;
        }

        lighting += (diffuse + specular) * lightAttenuation;
    }
    // Oren-Nayar model for diffuse and Cook-Torrance for Specular
    else if(reflectanceModel == 0) {
        vec3 diffuse = vec3(0.0);
        vec3 specular = vec3(0.0);

        float roughness = MaterialParams.r;

        float LdotV = max(dot(L, V), 0.0);
        float NdotL = max(dot(L, N), 0.0);
        float NdotV = max(dot(N, V), 0.0);

        float s = LdotV - NdotL * NdotV;
        float t = max(mix(1.0, max(NdotL, NdotV), step(0.0, s)), 0.0001);

        float sigma2 = roughness * roughness;
        float A = 1.0 - sigma2 / (sigma2 + 0.33);
        float B = 0.45 * sigma2 / (sigma2 + 0.09);

        float L1 = NdotL * (A + B * s / t) / PI;

        vec3 inputColor = intensity * emissionColor.rgb * Albedo.rgb * (1.0f - ambientOcclusion);

        diffuse = inputColor * L1;

        if(Specular > 0.0 || MaterialParams.g > 0.0) {
            float metallic = MaterialParams.g;
            vec3 F0 = vec3(0.04);
            F0 = mix(F0, Albedo.rgb, metallic);

            float roughness = MaterialParams.r;

            float NDF = GGXDistribution(N, H, roughness);
            float G = GeometrySmith(N, V, L, roughness);
            vec3 F = FresnelSchlick(max(dot(H, V), 0.0), F0);

            vec3 BRDF = (NDF * G * F)/max(4.0 * abs(dot(N, V)) * abs(dot(N, L)), 0.001);

            vec3 kS = F;
            vec3 kD = (vec3(1.0) - kS);
            kD *= 1.0 - metallic;

            vec3 radiance = intensity * emissionColor.rgb;
            specular = (kD * Albedo.rgb / PI + BRDF) * radiance * NdotL;
        }

        lighting += (diffuse + specular) * lightAttenuation;
    }

    FragColor = vec4(lighting, 1.0);
    gl_FragDepth = Depth;
}
