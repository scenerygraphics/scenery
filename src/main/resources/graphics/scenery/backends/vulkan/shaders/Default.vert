#version 450 core
#extension GL_ARB_separate_shader_objects: enable

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 5;
const int MAX_NUM_LIGHTS = 128;

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Shininess;
};

layout(location = 0) out VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
} VertexOut;

layout(binding = 0) uniform Matrices {
	mat4 ModelViewMatrix;
	mat4 ModelMatrix;
	mat4 ProjectionMatrix;
	mat4 MVP;
	vec3 CamPosition;
	int isBillboard;
} ubo;

layout(binding = 1) uniform MaterialProperties {
    MaterialInfo Material;
    int materialType;
};

layout(set = 1, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];

void main()
{
    mat4 mv = ubo.ModelViewMatrix;
    mat4 nMVP;

	if(ubo.isBillboard > 0) {
		mv[0][0] = 1.0f;
		mv[0][1] = .0f;
		mv[0][2] = .0f;

		mv[1][0] = .0f;
		mv[1][1] = 1.0f;
		mv[1][2] = .0f;

		mv[2][0] = .0f;
		mv[2][1] = .0f;
		mv[2][2] = 1.0f;

		nMVP = ubo.ProjectionMatrix*mv;
	} else {
	    nMVP = ubo.MVP;
	}

    VertexOut.Normal = transpose(inverse(mat3(ubo.ModelMatrix)))*vertexNormal;
 	VertexOut.Position = vec3( mv * vec4(vertexPosition, 1.0));
   	VertexOut.TexCoord = vertexTexCoord;
   	VertexOut.FragPosition = vec3(ubo.ModelMatrix * vec4(vertexPosition, 1.0));

   	gl_Position = nMVP * vec4(vertexPosition, 1.0);
}
