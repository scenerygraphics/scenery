package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.primitives.Line
import graphics.scenery.primitives.Plane
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class TexturedCubeExampleRC : SceneryBase("TexturedCubeExampleRC") {
    val cam: Camera = DetachedHeadCamera()

    override fun init() {
        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        )

        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        box.name = "le box du win"
        box.material {
            textures["diffuse"] =
                Texture.fromImage(Image.fromResource("textures/helix.png", TexturedCubeExample::class.java))
            metallic = 0.3f
            roughness = 0.9f
        }
        //scene.addChild(box)

        val plane = Plane(
            Vector3f(-0.5f,-0.5f,0f),
            Vector3f(-0.5f,0.5f,0f),
            Vector3f(0.5f,-0.5f,0f),
            Vector3f(0.5f,0.5f,0f)
        )
        plane.material().cullingMode = Material.CullingMode.None
        scene.addChild(plane)


        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(2.0f, 2.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)


        with(cam) {
            nearPlaneDistance = 0.01f
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }

        thread {
            while (running) {
                box.spatial {
                    rotation.rotateY(0.01f)
                    needsUpdate = true
                }

                Thread.sleep(20)
            }
        }
    }

    override fun inputSetup() {
        super.inputSetup()

        inputHandler?.addBehaviour(
            "sphereDragObject", object : ClickBehaviour {
                override fun click(x: Int, y: Int) {
                    val ray = cam.getNodesForScreenSpacePosition(x,y, listOf<Class<*>>(BoundingGrid::class.java), true)
                    val line = scene.find("Line") as? Line
                    if(line is Line)
                    {
                        line.addPoint(cam.spatial().position)
                        line.addPoint(ray.initialPosition + ray.initialDirection.times(5.0f))
                    }

                    ray.matches.firstOrNull()?.let { hit ->
                        val node = hit.node as? Box ?: return //backside might get hit first
                        val hitPos = ray.initialPosition + ray.initialDirection.normalize(hit.distance)
                        logger.info("Node: $node, HitPos: $hitPos, Distance: ${hit.distance}, Origin: ${ray.initialPosition}")
                    }
                }
            }
        )
        inputHandler?.addKeyBinding("sphereDragObject", "1")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            TexturedCubeExampleRC().main()
        }
    }
}

