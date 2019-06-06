package graphics.scenery.tests.unit.controls

import graphics.scenery.Box
import graphics.scenery.Hub
import graphics.scenery.Scene
import graphics.scenery.Settings
import graphics.scenery.controls.InputHandler
import graphics.scenery.tests.unit.backends.FauxRenderer
import graphics.scenery.utils.LazyLogger
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.ui.behaviour.InputTrigger

/**
 * Tests for the input handler
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class InputHandlerTests {
    private val logger by LazyLogger()

    private fun prepareInputHandler(): InputHandler {
        val hub = Hub()
        val settings = Settings(hub)
        hub.add(settings)
        val scene = Scene()
        val box = Box()
        scene.addChild(box)

        val renderer = FauxRenderer(hub, scene)
        hub.add(renderer)

        val inputHandler = InputHandler(scene, renderer, hub)
        inputHandler.useDefaultBindings("doesnotexist.config")

        return inputHandler
    }

    @Test
    fun testInitialisation() {
        logger.info("Testing InputHandler initialisation...")
        val inputHandler = prepareInputHandler()
        assert(inputHandler.behaviourMap.allBindings.isNotEmpty())
    }

    @Test
    fun testAddBehaviour() {
        logger.info("Testing adding a behaviour...")
        val inputHandler = prepareInputHandler()
        inputHandler.addBehaviour("testbehaviour", ClickBehaviour { _, _ ->
            logger.info("Doing nothing")
        })

        assert(inputHandler.getBehaviour("testbehaviour") != null)
    }

    @Test
    fun testTriggerBehaviour() {
        logger.info("Testing triggering a behaviour...")
        var triggered = false
        val inputHandler = prepareInputHandler()
        inputHandler.addBehaviour("testbehaviour", ClickBehaviour { _, _ ->
            triggered = true
        })

        (inputHandler.getBehaviour("testbehaviour") as ClickBehaviour).click(0, 0)
        assert(triggered)
    }

    @Test
    fun testRemoveBehaviour() {
        logger.info("Testing removing a behaviour...")
        val inputHandler = prepareInputHandler()
        inputHandler.addBehaviour("testbehaviour", ClickBehaviour { _, _ ->
            logger.info("Doing nothing")
        })

        inputHandler.removeBehaviour("testbehaviour")
        assert(inputHandler.getBehaviour("testbehaviour") == null)
    }

    @Test
    fun testAddKeybinding() {
        logger.info("Testing adding a key binding...")
        val inputHandler = prepareInputHandler()
        inputHandler.addBehaviour("testbehaviour", ClickBehaviour { _, _ ->
            logger.info("Doing nothing")
        })

        inputHandler.addKeyBinding("testbehaviour", "A")
        assert(inputHandler.getAllBindings().containsKey(InputTrigger.getFromString("A")))
        assert(inputHandler.getAllBindings().entries.any { it.value.contains("testbehaviour") })
    }
}
