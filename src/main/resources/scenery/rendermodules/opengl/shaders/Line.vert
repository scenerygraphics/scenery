#version 410 core
 
layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;
layout(location = 2) in vec2 vertexTexCoord;

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

uniform int vertexCount;

uniform vec4 startColor = vec4(1.0, 1.0, 1.0, 1.0);
uniform vec4 endColor = vec4(1.0, 1.0, 1.0, 1.0);

void main()
{
   VertexOut.Normal = transpose(inverse(mat3(ModelMatrix)))*vertexNormal;;
   VertexOut.Position = vec3(ModelViewMatrix*vec4(vertexPosition, 1.0));
   VertexOut.TexCoord = vertexTexCoord;
   VertexOut.FragPosition = vec3(ModelMatrix * vec4(vertexPosition, 1.0));

    gl_Position = MVP * vec4(vertexPosition, 1.0);

   if(gl_VertexID == 0 || gl_VertexID == 1) {
        VertexOut.Color = startColor;
        return;
   } if(gl_VertexID == vertexCount-1 || gl_VertexID == vertexCount-2) {
        VertexOut.Color = endColor;
        return;
   } else {
        VertexOut.Color = vec4(0.0, 0.0, 1.0, 0.7);
        return;
   }

}
