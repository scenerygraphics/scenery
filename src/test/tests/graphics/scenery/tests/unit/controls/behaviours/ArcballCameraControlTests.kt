package graphics.scenery.tests.unit.controls.behaviours

import org.joml.Vector3f
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.Hub
import graphics.scenery.Scene
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.tests.unit.backends.FauxRenderer
import graphics.scenery.utils.LazyLogger
import org.junit.Assert
import org.junit.Test
import java.io.FileWriter
import java.io.PrintWriter
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


/**
 * Tests for [ArcballCameraControl]
 *
 * @author Aryaman Gupta <aryaman1994@gmail.com>
 */
class ArcballCameraControlTests {
    private val logger by LazyLogger()
    private val seqLength = 100

    private fun prepareArcballCameraControl(scene: Scene) : ArcballCameraControl {
        var hub: Hub = Hub()

        val renderer = FauxRenderer(hub, scene)
        hub.add(renderer)

        val window: SceneryWindow = renderer.window

        val target = scene.findObserver()?.target ?: Vector3f(0.0f)

        val arcballCameraControl = ArcballCameraControl("TestController", { scene.findObserver() }, 512, 512, target)
        return arcballCameraControl
    }

    private fun prepareTestFile() {
        val scene = Scene()

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = Vector3f(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512, 512)
            active = true
            scene.addChild(this)
        }

        val arcballCameraControl = prepareArcballCameraControl(scene)

        val path = "src/test/resources/graphics/scenery/tests/unit/controls/behaviours/arcballSequence.json"
        val fileWriter = FileWriter(path, true)
        val printWriter = PrintWriter(fileWriter)

        val initX = kotlin.random.Random.nextInt(0, 512)
        val initY = kotlin.random.Random.nextInt(0, 512)

        var X = initX
        var Y = initY

        arcballCameraControl.init(initX, initY)

        /*TODO: Generate multiple test sequences*/
        for(i in 1..seqLength) {
            printWriter.println("[$i, $X, $Y, ${cam.rotation.x}, ${cam.rotation.y}, ${cam.rotation.z}, ${cam.rotation.w}, ${cam.position.x()}, ${cam.position.y()}, ${cam.position.z()}]")
            val deltaX = kotlin.random.Random.nextInt(-6, 6)
            val deltaY = kotlin.random.Random.nextInt(-6, 6)

            if(X + deltaX >= 512) {
                X = 511
            }
            else if (X + deltaX < 0) {
                X = 0
            }
            else {
                X += deltaX
            }

            if(Y + deltaY >= 512) {
                Y = 511
            }
            else if (Y + deltaY < 0) {
                Y = 0
            }
            else {
                Y += deltaY
            }
            arcballCameraControl.drag(X, Y)
        }
        arcballCameraControl.end(X, Y)
        printWriter.close()
    }

    /**
     * Tests initialisation of [ArcballCameraControl].
     */
    @Test
    fun testInitialisation() {
        logger.info("Testing ArcballCameraControl initialisation...")
        val scene = Scene()
        val arcballCameraControl = prepareArcballCameraControl(scene)
        assertNotNull(arcballCameraControl)
    }

    /**
     * Tests arcball interaction with a set of pre-created samples.
     */
    @Test
    fun testArcballControl() {
        logger.info("Testing ArcballCameraControl...")
        val scene = Scene()

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = Vector3f(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512, 512)
            active = true
            scene.addChild(this)
        }

        val arcballCameraControl = prepareArcballCameraControl(scene)

        val samplesStream = GZIPInputStream(ArcballCameraControlTests::class.java.getResourceAsStream("arcballSequence.json.gz"))
        samplesStream.reader().readLines().forEach { line ->
            val numbers = line.replace("[", "").replace("]", "").split(",").map { it.toFloat() }
            if(numbers[0].toInt() == 1) {
                arcballCameraControl.init(numbers[1].toInt(), numbers[2].toInt())
            }
            else {
                arcballCameraControl.drag(numbers[1].toInt(), numbers[2].toInt())
            }

            if(numbers[0].toInt() == seqLength) {
                arcballCameraControl.end(numbers[1].toInt(), numbers[2].toInt())
            }

            Assert.assertEquals("Computed camera rotation is incorrect", numbers[3], cam.rotation.x, 0.00001f)
            Assert.assertEquals("Computed camera rotation is incorrect", numbers[4], cam.rotation.y, 0.00001f)
            Assert.assertEquals("Computed camera rotation is incorrect", numbers[5], cam.rotation.z, 0.00001f)
            Assert.assertEquals("Computed camera rotation is incorrect", numbers[6], cam.rotation.w, 0.00001f)
            Assert.assertEquals("Computed camera rotation is incorrect", numbers[7], cam.position.x(), 0.00001f)
            Assert.assertEquals("Computed camera rotation is incorrect", numbers[8], cam.position.y(), 0.00001f)
            Assert.assertEquals("Computed camera rotation is incorrect", numbers[9], cam.position.z(), 0.00001f)
        }
    }
}
