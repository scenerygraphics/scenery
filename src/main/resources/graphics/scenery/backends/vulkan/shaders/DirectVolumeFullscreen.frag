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
layout(location = 1) in mat4 inverseProjection;
layout(location = 5) in mat4 inverseModelView;


layout(binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 ViewMatrix;
	mat4 NormalMatrix;
	mat4 ProjectionMatrix;
	vec3 CamPosition;
	int isBillboard;
} ubo;

layout(set = 1, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];
layout(set = 1, binding = 1) uniform sampler3D VolumeTextures;

layout(set = 2, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
    mat4 headShift;
    float IPD;
    int stereoEnabled;
} vrParameters;

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

layout(location = 0) out vec4 FragColor;

layout(set = 3, binding = 0) uniform ShaderProperties {
    float trangemin;
    float trangemax;
    float boxMin_x;
    float boxMin_y;
    float boxMin_z;
    float boxMax_x;
    float boxMax_y;
    float boxMax_z;
    int maxsteps;
    float dithering;
    float phase;
    float alpha_blending;
    float gamma;
};

// useless comment

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

void mainold()
{
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

      orig0 = inverseProjection * front;
      orig0 *= 1.f/orig0.w;

      orig = inverseModelView * orig0;
      orig *= 1.f/orig.w;

      direc0 = inverseProjection * back;
      direc0 *= 1.f/direc0.w;

      direc = inverseModelView * normalize(direc0-orig0);
      direc.w = 0.0f;

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
      const vec3 vecstep = 0.5*tstep*direc.xyz;
      vec3 pos = 0.5 * (1.0 + orig.xyz + tnear * direc.xyz);
      vec3 stop = 0.5 * (1.0 + orig.xyz + tfar * direc.xyz);

        // raycasting loop:
        float maxp = 0.0f;
        float mappedVal = 0.0f;

        float transmission = 1.0;
        float density = 100.0f;
        float absorption = 0.05f;

        float colVal = 0.0;
        float alphaVal = 0.0;
        float newVal = 0.0;

        if (alpha_blending <= 0.f)
        {
          // No alpha blending:
      	  for(int i = 0; i < maxsteps; ++i, pos += vecstep)
      	  {
     	        float volume_sample = texture(VolumeTextures, pos.xyz).r * density;
//      	  		maxp = fmax(maxp,read_imagef(volume, volumeSampler, pos).x);
      	  		transmission *= 1.0 - tstep * volume_sample * absorption;
                maxp = volume_sample * tstep * transmission;

      	  		if(transmission < 0.01) {
          	  		break;
      	  		}
      		}

      		// Mapping to transfer function range and gamma correction:
//      		mappedVal = clamp(pow(ta*maxp + tb,gamma),0.f,1.f);
//      		mappedVal = pow(ta*maxp+tb,gamma);
//      		mappedVal = 0.5;
      	}
      	else
      	{
      	  // alpha blending:
      	float cumsum = 1.f;
             	for(int i=0; i<=maxsteps; ++i, pos += vecstep){
             		newVal = 1.f*texture(VolumeTextures, pos.xyz).r;
             		newVal = (trangemax== 0)?newVal:(newVal-trangemin)/(trangemax-trangemin);
             		colVal = max(colVal,cumsum*newVal);

             		cumsum  *= (1.f-.1f*alpha_blending*newVal);
             		if (cumsum<=0.02f)
             		  break;
             	}
      	}

        colVal = clamp(pow(colVal, gamma), 0.0, 1.0);
        alphaVal = clamp(colVal, 0.0, 1.0);

      	vec4 color = texture(ObjectTextures[3], vec2(colVal, 0.5f));

          // Alpha pre-multiply:
//          color.x = color.x;
//          color.y = color.y;
//          color.z = color.z;
          color.w = alphaVal;

      // write output color:
      FragColor = color;

}


void main_withoutalpha()
{
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

      orig0 = inverseProjection * front;
      orig0 *= 1.f/orig0.w;

      orig = inverseModelView * orig0;
      orig *= 1.f/orig.w;

      direc0 = inverseProjection * back;
      direc0 *= 1.f/direc0.w;

      direc = inverseModelView * normalize(direc0-orig0);
      direc.w = 0.0f;

      // find intersection with box
      const Intersection inter = intersectBox(orig, direc, boxMin, boxMax);

      if (!inter.hit || inter.tfar <= 0)
      {
       	FragColor = vec4(0.0f, 0.0f, 0.0f, 0.0f);
      	return;
      }


      const float tnear = inter.tnear;
      const float tfar = max(inter.tfar, 0.0f);
      const float tstep = abs(tnear-tfar)/(maxsteps);

      // apply phase:
      orig += phase*tstep*direc;

      // precompute vectors:
      const vec3 vecstep = 0.5*tstep*direc.xyz;
      vec3 pos = 0.5 * (1.0 + orig.xyz + tnear * direc.xyz);
      vec3 stop = 0.5 * (1.0 + orig.xyz + tfar * direc.xyz);

        // raycasting loop:
        float maxp = 0.0f;
        float mappedVal = 0.0f;

        float transmission = 1.0;
        float density = 100.0f;
        float absorption = 0.05f;

        float colVal = 0.0;
        float alphaVal = 0.0;
        float newVal = 0.0;


          // No alpha blending:
      for(int i = 0; i < maxsteps; ++i, pos += vecstep)
      {
     	   float volume_sample = texture(VolumeTextures, pos.xyz).r;
     	   maxp = max(maxp,volume_sample);

      }
    //dummy
      // Mapping to transfer function range and gamma correction:
      colVal = clamp(pow(ta*maxp + tb,gamma),0.f,1.f);
      colVal = clamp(ta*maxp + tb,0.f,1.f);
      alphaVal = clamp(colVal, 0.0, 1.0);


      vec4 color = texture(ObjectTextures[3], vec2(colVal, 0.5f));
      color.w = alphaVal;

      // useless comment
      FragColor = color;

      //FragColor = vec4(colVal, 0.0f, 0.0f, alphaVal);
      //FragColor = vec4(color.x, 0.0f, 0.0f, alphaVal);

      // FIXME sanity check: this should give the colormap texture (but doesnt!)
      //FragColor = texture(ObjectTextures[3], vec2(textureCoord.s ,0.5f));

}

// with opacity acumulation

void main()
{
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

      orig0 = inverseProjection * front;
      orig0 *= 1.f/orig0.w;

      orig = inverseModelView * orig0;
      orig *= 1.f/orig.w;

      direc0 = inverseProjection * back;
      direc0 *= 1.f/direc0.w;

      direc = inverseModelView * normalize(direc0-orig0);
      direc.w = 0.0f;

      // find intersection with box
      const Intersection inter = intersectBox(orig, direc, boxMin, boxMax);

      if (!inter.hit || inter.tfar <= 0)
      {
       	FragColor = vec4(0.0f, 0.0f, 0.0f, 0.0f);
      	return;
      }


      const float tnear = inter.tnear;
      const float tfar = max(inter.tfar, 0.0f);
      const float tstep = abs(tnear-tfar)/(maxsteps);

      // apply phase:
      orig += phase*tstep*direc;

      // precompute vectors:
      const vec3 vecstep = 0.5*tstep*direc.xyz;
      vec3 pos = 0.5 * (1.0 + orig.xyz + tnear * direc.xyz);
      vec3 stop = 0.5 * (1.0 + orig.xyz + tfar * direc.xyz);

      // raycasting loop:
      float maxp = 0.0f;
      float mappedVal = 0.0f;


      float colVal = 0.0;
      float alphaVal = 0.0;
      float newVal = 0.0;



      if (alpha_blending <= -10.f){
          // nop alpha blending
          for(int i = 0; i < maxsteps; ++i, pos += vecstep)
          {
            float volume_sample = texture(VolumeTextures, pos.xyz).r;
            maxp = max(maxp,volume_sample);

          }
          colVal = clamp(pow(ta*maxp + tb,gamma),0.f,1.f);



      }
      else{
          // alpha blending:
          float opacity= 1.0f;
          for(int i = 0; i < maxsteps; ++i, pos += vecstep){

               float volume_sample = texture(VolumeTextures, pos.xyz).r;
               newVal = clamp(ta*volume_sample + tb,0.f,1.f);
               colVal = max(colVal,opacity*newVal);

               opacity  *= (1.f-alpha_blending*clamp(newVal,0.f,1.f));

               if (opacity<=0.02f)
                    break;

          }
      }


      alphaVal = clamp(colVal, 0.0, 1.0);
    //dummy
      // Mapping to transfer function range and gamma correction:


      vec4 color = texture(ObjectTextures[3], vec2(colVal, 0.5f));
      color.w = alphaVal;

      // useless comment
      FragColor = color;

      //FragColor = vec4(colVal, 0.0f, 0.0f, alphaVal);
      //FragColor = vec4(color.x, 0.0f, 0.0f, alphaVal);

      // FIXME sanity check: this should give the colormap texture (but doesnt!)
      //FragColor = texture(ObjectTextures[3], vec2(textureCoord.s ,0.5f));

}

