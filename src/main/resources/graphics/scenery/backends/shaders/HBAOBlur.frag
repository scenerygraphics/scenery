#version 450 core
#extension GL_ARB_separate_shader_objects: enable
#extension GL_EXT_control_flow_attributes : enable

/*
Adapted from NVpro SDK sample -- https://github.com/nvpro-samples/gl_ssao

-----------------------------------------------------------------------
  Copyright (c) 2014, NVIDIA. All rights reserved.
  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions
  are met:
   * Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
   * Neither the name of its contributors may be used to endorse
     or promote products derived from this software without specific
     prior written permission.
  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS ``AS IS'' AND ANY
  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
  PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
  OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
-----------------------------------------------------------------------
*/

layout(set = 1, binding = 0) uniform sampler2D InputZBuffer;
layout(set = 2, binding = 0) uniform sampler2D InputOcclusion;

layout(location = 0) out vec4 FragColor;
layout(location = 0) in vec2 textureCoord;

layout(set = 0, binding = 0, std140) uniform ShaderParameters {
	int displayWidth;
	int displayHeight;
	vec2 Direction;
	float Sharpness;
};

const float KERNEL_RADIUS = 3.0f;

vec4 BlurFunction(vec2 uv, float r, vec4 center_c, float center_d, inout float w_total)
{
  vec4  c = texture( InputOcclusion, uv );
  float d = texture( InputZBuffer, uv).x;

  const float BlurSigma = float(KERNEL_RADIUS) * 0.5;
  const float BlurFalloff = 1.0 / (2.0*BlurSigma*BlurSigma);

  float ddiff = (d - center_d) * Sharpness;
  float w = exp2(-r*r*BlurFalloff - ddiff*ddiff);
  w_total += w;

  return c*w;
}

void main() {
  const vec2 texCoord = gl_FragCoord.xy/vec2(displayWidth, displayHeight);
  vec4  center_c = texture( InputOcclusion, texCoord );
  float center_d = texture( InputZBuffer, texCoord ).x;

  vec4  c_total = center_c;
  float w_total = 1.0;

  vec2 invDir = Direction / vec2(displayWidth, displayHeight);

  [[unroll]] for (float r = 1; r <= KERNEL_RADIUS; ++r)
  {
    vec2 uv = texCoord + invDir * r;
    c_total += BlurFunction(uv, r, center_c, center_d, w_total);
  }

  [[unroll]] for (float r = 1; r <= KERNEL_RADIUS; ++r)
  {
    vec2 uv = texCoord - invDir * r;
    c_total += BlurFunction(uv, r, center_c, center_d, w_total);
  }

  FragColor = c_total/w_total;
}

