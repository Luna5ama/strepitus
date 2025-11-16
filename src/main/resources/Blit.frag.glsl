#version 460 core

#include "/util/Dither.glsl"
#include "/util/Math.glsl"
#include "/util/Rand.glsl"

uniform float uval_slice;
uniform int uval_colorMode;

uniform sampler3D usam_outputImage;

out vec4 fragColor;

vec3 dither(vec3 x, float noiseV) {
    vec3 result = x;
    result *= 255.0;
    result = round(result + (noiseV - 0.5));
    result /= 255.0;
    return result;
}

void main() {
    vec2 screenPos = gl_FragCoord.xy / 1080.0;
    vec4 noiseV = texture(usam_outputImage, vec3(screenPos, uval_slice));

    if (uval_colorMode == 0) {
        fragColor.rgb = noiseV.rrr;
    } else if (uval_colorMode == 1) {
        fragColor.rgb = noiseV.aaa;
    } else {
        fragColor.rgb = noiseV.rgb;
    }

    fragColor.a = 1.0;
}