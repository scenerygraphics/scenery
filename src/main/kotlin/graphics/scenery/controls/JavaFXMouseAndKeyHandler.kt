package graphics.scenery.controls

import gnu.trove.map.hash.TIntLongHashMap
import gnu.trove.set.hash.TIntHashSet
import net.java.games.input.*
import org.scijava.ui.behaviour.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import graphics.scenery.Hub
import graphics.scenery.controls.behaviours.GamepadBehaviour
import graphics.scenery.utils.ExtractsNatives
import javafx.event.EventHandler
import javafx.scene.input.*
import javafx.stage.Stage
import java.awt.Toolkit
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Input handling class for JavaFX-based windows.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class JavaFXMouseAndKeyHandler(protected var hub: Hub?, protected var stage: Stage) : graphics.scenery.controls.MouseAndKeyHandler, ControllerListener, ExtractsNatives, EventHandler<javafx.event.Event> {
    /** slf4j logger for this class */
    protected var logger: Logger = LoggerFactory.getLogger("InputHandler")

    /** handle to the active controller */
    private var controller: Controller? = null

    /** handle to the active controller's polling thread */
    private var controllerThread: Thread? = null

    /** hash map of the controller's components that are currently above [CONTROLLER_DOWN_THRESHOLD] */
    private var controllerAxisDown: ConcurrentHashMap<Component.Identifier, Float> = ConcurrentHashMap()

    /** polling interval for the controller */
    private val CONTROLLER_HEARTBEAT = 5L

    /** threshold over which an axis is considered down */
    private val CONTROLLER_DOWN_THRESHOLD = 0.95f

    /** the windowing system's set double click interval */
    private val DOUBLE_CLICK_INTERVAL = getDoubleClickInterval()

    /** store os name */
    private var os = ""

    /** scroll speed multiplier to combat OS idiosyncrasies */
    private var scrollSpeedMultiplier = 1.0f

    /**
     * Queries the windowing system for the current double click interval
     *
     * @return The double click interval in ms
     */
    private fun getDoubleClickInterval(): Int {
        val prop = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval")

        if (prop == null) {
            return 200
        } else {
            return prop as Int
        }
    }

    /** ui-behaviour input trigger map */
    private var inputMap: InputTriggerMap? = null

    /** ui-behaviour behaviour map */
    private var behaviourMap: BehaviourMap? = null

    /** expected modifier count */
    private var inputMapExpectedModCount: Int = 0

    /** behaviour expected modifier count */
    private var behaviourMapExpectedModCount: Int = 0

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

        logger.debug("Native JARs for JInput: ${getNativeJars("jinput-platform").joinToString(", ")}")
        extractLibrariesFromJar(getNativeJars("jinput-platform", hint = "jinput-raw.dll"))

        ControllerEnvironment.getDefaultEnvironment().controllers.forEach {
            if (it.type == Controller.Type.STICK || it.type == Controller.Type.GAMEPAD) {
                this.controller = it
                logger.info("Added gamepad controller: $it")
            }
        }

        controllerThread = thread {
            var event_queue: EventQueue
            val event: Event = Event()

            while (true) {
                controller?.let {
                    controller!!.poll()

                    event_queue = controller!!.eventQueue

                    while (event_queue.getNextEvent(event)) {
                        controllerEvent(event)
                    }
                }

                for (gamepad in gamepads) {
                    for (it in controllerAxisDown) {
                        if (Math.abs(it.value) > 0.02f && gamepad.behaviour.axis.contains(it.key)) {
                            logger.trace("Triggering ${it.key} because axis is down (${it.value})")
                            gamepad.behaviour.axisEvent(it.key, it.value.toFloat())
                        }
                    }
                }

                Thread.sleep(this.CONTROLLER_HEARTBEAT)
            }
        }

        stage.addEventHandler(DragEvent.ANY, this)
        stage.addEventHandler(MouseEvent.ANY, this)
        stage.addEventHandler(KeyEvent.ANY, this)
        stage.addEventHandler(ScrollEvent.ANY, this)
    }

    /**
     * Handle JavaFX events
     */
    override fun handle(event: javafx.event.Event) {
        if (event is KeyEvent) {
            logger.info("Key event!: ${event.eventType}")
            when (event.eventType) {
                KeyEvent.KEY_PRESSED -> keyPressed(event)
                KeyEvent.KEY_RELEASED -> keyReleased(event)
                KeyEvent.KEY_TYPED -> keyPressed(event)
            }
        } else if (event is DragEvent) {
            logger.info("Drag event!")
            when (event.eventType) {
//                DragEvent.DRAG_ENTERED -> mouseDragged(event)
//                DragEvent.DRAG_DONE -> mouseReleased(event)
            }
        } else if (event is MouseEvent) {
            logger.info("mouse event! ${event.eventType}")
            when (event.eventType) {
                MouseEvent.MOUSE_PRESSED -> mousePressed(event)
                MouseEvent.MOUSE_CLICKED -> mouseClicked(event)
                MouseEvent.MOUSE_MOVED -> mouseMoved(event)
                MouseEvent.MOUSE_RELEASED -> mouseReleased(event)
            }
        } else if (event is ScrollEvent) {
            logger.info("Scroll event!")
            when (event.eventType) {
                ScrollEvent.ANY -> mouseWheelMoved(event)
            }
        }

        event.consume()
    }

    /**
     * Sets the input trigger map to the given map
     *
     * @param[inputMap] The input map to set
     */
    override fun setInputMap(inputMap: InputTriggerMap) {
        this.inputMap = inputMap
        inputMapExpectedModCount = inputMap.modCount() - 1
    }

    /**
     * Sets the behaviour trigger map to the given map
     *
     * @param[behaviourMap] The behaviour map to set
     */
    override fun setBehaviourMap(behaviourMap: BehaviourMap) {
        this.behaviourMap = behaviourMap
        behaviourMapExpectedModCount = behaviourMap.modCount() - 1
    }

    /**
     * Managing internal behaviour lists.
     *
     * The internal lists only contain entries for Behaviours that can be
     * actually triggered with the current InputMap, grouped by Behaviour type,
     * such that hopefully lookup from the event handlers is fast,
     *
     * @property[buttons] Buttons triggering the input
     * @property[behaviour] Behaviour triggered by these buttons
     */
    internal class BehaviourEntry<T : Behaviour>(
        val buttons: InputTrigger,
        val behaviour: T)

    private val buttonDrags = ArrayList<BehaviourEntry<DragBehaviour>>()

    private val keyDrags = ArrayList<BehaviourEntry<DragBehaviour>>()

    private val buttonClicks = ArrayList<BehaviourEntry<ClickBehaviour>>()

    private val keyClicks = ArrayList<BehaviourEntry<ClickBehaviour>>()

    private val scrolls = ArrayList<BehaviourEntry<ScrollBehaviour>>()

    private val gamepads = ArrayList<BehaviourEntry<GamepadBehaviour>>()

    /**
     * Make sure that the internal behaviour lists are up to date. For this, we
     * keep track the modification count of [.inputMap] and
     * [.behaviourMap]. If expected mod counts are not matched, call
     * [.updateInternalMaps] to rebuild the internal behaviour lists.
     */
    @Synchronized private fun update() {
        val imc = inputMap!!.modCount()
        val bmc = behaviourMap!!.modCount()

        if (imc != inputMapExpectedModCount || bmc != behaviourMapExpectedModCount) {
            inputMapExpectedModCount = imc
            behaviourMapExpectedModCount = bmc
            updateInternalMaps()
        }
    }

    /**
     * Build internal lists buttonDrag, keyDrags, etc from BehaviourMap(?) and
     * InputMap(?). The internal lists only contain entries for Behaviours that
     * can be actually triggered with the current InputMap, grouped by Behaviour
     * type, such that hopefully lookup from the event handlers is fast.
     */
    private fun updateInternalMaps() {
        buttonDrags.clear()
        keyDrags.clear()
        buttonClicks.clear()
        keyClicks.clear()

        for (entry in inputMap!!.getAllBindings().entries) {
            val buttons = entry.key
            val behaviourKeys = entry.value ?: continue

            for (behaviourKey in behaviourKeys) {
                val behaviour = behaviourMap!!.get(behaviourKey) ?: continue

                if (behaviour is DragBehaviour) {
                    val dragEntry = BehaviourEntry<DragBehaviour>(buttons, behaviour)
                    if (buttons.isKeyTriggered())
                        keyDrags.add(dragEntry)
                    else
                        buttonDrags.add(dragEntry)
                }

                if (behaviour is ClickBehaviour) {
                    val clickEntry = BehaviourEntry<ClickBehaviour>(buttons, behaviour)
                    if (buttons.isKeyTriggered())
                        keyClicks.add(clickEntry)
                    else
                        buttonClicks.add(clickEntry)
                }

                if (behaviour is ScrollBehaviour) {
                    val scrollEntry = BehaviourEntry<ScrollBehaviour>(buttons, behaviour)
                    scrolls.add(scrollEntry)
                }

                if (behaviour is GamepadBehaviour) {
                    val gamepadEntry = BehaviourEntry<GamepadBehaviour>(buttons, behaviour)
                    gamepads.add(gamepadEntry)
                }
            }
        }
    }


    /**
     * Which keys are currently pressed. This does not include modifier keys
     * Control, Shift, Alt, AltGr, Meta.
     */
    private val pressedKeys = TIntHashSet(5, 0.5f, -1)

    /**
     * When keys where pressed
     */
    private val keyPressTimes = TIntLongHashMap(100, 0.5f, -1, -1)

    /**
     * Whether the SHIFT key is currently pressed. We need this, because for
     * mouse-wheel AWT uses the SHIFT_DOWN_MASK to indicate horizontal
     * scrolling. We keep track of whether the SHIFT key was actually pressed
     * for disambiguation.
     */
    private var shiftPressed = false

    /**
     * Whether the META key is currently pressed. We need this, because on OS X
     * AWT sets the META_DOWN_MASK to for right clicks. We keep track of whether
     * the META key was actually pressed for disambiguation.
     */
    private var metaPressed = false

    /**
     * Whether the WINDOWS key is currently pressed.
     */
    private var winPressed = false

    /**
     * The current mouse coordinates, updated through [.mouseMoved].
     */
    private var mouseX: Int = 0

    /**
     * The current mouse coordinates, updated through [.mouseMoved].
     */
    private var mouseY: Int = 0

    /**
     * Active [DragBehaviour]s initiated by mouse button press.
     */
    private val activeButtonDrags = ArrayList<BehaviourEntry<DragBehaviour>>()

    /**
     * Active [DragBehaviour]s initiated by key press.
     */
    private val activeKeyDrags = ArrayList<BehaviourEntry<DragBehaviour>>()

    /**
     * Returns the key mask of a given input event
     *
     * @param[e] The input event to evaluate.
     */
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
            System.err.println("Windows key not supported")
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
            System.err.println("Windows key not supported")
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

        if (e is MouseEvent && e.clickCount > 1) {
            mask = mask or InputTrigger.DOUBLE_CLICK_MASK
        }

        return mask
    }

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
            System.err.println("Windows key not supported")
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
        for (click in buttonClicks) {
            if (click.buttons.matches(mask, pressedKeys) || clickMask != mask && click.buttons.matches(clickMask, pressedKeys)) {
                click.behaviour.click(x.toInt(), y.toInt())
            }
        }
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
    }

    /**
     * Called when a key is pressed
     *
     * @param[e] The incoming keyboard event
     */
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
            val inserted = pressedKeys.add(e.code.ordinal)

            /*
			 * Create mask and deal with double-click on keys.
			 */

            val mask = getMask(e)
            var doubleClick = false
            if (inserted) {
                // double-click on keys.
                val lastPressTime = keyPressTimes.get(e.code.ordinal)
                if (lastPressTime.toInt() != -1 && System.nanoTime() - lastPressTime < DOUBLE_CLICK_INTERVAL)
                    doubleClick = true

                keyPressTimes.put(e.code.ordinal, System.nanoTime())
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
            pressedKeys.remove(e.code.ordinal)

            for (drag in activeKeyDrags)
                drag.behaviour.end(mouseX, mouseY)
            activeKeyDrags.clear()
        }
    }

    /**
     * Called when a new controller is added
     *
     * @param[event] The incoming controller event
     */
    override fun controllerAdded(event: ControllerEvent?) {
        if (controller == null && event != null && event.controller.type == Controller.Type.GAMEPAD) {
            logger.info("Adding controller ${event.controller}")
            this.controller = event.controller
        }
    }

    /**
     * Called when a controller is removed
     *
     * @param[event] The incoming controller event
     */
    override fun controllerRemoved(event: ControllerEvent?) {
        if (event != null && controller != null) {
            logger.info("Controller removed: ${event.controller}")

            controller = null
        }
    }

    /**
     * Called when a controller event is fired. This will update the currently down
     * buttons/axis on the controller.
     *
     * @param[event] The incoming controller event
     */
    fun controllerEvent(event: Event) {
        for (gamepad in gamepads) {
            if (event.component.isAnalog && Math.abs(event.component.pollData) < CONTROLLER_DOWN_THRESHOLD) {
                logger.trace("${event.component.identifier} over threshold, removing")
                controllerAxisDown.put(event.component.identifier, 0.0f)
            } else {
                controllerAxisDown.put(event.component.identifier, event.component.pollData)
            }

            if (gamepad.behaviour.axis.contains(event.component.identifier)) {
                gamepad.behaviour.axisEvent(event.component.identifier, event.component.pollData)
            }
        }
    }
}
