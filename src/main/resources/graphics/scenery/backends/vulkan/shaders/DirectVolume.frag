#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(set = 4, binding = 0) uniform sampler2D hdrColor;
layout(set = 4, binding = 1) uniform sampler2D depth;

//layout(set = 1, binding = 0, std140) uniform ShaderParameters {
//	float Gamma;
//	float Exposure;
//} hdrParams;

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 6;
const int MAX_NUM_LIGHTS = 128;

struct Light {
	float Linear;
	float Quadratic;
	float Intensity;
	vec4 Position;
  	vec4 Color;
};

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Shininess;
};

layout(location = 0) in VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
} VertexIn;


layout(binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 ViewMatrix;
	mat4 NormalMatrix;
	mat4 ProjectionMatrix;
	vec3 CamPosition;
	int isBillboard;
} ubo;

layout(binding = 1) uniform MaterialProperties {
    MaterialInfo Material;
    int materialType;
};

layout(set = 1, binding = 0) uniform sampler3D ObjectTextures[NUM_OBJECT_TEXTURES];

//layout(set = 3, binding = 0, std140) uniform LightParameters {
//    int numLights;
//	Light lights[MAX_NUM_LIGHTS];
//};

layout(location = 0) out vec4 FragColor;

float PI_r = 0.3183098;

float HG(float costheta) {
	float g = 0.99;
	return 0.25 * PI_r * (1 - pow(g, 2.0)) / pow((1 + pow(g, 2.0) - 2 * g * costheta), 1.5);
}

float Mie(float costheta) {
	float angle = acos(costheta);
//	return texture(mie_texture, (PI - angle) * PI_r).r;
    return 0.5;
}

float phase(vec3 v1, vec3 v2) {
	float costheta = dot(v1, v2) / length(v1) / length(v2);
	return HG(-costheta);
	//return Mie(costheta);
}

float cloud_sampling_structure(vec3 v, float delta) {
	/* Reposition the cloud first */
	v = (v + vec3(50, -50, 50)) * 0.001;

	vec4 texture = texture(ObjectTextures[3], v);
	return texture.r;
}

float cloud_sampling_tile(float tile, vec3 v, float delta) {
	/* Reposition the tile first */
	v = (v + vec3(50, -50, 50)) * 0.256;
	v = fract(v);

	if (tile * 255 == 1) {
		return texture(ObjectTextures[3], v).r * delta;
	}

	if (tile * 255 == 255) {
		/* This is the safety tile that surrounds all non-zero tiles */
		return 0.008 * delta;
	}

	return 0;
}

float cast_scatter_ray(vec3 origin, vec3 dir) {
	float delta = 5.0;
	float end = 50.0;

	vec3 sample_point = vec3(0.0);
	float inside = 0.0;

	float phase = phase(dir, vec3(ubo.CamPosition - origin));

	for (float t = 0.0; t < end; t += delta) {
		sample_point = origin + dir * t;
		//inside += cloud_sampling(sample_point, delta);
	}

	float beer = exp(-0.2 * inside);

	float value = phase + beer;
	return value;
}


// http://www.iquilezles.org/www/articles/terrainmarching/terrainmarching.htm
vec4 cast_ray(vec3 origin, vec3 dir) {
	float delta_large = 10.0;
	float delta_small = 0.1;
	float start = 0.1;
//	float start = gl_DepthRange.near;
	float end = 500.0;

	vec4 value = vec4(0.0);
	vec3 cloud_color = vec3(0.93, 0.93, 0.95);
	vec3 cloud_shade = vec3(0.859, 0.847, 0.757) - 0.1;
	vec3 cloud_bright = vec3(0.99, 0.96, 0.95);
	vec3 cloud_dark = vec3(0.671, 0.725, 0.753);
	value.rgb = cloud_dark;

	bool inside = false;
	bool looking_for_new_tile = true;
	int points_inside = 0;
	vec3 sample_point = origin;

	float tile;
	float delta = delta_large;
	for (float t = start; t < end; t += delta) {
		sample_point = origin + dir * t;

		/* Stop rays that are going below ground */
		if (sample_point.y < 0.0) {
			break;
		}

		/* Stop rays that already reach full opacity */
		if (value.a > 1.0) {
			break;
		}

		float alpha;
		if (!inside) {
			tile = cloud_sampling_structure(sample_point, delta);
			if (tile > 0.0) {
				inside = true;
			} else {
				looking_for_new_tile = true;
			}
		}

		if (inside) {
			/* Start of a new tile? */
			if (looking_for_new_tile) {
				/* Move the starting point a large delta backwards */
				t -= delta_large;
				if (t < start) {
					t = start;
				}
				sample_point = origin + dir * t;
				delta = delta_small;

				looking_for_new_tile = false;
				points_inside = 0;
			}
			delta = delta_small;
			alpha = cloud_sampling_tile(tile, sample_point, delta);
			value.a += alpha;
			points_inside += 1;
		}

		/* Check next structure block if we are still inside */
		if (inside && points_inside * delta_small > delta_large) {
			tile = cloud_sampling_structure(sample_point, delta);
			if (tile == 0.0) {
				inside = false;
				looking_for_new_tile = true;

				delta = delta_large;
			} else {
				points_inside = 0;
			}
		}

		/* Calculate the scattering */
		vec3 light_position = vec3(10.0f);
		vec3 sun_pos = light_position; //lights[0].Position.rgb;
		float energy = cast_scatter_ray(sample_point, normalize(sun_pos - sample_point));
		//value.rgb = mix(cloud_dark, cloud_bright, energy);
	}

	return clamp(value, 0.0, 1.0);
}

void main()
{
//	FragColor = vec4(texture(ObjectTextures[3], VertexIn.FragPosition).rgb, 0.8);
//FragColor = vec4(VertexIn.FragPosition, 0.5);

/* Calculate the ray */
	// http://antongerdelan.net/opengl/raycasting.html
	vec2 view_port = vec2(1280, 720);
	float x = 2.0 * gl_FragCoord.x / view_port.x - 1.0;
	float y = 2.0 * gl_FragCoord.y / view_port.y - 1.0;
	vec2 ray_nds = vec2(x, y);
	vec4 ray_clip = vec4(ray_nds, -1.0, 1.0);
	vec4 ray_view = inverse(ubo.ProjectionMatrix) * ray_clip;
	ray_view = vec4(ray_view.xy, -1.0, 0.0);
	vec3 ray_world = (inverse(ubo.ViewMatrix) * ray_view).xyz;
	ray_world = normalize(ray_world);

	vec4 cloud_color = cast_ray(ubo.CamPosition, ray_world);

//	vec4 diffuse_color = texelFetch(diffuse_buffer, ivec2(gl_FragCoord.xy), 0);
//	float depth = pow(texelFetch(depth_buffer, ivec2(gl_FragCoord.xy), 0).x, 128.0);

//	frag_color.a = 0.5;
//	frag_color.rgb = mix(diffuse_color.rgb, cloud_color.rgb, cloud_color.a);
//    FragColor = cloud_color;
    FragColor = vec4(texture(ObjectTextures[3], vec3(VertexIn.TexCoord, 0.5)).rgb, 0.8);

}
