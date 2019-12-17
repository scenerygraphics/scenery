package graphics.scenery.controls

import com.jogamp.newt.awt.NewtCanvasAWT
import com.jogamp.newt.event.*
import graphics.scenery.Hub
import graphics.scenery.Settings
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.utils.ExtractsNatives
import net.java.games.input.ControllerListener
import org.scijava.ui.behaviour.BehaviourMap
import org.scijava.ui.behaviour.InputTrigger
import org.scijava.ui.behaviour.InputTriggerMap

/**
 * Input handling class for JOGL-based windows
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
@CanHandleInputFor([SceneryWindow.ClearGLWindow::class, SceneryWindow.SwingWindow::class, SceneryWindow.JOGLDrawable::class])
open class JOGLMouseAndKeyHandler(protected var hub: Hub?) : MouseAndKeyHandlerBase(), MouseListener, KeyListener, WindowListener, ControllerListener, ExtractsNatives {
    /** store os name */
    private var os = ""

    /** scroll speed multiplier to combat OS idiosyncrasies */
    private var scrollSpeedMultiplier = 1.0f

    init {
        os = if(System.getProperty("os.name").toLowerCase().indexOf("windows") != -1) {
            "windows"
        } else if(System.getProperty("os.name").toLowerCase().indexOf("mac") != -1) {
            "mac"
        } else if(System.getProperty("os.name").toLowerCase().indexOf("linux") != -1) {
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
    private fun getMask(e: InputEvent): Int {
        val modifiers = e.modifiers
        var mask = 0

        /*
		 * For scrolling AWT uses the SHIFT_DOWN_MASK to indicate horizontal scrolling.
		 * We keep track of whether the SHIFT key was actually pressed for disambiguation.
		 */
        if (shiftPressed)
            mask = mask or (1 shl 6)

        /*
		 * On OS X AWT sets the META_DOWN_MASK to for right clicks. We keep
		 * track of whether the META key was actually pressed for
		 * disambiguation.
		 */
        if (metaPressed) {
            mask = mask or (1 shl 8)
        }

        if (winPressed) {
            logger.warn("Windows key not supported")
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
        if (e is MouseEvent && (e.rotation[0] < 0.001f || e.rotation[1] < 0.001f)) {
            if (modifiers and InputEvent.BUTTON1_MASK != 0) {
                mask = mask or (1 shl 10)
            }
            if (modifiers and InputEvent.BUTTON2_MASK != 0) {
                mask = mask or (1 shl 11)
            }
            if (modifiers and InputEvent.BUTTON3_MASK != 0) {
                mask = mask or (1 shl 12)
            }
        }

        /*
		 * Deal with mous double-clicks.
		 */

        if (e is MouseEvent && e.clickCount > 1) {
            mask = mask or InputTrigger.DOUBLE_CLICK_MASK
        } // mouse

        if (e is MouseEvent && e.eventType == MouseEvent.EVENT_MOUSE_WHEEL_MOVED) {
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
    override fun mouseMoved(e: MouseEvent) {
        val supersamplingFactor = hub?.get<Settings>()?.get("Renderer.SupersamplingFactor", 1.0f) ?: 1.0f

        update()

        mouseX = (e.x * supersamplingFactor).toInt()
        mouseY = (e.y * supersamplingFactor).toInt()

        for (drag in activeKeyDrags)
            drag.behaviour.drag(mouseX, mouseY)
    }

    /**
     * Called when the mouse enters, updates state
     *
     * @param[e] The incoming MouseEvent
     */
    override fun mouseEntered(e: MouseEvent) {
        update()
    }

    /**
     * Called when the mouse is clicked, updates state
     *
     * @param[e] The incoming MouseEvent
     */
    override fun mouseClicked(e: MouseEvent) {
        val supersamplingFactor = hub?.get<Settings>()?.get("Renderer.SupersamplingFactor", 1.0f) ?: 1.0f

        update()

        val mask = getMask(e)
        val x = (e.x * supersamplingFactor).toInt()
        val y = (e.y * supersamplingFactor).toInt()

        val clickMask = mask and InputTrigger.DOUBLE_CLICK_MASK.inv()
        buttonClicks
            .filter { it.buttons.matches(mask, pressedKeys) || clickMask != mask && it.buttons.matches(clickMask, pressedKeys) }
            .forEach { it.behaviour.click(x, y) }
    }

    /**
     * Called when the mouse wheel is moved
     *
     * @param[e] The incoming mouse event
     */
    override fun mouseWheelMoved(e: MouseEvent) {
        val supersamplingFactor = hub?.get<Settings>()?.get("Renderer.SupersamplingFactor", 1.0f) ?: 1.0f

        update()

        val mask = getMask(e)
        val x = (e.x * supersamplingFactor).toInt()
        val y = (e.y * supersamplingFactor).toInt()
        val wheelRotation = e.rotation

        /*
		 * AWT uses the SHIFT_DOWN_MASK to indicate horizontal scrolling. We
		 * keep track of whether the SHIFT key was actually pressed for
		 * disambiguation. However, we can only detect horizontal scrolling if
		 * the SHIFT key is not pressed. With SHIFT pressed, everything is
		 * treated as vertical scrolling.
		 */
        val exShiftMask = e.modifiers and InputEvent.SHIFT_MASK != 0
        val isHorizontal = !shiftPressed && exShiftMask && wheelRotation[1] == 0.0f

        scrolls
            .filter { it.buttons.matches(mask, pressedKeys) }
            .forEach {
                if(isHorizontal) {
                    it.behaviour.scroll(wheelRotation[0].toDouble()*scrollSpeedMultiplier, isHorizontal, x, y)
                } else {
                    it.behaviour.scroll(wheelRotation[1].toDouble()*scrollSpeedMultiplier, isHorizontal, x, y)
                }
            }
    }

    /**
     * Called when the mouse is release
     *
     * @param[e] The incoming mouse event
     */
    override fun mouseReleased(e: MouseEvent) {
        val supersamplingFactor = hub?.get<Settings>()?.get("Renderer.SupersamplingFactor", 1.0f) ?: 1.0f

        update()

        val x = (e.x * supersamplingFactor).toInt()
        val y = (e.y * supersamplingFactor).toInt()

        for (drag in activeButtonDrags)
            drag.behaviour.end(x, y)
        activeButtonDrags.clear()
    }

    /**
     * Called when the mouse is dragged, evaluates current drag behaviours
     *
     * @param[e] The incoming mouse event
     */
    override fun mouseDragged(e: MouseEvent) {
        val supersamplingFactor = hub?.get<Settings>()?.get("Renderer.SupersamplingFactor", 1.0f) ?: 1.0f

        update()

        val x = (e.x * supersamplingFactor).toInt()
        val y = (e.y * supersamplingFactor).toInt()

        for (drag in activeButtonDrags) {
            drag.behaviour.drag(x, y)
        }
    }

    /**
     * Called when the mouse is exiting, updates state
     *
     * @param[e] The incoming mouse event
     */
    override fun mouseExited(e: MouseEvent) {
        update()
    }

    /**
     * Called when the mouse is pressed, updates state and masks, evaluates drags
     *
     * @param[e] The incoming mouse event
     */
    override fun mousePressed(e: MouseEvent) {
        val supersamplingFactor = hub?.get<Settings>()?.get("Renderer.SupersamplingFactor", 1.0f) ?: 1.0f

        update()

        val mask = getMask(e)
        val x = (e.x * supersamplingFactor).toInt()
        val y = (e.y * supersamplingFactor).toInt()

        for (drag in buttonDrags) {
            if (drag.buttons.matches(mask, pressedKeys)) {
                drag.behaviour.init(x, y)
                activeButtonDrags.add(drag)
            }
        }
    }

    /**
     * Called when a key is pressed
     *
     * @param[e] The incoming keyboard event
     */
    override fun keyPressed(e: KeyEvent) {
        update()

        if (e.keyCode == KeyEvent.VK_SHIFT) {
            shiftPressed = true
        } else if (e.keyCode == KeyEvent.VK_META) {
            metaPressed = true
        } else if (e.keyCode == KeyEvent.VK_WINDOWS) {
            winPressed = true
        }
        else if (e.keyCode != KeyEvent.VK_ALT &&
                e.keyCode != KeyEvent.VK_CONTROL &&
                e.keyCode != KeyEvent.VK_ALT_GRAPH) {
            val inserted = pressedKeys.add(e.keyCode.toInt())

            /*
			 * Create mask and deal with double-click on keys.
			 */

            val mask = getMask(e)
            var doubleClick = false
            if (inserted) {
                // double-click on keys.
                val lastPressTime = keyPressTimes.get(e.keyCode.toInt())
                if (lastPressTime.toInt() != -1 && e.`when` - lastPressTime < DOUBLE_CLICK_INTERVAL)
                    doubleClick = true

                keyPressTimes.put(e.keyCode.toInt(), e.`when`)
            }
            val doubleClickMask = mask or InputTrigger.DOUBLE_CLICK_MASK

            for (drag in keyDrags) {
                if (!activeKeyDrags.contains(drag) && (drag.buttons.matches(mask, pressedKeys) || doubleClick && drag.buttons.matches(doubleClickMask, pressedKeys))) {
                    drag.behaviour.init(mouseX, mouseY)
                    activeKeyDrags.add(drag)
                }
            }

            keyClicks
                .filter { it.buttons.matches(mask, pressedKeys) || doubleClick && it.buttons.matches(doubleClickMask, pressedKeys) }
                .forEach { it.behaviour.click(mouseX, mouseY) }
        }
    }

    /**
     * Called when a key is released
     *
     * @param[e] The incoming keyboard event
     */
    override fun keyReleased(e: KeyEvent) {
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
            pressedKeys.remove(e.keyCode.toInt())

            for (drag in activeKeyDrags)
                drag.behaviour.end(mouseX, mouseY)
            activeKeyDrags.clear()
        }
    }

    /**
     * Called when a window repaint event is registered
     *
     * @param[e] The incoming window update event
     */
    override fun windowRepaint(e: WindowUpdateEvent?) {
    }

    /**
     * Called when a window destroy event is registered
     *
     * @param[e] The incoming window event
     */
    override fun windowDestroyed(e: WindowEvent?) {
    }

    /**
     * Called when a window destruction notification event is registered
     *
     * @param[e] The incoming window update event
     */
    override fun windowDestroyNotify(e: WindowEvent?) {
    }

    /**
     * Called when the window lost focus. Clears pressed keys
     *
     * @param[e] The incoming window update event
     */
    override fun windowLostFocus(e: WindowEvent?) {
        pressedKeys.clear()
        shiftPressed = false
        metaPressed = false
        winPressed = false
    }

    /**
     * Called when a window move event is registered
     *
     * @param[e] The incoming window update event
     */
    override fun windowMoved(e: WindowEvent?) {
    }

    /**
     * Called when a window resize event is registered
     *
     * @param[e] The incoming window update event
     */
    override fun windowResized(e: WindowEvent?) {
    }

    /**
     * Called when a window regains focus, clears pressed keys
     *
     * @param[e] The incoming window update event
     */
    override fun windowGainedFocus(e: WindowEvent?) {
        pressedKeys.clear()
        shiftPressed = false
        metaPressed = false
        winPressed = false
    }

    override fun attach(window: SceneryWindow, inputMap: InputTriggerMap, behaviourMap: BehaviourMap): MouseAndKeyHandlerBase {
        val handler: MouseAndKeyHandlerBase
        when(window) {
            is SceneryWindow.SwingWindow -> {
                val component = window.panel.component
                val cglWindow = window.panel.cglWindow

                if(component is NewtCanvasAWT && cglWindow != null) {
                    handler = this

                    handler.setInputMap(inputMap)
                    handler.setBehaviourMap(behaviourMap)

                    cglWindow.addKeyListener(handler)
                    cglWindow.addMouseListener(handler)
                } else {
                    handler = SwingMouseAndKeyHandler()

                    handler.setInputMap(inputMap)
                    handler.setBehaviourMap(behaviourMap)

                    val ancestor = window.panel.component
                    ancestor?.addKeyListener(handler)
                    ancestor?.addMouseListener(handler)
                    ancestor?.addMouseMotionListener(handler)
                    ancestor?.addMouseWheelListener(handler)
                    ancestor?.addFocusListener(handler)
                }
            }

            is SceneryWindow.ClearGLWindow  -> {
                // create Mouse & Keyboard Handler
                handler = this
                handler.setInputMap(inputMap)
                handler.setBehaviourMap(behaviourMap)

                window.window.addKeyListener(handler)
                window.window.addMouseListener(handler)
            }

            is SceneryWindow.JOGLDrawable -> {
                // create Mouse & Keyboard Handler
                handler = this
                handler.setInputMap(inputMap)
                handler.setBehaviourMap(behaviourMap)

                // TODO: Add listeners in appropriate place
                // window.drawable.addKeyListener(handler)
                // window.drawable.addMouseListener(handler)
            }

            is SceneryWindow.HeadlessWindow -> {
                handler = this
                handler.setInputMap(inputMap)
                handler.setBehaviourMap(behaviourMap)
            }

            else -> throw UnsupportedOperationException("Don't know how to handle window of type $window. Supported types are: ${(this.javaClass.annotations.find { it is CanHandleInputFor } as? CanHandleInputFor)?.windowTypes?.joinToString(", ")}")
        }

        return handler
    }
}
