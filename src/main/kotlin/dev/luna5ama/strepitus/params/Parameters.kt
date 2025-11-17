package dev.luna5ama.strepitus.params

import androidx.compose.runtime.*
import dev.luna5ama.strepitus.DecimalInput
import dev.luna5ama.strepitus.IntegerInput
import dev.luna5ama.strepitus.SliderDecimalInput
import dev.luna5ama.strepitus.SliderIntegerInput
import dev.luna5ama.strepitus.ToggleSwitch
import dev.luna5ama.strepitus.camelCaseToWords
import dev.luna5ama.strepitus.params.displayName
import io.github.composefluent.component.*
import kotlinx.serialization.Transient
import java.math.BigDecimal
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties

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
        .filter { it.annotations.none { ann -> ann is Transient } }
        .sortedBy { copyFunParameterOrder[it.name] ?: Int.MAX_VALUE }

    properties.forEachIndexed { index, it ->
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
                CardExpanderItem(heading = { Text(propName) }) {
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
                CardExpanderItem(heading = { Text(propName) }) {
                    DecimalInput(value = propValue as BigDecimal, onValueChange = newParameterFunc)
                }
            }
        }

        Boolean::class -> {
            CardExpanderItem(heading = { Text(propName) }) {
                ToggleSwitch(
                    checked = propValue as Boolean,
                    onCheckStateChange = newParameterFunc
                )
            }
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
                CardExpanderItem(heading = { Text(propName) }) {
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

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DisplayName(val name: String)

val KAnnotatedElement.displayName: String?
    get() = this.annotations.filterIsInstance<DisplayName>().firstOrNull()?.name

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class IntRangeVal(val min: Int, val max: Int, val step: Int = 1)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DecimalRangeVal(val min: Double, val max: Double, val step: Double)