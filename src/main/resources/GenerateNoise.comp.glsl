#version 460 core

#include "/util/Math.glsl"
#include "/util/Hash.glsl"
#include "/util/Mat2.glsl"
#include "/util/noise/psrdnoise2.glsl"

layout(local_size_x = 16, local_size_y = 16) in;

layout(rgba32f) uniform writeonly image3D uimg_noiseImage;

uniform vec3 uval_noiseTexSizeF;

// 0: cubic
// 1: quintic
#define _NOISE_INTERPOLANT 1

#if _NOISE_INTERPOLANT == 1
#define _NOISE_INTERPO(w) (w * w * w * (w * (w * 6.0 - 15.0) + 10.0))
#define _NOISE_INTERPO_GRAD(w) (30.0 * w * w * (w * (w - 2.0) + 1.0))
#else
#define _NOISE_INTERPO(w) (w * w * (3.0 - 2.0 * w))
#define _NOISE_INTERPO_GRAD(w) (6.0 * w * (1.0 - w))
#endif

vec2 _GradientNoise_2D_hash(uvec2 x) {
    return hash_uintToFloat(hash_22_q3(uvec2(x))) * 2.0 - 1.0;
}
#define rotMatGA     mat2(-0.737368878, 0.675490294, -0.675490294, -0.737368878)

vec2 GradientNoise_2D_grad(vec2 x, int freq, uvec2 hashOffset) {
    ivec2 i = ivec2(floor(x));
    vec2 w = fract(x);

    vec2 u = _NOISE_INTERPO(w);
    vec2 du = _NOISE_INTERPO_GRAD(w);

    vec2 ga = _GradientNoise_2D_hash(uvec2((i + ivec2(0, 0)) & (freq - 1)) + hashOffset);
    vec2 gb = _GradientNoise_2D_hash(uvec2((i + ivec2(1, 0)) & (freq - 1)) + hashOffset);
    vec2 gc = _GradientNoise_2D_hash(uvec2((i + ivec2(0, 1)) & (freq - 1)) + hashOffset);
    vec2 gd = _GradientNoise_2D_hash(uvec2((i + ivec2(1, 1)) & (freq - 1)) + hashOffset);

    float va = dot(ga, w - vec2(0.0, 0.0));
    float vb = dot(gb, w - vec2(1.0, 0.0));
    float vc = dot(gc, w - vec2(0.0, 1.0));
    float vd = dot(gd, w - vec2(1.0, 1.0));

    vec2 g = mix(mix(ga, gb, u.x), mix(gc, gd, u.x), u.y);
    vec2 d = mix(vec2(vb, vc)-va, vd - vec2(vc, vb), u.yx);
    vec2 grad = g + du * d;

    return grad;
}

vec2 noiseGradient(vec2 p, int freq, float alpha) {
    vec2 grad;
    psrdnoise(p * float(freq), vec2(freq * 2), alpha, grad);
    return grad;
}

float hash11(uint p) {
    return hash_uintToFloat(hash_11_q5(p));
}

void main() {
    ivec3 texelPos = ivec3(gl_GlobalInvocationID.xyz);

    vec3 noisePos = (vec3(texelPos) + vec3(0.5)) / uval_noiseTexSizeF;
    noisePos = noisePos * 2.0 - 1.0;

    int freq = 4;
    float amp = 1.0;

    vec2 v = vec2(0.0);
    uint k = 0;

    for (uint i = 0; i < 4; ++i) {
        v += amp * noiseGradient(noisePos.xy, freq, hash11(k++) * PI_2);
        freq *= 2;
    }

    imageStore(uimg_noiseImage, texelPos, vec4(v, v));
}