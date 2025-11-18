package dev.luna5ama.strepitus.params

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.*
import dev.luna5ama.glwrapper.ShaderProgram
import dev.luna5ama.strepitus.EnumDropdownMenu
import dev.luna5ama.strepitus.ToggleSwitch
import io.github.composefluent.*
import io.github.composefluent.component.*
import io.github.composefluent.icons.*
import io.github.composefluent.icons.regular.*
import org.apache.commons.rng.simple.RandomSource
import java.math.BigDecimal
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.memberProperties

enum class CompositeMode {
    None,
    Add,
    Subtract,
    Multiply
}

enum class DimensionType(override val displayName: String) : DisplayNameOverride {
    _2D("2D"),
    _3D("3D"),
}

private fun Boolean.toInt() = if (this) 1 else 0

interface ShaderProgramParameters {
    fun applyShaderUniforms(shaderProgram: ShaderProgram)
}

data class FBMParameters(
    @IntRangeVal(min = 1, max = 16)
    val octaves: Int = 4,
    @DecimalRangeVal(min = -2.0, max = 2.0, step = 0.03125)
    val persistence: BigDecimal = 0.5.toBigDecimal(),
    @DecimalRangeVal(min = 1.0, max = 4.0, step = 0.03125)
    val lacunarity: BigDecimal = 2.0.toBigDecimal(),
    val perOctaveSeed: Boolean = true,
) : ShaderProgramParameters {
    override fun applyShaderUniforms(shaderProgram: ShaderProgram) {
        shaderProgram.uniform1i("uval_octaves", this.octaves)
        shaderProgram.uniform1f("uval_lacunarity", this.lacunarity.toFloat())
        shaderProgram.uniform1f("uval_persistence", this.persistence.toFloat())
        shaderProgram.uniform1i("uval_perOctaveSeed", this.perOctaveSeed.toInt())
    }
}

data class NoiseLayerParameters(
    @Transient
    @HiddenFromAutoParameter
    val visible: Boolean = true,
    @HiddenFromAutoParameter
    val enabled: Boolean = true,

    // Actual shader parameters
    val compositeMode: CompositeMode = CompositeMode.Add,
    val dimensionType: DimensionType = DimensionType._2D,
    val baseSeed: String,
    @IntRangeVal(min = 1, max = 32)
    val baseFrequency: Int = 4,

    @DisplayName("FBM Parameters")
    val fbmParameters: FBMParameters = FBMParameters(),
    @DisplayName("Noise Type Specific Parameters")
    val specificParameters: NoiseSpecificParameters = NoiseSpecificParameters.Simplex()
) : ShaderProgramParameters {

    override fun applyShaderUniforms(shaderProgram: ShaderProgram) {
        shaderProgram.uniform1i("uval_compositeMode", this.compositeMode.ordinal)
        shaderProgram.uniform1i("uval_dimensionType", this.dimensionType.ordinal)

        shaderProgram.uniform1i("uval_baseFrequency", this.baseFrequency)

        this.fbmParameters.applyShaderUniforms(shaderProgram)
        this.specificParameters.applyShaderUniforms(shaderProgram)
    }


    companion object {
        @OptIn(ExperimentalUnsignedTypes::class)
        private val BASE_SEED_SEED = ulongArrayOf(
            0x37_00_81_3b_4b_b4_9d_d5UL,
            0xe7_bd_39_c5_04_c4_41_f3UL,
            0x94_78_59_58_61_f6_09_21UL,
            0x31_42_83_88_a3_0f_06_71UL,
            0x5a_bc_6e_02_0e_95_e1_2dUL,
            0x01_3c_9c_3b_70_34_bb_4eUL,
            0xab_f4_a0_8e_f7_48_9e_b6UL,
            0xcc_a9_d9_81_19_3c_34_cdUL,
            0x59_58_15_24_67_a6_5e_9eUL,
            0xbf_a8_fd_ab_b2_d9_43_92UL,
            0x35_79_58_3a_cf_22_64_51UL,
            0x17_60_f1_e5_80_95_04_c1UL,
            0xfe_b0_cf_ed_b4_c4_ce_53UL,
            0x86_5d_d1_3e_04_4b_33_4fUL,
            0xf2_ce_67_11_cc_a8_3e_98UL,
            0xd9_13_68_e8_85_4d_93_faUL
        ).toLongArray()

        private const val HEX_CHARS = "0123456789ABCDEF"

        fun generateBaseSeed(index: Int): String {
            val newSeed = BASE_SEED_SEED.copyOf()
            newSeed[0] += index.toLong()
            val random = RandomSource.XO_RO_SHI_RO_1024_PP.create(newSeed)
            return (0..<8).map {
                HEX_CHARS[random.nextInt(HEX_CHARS.length)]
            }.fastJoinToString("")
        }
    }
}

enum class DistanceFunction {
    Euclidean,
    Manhattan,
    Chebyshev
}

enum class NoiseType(
    val defaultParameter: NoiseSpecificParameters,
    val copyFunc: KFunction<NoiseSpecificParameters>,
    val constructor: KFunction<NoiseSpecificParameters>
) {
    Value(NoiseSpecificParameters.Value(), NoiseSpecificParameters.Value::copy, NoiseSpecificParameters::Value),
    Perlin(NoiseSpecificParameters.Perlin(), NoiseSpecificParameters.Perlin::copy, NoiseSpecificParameters::Perlin),
    Simplex(NoiseSpecificParameters.Simplex(), NoiseSpecificParameters.Simplex::copy, NoiseSpecificParameters::Simplex),
    Worley(NoiseSpecificParameters.Worley(), NoiseSpecificParameters.Worley::copy, NoiseSpecificParameters::Worley);

    private val propParams = copyFunc.parameters.asSequence()
        .drop(1)
        .associateBy { it.name!! }

    private val constructorParams = constructor.parameters.associateBy { it.name!! }

    internal val props = defaultParameter::class.memberProperties.asSequence()
        .filter { it.name in propParams }
        .associateBy { it.name }

    internal val mappableValues by lazy {
        entries.associateWithTo(EnumMap(NoiseType::class.java)) { otherType ->
            otherType.propParams.keys.intersect(this.propParams.keys).map {
                otherType.constructorParams[it]!!
            }
        }
    }
}

enum class GradientMode {
    Value,
    Gradient,
    Both
}

@Immutable
sealed interface NoiseSpecificParameters : ShaderProgramParameters {
    val type: NoiseType

    fun copyToType(dstType: NoiseType): NoiseSpecificParameters {
        val src = this
        val srcType = src.type
        val args = srcType.mappableValues[dstType]!!.associateWith {
            srcType.props[it.name]!!.call(src)
        }
        val newParam = dstType.constructor.callBy(args)
        return newParam
    }

    override fun applyShaderUniforms(shaderProgram: ShaderProgram) {
        shaderProgram.uniform1i("uval_noiseType", type.ordinal)
    }

    sealed interface HasGradient : NoiseSpecificParameters, ShaderProgramParameters {
        val gradientMode: GradientMode

        override fun applyShaderUniforms(shaderProgram: ShaderProgram) {
            super.applyShaderUniforms(shaderProgram)
            shaderProgram.uniform1i("uval_gradientMode", gradientMode.ordinal)
        }
    }

    data class Value(
        override val gradientMode: GradientMode = GradientMode.Value,
    ) : NoiseSpecificParameters, HasGradient {
        override val type: NoiseType
            get() = NoiseType.Value
    }

    data class Perlin(
        override val gradientMode: GradientMode = GradientMode.Value,
    ) : NoiseSpecificParameters, HasGradient {
        override val type: NoiseType
            get() = NoiseType.Perlin
    }

    data class Simplex(
        override val gradientMode: GradientMode = GradientMode.Value,
    ) : NoiseSpecificParameters, HasGradient {
        override val type: NoiseType
            get() = NoiseType.Simplex
    }

    data class Worley(
        val distanceFunction: DistanceFunction = DistanceFunction.Euclidean,
    ) : NoiseSpecificParameters {
        override val type: NoiseType
            get() = NoiseType.Worley
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
            ParameterEditor(layer, { layers[i] = it })
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