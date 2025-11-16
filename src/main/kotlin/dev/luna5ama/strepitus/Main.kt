package dev.luna5ama.strepitus

import androidx.collection.mutableScatterSetOf
import androidx.compose.runtime.snapshots.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.scene.*
import androidx.compose.ui.unit.*
import dev.luna5ama.strepitus.gl.GlfwCoroutineDispatcher
import dev.luna5ama.strepitus.gl.subscribeToGLFWEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.skia.*
import org.jetbrains.skiko.FrameDispatcher
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL

@OptIn(InternalComposeUiApi::class)
fun main() {
    var width = 1920
    var height = 1080

    glfwInit()
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, 1)
    glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, 0)
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
    val windowHandle: Long = glfwCreateWindow(width, height, "Strepitus", 0L, 0L)
    glfwMakeContextCurrent(windowHandle)
    glfwSwapInterval(1)

    glfwSetInputMode(windowHandle, GLFW_LOCK_KEY_MODS, GLFW_TRUE)

    GL.createCapabilities()

    val context = DirectContext.makeGL()
    var renderTarget = BackendRenderTarget.makeGL(width, height, 1, 8, 0, 0x8058)
    var surface = Surface.makeFromBackendRenderTarget(
        context,
        renderTarget,
        SurfaceOrigin.BOTTOM_LEFT,
        SurfaceColorFormat.RGBA_8888,
        ColorSpace.sRGB,
        SurfaceProps()
    )!!
    val glfwDispatcher = GlfwCoroutineDispatcher() // a custom coroutine dispatcher, in which Compose will run
    val scope = CoroutineScope(glfwDispatcher)

    glfwSetWindowCloseCallback(windowHandle) {
        scope.cancel()
        glfwDispatcher.stop()
    }

    var renderFunc = {}

    val frameDispatcher = FrameDispatcher(glfwDispatcher) { renderFunc() }
    val renderer = NoiseGeneratorRenderer(scope, frameDispatcher,{ width }, { height })
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
        size = IntSize(width, height),
        invalidate = frameDispatcher::scheduleFrame,
        coroutineContext = glfwDispatcher
    )

    renderFunc = {
        Snapshot.observe(readObserver = readingStatesOnRender::add) {
            renderer.draw()
        }
        context.resetGLAll()
        context.flush()
        composeScene.size = IntSize(width, height)
        composeScene.render(surface.canvas.asComposeCanvas(), System.nanoTime())

        context.flush()
        glfwSwapBuffers(windowHandle)
    }

    glfwSetWindowSizeCallback(windowHandle) { _, windowWidth, windowHeight ->
        width = windowWidth
        height = windowHeight
        surface.close()
        renderTarget.close()

        renderTarget = BackendRenderTarget.makeGL(width, height, 1, 8, 0, 0x8058)
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

    composeScene.subscribeToGLFWEvents(windowHandle, renderer)
    composeScene.setContent { App(renderer) }
    glfwShowWindow(windowHandle)

    glfwDispatcher.runLoop()

    applyObserverHandle.dispose()
    composeScene.close()
    renderer.dispose()
    glfwDestroyWindow(windowHandle)
}