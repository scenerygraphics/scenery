#version 410 core

layout(points) in;
layout(triangle_strip, max_vertices=4) out;

in VertexData {
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
    vec4 Color;
    mat4 nMVP;
} VertexIn[];

out VertexData {
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
    vec4 Color;
} VertexOut;

uniform mat4 ModelViewMatrix;
uniform mat4 ModelMatrix;
uniform mat4 ProjectionMatrix;
uniform mat4 MVP;

void main() {
    float pointRadius = VertexIn[0].TexCoord.x;
    float w = gl_Position.w;

    vec4 coord;


    // (-,-,0)
    coord = vec4( ( VertexIn[0].FragPosition.xyz + vec3(-pointRadius,-pointRadius,0) ), 1.0 );
    gl_PrimitiveID = gl_PrimitiveIDIn;
    VertexOut.FragPosition = coord.xyz;
    VertexOut.Normal = VertexIn[0].Normal;
    VertexOut.TexCoord = vec2(0.0, 0.0);
    VertexOut.Color = VertexIn[0].Color;
    gl_Position = VertexIn[0].nMVP * coord;
    EmitVertex();

    // (+,-,0)
    coord = vec4( ( VertexIn[0].FragPosition.xyz + vec3(pointRadius,-pointRadius,0) ), 1.0 );
    gl_PrimitiveID = gl_PrimitiveIDIn + 1;
    VertexOut.FragPosition = coord.xyz;
    VertexOut.Normal = VertexIn[0].Normal;
    VertexOut.TexCoord = vec2(1.0, 0.0);
    VertexOut.Color = VertexIn[0].Color;
    gl_Position = VertexIn[0].nMVP * coord;
    EmitVertex();

    // (-,+,0)
    coord = vec4( ( VertexIn[0].FragPosition.xyz + vec3(-pointRadius,pointRadius,0) ), 1.0 );
    gl_PrimitiveID = gl_PrimitiveIDIn + 2;
    VertexOut.FragPosition = coord.xyz;
    VertexOut.Normal = VertexIn[0].Normal;
    VertexOut.TexCoord = vec2(0.0, 1.0);
    VertexOut.Color = VertexIn[0].Color;
    gl_Position = VertexIn[0].nMVP * coord;
    EmitVertex();

    // (+,+,0)
    coord = vec4(  ( VertexIn[0].FragPosition.xyz + vec3(pointRadius,pointRadius,0) ), 1.0 );
    gl_PrimitiveID = gl_PrimitiveIDIn + 3;
    VertexOut.FragPosition = coord.xyz;
    VertexOut.Normal = VertexIn[0].Normal;
    VertexOut.TexCoord = vec2(1.0, 1.0);
    VertexOut.Color = VertexIn[0].Color;
    gl_Position = VertexIn[0].nMVP * coord;
    EmitVertex();

    EndPrimitive();
}
