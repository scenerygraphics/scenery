package scenery.controls

import com.jogamp.newt.event.*
import gnu.trove.map.hash.TIntLongHashMap
import gnu.trove.set.hash.TIntHashSet
import net.java.games.input.*
import org.scijava.ui.behaviour.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scenery.controls.behaviours.GamepadBehaviour
import java.awt.Toolkit
import java.io.File
import java.nio.file.Files
import java.util.*
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class JOGLMouseAndKeyHandler : MouseListener, KeyListener, WindowListener, WindowAdapter(), ControllerListener {
    protected var logger: Logger = LoggerFactory.getLogger("JOGLMouseAndKeyHandler")

    private var controller: Controller? = null

    private var controllerThread: Thread? = null

    private val CONTROLLER_HEARTBEAT = 10L

    private val DOUBLE_CLICK_INTERVAL = getDoubleClickInterval()

    private val OSX_META_LEFT_CLICK = InputEvent.BUTTON1_MASK or InputEvent.BUTTON3_MASK or InputEvent.META_MASK

    private val OSX_ALT_LEFT_CLICK = InputEvent.BUTTON1_MASK or InputEvent.BUTTON2_MASK or InputEvent.ALT_MASK

    private val OSX_ALT_RIGHT_CLICK = InputEvent.BUTTON3_MASK or InputEvent.BUTTON2_MASK or InputEvent.ALT_MASK or InputEvent.META_MASK

    private fun getDoubleClickInterval(): Int {
        val prop = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval")

        if (prop == null){
            return 200
        } else {
            return prop as Int
        }
    }

    private var inputMap: InputTriggerMap? = null

    private var behaviourMap: BehaviourMap? = null

    private var inputMapExpectedModCount: Int = 0

    private var behaviourMapExpectedModCount: Int = 0

    private var gamepadState: Event? = null

    private fun getNativeJars(searchName: String): List<String> {
        val classpath = System.getProperty("java.class.path")

        return classpath.split(File.pathSeparator).filter { it.contains(searchName) }
    }

    private fun extractLibrariesFromJar(paths: List<String>, replace: Boolean = false) {
        val lp = System.getProperty("java.library.path")
        val tmpDir = Files.createTempDirectory("scenery-natives-tmp").toFile()

        paths.forEach {
            val jar = java.util.jar.JarFile(it);
            val enumEntries = jar.entries();

            while (enumEntries.hasMoreElements()) {
                val file = enumEntries.nextElement()
                val f = java.io.File(tmpDir.absolutePath + java.io.File.separator + file.getName());

                if (file.isDirectory()) { // if its a directory, create it
                    f.mkdir();
                    continue;
                }

                val ins = jar.getInputStream(file); // get the input stream
                val fos = java.io.FileOutputStream(f);
                while (ins.available() > 0) {  // write contents of 'is' to 'fos'
                    fos.write(ins.read())
                }

                fos.close();
                ins.close();
            }
        }

        if(replace) {
            System.setProperty("java.library.path", paths.joinToString(File.pathSeparator))
        } else {
            val newPath = "${lp}${File.pathSeparator}${tmpDir.absolutePath}"
            logger.debug("New java.library.path is $newPath")
            System.setProperty("java.library.path", newPath)
        }

        val fieldSysPath = ClassLoader::class.java.getDeclaredField( "sys_paths" );
        fieldSysPath.setAccessible( true );
        fieldSysPath.set( null, null );

        logger.debug("java.library.path is now ${System.getProperty("java.library.path")}")
    }

    init {

        logger.debug("Native JARs for JInput: ${getNativeJars("jinput-platform").joinToString(", ")}")
        extractLibrariesFromJar(getNativeJars("jinput-platform"))

        ControllerEnvironment.getDefaultEnvironment().controllers.forEach {
            if(it.type == Controller.Type.STICK || it.type == Controller.Type.GAMEPAD) {
                this.controller = it
                logger.info("Added gamepad controller: $it")
            }
        }

        controllerThread = thread {
            while(true) {
                controller?.let {
                    controller!!.poll()

                    val event_queue = controller!!.eventQueue
                    val event = Event()

                    while (event_queue.getNextEvent(event)) {
                        gamepadState = event
                        controllerEvent(event)
                    }
                }

                Thread.sleep(this.CONTROLLER_HEARTBEAT)
            }
        }
    }

    fun setInputMap(inputMap: InputTriggerMap) {
        this.inputMap = inputMap
        inputMapExpectedModCount = inputMap.modCount() - 1
    }

    fun setBehaviourMap(behaviourMap: BehaviourMap) {
        this.behaviourMap = behaviourMap
        behaviourMapExpectedModCount = behaviourMap.modCount() - 1
    }

    /*
	 * Managing internal behaviour lists.
	 *
	 * The internal lists only contain entries for Behaviours that can be
	 * actually triggered with the current InputMap, grouped by Behaviour type,
	 * such that hopefully lookup from the event handlers is fast,
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
                } else if (behaviour is ClickBehaviour) {
                    val clickEntry = BehaviourEntry<ClickBehaviour>(buttons, behaviour)
                    if (buttons.isKeyTriggered())
                        keyClicks.add(clickEntry)
                    else
                        buttonClicks.add(clickEntry)
                } else if (behaviour is ScrollBehaviour) {
                    val scrollEntry = BehaviourEntry<ScrollBehaviour>(buttons, behaviour)
                    scrolls.add(scrollEntry)
                } else if (behaviour is GamepadBehaviour) {
                    val gamepadEntry = BehaviourEntry<GamepadBehaviour>(buttons, behaviour)
                    gamepads.add(gamepadEntry)
                }
            }
        }
    }



    /*
	 * Event handling. Forwards to registered behaviours.
	 */


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
        if (e is MouseEvent && ((e as MouseEvent).rotation[0] < 0.001f || (e as MouseEvent).rotation[1] < 0.001f)) {
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

        if (e is MouseEvent && (e as MouseEvent).eventType == MouseEvent.EVENT_MOUSE_WHEEL_MOVED) {
            mask = mask or InputTrigger.SCROLL_MASK
            mask = mask and (1 shl 10).inv()
        }

        return mask
    }
    override fun mouseMoved(e: MouseEvent) {
        update()

        mouseX = e.getX()
        mouseY = e.getY()

        for (drag in activeKeyDrags)
            drag.behaviour.drag(mouseX, mouseY)
    }

    override fun mouseEntered(e: MouseEvent) {
        update()
    }

    override fun mouseClicked(e: MouseEvent) {
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

    override fun mouseWheelMoved(e: MouseEvent) {
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

    override fun mouseReleased(e: MouseEvent) {
        update()

        val x = e.x
        val y = e.y

        for (drag in activeButtonDrags)
            drag.behaviour.end(x, y)
        activeButtonDrags.clear()
    }

    override fun mouseDragged(e: MouseEvent) {
        update()

        val x = e.x
        val y = e.y

        for (drag in activeButtonDrags) {
            drag.behaviour.drag(x, y)
        }
    }

    override fun mouseExited(e: MouseEvent) {
        update()
    }

    override fun mousePressed(e: MouseEvent) {
        update()

        val mask = getMask(e)
        val x = e.x
        val y = e.y

        for (drag in buttonDrags) {
            if (drag.buttons.matches(mask, pressedKeys)) {
                drag.behaviour.init(x, y)
                activeButtonDrags.add(drag)
            }
        }
    }

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

            for (click in keyClicks) {
                if (click.buttons.matches(mask, pressedKeys) || doubleClick && click.buttons.matches(doubleClickMask, pressedKeys)) {
                    click.behaviour.click(mouseX, mouseY)
                }
            }
        }
    }

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

    override fun windowRepaint(e: WindowUpdateEvent?) {
    }

    override fun windowDestroyed(e: WindowEvent?) {
    }

    override fun windowDestroyNotify(e: WindowEvent?) {
    }

    override fun windowLostFocus(e: WindowEvent?) {
        pressedKeys.clear()
        shiftPressed = false
        metaPressed = false
        winPressed = false
    }

    override fun windowMoved(e: WindowEvent?) {
    }

    override fun windowResized(e: WindowEvent?) {
    }

    override fun windowGainedFocus(e: WindowEvent?) {
        pressedKeys.clear()
        shiftPressed = false
        metaPressed = false
        winPressed = false
    }

    override fun controllerAdded(event: ControllerEvent?) {
        if(controller == null && event != null && event.controller.type == Controller.Type.GAMEPAD) {
            logger.info("Adding controller ${event.controller}")
            this.controller = event.controller
        }
    }

    override fun controllerRemoved(event: ControllerEvent?) {
        if(event != null && controller != null) {
            logger.info("Controller removed: ${event.controller}")

            controller = null
        }
    }

    fun controllerEvent(event: Event) {
        logger.debug("CE: ${event.component.identifier}=${event.value}")

        for(gamepad in gamepads) {
            if(gamepad.behaviour.axis.contains(event.component.identifier)) {
                gamepad.behaviour.axisEvent(event.component.identifier, event.value)
            }
        }
    }
}