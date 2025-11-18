@file:UseSerializers(BigDecimalSerializer::class)
package dev.luna5ama.strepitus.params

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import dev.luna5ama.strepitus.*
import io.github.composefluent.component.*
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import java.math.BigDecimal
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

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

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DisplayName(val name: String)

interface DisplayNameOverride {
    val displayName: String
}

val KAnnotatedElement.displayName: String?
    get() = this.annotations.filterIsInstance<DisplayName>().firstOrNull()?.name

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class IntRangeVal(val min: Int, val max: Int, val step: Int = 1)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class HiddenFromAutoParameter

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DecimalRangeVal(val min: Double, val max: Double, val step: Double)