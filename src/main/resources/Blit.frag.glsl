#version 460 core

#include "/util/Dither.glsl"
#include "/util/Math.glsl"
#include "/util/Rand.glsl"

uniform vec3 uval_noiseTexSizeF;
uniform float uval_slice;
uniform float uval_zoom;
uniform int uval_colorMode; // 0: RGB, 1: Alpha, 2: Grayscale
uniform int uval_tilling;
uniform vec2 uval_offset;

uniform sampler3D usam_outputImageTiling;
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
    vec3 pixelPos = vec3(gl_FragCoord.xy - uval_offset, uval_slice);
    vec3 samplePos = pixelPos / uval_noiseTexSizeF;
    samplePos = samplePos * 2.0 - 1.0;
    samplePos.xy *= uval_zoom;
    samplePos = samplePos * 0.5 + 0.5;
    vec4 noiseV;
    if (uval_tilling == 1 )  {
        noiseV = texture(usam_outputImageTiling, samplePos);
    } else {
        noiseV = texture(usam_outputImage, samplePos);
    }

    if (uval_colorMode == 0) {
        fragColor.rgb = noiseV.rgb;
    } else if (uval_colorMode == 1) {
        fragColor.rgb = noiseV.aaa;
    } else {
        fragColor.rgb = noiseV.rrr;
    }

    fragColor.a = 1.0;
}