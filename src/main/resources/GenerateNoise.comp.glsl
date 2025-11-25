/*
    References:
        [QUI13a] Quilez, Inigo. "Voronoi - smooth ". 2014.
            MIT License. Copyright (c) 2014 Inigo Quilez.
            https://www.shadertoy.com/view/ldB3zc#

        You can find full license texts in /licenses
*/
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

uniform int uval_baseFrequency;
uniform float uval_baseAmplitude;

// FBMParameters
uniform int uval_octaves;
uniform float uval_lacunarity;
uniform float uval_persistence;
uniform int uval_perOctaveSeed;

uniform int uval_compositeMode;// 0: none, 1: add, 2: subtract, 3: multiply

uniform int uval_worleyDistanceFunction;// 0: Euclidean, 1: Manhattan, 2: Chebyshev

uniform int uval_worleySmoothFlip;
uniform float uval_worleySmoothMixWeight;
uniform float uval_worleySmoothSmoothness;
uniform float uval_worleySmoothRandPower;
uniform vec2 uval_worleySmoothBounds;
uniform int uval_worleySmoothStepFuncType;// 0: linearStep, 1: smoothStep

uniform int uval_worleyRegularF1Flip;
uniform int uval_worleyRegularF2Flip;
uniform float uval_worleyRegularF1MixWeight;
uniform float uval_worleyRegularF2MixWeight;
uniform float uval_worleyRegularRandPower;
uniform vec2 uval_worleyRegularBounds;
uniform int uval_worleyRegularStepFuncType;// 0: linearStep, 1: smoothStep

vec4 valueNoise2(vec3 p, float freq, uint seed) {
    vec3 result = ValueNoise_2D_valueGrad(p.xy * freq / 2.0, vec2(freq), seed);
    return vec4(result.yz, 0.0, result.x);
}

vec4 valueNoise3(vec3 p, float freq, uint seed) {
    return ValueNoise_3D_valueGrad(p * freq / 2.0, vec3(freq), seed).yzwx;
}

vec4 perlinNoise2(vec3 p, float freq, uint seed) {
    vec3 result = GradientNoise_2D_valueGrad(p.xy * freq / 2.0, vec2(freq), seed);
    return vec4(result.yz, 0.0, result.x);
}

vec4 perlinNoise3(vec3 p, float freq, uint seed) {
    return GradientNoise_3D_valueGrad(p * freq / 2.0, vec3(freq), seed).yzwx;
}

vec4 simplexNoise2(vec3 p, float freq, uint seed) {
    freq = floor(freq);
    vec2 grad;
    float value = psrdnoise2(p.xy * freq / 2.0, vec2(freq), 0.0, seed, grad);
    return vec4(grad, 0.0, value);
}

vec4 simplexNoise3(vec3 p, float freq, uint seed) {
    freq = floor(freq);
    vec3 grad;
    float value = psrdnoise3(p * freq / 2.0, vec3(freq), 0.0, seed, grad);
    return vec4(grad, value);
}

float euclideanDistance(vec2 a, vec2 b) {
    return distance(a, b);
}

float euclideanDistance(vec3 a, vec3 b) {
    return distance(a, b);
}

float manhattanDistance(vec2 a, vec2 b) {
    vec2 diff = abs(a - b);
    return diff.x + diff.y;
}

float manhattanDistance(vec3 a, vec3 b) {
    vec3 diff = abs(a - b);
    return diff.x + diff.y + diff.z;
}

float chebyshevDistance(vec2 a, vec2 b) {
    vec2 diff = abs(a - b);
    return max(diff.x, diff.y);
}

float chebyshevDistance(vec3 a, vec3 b) {
    vec3 diff = abs(a - b);
    return max(max(diff.x, diff.y), diff.z);
}

float worleyNoise2D(vec2 x, vec2 freq, uint seed) {
    vec2 currCenterOffset = fract(x);

    // Initialize results
    float f1 = 0.0;
    float f2 = 0.0;
    float m = 1.0;

    // compare to 3x3x3 neighbor cells
    for (int ix = -2; ix <= 2; ++ix) {
        for (int iy = -2; iy <= 2; ++iy) {
            // Offset to the neighbor cell
            ivec2 idOffset = ivec2(ix, iy);

            uvec2 cellID = hashCoordWarp(x + (idOffset + 2), freq);

            uvec3 hashPos = uvec3(cellID, seed);
            vec3 hashVal = hash_uintToFloat(hash_33_q3(hashPos));

            vec2 center = hashVal.xy + vec2(idOffset);

            float cellDistance = 0.0;
            if (uval_worleyDistanceFunction == 0) {
                cellDistance = euclideanDistance(currCenterOffset, center);
            } else if (uval_worleyDistanceFunction == 1) {
                cellDistance = manhattanDistance(currCenterOffset, center);
            } else if (uval_worleyDistanceFunction == 2) {
                cellDistance = chebyshevDistance(currCenterOffset, center);
            }

            float regularV = 0.0;
            if (uval_worleyRegularStepFuncType == 0) {
                regularV = linearStep(uval_worleyRegularBounds.y, uval_worleyRegularBounds.x, cellDistance);
            } else if (uval_worleyRegularStepFuncType == 1) {
                regularV = smoothstep(uval_worleyRegularBounds.y, uval_worleyRegularBounds.x, cellDistance);
            }
            regularV *= pow(hashVal.z, uval_worleyRegularRandPower);

            const float smoothness = uval_worleySmoothSmoothness;
            float h = (m - cellDistance) / smoothness;
            if (uval_worleySmoothStepFuncType == 0) {
                h = linearStep(uval_worleySmoothBounds.x, uval_worleySmoothBounds.y, h);
            } else if (uval_worleySmoothStepFuncType == 1) {
                h = smoothstep(uval_worleySmoothBounds.x, uval_worleySmoothBounds.y, h);
            }
            h *= pow(hashVal.z, uval_worleySmoothRandPower);
            m = mix(m, cellDistance, h) - h * (1.0 - h) * smoothness / (1.0 + 3.0 * smoothness);

            if (f1 < regularV) {
                f2 = f1;
                f1 = regularV;
            } else if (f2 < regularV) {
                f2 = regularV;
            }
        }
    }
    m = uval_worleySmoothFlip == 0 ? saturate(1.0 - m): saturate(m);
    f1 = uval_worleyRegularF1Flip == 0 ? f1: saturate(1.0 - f1);
    f2 = uval_worleyRegularF2Flip == 0 ? f2: saturate(1.0 - f2);

    float result = 0.0;
    result += f1 * uval_worleyRegularF1MixWeight;
    result += f2 * uval_worleyRegularF2MixWeight;
    result += m * uval_worleySmoothMixWeight;
    return result;
}

float worleyNoise3D(vec3 x, vec3 freq, uint seed) {
    vec3 currCenterOffset = fract(x);

    // Initialize results
    float f1 = 0.0;
    float f2 = 0.0;
    float m = 1.0;

    // compare to 3x3x3 neighbor cells
    for (int ix = -2; ix <= 2; ++ix) {
        for (int iy = -2; iy <= 2; ++iy) {
            for (int iz = -2; iz <= 2; ++iz) {
                // Offset to the neighbor cell
                ivec3 idOffset = ivec3(ix, iy, iz);

                uvec3 cellID = hashCoordWarp(x + (idOffset + 2), freq);

                uvec4 hashPos = uvec4(cellID, seed);
                vec4 hashVal = hash_uintToFloat(hash_44_q3(hashPos));

                vec3 center = hashVal.xyz + vec3(idOffset);

                float cellDistance = 0.0;
                if (uval_worleyDistanceFunction == 0) {
                    cellDistance = euclideanDistance(currCenterOffset, center);
                } else if (uval_worleyDistanceFunction == 1) {
                    cellDistance = manhattanDistance(currCenterOffset, center);
                } else if (uval_worleyDistanceFunction == 2) {
                    cellDistance = chebyshevDistance(currCenterOffset, center);
                }

                float regularV = 0.0;
                if (uval_worleyRegularStepFuncType == 0) {
                    regularV = linearStep(uval_worleyRegularBounds.y, uval_worleyRegularBounds.x, cellDistance);
                } else if (uval_worleyRegularStepFuncType == 1) {
                    regularV = smoothstep(uval_worleyRegularBounds.y, uval_worleyRegularBounds.x, cellDistance);
                }
                regularV *= pow(hashVal.w, uval_worleyRegularRandPower);

                const float smoothness = uval_worleySmoothSmoothness;
                float h = (m - cellDistance) / smoothness;
                if (uval_worleySmoothStepFuncType == 0) {
                    h = linearStep(uval_worleySmoothBounds.x, uval_worleySmoothBounds.y, h);
                } else if (uval_worleySmoothStepFuncType == 1) {
                    h = smoothstep(uval_worleySmoothBounds.x, uval_worleySmoothBounds.y, h);
                }
                h *= pow(hashVal.w, uval_worleySmoothRandPower);
                m = mix(m, cellDistance, h) - h * (1.0 - h) * smoothness / (1.0 + 3.0 * smoothness);

                if (f1 < regularV) {
                    f2 = f1;
                    f1 = regularV;
                } else if (f2 < regularV) {
                    f2 = regularV;
                }
            }
        }
    }
    m = uval_worleySmoothFlip == 0 ? saturate(1.0 - m): saturate(m);
    f1 = uval_worleyRegularF1Flip == 0 ? f1: saturate(1.0 - f1);
    f2 = uval_worleyRegularF2Flip == 0 ? f2: saturate(1.0 - f2);

    float result = 0.0;
    result += f1 * uval_worleyRegularF1MixWeight;
    result += f2 * uval_worleyRegularF2MixWeight;
    result += m * uval_worleySmoothMixWeight;
    return result;
}

vec4 worleyNoise2(vec3 p, float freq, uint seed) {
    freq = floor(freq);
    float value = worleyNoise2D(p.xy * freq / 2.0, vec2(freq), seed);
    return vec4(value);
}

vec4 worleyNoise3(vec3 p, float freq, uint seed) {
    freq = floor(freq);
    float value = worleyNoise3D(p * freq / 2.0, uvec3(freq), seed);
    return vec4(value);
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
        uint seed = ssbo_seed.gridSeed[seedIndex];
        float alpha = hash_uintToFloat(ssbo_seed.gridSeed[seedIndex]) * PI_2;
        if (uval_noiseType == 0) {
            noiseV = uval_dimensionType == 0 ? valueNoise2(noisePos, freq, seed) : valueNoise3(noisePos, freq, seed);
        } else if (uval_noiseType == 1) {
            noiseV = uval_dimensionType == 0 ? perlinNoise2(noisePos, freq, seed) : perlinNoise3(noisePos, freq, seed);
        } else if (uval_noiseType == 2) {
            noiseV = uval_dimensionType == 0 ? simplexNoise2(noisePos, freq, seed) : simplexNoise3(noisePos, freq, seed);
        } else if (uval_noiseType == 3) {
            noiseV = uval_dimensionType == 0 ? worleyNoise2(noisePos, freq, seed) : worleyNoise3(noisePos, freq, seed);
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

    v *= uval_baseAmplitude;

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