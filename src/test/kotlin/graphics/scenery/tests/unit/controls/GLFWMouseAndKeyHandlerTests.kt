package graphics.scenery.tests.unit.controls

import graphics.scenery.Box
import graphics.scenery.Hub
import graphics.scenery.Scene
import graphics.scenery.Settings
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.controls.GLFWMouseAndKeyHandler
import graphics.scenery.controls.GLFWMouseAndKeyHandler.Companion.fakeComponent
import graphics.scenery.controls.InputHandler
import graphics.scenery.tests.unit.backends.FauxRenderer
import graphics.scenery.utils.extensions.toBinaryString
import graphics.scenery.utils.lazyLogger
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.ui.behaviour.DragBehaviour
import org.scijava.ui.behaviour.ScrollBehaviour
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the [GLFWMouseAndKeyHandler].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class GLFWMouseAndKeyHandlerTests {
    private val logger by lazyLogger()

    private fun prepareInputHandler(preparedWindow: SceneryWindow? = null): Triple<InputHandler, Scene, GLFWMouseAndKeyHandler> {
        val h = Hub()
        val settings = Settings(h)
        h.add(settings)
        val scene = Scene()
        val box = Box()
        scene.addChild(box)

        val renderer = FauxRenderer(h, scene, preparedWindow)
        h.add(renderer)

        val inputHandler = InputHandler(scene, renderer, h, forceHandler = GLFWMouseAndKeyHandler::class.java)

        return Triple(inputHandler, scene, inputHandler.handler as GLFWMouseAndKeyHandler)
    }

    /**
     * Tests input handler initialisation.
     */
    @Test
    fun testInitialisation() {
        logger.info("Testing input handler initialisation ...")
        val (inputHandler, scene, glfwHandler) = prepareInputHandler()

        assertNotNull(inputHandler)
        assertNotNull(scene)
        assertNotNull(glfwHandler)
    }

    /**
     * Tests the key press handlers with a series of key events.
     */
    @Test
    fun testKeyPressedHandler() {
        logger.info("Testing input handler key press handlers ...")
        val (inputHandler, _, glfwHandler) = prepareInputHandler()

        repeat(100) {
            val key = Random.nextInt(0x41, 0x5A)
            var keyPressed = false

            // we create a press and release event here, otherwise
            // the keys are assumed to be down simultaneously
            val pressEvent = KeyEvent(
                fakeComponent,
                KeyEvent.KEY_PRESSED,
                System.nanoTime(),
                0,
                key,
                key.toChar(),
                KeyEvent.KEY_LOCATION_UNKNOWN
            )

            val releaseEvent = KeyEvent(
                fakeComponent,
                KeyEvent.KEY_PRESSED,
                System.nanoTime(),
                0,
                key,
                key.toChar(),
                KeyEvent.KEY_LOCATION_UNKNOWN
            )

            val char = KeyEvent.getKeyText(key)

            inputHandler.addBehaviour("keyPressed_$char", ClickBehaviour { _, _ ->
                keyPressed = true
            })
            inputHandler.addKeyBinding("keyPressed_$char", char)

            glfwHandler.keyPressed(pressEvent)
            glfwHandler.keyReleased(releaseEvent)

            assertTrue(keyPressed, "Expected key to be pressed for $key/$key/$char")
        }
    }

    private fun String.toMask(): Int {
        var mask = 0
        if(this.contains("shift")) {
            mask = mask or KeyEvent.SHIFT_DOWN_MASK
        }

        if(this.contains("alt")) {
            mask = mask or KeyEvent.ALT_DOWN_MASK
        }

        if(this.contains("ctrl")) {
            mask = mask or KeyEvent.CTRL_DOWN_MASK
        }

        return mask
    }

    private fun String.toEvents(): List<Pair<KeyEvent, KeyEvent>> {
        val events = mutableListOf<Pair<KeyEvent, KeyEvent>>()
        if(this.contains("shift")) {
            events.add(KeyEvent(
                fakeComponent,
                KeyEvent.KEY_PRESSED,
                System.nanoTime(),
                0,
                KeyEvent.VK_SHIFT,
                KeyEvent.CHAR_UNDEFINED
            ) to KeyEvent(
                fakeComponent,
                KeyEvent.KEY_RELEASED,
                System.nanoTime(),
                0,
                KeyEvent.VK_SHIFT,
                KeyEvent.CHAR_UNDEFINED
            ))
        }

        if(this.contains("alt")) {
            events.add(KeyEvent(
                fakeComponent,
                KeyEvent.KEY_PRESSED,
                System.nanoTime(),
                0,
                KeyEvent.VK_ALT,
                KeyEvent.CHAR_UNDEFINED
            ) to KeyEvent(
                fakeComponent,
                KeyEvent.KEY_RELEASED,
                System.nanoTime(),
                0,
                KeyEvent.VK_ALT,
                KeyEvent.CHAR_UNDEFINED
            ))
        }

        if(this.contains("ctrl")) {
            events.add(KeyEvent(
                fakeComponent,
                KeyEvent.KEY_PRESSED,
                System.nanoTime(),
                0,
                KeyEvent.VK_CONTROL,
                KeyEvent.CHAR_UNDEFINED
            ) to KeyEvent(
                fakeComponent,
                KeyEvent.KEY_RELEASED,
                System.nanoTime(),
                0,
                KeyEvent.VK_CONTROL,
                KeyEvent.CHAR_UNDEFINED
            ))
        }

        if(this.contains("meta")) {
            events.add(KeyEvent(
                fakeComponent,
                KeyEvent.KEY_PRESSED,
                System.nanoTime(),
                0,
                KeyEvent.VK_META,
                KeyEvent.CHAR_UNDEFINED
            ) to KeyEvent(
                fakeComponent,
                KeyEvent.KEY_RELEASED,
                System.nanoTime(),
                0,
                KeyEvent.VK_META,
                KeyEvent.CHAR_UNDEFINED
            ))
        }

        return events
    }

    /**
     * Tests the click handlers with a series of events and modifier keys.
     */
    @Test
    fun testMouseClickedHandlers() {
        logger.info("Testing input handler mouse press handlers with modifiers ...")
        val (inputHandler, _, glfwHandler) = prepareInputHandler()

        repeat(100) { i ->
            val button = Random.nextInt(1, 4)
            var keyPressed = false

            val modifiers = listOf(
                "",
                "ctrl",
                "ctrl alt",
                "ctrl shift",
                "ctrl alt shift",
                "alt shift",
                "alt",
                "shift"
            ).random()

            val modifiersFull = (1 shl 9+button) or modifiers.toMask()
            val modifierEvents = modifiers.toEvents()

            // we create a press and release event here, otherwise
            // the keys are assumed to be down simultaneously
            val clickCount = Random.nextInt(1, 3)
            val point = Pair(Random.nextInt(0, 8192), Random.nextInt(0, 8192))
            val clickEvent = MouseEvent(
                fakeComponent,
                MouseEvent.MOUSE_CLICKED,
                System.nanoTime(),
                modifiersFull,
                point.first,
                point.second,
                clickCount,
                false,
                button)

            val clicks = when(clickCount) {
                1 -> ""
                else -> "double-click "
            }

            val buttonString = when(button) {
                1 -> "button1"
                2 -> "button2"
                else -> "button3"
            }

            logger.debug("Mask is ${modifiersFull.toBinaryString()}, modifiers=$modifiers")

            modifierEvents.forEach {
                glfwHandler.keyPressed(it.first)
            }

            var actualPoint = Pair(0, 0)
            inputHandler.addBehaviour("mousePressed_$i", ClickBehaviour { x, y ->
                actualPoint = Pair(x, y)
                keyPressed = true
            })
            inputHandler.addKeyBinding("mousePressed_$i", "$modifiers $clicks$buttonString")

            logger.debug("Mouse clicked ($clickCount) for $modifiers $clicks $buttonString")
            glfwHandler.mouseClicked(clickEvent)

            modifierEvents.forEach {
                glfwHandler.keyReleased(it.second)
            }

            inputHandler.removeKeyBinding("mousePressed_$i")
            inputHandler.removeBehaviour("mousePressed_$i")

            assertEquals(point, actualPoint, "Expected click points to be equal")
            assertTrue(keyPressed, "Expected mouse to be clicked for $button/count=$clickCount, '$modifiers $clicks$buttonString'")
        }
    }

    /**
     * Tests the mouse wheel handlers with a series of events and modifier keys.
     */
    @Test
    fun testMouseWheelMovedHandlers() {
        logger.info("Testing input handler mouse scroll handlers with modifiers ...")
        val (inputHandler, _, glfwHandler) = prepareInputHandler()

        repeat(100) { i ->
            var scrolled = false

            val modifiers = listOf(
                "",
//                "ctrl",
//                "ctrl alt",
//                "ctrl shift",
//                "ctrl alt shift",
//                "alt shift",
//                "alt",
                "shift").random()

            val modifierEvents = modifiers.toEvents()

            // we create a press and release event here, otherwise
            // the keys are assumed to be down simultaneously
            val point = Pair(Random.nextInt(0, 8192), Random.nextInt(0, 8192))
            val scroll = when(Random.nextInt(0, 3)) {
                0 -> Pair(Random.nextInt(), 0)
                1 -> Pair(0, Random.nextInt())
                else -> Pair(Random.nextInt(), Random.nextInt())
            }

            val scrollEvent = MouseWheelEvent(
                fakeComponent,
                MouseWheelEvent.MOUSE_WHEEL,
                System.nanoTime(),
                modifiers.toMask(),
                point.first,
                point.second,
                0,
                false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL,
                scroll.first,
                0
            )

            logger.debug("Mask is ${modifiers.toMask()}, modifiers=$modifiers")

            modifierEvents.forEach {
                glfwHandler.keyPressed(it.first)
            }

            inputHandler.addBehaviour("mouseScrolled_$i", object: ScrollBehaviour {
                override fun scroll(p0: Double, p1: Boolean, p2: Int, p3: Int) {
                    scrolled = true
                }
            })
            inputHandler.addKeyBinding("mouseScrolled_$i", "$modifiers scroll")

            glfwHandler.mouseWheelMoved(scrollEvent)

            modifierEvents.forEach {
                glfwHandler.keyReleased(it.second)
            }

            assertTrue(scrolled, "Expected scroll event to have happened")
        }
    }

    /**
     * Tests the dragging handlers by creating a series of drag events
     * with different mouse buttons and modifiers.
     */
    @Test
    fun testMouseDraggedHandlers() {
        logger.info("Testing input handler dragging handlers ...")
        val (inputHandler, _, glfwHandler) = prepareInputHandler()

        repeat(100) { i ->
            val button = Random.nextInt(0, 3)
            var dragStart = Pair(0, 0)
            var dragEnd = Pair(0, 0)

            val modifiers = listOf(
                "",
//                "ctrl",
//                "ctrl alt",
//                "ctrl shift",
//                "ctrl alt shift",
//                "alt shift",
//                "alt",
                "shift").random()

            val modifiersFull = 2.0.pow(10.0 + button).toInt() or modifiers.toMask()
            val modifierEvents = modifiers.toEvents()

            // we create a press and release event here, otherwise
            // the keys are assumed to be down simultaneously
            val start = Pair(Random.nextInt(0, 8192), Random.nextInt(0, 8192))
            val end = Pair(Random.nextInt(0, 8192), Random.nextInt(0, 8192))

            val clickEvent = MouseEvent(
                fakeComponent,
                MouseEvent.MOUSE_PRESSED,
                System.nanoTime(),
                modifiersFull,
                start.first,
                start.second,
                1,
                false,
                button
            )

            val moveEvents = (0.. Random.nextInt(5, 10)).map {
                MouseEvent(
                    fakeComponent,
                    MouseEvent.MOUSE_MOVED,
                    System.nanoTime(),
                    modifiersFull,
                    start.first,
                    start.second,
                    0,
                    false,
                    0
                )
            }

            val dragCount = moveEvents.size
            var actualDrags = 0

            val releaseEvent = MouseEvent(
                fakeComponent,
                MouseEvent.MOUSE_RELEASED,
                System.nanoTime(),
                modifiersFull,
                end.first,
                end.second,
                1,
                false,
                button
            )

            val buttonString = when(button) {
                0 -> "button1"
                1 -> "button2"
                else -> "button3"
            }

            logger.debug("Mask is $modifiersFull, modifiers=$modifiers")

            modifierEvents.forEach {
                glfwHandler.keyPressed(it.first)
            }

            inputHandler.addBehaviour("dragged_$i", object: DragBehaviour {
                override fun drag(p0: Int, p1: Int) {
                    actualDrags++
                }

                override fun end(p0: Int, p1: Int) {
                    dragEnd = Pair(p0, p1)
                }

                override fun init(p0: Int, p1: Int) {
                    dragStart = Pair(p0, p1)
                }
            })
            inputHandler.addKeyBinding("dragged_$i", "$modifiers $buttonString")

            glfwHandler.mousePressed(clickEvent)
            moveEvents.forEach { move ->
                glfwHandler.mouseMoved(move)
            }
            glfwHandler.mouseReleased(releaseEvent)

            modifierEvents.forEach {
                glfwHandler.keyReleased(it.second)
            }

            assertEquals(start, dragStart, "Expected dragging start to be")
            assertEquals(end, dragEnd, "Expected dragging end to be")
            assertEquals(dragCount, actualDrags, "Expected $dragCount events to have happened")
        }
    }

    /**
     * Tests that the input handler fails predictably with an unknown window type.
     */
    @Test
    fun testUnsupportedWindowType() {
        logger.info("Testing unsupported window type ...")

        /**
         * Window class scenery has no idea about.
         */
        class BrokenWindow: SceneryWindow()
        val broken = BrokenWindow()

        assertFailsWith(UnsupportedOperationException::class, "Expeceted glfwMouseAndKeyHandler not to support BrokenWindow") {
            prepareInputHandler(preparedWindow = broken)
        }
    }
}
