package dev.luna5ama.strepitus.glfw

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.scene.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.*
import dev.luna5ama.kmogus.MemoryStack
import dev.luna5ama.strepitus.AbstractRenderer
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap
import org.lwjgl.glfw.GLFW
import java.awt.Component
import java.awt.Cursor
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseWheelEvent
import kotlin.properties.Delegates

@OptIn(InternalComposeUiApi::class)
class GLFWWindowState() {
    val windowSize get() = windowInfo.containerSize
    val windowWidth get() = windowSize.width
    val windowHeight get() = windowSize.height

    private lateinit var scene: ComposeScene
    var windowHandle by Delegates.notNull<Long>(); private set

    val windowInfo = WindowInfoImpl()
    val platformContext = GLFWPlatformContext()

    private val resizeCallbacks = mutableListOf<(Int, Int) -> Unit>()

    fun init(windowHandle: Long, composeScene: ComposeScene, renderer: AbstractRenderer) {
        this.scene = composeScene
        this.windowHandle = windowHandle

        MemoryStack.Companion {
            val int2 = malloc(2L * 4L)
            GLFW.nglfwGetWindowSize(windowHandle, int2.ptr.address, (int2.ptr + 4L).address)
            val width = int2.ptr.getInt()
            val height = int2.ptr.getInt(4L)
            windowInfo.containerSize = IntSize(width, height)
        }

        GLFW.glfwSetMouseButtonCallback(windowHandle) { window, button, action, mods ->
            scene.sendPointerEvent(
                eventType = when (action) {
                    GLFW.GLFW_PRESS -> PointerEventType.Companion.Press
                    else -> PointerEventType.Companion.Release
                },
                position = glfwGetCursorPos(windowHandle),
                timeMillis = System.currentTimeMillis(),
                type = PointerType.Companion.Mouse,
                buttons = PointerButtons(
                    isPrimaryPressed = button == GLFW.GLFW_MOUSE_BUTTON_1 && action == GLFW.GLFW_PRESS
                            || GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS,
                    isSecondaryPressed = button == GLFW.GLFW_MOUSE_BUTTON_2 && action == GLFW.GLFW_PRESS
                            || GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_2) == GLFW.GLFW_PRESS,
                    isBackPressed = button == GLFW.GLFW_MOUSE_BUTTON_4 && action == GLFW.GLFW_PRESS
                            || GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_4) == GLFW.GLFW_PRESS,
                    isForwardPressed = button == GLFW.GLFW_MOUSE_BUTTON_5 && action == GLFW.GLFW_PRESS
                            || GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_5) == GLFW.GLFW_PRESS,
                ),
                keyboardModifiers = PointerKeyboardModifiers(
                    isCtrlPressed = mods and GLFW.GLFW_MOD_CONTROL != 0,
                    isMetaPressed = mods and GLFW.GLFW_MOD_SUPER != 0,
                    isAltPressed = mods and GLFW.GLFW_MOD_ALT != 0,
                    isShiftPressed = mods and GLFW.GLFW_MOD_SHIFT != 0,
                    isAltGraphPressed = false,
                    isSymPressed = false,
                    isFunctionPressed = false,
                    isCapsLockOn = mods and GLFW.GLFW_MOD_CAPS_LOCK != 0,
                    isScrollLockOn = false,
                    isNumLockOn = mods and GLFW.GLFW_MOD_NUM_LOCK != 0,
                ),
//            nativeEvent = MouseEvent(getAwtMods(windowHandle)),
                button = when (button) {
                    GLFW.GLFW_MOUSE_BUTTON_1 -> PointerButton.Companion.Primary
                    GLFW.GLFW_MOUSE_BUTTON_2 -> PointerButton.Companion.Secondary
                    GLFW.GLFW_MOUSE_BUTTON_3 -> PointerButton.Companion.Tertiary
                    GLFW.GLFW_MOUSE_BUTTON_4 -> PointerButton.Companion.Back
                    GLFW.GLFW_MOUSE_BUTTON_5 -> PointerButton.Companion.Forward
                    else -> null
                }
            )
            renderer.mouse.mouseButtonCallback(window, button, action, mods)
        }

        GLFW.glfwSetCursorPosCallback(windowHandle) { window, xpos, ypos ->
            scene.sendPointerEvent(
                eventType = PointerEventType.Companion.Move,
                position = Offset(xpos.toFloat(), ypos.toFloat()),
                timeMillis = System.currentTimeMillis(),
                type = PointerType.Companion.Mouse,
                buttons = PointerButtons(
                    isPrimaryPressed = GLFW.glfwGetMouseButton(
                        windowHandle,
                        GLFW.GLFW_MOUSE_BUTTON_1
                    ) == GLFW.GLFW_PRESS,
                    isSecondaryPressed = GLFW.glfwGetMouseButton(
                        windowHandle,
                        GLFW.GLFW_MOUSE_BUTTON_2
                    ) == GLFW.GLFW_PRESS,
                    isBackPressed = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_4) == GLFW.GLFW_PRESS,
                    isForwardPressed = GLFW.glfwGetMouseButton(
                        windowHandle,
                        GLFW.GLFW_MOUSE_BUTTON_5
                    ) == GLFW.GLFW_PRESS,
                ),
                keyboardModifiers = PointerKeyboardModifiers(
                    isCtrlPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS,
                    isMetaPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS,
                    isAltPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS,
                    isShiftPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS,
                    isAltGraphPressed = false,
                    isSymPressed = false,
                    isFunctionPressed = false,
                    isCapsLockOn = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_CAPS_LOCK) == GLFW.GLFW_PRESS,
                    isScrollLockOn = false,
                    isNumLockOn = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_NUM_LOCK) == GLFW.GLFW_PRESS,
                ),
//            nativeEvent = MouseEvent(getAwtMods(windowHandle),
                button = null
            )
        }

        GLFW.glfwSetCursorEnterCallback(windowHandle) { _, entered ->
            scene.sendPointerEvent(
                position = glfwGetCursorPos(windowHandle),
                eventType = if (entered) PointerEventType.Companion.Enter else PointerEventType.Companion.Exit,
                nativeEvent = MouseEvent(getAwtMods(windowHandle))
            )
        }

        GLFW.glfwSetScrollCallback(windowHandle) { _, xoffset, yoffset ->
            scene.sendPointerEvent(
                eventType = PointerEventType.Companion.Scroll,
                position = glfwGetCursorPos(windowHandle),
                scrollDelta = Offset(xoffset.toFloat(), -yoffset.toFloat()),
                nativeEvent = MouseWheelEvent(getAwtMods(windowHandle))
            )
        }

        GLFW.glfwSetKeyCallback(windowHandle) { window, key, scancode, action, mods ->
            val awtId = when (action) {
                GLFW.GLFW_PRESS, GLFW.GLFW_REPEAT -> KeyEvent.KEY_PRESSED
                else -> KeyEvent.KEY_RELEASED
            }
            val awtKey = glfwToAwtKeyCode(key)
            val time = System.nanoTime() / 1_000_000

            // Note that we don't distinguish between Left/Right Shift, Del from numpad or not, etc.
            // To distinguish we should change `location` parameter
            scene.sendKeyEvent(
                makeKeyEvent(
                    awtId,
                    time,
                    getAwtMods(windowHandle),
                    awtKey,
                    0.toChar(),
                    KeyEvent.KEY_LOCATION_STANDARD
                )
            )
            renderer.keyboard.keyCallback(window, key, scancode, action, mods)
        }

        GLFW.glfwSetCharCallback(windowHandle) { _, codepoint ->
            for (char in Character.toChars(codepoint)) {
                val time = System.nanoTime() / 1_000_000
                scene.sendKeyEvent(
                    makeKeyEvent(
                        KeyEvent.KEY_TYPED, time, getAwtMods(windowHandle), 0, char,
                        KeyEvent.KEY_LOCATION_UNKNOWN
                    )
                )
            }
        }

        GLFW.glfwSetWindowContentScaleCallback(windowHandle) { _, xscale, _ ->
            scene.density = Density(xscale)
        }

        GLFW.glfwSetWindowFocusCallback(windowHandle) { _, focused ->
            windowInfo.isWindowFocused = focused
        }

        GLFW.glfwSetWindowSizeCallback(windowHandle) { _, windowWidth, windowHeight ->
            onResize(windowWidth, windowHeight)
            resizeCallbacks.forEach {
                it(windowWidth, windowHeight)
            }
        }
    }

    fun onResize(callback: (Int, Int) -> Unit) {
        resizeCallbacks.add(callback)
    }

    private fun onResize(newWidth: Int, newHeight: Int) {
        windowInfo.containerSize = IntSize(newWidth, newHeight)
        scene.invalidatePositionInWindow()
        scene.invalidatePositionOnScreen()
    }

    private fun glfwGetCursorPos(window: Long): Offset {
        val x = DoubleArray(1)
        val y = DoubleArray(1)
        GLFW.glfwGetCursorPos(window, x, y)
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

    private fun makeKeyEvent(
        awtId: Int,
        time: Long,
        awtMods: Int,
        key: Int,
        char: Char,
        location: Int
    ): androidx.compose.ui.input.key.KeyEvent {
        val awtEvent = KeyEvent(awtComponent, awtId, time, awtMods, key, char, location)
        fun Key(nativeKeyCode: Int, nativeKeyLocation: Int = KeyEvent.KEY_LOCATION_STANDARD): Long {
            // Only 3 bits are required for nativeKeyLocation.
            return packInts(nativeKeyLocation, nativeKeyCode)
        }


        fun KeyEvent.keyLocationForCompose() =
            if (keyLocation == KeyEvent.KEY_LOCATION_UNKNOWN) KeyEvent.KEY_LOCATION_STANDARD else keyLocation

        fun getLockingKeyStateSafe(
            mask: Int
        ): Boolean = try {
            Toolkit.getDefaultToolkit().getLockingKeyState(mask)
        } catch (_: Exception) {
            false
        }

        fun KeyEvent.toPointerKeyboardModifiers(): Int {
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
                isCapsLockOn = getLockingKeyStateSafe(KeyEvent.VK_CAPS_LOCK),
                isScrollLockOn = getLockingKeyStateSafe(KeyEvent.VK_SCROLL_LOCK),
                isNumLockOn = getLockingKeyStateSafe(KeyEvent.VK_NUM_LOCK),
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
            KeyEvent.KEY_PRESSED -> 2
            KeyEvent.KEY_RELEASED -> 1
            else -> 0
        }
        val codePoint = awtEvent.keyChar.code
        val modifiers = awtEvent.toPointerKeyboardModifiers()
        return androidx.compose.ui.input.key.KeyEvent(
            nativeKeyEvent = constructor.newInstance(
                key,
                type,
                codePoint,
                modifiers,
                awtEvent
            )
        )
    }

    private fun MouseEvent(awtMods: Int) = java.awt.event.MouseEvent(
        awtComponent, 0, 0, awtMods, 0, 0, 1, false
    )

    private fun MouseWheelEvent(awtMods: Int) = java.awt.event.MouseWheelEvent(
        awtComponent, 0, 0, awtMods, 0, 0, 1, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1
    )

    private fun getAwtMods(windowHandle: Long): Int {
        var awtMods = 0
        if (GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS)
            awtMods = awtMods or InputEvent.BUTTON1_DOWN_MASK
        if (GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_2) == GLFW.GLFW_PRESS)
            awtMods = awtMods or InputEvent.BUTTON2_DOWN_MASK
        if (GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_3) == GLFW.GLFW_PRESS)
            awtMods = awtMods or InputEvent.BUTTON3_DOWN_MASK
        if (GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_4) == GLFW.GLFW_PRESS)
            awtMods = awtMods or (1 shl 14)
        if (GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_5) == GLFW.GLFW_PRESS)
            awtMods = awtMods or (1 shl 15)
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(
                windowHandle,
                GLFW.GLFW_KEY_RIGHT_CONTROL
            ) == GLFW.GLFW_PRESS
        )
            awtMods = awtMods or InputEvent.CTRL_DOWN_MASK
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(
                windowHandle,
                GLFW.GLFW_KEY_RIGHT_SHIFT
            ) == GLFW.GLFW_PRESS
        )
            awtMods = awtMods or InputEvent.SHIFT_DOWN_MASK
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(
                windowHandle,
                GLFW.GLFW_KEY_RIGHT_ALT
            ) == GLFW.GLFW_PRESS
        )
            awtMods = awtMods or InputEvent.ALT_DOWN_MASK
        return awtMods
    }

    inner class WindowInfoImpl : WindowInfo {
        private val _containerSize = mutableStateOf(IntSize.Companion.Zero)

        override var isWindowFocused: Boolean by mutableStateOf(true)

        override var containerSize: IntSize
            get() = _containerSize.value
            set(value) {
                _containerSize.value = value
            }
    }

    @OptIn(InternalComposeUiApi::class)
    inner class GLFWPlatformContext : PlatformContext by PlatformContext.Companion.Empty {
        override val windowInfo: WindowInfo
            get() = this@GLFWWindowState.windowInfo

        private val awtCursorClass = Class.forName("androidx.compose.ui.input.pointer.AwtCursor")
        private val cursorField = awtCursorClass.getDeclaredField("cursor").apply { isAccessible = true }

        private val glfwCursors: Int2LongOpenHashMap

        init {
            val arrow = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
            val ibeam = GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR)
            val crosshair = GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR)
            val pointingHand = GLFW.glfwCreateStandardCursor(GLFW.GLFW_POINTING_HAND_CURSOR)
            val resizeEW = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR)
            val resizeNS = GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR)
            val resizeNWSE = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR)
            val resizeNESW = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NESW_CURSOR)
            val resizeAll = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_ALL_CURSOR)
            val notAllowed = GLFW.glfwCreateStandardCursor(GLFW.GLFW_NOT_ALLOWED_CURSOR)

            glfwCursors = Int2LongOpenHashMap(
                mapOf(
                    Cursor.DEFAULT_CURSOR to arrow,
                    Cursor.CROSSHAIR_CURSOR to crosshair,
                    Cursor.TEXT_CURSOR to ibeam,
                    Cursor.WAIT_CURSOR to notAllowed,
                    Cursor.SW_RESIZE_CURSOR to resizeNESW,
                    Cursor.SE_RESIZE_CURSOR to resizeNWSE,
                    Cursor.NW_RESIZE_CURSOR to resizeNWSE,
                    Cursor.NE_RESIZE_CURSOR to resizeNESW,
                    Cursor.N_RESIZE_CURSOR to resizeNS,
                    Cursor.S_RESIZE_CURSOR to resizeNS,
                    Cursor.W_RESIZE_CURSOR to resizeEW,
                    Cursor.E_RESIZE_CURSOR to resizeEW,
                    Cursor.HAND_CURSOR to pointingHand,
                    Cursor.MOVE_CURSOR to resizeAll
                )
            ).apply {
                defaultReturnValue(arrow)
            }
        }

        override fun setPointerIcon(pointerIcon: PointerIcon) {
            var awtCursor = Cursor.DEFAULT_CURSOR
            if (pointerIcon.javaClass.isAssignableFrom(awtCursorClass)) {
                val cursorObj = cursorField.get(pointerIcon)
                if (cursorObj is Cursor) {
                    awtCursor = cursorObj.type
                }
            }
            val glfwCursor = glfwCursors.get(awtCursor)
            GLFW.glfwSetCursor(windowHandle, glfwCursor)
        }
    }
}