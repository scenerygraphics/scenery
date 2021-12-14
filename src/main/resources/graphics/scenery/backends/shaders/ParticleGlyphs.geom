#version 450

layout(points) in;
layout(triangle_strip, max_vertices=4) out;

layout(location = 0) in VertexDataIn {
    vec3 Position;
    vec2 Properties; // .x = radius
    vec3 Color;
} VertexIn[];

// for now I calculate for every particle the silhouette, instead of checking if they are in view -> only CamPosition needed
layout(location = 3) in CameraDataIn {
    vec3 Position;
    mat4 Transform;
    mat4 VP;
} Camera[];

// dont need Normal, because the 2D silhouette has no real normals we need to use, its just a tool to create the spherical partical
// normal calculation takes place in the fragment shader while raytracing the silhouettes
layout(location = 0) out SilhouetteData /*This is a corner of the silhouette-quad */ {
    vec3 Position;
    vec2 TexCoord;
    flat vec3 Center;
    flat vec2 Properties;
    flat vec3 Color;
} SilhouetteCorner;

layout(location = 5) out CameraDataOut {
    mat4 VP;
} CameraOut;

const float eps = 0.0000000001;

// Function that calculates the silhouette center and the radius of the silhouette, from Camera.Position, VertexIn.Position
// and VertexIn.Properties.x = ParticleRadius with
// sr -> silhouette radius
// sc -> silhouette center
// e  -> distance between VertexIn.Position and Camera.Position
// V  -> Vector from Camera.Position to silhouette border
void CalculateSilhouette(in vec3 particlePos, in vec3 cameraPos, in float particleRadius, inout vec3 sc, inout float sr) {
    // distance between particle position and camera position
    float e = max(eps, abs(distance(particlePos, cameraPos)));
    float rr = (particleRadius * particleRadius);
    // distance between particle position  and silhouette center
    float m =  rr / e;

    vec3 distCenter = (m / e) * (cameraPos - particlePos);
    sc = particlePos + distCenter;
    sr = sqrt(rr * (1 - (rr / (e * e))));
}
void main() {

    vec3 pos = gl_in[0].gl_Position.xyz;

    vec3 sc = vec3(0.0, 0.0, 0.0);
    float sr = 0.0;
    CalculateSilhouette(pos, Camera[0].Position, VertexIn[0].Properties.x, sc, sr);

    vec3 up = Camera[0].Transform[1].xyz;
    vec3 right = Camera[0].Transform[0].xyz;
    vec3 cornerPos = vec3(1.0);
    vec4 unnormPos = vec4(1.0);

    vec3 rMulUp = sr * up;
    vec3 rMulRight = sr * right;

    // calculate corner in World space
    cornerPos = sc + rMulUp + rMulRight;
    // bring corner to view space
    unnormPos = Camera[0].VP * vec4(cornerPos, 1.0);

    // bring corner to clip space
    gl_Position = vec4(unnormPos.xyz / unnormPos.w, 1.0);
    SilhouetteCorner.Position = cornerPos;
    SilhouetteCorner.TexCoord = vec2(1.0, 1.0);
    SilhouetteCorner.Center = VertexIn[0].Position;
    SilhouetteCorner.Properties = VertexIn[0].Properties;
    SilhouetteCorner.Color = VertexIn[0].Color;
    CameraOut.VP = Camera[0].VP;
    EmitVertex();


    cornerPos = sc + rMulUp - rMulRight;
    unnormPos = Camera[0].VP * vec4(cornerPos, 1.0);

    gl_Position = vec4(unnormPos.xyz / unnormPos.w, 1.0);
    SilhouetteCorner.Position = cornerPos;
    SilhouetteCorner.TexCoord = vec2(1.0, -1.0);
    SilhouetteCorner.Center = VertexIn[0].Position;
    SilhouetteCorner.Properties = VertexIn[0].Properties;
    SilhouetteCorner.Color = VertexIn[0].Color;
    CameraOut.VP = Camera[0].VP;
    EmitVertex();


    cornerPos = sc - rMulUp + rMulRight;
    unnormPos = Camera[0].VP * vec4(cornerPos, 1.0);

    gl_Position = vec4(unnormPos.xyz / unnormPos.w, 1.0);
    SilhouetteCorner.Position = cornerPos;
    SilhouetteCorner.TexCoord = vec2(-1.0, 1.0);
    SilhouetteCorner.Center = VertexIn[0].Position;
    SilhouetteCorner.Properties = VertexIn[0].Properties;
    SilhouetteCorner.Color = VertexIn[0].Color;
    CameraOut.VP = Camera[0].VP;
    EmitVertex();


    cornerPos = sc - rMulUp - rMulRight;
    unnormPos = Camera[0].VP * vec4(cornerPos, 1.0);

    gl_Position = vec4(unnormPos.xyz / unnormPos.w, 1.0);
    SilhouetteCorner.Position = cornerPos;
    SilhouetteCorner.TexCoord = vec2(-1.0, -1.0);
    SilhouetteCorner.Center = VertexIn[0].Position;
    SilhouetteCorner.Properties = VertexIn[0].Properties;
    SilhouetteCorner.Color = VertexIn[0].Color;
    CameraOut.VP = Camera[0].VP;
    EmitVertex();

    EndPrimitive();
}
