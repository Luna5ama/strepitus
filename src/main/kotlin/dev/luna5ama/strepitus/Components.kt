package dev.luna5ama.strepitus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.input.*
import io.github.composefluent.component.*
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.max
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

@Composable
fun ToggleSwitch(
    checked: Boolean,
    onCheckStateChange: (Boolean) -> Unit,
    textOn: String = "On",
    textOff: String = "Off",
) {
    Switcher(
        checked = checked,
        onCheckStateChange = onCheckStateChange,
        text = if (checked) textOn else textOff,
        textBefore = true
    )
}

@Composable
fun IntegerInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    TextField(
        value = value.toString(),
        onValueChange = { str ->
            str.toIntOrNull()?.let {
                onValueChange(it)
            }
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        keyboardActions = KeyboardActions.Default,
    )
}

@Suppress("UNCHECKED_CAST")
private val sliderStateOnValueChangeProp = SliderState::class.memberProperties
    .find { it.name == "onValueChange" }!!
    .run {
        isAccessible = true
        this as KMutableProperty1<SliderState, (Float) -> Unit>
    }

@Composable
fun SliderIntegerInput(
    name: String,
    value: Int,
    sliderMin: Int,
    sliderMax: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var typedValue by remember(value) { mutableStateOf(value.toString()) }
    var fieldFocus by remember { mutableStateOf(false) }

    val steps = (sliderMax - sliderMin - 1).coerceAtLeast(0)
    val sliderState = remember(fieldFocus) {
        SliderState(
            value.toFloat(),
            steps,
            true,
            { },
            sliderMin.toFloat()..sliderMax.toFloat()
        )
    }
    sliderState.value = value.toFloat()
    sliderStateOnValueChangeProp.set(sliderState) {
        onValueChange(sliderState.nearestValue().toInt())
    }
    Expander(
        expanded = expanded,
        onExpandedChanged = { expanded = it },
        heading = { Text(name) },
        trailing = {
            TextField(
                value = typedValue,
                onValueChange = { str ->
                    typedValue = str
                    str.toIntOrNull()?.let {
                        onValueChange(it)
                    }
                },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions.Default,
                modifier = Modifier.onFocusChanged { state ->
                    fieldFocus = state.isFocused
                    if (!state.isFocused) {
                        typedValue = value.toString()
                    }
                }
            )
        }
    ) {
        CardExpanderItem(heading = {}) {
            Slider(
                state = sliderState,
                showTickMark = false,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            )
        }
    }
}

@Composable
fun DecimalInput(
    value: BigDecimal,
    onValueChange: (BigDecimal) -> Unit,
    enabled: Boolean = true,
) {
    var typedValue by remember(value) { mutableStateOf(value.toString()) }
    TextField(
        value = typedValue,
        onValueChange = { str ->
            typedValue = str
            str.toBigDecimalOrNull()?.let {
                onValueChange(it)
            }
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        keyboardActions = KeyboardActions.Default,
        modifier = Modifier.onFocusChanged { state ->
            if (!state.isFocused) {
                typedValue = value.toString()
            }
        }
    )
}

@Composable
fun SliderDecimalInput(
    name: String,
    value: BigDecimal,
    sliderMin: BigDecimal,
    sliderMax: BigDecimal,
    sliderStep: BigDecimal,
    onValueChange: (BigDecimal) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var typedValue by remember(value) { mutableStateOf(value.toString()) }
    var fieldFocus by remember { mutableStateOf(false) }

    val steps = max(((sliderMax - sliderMin) / sliderStep).toInt() - 1, 1)
    val sliderState = remember(fieldFocus) {
        SliderState(
            value.toFloat(),
            steps,
            true,
            { },
            sliderMin.toFloat()..sliderMax.toFloat()
        )
    }
    sliderState.value = value.toFloat()
    sliderStateOnValueChangeProp.set(sliderState) {
        val newValue = (sliderState.nearestValue().toBigDecimal() / sliderStep).setScale(0, RoundingMode.HALF_UP) * sliderStep
        onValueChange(newValue)
    }

    Expander(
        expanded = expanded,
        onExpandedChanged = { expanded = it },
        heading = { Text(name) },
        trailing = {
            TextField(
                value = typedValue,
                onValueChange = { str ->
                    typedValue = str
                    str.toBigDecimalOrNull()?.let {
                        onValueChange(it)
                    }
                },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                keyboardActions = KeyboardActions.Default,
                modifier = Modifier.onFocusChanged { state ->
                    if (!state.isFocused) {
                        typedValue = value.toString()
                    }
                }
            )
        }
    ) {
        CardExpanderItem(heading = {}) {
            Slider(
                state = sliderState,
                showTickMark = false,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            )
        }
    }
}