package dev.luna5ama.strepitus

import dev.luna5ama.strepitus.gl.Basic
import dev.luna5ama.strepitus.gl.IGLObjContainer
import dev.luna5ama.strepitus.gl.SamplerManager
import dev.luna5ama.strepitus.gl.register
import dev.luna5ama.strepitus.glfw.Keyboard
import dev.luna5ama.strepitus.glfw.Mouse

abstract class AbstractRenderer : IGLObjContainer by IGLObjContainer.Impl() {
    val keyboard = Keyboard()
    val mouse = Mouse()
    val samplerManager = register(SamplerManager())
    val basic = register(Basic())

    abstract fun draw()
}