#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(set = 5, binding = 0) uniform sampler2D hdrColor;
layout(set = 5, binding = 1) uniform sampler2D depth;

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

layout(location = 0) in vec2 textureCoord;


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

layout(set = 1, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];
layout(set = 1, binding = 1) uniform sampler3D VolumeTextures;

layout(set = 3, binding = 0, std140) uniform LightParameters {
    int numLights;
	Light lights[MAX_NUM_LIGHTS];
};

layout(location = 0) out vec4 FragColor;

float PI_r = 0.3183098;

struct Ray {
    vec3 Origin;
    vec3 Dir;
};

struct AABB {
    vec3 Min;
    vec3 Max;
};

//bool IntersectBox(Ray r, AABB aabb, out float t0, out float t1)
//{
//    vec3 invR = 1.0 / r.Dir;
//    vec3 tbot = invR * (aabb.Min-r.Origin);
//    vec3 ttop = invR * (aabb.Max-r.Origin);
//    vec3 tmin = min(ttop, tbot);
//    vec3 tmax = max(ttop, tbot);
//    vec2 t = max(tmin.xx, tmin.yz);
//    t0 = max(t.x, t.y);
//    t = min(tmax.xx, tmax.yz);
//    t1 = min(t.x, t.y);
//    return t0 <= t1;
//}

float Absorption = 2.5;

const float maxDist = sqrt(2.0);
const int numSamples = 256;
const float stepSize = maxDist/float(numSamples);
const int numLightSamples = 16;
const float lscale = maxDist / float(numLightSamples);
const float densityFactor = 700;

const float trangemin = 0.0f;
const float trangemax = 2000.0f;

const float boxMin_x = -1.0f;
const float boxMin_y = -1.0f;
const float boxMin_z = -1.0f;

const float boxMax_x = 1.0f;
const float boxMax_y = 1.0f;
const float boxMax_z = 1.0f;

const int maxsteps = 128;
const float dithering = 0.0f;
const float phase = 0.0f;
const float alpha_blending = -1.0f;
const float gamma = 1.0f;

const int LOOPUNROLL=16;

struct Intersection {
    bool hit;
    float tnear;
    float tfar;
};

Intersection intersectBox(vec4 r_o, vec4 r_d, vec4 boxmin, vec4 boxmax)
{
    // compute intersection of ray with all six bbox planes
    vec4 invR = vec4(1.0f,1.0f,1.0f,1.0f) / r_d;
    vec4 tbot = invR * (boxmin - r_o);
    vec4 ttop = invR * (boxmax - r_o);

    // re-order intersections to find smallest and largest on each axis
    vec4 tmin = min(ttop, tbot);
    vec4 tmax = max(ttop, tbot);

    // find the largest tmin and the smallest tmax
    float largest_tmin = max(max(tmin.x, tmin.y), max(tmin.x, tmin.z));
    float smallest_tmax = min(min(tmax.x, tmax.y), min(tmax.x, tmax.z));

	return Intersection(smallest_tmax > largest_tmin, largest_tmin, smallest_tmax);
}

void main()
{
    const mat4 invP = transpose(inverse(ubo.ProjectionMatrix));
    const mat4 invM = transpose(inverse(ubo.ViewMatrix * ubo.ModelMatrix));
    // convert range bounds to linear map:
      const float ta = 1.f/(trangemax-trangemin);
      const float tb = trangemin/(trangemin-trangemax);

    	// box bounds using the clipping box
      const vec4 boxMin = vec4(boxMin_x,boxMin_y,boxMin_z,1.f);
      const vec4 boxMax = vec4(boxMax_x,boxMax_y,boxMax_z,1.f);

      // thread float coordinates:
//      const float u = (x / (float) imageW)*2.0f-1.0f;
//      const float v = (y / (float) imageH)*2.0f-1.0f;
      const float u = textureCoord.s*2.0 - 1.0;
      const float v = textureCoord.t*2.0 - 1.0;

      // front and back:
      const vec4 front = vec4(u,v,-1.f,1.f);
      const vec4 back = vec4(u,v,1.f,1.f);

      // calculate eye ray in world space
      vec4 orig0, orig;
      vec4 direc0, direc;

      orig0.x = dot(front, invP[0]);
      orig0.y = dot(front, invP[1]);
      orig0.z = dot(front, invP[2]);
      orig0.w = dot(front, invP[3]);

      orig0 *= 1.f/orig0.w;

      orig.x = dot(orig0, invM[0]);
      orig.y = dot(orig0, invM[1]);
      orig.z = dot(orig0, invM[2]);
      orig.w = dot(orig0, invM[3]);

      orig *= 1.f/orig.w;

      direc0.x = dot(back, invP[0]);
      direc0.y = dot(back, invP[1]);
      direc0.z = dot(back, invP[2]);
      direc0.w = dot(back, invP[3]);

      direc0 *= 1.f/direc0.w;

      direc0 = normalize(direc0-orig0);

      direc.x = dot(direc0, invM[0]);
      direc.y = dot(direc0, invM[1]);
      direc.z = dot(direc0, invM[2]);
      direc.w = 0.0f;

    //    printf("%f %f %f %f\n", invP[0], invP[5], invP[10], invP[15]);
    //printf("orig: %f %f %f\n", orig.x, orig.y, orig.z);
    //printf("dir: %f %f %f\n", direc.x, direc.y, direc.z);

      // find intersection with box
      const Intersection inter = intersectBox(orig, direc, boxMin, boxMax);
      if (!inter.hit || inter.tfar <= 0)
      {
    //  	d_output[x+imageW*y] = (vec4)(orig.x, orig.y, orig.z, 1.0f);
      	FragColor = vec4(0.0f, 0.0f, 0.0f, 0.0f);
      	return;
      }

      const float tnear = inter.tnear;
      const float tfar = max(inter.tfar, 0.0f);
      const float tstep = abs(tnear-tfar)/(maxsteps);

      // apply phase:
      orig += phase*tstep*direc;

      // precompute vectors:
      const vec4 vecstep = 0.5f*tstep*direc;
      vec4 pos = orig*0.5f+0.5f + tnear*0.5f*direc;

        // raycasting loop:
        float maxp = 0.0f;

        float mappedVal = 0.0f;

        float volume_sample = texture(VolumeTextures, pos.xyz).x;

        if (alpha_blending<=0.f)
        {
          // No alpha blending:
      	  for(int i=0; i<maxsteps; i++)
      	  {
//      	  		maxp = fmax(maxp,read_imagef(volume, volumeSampler, pos).x);
      	  		maxp = max(volume_sample, maxp);
      	  		pos+=vecstep;
      		}

      		// Mapping to transfer function range and gamma correction:
//      		mappedVal = clamp(pow(mad(ta,maxp,tb),gamma),0.f,1.f);
//      		mappedVal = pow(ta*maxp+tb,gamma);
            mappedVal = maxp;
      	}
      	else
      	{
      	  // alpha blending:
      		float cumsum = 1.f;
      	  float decay_rate = alpha_blending*tstep;

      		for(int i=0; i<maxsteps; i++)
      		{
//      		  	float new_val = read_imagef(volume, volumeSampler, pos).x;
      		  	float new_val = volume_sample.x;

      		  	//normalize to 0...1
      		  	float normalized_val = ta*new_val+tb;
      		  	maxp = max(maxp,cumsum*normalized_val);
      		  	cumsum  *= exp(-decay_rate*normalized_val);
      	  		pos+=vecstep;
      		}

      		// Mapping to transfer function range and gamma correction:
          mappedVal = clamp(pow(maxp,gamma),0.f,1.f);
      	}


      	vec4 color = vec4(mappedVal);//brightness*read_imagef(transferColor4,transferSampler, (float2)(mappedVal,0.0f));

          // Alpha pre-multiply:
          color.x = color.x;
          color.y = color.y;
          color.z = color.z;
          color.w = 1.0f;

      // write output color:
      FragColor = color;

}
