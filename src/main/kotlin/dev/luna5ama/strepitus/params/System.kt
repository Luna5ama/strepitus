@file:UseSerializers(BigDecimalSerializer::class)
package dev.luna5ama.strepitus.params

import dev.luna5ama.strepitus.BigDecimalSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

enum class DarkModeOption {
    Auto,
    Dark,
    Light,
}

@Serializable
data class SystemParameters(
    val darkMode: DarkModeOption = DarkModeOption.Auto,
)