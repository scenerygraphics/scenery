#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

layout(location = 0) out VertexDataIn {
    vec4 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
    vec4 Color;
} Vertex;


layout(set = 0, binding = 0) uniform VRParameters {
    mat4 projectionMatrices[2];
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
    mat4 ViewMatrix;
    vec3 CamPosition;
    int numLights;
	Light lights[MAX_NUM_LIGHTS];
};

layout(set = 2, binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 NormalMatrix;
	mat4 ProjectionMatrix;
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

void main()
{
	mat4 mv;
	mat4 nMVP;
	mat4 projectionMatrix;

    mat4 headToEye = vrParameters.headShift;
	headToEye[3][0] -= currentEye.eye * vrParameters.IPD;

    mv = (vrParameters.stereoEnabled ^ 1) * ViewMatrix * ubo.ModelMatrix + (vrParameters.stereoEnabled * headToEye * ViewMatrix * ubo.ModelMatrix);
	projectionMatrix = (vrParameters.stereoEnabled ^ 1) * ubo.ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];

	nMVP = projectionMatrix*mv;

    Vertex.Normal = mat3(ubo.NormalMatrix) * normalize(vertexNormal);
    Vertex.TexCoord = vertexTexCoord;
    Vertex.FragPosition = vec3(ubo.ModelMatrix * vec4(vertexPosition, 1.0));

	gl_Position = nMVP * vec4(vertexPosition, 1.0);
	Vertex.Position = gl_Position;

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
