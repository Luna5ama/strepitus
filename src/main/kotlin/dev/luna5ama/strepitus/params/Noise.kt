package dev.luna5ama.strepitus.params

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import io.github.composefluent.*
import io.github.composefluent.component.*
import io.github.composefluent.icons.*
import io.github.composefluent.icons.regular.*
import kotlinx.serialization.Transient
import java.math.BigDecimal

enum class CompositeMode {
    Add,
    Subtract,
    Multiply
}

data class NoiseLayerParameters(
    val enabled: Boolean = true,
    val compositeMode: CompositeMode = CompositeMode.Add,
    @IntRangeVal(min = 1, max = 32)
    val baseFrequency: Int = 4,
    @IntRangeVal(min = 1, max = 16)
    val octaves: Int = 4,
    @DecimalRangeVal(min = -2.0, max = 2.0, step = 0.05)
    val persistence: BigDecimal = 0.5.toBigDecimal(),
    @DecimalRangeVal(min = 1.0, max = 4.0, step = 0.05)
    val lacunarity: BigDecimal = 2.0.toBigDecimal(),
    val specificParameters: NoiseSpecificParameters = NoiseSpecificParameters.Simplex(),
    @Transient
    val expanded: Boolean = true,
)

enum class DistanceFunction {
    Euclidean,
    Manhattan,
    Chebyshev
}

enum class NoiseType {
    Value,
    Perlin,
    Simplex,
    Worley
}

@Immutable
sealed interface NoiseSpecificParameters {
    val type: NoiseType

    data class Value(
        val value: BigDecimal = 0.0.toBigDecimal(),
    ) : NoiseSpecificParameters {
        override val type: NoiseType
            get() = NoiseType.Value
    }

    data class Perlin(
        val rotated: Boolean = false,
    ) : NoiseSpecificParameters {
        override val type: NoiseType
            get() = NoiseType.Perlin
    }

    data class Simplex(
        val rotated: Boolean = false,
    ) : NoiseSpecificParameters {
        override val type: NoiseType
            get() = NoiseType.Simplex
    }

    data class Worley(
        val distanceFunction: DistanceFunction = DistanceFunction.Euclidean,
    )
}

@Composable
fun NoiseLayerEditor(
    layers: SnapshotStateList<NoiseLayerParameters>,
) {
    var deletingIndex by remember { mutableIntStateOf(-1) }
    ContentDialog(
        title = "Delete Layer",
        visible = deletingIndex in layers.indices,
        size = DialogSize.Companion.Standard,
        primaryButtonText = "Delete",
        onButtonClick = {
            if (it == ContentDialogButton.Primary && deletingIndex in layers.indices) {
                layers.removeAt(deletingIndex)
            }
            deletingIndex = -1
        },
        secondaryButtonText = "Cancel",
        content = {
            Text("Are you sure you want to delete this layer?")
        }
    )
    layers.forEachIndexed { i, layer ->
        Expander(
            layer.expanded,
            onExpandedChanged = { layers[i] = layer.copy(expanded = it) },
            icon = {
                Spacer(modifier = Modifier.Companion.width(FluentTheme.typography.subtitle.fontSize.value.dp * 3.0f))
                Icon(
                    imageVector = Icons.Default.ReOrderDotsVertical,
                    contentDescription = "",
                    modifier = Modifier.Companion.size(FluentTheme.typography.subtitle.fontSize.value.dp)
                )
            },
            heading = {
                Text(layer.specificParameters::class.simpleName!!, style = FluentTheme.typography.subtitle)
            },
            trailing = {
                Button(
                    onClick = {
                        deletingIndex = i
                    },
                    iconOnly = true
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Layer"
                    )
                }
            }
        ) {
            ParameterEditor(layer, { layers[i] = it })
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = {
                layers.add(NoiseLayerParameters())
            },
            buttonColors = ButtonDefaults.accentButtonColors(),
            iconOnly = true,
            modifier = Modifier.fillMaxWidth(0.5f)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Layer"
            )
        }
    }
}