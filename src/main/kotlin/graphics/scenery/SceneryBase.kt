package graphics.scenery

import cleargl.ClearGLDefaultEventListener
import cleargl.GLVector
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.opengl.OpenGLRenderer
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.controls.behaviours.FPSCameraControl
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import graphics.scenery.repl.REPL
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.Renderdoc
import graphics.scenery.utils.Statistics
import org.scijava.Context
import org.scijava.ui.behaviour.ClickBehaviour
import java.lang.management.ManagementFactory
import java.util.*
import kotlin.concurrent.thread

/**
 * Base class to use scenery with, keeping the needed boilerplate
 * to a minimum. Inherit from this class for a quick start.
 *
 * @property[applicationName] Name of the application, do not use special chars
 * @property[windowWidth] Window width of the application window
 * @property[windowHeight] Window height of the application window
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
    protected val scene: Scene = Scene()
    /** REPL for the application, can be initialised in the [init] function */
    protected var repl: REPL? = null
    /** Frame number for counting FPS */
    protected var ticks = 0L
    /** The Deferred Lighting Renderer for the application, see [OpenGLRenderer] */
    protected var renderer: Renderer? = null
    /** The Hub used by the application, see [Hub] */
    var hub: Hub = Hub()
    /** Global settings storage */
    protected var settings: Settings = Settings()
    /** ui-behaviour input handler */
    protected var inputHandler: InputHandler? = null

    protected var stats: Statistics = Statistics(hub)

    protected val logger by LazyLogger()

    var updateFunction: () -> Any = {}

    var running: Boolean = false
        protected set
    var runtime: Float = 0.0f
        protected set

    var timeStep = 0.01f

    private var accumulator = 0.0f
    private var currentTime = System.nanoTime()
    private var t = 0.0f

    /**
     * the init function of [SceneryBase], override this in your subclass,
     * e.g. for [Scene] construction and [Renderer] initialisation.
     */
    open fun init() {

    }

    open fun inputSetup() {

    }

    /**
     * Main routine for [SceneryBase]
     *
     * This routine will construct a internal [ClearGLDefaultEventListener], and initialize
     * with the [init] function. Override this in your subclass and be sure to call `super.main()`.
     *
     * The [ClearGLDefaultEventListener] will take care of usually used window functionality, like
     * resizing, closing, setting the OpenGL context, etc. It'll also read a keymap for the [InputHandler],
     * based on the [applicationName], from the file `~/.[applicationName].bindings
     *
     */
    open fun main() {
        hub.addApplication(this)
        logger.info("Started application as PID ${getProcessID()}")

        val headless = System.getProperty("scenery.Headless", "false").toBoolean()
        val renderdoc = if(System.getProperty("scenery.AttachRenderdoc")?.toBoolean() == true) {
            Renderdoc()
        } else {
            null
        }

        val master = System.getProperty("scenery.master")?.toBoolean() ?: false
        val masterAddress = System.getProperty("scenery.MasterNode")

        val publisher: NodePublisher? = if (master) {
            logger.info("Listening on 0.0.0.0:6666")
            NodePublisher(hub, "tcp://*:6666")
        } else {
            null
        }

        hub.add(SceneryElement.Statistics, stats)
        hub.add(SceneryElement.Settings, settings)

        settings.set("System.PID", getProcessID())

        if (wantREPL && !headless) {
            repl = REPL(scijavaContext, scene, stats, hub)
            repl?.addAccessibleObject(settings)
        }

        // initialize renderer, etc first in init, then setup key bindings
        init()

        // wait for renderer
        while(renderer?.initialized == false) {
            Thread.sleep(100)
        }

        renderer?.let {
            repl?.addAccessibleObject(it)

            inputHandler = InputHandler(scene, it, hub)
            inputHandler?.useDefaultBindings(System.getProperty("user.home") + "/.$applicationName.bindings")
        }

        // start & show REPL -- note: REPL will only exist if not running in headless mode
        repl?.start()
        if(!System.getProperty("scenery.Headless", "false").toBoolean()) {
            repl?.showConsoleWindow()
        }

        val statsRequested = java.lang.Boolean.parseBoolean(System.getProperty("scenery.PrintStatistics", "false"))

        // setup additional key bindings, if requested by the user
        inputSetup()

        running = true
        val startTime = System.nanoTime()

        if (!master && masterAddress != null) {
            thread {
                logger.info("Will connect to master at $masterAddress")
                val subscriber = NodeSubscriber(hub, masterAddress)

                hub.add(SceneryElement.NodeSubscriber, subscriber)
                scene.discover(scene, { true }).forEachIndexed { index, node ->
                    subscriber.nodes.put(index, node)
                }

                while (true) {
                    subscriber.process()
                    Thread.sleep(2)
                }
            }
        } else {
            thread {
                publisher?.let {
                    hub.add(SceneryElement.NodePublisher, it)
                    scene.discover(scene, { true }).forEachIndexed { index, node ->
                        publisher.nodes.put(index, node)
                    }
                }

                while (true) {
                    publisher?.publish()
                    Thread.sleep(2)
                }
            }
        }

        var frameTime = 0.0f
        var lastFrameTime = 0.0f
        val frameTimes = ArrayDeque<Float>(16)
        val frameTimeKeepCount = 16

        while (renderer?.shouldClose == false) {
            runtime = (System.nanoTime() - startTime) / 1000000f
            settings.set("System.Runtime", runtime)

            if(renderer?.managesRenderLoop == false) {
                hub.getWorkingHMD()?.update()
            }

            if (renderer?.managesRenderLoop != false) {
                Thread.sleep(2)
            } else {
                stats.addTimed("render", { renderer?.render() ?: 0.0f })
            }

            // only run loop if we are either in standalone mode, or master
            // for details about the interpolation code, see
            // https://gafferongames.com/post/fix_your_timestep/
            if(master || masterAddress == null) {
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

                    stats.addTimed("Scene.Update", updateFunction)
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

            ticks++
        }

        inputHandler?.close()
        renderer?.close()
        renderdoc?.close()
        running = false
    }

    fun setupCameraModeSwitching(keybinding: String = "C") {
        val target = scene.findObserver()?.target ?: GLVector.getNullVector(3)
        val inputHandler = (hub.get(SceneryElement.Input) as InputHandler)
        val targetArcball = ArcballCameraControl("mouse_control", { scene.findObserver() }, renderer!!.window.width, renderer!!.window.height, target)
        val fpsControl = FPSCameraControl("mouse_control", { scene.findObserver() }, renderer!!.window.width, renderer!!.window.height)

        val toggleControlMode = object : ClickBehaviour {
            var currentMode = "fps"

            override fun click(x: Int, y: Int) {
                if (currentMode.startsWith("fps")) {
                    targetArcball.target = scene.findObserver()?.target ?: return

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
    fun close() {
        renderer?.shouldClose = true
    }

    /**
     * Returns whether the current scene is done initialising.
     */
    fun sceneInitialized(): Boolean {
        return scene.initialized
    }

    companion object {
        val logger by LazyLogger()

        fun getProcessID(): Int {
            return Integer.parseInt(ManagementFactory.getRuntimeMXBean().name.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0])
        }

        fun getDemoFilesPath(): String {
            val demoDir = System.getenv("SCENERY_DEMO_FILES")

            return if (demoDir == null) {
                logger.warn("This example needs additional model files, see https://github.com/scenerygraphics/scenery#examples")
                logger.warn("Download the model files mentioned there and set the environment variable SCENERY_DEMO_FILES to the")
                logger.warn("directory where you have put these files.")

                ""
            } else {
                demoDir
            }
        }
    }
}
