package dev.luna5ama.strepitus

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import dev.luna5ama.strepitus.params.*
import dev.luna5ama.strepitus.util.camelCaseToWords
import io.github.composefluent.*
import io.github.composefluent.component.*
import io.github.composefluent.component.rememberScrollbarAdapter
import io.github.composefluent.icons.*
import io.github.composefluent.icons.regular.*
import kotlinx.serialization.Transient
import java.math.BigDecimal
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField


@OptIn(ExperimentalFoundationApi::class, ExperimentalFluentApi::class)
@Composable
fun SideEditor(renderer: NoiseGeneratorRenderer, appState: AppState) {
    var mainParameters by appState::mainParameters
    var outputParameters by appState::outputParameters
    var viewerParameters by appState::viewerParameters
    var systemParameters by appState::systemParameters
    val noiseLayers by appState::noiseLayers

    Row {
        var sideNavItem by remember { mutableStateOf(SideNavItem.Main) }
        var sideNavExpanded by remember { mutableStateOf(false) }

        @Composable
        fun AppSideNavItem(item: SideNavItem) {
            SideNavItem(
                selected = sideNavItem == item,
                onClick = { if (it) sideNavItem = item },
                icon = {
                    Icon(imageVector = item.icon, contentDescription = "")
                },
            ) {
                Text(item.name)
            }
        }

        SideNav(
            expanded = sideNavExpanded,
            onExpandStateChange = { sideNavExpanded = it },
            footer = {
                val item = SideNavItem.Setting
                AppSideNavItem(item)
            }
        ) {
            SideNavItem.entries.dropLast(1).forEach { item ->
                AppSideNavItem(item)
            }
        }

        val scrollState = rememberScrollState()
        ScrollbarContainer(
            modifier = Modifier
                .background(color = FluentTheme.colors.background.layer.default),
            adapter = rememberScrollbarAdapter(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .width(480.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    sideNavItem.name,
                    style = FluentTheme.typography.title.copy(color = FluentTheme.colors.text.text.primary),
                    modifier = Modifier.padding(8.dp, vertical = 12.dp)
                )
                when (sideNavItem) {
                    SideNavItem.Main -> {
                        ParameterEditor(
                            mainParameters,
                            { mainParameters = it }
                        )
                    }

                    SideNavItem.Output -> {
                        ParameterEditor(
                            outputParameters,
                            { outputParameters = it }
                        )
                    }

                    SideNavItem.Viewer -> {
                        ParameterEditor(
                            viewerParameters,
                            { viewerParameters = it }
                        )
                    }

                    SideNavItem.Noise -> {
                        NoiseLayerEditor(noiseLayers)
                    }

                    SideNavItem.Setting -> {
                        ParameterEditor(
                            systemParameters,
                            { systemParameters = it }
                        )
                        CardExpanderItem(heading = { }, icon = null) {
                            Button(
                                onClick = { renderer.reloadShaders() },
                                buttonColors = ButtonDefaults.accentButtonColors()
                            ) {
                                Text("Reload Shaders")
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
fun NoiseLayerEditor(
    layers: SnapshotStateList<NoiseLayerParameters>,
) {
    var deletingIndex by remember { mutableIntStateOf(-1) }
    ContentDialog(
        title = "Delete Layer",
        visible = deletingIndex in layers.indices,
        size = DialogSize.Standard,
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
            layer.visible,
            onExpandedChanged = { layers[i] = layer.copy(visible = it) },
            icon = {
                Spacer(modifier = Modifier.width(FluentTheme.typography.subtitle.fontSize.value.dp * 3.0f))
                Icon(
                    imageVector = Icons.Default.ReOrderDotsVertical,
                    contentDescription = "",
                    modifier = Modifier.size(FluentTheme.typography.subtitle.fontSize.value.dp)
                )
            },
            heading = {
                EnumDropdownMenu(
                    value = layer.specificParameters.type,
                    onValueChange = { newType ->
                        val currType = layer.specificParameters.type
                        if (newType != currType) {
                            layers[i] = layer.copy(specificParameters = layer.specificParameters.copyToType(newType))
                        }
                    },
                    buttonText = {
                        Text(it, style = FluentTheme.typography.subtitle, modifier = Modifier.padding(vertical = 8.dp))
                    }
                )
            },
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            layers.add(i, layer)
                        },
                        iconOnly = true
                    ) {
                        Icon(
                            imageVector = Icons.Default.CopyAdd,
                            contentDescription = "Duplicate Layer"
                        )
                    }
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
                    Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                        ToggleSwitch(
                            checked = layer.enabled,
                            onCheckStateChange = { newEnabled ->
                                layers[i] = layer.copy(enabled = newEnabled)
                            },
                            textOn = null,
                            textOff = null
                        )
                    }
                }
            }
        ) {
            ParameterEditor(layer) { layers[i] = it }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth(0.5f)) {
            var showAddMenu by remember { mutableStateOf(false) }
            Button(
                onClick = { showAddMenu = true },
                buttonColors = ButtonDefaults.accentButtonColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
            }

            DropdownMenu(
                expanded = showAddMenu,
                onDismissRequest = { showAddMenu = false }
            ) {
                NoiseType.entries.forEach { noiseType ->
                    DropdownMenuItem(
                        onClick = {
                            layers.add(
                                NoiseLayerParameters(
                                    baseSeed = NoiseLayerParameters.generateBaseSeed(layers.size),
                                    specificParameters = noiseType.defaultParameter
                                )
                            )
                            showAddMenu = false
                        },
                    ) {
                        Text(noiseType.name)
                    }
                }
            }
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
    val copyFunc = clazz.memberFunctions.first { member -> member.name == "copy" }
    val copyFunParameterOrder = copyFunc.parameters.drop(1).withIndex().associate { it.value.name!! to it.index }
    val properties = clazz.memberProperties
        .filter { it.javaField != null }
        .filter { it.annotations.none { ann -> ann is HiddenFromAutoParameter || ann is Transient } }
        .sortedBy { copyFunParameterOrder[it.name] ?: Int.MAX_VALUE }

    properties.forEach {
        val propValue = it.get(parameters)!!
        val newParameterFunc = { newValue: Any ->
            val newParameters = copyFunc.callBy(
                mapOf(
                    copyFunc.parameters[0] to parameters,
                    copyFunc.parameters[1 + copyFunParameterOrder[it.name]!!] to newValue
                )
            ) as T
            onChange(newParameters)
        }
        ParameterField(
            prop = it,
            propValue = propValue,
            newParameterFunc = newParameterFunc
        )
    }
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun ParameterField(
    prop: KProperty<*>,
    propValue: Any,
    newParameterFunc: (Any) -> Unit
) {
    val propName = prop.displayName ?: camelCaseToWords(prop.name)
    when (val propType = prop.returnType.classifier!! as KClass<Any>) {

        String::class -> {
            CardExpanderItem(heading = { Text(propName) }, icon = null) {
                StringInput(
                    value = propValue as String,
                    onValueChange = newParameterFunc
                )
            }
        }

        Int::class -> {
            val intRangeAnn = prop.annotations.filterIsInstance<IntRangeVal>().firstOrNull()
            if (intRangeAnn != null) {
                SliderIntegerInput(
                    name = propName,
                    value = propValue as Int,
                    sliderMin = intRangeAnn.min,
                    sliderMax = intRangeAnn.max,
                    sliderStep = intRangeAnn.step,
                    onValueChange = newParameterFunc
                )
            } else {
                CardExpanderItem(heading = { Text(propName) }, icon = null) {
                    IntegerInput(value = propValue as Int, onValueChange = newParameterFunc)
                }
            }
        }

        BigDecimal::class -> {
            val decimalRangeAnn = prop.annotations.filterIsInstance<DecimalRangeVal>().firstOrNull()
            if (decimalRangeAnn != null) {
                SliderDecimalInput(
                    name = propName,
                    value = propValue as BigDecimal,
                    sliderMin = decimalRangeAnn.min.toBigDecimal(),
                    sliderMax = decimalRangeAnn.max.toBigDecimal(),
                    sliderStep = decimalRangeAnn.step.toBigDecimal(),
                    onValueChange = newParameterFunc
                )
            } else {
                CardExpanderItem(heading = { Text(propName) }, icon = null) {
                    DecimalInput(value = propValue as BigDecimal, onValueChange = newParameterFunc)
                }
            }
        }

        Boolean::class -> {
            CardExpanderItem(heading = { Text(propName) }, icon = null) {
                ToggleSwitch(
                    checked = propValue as Boolean,
                    onCheckStateChange = newParameterFunc
                )
            }
        }

        else -> when {
            propType.isData || propValue::class.isData -> {
                CardExpanderItem(
                    heading = {
                        var expanded by remember { mutableStateOf(false) }
                        Expander(
                            expanded = expanded,
                            onExpandedChanged = { expanded = it },
                            heading = { Text(propName) },
                            icon = null,
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            ParameterEditor(
                                clazz = propValue::class as KClass<Any>,
                                parameters = propValue,
                                onChange = newParameterFunc
                            )
                        }
                    }, icon = null
                )
            }

            Enum::class.isSuperclassOf(propType) -> {
                CardExpanderItem(heading = { Text(propName) }, icon = null) {
                    EnumDropdownMenu(
                        propValue as Enum<*>,
                        propType as KClass<Enum<*>>,
                        newParameterFunc
                    )
                }
            }
        }
    }
}