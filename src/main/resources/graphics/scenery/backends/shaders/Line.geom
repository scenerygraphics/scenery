#version 450 core

layout(lines) in;
layout(triangle_strip, max_vertices=4) out;

layout(location = 0) in VertexDataIn {
    vec4 Position;
    vec3 Normal;
    vec4 Color;
} VertexIn[];

layout(location = 0) out VertexData {
    vec3 Position;
    vec3 Normal;
    vec4 Color;
} Vertex;

layout(set = 1, binding = 0) uniform LightParameters {
    mat4 ViewMatrices[2];
    mat4 InverseViewMatrices[2];
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec3 CamPosition;
};


layout(set = 4, binding = 0) uniform ShaderProperties {
    vec4 startColor;
    vec4 endColor;
    vec4 lineColor;
    int capLength;
    int vertexCount;
    float edgeWidth;
};

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

void main() {
    vec3 p1 = gl_in[0].gl_Position.xyz / gl_in[0].gl_Position.w;
    vec3 p2 = gl_in[1].gl_Position.xyz / gl_in[1].gl_Position.w;

    // tangent
    vec2 t = normalize(p1.xy - p2.xy);
    // binormal
    vec2 n = vec2(-t.y, t.x) * edgeWidth;

    if ( p1.z < 0 || p2.z < 0 || p1.z > 1 || p2.z > 1 )  {
        return;
    }

    vec4 a = vec4( p1.xy + 0.5*n, p1.z, 1.0);
    vec4 b = vec4( p1.xy - 0.5*n, p1.z, 1.0);
    vec4 c = vec4( p2.xy + 0.5*n, p2.z, 1.0);
    vec4 d = vec4( p2.xy - 0.5*n, p2.z, 1.0);

//    vec3 N = normalize(cross(normalize(a.xyz-b.xyz), normalize(c.xyz)));
    vec3 N = vec3(-1.0/0.0, -1.0/0.0, -1.0/0.0);

    gl_Position = a;
    Vertex.Position = gl_Position.xyz;
    Vertex.Normal = N;
    Vertex.Color = VertexIn[0].Color;
    EmitVertex();

    gl_Position = b;
    Vertex.Position = gl_Position.xyz;
    Vertex.Normal = N;
    Vertex.Color = VertexIn[0].Color;
    EmitVertex();

    gl_Position = c;
    Vertex.Position = gl_Position.xyz;
    Vertex.Normal = N;
    Vertex.Color = VertexIn[1].Color;
    EmitVertex();

    gl_Position = d;
    Vertex.Position = gl_Position.xyz;
    Vertex.Normal = N;
    Vertex.Color = VertexIn[1].Color;
    EmitVertex();

    EndPrimitive();
}
