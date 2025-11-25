package dev.luna5ama.strepitus.glfw

import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.scene.*
import androidx.compose.ui.util.*
import dev.luna5ama.strepitus.AbstractRenderer
import org.lwjgl.glfw.GLFW.*
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.KeyEvent.*
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.KeyEvent as AwtKeyEvent

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
fun ComposeScene.subscribeToGLFWEvents(windowHandle: Long, renderer: AbstractRenderer) {
    glfwSetMouseButtonCallback(windowHandle) { window, button, action, mods ->
        sendPointerEvent(
            eventType = when (action) {
                GLFW_PRESS -> PointerEventType.Press
                else -> PointerEventType.Release
            },
            position = glfwGetCursorPos(windowHandle),
            timeMillis = System.currentTimeMillis(),
            type = PointerType.Mouse,
            buttons = PointerButtons(
                isPrimaryPressed = button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS
                        || glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS,
                isSecondaryPressed = button == GLFW_MOUSE_BUTTON_2 && action == GLFW_PRESS
                        || glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_2) == GLFW_PRESS,
                isBackPressed = button == GLFW_MOUSE_BUTTON_4 && action == GLFW_PRESS
                        || glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_4) == GLFW_PRESS,
                isForwardPressed = button == GLFW_MOUSE_BUTTON_5 && action == GLFW_PRESS
                        || glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_5) == GLFW_PRESS,
            ),
            keyboardModifiers = PointerKeyboardModifiers(
                isCtrlPressed = mods and GLFW_MOD_CONTROL != 0,
                isMetaPressed = mods and GLFW_MOD_SUPER != 0,
                isAltPressed = mods and GLFW_MOD_ALT != 0,
                isShiftPressed = mods and GLFW_MOD_SHIFT != 0,
                isAltGraphPressed = false,
                isSymPressed = false,
                isFunctionPressed = false,
                isCapsLockOn = mods and GLFW_MOD_CAPS_LOCK != 0,
                isScrollLockOn = false,
                isNumLockOn = mods and GLFW_MOD_NUM_LOCK != 0,
            ),
//            nativeEvent = MouseEvent(getAwtMods(windowHandle)),
            button = when (button) {
                GLFW_MOUSE_BUTTON_1 -> PointerButton.Primary
                GLFW_MOUSE_BUTTON_2 -> PointerButton.Secondary
                GLFW_MOUSE_BUTTON_3 -> PointerButton.Tertiary
                GLFW_MOUSE_BUTTON_4 -> PointerButton.Back
                GLFW_MOUSE_BUTTON_5 -> PointerButton.Forward
                else -> null
            }
        )
        renderer.mouse.mouseButtonCallback(window, button, action, mods)
    }

    glfwSetCursorPosCallback(windowHandle) { _, xpos, ypos ->
        sendPointerEvent(
            eventType = PointerEventType.Move,
            position = Offset(xpos.toFloat(), ypos.toFloat()),
            timeMillis = System.currentTimeMillis(),
            type = PointerType.Mouse,
            buttons = PointerButtons(
                isPrimaryPressed = glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS,
                isSecondaryPressed = glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_2) == GLFW_PRESS,
                isBackPressed = glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_4) == GLFW_PRESS,
                isForwardPressed = glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_5) == GLFW_PRESS,
            ),
            keyboardModifiers = PointerKeyboardModifiers(
                isCtrlPressed = glfwGetKey(windowHandle, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS
                        || glfwGetKey(windowHandle, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS,
                isMetaPressed = glfwGetKey(windowHandle, GLFW_KEY_LEFT_SUPER) == GLFW_PRESS
                        || glfwGetKey(windowHandle, GLFW_KEY_RIGHT_SUPER) == GLFW_PRESS,
                isAltPressed = glfwGetKey(windowHandle, GLFW_KEY_LEFT_ALT) == GLFW_PRESS
                        || glfwGetKey(windowHandle, GLFW_KEY_RIGHT_ALT) == GLFW_PRESS,
                isShiftPressed = glfwGetKey(windowHandle, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS
                        || glfwGetKey(windowHandle, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS,
                isAltGraphPressed = false,
                isSymPressed = false,
                isFunctionPressed = false,
                isCapsLockOn = glfwGetKey(windowHandle, GLFW_KEY_CAPS_LOCK) == GLFW_PRESS,
                isScrollLockOn = false,
                isNumLockOn = glfwGetKey(windowHandle, GLFW_KEY_NUM_LOCK) == GLFW_PRESS,
            ),
//            nativeEvent = MouseEvent(getAwtMods(windowHandle),
            button = null
        )
    }

    glfwSetCursorEnterCallback(windowHandle) { _, entered ->
        sendPointerEvent(
            position = glfwGetCursorPos(windowHandle),
            eventType = if (entered) PointerEventType.Enter else PointerEventType.Exit,
            nativeEvent = MouseEvent(getAwtMods(windowHandle))
        )
    }

    glfwSetScrollCallback(windowHandle) { _, xoffset, yoffset ->
        sendPointerEvent(
            eventType = PointerEventType.Scroll,
            position = glfwGetCursorPos(windowHandle),
            scrollDelta = Offset(xoffset.toFloat(), -yoffset.toFloat()),
            nativeEvent = MouseWheelEvent(getAwtMods(windowHandle))
        )
    }

    glfwSetKeyCallback(windowHandle) { window, key, scancode, action, mods ->
        val awtId = when (action) {
            GLFW_PRESS, GLFW_REPEAT -> KEY_PRESSED
            else -> KEY_RELEASED
        }
        val awtKey = glfwToAwtKeyCode(key)
        val time = System.nanoTime() / 1_000_000

        // Note that we don't distinguish between Left/Right Shift, Del from numpad or not, etc.
        // To distinguish we should change `location` parameter
        sendKeyEvent(makeKeyEvent(awtId, time, getAwtMods(windowHandle), awtKey, 0.toChar(), KEY_LOCATION_STANDARD))
        renderer.keyboard.keyCallback(window, key, scancode, action, mods)
    }

    glfwSetCharCallback(windowHandle) { _, codepoint ->
        for (char in Character.toChars(codepoint)) {
            val time = System.nanoTime() / 1_000_000
            sendKeyEvent(
                makeKeyEvent(
                    KEY_TYPED, time, getAwtMods(windowHandle), 0, char,
                    KEY_LOCATION_UNKNOWN
                )
            )
        }
    }

    glfwSetWindowContentScaleCallback(windowHandle) { _, xscale, _ ->
//        density = Density(xscale)
    }
}

private fun glfwGetCursorPos(window: Long): Offset {
    val x = DoubleArray(1)
    val y = DoubleArray(1)
    glfwGetCursorPos(window, x, y)
    return Offset(x[0].toFloat(), y[0].toFloat())
}

// in the future versions of Compose we plan to get rid of the need of AWT events/components
val awtComponent = object : Component() {}

private val clazz = Class.forName("androidx.compose.ui.input.key.InternalKeyEvent")
private val constructor = clazz.getDeclaredConstructor(
    Long::class.java,
    Int::class.java,
    Int::class.java,
    Int::class.java,
    Any::class.java,
).apply { isAccessible = true }

private fun makeKeyEvent(awtId: Int, time: Long, awtMods: Int, key: Int, char: Char, location: Int): KeyEvent {
    val awtEvent = AwtKeyEvent(awtComponent, awtId, time, awtMods, key, char, location)
    fun Key(nativeKeyCode: Int, nativeKeyLocation: Int = KEY_LOCATION_STANDARD): Long {
        // Only 3 bits are required for nativeKeyLocation.
        return packInts(nativeKeyLocation, nativeKeyCode)
    }


    fun AwtKeyEvent.keyLocationForCompose() =
        if (keyLocation == KEY_LOCATION_UNKNOWN) KEY_LOCATION_STANDARD else keyLocation

    fun getLockingKeyStateSafe(
        mask: Int
    ): Boolean = try {
        Toolkit.getDefaultToolkit().getLockingKeyState(mask)
    } catch (_: Exception) {
        false
    }

    fun AwtKeyEvent.toPointerKeyboardModifiers(): Int {
        fun PointerKeyboardModifiers(
            isCtrlPressed: Boolean = false,
            isMetaPressed: Boolean = false,
            isAltPressed: Boolean = false,
            isShiftPressed: Boolean = false,
            isAltGraphPressed: Boolean = false,
            isSymPressed: Boolean = false,
            isFunctionPressed: Boolean = false,
            isCapsLockOn: Boolean = false,
            isScrollLockOn: Boolean = false,
            isNumLockOn: Boolean = false,
        ): Int {
            val CtrlPressed = 1 shl 0
            val MetaPressed = 1 shl 1
            val AltPressed = 1 shl 2
            val AltGraphPressed = 1 shl 3
            val SymPressed = 1 shl 4
            val ShiftPressed = 1 shl 5
            val FunctionPressed = 1 shl 6
            val CapsLockOn = 1 shl 7
            val ScrollLockOn = 1 shl 8
            val NumLockOn = 1 shl 9

            var res = 0
            if (isCtrlPressed) res = res or CtrlPressed
            if (isMetaPressed) res = res or MetaPressed
            if (isAltPressed) res = res or AltPressed
            if (isShiftPressed) res = res or ShiftPressed
            if (isAltGraphPressed) res = res or AltGraphPressed
            if (isSymPressed) res = res or SymPressed
            if (isFunctionPressed) res = res or FunctionPressed
            if (isCapsLockOn) res = res or CapsLockOn
            if (isScrollLockOn) res = res or ScrollLockOn
            if (isNumLockOn) res = res or NumLockOn
            return res
        }

        return PointerKeyboardModifiers(
            isCtrlPressed = isControlDown,
            isMetaPressed = isMetaDown,
            isAltPressed = isAltDown,
            isShiftPressed = isShiftDown,
            isAltGraphPressed = isAltGraphDown,
            isSymPressed = false, // no sym in awtEvent?
            isFunctionPressed = false, // no Fn in awtEvent?
            isCapsLockOn = getLockingKeyStateSafe(VK_CAPS_LOCK),
            isScrollLockOn = getLockingKeyStateSafe(VK_SCROLL_LOCK),
            isNumLockOn = getLockingKeyStateSafe(VK_NUM_LOCK),
        )
    }

    val key = Key(
        nativeKeyCode = awtEvent.keyCode,
        nativeKeyLocation = awtEvent.keyLocationForCompose()
    )
//    val type = when (awtEvent.id) {
//        KEY_PRESSED -> KeyEventType.KeyDown
//        KEY_RELEASED -> KeyEventType.KeyUp
//        else -> KeyEventType.Unknown
//    }
    val type = when (awtEvent.id) {
        KEY_PRESSED -> 2
        KEY_RELEASED -> 1
        else -> 0
    }
    val codePoint = awtEvent.keyChar.code
    val modifiers = awtEvent.toPointerKeyboardModifiers()
    return KeyEvent(
        nativeKeyEvent = constructor.newInstance(
            key,
            type,
            codePoint,
            modifiers,
            awtEvent
        )
    )
}

private fun MouseEvent(awtMods: Int) = MouseEvent(
    awtComponent, 0, 0, awtMods, 0, 0, 1, false
)

private fun MouseWheelEvent(awtMods: Int) = MouseWheelEvent(
    awtComponent, 0, 0, awtMods, 0, 0, 1, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1
)

private fun getAwtMods(windowHandle: Long): Int {
    var awtMods = 0
    if (glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS)
        awtMods = awtMods or BUTTON1_DOWN_MASK
    if (glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_2) == GLFW_PRESS)
        awtMods = awtMods or BUTTON2_DOWN_MASK
    if (glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_3) == GLFW_PRESS)
        awtMods = awtMods or BUTTON3_DOWN_MASK
    if (glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_4) == GLFW_PRESS)
        awtMods = awtMods or (1 shl 14)
    if (glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_5) == GLFW_PRESS)
        awtMods = awtMods or (1 shl 15)
    if (glfwGetKey(windowHandle, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS || glfwGetKey(
            windowHandle,
            GLFW_KEY_RIGHT_CONTROL
        ) == GLFW_PRESS
    )
        awtMods = awtMods or CTRL_DOWN_MASK
    if (glfwGetKey(windowHandle, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS || glfwGetKey(
            windowHandle,
            GLFW_KEY_RIGHT_SHIFT
        ) == GLFW_PRESS
    )
        awtMods = awtMods or SHIFT_DOWN_MASK
    if (glfwGetKey(windowHandle, GLFW_KEY_LEFT_ALT) == GLFW_PRESS || glfwGetKey(
            windowHandle,
            GLFW_KEY_RIGHT_ALT
        ) == GLFW_PRESS
    )
        awtMods = awtMods or ALT_DOWN_MASK
    return awtMods
}