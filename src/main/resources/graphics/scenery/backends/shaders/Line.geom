#version 450 core

layout(lines_adjacency) in;
layout(triangle_strip, max_vertices=4) out;

in VertexDataIn {
    vec4 Position;
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
} Vertex;

layout(set = 4, binding = 0) uniform ShaderProperties {
    vec4 startColor;
    vec4 endColor;
    vec4 lineColor;
    int capLength;
    int vertexCount;
    float edgeWidth;
};

void main() {
    vec3 p1 = VertexIn[0].Position.xyz / VertexIn[0].Position.w;
    vec3 p2 = VertexIn[1].Position.xyz / VertexIn[1].Position.w;

    vec2 v = normalize(p1.xy - p2.xy);
    vec2 n = vec2(-v.y, v.x) * edgeWidth;

    if ( p1.z < 0 || p2.z < 0 || p1.z > 1 || p2.z > 1 )  {
            return;
    }

    gl_Position = vec4( p1.xy + 0.5*n, p1.z, 1.0);
    Vertex.Position = gl_Position.xyz;
    Vertex.Normal = normalize(vec3(n, 1.0));
    Vertex.TexCoord = vec2(0.0, 0.0);
    Vertex.FragPosition = VertexIn[0].FragPosition;
    Vertex.Color = VertexIn[0].Color;
    EmitVertex();

    gl_Position = vec4( p1.xy - 0.5*n, p1.z, 1.0);
    Vertex.Position = gl_Position.xyz;
    Vertex.Normal = normalize(vec3(n, 1.0));
    Vertex.TexCoord = vec2(0.0, 0.0);
    Vertex.FragPosition = VertexIn[1].FragPosition;
    Vertex.Color = VertexIn[0].Color;
    EmitVertex();

    gl_Position = vec4( p2.xy + 0.5*n, p2.z, 1.0);
    Vertex.Position = gl_Position.xyz;
    Vertex.Normal = normalize(vec3(n, 1.0));
    Vertex.TexCoord = vec2(0.0, 0.0);
    Vertex.FragPosition = VertexIn[0].FragPosition;
    Vertex.Color = VertexIn[0].Color;
    EmitVertex();

    gl_Position = vec4( p2.xy - 0.5*n, p2.z, 1.0);
    Vertex.Position = gl_Position.xyz;
    Vertex.Normal = normalize(vec3(n, 1.0));
    Vertex.TexCoord = vec2(0.0, 0.0);
    Vertex.FragPosition = VertexIn[1].FragPosition;
    Vertex.Color = VertexIn[0].Color;
    EmitVertex();

    EndPrimitive();
}
