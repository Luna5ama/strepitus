package dev.luna5ama.strepitus

import androidx.collection.mutableScatterSetOf
import androidx.compose.runtime.snapshots.*
import dev.luna5ama.glwrapper.ShaderBindingSpecs
import dev.luna5ama.glwrapper.ShaderProgram
import dev.luna5ama.glwrapper.ShaderSource
import dev.luna5ama.glwrapper.base.*
import dev.luna5ama.glwrapper.enums.BufferTarget
import dev.luna5ama.glwrapper.enums.FilterMode
import dev.luna5ama.glwrapper.enums.GLObjectType
import dev.luna5ama.glwrapper.enums.ImageFormat
import dev.luna5ama.glwrapper.enums.WrapMode
import dev.luna5ama.glwrapper.objects.BufferObject
import dev.luna5ama.glwrapper.objects.IGLObject
import dev.luna5ama.glwrapper.objects.TextureObject
import dev.luna5ama.strepitus.gl.register
import dev.luna5ama.strepitus.params.GPUFormat
import dev.luna5ama.strepitus.params.MainParameters
import dev.luna5ama.strepitus.params.NoiseLayerParameters
import dev.luna5ama.strepitus.params.NoiseSpecificParameters
import dev.luna5ama.strepitus.params.OutputParameters
import dev.luna5ama.strepitus.params.ViewerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.skiko.FrameDispatcher
import org.lwjgl.glfw.GLFW.GLFW_KEY_F6
import org.lwjgl.glfw.GLFW.GLFW_RELEASE
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.forEach
import kotlin.math.pow

class NoiseGeneratorRenderer(
    private val scope: CoroutineScope,
    private val frameDispatcher: FrameDispatcher,
    private val widthProvider: () -> Int,
    private val heightProvider: () -> Int,
) : AbstractRenderer() {
    lateinit var mainParametersProvider: () -> MainParameters
    lateinit var outputParametersProvider: () -> OutputParameters
    lateinit var viewerParametersProvider: () -> ViewerParameters
    lateinit var noiseLayersProvider: () -> List<NoiseLayerParameters>

    private val mainParameters: MainParameters
        get() = mainParametersProvider()
    private val outputParameters: OutputParameters
        get() = outputParametersProvider()
    private val viewerParameters: ViewerParameters
        get() = viewerParametersProvider()
    private val noiseLayers: List<NoiseLayerParameters>
        get() = noiseLayersProvider()

    var frameWidth = 0
    var frameHeight = 0

    val windowWidth: Int
        get() = widthProvider()

    val windowHeight: Int
        get() = heightProvider()

    private val noiseImage = register(TextureObject.Texture3D()).apply {
        label = "noiseImage"
    }

    private val outputImage = register(TextureObject.Texture3D()).apply {
        label = "outputImage"
    }

    private val dataBuffer = register(BufferObject.Immutable()).apply {
        label = "dataBuffer"
        allocate(16L, 0)
    }

    private val bindings = ShaderBindingSpecs.of {
        image("uimg_noiseImage", noiseImage)
        image("uimg_outputImage", outputImage)
        sampler("usam_outputImageTiling", outputImage, samplerManager.get {
            filter(FilterMode.Linear, FilterMode.Linear)
            wrap(WrapMode.Repeat, WrapMode.Repeat, WrapMode.ClampToEdge)
        })
        sampler("usam_outputImage", outputImage, samplerManager.get {
            filter(FilterMode.Linear, FilterMode.Linear)
            wrap(WrapMode.ClampToBorder, WrapMode.ClampToBorder, WrapMode.ClampToEdge)
            borderColor(0.0f, 0.0f, 0.0f, 0.0f)
        })
        buffer("DataBuffer", dataBuffer, BufferTarget.ShaderStorage)
    }

    private var generateNoiseShader: ShaderProgram? = null
    private var lastSource = ""

    private fun updateShaderProgram(): Boolean {
        val src = ShaderSource.Comp("GenerateNoise.comp.glsl")
        val srcStr = src.resolveCodeSrc()
        if (generateNoiseShader == null || lastSource != srcStr) {
            lastSource = srcStr
            val newProgram = try {
                ShaderProgram(src)
            } catch (_: IllegalStateException) {
                return false
            }
            generateNoiseShader?.destroy()
            generateNoiseShader = newProgram
            return true
        }

        return false
    }

    private val resetCounterShader = register(ShaderProgram(ShaderSource.Comp("ResetCounter.comp.glsl")))
    private val countRangeShader = register(ShaderProgram(ShaderSource.Comp("CountRange.comp.glsl")))
    private val normalizeShaders = object : ShaderProgram.Variants<GPUFormat>() {
        override fun create(key: GPUFormat): ShaderProgram {
            return register(ShaderProgram(ShaderSource.Comp("Normalize.comp.glsl") {
                define("OUTPUT_IMAGE_FORMAT", key.glslFormat)
            }))
        }
    }

    private val readingStatesOnGenerate = mutableScatterSetOf<Any>()
    private val readingStatesOnProcess = mutableScatterSetOf<Any>()
    private val needRegenerate = AtomicBoolean(true)
    private val needReprocess = AtomicBoolean(true)
    private val applyObserverHandle: ObserverHandle = Snapshot.registerApplyObserver { changedStates, _ ->
        if (needRegenerate.get()) return@registerApplyObserver
        for (state in changedStates) {
            if (state in readingStatesOnGenerate) {
                needRegenerate.set(true)
                break
            }
            if (!needReprocess.get() && state in readingStatesOnProcess) {
                needReprocess.set(true)
            }
        }
    }

    private val finalBlitShader = register(
        ShaderProgram(
            ShaderSource.Vert("Blit.vert.glsl"),
            ShaderSource.Frag("Blit.frag.glsl")
        )
    )

    init {
        keyboard.register(GLFW_KEY_F6) { action ->
            if (action == GLFW_RELEASE) {
                reloadShaders()
            }
        }
        scope.launch {
            while (isActive) {
                if (updateShaderProgram()) {
                    needRegenerate.set(true)
                    frameDispatcher.scheduleFrame()
                }
                delay(500L)
            }
        }
    }

    private fun generate(generateNoiseShader: ShaderProgram) {
        noiseImage.destroy()
        outputImage.destroy()

        noiseImage.allocate(
            1,
            ImageFormat.R32G32B32A32_F,
            mainParameters.width,
            mainParameters.height,
            mainParameters.slices
        )
        outputImage.allocate(
            1,
            outputParameters.format.gpuFormat.value,
            mainParameters.width,
            mainParameters.height,
            mainParameters.slices
        )

        resetCounterShader.bind()
        resetCounterShader.applyBinding(bindings)
        glDispatchCompute(1, 1, 1)

        noiseLayers.forEach {
            if (!it.enabled) return@forEach
            generateNoiseShader.bind()
            generateNoiseShader.applyBinding(bindings)
            generateNoiseShader.uniform3f(
                "uval_noiseTexSizeF",
                mainParameters.width.toFloat(),
                mainParameters.height.toFloat(),
                mainParameters.slices.toFloat()
            )

            val gradientMode = (it.specificParameters as? NoiseSpecificParameters.HasGradient)?.gradientMode?.ordinal ?: 0
            generateNoiseShader.uniform1i("uval_gradientMode", gradientMode)

            generateNoiseShader.uniform1i("uval_baseFrequency", it.baseFrequency)
            generateNoiseShader.uniform1i("uval_octaves", it.octaves)
            generateNoiseShader.uniform1f("uval_lacunarity", it.lacunarity.toFloat())
            generateNoiseShader.uniform1f("uval_persistence", it.persistence.toFloat())
            generateNoiseShader.uniform1i("uval_compositeMode", it.compositeMode.ordinal)

            glDispatchCompute(mainParameters.width / 16, mainParameters.height / 16, mainParameters.slices)
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT)
        }
    }

    private fun process() {
        countRangeShader.bind()
        countRangeShader.applyBinding(bindings)
        glDispatchCompute(mainParameters.width / 32, mainParameters.height / 32, mainParameters.slices)
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT)

        val normalizeShader = normalizeShaders[outputParameters.format.gpuFormat]
        normalizeShader.bind()
        normalizeShader.applyBinding(bindings)
        normalizeShader.uniform1i("uval_normalize", if (outputParameters.normalize) 1 else 0)
        normalizeShader.uniform1f("uval_minVal", outputParameters.minVal.toFloat())
        normalizeShader.uniform1f("uval_maxVal", outputParameters.maxVal.toFloat())
        normalizeShader.uniform1i("uval_flip", if (outputParameters.flip) 1 else 0)
        normalizeShader.uniform1i("uval_dither", if (outputParameters.dither) 1 else 0)
        glDispatchCompute(mainParameters.width / 16, mainParameters.height / 16, mainParameters.slices)
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT)
    }

    override fun draw() {
        val generateNoiseShader = generateNoiseShader
        if (generateNoiseShader != null) {
            if (needRegenerate.getAndSet(false)) {
                needReprocess.set(true)
                Snapshot.observe(readObserver = readingStatesOnGenerate::add) {
                    generate(generateNoiseShader)
                }
            }
            if (needReprocess.getAndSet(false)) {
                Snapshot.observe(readObserver = readingStatesOnProcess::add) {
                    process()
                }
            }
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glViewport(windowWidth - frameWidth, 0, frameWidth, frameHeight)
        glScissor(0, 0, windowWidth, windowHeight)
        finalBlitShader.bind()
        finalBlitShader.applyBinding(bindings)
        finalBlitShader.uniform3f(
            "uval_noiseTexSizeF",
            mainParameters.width.toFloat(),
            mainParameters.height.toFloat(),
            mainParameters.slices.toFloat()
        )
        finalBlitShader.uniform1f("uval_slice", viewerParameters.slice.toFloat())
        finalBlitShader.uniform1f("uval_zoom", 2.0.pow(-viewerParameters.zoom.toDouble()).toFloat())
        finalBlitShader.uniform1i("uval_colorMode", viewerParameters.colorMode.ordinal)
        finalBlitShader.uniform1i("uval_tilling", if (viewerParameters.tilling) 1 else 0)
        var offsetX = frameWidth / 2.0 - mainParameters.width / 2.0
        offsetX += windowWidth - frameWidth
        offsetX -= viewerParameters.centerX.toDouble()
        var offsetY = frameHeight / 2.0 - mainParameters.height / 2.0
        offsetY += viewerParameters.centerY.toDouble()
        finalBlitShader.uniform2f("uval_offset", offsetX.toFloat(), offsetY.toFloat())
        basic.drawQuad()
    }

    fun dispose() {
        applyObserverHandle.dispose()
        this.destroy()
    }

    fun reloadShaders() {
        needRegenerate.set(true)
        ShaderSource.clearCache()
        val objs = mutableMapOf<GLObjectType, MutableSet<IGLObject>>()
        collectObjs(objs)
        objs[GLObjectType.Program]?.forEach { obj ->
            if (obj is ShaderProgram) {
                runCatching {
                    obj.reload()
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }
    }
}