package graphics.scenery

import cleargl.ClearGLDefaultEventListener
import cleargl.GLVector
import com.sun.jna.Pointer
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.opengl.OpenGLRenderer
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.controls.behaviours.FPSCameraControl
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import graphics.scenery.repl.REPL
import graphics.scenery.utils.Statistics
import org.scijava.ui.behaviour.ClickBehaviour
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.zeromq.ZContext
import kotlin.concurrent.thread
import java.lang.reflect.AccessibleObject.setAccessible
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.Kernel32
import java.lang.management.ManagementFactory





/**
 * A default application to use scenery with, keeping the needed boilerplate
 * to a minimum. Inherit from this class for a quick start.
 *
 * @property[applicationName] Name of the application, do not use special chars
 * @property[windowWidth] Window width of the application window
 * @property[windowHeight] Window height of the application window
 *
 * @constructor Creates a new SceneryDefaultApplication
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

open class SceneryDefaultApplication(var applicationName: String,
                                     var windowWidth: Int = 1024,
                                     var windowHeight: Int = 1024,
                                     val wantREPL: Boolean = true) {

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

    protected var logger: Logger = LoggerFactory.getLogger(applicationName)

    var updateFunction: () -> Any = {}

    var running: Boolean = false
        protected set

    var maxFrameskip = 5

    var ticksPerSecond = 60000.0f
        set(value) {
            field = value
            skipTicks = 1000000.0f/value
        }

    var skipTicks = 1000000.0f/ticksPerSecond
        private set

    /**
     * the init function of [SceneryDefaultApplication], override this in your subclass,
     * e.g. for [Scene] construction and [Renderer] initialisation.
     */
    open fun init() {

    }

    open fun inputSetup() {

    }

    /**
     * Main routine for [SceneryDefaultApplication]
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
        logger.info("Started application as PID ${getProcessID()}")
        Thread.sleep(3500)
        val master = System.getProperty("scenery.master").toBoolean()
        val context = ZContext(2)

        val publisher: NodePublisher? = if(master) {
            NodePublisher(hub, "tcp://*:6666", context)
        } else {
            null
        }

        publisher?.let { hub.add(SceneryElement.NodePublisher, it) }

        val subscriber: NodeSubscriber? = if(!master) {
            val masterAddress = System.getProperty("scenery.MasterNode", "tcp://localhost:6666")
            logger.info("Will connect to master at $masterAddress")
            NodeSubscriber(hub, masterAddress, context)
        } else {
            null
        }

        subscriber?.let { hub.add(SceneryElement.NodeSubscriber, it) }

        hub.add(SceneryElement.Statistics, stats)
        hub.add(SceneryElement.Settings, settings)

        settings.set("System.PID", getProcessID())

        if(wantREPL) {
            repl = REPL(scene, stats, hub)
            repl?.addAccessibleObject(settings)
        }

        // initialize renderer, etc first in init, then setup key bindings
        init()

        renderer?.let {
            repl?.addAccessibleObject(it)

            inputHandler = InputHandler(scene, it, hub)
            inputHandler?.useDefaultBindings(System.getProperty("user.home") + "/.$applicationName.bindings")
        }

        repl?.start()
        repl?.showConsoleWindow()

        val statsRequested = java.lang.Boolean.parseBoolean(System.getProperty("scenery.PrintStatistics", "false"))

        // setup additional key bindings, if requested by the user
        inputSetup()

        running = true

        if(!master) {
            thread {
                while (true) {
                    subscriber?.process()
                    Thread.sleep(2)
                }
            }
        }

        var nextTick = getTickCount()
        var interpolation: Float

        while(!(renderer?.shouldClose ?: true)) {
            val start = System.nanoTime()
            var loops = 0

            hub.getWorkingHMD()?.update()

            while(getTickCount() > nextTick && loops < maxFrameskip) {
                // update
                stats.addTimed("sceneUpdate", updateFunction)

                nextTick += skipTicks
                loops++
            }

            publisher?.publish()

            interpolation = (1.0f*getTickCount() + skipTicks - nextTick)/skipTicks
            scene.activeObserver?.deltaT = interpolation/10e4f

            if(renderer?.managesRenderLoop ?: true) {
                Thread.sleep(2)
            } else {
                stats.addTimed("render", { renderer?.render() ?: 0.0f })
            }

            if(statsRequested && ticks % 100L == 0L) {
                logger.info("\nStatistics:\n=============\n$stats")
            }

            stats.add("loop", System.nanoTime() - start*1.0f)
            ticks++
        }

        inputHandler?.close()
        renderer?.close()
    }

    fun setupCameraModeSwitching(keybinding: String = "C") {
        val target = GLVector.getNullVector(3)
        val inputHandler = (hub.get(SceneryElement.Input) as InputHandler)
        val targetArcball = ArcballCameraControl("mouse_control", { scene.findObserver() }, renderer!!.window.width, renderer!!.window.height, target)
        val fpsControl = FPSCameraControl("mouse_control", { scene.findObserver() }, renderer!!.window.width, renderer!!.window.height)

        val toggleControlMode = object : ClickBehaviour {
            var currentMode = "fps"

            override fun click(x: Int, y: Int) {
                if (currentMode.startsWith("fps")) {
                    targetArcball.target = GLVector(0.0f, 0.0f, 0.0f)

                    inputHandler.addBehaviour("mouse_control", targetArcball)
                    inputHandler.addBehaviour("scroll_arcball", targetArcball)
                    inputHandler.addKeyBinding("scroll_arcball", "scroll")

                    currentMode = "arcball"
                } else {
                    inputHandler.addBehaviour("mouse_control", fpsControl)
                    inputHandler.removeBehaviour("scroll_arcball")

                    currentMode = "fps"
                }

                System.out.println("Switched to $currentMode control")
            }
        }

        inputHandler.addBehaviour("toggle_control_mode", toggleControlMode)
        inputHandler.addKeyBinding("toggle_control_mode", keybinding)
    }

    protected fun getTickCount(): Float = System.nanoTime()/1000.0f

    protected fun getDemoFilesPath(): String {
        val demoDir = System.getenv("SCENERY_DEMO_FILES")

        if(demoDir == null) {
            logger.warn("This example needs additional model files, see https://github.com/scenerygraphics/scenery#examples")
            logger.warn("Download the model files mentioned there and set the environment variable SCENERY_DEMO_FILES to the")
            logger.warn("directory where you have put these files.")

            return ""
        } else {
            return demoDir
        }
    }

    protected fun getProcessID(): Int {
       return Integer.parseInt(ManagementFactory.getRuntimeMXBean().name.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0])
    }
}
