/*
volume and iso surface rendering

		volume rendering adapted from the Nvidia sdk sample
  		http://developer.download.nvidia.com/compute/cuda/4_2/rel/sdk/website/OpenCL/html/samples.html


  Author: Martin Weigert (mweigert@mpi-cbg.de)
    	  Loic Royer		 (royer@mpi-cbg.de)
*/


// Loop unrolling length:
#define LOOPUNROLL 16

// Typedefs:
//typedef unsigned int  uint;
//typedef unsigned char uchar;

// random number generator for dithering
inline
float random(uint x, uint y)
{
    uint a = 4421 +(1+x)*(1+y) +x +y;

    for(uint i=0; i < 10; i++)
    {
        a = ((uint)1664525 * a + (uint)1013904223) % (uint)79197919;
    }

    float rnd = (a*1.0f)/(79197919.f);

    return rnd-0.5f;
}


// intersect ray with a box
// http://www.siggraph.org/education/materials/HyperGraph/raytrace/rtinter3.htm
inline
int intersectBox(float4 r_o, float4 r_d, float4 boxmin, float4 boxmax, float *tnear, float *tfar)
{
    // compute intersection of ray with all six bbox planes
    float4 invR = (float4)(1.0f,1.0f,1.0f,1.0f) / r_d;
    float4 tbot = invR * (boxmin - r_o);
    float4 ttop = invR * (boxmax - r_o);

    // re-order intersections to find smallest and largest on each axis
    float4 tmin = min(ttop, tbot);
    float4 tmax = max(ttop, tbot);

    // find the largest tmin and the smallest tmax
    float largest_tmin = max(max(tmin.x, tmin.y), max(tmin.x, tmin.z));
    float smallest_tmax = min(min(tmax.x, tmax.y), min(tmax.x, tmax.z));

	*tnear = largest_tmin;
	*tfar = smallest_tmax;

	return smallest_tmax > largest_tmin;
}


// convert float4 into uint:
inline
uint rgbaFloatToInt(float4 rgba)
{
    rgba = clamp(rgba,(float4)(0.f,0.f,0.f,0.f),(float4)(1.f,1.f,1.f,1.f));

    return ((uint)(rgba.w*255)<<24) | ((uint)(rgba.z*255)<<16) | ((uint)(rgba.y*255)<<8) | (uint)(rgba.x*255);
}

// convert float4 into uint and take the max with an existing RGBA value in uint form:
inline
uint rgbaFloatToIntAndMax(uint existing, float4 rgba)
{
    rgba = clamp(rgba,(float4)(0.f,0.f,0.f,0.f),(float4)(1.f,1.f,1.f,1.f));

    const uint nr = (uint)(rgba.x*255);
    const uint ng = (uint)(rgba.y*255);
    const uint nb = (uint)(rgba.z*255);
    const uint na = (uint)(rgba.w*255);

    const uint er = existing&0xFF;
    const uint eg = (existing>>8)&0xFF;
    const uint eb = (existing>>16)&0xFF;
    const uint ea = (existing>>24)&0xFF;

    const uint  r = max(nr,er);
    const uint  g = max(ng,eg);
    const uint  b = max(nb,eb);
    const uint  a = max(na,ea);

    return a<<24|b<<16|g<<8|r ;
}


// multiply matrix with vector
float4 mult(__constant float* M, float4 v){
  float4 res;
  res.x = dot(v, (float4)(M[0],M[1],M[2],M[3]));
  res.y = dot(v, (float4)(M[4],M[5],M[6],M[7]));
  res.z = dot(v, (float4)(M[8],M[9],M[10],M[11]));
  res.w = dot(v, (float4)(M[12],M[13],M[14],M[15]));
  return res;
}

__kernel void
dummy_render(
                                    __global float4* d_output,
									const	uint  imageW,
									const	uint  imageH,
									const	float brightness,
									const	float trangemin,
									const	float trangemax,
									const	float gamma,
									const   float alpha_blending,
									const	int   maxsteps,
									const	float dithering,
									const	float phase,
									const	int   clear,
									const	float boxMin_x,
									const float boxMax_x,
									const float boxMin_y,
									const float boxMax_y,
									const float boxMin_z,
									const float boxMax_z,
									__constant float* 		invP,
									__constant float* 		invM,
									__read_only image3d_t 	volume)
{
  const uint x = get_global_id(0);
  const uint y = get_global_id(1);

  d_output[x + y*imageW] = (float4)(0.0f, 0.0f, 1.0f, 1.0f);
}

// Render function,
// performs max projection and then uses the transfert function to obtain a color per pixel:
__kernel void
maxproj_render(
                                    __global float4	*d_output,
									const	uint  imageW,
									const	uint  imageH,
									const	float brightness,
									const	float trangemin,
									const	float trangemax,
									const	float gamma,
									const   float alpha_blending,
									const	int   maxsteps,
									const	float dithering,
									const	float phase,
									const	int   clear,
									const	float boxMin_x,
									const float boxMax_x,
									const float boxMin_y,
									const float boxMax_y,
									const float boxMin_z,
									const float boxMax_z,
									__read_only image2d_t   transferColor4,
									__constant float* 		invP,
									__constant float* 		invM,
									__read_only image3d_t 	volume)
{
	// samplers:
  const sampler_t volumeSampler   =   CLK_NORMALIZED_COORDS_TRUE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR ;
  const sampler_t transferSampler =   CLK_NORMALIZED_COORDS_TRUE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR ;

	// convert range bounds to linear map:
  const float ta = 1.f/(trangemax-trangemin);
  const float tb = trangemin/(trangemin-trangemax);

	// box bounds using the clipping box
  const float4 boxMin = (float4)(boxMin_x,boxMin_y,boxMin_z,1.f);
  const float4 boxMax = (float4)(boxMax_x,boxMax_y,boxMax_z,1.f);

	// thread int coordinates:
  const uint x = get_global_id(0);
  const uint y = get_global_id(1);

  if ((x >= imageW) || (y >= imageH)) return;

  // thread float coordinates:
  const float u = (x / (float) imageW)*2.0f-1.0f;
  const float v = (y / (float) imageH)*2.0f-1.0f;

  // front and back:
  const float4 front = (float4)(u,v,-1.f,1.f);
  const float4 back = (float4)(u,v,1.f,1.f);

  // calculate eye ray in world space
  float4 orig0, orig;
  float4 direc0, direc;

  orig0.x = dot(front, ((float4)(invP[0],invP[1],invP[2],invP[3])));
  orig0.y = dot(front, ((float4)(invP[4],invP[5],invP[6],invP[7])));
  orig0.z = dot(front, ((float4)(invP[8],invP[9],invP[10],invP[11])));
  orig0.w = dot(front, ((float4)(invP[12],invP[13],invP[14],invP[15])));

  orig0 *= 1.f/orig0.w;

  orig.x = dot(orig0, ((float4)(invM[0],invM[1],invM[2],invM[3])));
  orig.y = dot(orig0, ((float4)(invM[4],invM[5],invM[6],invM[7])));
  orig.z = dot(orig0, ((float4)(invM[8],invM[9],invM[10],invM[11])));
  orig.w = dot(orig0, ((float4)(invM[12],invM[13],invM[14],invM[15])));

  orig *= 1.f/orig.w;

  direc0.x = dot(back, ((float4)(invP[0],invP[1],invP[2],invP[3])));
  direc0.y = dot(back, ((float4)(invP[4],invP[5],invP[6],invP[7])));
  direc0.z = dot(back, ((float4)(invP[8],invP[9],invP[10],invP[11])));
  direc0.w = dot(back, ((float4)(invP[12],invP[13],invP[14],invP[15])));

  direc0 *= 1.f/direc0.w;

  direc0 = normalize(direc0-orig0);

  direc.x = dot(direc0, ((float4)(invM[0],invM[1],invM[2],invM[3])));
  direc.y = dot(direc0, ((float4)(invM[4],invM[5],invM[6],invM[7])));
  direc.z = dot(direc0, ((float4)(invM[8],invM[9],invM[10],invM[11])));
  direc.w = 0.0f;

//    printf("%f %f %f %f\n", invP[0], invP[5], invP[10], invP[15]);
//printf("orig: %f %f %f\n", orig.x, orig.y, orig.z);
//printf("dir: %f %f %f\n", direc.x, direc.y, direc.z);

  // find intersection with box
  float tnear, tfar;
  const int hit = intersectBox(orig,direc, boxMin, boxMax, &tnear, &tfar);
  if (!hit || tfar<=0)
  {
//  	d_output[x+imageW*y] = (float4)(orig.x, orig.y, orig.z, 1.0f);
  	d_output[x+imageW*y] = (float4)(0.0f, 0.0f, 0.0f, 1.0f);
  	return;
  }

//  d_output[x+imageW*y] = (float4)(tnear, tfar, 0.0f, 1.0f);
//  return;

  // clamp to near plane:
  if (tnear < 0.0f) tnear = 0.0f;

  // compute step size:
  const float tstep = fabs(tnear-tfar)/((maxsteps/LOOPUNROLL)*LOOPUNROLL);

  // apply phase:
  orig += phase*tstep*direc;

  // randomize origin point a bit:
  const uint entropy = (uint)( 6779514*fast_length(orig) + 6257327*fast_length(direc) );
  orig += dithering*tstep*random(entropy+x,entropy+y)*direc;

  // precompute vectors:
  const float4 vecstep = 0.5f*tstep*direc;
  float4 pos = orig*0.5f+0.5f + tnear*0.5f*direc;

  // Loop unrolling setup:
  const int unrolledmaxsteps = (maxsteps/LOOPUNROLL);

    // raycasting loop:
    float maxp = 0.0f;

    float mappedVal;

    if (alpha_blending<=0.f)
    {
      // No alpha blending:
  	  for(int i=0; i<unrolledmaxsteps; i++)
  	  {
  			for(int j=0; j<LOOPUNROLL; j++)
  			{
  	  		maxp = fmax(maxp,read_imagef(volume, volumeSampler, pos).x);
  	  		pos+=vecstep;
  			}
  		}

  		// Mapping to transfer function range and gamma correction:
  		mappedVal = clamp(pow(mad(ta,maxp,tb),gamma),0.f,1.f);
  	}
  	else
  	{
  	  // alpha blending:
  		float cumsum = 1.f;
  	  float decay_rate = alpha_blending*tstep;

  		for(int i=0; i<unrolledmaxsteps; i++)
  		{
  			for(int j=0; j<LOOPUNROLL; j++)
  			{
  		  	float new_val = read_imagef(volume, volumeSampler, pos).x;

  		  	//normalize to 0...1
  		  	float normalized_val = mad(ta,new_val,tb);
  		  	maxp = fmax(maxp,cumsum*normalized_val);
  		  	cumsum  *= native_exp(-decay_rate*normalized_val);
  	  		pos+=vecstep;
  			}
  		}

  		// Mapping to transfer function range and gamma correction:
      mappedVal = clamp(pow(maxp,gamma),0.f,1.f);
  	}


  	const float4 color = mappedVal;//brightness*read_imagef(transferColor4,transferSampler, (float2)(mappedVal,0.0f));

      // Alpha pre-multiply:
      color.x = color.x*color.w;
      color.y = color.y*color.w;
      color.z = color.z*color.w;

  // write output color:
  d_output[x + y*imageW] = (float4)color;//rgbaFloatToIntAndMax(clear*d_output[x + y*imageW],color); //d_output[x + y*imageW]

}


/****************************************************************************************************************/
__kernel void isosurface_render(
								__global uint	*d_output,
								const	uint  imageW,
								const	uint  imageH,
								const	float brightness,
								const	float trangemin,
								const	float trangemax,
								const	float gamma,
								const	int   maxsteps,
								const	float dithering,
								const	float phase,
								const	int   clear,
								const float lightX,
								const float lightY,
								const float lightZ,
								const	float boxMin_x,
								const float boxMax_x,
								const float boxMin_y,
								const float boxMax_y,
								const float boxMin_z,
								const float boxMax_z,
									__read_only image2d_t 	transferColor4,
								__constant float* 		invP,
								__constant float* 		invM,
								__read_only image3d_t 	volume
								)
{


	// samplers:
  const sampler_t volumeSampler   =   CLK_NORMALIZED_COORDS_TRUE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR ;
  const sampler_t transferSampler =   CLK_NORMALIZED_COORDS_TRUE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR ;

	// convert range bounds to linear map:
  const float ta = 1.f/(trangemax-trangemin);
  const float tb = trangemin/(trangemin-trangemax);

  // box bounds using the clipping box
  const float4 boxMin = (float4)(boxMin_x,boxMin_y,boxMin_z,1.f);
  const float4 boxMax = (float4)(boxMax_x,boxMax_y,boxMax_z,1.f);


	// thread int coordinates:
  const uint x = get_global_id(0);
  const uint y = get_global_id(1);

  if ((x >= imageW) || (y >= imageH)) return;

  // thread float coordinates:
  const float u = (x / (float) imageW)*2.0f-1.0f;
  const float v = (y / (float) imageH)*2.0f-1.0f;

  // front and back:
  const float4 front = (float4)(u,v,-1.f,1.f);
  const float4 back = (float4)(u,v,1.f,1.f);

  // calculate eye ray in world space
  float4 orig0, orig;
  float4 direc0, direc;

  orig0.x = dot(front, ((float4)(invP[0],invP[1],invP[2],invP[3])));
  orig0.y = dot(front, ((float4)(invP[4],invP[5],invP[6],invP[7])));
  orig0.z = dot(front, ((float4)(invP[8],invP[9],invP[10],invP[11])));
  orig0.w = dot(front, ((float4)(invP[12],invP[13],invP[14],invP[15])));

  orig0 *= 1.f/orig0.w;

  orig.x = dot(orig0, ((float4)(invM[0],invM[1],invM[2],invM[3])));
  orig.y = dot(orig0, ((float4)(invM[4],invM[5],invM[6],invM[7])));
  orig.z = dot(orig0, ((float4)(invM[8],invM[9],invM[10],invM[11])));
  orig.w = dot(orig0, ((float4)(invM[12],invM[13],invM[14],invM[15])));

  orig *= 1.f/orig.w;

  direc0.x = dot(back, ((float4)(invP[0],invP[1],invP[2],invP[3])));
  direc0.y = dot(back, ((float4)(invP[4],invP[5],invP[6],invP[7])));
  direc0.z = dot(back, ((float4)(invP[8],invP[9],invP[10],invP[11])));
  direc0.w = dot(back, ((float4)(invP[12],invP[13],invP[14],invP[15])));

  direc0 *= 1.f/direc0.w;

  direc0 = normalize(direc0-orig0);

  direc.x = dot(direc0, ((float4)(invM[0],invM[1],invM[2],invM[3])));
  direc.y = dot(direc0, ((float4)(invM[4],invM[5],invM[6],invM[7])));
  direc.z = dot(direc0, ((float4)(invM[8],invM[9],invM[10],invM[11])));
  direc.w = 0.0f;


  // find intersection with box
  float tnear, tfar;
  const int hit = intersectBox(orig,direc, boxMin, boxMax, &tnear, &tfar);
  if (!hit || tfar<=0)
  {
  	d_output[x+imageW*y] = 0.f;
  	return;
  }

  // clamp to near plane:
  if (tnear < 0.0f) tnear = 0.0f;

  // compute step size:
  const float tstep = fabs(tnear-tfar)/((maxsteps/LOOPUNROLL)*LOOPUNROLL);

  // randomize origin point a bit:
  const uint entropy = (uint)( 6779514*fast_length(orig) + 6257327*fast_length(direc) );
  orig += dithering*tstep*random(entropy+x,entropy+y)*direc;

  // precompute vectors:
  const float4 vecstep = 0.5f*tstep*direc;
  float4 pos = orig*0.5f+0.5f + tnear*0.5f*direc;

  // Loop unrolling setup:
  const int unrolledmaxsteps = (maxsteps/LOOPUNROLL);

	// iso value:
  float isoVal = mad(1.0f/ta,pow(0.5f,1.0f/gamma),-tb);

  // starting value:
  float newVal = 0; //read_imagef(volume, volumeSampler, pos).x;

	// is iso surface value greater or lower:
  bool isGreater = newVal>isoVal;

  bool hitIso = false;

  // first pass:
  for(int i=0; i<unrolledmaxsteps; i++)
  	{
  	  for(int j=1; j<LOOPUNROLL; j++)
  		{
  		  newVal = read_imagef(volume, volumeSampler, pos).x;
  		  if ((newVal>isoVal) != isGreater)
  		  {
  				hitIso = true;
  				break;
  			}

  		  pos+=vecstep;
  		}
  	  if (hitIso) break;
  	}


  //early termination if iso surface not hit:
 	if (!hitIso)
  {
  	d_output[x+imageW*y] = 0.f;
  	return;
  }

  //second pass:
  hitIso = false;
  pos-=2*vecstep;
  const float4 finevecstep = 3*vecstep/maxsteps;
  for(int i=0; i<unrolledmaxsteps; i++)
  	{
  	  for(int j=1; j<LOOPUNROLL; j++)
  		{
  		  newVal = read_imagef(volume, volumeSampler, pos).x;
  		  if ((newVal>isoVal) != isGreater)
  		  {
  				hitIso = true;
  				break;
  			}
  		  pos+=finevecstep;
  		}
  	  if (hitIso) break;
  	}
  /**/


  // find the real intersection point
  float oldVal = read_imagef(volume, volumeSampler, pos-finevecstep).x;
  float lam = (newVal - isoVal)/(newVal-oldVal);
  pos += lam*vecstep;


  // getting the normals and do some nice phong shading

  // build light vector:
  float4 light = (float4)(-lightX,-lightY,-lightZ,0);

  float c_diffuse = 0.2;
  float c_specular = 0.6;

  light = mult(invM,light);
  light = fast_normalize(light);

  // compute lateral step for normal calculation:
	const float latx = 1.0f/(get_image_width(volume));
	const float laty = 1.0f/(get_image_height(volume));
	const float latz = 1.0f/(get_image_depth(volume));


  // robust 2nd order normal estimation:
  float4 normal;
  normal.x = 2.f*read_imagef(volume,volumeSampler,pos+(float4)(latx,0,0,0)).x-
  	2.f*read_imagef(volume,volumeSampler,pos+(float4)(-latx,0,0,0)).x+
  	read_imagef(volume,volumeSampler,pos+(float4)(2.f*latx,0,0,0)).x-
  	read_imagef(volume,volumeSampler,pos+(float4)(-2.f*latx,0,0,0)).x;

  normal.y = 2.f*read_imagef(volume,volumeSampler,pos+(float4)(0,laty,0,0)).x-
  	2.f*read_imagef(volume,volumeSampler,pos+(float4)(0,-laty,0,0)).x+
  	read_imagef(volume,volumeSampler,pos+(float4)(0,2.f*laty,0,0)).x-
  	read_imagef(volume,volumeSampler,pos+(float4)(0,-2.f*laty,0,0)).x;

  normal.z = 2.f*read_imagef(volume,volumeSampler,pos+(float4)(0,0,latz,0)).x-
  	2.f*read_imagef(volume,volumeSampler,pos+(float4)(0,0,-latz,0)).x+
  	read_imagef(volume,volumeSampler,pos+(float4)(0,0,2.f*latz,0)).x-
  	read_imagef(volume,volumeSampler,pos+(float4)(0,0,-2.f*latz,0)).x;

  normal.w = 0;

  // flip normal if we are comming from values greater than isoVal...
  normal = (1.f-2*isGreater)*fast_normalize(normal);

	// Blinn-Phong specular reflection:
  //float diffuse = fmax(0.f,dot(light,normal));
  //float specular = native_powr(fmax(0.f,dot(fast_normalize(light+fast_normalize(direc)),fast_normalize(normal))),45);

  // Phong specular reflection:
  float diffuse = fmax(0.f,dot(light,normal));
  float4 reflect = 2*dot(light,normal)*normal-light;
  float specular = native_powr(fmax(0.f,dot(fast_normalize(reflect),fast_normalize(direc))),10);

  // Mapping to transfert function range and gamma correction:
  const float mappedVal = gamma;

	// lookup in transfer function texture:
  float4 color = read_imagef(transferColor4, transferSampler, (float2)(mappedVal,0.0f));

  const float lighting  =  (c_diffuse*diffuse + (diffuse>0)*c_specular*specular);

	// Apply lighting:
	const float3 lightcolor = (float3)(1.0f,1.0f,1.0f);
  color.x = mad(lightcolor.x,lighting,color.x);
  color.y = mad(lightcolor.y,lighting,color.y);
  color.z = mad(lightcolor.z,lighting,color.z);

  color = brightness*color;

  //d_output[x + y*imageW] = rgbaFloatToIntAndMax(0,color);
  d_output[x + y*imageW] = rgbaFloatToIntAndMax(clear*d_output[x + y*imageW],color);

}




// clears a buffer
__kernel void
clearbuffer(__global uint *buffer,
                     uint imageW,
                     uint imageH)
{

	// thread int coordinates:
  const uint x = get_global_id(0);
  const uint y = get_global_id(1);

   // clears buffer:
  if ((x < imageW) || (y < imageH))
  	buffer[x + y*imageW] = 0;
}
