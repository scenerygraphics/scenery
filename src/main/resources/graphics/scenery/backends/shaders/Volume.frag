#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(set = 6, binding = 0) uniform sampler2D InputOutput;
layout(set = 6, binding = 1) uniform sampler2D InputOutputDepth;

layout(location = 0) in VertexData {
    vec2 textureCoord;
    mat4 inverseProjection;
    mat4 inverseModelView;
    mat4 MVP;
} Vertex;

layout(location = 0) out vec4 FragColor;

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 6;
const int MAX_NUM_LIGHTS = 1024;

struct Light {
	float Linear;
	float Quadratic;
	float Intensity;
	float Radius;
	vec4 Position;
  	vec4 Color;
};

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Shininess;
};

layout(set = 1, binding = 0) uniform LightParameters {
    mat4 ViewMatrix;
    vec3 CamPosition;
    int numLights;
	Light lights[MAX_NUM_LIGHTS];
};

layout(set = 2, binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 NormalMatrix;
	mat4 ProjectionMatrix;
	int isBillboard;
} ubo;

layout(set = 4, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];
layout(set = 4, binding = 1) uniform usampler3D VolumeTextures;

layout(set = 5, binding = 0) uniform ShaderProperties {
    float voxelSizeX;
    float voxelSizeY;
    float voxelSizeZ;
    int sizeX;
    int sizeY;
    int sizeZ;
    float trangemin;
    float trangemax;
    float boxMin_x;
    float boxMin_y;
    float boxMin_z;
    float boxMax_x;
    float boxMax_y;
    float boxMax_z;
    int maxsteps;
    float alpha_blending;
    float gamma;
};

layout(set = 3, binding = 0) uniform MaterialProperties {
    MaterialInfo Material;
    int materialType;
};

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

vec3 posFromDepth(vec2 textureCoord) {
    float z = texture(InputOutputDepth, textureCoord).r;
    float x = textureCoord.x * 2.0 - 1.0;
    float y = (1.0 - textureCoord.y) * 2.0 - 1.0;
    vec4 projectedPos = Vertex.inverseProjection * vec4(x, y, z, 1.0);

    return projectedPos.xyz/projectedPos.w;
}

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
      const float u = Vertex.textureCoord.s*2.0 - 1.0;
      const float v = Vertex.textureCoord.t*2.0 - 1.0;

      // front and back:
      const vec4 front = vec4(u,v,-1.f,1.f);
      const vec4 back = vec4(u,v,1.f,1.f);

      // calculate eye ray in world space
      vec4 orig0, orig;
      vec4 direc0, direc;

      orig0 = Vertex.inverseProjection * front;
      orig0 *= 1.f/orig0.w;

      orig = Vertex.inverseModelView * orig0;
      orig *= 1.f/orig.w;

      direc0 = Vertex.inverseProjection * back;
      direc0 *= 1.f/direc0.w;

      direc = Vertex.inverseModelView * normalize(direc0-orig0);
      direc.w = 0.0f;

      // find intersection with box
      const Intersection inter = intersectBox(orig, direc, boxMin, boxMax);

      if (!inter.hit || inter.tfar <= 0)
      {
       	FragColor = vec4(0.0f, 0.0f, 0.0f, 0.0f);
       	gl_FragDepth = texture(InputOutputDepth, Vertex.textureCoord).r;
      	return;
      }

      const float tnear = max(inter.tnear, 0.0f);
      const float tfar = inter.tfar;

      const float tstep = abs(tnear-tfar)/(maxsteps);

      // precompute vectors:
      const vec3 vecstep = 0.5*tstep*direc.xyz;
      vec3 pos = 0.5 * (1.0 + orig.xyz + tnear * direc.xyz);
      vec3 stop = 0.5 * (1.0 + orig.xyz + tfar * direc.xyz);

      vec4 stopNDC = Vertex.MVP * vec4(stop, 1.0);
      stopNDC *= 1.0/stopNDC.w;

      vec4 startNDC = Vertex.MVP * vec4(pos, 1.0);
      startNDC *= 1.0/startNDC.w;
//      gl_FragDepth = texture(InputOutputDepth, Vertex.textureCoord).r;


//      float d = (geomstart.z + 1.0)/2.0;

//      float d = geomstart.z;
//      if(d > texture(InputDepth, Vertex.textureCoord).r) {
//        FragColor = vec4(0.0, 0.0, 0.0, 0.0);
//        return;
//      }

//      d = (stopNDC.z + 1.0)/2.0;

//      if(stopWorld.z > texture(InputDepth, Vertex.textureCoord).r) {
      vec4 geompos = Vertex.MVP * vec4(posFromDepth(Vertex.textureCoord), 1.0);

      // geometry is in front of volume, don't raycast at all
      if(startNDC.z > texture(InputOutputDepth, Vertex.textureCoord).r) {
        FragColor = vec4(0.0, 1.0, 0.0, 0.0);
        return;
      }

      // geometry intersects volume, terminate rays early
      if(stopNDC.z > texture(InputOutputDepth, Vertex.textureCoord).r) {
        stop = posFromDepth(Vertex.textureCoord);
//        stop0 *= 1.0/stop0.w;
//
//        vec4 stopW = Vertex.inverseModelView * stop0;
//        stop = stopW.xyz/stopW.w;
//        FragColor = vec4(1.0, 0.0, 0.0, 0.0);
//        return;
      }

//      FragColor = vec4(stop, 1.0);
//      return;


      vec3 origin = pos;

      // raycasting loop:
      float maxp = 0.0f;
      float mappedVal = 0.0f;


      float colVal = 0.0;
      float alphaVal = 0.0;
      float newVal = 0.0;
//      gl_FragDepth = geompos.z/geompos.w;


      if (alpha_blending <= 0.f){
          // nop alpha blending
          for(int i = 0; i < maxsteps; ++i, pos += vecstep) {
            float volume_sample = texture(VolumeTextures, pos.xyz).r;
            maxp = max(maxp,volume_sample);
          }

          colVal = clamp(pow(ta*maxp + tb,gamma),0.f,1.f);
      }
      else{
          // alpha blending:
          float opacity = 1.0f;
          for(int i = 0; i < maxsteps; ++i, pos += vecstep) {
               float volume_sample = texture(VolumeTextures, pos.xyz).r;
               newVal = clamp(ta*volume_sample + tb,0.f,1.f);
               colVal = max(colVal,opacity*newVal);

               opacity  *= (1.f-alpha_blending*clamp(newVal,0.f,1.f));

              vec4 geomstart = Vertex.MVP * vec4(pos, 1.0);
              geomstart *= 1.0/geomstart.w;
              gl_FragDepth = geomstart.z;

               if (opacity<=0.02f) {
//                    gl_FragDepth = geomstart.z;
                    break;
               }
//               vec4 proj = Vertex.MVP * vec4(pos, 1.0);
//               vec3 coord = proj.xyz/proj.w;
//               if(coord.z > texture(InputDepth, Vertex.textureCoord).r) {
//                    discard;
//               }
          }
      }


      alphaVal = clamp(colVal, 0.0, 1.0);

      // FIXME: this is a workaround for grey lines appearing at borders
      alphaVal = alphaVal<0.01?0.0f:alphaVal;

      // Mapping to transfer function range and gamma correction:
      vec4 color = texture(ObjectTextures[3], vec2(colVal, 0.5f));
      color.w = alphaVal;

      FragColor = color;
//      gl_FragDepth = p.w;
}

