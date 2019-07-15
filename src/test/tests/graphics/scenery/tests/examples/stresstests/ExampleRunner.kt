package graphics.scenery.tests.examples.stresstests

import graphics.scenery.SceneryBase
import graphics.scenery.SceneryElement
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.ExtractsNatives
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.SystemHelpers
import org.junit.Test
import org.reflections.Reflections
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Experimental test runner that saves screenshots of all discovered tests.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class ExampleRunner {
    val logger by LazyLogger()

    @Test fun runAllExamples() {
        val reflections = Reflections("graphics.scenery.tests")

        // blacklist contains examples that require user interaction or additional devices
        val blacklist = listOf("LocalisationExample",
//            "SwingTexturedCubeExample",
            "TexturedCubeJavaApplication",
            "XwingLiverExample",
            "VRControllerExample",
            "EyeTrackingExample",
            "ARExample",
            "ReaderExample",
            "BDVExample")

        // find all basic and advanced examples, exclude blacklist
        val examples = reflections
            .getSubTypesOf(SceneryBase::class.java)
            .filter { !it.canonicalName.contains("stresstests") && !it.canonicalName.contains("cluster") }
            .filter { !blacklist.contains(it.simpleName) }

        val renderers = when(ExtractsNatives.getPlatform()) {
            ExtractsNatives.Platform.WINDOWS,
            ExtractsNatives.Platform.LINUX -> listOf("VulkanRenderer", "OpenGLRenderer")
            ExtractsNatives.Platform.MACOS -> listOf("OpenGLRenderer")
            ExtractsNatives.Platform.UNKNOWN -> { logger.error("Don't know what to do on this platform, sorry."); return }
        }

        val configurations = listOf("DeferredShading.yml", "DeferredShadingStereo.yml")

        val directoryName = "ExampleRunner-${SystemHelpers.formatDateTime()}"
        Files.createDirectory(Paths.get(directoryName))


        renderers.shuffled().forEach { renderer ->
            System.setProperty("scenery.Renderer", renderer)

            configurations.shuffled().forEach { config ->
                System.setProperty("scenery.Renderer.Config", config)
                val executor = Executors.newSingleThreadExecutor()
                val rendererDirectory = "$directoryName/$renderer-${config.substringBefore(".")}"
                Files.createDirectory(Paths.get(rendererDirectory))

                examples.shuffled().forEachIndexed { i, example ->
                    logger.info("Running ${example.simpleName} with $renderer ($i/${examples.size}) ...")

                    if (!example.simpleName.contains("JavaFX")) {
                        System.setProperty("scenery.Headless", "true")
                    }

                    val instance = example.getConstructor().newInstance()
                    val future: Future<*>

                    try {
                        val exampleRunnable = Runnable {
                            instance.assertions[SceneryBase.AssertionCheckPoint.BeforeStart]?.forEach {
                                it.invoke()
                            }
                            instance.main()
                        }

                        future = executor.submit(exampleRunnable)

                        while (!instance.running || !instance.sceneInitialized() || instance.hub.get(SceneryElement.Renderer) == null) {
                            Thread.sleep(200)
                        }
                        val r = (instance.hub.get(SceneryElement.Renderer) as Renderer)

                        while(!r.firstImageReady) {
                            Thread.sleep(200)
                        }

                        r.screenshot("$rendererDirectory/${example.simpleName}.png")
                        Thread.sleep(2000)

                        logger.info("Sending close to ${example.simpleName}")
                        instance.close()
                        instance.assertions[SceneryBase.AssertionCheckPoint.AfterClose]?.forEach {
                            it.invoke()
                        }

                        while (instance.running) {
                            Thread.sleep(200)
                        }

                        future.get()
                    } catch (e: ThreadDeath) {
                        logger.info("JOGL threw ThreadDeath")
                    }

                    logger.info("${example.simpleName} closed ($renderer ran ${i + 1}/${examples.size} so far).")
                }
            }
        }
    }
}
