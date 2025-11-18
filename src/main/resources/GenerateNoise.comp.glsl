#version 460 core

#include "/util/Math.glsl"
#include "/util/Hash.glsl"
#include "/util/Mat2.glsl"
#include "/util/noise/ValueNoise.glsl"
#include "/util/noise/GradientNoise.glsl"
#include "/util/noise/psrdnoise2.glsl"
#include "/util/noise/psrdnoise3.glsl"

layout(local_size_x = 16, local_size_y = 16) in;

layout(std430) buffer SeedBuffer {
    uint gridSeed[128];
} ssbo_seed;

layout(rgba32f) uniform restrict image3D uimg_noiseImage;

uniform vec3 uval_noiseTexSizeF;

uniform int uval_noiseType;// 0: Value, 1: Perlin, 2: Simplex, 3: Worley
uniform int uval_dimensionType;// 0: 2D, 1: 3D
uniform int uval_gradientMode;// 0: value, 1: gradient, 2: both

// FBMParameters
uniform int uval_baseFrequency;
uniform int uval_octaves;
uniform float uval_lacunarity;
uniform float uval_persistence;
uniform int uval_perOctaveSeed;

uniform int uval_compositeMode;// 0: none, 1: add, 2: subtract, 3: multiply

vec4 valueNoise2(vec3 p, float freq, float alpha) {
    vec3 result = ValueNoise_2D_valueGrad(p.xy * freq / 2.0, vec2(freq));
    return vec4(result.yz, 0.0, result.x);
}

vec4 valueNoise3(vec3 p, float freq, float alpha) {
    return ValueNoise_3D_valueGrad(p * freq / 2.0, vec3(freq)).yzwx;
}

vec4 perlinNoise2(vec3 p, float freq, float alpha) {
    vec3 result = GradientNoise_2D_valueGrad(p.xy * freq / 2.0, vec2(freq));
    return vec4(result.yz, 0.0, result.x);
}

vec4 perlinNoise3(vec3 p, float freq, float alpha) {
    return GradientNoise_3D_valueGrad(p * freq / 2.0, vec3(freq)).yzwx;
}

vec4 simplexNoise2(vec3 p, float freq, float alpha) {
    freq = floor(freq);
    vec2 grad;
    float value = psrdnoise2(p.xy * freq / 2.0, vec2(freq), alpha, grad);
    return vec4(grad, 0.0, value);
}

vec4 simplexNoise3(vec3 p, float freq, float alpha) {
    freq = floor(freq);
    vec3 grad;
    float value = psrdnoise3(p * freq / 2.0, vec3(freq), alpha, grad);
    return vec4(grad, value);
}

float hash11(uint p) {
    return hash_uintToFloat(hash_11_q5(p));
}

void main() {
    ivec3 texelPos = ivec3(gl_GlobalInvocationID.xyz);

    vec3 noisePos = (vec3(texelPos) + vec3(0.5)) / uval_noiseTexSizeF;
    noisePos = noisePos * 2.0 - 1.0;

    float freq = uval_baseFrequency;
    float amp = 1.0;

    vec4 v = vec4(0.0);
    uint k = 0;

    for (int i = 0; i < uval_octaves; ++i) {
        vec4 noiseV = vec4(0.0);
        uint seedIndex = uval_perOctaveSeed == 0 ? 0 : i;
        float alpha = hash_uintToFloat(ssbo_seed.gridSeed[seedIndex]) * PI_2;
        if (uval_noiseType == 0) {
            noiseV = uval_dimensionType == 0 ? valueNoise2(noisePos, freq, alpha) : valueNoise3(noisePos, freq, alpha);
        } else if (uval_noiseType == 1) {
            noiseV = uval_dimensionType == 0 ? perlinNoise2(noisePos, freq, alpha) : perlinNoise3(noisePos, freq, alpha);
        } else if (uval_noiseType == 2) {
            noiseV = uval_dimensionType == 0 ? simplexNoise2(noisePos, freq, alpha) : simplexNoise3(noisePos, freq, alpha);
        }

        if (uval_gradientMode == 0) {
            noiseV = noiseV.aaaa;
        } else if (uval_gradientMode == 1) {
            noiseV = noiseV.rgbb;
        }

        v += amp * noiseV;
        amp *= uval_persistence;
        freq *= uval_lacunarity;
    }

    vec4 outputValue = imageLoad(uimg_noiseImage, texelPos);
    if (uval_compositeMode == 0) {
        outputValue = v;
    } else if (uval_compositeMode == 1) {
        outputValue += v;
    } else if (uval_compositeMode == 2) {
        outputValue -= v;
    } else if (uval_compositeMode == 3) {
        outputValue *= v;
    }
    imageStore(uimg_noiseImage, texelPos, outputValue);
}