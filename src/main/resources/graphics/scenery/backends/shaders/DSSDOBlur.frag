#version 450 core
#extension GL_ARB_separate_shader_objects: enable
#extension GL_EXT_control_flow_attributes : enable

// DSSDO method adapted from DSSDO RenderMonkey example, https://github.com/kayru/dssdo
// MIT License, Copyright (c) 2011 Yuriy O'Donnell
// Original reference for SSDO: Ritschel, et al.
// https://people.mpi-inf.mpg.de/~ritschel/Papers/SSDO.pdf

#define PI 3.14159265359

layout(set = 1, binding = 0) uniform sampler2D InputNormalsMaterial;
layout(set = 1, binding = 1) uniform sampler2D InputDiffuseAlbedo;
layout(set = 1, binding = 2) uniform sampler2D InputZBuffer;
layout(set = 1, binding = 3) uniform sampler2D InputEmission;
layout(set = 1, binding = 4) uniform sampler2D InputReveal;
layout(set = 2, binding = 0) uniform sampler2D InputOcclusion;

layout(location = 0) out vec4 FragColor;
layout(location = 0) in vec2 textureCoord;

layout(set = 0, binding = 0, std140) uniform ShaderParameters {
	int displayWidth;
	int displayHeight;
	vec2 Direction;
};

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

void main() {
    const vec2 textureCoord = gl_FragCoord.xy/vec2(displayWidth, displayHeight);
    const float indices[9] = {-4, -3, -2, -1, 0, +1, +2, +3, +4};
    const vec2 step = Direction/vec2(displayWidth, displayHeight).xy;

    vec3 normal[9];
    vec4 res = vec4(0.0);

    float weights[9] =
    {
        0.013519569015984728,
        0.047662179108871855,
        0.11723004402070096,
        0.20116755999375591,
        0.240841295721373,
        0.20116755999375591,
        0.11723004402070096,
        0.047662179108871855,
        0.013519569015984728
    };

    normal[0] = DecodeOctaH(texture(InputNormalsMaterial, textureCoord + indices[0]*step).rg);
    normal[1] = DecodeOctaH(texture(InputNormalsMaterial, textureCoord + indices[1]*step).rg);
    normal[2] = DecodeOctaH(texture(InputNormalsMaterial, textureCoord + indices[2]*step).rg);
    normal[3] = DecodeOctaH(texture(InputNormalsMaterial, textureCoord + indices[3]*step).rg);
    normal[4] = DecodeOctaH(texture(InputNormalsMaterial, textureCoord + indices[4]*step).rg);
    normal[5] = DecodeOctaH(texture(InputNormalsMaterial, textureCoord + indices[5]*step).rg);
    normal[6] = DecodeOctaH(texture(InputNormalsMaterial, textureCoord + indices[6]*step).rg);
    normal[7] = DecodeOctaH(texture(InputNormalsMaterial, textureCoord + indices[7]*step).rg);
    normal[8] = DecodeOctaH(texture(InputNormalsMaterial, textureCoord + indices[8]*step).rg);

    float total_weight = 1.0;
    float discard_threshold = 0.85;

    [[unroll]] for(int i = 0; i < 9; i++) {
        if(dot(normal[i], normal[4]) < discard_threshold) {
            total_weight -= weights[i];
            weights[i] = 0;
        }
    }

    [[unroll]] for(int i = 0; i < 9; i++) {
        res += texture(InputOcclusion, textureCoord + indices[i]*step) * weights[i];
    }

    FragColor = res/total_weight;
}
