#version 410 core

layout (location = 0) out vec3 gPosition;
layout (location = 1) out vec3 gNormal;
layout (location = 2) out vec4 gAlbedoSpec;

in VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
    vec4 Color;
} VertexIn;

uniform int vertexCount;
uniform vec3 startColor;
uniform vec3 endColor;
uniform vec3 lineColor;
uniform int capLength;

void main()
{
    // Store the fragment position vector in the first gbuffer texture
    gPosition = VertexIn.FragPosition;
    // Also store the per-fragment normals into the gbuffer
    gNormal = normalize(VertexIn.Normal);
    // And the diffuse per-fragment color
    gAlbedoSpec.rgb = VertexIn.Color.rgb;
    // Store specular intensity in gAlbedoSpec's alpha component
    gAlbedoSpec.a = 0.2f;
}
