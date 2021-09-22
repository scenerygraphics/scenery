package graphics.scenery.tests.examples.basic


import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class PictureDisplayExample : SceneryBase("PictureDisplayExample", wantREPL = System.getProperty("scenery.master", "false").toBoolean()) {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 512, 512))

        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        box.name = "le box du win"
        val img = Image.fromResource("textures/L-Glutamic-Acid.jpg", TexturedCubeExample::class.java)
        val height = img.height
        val width = img.width
        box.material {
            textures["diffuse"] = Texture.fromImage(img)
            diffuse = Vector3f(100f, 100f, 100f)
            metallic = 0.3f
            roughness = 0.9f
        }
        box.spatial().scale = Vector3f(width/height.toFloat(), 1f, 0f)
        scene.addChild(box)

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

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
            PictureDisplayExample().main()
        }
    }
}
