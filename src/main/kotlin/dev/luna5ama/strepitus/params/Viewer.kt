package dev.luna5ama.strepitus.params

import java.math.BigDecimal


enum class DisplayColorMode {
    Grayscale,
    Alpha,
    RGB,
}

data class ViewerParameters(
    val colorMode: DisplayColorMode = DisplayColorMode.Grayscale,
    val tilling: Boolean = false,
    @DisplayName("Center X")
    val centerX: BigDecimal = 0.0.toBigDecimal(),
    @DisplayName("Center Y")
    val centerY: BigDecimal = 0.0.toBigDecimal(),
    @DecimalRangeVal(min = 0.0, max = 256.0, step = 0.5)
    val slice: BigDecimal = 0.0.toBigDecimal(),
    @DecimalRangeVal(min = -4.0, max = 4.0, step = 0.03125)
    val zoom: BigDecimal = 0.0.toBigDecimal(),
)