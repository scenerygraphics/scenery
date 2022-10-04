package graphics.scenery.tests.examples.volumes

import bdv.spimdata.XmlIoSpimDataMinimal
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.SwingUiNode
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunctionUI
import graphics.scenery.volumes.Volume
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.ui.behaviour.DragBehaviour
import tpietzsch.example2.VolumeViewerOptions
import java.io.File


/**
 * @author Konrad Michel
 * Example for scenery - swing bridge
 *
 * A TransferFunctionEditor example to add, manipulate and remove control points of a volume's transfer function.
 * Further more able to generate a histogram representation of the volume data distribution to help with the transfer function setup.
 */
class TransferFunctionEditorExample : SceneryBase("TransferFunctionEditor Example", 1280, 720) {
    var maxCacheSize = 512
    val cam: Camera = DetachedHeadCamera()

    override fun init() {

        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        )

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(2.0f, 0.0f, 2.0f)*2f
        light.intensity = 15.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val light2 = PointLight(radius = 15.0f)
        light2.spatial().position = Vector3f(-2.0f, 0.0f, -2.0f)*2f
        light2.intensity = 15.0f
        light2.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light2)

        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            nearPlaneDistance = 0.01f
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        val workingDirectoryPath = File("").absolutePath

        val options = VolumeViewerOptions().maxCacheSizeInMB(maxCacheSize)
        val name = "t1-head"
        val v = Volume.fromSpimData(XmlIoSpimDataMinimal().load("$workingDirectoryPath/src/main/resources/volumes/$name.xml"), hub, options)
        v.name = name
        v.colormap = Colormap.get("grays")
        cam.spatial().position = Vector3f(0.0f, 0.0f, 0.0f)
        v.spatial().scale = Vector3f(0.1f)
        v.setTransferFunctionRange(0.0f, 1000.0f)
        scene.addChild(v)

        val tfUI = TransferFunctionUI(650, 500, v)
        val swingUiNode = tfUI.uiNode
        swingUiNode.spatial() {
            position = Vector3f(1f,0f,0f)
        }

        scene.addChild(swingUiNode)
    }

    override fun inputSetup() {
        super.inputSetup()

        inputHandler?.addBehaviour(
            "sphereClickObject", object : ClickBehaviour {
                override fun click(x: Int, y: Int) {
                    val ray = cam.getNodesForScreenSpacePosition(x,y, listOf<Class<*>>(BoundingGrid::class.java))

                    ray.matches.firstOrNull()?.let { hit ->
                        val node = hit.node as? SwingUiNode ?: return
                        val hitPos = ray.initialPosition + ray.initialDirection.normalize(hit.distance)
                        logger.info("Distance: ${hit.distance}")
                        node.click(hitPos)
                    }
                }
            }
        )
        inputHandler?.addBehaviour(
            "sphereDragObject", object : DragBehaviour {
                override fun init(x:Int, y: Int) {
                    val ray = cam.getNodesForScreenSpacePosition(x,y, listOf<Class<*>>(BoundingGrid::class.java))

                    ray.matches.firstOrNull()?.let { hit ->
                        val node = hit.node as? SwingUiNode ?: return
                        val hitPos = ray.initialPosition + ray.initialDirection.normalize(hit.distance)
                        logger.info("Pressed Distance: ${hit.distance}")
                        node.pressed(hitPos)
                    }
                }
                override fun drag(x: Int, y: Int) {
                    val ray = cam.getNodesForScreenSpacePosition(x,y, listOf<Class<*>>(BoundingGrid::class.java))

                    ray.matches.firstOrNull()?.let { hit ->
                        val node = hit.node as? SwingUiNode ?: return
                        val hitPos = ray.initialPosition + ray.initialDirection.normalize(hit.distance)
                        logger.info("Dragged Distance: ${hit.distance}")
                        node.drag(hitPos)
                    }
                }
                override fun end(x: Int, y: Int) {
                    val ray = cam.getNodesForScreenSpacePosition(x,y, listOf<Class<*>>(BoundingGrid::class.java))

                    ray.matches.firstOrNull()?.let { hit ->
                        val node = hit.node as? SwingUiNode ?: return //backside might get hit first
                        val hitPos = ray.initialPosition + ray.initialDirection.normalize(hit.distance)
                        logger.info("Released Distance: ${hit.distance}")
                        node.released(hitPos)
                    }
                }
            }
        )
        inputHandler?.addKeyBinding("sphereClickObject", "1")
        inputHandler?.addKeyBinding("sphereDragObject", "1")
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            TransferFunctionEditorExample().main()
        }
    }
}


