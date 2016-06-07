#version 400 core

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;
layout(location = 3) in vec4 instancedModel;
layout(location = 7) in vec4 instancedModelView;
layout(location = 11) in vec4 instancedMVP;

out VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
    vec4 Color;
} VertexOut;

uniform mat4 ModelViewMatrix;
uniform mat4 ModelMatrix;
uniform mat3 NormalMatrix;
uniform mat4 ProjectionMatrix;
uniform mat4 MVP;
uniform vec3 CamPosition;

uniform int isBillboard = 0;

void main()
{
    mat4 mv = ModelViewMatrix;
    if(isBillboard > 0) {
        mv[0][0] = 1.0f;
        mv[0][1] = .0f;
        mv[0][2] = .0f;

        mv[1][0] = .0f;
        mv[1][1] = 1.0f;
        mv[1][2] = .0f;

        mv[2][0] = .0f;
        mv[2][1] = .0f;
        mv[2][2] = 1.0f;
    }
    VertexOut.Normal = transpose(inverse(mat3(ModelMatrix)))*vertexNormal;
    VertexOut.Position = vec3( mv * vec4(vertexPosition, 1.0));
    VertexOut.TexCoord = vertexTexCoord;
    VertexOut.FragPosition = vec3(ModelMatrix * vec4(vertexPosition, 1.0));
    VertexOut.Color = vec4(0.0f, 0.0f, 0.0f, 0.0f);

    mat4 nMVP = ProjectionMatrix*mv;

    gl_Position = nMVP * vec4(vertexPosition, 1.0);
}


