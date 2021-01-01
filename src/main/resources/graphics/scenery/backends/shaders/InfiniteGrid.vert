#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

layout(location = 0) out VertexData {
    vec3 nearPosition;
    vec3 farPosition;
    mat4 pv;
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

//layout(set = 2, binding = 0) uniform Matrices {
//    mat4 ModelMatrix;
//    mat4 NormalMatrix;
//    int isBillboard;
//} ubo;


layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

vec3 unprojectPoint(vec3 p, mat4 inverseView, mat4 inverseProjection) {
    vec4 unprojectedPoint = inverseView * inverseProjection * vec4(p, 1.0);
    return (unprojectedPoint.xyz / unprojectedPoint.w);
}

vec3 gridPlane[6] = vec3[] (
    vec3(1, 1, 0), vec3(-1, -1, 0), vec3(-1, 1, 0),
    vec3(-1, -1, 0), vec3(1, 1, 0), vec3(1, -1, 0)
);

void main() {
    mat4 view;
    mat4 projectionMatrix;

    view = (vrParameters.stereoEnabled ^ 1) * ViewMatrices[0] + (vrParameters.stereoEnabled * ViewMatrices[currentEye.eye]);
    projectionMatrix = (vrParameters.stereoEnabled ^ 1) * ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];

    mat4 inverseProjection = (vrParameters.stereoEnabled ^ 1) * InverseProjectionMatrix + (vrParameters.stereoEnabled * vrParameters.inverseProjectionMatrices[currentEye.eye]);
    mat4 inverseView = inverse(view);

    vec3 p = gridPlane[gl_VertexIndex];

#ifndef OPENGL
    Vertex.nearPosition = unprojectPoint(vec3(p.x, p.y, 0.0), inverseView, inverseProjection);
#else
    Vertex.nearPosition = unprojectPoint(vec3(p.x, p.y, -1.0), inverseView, inverseProjection);
#endif
    Vertex.farPosition = unprojectPoint(vec3(p.x, p.y, 1.0), inverseView, inverseProjection);
    Vertex.pv = projectionMatrix * view;

    gl_PointSize = 1.0;
    gl_Position = vec4(p, 1.0);
}


