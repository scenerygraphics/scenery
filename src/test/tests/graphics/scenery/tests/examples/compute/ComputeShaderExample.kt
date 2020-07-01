package graphics.scenery.tests.examples.compute

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.tests.examples.basic.TexturedCubeExample
import graphics.scenery.tests.examples.basic.TexturedCubeJavaExample
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import org.junit.Test
import kotlin.concurrent.thread

/**
 * Example showing simple image processing with compute shaders.
 * Based on [graphics.scenery.tests.examples.basic.TexturedCubeExample].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ComputeShaderExample : SceneryBase("ComputeShaderExample") {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 512, 512))

        val compute = Box()
        compute.name = "compute node"
        compute.material = ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("BGRAMosaic.comp"), this::class.java))
        compute.material.textures["OutputViewport"] = Texture.fromImage(Image.fromResource("textures/helix.png", TexturedCubeExample::class.java), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        compute.material.textures["InputColor"] = Texture.fromImage(Image.fromResource("textures/helix.png", TexturedCubeExample::class.java), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))

        scene.addChild(compute)

        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        box.name = "le box du win"
        box.material.textures["diffuse"] = compute.material.textures["OutputViewport"]!!
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
                box.material.textures["diffuse"] = compute.material.textures["OutputViewport"]!!

                Thread.sleep(20)
            }
        }
    }

    @Test override fun main() {
        super.main()
    }
}
