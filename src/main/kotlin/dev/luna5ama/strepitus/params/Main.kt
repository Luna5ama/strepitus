package dev.luna5ama.strepitus.params

import dev.luna5ama.glwrapper.base.*

data class MainParameters(
    @IntRangeVal(min = 32, max = 2048, step = 32)
    val width: Int = 512,
    @IntRangeVal(min = 32, max = 2048, step = 32)
    val height: Int = 512,
    @IntRangeVal(min = 1, max = 256)
    val slices: Int = 1
)

data class OutputSpec(
    val channels: Int,
    val pixelType: Int,
    val pixelSize: Long
) {
    val format
        get() = when (channels) {
            1 -> GL_RED
            2 -> GL_RG
            3 -> GL_RGB
            4 -> GL_RGBA
            else -> throw IllegalArgumentException("Invalid number of channels: $channels")
        }
}