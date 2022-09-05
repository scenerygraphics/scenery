package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.primitives.Plane
import graphics.scenery.utils.extensions.plus
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class SceneRayCastingExample : SceneryBase("SceneRayCastingExample") {
    val cam: Camera = DetachedHeadCamera()

    override fun init() {
        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        )

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

                Thread.sleep(20)
            }
        }
    }

    override fun inputSetup() {
        super.inputSetup()

        inputHandler?.addBehaviour(
            "sphereDragObject", object : ClickBehaviour {
                override fun click(x: Int, y: Int) {
                    val ray = cam.getNodesForScreenSpacePosition(x,y, listOf<Class<*>>(BoundingGrid::class.java), true, true)

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
            SceneRayCastingExample().main()
        }
    }
}

