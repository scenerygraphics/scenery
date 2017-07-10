package graphics.scenery.controls

import gnu.trove.map.hash.TIntLongHashMap
import gnu.trove.set.hash.TIntHashSet
import graphics.scenery.controls.behaviours.GamepadBehaviour
import graphics.scenery.utils.ExtractsNatives
import net.java.games.input.*
import org.scijava.ui.behaviour.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Toolkit
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Base class for MouseAndKeyHandlers
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class MouseAndKeyHandlerBase : ControllerListener, ExtractsNatives {
    protected var logger: Logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    /** ui-behaviour input trigger map */
    protected var inputTriggerMap: InputTriggerMap? = null

    /** ui-behaviour behaviour map */
    protected var behaviours: BehaviourMap? = null

    /** expected modifier count */
    protected var inputMapExpectedModCount: Int = 0

    /** behaviour expected modifier count */
    protected var behaviourMapExpectedModCount: Int = 0

    /** handle to the active controller */
    protected var controller: Controller? = null

    /** handle to the active controller's polling thread */
    private var controllerThread: Thread? = null

    /** hash map of the controller's components that are currently above [CONTROLLER_DOWN_THRESHOLD] */
    private var controllerAxisDown: ConcurrentHashMap<Component.Identifier, Float> = ConcurrentHashMap()

    private val gamepads = ArrayList<BehaviourEntry<GamepadBehaviour>>()

    /** polling interval for the controller */
    private val CONTROLLER_HEARTBEAT = 5L

    /** threshold over which an axis is considered down */
    private val CONTROLLER_DOWN_THRESHOLD = 0.95f

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
    class BehaviourEntry<out T : Behaviour>(
        val buttons: InputTrigger,
        val behaviour: T)

    protected val buttonDrags = ArrayList<BehaviourEntry<DragBehaviour>>()

    protected val keyDrags = ArrayList<BehaviourEntry<DragBehaviour>>()

    protected val buttonClicks = ArrayList<BehaviourEntry<ClickBehaviour>>()

    protected val keyClicks = ArrayList<BehaviourEntry<ClickBehaviour>>()

    protected val scrolls = ArrayList<BehaviourEntry<ScrollBehaviour>>()


    /**
     * Which keys are currently pressed. This does not include modifier keys
     * Control, Shift, Alt, AltGr, Meta.
     */
    protected val pressedKeys = TIntHashSet(5, 0.5f, -1)

    /**
     * When keys where pressed
     */
    protected val keyPressTimes = TIntLongHashMap(100, 0.5f, -1, -1)

    /**
     * Whether the SHIFT key is currently pressed. We need this, because for
     * mouse-wheel AWT uses the SHIFT_DOWN_MASK to indicate horizontal
     * scrolling. We keep track of whether the SHIFT key was actually pressed
     * for disambiguation.
     */
    protected var shiftPressed = false

    /**
     * Whether the META key is currently pressed. We need this, because on OS X
     * AWT sets the META_DOWN_MASK to for right clicks. We keep track of whether
     * the META key was actually pressed for disambiguation.
     */
    protected var metaPressed = false

    /**
     * Whether the WINDOWS key is currently pressed.
     */
    protected var winPressed = false

    /**
     * The current mouse coordinates, updated through [.mouseMoved].
     */
    protected var mouseX: Int = 0

    /**
     * The current mouse coordinates, updated through [.mouseMoved].
     */
    protected var mouseY: Int = 0

    /**
     * Active [DragBehaviour]s initiated by mouse button press.
     */
    protected val activeButtonDrags = ArrayList<BehaviourEntry<DragBehaviour>>()

    /**
     * Active [DragBehaviour]s initiated by key press.
     */
    protected val activeKeyDrags = ArrayList<BehaviourEntry<DragBehaviour>>()

    /** the windowing system's set double click interval */
    protected val DOUBLE_CLICK_INTERVAL = getDoubleClickInterval()

    init {
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

                gamepads?.forEach { gamepad ->
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
    }

    /**
     * Queries the windowing system for the current double click interval
     *
     * @return The double click interval in ms
     */
    internal fun getDoubleClickInterval(): Int {
        val prop = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval")

        if (prop == null) {
            return 200
        } else {
            return prop as Int
        }
    }

    /**
     * Sets the input trigger map to the given map
     *
     * @param[inputMap] The input map to set
     */
    fun setInputMap(inputMap: InputTriggerMap) {
        this.inputTriggerMap = inputMap
        inputMapExpectedModCount = inputMap.modCount() - 1
    }

    /**
     * Sets the behaviour trigger map to the given map
     *
     * @param[behaviourMap] The behaviour map to set
     */
    fun setBehaviourMap(behaviourMap: BehaviourMap) {
        this.behaviours = behaviourMap
        behaviourMapExpectedModCount = behaviourMap.modCount() - 1
    }

    /**
     * Make sure that the internal behaviour lists are up to date. For this, we
     * keep track the modification count of [.inputMap] and
     * [.behaviourMap]. If expected mod counts are not matched, call
     * [.updateInternalMaps] to rebuild the internal behaviour lists.
     */
    @Synchronized protected fun update() {
        val imc = inputTriggerMap!!.modCount()
        val bmc = behaviours!!.modCount()

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

        for ((buttons, value) in inputTriggerMap!!.allBindings) {
            val behaviourKeys = value ?: continue

            for (behaviourKey in behaviourKeys) {
                val behaviour = behaviours!!.get(behaviourKey) ?: continue

                if (behaviour is DragBehaviour) {
                    val dragEntry = BehaviourEntry(buttons, behaviour)
                    if (buttons.isKeyTriggered)
                        keyDrags.add(dragEntry)
                    else
                        buttonDrags.add(dragEntry)
                }

                if (behaviour is ClickBehaviour) {
                    val clickEntry = BehaviourEntry(buttons, behaviour)
                    if (buttons.isKeyTriggered)
                        keyClicks.add(clickEntry)
                    else
                        buttonClicks.add(clickEntry)
                }

                if (behaviour is ScrollBehaviour) {
                    val scrollEntry = BehaviourEntry(buttons, behaviour)
                    scrolls.add(scrollEntry)
                }

                if (behaviour is GamepadBehaviour) {
                    val gamepadEntry = BehaviourEntry(buttons, behaviour)
                    gamepads.add(gamepadEntry)
                }
            }
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
