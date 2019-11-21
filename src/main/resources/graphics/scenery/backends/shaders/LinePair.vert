#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

layout(location = 0) out VertexDataIn {
    vec4 Position;
    vec3 Normal;
    vec4 Color;
} Vertex;

layout(set = 0, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
    mat4 headShift;
    float IPD;
    int stereoEnabled;
} vrParameters;

const int MAX_NUM_LIGHTS = 1024;

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
    float interpolationState;
};

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

void main()
{
    mat4 mv;
    mat4 nMVP;
    mat4 projectionMatrix;

    mv = (vrParameters.stereoEnabled ^ 1) * ViewMatrices[0] * ubo.ModelMatrix + (vrParameters.stereoEnabled * ViewMatrices[currentEye.eye] * ubo.ModelMatrix);
    projectionMatrix = (vrParameters.stereoEnabled ^ 1) * ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];

    nMVP = projectionMatrix*mv;

    Vertex.Normal = mat3(ubo.NormalMatrix) * normalize(vertexNormal);
    float mixDegree = interpolationState;

    // We interpolate the vertex position from the actual position and a second (e.g. bundled) position stored in the
    // normal array
    Vertex.Position = nMVP * vec4(mix(vertexPosition, vertexNormal, mixDegree), 1.0);
    gl_Position = Vertex.Position;

    Vertex.Color = lineColor;

    //   if(gl_VertexID < capLength) {
    if(0 < capLength) {
        Vertex.Color = startColor;
    }

    //   if(gl_VertexID > vertexCount-capLength) {
    if(vertexCount > vertexCount-capLength) {
        Vertex.Color = endColor;
    }
}
