#version 410 core

layout(lines) in;
layout(triangle_strip, max_vertices=4) out;

uniform float EdgeWidth = 0.004;

in VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
    vec4 Color;
} VertexIn[];

out VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
    vec4 Color;
} VertexOut;

void emitCenteredEdgeQuad(vec3 p1, vec3 p2) {
    vec2 v = normalize(p1.xy - p2.xy);
    vec2 n = vec2(-v.y, v.x) * EdgeWidth;

    gl_Position = vec4( p1.xy + 0.5*n, p1.z, 1.0);
    VertexOut.Position = gl_Position.xyz;
    VertexOut.Normal = vec3(n, 1.0);
    VertexOut.TexCoord = vec2(0.0, 0.0);
    VertexOut.FragPosition = VertexIn[0].FragPosition;
    VertexOut.Color = VertexIn[0].Color;
    EmitVertex();

    gl_Position = vec4( p1.xy - 0.5*n, p1.z, 1.0);
    VertexOut.Position = gl_Position.xyz;
    VertexOut.Normal = vec3(n, 1.0);
    VertexOut.TexCoord = vec2(0.0, 0.0);
    VertexOut.FragPosition = VertexIn[1].FragPosition;
    VertexOut.Color = VertexIn[0].Color;
    EmitVertex();

    gl_Position = vec4( p2.xy + 0.5*n, p2.z, 1.0);
    VertexOut.Position = gl_Position.xyz;
    VertexOut.Normal = vec3(n, 1.0);
    VertexOut.TexCoord = vec2(0.0, 0.0);
    VertexOut.FragPosition = VertexIn[0].FragPosition;
    VertexOut.Color = VertexIn[0].Color;
    EmitVertex();

    gl_Position = vec4( p2.xy - 0.5*n, p2.z, 1.0);
    VertexOut.Position = gl_Position.xyz;
    VertexOut.Normal = vec3(n, 1.0);
    VertexOut.TexCoord = vec2(0.0, 0.0);
    VertexOut.FragPosition = VertexIn[1].FragPosition;
    VertexOut.Color = VertexIn[0].Color;
    EmitVertex();

    EndPrimitive();
}

void main() {
    vec3 p0 = gl_in[0].gl_Position.xyz / gl_in[0].gl_Position.w;
    vec3 p1 = gl_in[1].gl_Position.xyz / gl_in[1].gl_Position.w;
    emitCenteredEdgeQuad(p0, p1);
}