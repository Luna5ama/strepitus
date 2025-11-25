package dev.luna5ama.strepitus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import dev.luna5ama.strepitus.params.DisplayNameOverride
import io.github.composefluent.*
import io.github.composefluent.component.*
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

@Composable
fun ToggleSwitch(
    checked: Boolean,
    onCheckStateChange: (Boolean) -> Unit,
    textOn: String? = "On",
    textOff: String? = "Off"
) {
    Switcher(
        checked = checked,
        onCheckStateChange = onCheckStateChange,
        text = if (checked) textOn else textOff,
        textBefore = true,
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

@Suppress("AssignedValueIsNeverRead")
@Composable
fun SliderIntegerInput(
    name: String,
    value: Int,
    sliderMin: Int,
    sliderMax: Int,
    sliderStep: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var typedValue by remember(value) { mutableStateOf(value.toString()) }
    var fieldFocus by remember { mutableStateOf(false) }

    Expander(
        expanded = expanded,
        onExpandedChanged = { expanded = it },
        heading = { Text(name) },
        icon = null,
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
        val steps = max((sliderMax - sliderMin - 1) / sliderStep, 1)
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

@Suppress("AssignedValueIsNeverRead")
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

@Suppress("AssignedValueIsNeverRead")
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

    Expander(
        expanded = expanded,
        onExpandedChanged = { expanded = it },
        heading = { Text(name) },
        icon = null,
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
                    fieldFocus = state.isFocused
                    if (!state.isFocused) {
                        typedValue = value.toString()
                    }
                }
            )
        }
    ) {
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
inline fun <reified T: Enum<*>> EnumDropdownMenu(
    value: T,
    noinline onValueChange: (T) -> Unit,
    noinline buttonText: @Composable (String) -> Unit = { text -> Text(text) }
) {
    EnumDropdownMenu(
        value = value,
        enumType = T::class,
        onValueChange = onValueChange,
        buttonText = buttonText
    )
}

@Suppress("UNCHECKED_CAST", "AssignedValueIsNeverRead")
@Composable
fun <T : Enum<*>> EnumDropdownMenu(
    value: T,
    enumType: KClass<T>,
    onValueChange: (T) -> Unit,
    buttonText: @Composable (String) -> Unit = { text -> Text(text) }
) {
    var expanded by remember { mutableStateOf(false) }
    DropDownButton(
        onClick = { expanded = true },
    ) {
        fun enumName(enumConst: Enum<*>): String =
            (enumConst as? DisplayNameOverride)?.displayName ?: enumConst.name

        buttonText(enumName(value))

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val enumClass = enumType.java
            enumClass.enumConstants.forEach { enumConst ->
                DropdownMenuItem(
                    onClick = {
                        onValueChange(enumConst)
                        expanded = false
                    },
                ) {
                    Text(enumName(enumConst))
                }
            }
        }
    }
}

@Composable
fun StringInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    TextField(
        value = value,
        onValueChange = { str ->
            onValueChange(str)
        },
        enabled = enabled,
        singleLine = true
    )
}

@Composable
fun MenuFlyoutScope.MenuFlyoutButton(
    onClick: () -> Unit,
    icon: ImageVector? = null,
    text: String,
    trailingText: String? = null,
    enabled: Boolean = true,
) {
    MenuFlyoutItem(
        onClick = onClick,
        icon = icon?.let { { Icon(imageVector = it, contentDescription = null) } },
        text = { Text(text) },
        trailing = trailingText?.let {
            {
                Spacer(Modifier.Companion.width(16.dp))
                Text(it, style = FluentTheme.typography.caption)
            }
        },
        enabled = enabled,
    )
}