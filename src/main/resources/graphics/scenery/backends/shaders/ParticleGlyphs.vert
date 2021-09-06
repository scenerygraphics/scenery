#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;

layout(location = 0) out VertexData {
    flat vec3 Position;
    flat vec3 Properties;
} Vertex;

layout(location = 2) out CameraDataOut {
    vec3 CamPosition;
    mat4 Transform;
    mat4 VP;
} Camera;

layout(set = 0, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
    mat4 inverseProjectionMatrices[2];
    mat4 headShift;
    float IPD;
    int stereoEnabled;
} vrParameters;

struct Light {
    float Linear;
    float Quadratic;
    float Intensity;
    float Radius;
    vec4 Position;
    vec4 Color;
};

const int MAX_NUM_LIGHTS = 1024;

layout(set = 1, binding = 0) uniform LightParameters {
    mat4 ViewMatrices[2];
    mat4 InverseViewMatrices[2];
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec3 CamPosition;
} lightParams;

layout(set = 2, binding = 0) uniform Matrices {
    mat4 ModelMatrix;
    mat4 NormalMatrix;
    int isBillboard;
} ubo;

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

void main()
{
    //not needed, figure out how to remove without binding errors)...
    mat4 mv;
    mat4 nMVP;
    mat4 projectionMatrix;

    mv = (vrParameters.stereoEnabled ^ 1) * lightParams.ViewMatrices[0] * ubo.ModelMatrix + (vrParameters.stereoEnabled * lightParams.ViewMatrices[currentEye.eye] * ubo.ModelMatrix);
    projectionMatrix = (vrParameters.stereoEnabled ^ 1) * lightParams.ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];

    nMVP = projectionMatrix * mv;
    //...

    Vertex.Position = vertexPosition;
    Vertex.Properties = vertexNormal;

    gl_Position = vec4(Vertex.Position, 1.0);

    Camera.CamPosition = lightParams.CamPosition;
    Camera.VP = lightParams.ProjectionMatrix * lightParams.ViewMatrices[0];
    Camera.Transform = lightParams.InverseViewMatrices[0];
}


