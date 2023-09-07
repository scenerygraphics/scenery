#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

layout(location = 0) out VertexData {
    vec3 FragPosition;
    vec3 Normal;
    vec2 TexCoord;
} Vertex;


layout(set = 0, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
    mat4 inverseProjectionMatrices[2];
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

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

void main()
{
    mat4 mv;
    mat4 nMVP;
    mat4 projectionMatrix;

    mv = (vrParameters.stereoEnabled ^ 1) * ViewMatrices[0] + (vrParameters.stereoEnabled * ViewMatrices[currentEye.eye]);
    projectionMatrix = (vrParameters.stereoEnabled ^ 1) * ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];

    nMVP = projectionMatrix*mv;
    nMVP[3] = vec4(0.0f, 0.0f, 0.0f, 1.0f);

    Vertex.FragPosition = vec3(ubo.ModelMatrix * vec4(vertexPosition, 1.0));
    Vertex.Normal = mat3(ubo.NormalMatrix) * normalize(vertexNormal);
    Vertex.TexCoord = vertexTexCoord;

    gl_PointSize = 1.0;
    vec4 ndc = nMVP * vec4(vertexPosition, 1.0);
    gl_Position = ndc.xyww;
}

