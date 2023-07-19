#version 450 core
#extension GL_ARB_separate_shader_objects: enable
#extension GL_EXT_control_flow_attributes : enable
#extension GL_EXT_debug_printf : enable

layout(location = 0) in VertexData {
    vec2 textureCoord;
    mat4 inverseProjection;
    mat4 inverseModelView;
    mat4 modelView;
    mat4 MVP;
} Vertex;

layout(location = 0) out vec4 FragColor;
//
layout(set = 4, binding = 0) uniform ShaderProperties {
    float downImage;
};
layout(set=5, binding = 0, r16ui) uniform uimage3D Indices;

layout(set=6, binding = 0, r32f) uniform image3D Weights;
layout(set=7, binding = 0, r16ui) uniform uimage2D m2p;
layout(set=8, binding = 0, rg16ui) uniform uimage2D PixelCoords;

const int NUM_OBJECT_TEXTURES = 6;
layout(set = 3, binding = 0) uniform sampler2D ObjectTextures[NUM_OBJECT_TEXTURES];
layout(set=9, binding=0, rgba8) uniform  image2D FoveatedTexture;
void main()
{
    float yzx=downImage;
//    float w3 = imageLoad(Weights, ivec3(int(Vertex.textureCoord.x*1920), int(Vertex.textureCoord.y*1080), 3)).x;
//    float w4 = imageLoad(Weights, ivec3(int(Vertex.textureCoord.x*1920), int(Vertex.textureCoord.y*1080), 4)).x;
//    float w5 = imageLoad(Weights, ivec3(int(Vertex.textureCoord.x*1920), int(Vertex.textureCoord.y*1080), 5)).x;
//    float w6 = imageLoad(Weights, ivec3(int(Vertex.textureCoord.x*1920), int(Vertex.textureCoord.y*1080), 6)).x;
//    float w7 = imageLoad(Weights, ivec3(int(Vertex.textureCoord.x*1920), int(Vertex.textureCoord.y*1080), 7)).x;
//    float w8 = imageLoad(Weights, ivec3(int(Vertex.textureCoord.x*1920), int(Vertex.textureCoord.y*1080), 8)).x;
//    float w9 = imageLoad(Weights, ivec3(int(Vertex.textureCoord.x*1920), int(Vertex.textureCoord.y*1080), 9)).x;
//    float w3 = imageLoad(Weights, ivec3(int(Vertex.textureCoord.x*1920), int(Vertex.textureCoord.y*1080), 10)).x;
//    float w4 = imageLoad(Weights, ivec3(int(Vertex.textureCoord.x*1920), int(Vertex.textureCoord.y*1080), 11)).x;
//    float w1 = imageLoad(Weights, ivec3(int(Vertex.textureCoord.x*1920), int(Vertex.textureCoord.y*1080), 12)).x;
//    float w2 = imageLoad(Weights, ivec3(int(Vertex.textureCoord.x*1920), int(Vertex.textureCoord.y*1080), 13)).x;
//    float w3 = imageLoad(Weights, ivec3(int(Vertex.textureCoord.x*1920), int(Vertex.textureCoord.y*1080), 14)).x;


    float wts[16];
    uint inds[16];
    uvec2 pixs[16];
    vec2 fs[16];
    vec3 rgbColor=vec3(0.0,0.0,0.0);
    ivec2 debug_pixel=ivec2(100,50);

//    FragColor=vec4(x.rgb,1.0);
//    return;
/*
    if(ivec2(int(Vertex.textureCoord.x*480), int(Vertex.textureCoord.y*270))==debug_pixel){
        for(int i=0;i<16;i++){
            inds[i] = imageLoad(Indices, ivec3(debug_pixel, i)).x;
            wts[i] = imageLoad(Weights, ivec3(debug_pixel, i)).x;
            pixs[i]=imageLoad(PixelCoords, ivec3(inds[i], 0,0)).rg;
            fs[i] = vec2((float(pixs[i].x)+0.5f)/480, (float(pixs[i].y)+0.5f)/270);
            vec3 col=texture(ObjectTextures[1], fs[i]).rgb;
//            debugPrintfEXT("%d, %f, %f", col);

//            debugPrintfEXT("%d,,,", inds[i]);
//            debugPrintfEXT("%d, %d", pixs[i]);
//            debugPrintfEXT("%f", wts[i]);

            //        fs[i] = vec2((float(pixs[i].x)+0.5f)/960, (float(pixs[i].y) +0.5f)/540);
            //        rgbColor+= vec3(texture(ObjectTextures[1], fs[i]).rgb)*wts[i];
        }
    }
    float l=downImage;
*/

//    return;
//    uint inn=1;
//    for(int i=0;i<16;i++){
//        inds[i] = imageLoad(Indices, ivec3(int(Vertex.textureCoord.x*480)+240, int(Vertex.textureCoord.y*270)+135, i)).x;
//        wts[i] = imageLoad(Weights, ivec3(int(Vertex.textureCoord.x*480)+240, int(Vertex.textureCoord.y*270)+135, i)).x;
//
//        inn = imageLoad(m2p, ivec2(inds[i],0)).r;
//
//        pixs[i]=imageLoad(PixelCoords, ivec2(inn, 0)).rg;
////        debugPrintfEXT("%d", inn);
//
//
//        fs[i] = vec2((float(pixs[i].x-240)+0.5f)/480, (float(pixs[i].y-135) +0.5f)/270);
//        if(ivec2(int(Vertex.textureCoord.x*480), int(Vertex.textureCoord.y*270))==debug_pixel){
//            debugPrintfEXT("%d, %d, %d %d For texturecoords: %f, %f", inds[i], inn,pixs[i], fs[i]);
//        }
//        rgbColor += imageLoad(FoveatedTexture, ivec2(pixs[i].x-240, pixs[i].y-135)).rgb*wts[i];
////        rgbColor= vec3(texture(ObjectTextures[1], fs[i]).rgb);
//        if(ivec2(int(Vertex.textureCoord.x*480), int(Vertex.textureCoord.y*270))==debug_pixel){
//            debugPrintfEXT("found color: %f, %f, %f", rgbColor);
//        }
//    }
//    if(ivec2(int(Vertex.textureCoord.x*480), int(Vertex.textureCoord.y*270))==debug_pixel){
//        debugPrintfEXT("%f, %f, %f", rgbColor);
//
//    }

    //    uint c1 = imageLoad(Indices, ivec3(int(Vertex.textureCoord.x*960), int(Vertex.textureCoord.y*540), 0)).x;
//    uint c2 = imageLoad(Indices, ivec3(int(Vertex.textureCoord.x*480), int(Vertex.textureCoord.y*270), 1)).x;
//    uint c3 = imageLoad(Indices, ivec3(int(Vertex.textureCoord.x*480), int(Vertex.textureCoord.y*270), 2)).x;
//
//
//    uint idx1 = imageLoad(p2m, ivec3(int(c1), 0, 0)).x;
//    uint idx2 = imageLoad(p2m, ivec3(int(c2), 0, 0)).x;
//    uint idx3 = imageLoad(p2m, ivec3(int(c3), 0, 0)).x;
//
//    uvec2 pix1 = imageLoad(PixelCoords, ivec2(int(idx1), 0)).rg;
//    uvec2 pix2 = imageLoad(PixelCoords, ivec2(int(idx2), 0)).rg;
//    uvec2 pix3 = imageLoad(PixelCoords, ivec2(int(idx3), 0)).rg;
//    //    uvec2 c4 = imageLoad(Indices, ivec3(int(Vertex.textureCoord.x*1920), int(Vertex.textureCoord.y*1080), 3)).rg;
//    vec2 f1 = vec2((float(pix1.x)+0.5f)/960, (float(pix1.y) +0.5f)/540);
//    vec2 f2 = vec2((float(pix2.x)+0.5f)/480, (float(pix2.y)+0.5f)/270);
//    vec2 f3 = vec2((float(pix3.x)+0.5f)/480, (float(pix3.y)+0.5f)/270);
////    vec2 f4 = vec2((float(c4.x)+0.5f)/1920, (float(c4.y)+0.5f)/1080);
//    float diff = Vertex.textureCoord.x*1920 - c1.x;
//    debugPrintfEXT("%f", diff);
//    if (texture(ObjectTextures[1],Vertex.textureCoord.xy).rgb == vec3(0.0,0.0,0.0)){
//    if(Vertex.textureCoord.x*1920>1910 && Vertex.textureCoord.x<1911)
//        debugPrintfEXT("%d, %d", c1);
////    }
//    FragColor = vec4(texture(ObjectTextures[1], Vertex.textureCoord.xy * (downImage)).rgb, 1.0);
//    FragColor = vec4(vec3(texture(ObjectTextures[1], f1).rgb), 1.0);
//    FragColor = vec4(rgbColor,1.0);
//    FragColor=  vec4(texture(ObjectTextures[1],Vertex.textureCoord.xy).rgb, 1);//*w1+vec4(texture(ObjectTextures[1],f2).rgb,0.25)*w2+vec4(texture(ObjectTextures[1],f3).rgb,0.25)*w3+vec4(texture(ObjectTextures[1],f4).rgb, 0.25)*w4;
    FragColor = vec4(texture(ObjectTextures[1], Vertex.textureCoord.xy * (downImage)).rgb, 1.0);


}

