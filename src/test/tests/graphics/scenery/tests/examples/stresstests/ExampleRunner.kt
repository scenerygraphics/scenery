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
import java.util.concurrent.*
import kotlin.test.assertFalse
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds
import kotlin.time.minutes

/**
 * Experimental test runner that saves screenshots of all discovered tests.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
@ExperimentalTime
class ExampleRunner {
    val logger by LazyLogger()

    private var failure = false

    private var maxRuntimePerTest = System.getProperty("scenery.ExampleRunner.maxRuntimePerTest", "5").toInt().minutes

    private inner class LoggingThreadPoolExecutor(
        corePoolSize: Int,
        maximumPoolSize: Int,
        keepAliveTime: Long,
        unit: TimeUnit,
        workQueue: BlockingQueue<Runnable>,
    ): ThreadPoolExecutor(
        corePoolSize,
        maximumPoolSize,
        keepAliveTime,
        unit,
        workQueue
    ) {
        override fun afterExecute(r: Runnable, t: Throwable?) {
            var throwable: Throwable? = t
            super.afterExecute(r, t)

            if (throwable != null && r is Future<*> && (r as Future<*>).isDone) {
                try {
                    val result = (r as Future<*>).get()
                } catch (ce: CancellationException) {
                    throwable = ce
                } catch (ee: ExecutionException) {
                    throwable = ee.cause
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }

            if(throwable != null) {
                logger.error("Exception in thread: $throwable")
                failure = true
            }
        }
    }


    /**
     * Runs all examples in the class path and bails out on the first exception encountered.
     * Examples are run using both renderers and stereo and non-stereo render paths. Examples
     * are run in random order.
     */
    @Test fun runAllExamples() {
        val reflections = Reflections("graphics.scenery.tests")

        // blacklist contains examples that require user interaction or additional devices
        val blocklist = mutableListOf(
            "LocalisationExample",
            "SwingTexturedCubeExample",
            "TexturedCubeJavaApplication",
            "XwingLiverExample",
            "VRControllerExample",
            "EyeTrackingExample",
            "ARExample",
            "ReaderExample",
            "BDVExample",
            "BigAndSmallVolumeExample",
            "VolumeSamplingExample",
            "SwingTexturedCubeExample",
            "VideoRecordingExample"
        )

        blocklist.addAll(System.getProperty("scenery.ExampleRunner.Blocklist", "").split(","))
        val allowedTests = System.getProperty("scenery.ExampleRunner.AllowedTests")?.split(",")

        // find all basic and advanced examples, exclude blacklist
        val examples = reflections
            .getSubTypesOf(SceneryBase::class.java)
            .filter { !it.canonicalName.contains("stresstests") && !it.canonicalName.contains("cluster") }
            .filter { !blocklist.contains(it.simpleName) }.toMutableList()
            .filter { allowedTests?.contains(it.simpleName) ?: true }

        val rendererProperty = System.getProperty("scenery.Renderer")
        val renderers = rendererProperty?.split(",") ?: when(ExtractsNatives.getPlatform()) {
            ExtractsNatives.Platform.WINDOWS,
            ExtractsNatives.Platform.LINUX -> listOf("VulkanRenderer", "OpenGLRenderer")
            ExtractsNatives.Platform.MACOS -> listOf("OpenGLRenderer")
            ExtractsNatives.Platform.UNKNOWN -> {
                logger.error("Don't know what to do on this platform, sorry."); return
            }
        }

        val configurations = listOf("DeferredShading.yml", "DeferredShadingStereo.yml")

        val directoryName = System.getProperty("scenery.ExampleRunner.OutputDir") ?: "ExampleRunner-${SystemHelpers.formatDateTime(delimiter = "_")}"
        Files.createDirectory(Paths.get(directoryName))

        logger.info("ExampleRunner: Running ${examples.size} examples with ${configurations.size} configurations. Memory: ${Runtime.getRuntime().freeMemory().toFloat()/1024.0f/1024.0f}M/${Runtime.getRuntime().totalMemory().toFloat()/1024.0f/1024.0f}/${Runtime.getRuntime().maxMemory().toFloat()/1024.0f/1024.0f}M (free/total/max) available.")
        System.setProperty("scenery.RandomSeed", "31337")

        renderers.shuffled().forEach { renderer ->
            System.setProperty("scenery.Renderer", renderer)

            configurations.shuffled().forEach { config ->
                System.setProperty("scenery.Renderer.Config", config)
                val executor = LoggingThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    LinkedBlockingQueue<Runnable>()
                )

                val rendererDirectory = "$directoryName/$renderer-${config.substringBefore(".")}"
                Files.createDirectory(Paths.get(rendererDirectory))

                examples.shuffled().forEachIndexed { i, example ->
                    var runtime = 0.milliseconds

                    logger.info("Running ${example.simpleName} with $renderer ($i/${examples.size}) ...")
                    logger.info("Memory: ${Runtime.getRuntime().freeMemory().toFloat()/1024.0f/1024.0f}M/${Runtime.getRuntime().totalMemory().toFloat()/1024.0f/1024.0f}/${Runtime.getRuntime().maxMemory().toFloat()/1024.0f/1024.0f}M (free/total/max) available.")

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

                        while (instance.running && !future.isCancelled && !failure) {
                            if(runtime > maxRuntimePerTest) {
                                future.cancel(true)
                                logger.error("Maximum runtime of $maxRuntimePerTest exceeded, aborting test run.")
                                failure = true
                            }

                            runtime += 200.milliseconds
                            Thread.sleep(200)
                        }

                        future.get()
                    } catch (e: ThreadDeath) {
                        logger.info("JOGL threw ThreadDeath")
                    }

                    logger.info("${example.simpleName} closed ($renderer ran ${i + 1}/${examples.size} so far).")

                    assertFalse(failure, "ExampleRunner aborted due to exceptions in tests or exceeding maximum per-test runtime of $maxRuntimePerTest.")
                }
            }
        }
    }
}
