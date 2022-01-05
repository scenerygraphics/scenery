package graphics.scenery.tests.examples.cluster

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer

/**
 * Empty scene to receive content via network
 *
 * Start with vm param:
 * -ea -Dscenery.ServerAddress=tcp://127.0.0.1
 */
class SlimClient : SceneryBase("Client", wantREPL = false) {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 512, 512))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SlimClient().main()
        }
    }
}

