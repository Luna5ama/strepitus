#version 460 core

#include "/util/Math.glsl"

layout(local_size_x = 16, local_size_y = 16) in;

layout(std430) readonly buffer DataBuffer {
    vec4 data;
} ssbo_data;

layout(rgba32f) uniform readonly image3D uimg_noiseImage;
layout(OUTPUT_IMAGE_FORMAT) uniform writeonly image3D uimg_outputImage;

uniform int uval_normalize;
uniform float uval_minVal;
uniform float uval_maxVal;
uniform int uval_flip;
uniform int uval_dither;

void main() {
    ivec3 texelPos = ivec3(gl_GlobalInvocationID.xyz);
    vec2 dataMinMax = vec2(ssbo_data.data.xy);
    vec4 value = imageLoad(uimg_noiseImage, texelPos);
    if (uval_normalize == 1) {
        value = linearStep(dataMinMax.x, dataMinMax.y, value);
        if (uval_flip == 1) {
            value = vec4(1.0) - value;
        }
        value = mix(vec4(uval_minVal), vec4(uval_maxVal), value);
    }
    if (uval_dither == 1) {
        // TODO: dithering
    }
    imageStore(uimg_outputImage, texelPos, vec4(value));
}