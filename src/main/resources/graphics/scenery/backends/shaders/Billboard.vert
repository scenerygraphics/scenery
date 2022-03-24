#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

layout(location = 0) out VertexData {
    vec3 Position;
    vec2 TexCoord;
    vec3 Normal; //hold sizes if geometryBillboarding is used in .xy
} Vertex;

layout(location = 3) out CameraDataOut {
    vec3 CamPosition;
    mat4 Transform;
    mat4 VP;
} Camera;

layout(set = 4, binding = 0) uniform ShaderProperties {
    int UseGeometryBillboarding;
};

layout(set = 2, binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 NormalMatrix;
	int isBillboard;
} ubo;

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
}lightParams;

layout(push_constant) uniform currentEye_t {
    int eye;
} currentEye;

void main()
{
    if(UseGeometryBillboarding == 0)
    {
        mat4 mv;
        mat4 nMVP;
        mat4 projectionMatrix;

        mv = (vrParameters.stereoEnabled ^ 1) * lightParams.ViewMatrices[0] * ubo.ModelMatrix + (vrParameters.stereoEnabled * lightParams.ViewMatrices[currentEye.eye] * ubo.ModelMatrix);
        projectionMatrix = (vrParameters.stereoEnabled ^ 1) * lightParams.ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];


        /*mv[0][0] = 1.0f;
        mv[0][1] = 0.0f;
        mv[0][2] = 0.0f;

        mv[1][0] = 0.0f;
        mv[1][1] = 1.0f;
        mv[1][2] = 0.0f;

        mv[2][0] = 0.0f;
        mv[2][1] = 0.0f;
        mv[2][2] = 1.0f;*/


        nMVP = projectionMatrix*mv;

        Vertex.Normal = mat3(ubo.NormalMatrix) * normalize(vertexNormal);
        Vertex.TexCoord = vertexTexCoord;

        gl_PointSize = 1.0;
        //gl_Position = vrParameters.projectionMatrices[currentEye.eye] * ubo.ModelMatrix * vec4(vertexPosition, 1.0);
        gl_Position = ubo.ModelMatrix * vec4(vertexPosition, 1.0);
        Vertex.Position = gl_Position.xyz;
    }
    else
    {
        Vertex.Position = vertexPosition;
        Vertex.TexCoord = vec2(vertexTexCoord.x, -vertexTexCoord.y);
        Vertex.Normal = vertexNormal;

        gl_Position = vec4(Vertex.Position, 1.0);

        Camera.CamPosition = lightParams.CamPosition;
        Camera.VP = lightParams.ProjectionMatrix * lightParams.ViewMatrices[0];
        Camera.Transform = lightParams.InverseViewMatrices[0];
    }
}

