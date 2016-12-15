package scenery.controls

import com.jogamp.newt.event.InputEvent
import com.jogamp.newt.event.KeyEvent
import com.jogamp.newt.event.MouseEvent
import com.jogamp.newt.event.WindowEvent
import gnu.trove.map.hash.TIntLongHashMap
import gnu.trove.set.hash.TIntHashSet
import net.java.games.input.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWCursorPosCallback
import org.lwjgl.glfw.GLFWKeyCallback
import org.lwjgl.glfw.GLFWMouseButtonCallback
import org.lwjgl.glfw.GLFWScrollCallback
import org.scijava.ui.behaviour.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scenery.Hub
import scenery.controls.behaviours.GamepadBehaviour
import java.awt.Toolkit
import java.io.File
import java.nio.file.Files
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Created by ulrik on 10/26/2016.
 */
open class GLFWMouseAndKeyHandler(protected var hub: Hub?) : MouseAndKeyHandler {

    var cursorCallback = object : GLFWCursorPosCallback() {
        override fun invoke(window: Long, xpos: Double, ypos: Double) {
            mouseMoved(MouseEvent(MouseEvent.EVENT_MOUSE_MOVED,
                this,
                System.nanoTime(),
                0,
                xpos.toInt(),
                ypos.toInt(),
                0, 0,
                floatArrayOf(0.0f, 0.0f, 0.0f), 1.0f))
        }
    }

    var keyCallback = object : GLFWKeyCallback() {
        override fun invoke(window: Long, key: Int, scancode: Int, action: Int, mods: Int) {
            val type = when(action) {
                GLFW_PRESS -> KeyEvent.EVENT_KEY_PRESSED
                GLFW_RELEASE -> KeyEvent.EVENT_KEY_RELEASED
                GLFW_REPEAT -> KeyEvent.EVENT_KEY_PRESSED
                else -> KeyEvent.EVENT_KEY_PRESSED
            }

            val event = KeyEvent.create(
                type,
                this,
                System.nanoTime(),
                mods,
                key.toShort(),
                scancode.toShort(),
                ' '
            )

            when (action) {
                GLFW_PRESS -> keyPressed(event)
                GLFW_REPEAT -> keyPressed(event)
                GLFW_RELEASE -> keyReleased(event)
            }
        }

    }

    var mouseCallback = object : GLFWMouseButtonCallback() {
        override fun invoke(window: Long, key: Int, action: Int, mods: Int) {
            val type = when(action) {
                GLFW_PRESS -> MouseEvent.EVENT_MOUSE_PRESSED
                GLFW_RELEASE -> MouseEvent.EVENT_MOUSE_RELEASED
                else -> MouseEvent.EVENT_MOUSE_CLICKED
            }
            val event = MouseEvent(type,
                this,
                System.nanoTime(),
                0,
                mouseX,
                mouseY,
                1, 0,
                floatArrayOf(0.0f, 0.0f, 0.0f), 1.0f)


                when (action) {
                    GLFW_PRESS -> { mousePressed(event);  }
                    GLFW_RELEASE -> { mouseReleased(event); }
                }
        }
    }

    var scrollCallback = object : GLFWScrollCallback() {
        override fun invoke(window: Long, xoffset: Double, yoffset: Double) {
            mouseWheelMoved(MouseEvent(MouseEvent.EVENT_MOUSE_WHEEL_MOVED,
                this,
                System.nanoTime(),
                0,
                0,
                0,
                0, 0,
                floatArrayOf(xoffset.toFloat(), yoffset.toFloat(), 0.0f), 1.0f))
        }

    }

    /** slf4j logger for this class */
    protected var logger: Logger = LoggerFactory.getLogger("InputHandler")

    /** handle to the active controller */
    private var controller: Controller? = null

    /** handle to the active controller's polling thread */
    private var controllerThread: Thread? = null

    /** hash map of the controller's components that are currently above [CONTROLLER_DOWN_THRESHOLD] */
    private var controllerAxisDown: ConcurrentHashMap<Component.Identifier, Float> = ConcurrentHashMap()

    /** hash map for mouse down events */
    private var mouseButtonsDown: ConcurrentHashMap<Int, Float> = ConcurrentHashMap()

    /** polling interval for the controller */
    private val CONTROLLER_HEARTBEAT = 5L

    /** threshold over which an axis is considered down */
    private val CONTROLLER_DOWN_THRESHOLD = 0.95f

    /** the windowing system's set double click interval */
    private val DOUBLE_CLICK_INTERVAL = getDoubleClickInterval()

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

    /**
     * Utility function to search the current class path for JARs with natie libraries
     *
     * @param[searchName] The string to match the JAR's name against
     * @return A list of JARs matching [searchName]
     */
    private fun getNativeJars(searchName: String): List<String> {
        val classpath = System.getProperty("java.class.path")

        return classpath.split(File.pathSeparator).filter { it.contains(searchName) }
    }

    /**
     * Utility function to extract native libraries from a given JAR, store them in a
     * temporary directory and modify the JRE's library path such that it can find
     * these libraries.
     *
     * @param[paths] A list of JAR paths to extract natives from.
     * @param[replace] Whether or not the java.library.path should be replaced.
     */
    private fun extractLibrariesFromJar(paths: List<String>, replace: Boolean = false) {
        val lp = System.getProperty("java.library.path")
        val tmpDir = Files.createTempDirectory("scenery-natives-tmp").toFile()

        paths.filter { it.toLowerCase().endsWith("jar") }.forEach {
            val jar = java.util.jar.JarFile(it)
            val enumEntries = jar.entries()

            while (enumEntries.hasMoreElements()) {
                val file = enumEntries.nextElement()
                val f = java.io.File(tmpDir.absolutePath + java.io.File.separator + file.getName())

                if (file.isDirectory()) { // if its a directory, create it
                    f.mkdir()
                    continue
                }

                val ins = jar.getInputStream(file) // get the input stream
                val fos = java.io.FileOutputStream(f)
                while (ins.available() > 0) {  // write contents of 'is' to 'fos'
                    fos.write(ins.read())
                }

                fos.close()
                ins.close()
            }
        }

        if (replace) {
            System.setProperty("java.library.path", paths.joinToString(File.pathSeparator))
        } else {
            val newPath = "${lp}${File.pathSeparator}${tmpDir.absolutePath}"
            logger.debug("New java.library.path is $newPath")
            System.setProperty("java.library.path", newPath)
        }

        val fieldSysPath = ClassLoader::class.java.getDeclaredField("sys_paths")
        fieldSysPath.setAccessible(true)
        fieldSysPath.set(null, null)

        logger.debug("java.library.path is now ${System.getProperty("java.library.path")}")
    }

    init {

        logger.debug("Native JARs for JInput: ${getNativeJars("jinput-platform").joinToString(", ")}")
        extractLibrariesFromJar(getNativeJars("jinput-platform"))

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
    private fun getMask(e: InputEvent, initial: Int = 0): Int {
        val modifiers = e.modifiers
        var mask = initial

        /*
		 * For scrolling AWT uses the SHIFT_DOWN_MASK to indicate horizontal scrolling.
		 * We keep track of whether the SHIFT key was actually pressed for disambiguation.
		 */
        if (modifiers and GLFW_MOD_SHIFT == 1)
            mask = mask or (1 shl 6)

        /*
		 * On OS X AWT sets the META_DOWN_MASK to for right clicks. We keep
		 * track of whether the META key was actually pressed for
		 * disambiguation.
		 */
        if (modifiers and GLFW_MOD_ALT == 1) {
            mask = mask or (1 shl 8)
        }

        if (modifiers and GLFW_MOD_SUPER == 1) {
            System.err.println("Windows key not supported")
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
    fun mouseMoved(e: MouseEvent) {
        update()

        mouseX = e.getX()
        mouseY = e.getY()

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
                click.behaviour.click(x, y)
            }
        }
    }

    /**
     * Called when the mouse wheel is moved
     *
     * @param[e] The incoming mouse event
     */
    fun mouseWheelMoved(e: MouseEvent) {
        update()

        val mask = getMask(e)
        val x = e.x
        val y = e.y
        val wheelRotation = e.rotation

        /*
		 * AWT uses the SHIFT_DOWN_MASK to indicate horizontal scrolling. We
		 * keep track of whether the SHIFT key was actually pressed for
		 * disambiguation. However, we can only detect horizontal scrolling if
		 * the SHIFT key is not pressed. With SHIFT pressed, everything is
		 * treated as vertical scrolling.
		 */
        val exShiftMask = e.getModifiers() and InputEvent.SHIFT_MASK != 0
        val isHorizontal = !shiftPressed && exShiftMask && wheelRotation[1] == 0.0f

        for (scroll in scrolls) {
            if (scroll.buttons.matches(mask, pressedKeys)) {
                if(isHorizontal) {
                    scroll.behaviour.scroll(wheelRotation[0].toDouble(), isHorizontal, x, y)
                } else {
                    scroll.behaviour.scroll(wheelRotation[1].toDouble(), isHorizontal, x, y)
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

        val mask = getMask(e, initial = 1024)
        val x = e.x
        val y = e.y

        for (drag in buttonDrags) {
            logger.trace("$pressedKeys vs ${drag.buttons.pressedKeys}")
            logger.trace("$mask vs ${drag.buttons.mask}")
            if (drag.buttons.matches(mask, pressedKeys)) {
                logger.trace("yay! match!")
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
    fun keyPressed(e: KeyEvent) {
        update()

        logger.trace("Key pressed: ${e.keyCode}")
        /*if (e.modifiers and GLFW_MOD_SHIFT == 1) {
            shiftPressed = true
        } else if (e.modifiers and GLFW_MOD_ALT == 1) {
            metaPressed = true
        } else if (e.modifiers and GLFW_MOD_CONTROL == 1) {
            winPressed = true
        }*/
        if (e.keyCode.toInt() != GLFW_KEY_LEFT_ALT &&
            e.keyCode.toInt() != GLFW_KEY_LEFT_CONTROL &&
            e.keyCode.toInt() != GLFW_KEY_LEFT_SHIFT) {
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
            pressedKeys.remove(e.keyCode.toInt())

            for (drag in activeKeyDrags)
                drag.behaviour.end(mouseX, mouseY)
            activeKeyDrags.clear()
        }
    }

    /**
     * Called when the window lost focus. Clears pressed keys
     *
     * @param[e] The incoming window update event
     */
    fun windowLostFocus(e: WindowEvent?) {
        pressedKeys.clear()
        shiftPressed = false
        metaPressed = false
        winPressed = false
    }

    /**
     * Called when a window regains focus, clears pressed keys
     *
     * @param[e] The incoming window update event
     */
    fun windowGainedFocus(e: WindowEvent?) {
        pressedKeys.clear()
        shiftPressed = false
        metaPressed = false
        winPressed = false
    }

    /**
     * Called when a new controller is added
     *
     * @param[event] The incoming controller event
     */
    override fun controllerAdded(event: ControllerEvent?) {
        if(controller == null && event != null && event.controller.type == Controller.Type.GAMEPAD) {
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
        if(event != null && controller != null) {
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
        for(gamepad in gamepads) {
            if(event.component.isAnalog && Math.abs(event.component.pollData) < CONTROLLER_DOWN_THRESHOLD) {
                logger.trace("${event.component.identifier} over threshold, removing")
                controllerAxisDown.put(event.component.identifier, 0.0f)
            } else {
                controllerAxisDown.put(event.component.identifier, event.component.pollData)
            }

            if(gamepad.behaviour.axis.contains(event.component.identifier)) {
                gamepad.behaviour.axisEvent(event.component.identifier, event.component.pollData)
            }
        }
    }
}
