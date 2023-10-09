#version 450

layout(location = 0) out VertexData {
    vec2 textureCoord;
    mat4 projectionMatrix;
    mat4 viewMatrix;
    mat4 frustumVectors;
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

const vec4 frustumVectors[] = vec4[](
    // top left
    vec4(1.0, -1.0, 0.0, 1.0),
    // bottom left
    vec4(-1.0, -1.0, 0.0, 1.0),
    // bottom right
    vec4(-1.0, 1.0, 0.0, 1.0),
    // top right
    vec4(1.0, 1.0, 0.0, 1.0)
);

void main()
{
    Vertex.viewMatrix = (vrParameters.stereoEnabled ^ 1) * ViewMatrices[0] + (vrParameters.stereoEnabled * ViewMatrices[currentEye.eye]);
	Vertex.projectionMatrix = (vrParameters.stereoEnabled ^ 1) * ProjectionMatrix + vrParameters.stereoEnabled * vrParameters.projectionMatrices[currentEye.eye];

    mat4 inverseProjection = (vrParameters.stereoEnabled ^ 1) * InverseProjectionMatrix + (vrParameters.stereoEnabled * vrParameters.inverseProjectionMatrices[currentEye.eye]);

	for(uint i = 0; i < 4; ++i) {
	    vec4 start = frustumVectors[i];
	    start.z = -1.0f;
	    start = inverseProjection * start;
	    start /= start.w;

	    vec4 end = frustumVectors[i];
	    end.z = 1.0f;
	    end = inverseProjection * end;
	    end /= end.w;

	    Vertex.frustumVectors[i] = end - start;
	    Vertex.frustumVectors[i].xyz /= Vertex.frustumVectors[i].z;
	}

    Vertex.textureCoord = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(Vertex.textureCoord * 2.0f - 1.0f, 0.0f, 1.0f);
}
