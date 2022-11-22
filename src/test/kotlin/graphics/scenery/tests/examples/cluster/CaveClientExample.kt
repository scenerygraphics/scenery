package graphics.scenery.tests.examples.cluster

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses

/**
 * Scene for clients in a cave setup.
 *
 * example vm parameters:
 * -Dscenery.ServerAddress=tcp://127.0.0.1
 * -Dscenery.ScreenName="right"
 * -Dscenery.TrackerAddress="DTrack:body-0@224.0.1.1:5001"
 * -Dscenery.ScreenConfig="CAVEExample.yml"
 *
 * @author Jan Tiemann
 */
class CaveClientExample(): SceneryBase("cave client", wantREPL = false) {
    var hmd: TrackedStereoGlasses? = null

    override fun init() {
        logger.warn("*** WARNING - EXPERIMENTAL ***")
        logger.warn("This is an experimental example, which might need additional configuration on your computer")
        logger.warn("or might not work at all. You have been warned!")


        val trackerAddress = System.getProperty("scenery.TrackerAddress") ?: "fake:"
        val screenConfig = System.getProperty("scenery.ScreenConfig") ?: "CAVEExample.yml"

        hmd = hub.add(TrackedStereoGlasses(trackerAddress, screenConfig = screenConfig))

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, 2560, 1600))

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            spatial {
                position = Vector3f(.0f, -0.4f, 5.0f)
                networkID = -7
            }
            networkID = -5
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            CaveClientExample().main()
        }
    }
}
