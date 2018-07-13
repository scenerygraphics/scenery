package graphics.scenery.controls

import graphics.scenery.Hub
import graphics.scenery.utils.SceneryPanel
import javafx.event.EventHandler
import javafx.scene.input.*
import javafx.stage.Stage
import org.scijava.ui.behaviour.InputTrigger

/**
 * Input handling class for JavaFX-based windows.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class JavaFXMouseAndKeyHandler(protected var hub: Hub?, protected var panel: SceneryPanel) : MouseAndKeyHandlerBase(), EventHandler<javafx.event.Event> {
    private var os = ""
    private var scrollSpeedMultiplier = 1.0f

    /** Double-click interval, hardcoded here as needed only for keystrokes */
    override val DOUBLE_CLICK_INTERVAL = 200

    init {
        os = if (System.getProperty("os.name").toLowerCase().indexOf("windows") != -1) {
            "windows"
        } else if (System.getProperty("os.name").toLowerCase().indexOf("mac") != -1) {
            "mac"
        } else if (System.getProperty("os.name").toLowerCase().indexOf("linux") != -1) {
            "linux"
        } else {
            "unknown"
        }

        scrollSpeedMultiplier = if (os == "mac") {
            1.0f
        } else {
            10.0f
        }
        
        val stage = panel.scene.window as Stage

        stage.addEventHandler(DragEvent.ANY, this)
        stage.addEventHandler(MouseEvent.ANY, this)
        stage.addEventHandler(KeyEvent.ANY, this)
        stage.addEventHandler(ScrollEvent.ANY, this)
    }

    /**
     * Handle JavaFX events
     */
    override fun handle(event: javafx.event.Event) {
        when (event) {
            is KeyEvent -> when (event.eventType) {
                KeyEvent.KEY_PRESSED -> keyPressed(event)
                KeyEvent.KEY_RELEASED -> keyReleased(event)
            }
            is MouseEvent -> when (event.eventType) {
                MouseEvent.MOUSE_PRESSED -> mousePressed(event)
//                MouseEvent.MOUSE_CLICKED -> mouseClicked(event)
                MouseEvent.MOUSE_MOVED -> mouseMoved(event)
                MouseEvent.MOUSE_DRAGGED -> mouseDragged(event)
                MouseEvent.MOUSE_RELEASED -> mouseReleased(event)
                MouseEvent.MOUSE_ENTERED_TARGET -> mouseEntered(event)
                MouseEvent.MOUSE_EXITED_TARGET -> mouseExited(event)
            }
            is ScrollEvent -> when (event.eventType) {
                ScrollEvent.ANY -> mouseWheelMoved(event)
                ScrollEvent.SCROLL -> mouseWheelMoved(event)
            }
        }

        event.consume()
    }

    /**
     * Returns the key mask of a given input event
     *
     * @param[e] The input event to evaluate.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun getMask(e: GestureEvent): Int {
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

        return mask
    }

    private fun getMask(e: MouseEvent): Int {
        var mask = 0

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

        if (e.isPrimaryButtonDown) {
            mask = mask or (1 shl 10)
        }
        if (e.isSecondaryButtonDown) {
            mask = mask or (1 shl 11)
        }
        if (e.isMiddleButtonDown) {
            mask = mask or (1 shl 12)
        }

        /*
		 * Deal with mouse double-clicks.
		 */

        if (e.clickCount > 1) {
            mask = mask or InputTrigger.DOUBLE_CLICK_MASK
        }

        return mask
    }

    @Suppress("UNUSED_PARAMETER")
    private fun getMask(e: KeyEvent): Int {
        var mask = 0

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

        return mask
    }

    /**
     * Called when the mouse is moved, evaluates active drag behaviours, updates state
     *
     * @param[e] The incoming MouseEvent
     */
    fun mouseMoved(e: MouseEvent) {
        update()

        mouseX = e.x.toInt()
        mouseY = e.y.toInt()

        for (drag in activeKeyDrags)
            drag.behaviour.drag(mouseX, mouseY)
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
    fun mouseClicked(e: MouseEvent) {
        update()

        val mask = getMask(e)
        val x = e.x
        val y = e.y

        val clickMask = mask and InputTrigger.DOUBLE_CLICK_MASK.inv()

        buttonClicks
            .filter { it.buttons.matches(mask, pressedKeys) || clickMask != mask && it.buttons.matches(clickMask, pressedKeys) }
            .forEach { it.behaviour.click(x.toInt(), y.toInt()) }
    }

    /**
     * Called when the mouse wheel is moved
     *
     * @param[e] The incoming mouse event
     */
    fun mouseWheelMoved(e: ScrollEvent) {
        update()

        val mask = getMask(e)
        val x = e.x
        val y = e.y
        val wheelRotation = e.deltaX.to(e.deltaY)
        val isHorizontal = wheelRotation.second == 0.0

        logger.info("It has been scrolled!")

        scrolls
            .filter { it.buttons.matches(mask, pressedKeys) }
            .forEach {
                if (isHorizontal) {
                    it.behaviour.scroll(wheelRotation.first * scrollSpeedMultiplier, isHorizontal, x.toInt(), y.toInt())
                } else {
                    it.behaviour.scroll(wheelRotation.second * scrollSpeedMultiplier, isHorizontal, x.toInt(), y.toInt())
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
            drag.behaviour.end(x.toInt(), y.toInt())
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
            drag.behaviour.drag(x.toInt(), y.toInt())
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

        val mask = getMask(e)
        val x = e.x
        val y = e.y

        for (drag in buttonDrags) {
            if (drag.buttons.matches(mask, pressedKeys)) {
                drag.behaviour.init(x.toInt(), y.toInt())
                activeButtonDrags.add(drag)
            }
        }

        val clickMask = mask and InputTrigger.DOUBLE_CLICK_MASK.inv()
        buttonClicks
            .filter { it.buttons.matches(mask, pressedKeys) || clickMask != mask && it.buttons.matches(clickMask, pressedKeys) }
            .forEach { it.behaviour.click(x.toInt(), y.toInt()) }
    }

    /**
     * Called when a key is pressed
     *
     * @param[e] The incoming keyboard event
     */
    @Suppress("DEPRECATION")
    fun keyPressed(e: KeyEvent) {
        update()

        if (e.code == KeyCode.SHIFT) {
            shiftPressed = true
        } else if (e.code == KeyCode.META) {
            metaPressed = true
        } else if (e.code == KeyCode.WINDOWS) {
            winPressed = true
        } else if (e.code != KeyCode.ALT &&
            e.code != KeyCode.CONTROL &&
            e.code != KeyCode.ALT_GRAPH) {
            val inserted = pressedKeys.add(e.code.code())

            /*
			 * Create mask and deal with double-click on keys.
			 */

            val mask = getMask(e)
            var doubleClick = false
            if (inserted) {
                // double-click on keys.
                val lastPressTime = keyPressTimes.get(e.code.code())
                if (lastPressTime.toInt() != -1 && System.nanoTime() - lastPressTime < DOUBLE_CLICK_INTERVAL)
                    doubleClick = true

                keyPressTimes.put(e.code.code(), System.nanoTime())
            }
            val doubleClickMask = mask or InputTrigger.DOUBLE_CLICK_MASK

            for (drag in keyDrags) {
                if (!activeKeyDrags.contains(drag) && (drag.buttons.matches(mask, pressedKeys) || doubleClick && drag.buttons.matches(doubleClickMask, pressedKeys))) {
                    drag.behaviour.init(mouseX, mouseY)
                    activeKeyDrags.add(drag)
                }
            }

            for (click in keyClicks) {
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
    @Suppress("DEPRECATION")
    fun keyReleased(e: KeyEvent) {
        update()

        if (e.code == KeyCode.SHIFT) {
            shiftPressed = false
        } else if (e.code == KeyCode.META) {
            metaPressed = false
        } else if (e.code == KeyCode.WINDOWS) {
            winPressed = false
        } else if (e.code != KeyCode.ALT &&
            e.code != KeyCode.CONTROL &&
            e.code != KeyCode.ALT_GRAPH) {
            pressedKeys.remove(e.code.code())

            for (drag in activeKeyDrags)
                drag.behaviour.end(mouseX, mouseY)
            activeKeyDrags.clear()
        }
    }

    private fun KeyCode.code(): Int {
        return try {
            KeyCode::class.java.getDeclaredMethod("impl_getCode").invoke(this) as Int
        } catch (e: NoSuchMethodException) {
            KeyCode::class.java.getDeclaredMethod("getCode").invoke(this) as Int
        }
    }
}
