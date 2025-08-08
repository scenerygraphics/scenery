package graphics.scenery.tests.examples.network

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer

/**
 * Empty scene to receive content via network
 *
 * Start with vm param:
 * -ea -Dscenery.ServerAddress=tcp://127.0.0.1 [-Dscenery.RemoteCamera=false|true]
 *
 * Explanation:
 * - RemoteCamera: (default false) Has to be set to true if the camera of the server provided scene should be used.
 */
class SlimClient : SceneryBase("Client", wantREPL = false) {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 512, 512))

        if(!Settings().get("RemoteCamera",false)) {
            val cam: Camera = DetachedHeadCamera()
            with(cam) {
                spatial {
                    position = Vector3f(0.0f, 0.0f, 5.0f)
                }
                perspectiveCamera(50.0f, 512, 512)

                scene.addChild(this)
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SlimClient().main()
        }
    }
}

