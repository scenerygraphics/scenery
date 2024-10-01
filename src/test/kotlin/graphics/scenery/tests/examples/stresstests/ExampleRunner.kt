package graphics.scenery.tests.examples.stresstests

import graphics.scenery.SceneryBase
import graphics.scenery.SceneryElement
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.utils.ExtractsNatives
import graphics.scenery.utils.lazyLogger
import graphics.scenery.utils.SystemHelpers
import io.github.classgraph.ClassGraph
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@RunWith(Parameterized::class)
class ExampleRunner(
    private val clazz: Class<*>,
    private val clazzName: String,
    private val renderer: String,
    private val pipeline: String
) {
    private val logger by lazyLogger()

    @Test
    fun runExample() = runBlocking {
        logger.info("Running scenery example ${clazz.simpleName} with renderer $renderer and pipeline $pipeline")
        var runtime = 0.milliseconds

        logger.info("Memory: ${Runtime.getRuntime().freeMemory().toFloat()/1024.0f/1024.0f}M/${Runtime.getRuntime().totalMemory().toFloat()/1024.0f/1024.0f}/${Runtime.getRuntime().maxMemory().toFloat()/1024.0f/1024.0f}M (free/total/max) available.")

        System.setProperty(Renderer.HEADLESS_PROPERTY_NAME, "true")
        System.setProperty("scenery.Renderer", renderer)
        System.setProperty("scenery.Renderer.Config", pipeline)

        val rendererDirectory = "$directoryName/$renderer-${pipeline.substringBefore(".")}"
        val instance: SceneryBase = clazz.getConstructor().newInstance() as SceneryBase
        var failure = false

        val handler = CoroutineExceptionHandler { _, e ->
            logger.error("${clazz.simpleName}: Received exception $e")
            logger.error("Stack trace: ${e.stackTraceToString()}")

            failure = true
            // we fail very hard here to prevent process clogging the CI
            exitProcess(-1)
        }

        // re-seed scenery's PRNG to the seed given via system property
        Random.reseed()

        val exampleRunnable = GlobalScope.launch(handler) {
            instance.assertions[SceneryBase.AssertionCheckPoint.BeforeStart]?.forEach {
                it.invoke()
            }
            instance.main()
        }

        while (!instance.running || !instance.sceneInitialized() || instance.hub.get(SceneryElement.Renderer) == null) {
            delay(200)
        }
        val r = (instance.hub.get(SceneryElement.Renderer) as Renderer)

        while(!r.firstImageReady) {
            delay(200)
        }

        delay(3000)
        r.screenshot("$rendererDirectory/${clazz.simpleName}.png", overwrite = true)
        Thread.sleep(2000)

        logger.info("Sending close to ${clazz.simpleName}")
        instance.close()
        instance.assertions[SceneryBase.AssertionCheckPoint.AfterClose]?.forEach {
            it.invoke()
        }

        while (instance.running && !failure) {
            if (runtime > maxRuntimePerTest) {
                exampleRunnable.cancelAndJoin()
                logger.error("Maximum runtime of $maxRuntimePerTest exceeded, aborting test run.")
                failure = true
            }

            runtime += 200.milliseconds
            delay(200)
        }

        if(failure) {
            exampleRunnable.cancelAndJoin()
        } else {
            exampleRunnable.join()
        }

        logger.info("${clazz.simpleName} closed.")

        assertFalse(failure, "ExampleRunner aborted due to exceptions in tests or exceeding maximum per-test runtime of $maxRuntimePerTest.")
    }

    companion object {
        private val logger by lazyLogger()

        var maxRuntimePerTest =
            System.getProperty("scenery.ExampleRunner.maxRuntimePerTest", "5").toInt().minutes

        // blacklist contains examples that require user interaction or additional devices
        val blocklist = mutableListOf(
            // these examples don't work in headless mode
            "SwingTexturedCubeExample",
            "TexturedCubeJavaExample",
            "SettingsEditorExample",
            "TransferFunctionEditorExample",
            // these examples need additional hardware
            "VRControllerExample",
            "VRControllerAdvancedExample",
            "VRSideChainsExample",
            "VRVolumeCroppingExample",
            "EyeTrackingExample",
            "ARExample",
            // these examples require user input and/or files
            "LocalisationExample",
            "ReaderExample",
            "BigAndSmallVolumeExample",
            "CroppingExample",
            "VolumeSamplingExample",
            "VideoRecordingExample",
            "NetworkVolumeExample",
            // these examples don't render anything
            "AttributesExample",
            "DFTExample",
            "DFTMDExample"
        ) + System.getProperty("scenery.ExampleRunner.Blocklist", "").split(",")

        val allowedTests = System.getProperty("scenery.ExampleRunner.AllowedTests")?.split(",")

        val testGroup: String = System.getProperty("scenery.ExampleRunner.TestGroup", "basic")

        // find all basic and advanced examples, exclude blacklist
        val examples = ClassGraph()
            .acceptPackages("graphics.scenery.tests.examples")
            .enableClassInfo()
            .scan()
            .getSubclasses(SceneryBase::class.java)
            .loadClasses()
            .filter { !it.canonicalName.contains("stresstests")
                && !it.canonicalName.contains("cluster")
                && it.name.substringBeforeLast(".").contains(testGroup)
                && !blocklist.contains(it.simpleName)
                && (allowedTests?.contains(it.simpleName) ?: true)
            }

        val renderers = System.getProperty("scenery.Renderer")?.split(",") ?: when(ExtractsNatives.getPlatform()) {
            ExtractsNatives.Platform.WINDOWS,
            ExtractsNatives.Platform.LINUX -> listOf("VulkanRenderer", "OpenGLRenderer")
            ExtractsNatives.Platform.MACOS -> listOf("OpenGLRenderer")
            ExtractsNatives.Platform.UNKNOWN -> {
                throw UnsupportedOperationException("Don't know what to do on this platform, sorry.")
            }
        }

        val configurations = System.getProperty("scenery.ExampleRunner.Configurations")?.split(",") ?: listOf("DeferredShading.yml", "DeferredShadingStereo.yml")

        val directoryName = System.getProperty("scenery.ExampleRunner.OutputDir") ?: "ExampleRunner-${SystemHelpers.formatDateTime(delimiter = "_")}"

        @Parameterized.Parameters(name = "{index}: {1} ({2}/{3})")
        @JvmStatic
        fun availableExamples(): Collection<Array<*>> {
            val configs = examples.flatMap { example ->
                renderers.flatMap { renderer ->
                    configurations.map { config ->
                        logger.debug("Adding ${example.simpleName} with $renderer/$config")
                        arrayOf(example, example.simpleName, renderer, config)
                    }
                }
            }

            logger.info("Discovered ${renderers.size} renderers with ${configurations.size} different pipelines, for ${examples.size} examples from group $testGroup, resulting in ${configs.size} run configurations.")

            return configs
        }

        @Parameterized.BeforeParam
        @JvmStatic
        fun createOutputDirectory() {
            renderers.forEach { renderer ->
                configurations.forEach { config ->
                    val rendererDirectory = "$directoryName/$renderer-${config.substringBefore(".")}"
                    Files.createDirectories(Paths.get(rendererDirectory))
                }
            }
        }
    }

}
