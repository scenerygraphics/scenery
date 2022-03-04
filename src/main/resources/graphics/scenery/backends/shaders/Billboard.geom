#version 450

layout(points) in;
layout(triangle_strip, max_vertices=4) out;

layout(location = 0) in VertexDataIn {
    vec3 Position;
    vec2 TexCoord;
    vec3 Normal; //hold sizes if geometryBillboarding is used in .xy
} VertexIn[];

// for now I calculate for every particle the silhouette, instead of checking if they are in view -> only CamPosition needed
layout(location = 3) in CameraDataIn {
    vec3 Position;
    mat4 Transform;
    mat4 VP;
} Camera[];

// dont need Normal, because the 2D silhouette has no real normals we need to use, its just a tool to create the spherical partical
// normal calculation takes place in the fragment shader while raytracing the silhouettes
layout(location = 0) out VertexDataOut /*This is a corner of the silhouette-quad */ {
    vec3 Position;
    vec2 TexCoord;
    vec3 Center;
} BillboardCorner;

const float eps = 0.0000000001;
void main() {
    vec3 sc = vec3(0.0, 0.0, 0.0);
    float sr = 0.0;

    vec3 up = Camera[0].Transform[1].xyz;
    vec3 right = Camera[0].Transform[0].xyz;
    vec3 cornerPos = vec3(1.0);
    vec4 unnormPos = vec4(1.0);

    vec3 rMulUp = VertexIn[0].Normal.y * up;
    vec3 rMulRight = VertexIn[0].Normal.x * right;

    // calculate corner in World space
    cornerPos = VertexIn[0].Position + rMulUp + rMulRight;
    // bring corner to view space
    unnormPos = Camera[0].VP * vec4(cornerPos, 1.0);

    // bring corner to clip space
    gl_Position = vec4(unnormPos.xyz / unnormPos.w, 1.0);
    BillboardCorner.Position = cornerPos;
    BillboardCorner.TexCoord = vec2(1.0, 1.0);
    BillboardCorner.Center = VertexIn[0].Position;
    //CameraOut.VP = Camera[0].VP;
    EmitVertex();


    cornerPos = VertexIn[0].Position + rMulUp - rMulRight;
    unnormPos = Camera[0].VP * vec4(cornerPos, 1.0);

    gl_Position = vec4(unnormPos.xyz / unnormPos.w, 1.0);
    BillboardCorner.Position = cornerPos;
    BillboardCorner.TexCoord = vec2(0.0, 1.0);
    BillboardCorner.Center = VertexIn[0].Position;
    //ameraOut.VP = Camera[0].VP;
    EmitVertex();


    cornerPos = VertexIn[0].Position - rMulUp + rMulRight;
    unnormPos = Camera[0].VP * vec4(cornerPos, 1.0);

    gl_Position = vec4(unnormPos.xyz / unnormPos.w, 1.0);
    BillboardCorner.Position = cornerPos;
    BillboardCorner.TexCoord = vec2(1.0, 0.0);
    BillboardCorner.Center = VertexIn[0].Position;
    //CameraOut.VP = Camera[0].VP;
    EmitVertex();


    cornerPos = VertexIn[0].Position - rMulUp - rMulRight;
    unnormPos = Camera[0].VP * vec4(cornerPos, 1.0);

    gl_Position = vec4(unnormPos.xyz / unnormPos.w, 1.0);
    BillboardCorner.Position = cornerPos;
    BillboardCorner.TexCoord = vec2(0.0, 0.0);
    BillboardCorner.Center = VertexIn[0].Position;
    //CameraOut.VP = Camera[0].VP;
    EmitVertex();

    EndPrimitive();
}
