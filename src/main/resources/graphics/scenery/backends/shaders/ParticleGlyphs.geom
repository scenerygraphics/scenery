#version 450

layout(points) in;
layout(triangle_strip, max_vertices=4) out;

layout(location = 0) in VertexDataIn {
    vec4 VertexPosition;
    vec3 VertexProperties; // .x = radius
} VertexIn[];

// for now I calculate for every particle the silhouette, instead of checking if they are in view -> only CamPosition needed
layout(location = 2) in CameraDataIn {
    vec3 CamPosition;
    mat4 VP;
} Camera[];

// dont need Normal, because the 2D silhouette has no real normals we need to use, its just a tool to create the spherical partical
// normal calculation takes place in the fragment shader while raytracing the silhouettes
layout(location = 0) out SilhouetteData /*This is a corner of the silhouette-quad */ {
    vec3 SilPosition;
    vec3 SilColor;
} SilhouetteCorner;

//layout(location = 2) out ParticleData /*This is the data for one particle*/ {
//    vec3 CenterPosition;
//    vec3 Properties; // .x = radius
//} Particle;
/*
layout(set = 2, binding = 0) uniform Matrices {
    mat4 ModelMatrix;
    mat4 NormalMatrix;
    int isBillboard;
} ubo;
*/

// Function that calculates the silhouette center and the radius of the silhouette, from Camera.Position, VertexIn.Position and VertexIn.Properties.x = ParticleRadius with
// sr -> silhouette radius
// sc -> silhouette center
// e  -> distance between VertexIn.Position and Camera.Position
// V  -> Vector from Camera.Position to silhouette border
void CalculateSilhouette(in vec3 particlePos, in vec3 cameraPos, in float particleRadius, inout vec3 sc, inout float sr) {
    float e = distance(particlePos, cameraPos); // distance between particle position and camera position
    float rr = (particleRadius*particleRadius);
    float m =  rr / e; // distance between particle position  and silhouette center

    sc = m * ((cameraPos - particlePos) / e);
    sr = sqrt((particleRadius*particleRadius) * (1 - (rr / (e * e))));
}

void main() {
    vec3 pos = gl_in[0].gl_Position.xyz;
    vec3 sc = vec3(1.0);
    float sr = 1.0;
    //CalculateSilhouette(pos, Camera[0].CamPosition, VertexIn[0].VertexProperties.x, sc, sr);

    vec3 up = vec3(0.0, 1.0, 0.0);
    vec3 right = cross(normalize(Camera[0].CamPosition - pos), up);
    vec3 cornerPos = pos;
    vec4 unnormPos = vec4(1.0);

    //sr = 1.0;
    cornerPos -= (0.5 * right);
    unnormPos = Camera[0].VP * vec4(cornerPos, 1.0);
    gl_Position = vec4(unnormPos.xyz / unnormPos.w, 1.0);
    gl_Position = vec4(0.2, 0.2, 0.3, 1.0);
    SilhouetteCorner.SilPosition = gl_Position.xyz;
    SilhouetteCorner.SilColor = vec3(1.0, 0.0, 0.0);
    EmitVertex();

    cornerPos.y += 1.0;
    unnormPos = Camera[0].VP * vec4(cornerPos, 1.0);
    gl_Position = vec4(unnormPos.xyz / unnormPos.w, 1.0);
    gl_Position = vec4(0.2, 0.4, 0.3, 1.0);
    SilhouetteCorner.SilPosition = gl_Position.xyz;
    SilhouetteCorner.SilColor = vec3(0.0, 1.0, 0.0);
    EmitVertex();

    cornerPos.y -= 1.0;
    cornerPos += right;
    unnormPos = Camera[0].VP * vec4(cornerPos, 1.0);
    gl_Position = vec4(unnormPos.xyz / unnormPos.w, 1.0);
    gl_Position = vec4(0.4, 0.4, 0.3, 1.0);
    SilhouetteCorner.SilPosition = gl_Position.xyz;
    SilhouetteCorner.SilColor = vec3(0.0, 0.0, 1.0);
    EmitVertex();

    cornerPos.y += 1.0;
    unnormPos = Camera[0].VP * vec4(cornerPos, 1.0);
    gl_Position = vec4(unnormPos.xyz / unnormPos.w, 1.0);
    gl_Position = vec4(0.4, 0.2, 0.3, 1.0);
    SilhouetteCorner.SilPosition = gl_Position.xyz;
    SilhouetteCorner.SilColor = vec3(1.0, 0.0, 1.0);
    EmitVertex();

    EndPrimitive();


    //Particle.Center = VertexIn.Position;
    //Particle.Properties = Vertex.Properties;
}
