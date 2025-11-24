#version 450 core
#extension GL_ARB_separate_shader_objects: enable

layout(location = 0) in VertexData {
    vec3 nearPosition;
    vec3 farPosition;
    mat4 pv;
} Vertex;

layout(location = 0) out vec4 FragColor;

layout(set = 2, binding = 0) uniform ShaderProperties {
    float baseLineWidth;
    int type;
    float lineLuminance;
};

vec4 grid(vec3 fragPos3D, float scale, bool drawAxis) {
    vec2 coord = fragPos3D.xz * scale;
    vec2 derivative = fwidth(coord);
    vec2 grid = abs(fract(coord - 0.5) - 0.5) / derivative;
    float line = min(grid.x, grid.y);
    float minimumz = min(derivative.y, 1);
    float minimumx = min(derivative.x, 1);
    vec4 color = vec4(0.2, 0.2, 0.2, 1.0 - min(line, 1.0));
    // z axis
    if(fragPos3D.x > -0.1 * minimumx && fragPos3D.x < 0.1 * minimumx)
    color.z = 1.0;
    // x axis
    if(fragPos3D.z > -0.1 * minimumz && fragPos3D.z < 0.1 * minimumz)
    color.x = 1.0;
    return color;
}
float computeDepth(vec3 pos) {
    vec4 clip_space_pos = Vertex.pv * vec4(pos.xyz, 1.0);
    return (clip_space_pos.z / clip_space_pos.w);
}

vec4 checkerboard(vec2 R, float scale) {
    return vec4(float((
    int(floor(R.x / scale)) +
    int(floor(R.y / scale))
    ) % 2));
}

vec4 grid(vec2 R, float scale, float lineWidth) {
    vec4 gridColor = vec4(0.8);

    vec2 coord = R;
    vec2 grid = abs(fract(coord*scale - 0.5) - 0.5) / fwidth(coord*scale);
    // line width is determined by the minimum gradient, for thicker lines, we
    // divide by lineWidth, lowering the gradient slope.
    float line = min(grid.x, grid.y)/lineWidth;

    vec4 color = vec4(0.0f);

    // mix together line colors and black background.
    // everything apart from the lines should be transparent.
    color = mix(vec4(0.0, 0.0, 0.0, 0.0), gridColor, 1.0 - min(line, 1.0));
//    if(color.x < 0.01) {
//        color.a = 0.0;
//    }
    return color;
}

vec4 blueprint(vec2 R, float scale, float lineWidth) {
    vec4 gridColor = vec4(121.0/255.0, 156.0/255.0, 210.0/255.0, 1.0);

    vec2 coord = R;
    vec2 grid = abs(fract(coord*scale - 0.5) - 0.5) / fwidth(coord*scale);
    // line width is determined by the minimum gradient, for thicker lines, we
    // divide by lineWidth, lowering the gradient slope.
    float line = min(grid.x, grid.y)/lineWidth;

    vec4 color = vec4(0.0f);

    // mix together line colors and black background.
    // everything apart from the lines should be transparent.
    color = mix(vec4(8.0/255.0, 96.0/255.0, 150.0/255.0, 1.0), gridColor, 1.0 - min(line, 1.0));

    return color;
}

void main() {
    float t = -Vertex.nearPosition.y / (Vertex.farPosition.y - Vertex.nearPosition.y);
    vec3 R = Vertex.nearPosition + t * (Vertex.farPosition - Vertex.nearPosition);
#ifndef OPENGL
    gl_FragDepth = computeDepth(R);
#else
    gl_FragDepth = (computeDepth(R)+1.0f)/2.0f;
#endif
    vec4 color = vec4(0.0);

    if(type == 0) {
        color = lineLuminance * grid(R.xz, 2.0, baseLineWidth) + lineLuminance * grid(R.xz, 0.5, baseLineWidth * 2);
    } else if (type == 1) {
        color = checkerboard(R.xz, 1) * 0.3 + checkerboard(R.xz, 10) * 0.2;
    } else {
        vec4 color = 0.5 * blueprint(R.xz, 2.0, baseLineWidth) + 0.5 * blueprint(R.xz, 0.5, baseLineWidth * 4.0);
    }

    color = color * float(t > 0);

    float spotlight = clamp(1.5 - 0.02*length(R.xz), 0.0, 1.0);
//    float spotlight = 1.0;

    FragColor = color * spotlight;

    if(FragColor.a < 0.001f) {
        discard;
    }
}
