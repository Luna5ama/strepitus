package dev.luna5ama.strepitus

import androidx.collection.mutableScatterSetOf
import androidx.compose.runtime.snapshots.*
import dev.luna5ama.glwrapper.ShaderBindingSpecs
import dev.luna5ama.glwrapper.ShaderProgram
import dev.luna5ama.glwrapper.ShaderSource
import dev.luna5ama.glwrapper.base.*
import dev.luna5ama.glwrapper.enums.*
import dev.luna5ama.glwrapper.objects.BufferObject
import dev.luna5ama.glwrapper.objects.IGLObject
import dev.luna5ama.glwrapper.objects.TextureObject
import dev.luna5ama.kmogus.Arr
import dev.luna5ama.kmogus.asByteBuffer
import dev.luna5ama.strepitus.gl.register
import dev.luna5ama.strepitus.params.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.apache.commons.rng.simple.RandomSource
import org.jetbrains.skia.DirectContext
import org.jetbrains.skiko.FrameDispatcher
import org.lwjgl.glfw.GLFW.GLFW_KEY_F6
import org.lwjgl.glfw.GLFW.GLFW_RELEASE
import java.awt.color.ColorSpace
import java.awt.image.*
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.println
import kotlin.math.pow
import kotlin.use

class NoiseGeneratorRenderer(
    private val context: DirectContext,
    scope: CoroutineScope,
    private val frameDispatcher: FrameDispatcher,
    private val appState: AppState,
    private val widthProvider: () -> Int,
    private val heightProvider: () -> Int,
) : AbstractRenderer() {

    private val mainParameters: MainParameters by appState::mainParameters
    private val outputParameters: OutputParameters by appState::outputParameters
    private val viewerParameters: ViewerParameters by appState::viewerParameters
    private val noiseLayers: List<NoiseLayerParameters> by appState::noiseLayers

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

    private val seedBuffer = register(BufferObject.Immutable()).apply {
        label = "seedBuffer"
        allocate(4L * GRID_SEED_COUNT, GL_DYNAMIC_STORAGE_BIT)
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
        buffer("SeedBuffer", seedBuffer, BufferTarget.ShaderStorage)
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

        Arr.malloc(seedBuffer.size).use { seedUploadBuffer ->
            noiseLayers.forEach { layer ->
                if (!layer.enabled) return@forEach

                val rng = RandomSource.XO_RO_SHI_RO_1024_PP.create(sha512(layer.baseSeed))

                var ptr = seedUploadBuffer.ptr
                repeat(GRID_SEED_COUNT) {
                    ptr = ptr.setIntInc(rng.nextInt())
                }

                seedBuffer.invalidate()
                seedBuffer.upload(seedUploadBuffer.ptr)

                generateNoiseShader.bind()
                generateNoiseShader.applyBinding(bindings)
                generateNoiseShader.uniform3f(
                    "uval_noiseTexSizeF",
                    mainParameters.width.toFloat(),
                    mainParameters.height.toFloat(),
                    mainParameters.slices.toFloat()
                )

                layer.applyShaderUniforms(generateNoiseShader)

                glDispatchCompute(
                    (mainParameters.width + 15) / 16,
                    (mainParameters.height + 15) / 16,
                    mainParameters.slices
                )
                glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT)
            }
        }
    }

    private fun sha512(str: String): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-512")
        return digest.digest(str.toByteArray(Charsets.UTF_8))
    }

    private fun process() {
        countRangeShader.bind()
        countRangeShader.applyBinding(bindings)
        glDispatchCompute((mainParameters.width + 15) / 16, (mainParameters.height + 15) / 16, mainParameters.slices)
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT)

        val normalizeShader = normalizeShaders[outputParameters.format.gpuFormat]
        normalizeShader.bind()
        normalizeShader.applyBinding(bindings)
        normalizeShader.uniform1i("uval_normalize", if (outputParameters.normalize) 1 else 0)
        normalizeShader.uniform1f("uval_minVal", outputParameters.minVal.toFloat())
        normalizeShader.uniform1f("uval_maxVal", outputParameters.maxVal.toFloat())
        normalizeShader.uniform1i("uval_flip", if (outputParameters.flip) 1 else 0)
        normalizeShader.uniform1i("uval_dither", if (outputParameters.dither) 1 else 0)
        glDispatchCompute((mainParameters.width + 15) / 16, (mainParameters.height + 15) / 16, mainParameters.slices)
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT)
    }

    private val alwaysRegenerate = System.getenv("strepitus.alwaysregen").toBoolean()

    override fun draw() {
        context.flush()

        val generateNoiseShader = generateNoiseShader
        if (generateNoiseShader != null) {
            if (alwaysRegenerate || needRegenerate.getAndSet(false)) {
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

        context.resetGLAll()
        context.flush()
    }

    fun saveImage(outputPath: Path, format: OutputFileFormat) {
        context.flush()

        val outputSpec = outputParameters.format.outputSpec
        val dataSize = mainParameters.width * mainParameters.height * mainParameters.slices * outputSpec.pixelSize

        println("Saving image...")
        val buffer = BufferObject.Immutable()
        buffer.allocate(dataSize, GL_MAP_READ_BIT)
        buffer.bind(GL_PIXEL_PACK_BUFFER)
        glGetTextureImage(outputImage.id, 0, outputSpec.format, outputSpec.pixelType, dataSize.toInt(), 0)
        buffer.unbind(GL_PIXEL_PACK_BUFFER)
        glFinish()
        val mapped = buffer.map(GL_MAP_READ_BIT)

        outputPath.parent.absolute().createDirectories()

        when (format) {
            OutputFileFormat.Binary -> {
                FileChannel.open(
                    outputPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE
                ).use { fc ->
                    fc.truncate(buffer.size)
                    val mappedBuffer = fc.map(FileChannel.MapMode.READ_WRITE, 0, buffer.size)
                        .order(ByteOrder.nativeOrder())
                    val byteBuffer = mapped.ptr.asByteBuffer(mapped.len.toInt())
                    mappedBuffer.put(byteBuffer)
                }
            }
            OutputFileFormat.PNG -> {
                val channelCount = outputSpec.channels
                val colorSpace = when (channelCount) {
                    1 -> ColorSpace.getInstance(ColorSpace.CS_GRAY)
                    else -> ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB)
                }

                val pixelTypeBitSize = outputSpec.pixelTypeBitSize
                val pixelTypeBytes = pixelTypeBitSize / 8
                val bits = IntArray(channelCount) { pixelTypeBitSize }
                val transferType = when (outputSpec.pixelType) {
                    GL_UNSIGNED_BYTE -> DataBuffer.TYPE_BYTE
                    GL_UNSIGNED_SHORT -> DataBuffer.TYPE_USHORT
                    GL_SHORT -> DataBuffer.TYPE_SHORT
                    GL_INT -> DataBuffer.TYPE_INT
                    GL_FLOAT -> DataBuffer.TYPE_FLOAT
                    else -> throw IllegalStateException("Unsupported pixel type for PNG export: ${outputSpec.pixelType}")
                }

                val colorModel = ComponentColorModel(
                    colorSpace,
                    bits,
                    channelCount == 4,
                    false,
                    if (channelCount == 4) ColorModel.TRANSLUCENT else ColorModel.OPAQUE,
                    transferType
                )
                val bandOffsets = IntArray(channelCount) { it }
                val raster = Raster.createInterleavedRaster(
                    transferType,
                    mainParameters.width,
                    mainParameters.height,
                    mainParameters.width * channelCount,
                    channelCount,
                    bandOffsets,
                    null
                )
                val bufferedImage = BufferedImage(colorModel, raster, false, null)

                if (transferType == DataBuffer.TYPE_FLOAT) {
                    val tempArray = FloatArray(channelCount)
                    for (y in (mainParameters.height - 1) downTo 0) {
                        val xPtr = mapped.ptr + (y * mainParameters.width * outputSpec.pixelSize)
                        for (x in 0..<mainParameters.width) {
                            val ptr = xPtr + (x * outputSpec.pixelSize)
                            repeat(channelCount) {
                                tempArray[it] = ptr.getFloat((it * pixelTypeBytes).toLong())
                            }
                            raster.setPixel(x, y, tempArray)
                        }
                    }
                } else {
                    val tempArray = IntArray(channelCount)
                    for (y in (mainParameters.height - 1) downTo 0) {
                        val xPtr = mapped.ptr + (y * mainParameters.width * outputSpec.pixelSize)
                        for (x in 0..<mainParameters.width) {
                            val ptr = xPtr + (x * outputSpec.pixelSize)
                            when (transferType) {
                                DataBuffer.TYPE_BYTE -> {
                                    repeat(channelCount) {
                                        tempArray[it] = ptr.getByte((it * pixelTypeBytes).toLong()).toInt() and 0xFF
                                    }
                                }

                                DataBuffer.TYPE_USHORT, DataBuffer.TYPE_SHORT -> {
                                    repeat(channelCount) {
                                        tempArray[it] =
                                            ptr.getShort((it * pixelTypeBytes).toLong()).toInt() and 0xFFFF
                                    }
                                }

                                DataBuffer.TYPE_INT -> {
                                    repeat(channelCount) {
                                        tempArray[it] = ptr.getInt((it * pixelTypeBytes).toLong())
                                    }
                                }

                                else -> throw IllegalStateException("Unsupported transfer type for PNG export: $transferType")
                            }
                            raster.setPixel(x, y, tempArray)
                        }
                    }
                }

                ImageIO.write(bufferedImage, "png", outputPath.toFile())
            }
        }

        buffer.unmap()
        buffer.destroy()

        context.resetGLAll()
        context.flush()
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

    private companion object {
        const val GRID_SEED_COUNT = 128
    }
}