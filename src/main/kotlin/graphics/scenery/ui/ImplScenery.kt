package graphics.scenery.ui

import imgui.ImGui.io
import glm_.bool
import glm_.c
import glm_.f
import glm_.vec2.Vec2
import glm_.vec2.Vec2d
import glm_.vec2.Vec2i
import graphics.scenery.Hub
import graphics.scenery.SceneryElement
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.controls.InputHandler
import graphics.scenery.utils.lazyLogger
import imgui.*
import imgui.ImGui.mainViewport
import imgui.ImGui.mouseCursor
import imgui.Key
import imgui.MouseButton
import imgui.windowsIme.imeListener
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWGamepadState
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Platform
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.ui.behaviour.ScrollBehaviour
import uno.glfw.*
import uno.glfw.GlfwWindow.CursorMode
import kotlin.concurrent.thread

class ImplScenery(val window: SceneryWindow, val hub: Hub, val installCallbacks: Boolean = true, val vrTexSize: Vec2i? = null) {
    val logger by lazyLogger()
    init {
        with(io) {
            assert(backendPlatformUserData == null) { "Already initialized a platform backend!" }
            //printf("GLFW_VERSION: %d.%d.%d (%d)", GLFW_VERSION_MAJOR, GLFW_VERSION_MINOR, GLFW_VERSION_REVISION, GLFW_VERSION_COMBINED);

            // Setup backend capabilities flags
            backendPlatformUserData = data
            backendPlatformName = "imgui_impl_glfw"
            backendFlags /= BackendFlag.HasMouseCursors   // We can honor GetMouseCursor() values (optional)
            backendFlags /= BackendFlag.HasSetMousePos    // We can honor io.WantSetMousePos requests (optional, rarely used)

            data.window = window
            //            Data.time = 0.0

            backendRendererName = null
            backendPlatformName = null
            backendLanguageUserData = null
            backendRendererUserData = null
            backendPlatformUserData = null
            setClipboardTextFn = { _, text -> logger.info("Set clipboard text to $text") }
            getClipboardTextFn = { logger.info("Gotten clipboard text"); "roflcopter goes soisoisoi" }
//            clipboardUserData = window.handle

            // Set platform dependent data in viewport
//            if (Platform.get() == Platform.WINDOWS)
//                mainViewport.platformHandleRaw = window.hwnd

            if(installCallbacks) {
                thread {
                    // These now use scenery's input system.
                    // Two options here how to streamline this:
                    // 1. remove/add the behaviour's bindings on-demand
                    // 2. use ui-behaviour's Contexts. I'd have to ask Tobi or Curtis how to do that
                    var h = hub.get<InputHandler>()
                    while(h == null) {
                        h = hub.get<InputHandler>()
                        Thread.sleep(50)
                    }
                    val inputHandler = h
                    inputHandler.addBehaviour("imgui_mouse_down", ClickBehaviour { mouseX, mouseY ->
                        io.addMousePosEvent(mouseX.toFloat(), mouseY.toFloat())
                        io.addMouseButtonEvent(MouseButton.Left, true)
                        Thread.sleep(2)
                        io.addMouseButtonEvent(MouseButton.Left, false)
                    })
                    inputHandler.addBehaviour(
                        "imgui_scroll",
                        ScrollBehaviour { wheelRotation, isHorizontal, mouseX, mouseY ->
                            val multiplier = 0.01f
                            if(isHorizontal) {
                                io.addMouseWheelEvent(multiplier * wheelRotation.toFloat(), 0.0f)
                            } else {
                                io.addMouseWheelEvent(0.0f, multiplier * wheelRotation.toFloat())
                            }
                        })
                    inputHandler.addKeyBinding("imgui_scroll", "scroll")
                    inputHandler.addKeyBinding("imgui_mouse_down", "button1")

                    logger.info("Callbacks installed")
                }
            }
        }
    }

    fun newFrame() {
        assert(data != null) { "Did you call ImGui_ImplGlfw_InitForXXX()?" }

        // Setup display size (every frame to accommodate for window resizing)
        val size = Vec2i(window.width, window.height)
        val displaySize = size//window.framebufferSize
        io.displaySize put (vrTexSize ?: size)
        if (size allGreaterThan 0)
            io.displayFramebufferScale put (displaySize / size)

        // Setup time step
        val currentTime = System.nanoTime().toDouble()
        io.deltaTime = if (data.time > 0) (currentTime - data.time).f else 1f / 60f
        data.time = currentTime

        updateMouseData()
        updateMouseCursor()

        // Update game controllers (if enabled and available)
        updateGamepads()
    }

    fun updateMouseData() {
        val inputHandler = hub.get(SceneryElement.Input) as InputHandler
        val x = (inputHandler.handler?.mouseX ?: 0).toFloat()
        val y = (inputHandler.handler?.mouseY ?: 0).toFloat()
        data.lastValidMousePos.put(x, y)
        io.addMousePosEvent(x, y)
    }

    fun updateMouseCursor() {
        io.mouseDrawCursor = true
    }

    fun updateGamepads() {}

    fun shutdown() {

    }

    companion object {

        lateinit var instance: ImplScenery

        fun init(window: SceneryWindow, hub: Hub, installCallbacks: Boolean = true, vrTexSize: Vec2i? = null) {
            instance = ImplScenery(window, hub, installCallbacks, vrTexSize)
        }

        fun newFrame() = instance.newFrame()
        fun shutdown() = instance.shutdown()

        // X11 does not include current pressed/released modifier key in 'mods' flags submitted by GLFW
        // See https://github.com/ocornut/imgui/issues/6034 and https://github.com/glfw/glfw/issues/1630

        fun updateKeyModifiers() {
//            val wnd = data.window.handle
//            io.addKeyEvent(Key.Mod_Ctrl, (glfwGetKey(wnd, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS) || (glfwGetKey(wnd, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS))
//            io.addKeyEvent(Key.Mod_Shift, (glfwGetKey(wnd, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) || (glfwGetKey(wnd, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS))
//            io.addKeyEvent(Key.Mod_Alt, (glfwGetKey(wnd, GLFW_KEY_LEFT_ALT) == GLFW_PRESS) || (glfwGetKey(wnd, GLFW_KEY_RIGHT_ALT) == GLFW_PRESS))
//            io.addKeyEvent(Key.Mod_Super, (glfwGetKey(wnd, GLFW_KEY_LEFT_SUPER) == GLFW_PRESS) || (glfwGetKey(wnd, GLFW_KEY_RIGHT_SUPER) == GLFW_PRESS))
        }



        object data {
            lateinit var window: SceneryWindow
            var time = 0.0
            var mouseWindow: GlfwWindow? = null
            val mouseCursors = LongArray/*<GlfwCursor>*/(MouseCursor.COUNT)
            val lastValidMousePos = Vec2()
            var installedCallbacks = false

            // Chain GLFW callbacks: our callbacks will call the user's previously installed callbacks, if any.
            //            GLFWmousebuttonfun      PrevUserCallbackMousebutton;
            //            GLFWscrollfun           PrevUserCallbackScroll;
            //            GLFWkeyfun              PrevUserCallbackKey;
            //            GLFWcharfun             PrevUserCallbackChar;
        }

        val uno.glfw.Key.imguiKey: Key
            get() = when (this) {
                uno.glfw.Key.TAB -> Key.Tab
                uno.glfw.Key.LEFT -> Key.LeftArrow
                uno.glfw.Key.RIGHT -> Key.RightArrow
                uno.glfw.Key.UP -> Key.UpArrow
                uno.glfw.Key.DOWN -> Key.DownArrow
                uno.glfw.Key.PAGE_UP -> Key.PageUp
                uno.glfw.Key.PAGE_DOWN -> Key.PageDown
                uno.glfw.Key.HOME -> Key.Home
                uno.glfw.Key.END -> Key.End
                uno.glfw.Key.INSERT -> Key.Insert
                uno.glfw.Key.DELETE -> Key.Delete
                uno.glfw.Key.BACKSPACE -> Key.Backspace
                uno.glfw.Key.SPACE -> Key.Space
                uno.glfw.Key.ENTER -> Key.Enter
                uno.glfw.Key.ESCAPE -> Key.Escape
                uno.glfw.Key.APOSTROPHE -> Key.Apostrophe
                uno.glfw.Key.COMMA -> Key.Comma
                uno.glfw.Key.MINUS -> Key.Minus
                uno.glfw.Key.PERIOD -> Key.Period
                uno.glfw.Key.SLASH -> Key.Slash
                uno.glfw.Key.SEMICOLON -> Key.Semicolon
                uno.glfw.Key.EQUAL -> Key.Equal
                uno.glfw.Key.LEFT_BRACKET -> Key.LeftBracket
                uno.glfw.Key.BACKSLASH -> Key.Backslash
                uno.glfw.Key.RIGHT_BRACKET -> Key.RightBracket
                uno.glfw.Key.GRAVE_ACCENT -> Key.GraveAccent
                uno.glfw.Key.CAPS_LOCK -> Key.CapsLock
                uno.glfw.Key.SCROLL_LOCK -> Key.ScrollLock
                uno.glfw.Key.NUM_LOCK -> Key.NumLock
                uno.glfw.Key.PRINT_SCREEN -> Key.PrintScreen
                uno.glfw.Key.PAUSE -> Key.Pause
                uno.glfw.Key.KP_0 -> Key.Keypad0
                uno.glfw.Key.KP_1 -> Key.Keypad1
                uno.glfw.Key.KP_2 -> Key.Keypad2
                uno.glfw.Key.KP_3 -> Key.Keypad3
                uno.glfw.Key.KP_4 -> Key.Keypad4
                uno.glfw.Key.KP_5 -> Key.Keypad5
                uno.glfw.Key.KP_6 -> Key.Keypad6
                uno.glfw.Key.KP_7 -> Key.Keypad7
                uno.glfw.Key.KP_8 -> Key.Keypad8
                uno.glfw.Key.KP_9 -> Key.Keypad9
                uno.glfw.Key.KP_DECIMAL -> Key.KeypadDecimal
                uno.glfw.Key.KP_DIVIDE -> Key.KeypadDivide
                uno.glfw.Key.KP_MULTIPLY -> Key.KeypadMultiply
                uno.glfw.Key.KP_SUBTRACT -> Key.KeypadSubtract
                uno.glfw.Key.KP_ADD -> Key.KeypadAdd
                uno.glfw.Key.KP_ENTER -> Key.KeypadEnter
                uno.glfw.Key.KP_EQUAL -> Key.KeypadEqual
                uno.glfw.Key.LEFT_SHIFT -> Key.LeftShift
                uno.glfw.Key.LEFT_CONTROL -> Key.LeftCtrl
                uno.glfw.Key.LEFT_ALT -> Key.LeftAlt
                uno.glfw.Key.LEFT_SUPER -> Key.LeftSuper
                uno.glfw.Key.RIGHT_SHIFT -> Key.RightShift
                uno.glfw.Key.RIGHT_CONTROL -> Key.RightCtrl
                uno.glfw.Key.RIGHT_ALT -> Key.RightAlt
                uno.glfw.Key.RIGHT_SUPER -> Key.RightSuper
                uno.glfw.Key.MENU -> Key.Menu
//                uno.glfw.Key.`0` -> Key.`0`
//                uno.glfw.Key.`1` -> Key.`1`
//                uno.glfw.Key.`2` -> Key.`2`
//                uno.glfw.Key.`3` -> Key.`3`
//                uno.glfw.Key.`4` -> Key.`4`
//                uno.glfw.Key.`5` -> Key.`5`
//                uno.glfw.Key.`6` -> Key.`6`
//                uno.glfw.Key.`7` -> Key.`7`
//                uno.glfw.Key.`8` -> Key.`8`
//                uno.glfw.Key.`9` -> Key.`9`
                uno.glfw.Key.A -> Key.A
                uno.glfw.Key.B -> Key.B
                uno.glfw.Key.C -> Key.C
                uno.glfw.Key.D -> Key.D
                uno.glfw.Key.E -> Key.E
                uno.glfw.Key.F -> Key.F
                uno.glfw.Key.G -> Key.G
                uno.glfw.Key.H -> Key.H
                uno.glfw.Key.I -> Key.I
                uno.glfw.Key.J -> Key.J
                uno.glfw.Key.K -> Key.K
                uno.glfw.Key.L -> Key.L
                uno.glfw.Key.M -> Key.M
                uno.glfw.Key.N -> Key.N
                uno.glfw.Key.O -> Key.O
                uno.glfw.Key.P -> Key.P
                uno.glfw.Key.Q -> Key.Q
                uno.glfw.Key.R -> Key.R
                uno.glfw.Key.S -> Key.S
                uno.glfw.Key.T -> Key.T
                uno.glfw.Key.U -> Key.U
                uno.glfw.Key.V -> Key.V
                uno.glfw.Key.W -> Key.W
                uno.glfw.Key.X -> Key.X
                uno.glfw.Key.Y -> Key.Y
                uno.glfw.Key.Z -> Key.Z
                uno.glfw.Key.F1 -> Key.F1
                uno.glfw.Key.F2 -> Key.F2
                uno.glfw.Key.F3 -> Key.F3
                uno.glfw.Key.F4 -> Key.F4
                uno.glfw.Key.F5 -> Key.F5
                uno.glfw.Key.F6 -> Key.F6
                uno.glfw.Key.F7 -> Key.F7
                uno.glfw.Key.F8 -> Key.F8
                uno.glfw.Key.F9 -> Key.F9
                uno.glfw.Key.F10 -> Key.F10
                uno.glfw.Key.F11 -> Key.F11
                uno.glfw.Key.F12 -> Key.F12
                else -> Key.None
            }
    }
}