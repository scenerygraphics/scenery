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
  int iDy)
{
  int iGID = get_global_id(0);

  if (iGID >= (iDx*iDy))
  {
    return;
  }

  float minVal = FLT_MAX;
  float sign = 0.0f;
  float max_dist = 50.0f;

  for(int y = 0; y < iDy; y++)
  {
    for(int x = 0; x < iDx; x++)
    {
    int idX = iGID % iDy;
            int idY = iGID / iDy;
            float dist = (float)((idX-x)*(idX-x) + (idY-y)*(idY-y));


//      if(dist > max_dist) {
//        continue;
//      }

      if(vIn[y*iDy + x] >= 254.0f)
      {
        sign = -1.0f;

        if(fabs(dist) < fabs(minVal)  && vIn[iGID] < 254.0f)
        {
          minVal = sign*dist;
        }
      } else {
        sign = 1.0f;

        if(fabs(dist) < fabs(minVal) && vIn[iGID] >= 254.0f)
        {
          minVal = sign*dist;
        }
      }
    }
  }

//  unsigned char val = (unsigned char)floor((minVal+192.0f));
//  val = clamp((int)val, (int)0, (int)255);

#ifdef DEBUG
    printf("%d %f %d\n", val, minVal, vOut[iGID]);
#endif

  if(minVal > 1000.0f) {
    vOut[iGID] = 1000.0f;
  } else {
    vOut[iGID] = minVal + 0.5f;
  }
}