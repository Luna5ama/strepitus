package dev.luna5ama.strepitus

import androidx.compose.runtime.Composable
import dev.luna5ama.glwrapper.enums.ImageFormat
import io.github.composefluent.component.*

enum class Format(val value: ImageFormat) {
    R8_UN(ImageFormat.R8_UN),
    R8G8_UN(ImageFormat.R8G8_UN),
    R8G8B8_UN(ImageFormat.R8G8B8_UN),
    R8G8B8A8_UN(ImageFormat.R8G8B8A8_UN),
    R8_SN(ImageFormat.R8_SN),
    R8G8_SN(ImageFormat.R8G8_SN),
    R8G8B8_SN(ImageFormat.R8G8B8_SN),
    R8G8B8A8_SN(ImageFormat.R8G8B8A8_SN),
    R16_UN(ImageFormat.R16_UN),
    R16G16_UN(ImageFormat.R16G16_UN),
    R16G16B16_UN(ImageFormat.R16G16B16_UN),
    R16G16B16A16_UN(ImageFormat.R16G16B16A16_UN),
    R10G10B10A2_UN(ImageFormat.R10G10B10A2_UN),
}

@Composable
fun OutputProcessingParametersEditor(
    parameters: OutputProcessingParameters,
    onChange: (OutputProcessingParameters) -> Unit
) {
    Text("Output Processing Parameters")
    ToggleButton(
        checked =parameters.normalized,
        onCheckedChanged = { onChange(parameters.copy(normalized = it)) },
    ) {
        Text("Normalized")
    }
}

data class OutputProcessingParameters(
    val normalized: Boolean = true,
    val dither: Boolean = true,
    val minVal: Float = 0.0f,
    val maxVal: Float = 1.0f
)