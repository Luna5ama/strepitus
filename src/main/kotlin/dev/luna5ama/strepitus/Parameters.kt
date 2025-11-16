package dev.luna5ama.strepitus

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.*
import dev.luna5ama.glwrapper.base.*
import dev.luna5ama.glwrapper.enums.ImageFormat
import io.github.composefluent.*
import io.github.composefluent.component.*
import io.github.composefluent.icons.*
import io.github.composefluent.icons.regular.*
import kotlinx.serialization.Transient
import java.math.BigDecimal
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties

@Composable
fun NoiseLayerEditor(
    layers: SnapshotStateList<NoiseLayerParameters<*>>,
) {
    var deletingIndex by remember { mutableIntStateOf(-1) }
    ContentDialog(
        title = "Delete Layer",
        visible = deletingIndex in layers.indices,
        size = DialogSize.Standard,
        primaryButtonText = "Delete",
        onButtonClick = {
            if (it == ContentDialogButton.Primary) {
                if (deletingIndex in layers.indices) {
                    layers.removeAt(deletingIndex)
                }
            } else {
                deletingIndex = -1
            }
        },
        secondaryButtonText = "Cancel",
        content = {
            Text("Are you sure you want to delete this layer?")
        }
    )
//        Button(
//            onClick = {
//                if (deletingIndex in layers.indices) {
//                    layers.removeAt(deletingIndex)
//                }
//                deletingIndex = -1
//            }
//        ) {
//            Text("Delete")
//        }
//        Button(
//            onClick = {
//                deletingIndex = -1
//            }
//        ) {
//            Text("Cancel")
//        }
    layers.forEachIndexed { i, layer ->
        Expander(
            layer.expanded,
            onExpandedChanged = { layers[i] = layer.copy(expanded = it) },
            icon = {
                Spacer(modifier = Modifier.width(FluentTheme.typography.subtitle.fontSize.value.dp * 3.0f))
                Icon(
                    imageVector = Icons.Default.ReOrderDotsVertical,
                    contentDescription = "",
                    modifier = Modifier.size(FluentTheme.typography.subtitle.fontSize.value.dp)
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

        }
    }
}

@Composable
inline fun <reified T : Any> ParameterEditor(
    parameters: T,
    noinline onChange: (T) -> Unit
) = ParameterEditor(
    clazz = T::class,
    parameters = parameters,
    onChange = onChange
)

@Suppress("UNCHECKED_CAST")
@Composable
fun <T : Any> ParameterEditor(
    clazz: KClass<T>,
    parameters: T,
    onChange: (T) -> Unit
) {
    var expended by remember { mutableStateOf(true) }
    val heading = clazz.displayName ?: camelCaseToTitle(clazz.simpleName!!.removeSuffix("Parameters"))
    val copyFunc = clazz.memberFunctions.first { member -> member.name == "copy" }
    val copyFunParameterOrder = copyFunc.parameters.drop(1).withIndex().associate { it.value.name!! to it.index }
    val properties = clazz.memberProperties
        .filter { it.annotations.none { ann -> ann is Transient } }
        .sortedBy { copyFunParameterOrder[it.name] ?: Int.MAX_VALUE }
    val icon = clazz.companionObject?.let { companion ->
        companion.memberProperties.firstOrNull { it.name == "icon" }?.getter?.call(companion.objectInstance) as? ImageVector
    }

    Expander(
        expended,
        onExpandedChanged = { expended = it },
        icon = {
            if (icon != null) {
                Spacer(modifier = Modifier.width(FluentTheme.typography.subtitle.fontSize.value.dp * 3.0f))
                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    modifier = Modifier.size(FluentTheme.typography.subtitle.fontSize.value.dp)
                )
            }
        },
        modifier = Modifier.padding(8.dp),
        heading = {
            Text(heading, style = FluentTheme.typography.subtitle)
        }
    ) {
        properties.forEachIndexed { index, it ->
            val propName = it.displayName ?: camelCaseToWords(it.name)
            val propValue = it.get(parameters)!!
            val newParameterFunc = { newValue: Any ->
                val newParameters = copyFunc.callBy(
                    mapOf(
                        copyFunc.parameters[0] to parameters,
                        copyFunc.parameters[1 + index] to newValue
                    )
                ) as T
                onChange(newParameters)
            }
            CardExpanderItem(heading = { Text(propName) }) {
                when (val propType = it.returnType.classifier!! as KClass<Any>) {
                    Int::class -> {
                        IntegerInput(value = propValue as Int, onValueChange = newParameterFunc)
                    }

                    BigDecimal::class -> {
                        DecimalInput(value = propValue as BigDecimal, onValueChange = newParameterFunc)
                    }

                    Boolean::class -> {
                        ToggleSwitch(
                            checked = propValue as Boolean,
                            onCheckStateChange = newParameterFunc
                        )
                    }

                    else -> when {
                        propType.isData -> {
                            ParameterEditor(
                                clazz = propType,
                                parameters = propValue,
                                onChange = newParameterFunc
                            )
                        }

                        Enum::class.isSuperclassOf(propType) -> {
                            var enumDropdownExpanded by remember { mutableStateOf(false) }
                            DropDownButton(
                                onClick = { enumDropdownExpanded = true },
                            ) {
                                Text((propValue as Enum<*>).name)
                                DropdownMenu(
                                    expanded = enumDropdownExpanded,
                                    onDismissRequest = { enumDropdownExpanded = false },
                                ) {
                                    val enumClass = propType.java as Class<out Enum<*>>
                                    enumClass.enumConstants.forEach { enumConst ->
                                        DropdownMenuItem(
                                            onClick = {
                                                newParameterFunc(enumConst)
                                                enumDropdownExpanded = false
                                            },
                                        ) {
                                            Text(enumConst.name)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DisplayName(val name: String)

val KAnnotatedElement.displayName: String?
    get() = this.annotations.filterIsInstance<DisplayName>().firstOrNull()?.name

data class MainParameters(
    val width: Int = 512,
    val height: Int = 512,
    val slices: Int = 1
) {
    companion object {
        val icon = Icons.Default.Image
    }
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

enum class GPUFormat(val value: ImageFormat.Sized, val glslFormat: String) {
    R8G8B8A8_UN(ImageFormat.R8G8B8A8_UN, "rgba8"),
    R10G10B10A2_UN(ImageFormat.R10G10B10A2_UN, "rgb10_a2"),
    R16G16B16A16_F(ImageFormat.R16G16B16A16_F, "rgba16f")
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


data class OutputProcessingParameters(
    val format: Format = Format.R8_UNORM,
    val normalize: Boolean = true,
    val minVal: BigDecimal = 0.0.toBigDecimal(),
    val maxVal: BigDecimal = 1.0.toBigDecimal(),
    val flip: Boolean = false,
    val dither: Boolean = true,
) {
    companion object {
        val icon = Icons.Default.Filter
    }
}


enum class DisplayColorMode {
    Grayscale,
    Alpha,
    RGB,
}

data class ViewerParameters(
    @DisplayName("Center X")
    val centerX: BigDecimal = 0.0.toBigDecimal(),
    @DisplayName("Center Y")
    val centerY: BigDecimal = 0.0.toBigDecimal(),
    val slice: BigDecimal = 0.0.toBigDecimal(),
    val zoom: BigDecimal = 0.0.toBigDecimal(),
    val colorMode: DisplayColorMode = DisplayColorMode.Grayscale,
) {
    companion object {
        val icon = Icons.Default.Eye
    }
}

enum class DarkModeOption {
    Auto,
    Dark,
    Light,
}

data class SystemParameters(
    val darkMode: DarkModeOption = DarkModeOption.Auto,
)

enum class CompositeMode {
    Add,
    Subtract,
    Multiply
}

data class NoiseLayerParameters<T : NoiseSpecificParameters>(
    val enabled: Boolean,
    val compositeMode: CompositeMode,
    val specificParameters: T,
    @Transient
    val expanded: Boolean = true,
)

enum class DistanceFunction {
    Euclidean,
    Manhattan,
    Chebyshev
}

@Immutable
sealed interface NoiseSpecificParameters {
    data class Value(
        val value: BigDecimal = 0.0.toBigDecimal(),
    ) : NoiseSpecificParameters

    data class Perlin(
        val rotated: Boolean = false,
    ) : NoiseSpecificParameters

    data class Simplex(
        val rotated: Boolean = false,
    ) : NoiseSpecificParameters

    data class Worley(
        val distanceFunction: DistanceFunction = DistanceFunction.Euclidean,
    )
}