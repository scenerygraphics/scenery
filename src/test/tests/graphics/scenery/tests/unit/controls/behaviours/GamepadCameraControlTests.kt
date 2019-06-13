package graphics.scenery.tests.unit.controls.behaviours

import cleargl.GLVector
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.Hub
import graphics.scenery.Scene
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.controls.behaviours.GamepadCameraControl
import graphics.scenery.numerics.Random
import graphics.scenery.tests.unit.backends.FauxRenderer
import graphics.scenery.utils.LazyLogger
import net.java.games.input.Component
import org.junit.Test
import java.io.FileWriter
import java.io.PrintWriter
import java.util.zip.GZIPInputStream
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

/**
 * Tests for [GamepadCameraControl]
 *
 * @author Aryaman Gupta <aryaman1994@gmail.com>
 */
class GamepadCameraControlTests {
    private val logger by LazyLogger()

    private fun prepareGamepadCameraControl(scene: Scene) : GamepadCameraControl {
        var hub: Hub = Hub()

        val renderer = FauxRenderer(hub, scene)
        hub.add(renderer)

        val window: SceneryWindow = renderer.window

        val gamepadCameraControl = GamepadCameraControl("TestController", listOf(Component.Identifier.Axis.X, Component.Identifier.Axis.Y), { scene.findObserver() }, window.width, window.height)
        return gamepadCameraControl
    }

    private fun prepareTestFile() {
        val scene = Scene()

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512.0f, 512.0f)
            active = true
            scene.addChild(this)
        }

        val gamepadCameraControl = prepareGamepadCameraControl(scene)

        val path = "src/test/resources/graphics/scenery/tests/unit/controls/behaviours/cameraRotationSequence.json"
        val fileWriter = FileWriter(path, true)
        val printWriter = PrintWriter(fileWriter)

        for(i in 1..10) {
            val axis = kotlin.random.Random.nextInt(0, 2)
            val rotationVal = Random.randomFromRange(0.0f, 2.0f)

            if(axis == 0) {
                gamepadCameraControl.axisEvent(Component.Identifier.Axis.X, rotationVal)
            }
            else {
                gamepadCameraControl.axisEvent(Component.Identifier.Axis.Y, rotationVal)
            }
            printWriter.println("[$i, $axis, $rotationVal, ${cam.rotation.x}, ${cam.rotation.y}, ${cam.rotation.z}, ${cam.rotation.w}]")
        }
        printWriter.close()
    }

    @Test
    fun testInitialisation() {
        logger.info("Testing GamepadCameraControl initialisation...")
        val scene = Scene()
        val gamepadCameraControl = prepareGamepadCameraControl(scene)
        assertNotNull(gamepadCameraControl)
    }

    @Test
    fun testAxisEvent() {
        //TODO: Create multiple test sequences
        logger.info("Testing triggering an axis event...")
        val scene = Scene()

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512.0f, 512.0f)
            active = true
            scene.addChild(this)
        }

        val gamepadCameraControl = prepareGamepadCameraControl(scene)
        val samplesStream = GZIPInputStream(GamepadCameraControlTests::class.java.getResourceAsStream("cameraRotationSequence.json.gz"))
        samplesStream.reader().readLines().forEach { line ->
            val numbers = line.replace("[", "").replace("]", "").split(",").map { it.toFloat() }
            if(numbers[1].toInt() == 0) {
                gamepadCameraControl.axisEvent(Component.Identifier.Axis.X, numbers[2])
            }
            else {
                gamepadCameraControl.axisEvent(Component.Identifier.Axis.Y, numbers[2])
            }
            assertEquals(numbers[3], cam.rotation.x, "Computed camera rotation is incorrect" )
            assertEquals(numbers[4], cam.rotation.y, "Computed camera rotation is incorrect")
            assertEquals(numbers[5], cam.rotation.z, "Computed camera rotation is incorrect")
            assertEquals(numbers[6], cam.rotation.w, "Computed camera rotation is incorrect")
        }
    }
}
