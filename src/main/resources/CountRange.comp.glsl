#version 460 core

#extension GL_KHR_shader_subgroup_arithmetic : enable

#include "/util/Math.glsl"

layout(local_size_x = 16, local_size_y = 16) in;

layout(std430) buffer DataBuffer {
    ivec4 data;
} ssbo_data;

layout(rgba32f) uniform readonly image3D uimg_noiseImage;

shared vec2 shared_minCount[32];
shared vec2 shared_maxCount[32];

void computeMinMax(float x, inout vec4 posMinNegMinPosMaxNegMax) {
    if (x >= 0.0) {
        posMinNegMinPosMaxNegMax.x = min(posMinNegMinPosMaxNegMax.x, abs(x));
        posMinNegMinPosMaxNegMax.z = max(posMinNegMinPosMaxNegMax.z, abs(x));
    }
    if (x <= 0.0) {
        posMinNegMinPosMaxNegMax.y = min(posMinNegMinPosMaxNegMax.y, abs(x));
        posMinNegMinPosMaxNegMax.w = max(posMinNegMinPosMaxNegMax.w, abs(x));
    }
}

void main() {
    vec4 count4 = imageLoad(uimg_noiseImage, ivec3(gl_GlobalInvocationID.xyz));
    vec4 posMinNegMinPosMaxNegMax = vec4(FLT_POS_INF, FLT_POS_INF, 0.0, 0.0);
    computeMinMax(count4.x, posMinNegMinPosMaxNegMax);
    computeMinMax(count4.y, posMinNegMinPosMaxNegMax);
    computeMinMax(count4.z, posMinNegMinPosMaxNegMax);
    computeMinMax(count4.w, posMinNegMinPosMaxNegMax);

    vec2 minCount1 = subgroupMin(posMinNegMinPosMaxNegMax.xy);
    vec2 maxCount1 = subgroupMax(posMinNegMinPosMaxNegMax.zw);
    if (subgroupElect()) {
        shared_minCount[gl_SubgroupID] = minCount1;
        shared_maxCount[gl_SubgroupID] = maxCount1;
    }
    barrier();

    if (gl_SubgroupID == 0) {
        vec2 minCount2 = shared_minCount[min(gl_NumSubgroups, gl_SubgroupInvocationID)];
        vec2 maxCount2 = shared_maxCount[min(gl_NumSubgroups, gl_SubgroupInvocationID)];
        vec2 minCount3 = subgroupMin(minCount2);
        vec2 maxCount3 = subgroupMax(maxCount2);
        ivec2 minCount3i = floatBitsToInt(minCount3);
        ivec2 maxCount3i = floatBitsToInt(maxCount3);
        if (subgroupElect()) {
            atomicMin(ssbo_data.data.x, minCount3i.x);
            atomicMin(ssbo_data.data.y, minCount3i.y);
            atomicMax(ssbo_data.data.z, maxCount3i.x);
            atomicMax(ssbo_data.data.w, maxCount3i.y);
        }
    }
}
