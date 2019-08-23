#pragma scenery verbatim
layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

layout(location = 0) out VertexData {
	vec2 textureCoord;
	mat4 inverseProjection;
	mat4 inverseView;
} Vertex;

layout(set = 0, binding = 0) uniform VRParameters {
	mat4 projectionMatrices[2];
	mat4 inverseProjectionMatrices[2];
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

layout(push_constant) uniform currentEye_t {
	int eye;
} currentEye;
#pragma scenery endverbatim

void main()
{
	mat4 view;
	mat4 projectionMatrix;

	view = (vrParameters.stereoEnabled ^ 1) * ViewMatrices[0] + (vrParameters.stereoEnabled * ViewMatrices[currentEye.eye]);
	projectionMatrix = (vrParameters.stereoEnabled ^ 1) * ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];

	Vertex.inverseProjection = (vrParameters.stereoEnabled ^ 1) * InverseProjectionMatrix + (vrParameters.stereoEnabled * vrParameters.inverseProjectionMatrices[currentEye.eye]);
	Vertex.inverseView = inverse(view);

	Vertex.textureCoord = vertexTexCoord;
	gl_Position = vec4(vertexPosition, 1.0f);
}
