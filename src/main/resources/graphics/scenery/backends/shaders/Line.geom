#version 450 core

layout(lines_adjacency) in;
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

layout(set = 0, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
    mat4 headShift;
    float IPD;
    int stereoEnabled;
} vrParameters;

layout(set = 1, binding = 0) uniform LightParameters {
    mat4 ViewMatrices[2];
    mat4 InverseViewMatrices[2];
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec3 CamPosition;
};

layout(set = 2, binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 NormalMatrix;
	int isBillboard;
} ubo;

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
    vec3 p1 = VertexIn[0].Position.xyz / VertexIn[0].Position.w;
    vec3 p2 = VertexIn[1].Position.xyz / VertexIn[1].Position.w;

    vec2 v = normalize(p1.xy - p2.xy);
    vec2 n = vec2(-v.y, v.x) * edgeWidth;

    if ( p1.z < 0 || p2.z < 0 || p1.z > 1 || p2.z > 1 )  {
            return;
    }

    gl_Position = vec4( p1.xy + 0.5*n, p1.z, 1.0);
    Vertex.Position = gl_Position.xyz;
    Vertex.Normal = normalize(CamPosition);
    Vertex.Color = VertexIn[0].Color;
    EmitVertex();

    gl_Position = vec4( p1.xy - 0.5*n, p1.z, 1.0);
    Vertex.Position = gl_Position.xyz;
    Vertex.Normal = normalize(CamPosition);
    Vertex.Color = VertexIn[0].Color;
    EmitVertex();

    gl_Position = vec4( p2.xy + 0.5*n, p2.z, 1.0);
    Vertex.Position = gl_Position.xyz;
    Vertex.Normal = normalize(CamPosition);
    Vertex.Color = VertexIn[0].Color;
    EmitVertex();

    gl_Position = vec4( p2.xy - 0.5*n, p2.z, 1.0);
    Vertex.Position = gl_Position.xyz;
    Vertex.Normal = normalize(CamPosition);
    Vertex.Color = VertexIn[0].Color;
    EmitVertex();

    EndPrimitive();
}
