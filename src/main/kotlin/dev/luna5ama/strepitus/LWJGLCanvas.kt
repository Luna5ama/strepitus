package dev.luna5ama.strepitus

import org.joml.Matrix4f
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import javax.swing.SwingUtilities
import kotlin.properties.Delegates

class LWJGLCanvas(
    val renderer: Renderer,
    var rotationSpeed: Float,
) : AWTGLCanvas(GLData().apply {
    swapInterval = 1
    sRGB = true
}) {
    init {
        println("Recomposição")
    }

    override fun initGL() {
        println("OpenGL version: " + effective.majorVersion + "." + effective.minorVersion + " (Profile: " + effective.profile + ")")
        GL.createCapabilities()
        renderer.setup()
    }

    private var lastDeltaTimeSec by Delegates.notNull<Float>()

    private var rotation = 0f

    override fun paintGL() {
        glViewport(0, 0, width, height)
        glClearColor(0f, 0f, 0f, 1f)
        glClear(GL_COLOR_BUFFER_BIT)

        val halfWidth = 0.5f * width.toFloat()
        val halfHeight = 0.5f * height.toFloat()
        val transformationMatrix =
            Matrix4f()
                .setOrtho2D(-halfWidth, halfWidth, -halfHeight, halfHeight)
                .rotateZ(rotation)
        renderer.render(transformationMatrix = transformationMatrix)

        rotation += lastDeltaTimeSec * rotationSpeed

        swapBuffers()
    }

    fun startLoop() {
        val renderLoop: Runnable = object : Runnable {
            var initialTime = System.currentTimeMillis()
            override fun run() {
                val now = System.currentTimeMillis()
                lastDeltaTimeSec = (now - initialTime).toFloat() / 1_000f
                if (!this@LWJGLCanvas.isValid) {
                    GL.setCapabilities(null)
                    return
                }
                this@LWJGLCanvas.render()
                SwingUtilities.invokeLater(this)
                initialTime = now
            }
        }
        SwingUtilities.invokeLater(renderLoop)
    }

    fun dispose() {
        renderer.dispose()
    }
}