package graphics.scenery.controls

import graphics.scenery.Hub
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.utils.ExtractsNatives
import graphics.scenery.utils.extensions.toBinaryString
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWCursorPosCallback
import org.lwjgl.glfw.GLFWKeyCallback
import org.lwjgl.glfw.GLFWMouseButtonCallback
import org.lwjgl.glfw.GLFWScrollCallback
import org.scijava.ui.behaviour.BehaviourMap
import org.scijava.ui.behaviour.InputTrigger
import org.scijava.ui.behaviour.InputTriggerMap
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

/**
 * Input handling class for GLFW-based windows.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
@CanHandleInputFor([SceneryWindow.GLFWWindow::class])
open class GLFWMouseAndKeyHandler(var hub: Hub?) : MouseAndKeyHandlerBase(), AutoCloseable, ExtractsNatives {
    /** store os name */
    private var os = ""

    /** scroll speed multiplier to combat OS idiosyncrasies */
    private var scrollSpeedMultiplier = 1.0f

    var cursorCallback = object : GLFWCursorPosCallback() {
        override fun invoke(window: Long, xpos: Double, ypos: Double) {
            mouseMoved(MouseEvent(
                fakeComponent,
                MouseEvent.MOUSE_MOVED,
                System.nanoTime(),
                0,
                xpos.toInt(),
                ypos.toInt(),
                0, false,
                0))
        }
    }

    private fun Int.glfwToSwingMods(buttonKey: Int): Int {
        var mask = 0

        if(this and GLFW_MOD_ALT != 0) {
            mask = mask or KeyEvent.ALT_DOWN_MASK
        }
        if(this and GLFW_MOD_CONTROL != 0) {
            mask = mask or KeyEvent.CTRL_DOWN_MASK
        }
        if(this and GLFW_MOD_SHIFT != 0) {
            mask = mask or KeyEvent.SHIFT_DOWN_MASK
        }
        if(this and GLFW_MOD_SUPER != 0) {
            mask = mask or KeyEvent.META_DOWN_MASK
        }
        if(buttonKey == GLFW_MOUSE_BUTTON_LEFT) {
            mask = mask or InputEvent.BUTTON1_DOWN_MASK
        }
        if(buttonKey == GLFW_MOUSE_BUTTON_RIGHT) {
            mask = mask or InputEvent.BUTTON2_DOWN_MASK
        }
        if(buttonKey == GLFW_MOUSE_BUTTON_MIDDLE) {
            mask = mask or InputEvent.BUTTON3_DOWN_MASK
        }

        return mask
    }


    /**
     * Return true for modiefiers keys acorrding to https://www.glfw.org/docs/3.3/group__keys.html
     */
    private fun Int.isGLFWModifierKey(): Boolean {
        val modKeys = listOf(GLFW_KEY_LEFT_SHIFT,
            GLFW_KEY_LEFT_CONTROL,
            GLFW_KEY_LEFT_ALT,
            GLFW_KEY_LEFT_SUPER,
            GLFW_KEY_RIGHT_SHIFT,
            GLFW_KEY_RIGHT_CONTROL,
            GLFW_KEY_RIGHT_ALT,
            GLFW_KEY_RIGHT_SUPER)
        return modKeys.contains(this)
    }

    var keyCallback = object : GLFWKeyCallback() {
        override fun invoke(window: Long, key: Int, scancode: Int, action: Int, mods: Int) {
            if (key.isGLFWModifierKey()){
                return
            }

            val type = when(action) {
                GLFW_PRESS -> KeyEvent.KEY_PRESSED
                GLFW_RELEASE -> KeyEvent.KEY_RELEASED
                GLFW_REPEAT -> KeyEvent.KEY_PRESSED
                else -> KeyEvent.KEY_PRESSED
            }

            // Fix cursor key mapping
            val mappedKey = when(key) {
                GLFW_KEY_UP -> KeyEvent.VK_UP
                GLFW_KEY_DOWN -> KeyEvent.VK_DOWN
                GLFW_KEY_LEFT -> KeyEvent.VK_LEFT
                GLFW_KEY_RIGHT -> KeyEvent.VK_RIGHT
                else -> key
            }

            val event = KeyEvent(
                fakeComponent,
                type,
                System.nanoTime(),
                mods.glfwToSwingMods(key),
                mappedKey,
                KeyEvent.CHAR_UNDEFINED
            )

            when (action) {
                GLFW_PRESS -> keyPressed(event)
                GLFW_REPEAT -> keyPressed(event)
                GLFW_RELEASE -> keyReleased(event)
            }
        }

    }

    private var clickBefore = System.nanoTime()
    var mouseCallback = object : GLFWMouseButtonCallback() {
        override fun invoke(window: Long, key: Int, action: Int, mods: Int) {
            val type = when (action) {
                GLFW_PRESS -> MouseEvent.MOUSE_PRESSED
                GLFW_RELEASE -> MouseEvent.MOUSE_RELEASED
                else -> MouseEvent.MOUSE_CLICKED
            }

            var clickCount = 1

            if(action == GLFW_PRESS) {
                val now = System.nanoTime()
                val diff = (now - clickBefore) / 10e5

                if (diff > 10 && diff < getDoubleClickInterval()) {
                    clickCount = 2
                }

                clickBefore = now
            }

            val event = MouseEvent(
                fakeComponent,
                type,
                System.nanoTime(),
                mods.glfwToSwingMods(key),
                mouseX,
                mouseY,
                clickCount,
                false,
                key
            )


            when (action) {
                GLFW_PRESS -> { mousePressed(event); }
                GLFW_RELEASE -> { mouseReleased(event); }
            }
        }
    }

    var scrollCallback = object : GLFWScrollCallback() {
        override fun invoke(window: Long, xoffset: Double, yoffset: Double) {
            mouseWheelMoved(MouseWheelEvent(
                fakeComponent,
                MouseWheelEvent.MOUSE_WHEEL,
                System.nanoTime(),
                0,
                0,
                0,
                0, false, 0,
                (yoffset.toFloat()*-scrollSpeedMultiplier).toInt(),
                (yoffset.toFloat()*-scrollSpeedMultiplier).toInt())
            )
        }

    }

    init {
        os = if(System.getProperty("os.name").lowercase().indexOf("windows") != -1) {
            "windows"
        } else if(System.getProperty("os.name").lowercase().indexOf("mac") != -1) {
            "mac"
        } else if(System.getProperty("os.name").lowercase().indexOf("linux") != -1) {
            "linux"
        } else {
            "unknown"
        }

        scrollSpeedMultiplier = if(os == "mac") {
            1.0f
        } else {
            10.0f
        }
    }

    /**
     * Returns the key mask of a given input event
     *
     * @param[e] The input event to evaluate.
     */
    private fun getMask(e: InputEvent, initial: Int = 0): Int {
        val modifiers = e.modifiersEx
        var mask = initial

        /*
		 * For scrolling AWT uses the SHIFT_DOWN_MASK to indicate horizontal scrolling.
		 * We keep track of whether the SHIFT key was actually pressed for disambiguation.
		 */
        if (modifiers and InputEvent.SHIFT_DOWN_MASK != 0) {
            mask = mask or (1 shl 6)
        }

        /*
		 * On OS X AWT sets the META_DOWN_MASK to for right clicks. We keep
		 * track of whether the META key was actually pressed for
		 * disambiguation.
		 */
        if (modifiers and InputEvent.ALT_DOWN_MASK != 0) {
            mask = mask or (1 shl 9)
        }

        if (modifiers and InputEvent.META_DOWN_MASK != 0) {
            mask = mask or (1 shl 8)
        }

        if (modifiers and InputEvent.CTRL_DOWN_MASK != 0) {
            mask = mask or (1 shl 7)
        }

        /*
		 * We add the button modifiers to modifiersEx such that the
		 * XXX_DOWN_MASK can be used as the canonical flag. E.g. we adapt
		 * modifiersEx such that BUTTON1_DOWN_MASK is also present in
		 * mouseClicked() when BUTTON1 was clicked (although the button is no
		 * longer down at this point).
		 *
		 * ...but only if its not a MouseWheelEvent because OS X sets button
		 * modifiers if ALT or META modifiers are pressed.
		 */
        if (e is MouseEvent) {
            if (modifiers and InputEvent.BUTTON1_DOWN_MASK != 0) {
                mask = mask or InputEvent.BUTTON1_DOWN_MASK
            }
            if (modifiers and InputEvent.BUTTON2_DOWN_MASK != 0) {
                mask = mask or InputEvent.BUTTON2_DOWN_MASK
            }
            if (modifiers and InputEvent.BUTTON3_DOWN_MASK != 0) {
                mask = mask or InputEvent.BUTTON3_DOWN_MASK
            }
        }

        /*
		 * Deal with mous double-clicks.
		 */

        if (e is MouseEvent && e.clickCount > 1) {
            mask = mask or InputTrigger.DOUBLE_CLICK_MASK
        } // mouse

        if (e is MouseWheelEvent && e.id == MouseWheelEvent.MOUSE_WHEEL) {
            mask = mask or InputTrigger.SCROLL_MASK
            mask = mask and (1 shl 10).inv()
        }

        return mask
    }

    /**
     * Called when the mouse is moved, evaluates active drag behaviours, updates state
     *
     * @param[e] The incoming MouseEvent
     */
    fun mouseMoved(e: MouseEvent) {
        update()

        mouseX = e.x
        mouseY = e.y

        for (drag in activeKeyDrags) {
            drag.behaviour.drag(mouseX, mouseY)
        }

        for (drag in activeButtonDrags) {
            drag.behaviour.drag(mouseX, mouseY)
        }
    }

    /**
     * Called when the mouse enters, updates state
     *
     * @param[e] The incoming MouseEvent
     */
    @Suppress("UNUSED_PARAMETER")
    fun mouseEntered(e: MouseEvent) {
        update()
    }

    /**
     * Called when the mouse is clicked, updates state
     *
     * @param[e] The incoming MouseEvent
     */
    @Suppress("UNUSED_PARAMETER")
    fun mouseClicked(e: MouseEvent) {
        update()

        val mask = getMask(e)
        val x = e.x
        val y = e.y

        val clickMask = mask and InputTrigger.DOUBLE_CLICK_MASK.inv()
        for (click in buttonClicks) {
            if(logger.isTraceEnabled) {
                logger.trace("behav = {}", click.buttons.mask.toBinaryString())
                logger.trace("event = {}", mask.toBinaryString())
                logger.trace("click = {}", clickMask.toBinaryString())
            }
            if (click.buttons.matches(mask, pressedKeys) || clickMask != mask && click.buttons.matches(clickMask, pressedKeys)) {
                click.behaviour.click(x, y)
            }
        }
    }

    /**
     * Called when the mouse wheel is moved
     *
     * @param[e] The incoming mouse event
     */
    fun mouseWheelMoved(e: MouseWheelEvent) {
        update()

        val mask = getMask(e)
        val x = e.x
        val y = e.y
        val wheelRotation = e.wheelRotation

        /*
		 * AWT uses the SHIFT_DOWN_MASK to indicate horizontal scrolling. We
		 * keep track of whether the SHIFT key was actually pressed for
		 * disambiguation. However, we can only detect horizontal scrolling if
		 * the SHIFT key is not pressed. With SHIFT pressed, everything is
		 * treated as vertical scrolling.
		 */
        val exShiftMask = e.getModifiersEx() and InputEvent.SHIFT_DOWN_MASK != 0
        val isHorizontal = !shiftPressed && exShiftMask

        for (scroll in scrolls) {
            if (scroll.buttons.matches(mask, pressedKeys)) {
                if(isHorizontal) {
                    scroll.behaviour.scroll(wheelRotation.toDouble(), isHorizontal, x, y)
                } else {
                    scroll.behaviour.scroll(wheelRotation.toDouble(), isHorizontal, x, y)
                }
            }
        }
    }

    /**
     * Called when the mouse is release
     *
     * @param[e] The incoming mouse event
     */
    fun mouseReleased(e: MouseEvent) {
        update()

        val x = e.x
        val y = e.y

        for (drag in activeButtonDrags)
            drag.behaviour.end(x, y)
        activeButtonDrags.clear()
    }

    /**
     * Called when the mouse is dragged, evaluates current drag behaviours
     *
     * @param[e] The incoming mouse event
     */
    fun mouseDragged(e: MouseEvent) {
        update()

        val x = e.x
        val y = e.y

        for (drag in activeButtonDrags) {
            drag.behaviour.drag(x, y)
        }
    }

    /**
     * Called when the mouse is exiting, updates state
     *
     * @param[e] The incoming mouse event
     */
    @Suppress("UNUSED_PARAMETER")
    fun mouseExited(e: MouseEvent) {
        update()
    }

    /**
     * Called when the mouse is pressed, updates state and masks, evaluates drags
     *
     * @param[e] The incoming mouse event
     */
    fun mousePressed(e: MouseEvent) {
        update()

        val mask = getMask(e, initial = 0)
        val x = e.x
        val y = e.y

        for (drag in buttonDrags) {
            if (drag.buttons.matches(mask, pressedKeys)) {
                drag.behaviour.init(x, y)
                activeButtonDrags.add(drag)
            }
        }

        val clickMask = mask and InputTrigger.DOUBLE_CLICK_MASK.inv()
        buttonClicks
            .filter { it.buttons.matches(mask, pressedKeys) || clickMask != mask && it.buttons.matches(clickMask, pressedKeys) }
            .forEach { it.behaviour.click(x, y) }
    }

    /**
     * Called when a key is pressed
     *
     * @param[e] The incoming keyboard event
     */
    fun keyPressed(e: KeyEvent) {
        update()

        /*if (e.modifiers and GLFW_MOD_SHIFT == 1) {
            shiftPressed = true
        } else if (e.modifiers and GLFW_MOD_ALT == 1) {
            metaPressed = true
        } else if (e.modifiers and GLFW_MOD_CONTROL == 1) {
            winPressed = true
        }*/
        if (e.keyCode != KeyEvent.VK_ALT &&
            e.keyCode != KeyEvent.VK_CONTROL &&
            e.keyCode != KeyEvent.VK_SHIFT) {
            val inserted = pressedKeys.add(e.keyCode)

            /*
			 * Create mask and deal with double-click on keys.
			 */

            val mask = getMask(e)
            var doubleClick = false
            if (inserted) {
                // double-click on keys.
                val lastPressTime = keyPressTimes.get(e.keyCode)
                if (lastPressTime.toInt() != -1 && e.`when` - lastPressTime < DOUBLE_CLICK_INTERVAL)
                    doubleClick = true

                keyPressTimes.put(e.keyCode, e.`when`)
            }
            val doubleClickMask = mask or InputTrigger.DOUBLE_CLICK_MASK

            for (drag in keyDrags) {
                if (!activeKeyDrags.contains(drag) && (drag.buttons.matches(mask, pressedKeys) || doubleClick && drag.buttons.matches(doubleClickMask, pressedKeys))) {
                    drag.behaviour.init(mouseX, mouseY)
                    activeKeyDrags.add(drag)
                }
            }

            for (click in keyClicks) {
                logger.trace(click.buttons.mask.toString() + " vs " + mask.toString())
                logger.trace(click.buttons.pressedKeys.toString() +  " vs " + pressedKeys.toString() )
                if (click.buttons.matches(mask, pressedKeys) || doubleClick && click.buttons.matches(doubleClickMask, pressedKeys)) {
                    click.behaviour.click(mouseX, mouseY)
                }
            }
        }
    }

    /**
     * Called when a key is released
     *
     * @param[e] The incoming keyboard event
     */
    fun keyReleased(e: KeyEvent) {
        update()

        if (e.keyCode == KeyEvent.VK_SHIFT) {
            shiftPressed = false
        } else if (e.keyCode == KeyEvent.VK_META) {
            metaPressed = false
        } else if (e.keyCode == KeyEvent.VK_WINDOWS) {
            winPressed = false
        } else if (e.keyCode != KeyEvent.VK_ALT &&
            e.keyCode != KeyEvent.VK_CONTROL &&
            e.keyCode != KeyEvent.VK_ALT_GRAPH) {
            pressedKeys.remove(e.keyCode)

            for (drag in activeKeyDrags)
                drag.behaviour.end(mouseX, mouseY)
            activeKeyDrags.clear()
        }
    }

    override fun close() {
        super.close()
        cursorCallback.close()
        keyCallback.close()
        mouseCallback.close()
        scrollCallback.close()
    }

    override fun attach(hub: Hub?, window: SceneryWindow, inputMap: InputTriggerMap, behaviourMap: BehaviourMap): MouseAndKeyHandlerBase {
        val handler: MouseAndKeyHandlerBase
        when(window) {
            is SceneryWindow.GLFWWindow -> {
                this.hub = hub
                handler = this

                handler.setInputMap(inputMap)
                handler.setBehaviourMap(behaviourMap)

                glfwSetCursorPosCallback(window.window, handler.cursorCallback)
                glfwSetKeyCallback(window.window, handler.keyCallback)
                glfwSetScrollCallback(window.window, handler.scrollCallback)
                glfwSetMouseButtonCallback(window.window, handler.mouseCallback)
            }

            is SceneryWindow.HeadlessWindow -> {
                this.hub = hub
                handler = this

                handler.setInputMap(inputMap)
                handler.setBehaviourMap(behaviourMap)
            }

            else -> throw UnsupportedOperationException("Don't know how to handle window of type $window. Supported types are: ${(this.javaClass.annotations.find { it is CanHandleInputFor } as? CanHandleInputFor)?.windowTypes?.joinToString(", ")}")
        }

        return handler
    }

    companion object {
        internal val fakeComponent = object: Component() {}
    }
}
