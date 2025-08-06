package graphics.scenery.tests.examples.cluster

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.TrackedStereoGlasses
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.system.exitProcess

/**
 * Demo reel example to be run on a CAVE system.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class CaveBaseScene(name: String = "Cave Scene") : SceneryBase(name) {
    var hmd: TrackedStereoGlasses? = null
    lateinit var cam: Camera

    val driveLetter: String = System.getProperty("scenery.DriveLetter", "E")

    override fun init() {
        logger.warn("*** WARNING - EXPERIMENTAL ***")
        logger.warn("This is an experimental example, which might need additional configuration on your computer")
        logger.warn("or might not work at all. You have been warned!")

        val trackerAddress = System.getProperty("scenery.TrackerAddress") ?: "DTrack:body-0@224.0.1.1:5000"
        hmd = hub.add(TrackedStereoGlasses(trackerAddress, screenConfig = "CAVEExample.yml"))
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, 2560, 1600))

        cam = DetachedHeadCamera(hmd)
        with(cam) {
            networkID = -5
            spatial {
                position = Vector3f(0.0f, 0.0f, 55.0f)
                networkID = -7
            }
            perspectiveCamera(50.0f, windowWidth, windowHeight, 0.02f, 500.0f)
            disableCulling = true
            scene.addChild(this)
        }

        // box setup
        val shell = Box(Vector3f(120.0f, 120.0f, 120.0f), insideNormals = true)
        shell.material {
            cullingMode = Material.CullingMode.Front
            diffuse = Vector3f(0.0f, 0.0f, 0.0f)
            specular = Vector3f(0.0f)
            ambient = Vector3f(0.0f)
        }
        scene.addChild(shell)

        Light.createLightTetrahedron<PointLight>(spread = 50.0f, intensity = 5.0f, radius = 150.0f)
            .forEach { scene.addChild(it) }

        val box = Box()
        scene.addChild(box)

    }

    /**
     * Standard input setup, plus additional key bindings to
     * switch scenes.
     */
    override fun inputSetup() {
        val inputHandler = (hub.get(SceneryElement.Input) as? InputHandler) ?: return

        inputHandler.addBehaviour("kill_clients", ClickBehaviour { _, _ ->
            CaveSceneLauncher.client?.shutdownLaunchedProcesses()
            Thread.sleep(1500)
            exitProcess(0)
        })
        inputHandler.addKeyBinding("kill_clients", "ctrl Q")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            CaveBaseScene().main()
        }
    }
}
