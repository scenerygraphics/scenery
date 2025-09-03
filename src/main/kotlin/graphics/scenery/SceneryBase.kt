package graphics.scenery

import org.joml.Vector3f
import com.sun.jna.Library
import com.sun.jna.Native
import graphics.scenery.backends.Renderer
import graphics.scenery.compute.OpenCLContext
import graphics.scenery.controls.DTrackButton
import graphics.scenery.controls.GamepadButton
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.controls.behaviours.FPSCameraControl
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import graphics.scenery.repl.REPL
import graphics.scenery.utils.*
import kotlinx.coroutines.*
import org.lwjgl.system.Platform
import org.scijava.Context
import org.scijava.ui.behaviour.Behaviour
import org.scijava.ui.behaviour.ClickBehaviour
import java.io.BufferedReader
import java.io.InputStreamReader
import org.zeromq.ZContext
import java.lang.Boolean.parseBoolean
import java.lang.management.ManagementFactory
import java.net.URL
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.io.path.absolute

/**
 * Base class to use scenery with, keeping the needed boilerplate
 * to a minimum. Inherit from this class for a quick start.
 *
 * @property[applicationName] Name of the application, do not use special chars.
 * @property[windowWidth] Window width of the application window.
 * @property[windowHeight] Window height of the application window.
 * @property[wantREPL] Whether this application should automatically start and display a [REPL].
 * @property[scijavaContext] A potential pre-existing SciJava context, or null.
 *
 * @constructor Creates a new SceneryBase
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

open class SceneryBase @JvmOverloads constructor(var applicationName: String,
                       var windowWidth: Int = 1024,
                       var windowHeight: Int = 1024,
                       val wantREPL: Boolean = true,
                       val scijavaContext: Context? = null) {

    /** The scene used by the renderer in the application */
    protected var scene: Scene = Scene()
    /** REPL for the application, can be initialised in the [init] function */
    protected var repl: REPL? = null
    /** Frame number for counting FPS */
    protected var ticks = 0L
    /** The default renderer for this application, see [Renderer] */
    protected var renderer: Renderer? = null
    /** The Hub used by the application, see [Hub] */
    var hub: Hub = Hub()
    /** Global settings storage */
    protected var settings: Settings = Settings()
    /** ui-behaviour input handler */
    protected var inputHandler: InputHandler? = null
    /** [Statistics] object to collect runtime stats on various routines. */
    protected var stats: Statistics = Statistics(hub)

    /** State variable for registering a new renderer */
    data class NewRendererParameters(val rendererType: String, val hub: Hub,
                                     val applicationName: String, val width: Int, val height: Int,
                                     val scene: Scene, val embedIn: SceneryPanel?, val config: String,
                                     val signal: CountDownLatch? = null)
    protected var registerNewRenderer: NewRendererParameters? = null

    /** Logger for this application, will be instantiated upon first use. */
    protected val logger by lazyLogger(System.getProperty("scenery.LogLevel", "info"))

    /** An optional update function to call during the main loop. */
    var updateFunction: (() -> Any)? = null

    /** Flag to indicate whether this instance is currently running. */
    var running: Boolean = false
        protected set
    /** Total runtime of this instance. */
    var runtime: Float = 0.0f
        protected set

    /** Time step for the main loop */
    var timeStep = 0.01f

    protected var accumulator = 0.0f
    protected var currentTime = System.nanoTime()
    protected var t = 0.0f
    protected var shouldClose: Boolean = false
    protected var gracePeriod = 0

    enum class AssertionCheckPoint { BeforeStart, AfterClose }
    var assertions = hashMapOf<AssertionCheckPoint, ArrayList<() -> Any>>(
        AssertionCheckPoint.BeforeStart to arrayListOf(),
        AssertionCheckPoint.AfterClose to arrayListOf()
    )

    val headless = parseBoolean(System.getProperty(Renderer.HEADLESS_PROPERTY_NAME, "false"))
    val renderdoc = if(System.getProperty("scenery.AttachRenderdoc")?.toBoolean() == true) {
        Renderdoc()
    } else {
        null
    }

    val server = System.getProperty("scenery.Server")?.toBoolean() ?: false
    val serverAddress = System.getProperty("scenery.ServerAddress")

    interface XLib: Library {
        fun XInitThreads()

        companion object {
            val INSTANCE = Native.loadLibrary("X11", XLib::class.java) as XLib
        }
    }

    init {
        // will only be called on Linux, and only if it hasn't been called before.
        xinitThreads()
        // will only run on M1 macOS
        m1supportForjHDF5()
    }

    /**
     * the init function of [SceneryBase], override this in your subclass,
     * e.g. for [Scene] construction and [Renderer] initialisation.
     */
    open fun init() {

    }

    /**
     * Function to contain any custom input setup.
     */
    open fun inputSetup() {

    }

    /**
     * Main routine for [SceneryBase]
     */
    open suspend fun sceneryMain() {


        // wait for renderer
        while(renderer?.initialized == false) {
            delay(100)
        }

        loadInputHandler(renderer)

        val statsRequested = parseBoolean(System.getProperty("scenery.PrintStatistics", "false"))

        // setup additional key bindings, if requested by the user
        inputSetup()

        val startTime = System.nanoTime()

        var frameTime = 0.0f
        var lastFrameTime: Float
        val frameTimes = ArrayDeque<Float>(16)
        val frameTimeKeepCount = 16

        val profiler = hub.get<Profiler>()

        var sceneObjects: Deferred<List<Node>> = GlobalScope.async {
            scene.discover(scene, { n ->
                    n.visible && n.state == State.Ready
            }, useDiscoveryBarriers = true)
                .map { it.spatialOrNull()?.updateWorld(recursive = true, force = false); it }
        }

        while (!shouldClose || gracePeriod > 0) {
            runtime = (System.nanoTime() - startTime) / 1000000f
            settings.set("System.Runtime", runtime)

            scene.update.forEach { it.invoke() }
            sceneObjects = GlobalScope.async {
                scene.discover(scene, { n ->
                        n.visible && n.state == State.Ready
                }, useDiscoveryBarriers = true)
                    .map { it.spatialOrNull()?.updateWorld(recursive = true, force = false); it }
            }
            scene.postUpdate.forEach { it.invoke() }
            val activeCamera = scene.findObserver() ?: continue

            profiler?.begin("Render")
           if (renderer?.managesRenderLoop != false) {
                renderer?.render(activeCamera, sceneObjects.await())
                delay(1)
            } else {
                stats.addTimed("render") { renderer?.render(activeCamera, sceneObjects.await()) ?: 0.0f }
            }
            profiler?.end()

            // only run loop if we are either in standalone mode, or master
            // for details about the interpolation code, see
            // https://gafferongames.com/post/fix_your_timestep/
            if(server || serverAddress == null) {
                val newTime = System.nanoTime()
                lastFrameTime = frameTime
                frameTime = (newTime - currentTime)/1e6f
                if(frameTime > 250.0f) {
                    frameTime = 250.0f
                }

                currentTime = newTime
                accumulator += frameTime

                inputHandler?.window?.pollEvents()

                while(accumulator >= timeStep) {
                    // evolve state
                    t += timeStep
                    accumulator -= timeStep

                    updateFunction?.let { update -> stats.addTimed("Scene.Update", update) }
                }

                val alpha = accumulator/timeStep

                if(frameTimes.size > frameTimeKeepCount) {
                    frameTimes.removeLast()
                }

                if(renderer?.managesRenderLoop == false) {
                    frameTimes.push((alpha * frameTime / 100.0f) + (1.0f - alpha)*(lastFrameTime/100.0f))
                    scene.activeObserver?.deltaT = frameTimes.average().toFloat()
                } else {
                    frameTimes.push((renderer?.lastFrameTime ?: 1.0f) / 100.0f)
                    scene.activeObserver?.deltaT = frameTimes.average().toFloat()
                }
            }

            if (statsRequested && ticks % 100L == 0L) {
                logger.info("\nStatistics:\n=============\n$stats")
            }

            stats.add("loop", frameTime)
            stats.add("ticks", ticks, isTime = false)

            val r = registerNewRenderer
            if(r != null) {
                if(renderer?.managesRenderLoop == false) {
                    renderer?.render(activeCamera, sceneObjects.await())
                }

                when (r.rendererType) {
                    "OpenGLRenderer" -> System.setProperty("scenery.Renderer", "OpenGLRenderer")
                    else -> System.setProperty("scenery.Renderer", "VulkanRenderer")
                }

                val config = r.config
                val embed = r.embedIn

                val width = r.width
                val height = r.height

                val newRenderer = Renderer.createRenderer(hub, applicationName, scene, width, height, embed, config)
                hub.add(SceneryElement.Renderer, newRenderer)
                loadInputHandler(newRenderer)

                renderer = newRenderer

                registerNewRenderer?.signal?.countDown()
                registerNewRenderer = null
            }

            ticks++
            if(gracePeriod > 0) {
                gracePeriod--
            }
        }

        running = false
        inputHandler?.close()
        renderdoc?.close()

        hub.get<NodePublisher>()?.close()
        hub.get<NodeSubscriber>()?.close()

        hub.get<Profiler>()?.close()
        hub.get<Statistics>()?.close()

        hub.get<REPL>()?.close()
    }

    /**
     * Sets up switching between [ArcballCameraControl] and [FPSCameraControl].
     *
     * @param[keybinding] The key to trigger the switching.
     */
    fun setupCameraModeSwitching(keybinding: String = "C") {
        val windowWidth = renderer?.window?.width ?: 512
        val windowHeight = renderer?.window?.height ?: 512

        val target = scene.findObserver()?.target ?: Vector3f(0.0f)
        val inputHandler = (hub.get(SceneryElement.Input) as InputHandler)
        val targetArcball = ArcballCameraControl("mouse_control", { scene.findObserver() }, windowWidth, windowHeight, target)
        val fpsControl = FPSCameraControl("mouse_control", { scene.findObserver() }, windowWidth, windowHeight)

        val toggleControlMode = object : ClickBehaviour {
            var currentMode = "fps"

            override fun click(x: Int, y: Int) {
                if (currentMode.startsWith("fps")) {
                    targetArcball.target = { target }

                    inputHandler.addBehaviour("mouse_control", targetArcball)
                    inputHandler.addBehaviour("scroll_arcball", targetArcball)
                    inputHandler.addKeyBinding("scroll_arcball", "scroll")

                    currentMode = "arcball"
                } else {
                    inputHandler.addBehaviour("mouse_control", fpsControl)
                    inputHandler.removeBehaviour("scroll_arcball")

                    currentMode = "fps"
                }

                logger.info("Switched to $currentMode control")
            }
        }

        inputHandler.addBehaviour("toggle_control_mode", toggleControlMode)
        inputHandler.addKeyBinding("toggle_control_mode", keybinding)
    }

    /**
     * Sets the shouldClose flag on renderer, causing it to shut down and thereby ending the main loop.
     */
    open fun close() {
        // Terminate main loop.
        shouldClose = true
        gracePeriod = 3

        renderer?.close()

        (hub.get(SceneryElement.NodePublisher) as? NodePublisher)?.close()
        (hub.get(SceneryElement.NodeSubscriber) as? NodeSubscriber)?.close()
        (hub.get(SceneryElement.OpenCLContext) as? OpenCLContext)?.close()
    }

    /**
     * Returns whether the current scene is done initialising.
     */
    fun sceneInitialized(): Boolean {
        return scene.initialized
    }

    /**
     * Loads a new [InputHandler] for the given [Renderer]. If running headless,
     * [renderer] can also be null.
     *
     * @param[renderer] A [Renderer] instance or null.
     */
    fun loadInputHandler(renderer: Renderer?) {
        renderer?.let {
            inputHandler = InputHandler(scene, it, hub)
            inputHandler?.useDefaultBindings(System.getProperty("user.home") + "/.$applicationName.bindings")

            if(wantREPL) {
                inputHandler?.addBehaviour("show_repl", ClickBehaviour { _, _ ->
                    repl = REPL(hub, scijavaContext, scene, stats, hub)
                    repl?.start()
                    repl?.addAccessibleObject(settings)
                    repl?.addAccessibleObject(inputHandler!!)
                    repl?.addAccessibleObject(it)
                    repl?.showConsoleWindow()
                })

                inputHandler?.addKeyBinding("show_repl", "shift R")
            }
        }
    }

    @JvmOverloads fun replaceRenderer(rendererPreference: String, force: Boolean = false, wait: Boolean = false) {
        val requestedRenderer = when (rendererPreference) {
            "OpenGLRenderer" -> "OpenGLRenderer"
            "VulkanRenderer" -> "VulkanRenderer"
            else -> {
                logger.warn("Unknown renderer '$rendererPreference', falling back to Vulkan.")
                "VulkanRenderer"
            }
        }

        if(requestedRenderer == renderer?.javaClass?.simpleName && !force) {
            logger.info("Not replacing renderer, because already running the same.")
            return
        }

        val latch = if(wait) {
            CountDownLatch(1)
        } else {
            null
        }

        val metadata = NewRendererParameters(
            rendererPreference, hub, applicationName,
            renderer?.window?.width ?: 512, renderer?.window?.height ?: 512,
            scene, renderer?.embedIn, renderer?.renderConfigFile ?: "DeferredShading.yml",
            latch)

        registerNewRenderer = metadata

        renderer?.close()

        if(wait) {
            latch?.await()
        }
    }

    open fun main() {
        thread {
            val start = System.nanoTime()
            while(renderer?.firstImageReady != true) {
                Thread.sleep(5)
            }

            val duration = (System.nanoTime() - start).nanoseconds
            logger.info("Full startup took ${duration.inWholeMilliseconds}ms")
        }

        System.getProperties().forEach { prop ->
            val name = prop.key as? String ?: return@forEach
            val value = prop.value as? String ?: return@forEach

            if(name.startsWith("scenery.LogLevel.")) {
                val className = name.substringAfter("scenery.LogLevel.")
                logger.info("Setting logging level of class $className to $value")
                System.setProperty("org.slf4j.simpleLogger.log.${className}", value)
            }
        }

        hub.addApplication(this)
        logger.info("Started application as PID ${getProcessID()} on ${Platform.get()}/${Platform.getArchitecture()}")
        running = true

        if(parseBoolean(System.getProperty("scenery.Profiler", "false"))) {
            hub.add(RemoteryProfiler(hub))
        }

        val server = System.getProperty("scenery.Server")?.toBoolean() ?: false
        val serverAddress = System.getProperty("scenery.ServerAddress") ?: "tcp://localhost"
        val mainPort = System.getProperty("scenery.MainPort")?.toIntOrNull() ?: 6040
        val backchannelPort = System.getProperty("scenery.BackchannelPort")?.toIntOrNull() ?: 6041

        hub.add(SceneryElement.Statistics, stats)
        hub.add(SceneryElement.Settings, settings)

        settings.set("System.PID", getProcessID())

        if (!server && serverAddress != null) {
            val subscriber = NodeSubscriber(hub, serverAddress, mainPort, backchannelPort, context = ZContext())
            hub.add(subscriber)
            scene.postUpdate += {subscriber.networkUpdate(scene)}
        } else if (server) {
            applicationName += " [Server]"
            val publisher = NodePublisher(hub, serverAddress ?: "localhost", portMain = mainPort, portBackchannel = backchannelPort, context = ZContext())

            hub.add(publisher)
            publisher.register(scene)
            scene.postUpdate += { publisher.scanForChanges()}
        }


        // initialize renderer, etc first in init, then setup key bindings
        init()
        runBlocking { sceneryMain() }
    }

    fun waitForSceneInitialisation() {
        while(!sceneInitialized()) {
            Thread.sleep(200)
        }
    }

    infix fun Behaviour.called(name: String): Pair<String, Behaviour> {
        return name to this
    }

    infix fun Pair<String, Behaviour>.boundTo(key: String): InputHandler.NamedBehaviourWithKeyBinding {
        return InputHandler.NamedBehaviourWithKeyBinding(this.first, this.second, key)
    }

    infix fun Pair<String, Behaviour>.boundTo(key: GamepadButton): InputHandler.NamedBehaviourWithKeyBinding {
        val button = when {
            key.ordinal <= GamepadButton.Button8.ordinal -> key.ordinal.toString()
            key.ordinal == GamepadButton.PovUp.ordinal -> "NUMPAD8"
            key.ordinal == GamepadButton.PovRight.ordinal -> "NUMPAD6"
            key.ordinal == GamepadButton.PovDown.ordinal -> "NUMPAD2"
            key.ordinal == GamepadButton.PovLeft.ordinal -> "NUMPAD4"
            key.ordinal == GamepadButton.AlwaysActive.ordinal -> "F24"
            else -> throw IllegalStateException("Don't know how to translate gamepad button with ordinal ${key.ordinal} to key code.")
        }

        return InputHandler.NamedBehaviourWithKeyBinding(this.first, this.second, button)
    }

    infix fun Pair<String, Behaviour>.boundTo(key: DTrackButton): InputHandler.NamedBehaviourWithKeyBinding {
        val button = when(key) {
            DTrackButton.Trigger -> "0"
            DTrackButton.Left -> "3"
            DTrackButton.Center -> "2"
            DTrackButton.Right -> "1"
        }

        return InputHandler.NamedBehaviourWithKeyBinding(this.first, this.second, button)
    }

    companion object {
        private val logger by lazyLogger(System.getProperty("scenery.LogLevel", "info"))
        private var xinitThreadsCalled: Boolean = false

        /**
         * Returns the process ID we are running under.
         *
         * @return The process ID as integer.
         */
        @JvmStatic fun getProcessID(): Int {
            return Integer.parseInt(ManagementFactory.getRuntimeMXBean().name.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0])
        }

        /**
         * Returns the path set defined by the environment variable SCENERY_DEMO_FILES.
         * Should only be used in examples that require the additional model files and will
         * emit a warning in case the variable is not set.
         *
         * @return String containing the path set in SCENERY_DEMO_FILES.
         */
        @JvmStatic fun getDemoFilesPath(): String {
            val demoDir = System.getenv("SCENERY_DEMO_FILES")
            var maybeDemoPath: String? = null

            if(demoDir == null) {
                val relativePath = Paths.get("./models")
                maybeDemoPath = relativePath.toAbsolutePath().toString()
            }

            return if (demoDir == null) {
                logger.warn("This example needs additional model files, see https://github.com/scenerygraphics/scenery#examples")
                logger.warn("Download the model files mentioned there and set the environment variable SCENERY_DEMO_FILES to the")
                logger.warn("directory where you have put these files.")

                if(maybeDemoPath != null) {
                    logger.warn("Returning $maybeDemoPath, the files you need might be there.")
                    maybeDemoPath
                } else {
                    ""
                }
            } else {
                demoDir
            }
        }

        @JvmStatic fun URL.sanitizedPath(): String {
            // cuts off the initial / on Windows
            return if(Platform.get() == Platform.WINDOWS) {
                this.path.substringAfter("/")
            } else {
                this.path
            }
        }

        @JvmStatic fun xinitThreads() {
            if(Platform.get() == Platform.LINUX && xinitThreadsCalled == false) {
                logger.debug("Running XInitThreads")
                XLib.INSTANCE.XInitThreads()
                xinitThreadsCalled = true
            }
        }

        @JvmStatic fun m1supportForjHDF5() {
            if(Platform.get() == Platform.MACOSX && Platform.getArchitecture() == Platform.Architecture.ARM64) {
                val arch = System.getProperty("os.arch")
                val os = System.getProperty("os.name")

                var basepath = ""
                logger.debug("Downloading M1 support libraries for JHDF5, if not already existing...")
                listOf("hdf5", "jhdf5").forEach { lib ->
                    basepath = ExtractsNatives.nativesFromGithubRelease(
                        "JaneliaSciComp",
                        "jhdf5",
                        "jhdf5-19.04.1_fatjar",
                        "sis-jhdf5-1654327451.jar",
                        "$arch-$os",
                        lib,
                        "jnilib"
                    )
                }

                if(System.getProperty("native.libpath") == null) {
                    System.setProperty("native.libpath", basepath)
                }
            }
        }
    }
}
