package dev.luna5ama.strepitus.params

import kotlinx.serialization.Serializable

@Serializable
data class MainParameters(
    @IntRangeVal(min = 32, max = 2048, step = 32)
    val width: Int = 512,
    @IntRangeVal(min = 32, max = 2048, step = 32)
    val height: Int = 512,
    @IntRangeVal(min = 1, max = 256)
    val slices: Int = 1
)