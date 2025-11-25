package dev.luna5ama.strepitus.glfw

import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWKeyCallbackI
import org.lwjgl.glfw.GLFWMouseButtonCallbackI
import java.util.function.IntConsumer

class Mouse {
    val keyState = IntArray(GLFW_MOUSE_BUTTON_LAST + 1)
    val mouseButtonCallback = GLFWMouseButtonCallbackI { window, button, action, mods ->
        if (button >= 0 && button <= GLFW_MOUSE_BUTTON_LAST) {
            keyState[button] = action
            registered[button]?.forEach { it.accept(action) }
        }
    }

    fun isPressed(button: Int) = keyState[button] == GLFW_PRESS

    private val registered = mutableMapOf<Int, MutableList<IntConsumer>>()

    fun register(button: Int, action: IntConsumer) {
        if (button in registered) {
            registered[button]!!.add(action)
        } else {
            registered[button] = mutableListOf(action)
        }
    }
}

class Keyboard {
    val keyState = IntArray(GLFW_KEY_LAST + 1)
    val keyCallback = GLFWKeyCallbackI { window, key, scancode, action, mods ->
        if (key in keyState.indices) {
            keyState[key] = action
        }

        registered[key]?.forEach { it.accept(action) }
    }

    private val registered = mutableMapOf<Int, MutableList<IntConsumer>>()

    val ctrlPressed get() = pressing(GLFW_KEY_LEFT_CONTROL) || pressing(GLFW_KEY_RIGHT_CONTROL)
    val shiftPressed get() = pressing(GLFW_KEY_LEFT_SHIFT) || pressing(GLFW_KEY_RIGHT_SHIFT)
    val altPressed get() = pressing(GLFW_KEY_LEFT_ALT) || pressing(GLFW_KEY_RIGHT_ALT)
    val superPressed get() = pressing(GLFW_KEY_LEFT_SUPER) || pressing(GLFW_KEY_RIGHT_SUPER)

    fun register(button: Int, action: IntConsumer) {
        if (button in registered) {
            registered[button]!!.add(action)
        } else {
            registered[button] = mutableListOf(action)
        }
    }

    fun press(key: Int) = keyState[key] == GLFW_PRESS

    fun release(key: Int) = keyState[key] == GLFW_RELEASE

    fun pressing(key: Int) = keyState[key] == GLFW_PRESS || keyState[key] == GLFW_REPEAT
}