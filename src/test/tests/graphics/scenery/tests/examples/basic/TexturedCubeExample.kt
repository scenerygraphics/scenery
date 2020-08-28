package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import org.junit.Test
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class TexturedCubeExample : SceneryBase("TexturedCubeExample") {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 700, 700))

        renderer?.recordMovie("")

        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        box.name = "le box du win"
        box.material.textures["diffuse"] = Texture.fromImage(Image.fromResource("textures/helix.png", this::class.java))
        box.material.metallic = 0.3f
        box.material.roughness = 0.9f
        scene.addChild(box)

        val light = PointLight(radius = 15.0f)
        light.position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = Vector3f(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }

        thread {
            while (running) {
                box.rotation.rotateY(0.01f)
                box.needsUpdate = true

                Thread.sleep(20)
            }
        }
    }

    @Test override fun main() {
//        settings.set("VideoEncoder.Format", "HEVC")
        settings.set("VideoEncoder.StreamVideo", true)
        settings.set("H264Encoder.StreamingAddress", "udp://${InetAddress.getLocalHost().hostAddress}:3337")
        super.main()
    }
}
