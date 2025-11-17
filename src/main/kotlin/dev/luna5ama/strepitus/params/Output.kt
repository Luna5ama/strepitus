package dev.luna5ama.strepitus.params

import dev.luna5ama.glwrapper.base.*
import dev.luna5ama.glwrapper.enums.ImageFormat
import java.math.BigDecimal

enum class GPUFormat(val value: ImageFormat.Sized, val glslFormat: String) {
    R8G8B8A8_UN(ImageFormat.R8G8B8A8_UN, "rgba8"),
    R10G10B10A2_UN(ImageFormat.R10G10B10A2_UN, "rgb10_a2"),
    R16G16B16A16_F(ImageFormat.R16G16B16A16_F, "rgba16f")
}

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

enum class Format(val gpuFormat: GPUFormat, val outputSpec: OutputSpec) {
    R8_UNORM(
        GPUFormat.R8G8B8A8_UN,
        OutputSpec(1, GL_UNSIGNED_BYTE, 1L)
    ),
    R8G8_UNORM(
        GPUFormat.R8G8B8A8_UN,
        OutputSpec(2, GL_UNSIGNED_BYTE, 2L)
    ),
    R8G8B8A8_UNORM(
        GPUFormat.R8G8B8A8_UN,
        OutputSpec(4, GL_UNSIGNED_BYTE, 4L),
    ),

    R10G10B10A2_UNORM(
        GPUFormat.R10G10B10A2_UN,
        OutputSpec(4, GL_UNSIGNED_INT_2_10_10_10_REV, 4L),
    ),

    R8_SNORM(
        GPUFormat.R8G8B8A8_UN,
        OutputSpec(1, GL_BYTE, 1L)
    ),
    R8G8_SNORM(
        GPUFormat.R8G8B8A8_UN,
        OutputSpec(2, GL_BYTE, 2L)
    ),
    R8G8B8A8_SNORM(
        GPUFormat.R8G8B8A8_UN,
        OutputSpec(4, GL_BYTE, 4L),
    ),

    R16_UNORM(
        GPUFormat.R16G16B16A16_F,
        OutputSpec(1, GL_UNSIGNED_SHORT, 2L)
    ),
    R16G16_UNORM(
        GPUFormat.R16G16B16A16_F,
        OutputSpec(2, GL_UNSIGNED_SHORT, 4L)
    ),
    R16G16B16A16_UNORM(
        GPUFormat.R16G16B16A16_F,
        OutputSpec(4, GL_UNSIGNED_SHORT, 8L),
    ),

    R16_SNORM(
        GPUFormat.R16G16B16A16_F,
        OutputSpec(1, GL_SHORT, 2L)
    ),
    R16G16_SNORM(
        GPUFormat.R16G16B16A16_F,
        OutputSpec(2, GL_SHORT, 4L)
    ),
    R16G16B16A16_SNORM(
        GPUFormat.R16G16B16A16_F,
        OutputSpec(4, GL_SHORT, 8L),
    ),

    R16_F(
        GPUFormat.R16G16B16A16_F,
        OutputSpec(1, GL_HALF_FLOAT, 2L)
    ),
    R16G16_F(
        GPUFormat.R16G16B16A16_F,
        OutputSpec(2, GL_HALF_FLOAT, 4L)
    ),
    R16G16B16A16_F(
        GPUFormat.R16G16B16A16_F,
        OutputSpec(4, GL_HALF_FLOAT, 8L),
    ),
}


data class OutputParameters(
    val format: Format = Format.R8G8B8A8_UNORM,
    val normalize: Boolean = true,
    @DecimalRangeVal(min = -1.0, max = 1.0, step = 0.1)
    val minVal: BigDecimal = 0.0.toBigDecimal(),
    @DecimalRangeVal(min = -1.0, max = 1.0, step = 0.1)
    val maxVal: BigDecimal = 1.0.toBigDecimal(),
    val flip: Boolean = false,
    val dither: Boolean = true,
)