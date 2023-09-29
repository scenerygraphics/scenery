package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.attribute.material.DefaultMaterial
import graphics.scenery.attribute.material.Material
import graphics.scenery.numerics.Random
import org.joml.AxisAngle4f
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class IntersectionExample: SceneryBase("IntersectionExample") {
    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, 512, 512))

        val boxmaterial = DefaultMaterial()
        with(boxmaterial) {
            ambient = Vector3f(1.0f, 0.0f, 0.0f)
            diffuse = Vector3f(0.0f, 1.0f, 0.0f)
            specular = Vector3f(1.0f, 1.0f, 1.0f)
            roughness = 0.3f
            metallic = 1.0f
        }

        val sphere = Icosphere(0.5f, 2)
        sphere.name = "le box du win"

        val box2 = Box(Random.random3DVectorFromRange(0.1f, 1.0f))

        with(sphere) {
            sphere.addAttribute(Material::class.java, boxmaterial)
            scene.addChild(this)
        }

        with(box2) {
            spatial {
                // shift box2 such that it'll always slightly intersect the sphere
                val shift = -1.0f * (box2.boundingBox?.getBoundingSphere()?.radius ?: 0.5f) * 0.9f
                position = Vector3f(shift, 0.0f, 0.0f)
            }
            box2.addAttribute(Material::class.java,  boxmaterial)
            scene.addChild(this)
        }
        val light = PointLight(radius = 15.0f)
        light.spatial {
            position = Vector3f(0.0f, 0.0f, 2.0f)
        }
        light.intensity = 100.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val ambientLight = AmbientLight(0.1f)
        scene.addChild(ambientLight)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }

        thread {
            val aa = AxisAngle4f(0.1f, Random.random3DVectorFromRange(0.2f, 0.8f).normalize())
            while (true) {
                if (sphere.spatial().intersects(box2, true)) {
                    sphere.material().diffuse = Vector3f(1.0f, 0.0f, 0.0f)
                } else {
                    sphere.material().diffuse = Vector3f(0.0f, 1.0f, 0.0f)
                }

                aa.angle += 0.01f
                box2.spatial().rotation = box2.spatial().rotation.rotationAxis(aa)

                Thread.sleep(20)
            }
        }
    }

    override fun inputSetup() {
        super.inputSetup()
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            IntersectionExample().main()
        }
    }
}
