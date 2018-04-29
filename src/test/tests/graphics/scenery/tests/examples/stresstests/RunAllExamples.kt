package graphics.scenery.tests.examples.stresstests

import graphics.scenery.Hub
import graphics.scenery.SceneryBase
import graphics.scenery.SceneryElement
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.LazyLogger
import org.junit.Test
import org.lwjgl.glfw.GLFW.glfwTerminate
import org.reflections.Reflections
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/**
 * Experimental test runner that saves screenshots of all discovered tests.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class RunAllExamples {
    val logger by LazyLogger()

    @Test fun runAllExamples() {
        val reflections = Reflections("graphics.scenery.tests")

        // blacklist contains examples that require user interaction or additional devices
        val blacklist = listOf("LocalisationExample",
            "TexturedCubeJavaApplication",
            "JavaFXTexturedCubeApplication",
            "XwingLiverExample",
            "VRControllerExample",
            "EyeTrackingExample",
            "ARExample",
            "SponzaExample")

        // find all basic and advanced examples, exclude blacklist
        val examples = reflections
            .getSubTypesOf(SceneryBase::class.java)
            .filter { !it.canonicalName.contains("stresstests") && !it.canonicalName.contains("cluster") }
            .filter { !blacklist.contains(it.simpleName) }

        val renderers = listOf("VulkanRenderer", "OpenGLRenderer")

        val directoryName = "RAE-${SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(Date())}"
        Files.createDirectory(Paths.get(directoryName))


        renderers.forEach { renderer ->
            System.setProperty("scenery.Renderer", renderer)
            val executor = Executors.newSingleThreadExecutor()
            val rendererDirectory = "$directoryName/$renderer"
            Files.createDirectory(Paths.get(rendererDirectory))

            examples.sortedBy { Math.random() }.forEach { example ->
                logger.info("Running ${example.simpleName} with $renderer...")

                if(!example.simpleName.contains("JavaFX")) {
                    System.setProperty("scenery.Renderer.Headless", "true")
                }

                val instance = example.getConstructor().newInstance()

                try {
                    val exampleRunnable = Runnable {
                        instance.main()
                    }
                    executor.execute(exampleRunnable)

                    while (!instance.running || !instance.sceneInitialized()) {
                        Thread.sleep(200)
                    }

                    Thread.sleep(2000)
                    (instance.hub.get(SceneryElement.Renderer) as Renderer).screenshot("$rendererDirectory/${example.simpleName}.png")
                    Thread.sleep(2000)

                    instance.close()

                } catch(e: ThreadDeath) {
                    logger.info("JOGL threw ThreadDeath")
                }

                while (instance.running) {
                    Thread.sleep(200)
                }

                Hub.cleanHubs()
            }
        }
    }
}
