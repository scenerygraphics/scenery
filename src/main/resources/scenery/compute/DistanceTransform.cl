// (c) 2013 David Web, http://www.david-web.appspot.com/cnt/OpenCLDistanceTransform/

__kernel void DistanceTransform(
  __global const float* vIn,
  __global float*       vOut,
  int iDx,
  int iDy)
{
  int iGID = get_global_id(0);

  if (iGID >= (iDx*iDy))
  {
    return;
  }

  float minVal = FLT_MAX;

  for(int y = 0; y < iDy; y++)
  {
    for(int x = 0; x < iDx; x++)
    {
      if(vIn[y*iDy + x] >= 1.0f)
      {
        int idX = iGID % iDy;
        int idY = iGID / iDy;
        float dist = sqrt( (float)((idX-x)*(idX-x) + (idY-y)*(idY-y)) );

        if(dist < minVal)
        {
          minVal = dist;
        }
      }
    }
  }

  vOut[iGID] = minVal;
}

__kernel void DistanceTransformByte(
  __global const unsigned char* vIn,
  __global unsigned char*       vOut,
  int iDx,
  int iDy)
{
  int iGID = get_global_id(0);

  if (iGID >= (iDx*iDy))
  {
    return;
  }

  float minVal = (float)max(iDx, iDy);
  for(int y = 0; y < iDy; y++)
  {
    for(int x = 0; x < iDx; x++)
    {
      if(vIn[y*iDy + x] >= 254.0f)
      {
        int idX = iGID % iDy;
        int idY = iGID / iDy;
        float dist = sqrt( (float)((idX-x)*(idX-x) + (idY-y)*(idY-y)) );

        if(dist < minVal)
        {
          minVal = dist;
        }

      }
    }
  }

  unsigned char val = (unsigned char)floor((minVal));
  vOut[iGID] = val;

}

// Adapted version for Signed Distance Fields

__kernel void SignedDistanceTransformByte(
  __global const unsigned char* vIn,
  __global float*       vOut,
  int iDx,
  int iDy,
  int max_dist)
{
  int iGID = get_global_id(0);

  if (iGID >= (iDx*iDy))
  {
    return;
  }
  int idX = iGID % iDy;
  int idY = iGID / iDy;
  float2 pos = (float2)(idX, idY);

  int minX = clamp( idX - max_dist, 0, iDx );
  int maxX = clamp( idX + max_dist, 0, iDx );
  int minY = clamp( idY - max_dist, 0, iDy );
  int maxY = clamp( idY + max_dist, 0, iDy );

  float minVal = max(iDx, iDy);
  for(int y = minY; y < maxY; y++)
  {
	for(int x = minX; x < maxX; x++)
	{
	  float dist = fast_length( pos - (float2)(x, y) );
	  if(dist < minVal && fabs((float)(vIn[y*iDy + x] - vIn[iGID])) > 0)
	  {
		  minVal = dist;
	  }
	}
  }
  if (vIn[iGID] < 254.0f)
	minVal = -minVal;

//  unsigned char val = (unsigned char)floor((minVal+192.0f));
//  val = clamp((int)val, (int)0, (int)255);

  if(minVal > max_dist) {
    vOut[iGID] = max_dist;
  } else {
    vOut[iGID] = minVal + 0.5f;
  }
}
