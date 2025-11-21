package dev.luna5ama.strepitus

import androidx.collection.mutableScatterSetOf
import androidx.compose.runtime.snapshots.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.scene.*
import androidx.compose.ui.unit.*
import dev.luna5ama.glwrapper.base.*
import dev.luna5ama.strepitus.gl.GlfwCoroutineDispatcher
import dev.luna5ama.strepitus.gl.subscribeToGLFWEvents
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.skia.*
import org.jetbrains.skiko.FrameDispatcher
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL43C.glDebugMessageCallback
import org.lwjgl.opengl.GLDebugMessageCallback
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.nfd.NativeFileDialog

@OptIn(InternalComposeUiApi::class)
fun main() {
    System.getenv("strepitus.renderdoc")?.let {
        println("Loading RenderDoc library: $it")
        System.load(it)
    }

    val initWidth = 1920
    val initHeight = 1080

    glfwInit()
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, 1)
    glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, 0)
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
    val windowHandle: Long = glfwCreateWindow(initWidth, initHeight, "Strepitus", 0L, 0L)
    glfwMakeContextCurrent(windowHandle)
    glfwSwapInterval(1)

    glfwSetInputMode(windowHandle, GLFW_LOCK_KEY_MODS, GLFW_TRUE)

    GL.createCapabilities()

    val devenv = System.getenv("strepitus.devenv").toBoolean()
    if (devenv) {
        println("Running in development environment")
        glEnable(GL_DEBUG_OUTPUT)
        glDebugMessageCallback(GLDebugMessageCallback.create { source, type, _, severity, length, message, _ ->
            val sourceStr = when (source) {
                GL_DEBUG_SOURCE_API -> "API"
                GL_DEBUG_SOURCE_WINDOW_SYSTEM -> "WINDOW_SYSTEM"
                GL_DEBUG_SOURCE_SHADER_COMPILER -> "SHADER_COMPILER"
                GL_DEBUG_SOURCE_THIRD_PARTY -> "THIRD_PARTY"
                GL_DEBUG_SOURCE_APPLICATION -> "APPLICATION"
                GL_DEBUG_SOURCE_OTHER -> "OTHER"
                else -> "UNKNOWN"
            }
            val typeStr = when (type) {
                GL_DEBUG_TYPE_ERROR -> "ERROR"
                GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR -> "DEPRECATED_BEHAVIOR"
                GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR -> "UNDEFINED_BEHAVIOR"
                GL_DEBUG_TYPE_PORTABILITY -> "PORTABILITY"
                GL_DEBUG_TYPE_PERFORMANCE -> "PERFORMANCE"
                GL_DEBUG_TYPE_MARKER -> "MARKER"
                GL_DEBUG_TYPE_PUSH_GROUP -> "PUSH_GROUP"
                GL_DEBUG_TYPE_POP_GROUP -> "POP_GROUP"
                GL_DEBUG_TYPE_OTHER -> "OTHER"
                else -> "UNKNOWN"
            }
            val severityStr = when (severity) {
                GL_DEBUG_SEVERITY_HIGH -> "HIGH"
                GL_DEBUG_SEVERITY_MEDIUM -> "MEDIUM"
                GL_DEBUG_SEVERITY_LOW -> "LOW"
                GL_DEBUG_SEVERITY_NOTIFICATION -> "NOTIFICATION"
                else -> "UNKNOWN"
            }

            if (type == GL_DEBUG_TYPE_PUSH_GROUP) return@create
            if (type == GL_DEBUG_TYPE_POP_GROUP) return@create
            if (severity == GL_DEBUG_SEVERITY_NOTIFICATION) return@create
            if (severity == GL_DEBUG_SEVERITY_LOW) return@create
            if (severity == GL_DEBUG_SEVERITY_MEDIUM) return@create

            val messageStr = MemoryUtil.memUTF8Safe(message, length) ?: return@create
            println("[OpenGL/$sourceStr] type = $typeStr, severity = $severityStr, message = $messageStr")
            run {} // Dummy line for breakpoint
        }, 0)
        glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS)
        glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DEBUG_SEVERITY_MEDIUM, 0, 0L, false)
        glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DEBUG_SEVERITY_NOTIFICATION, 0, 0L, false)
        glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DEBUG_SEVERITY_LOW, 0, 0L, false)
        glDebugMessageControl(GL_DONT_CARE, GL_DEBUG_TYPE_PUSH_GROUP, GL_DONT_CARE, 0, 0L, false)
        glDebugMessageControl(GL_DONT_CARE, GL_DEBUG_TYPE_POP_GROUP, GL_DONT_CARE, 0, 0L, false)
    }

    val context = DirectContext.makeGL()
    var renderTarget = BackendRenderTarget.makeGL(initWidth, initHeight, 1, 8, 0, 0x8058)
    var surface = Surface.makeFromBackendRenderTarget(
        context,
        renderTarget,
        SurfaceOrigin.BOTTOM_LEFT,
        SurfaceColorFormat.RGBA_8888,
        ColorSpace.sRGB,
        SurfaceProps()
    )!!
    val glfwDispatcher =
        GlfwCoroutineDispatcher(windowHandle) // a custom coroutine dispatcher, in which Compose will run
    val scope = CoroutineScope(glfwDispatcher)

    NativeFileDialog.NFD_Init()

    var renderFunc = {}

    val frameDispatcher = FrameDispatcher(glfwDispatcher) { renderFunc() }
    val state = GLFWWindowState()
    val appState = AppState(windowHandle, glfwDispatcher, scope)
    appState.load()

    Runtime.getRuntime().addShutdownHook(Thread {
        appState.save()
    })

    val renderer =
        NoiseGeneratorRenderer(context, scope, frameDispatcher, appState, state::windowWidth, state::windowHeight)
    val readingStatesOnRender = mutableScatterSetOf<Any>()

    val applyObserverHandle: ObserverHandle = Snapshot.registerApplyObserver { changedStates, _ ->
        for (state in changedStates) {
            if (state in readingStatesOnRender) {
                frameDispatcher.scheduleFrame()
                break
            }
        }
    }

    val temp = floatArrayOf(0f)
    val dummy = floatArrayOf(1.0f)
    glfwGetWindowContentScale(windowHandle, temp, dummy)
    val composeScene = CanvasLayersComposeScene(
        Density(temp[0]),
        size = state.windowSize,
        invalidate = frameDispatcher::scheduleFrame,
        coroutineContext = glfwDispatcher,
        platformContext = state.platformContext
    )
    state.init(windowHandle, composeScene, renderer)

    state.onResize { newWidth, newHeight ->
        surface.close()
        renderTarget.close()

        composeScene.size = IntSize(newWidth, newWidth)
        renderTarget = BackendRenderTarget.makeGL(newWidth, newHeight, 1, 8, 0, 0x8058)
        surface = Surface.makeFromBackendRenderTarget(
            context,
            renderTarget,
            SurfaceOrigin.BOTTOM_LEFT,
            SurfaceColorFormat.RGBA_8888,
            ColorSpace.sRGB,
            SurfaceProps()
        )!!

        glfwSwapInterval(0)
        renderFunc()
        glfwSwapInterval(1)
    }

    renderFunc = {
        Snapshot.observe(readObserver = readingStatesOnRender::add) {
            renderer.draw()
        }
        composeScene.size = state.windowSize
        composeScene.render(surface.canvas.asComposeCanvas(), System.nanoTime())

        context.flush()
        glfwSwapBuffers(windowHandle)
    }

    composeScene.subscribeToGLFWEvents(windowHandle, renderer)
    composeScene.setContent { App(renderer, appState) }
    glfwShowWindow(windowHandle)

    glfwDispatcher.runLoop()

    NativeFileDialog.NFD_Quit()
    applyObserverHandle.dispose()
    composeScene.close()
    renderer.dispose()
    glfwDestroyWindow(windowHandle)
}