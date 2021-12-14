#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTex;

layout(location = 0) out VertexData {
    flat vec3 Position;
    flat vec2 Properties;
    flat vec3 Color;
} Vertex;

layout(location = 3) out CameraDataOut {
    vec3 CamPosition;
    mat4 Transform;
    mat4 VP;
} Camera;

struct Light {
    float Linear;
    float Quadratic;
    float Intensity;
    float Radius;
    vec4 Position;
    vec4 Color;
};

const int MAX_NUM_LIGHTS = 1024;

layout(set = 0, binding = 0) uniform LightParameters {
    mat4 ViewMatrices[2];
    mat4 InverseViewMatrices[2];
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec3 CamPosition;
} lightParams;

void main()
{
    Vertex.Position = vertexPosition;
    Vertex.Properties = vertexTex;
    Vertex.Color = vertexNormal;

    gl_Position = vec4(Vertex.Position, 1.0);

    Camera.CamPosition = lightParams.CamPosition;
    Camera.VP = lightParams.ProjectionMatrix * lightParams.ViewMatrices[0];
    Camera.Transform = lightParams.InverseViewMatrices[0];
}


