package graphics.scenery.tests.examples.volumes

import bvv.core.VolumeViewerOptions
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.ui.SwingBridgeFrame
import graphics.scenery.ui.SwingUINode
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunctionEditor
import graphics.scenery.volumes.Volume
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.ui.behaviour.DragBehaviour


/**
 * @author Konrad Michel <Konrad.Michel@mailbox.tu-dresden.de>
 * Example for scenery - swing bridge
 *
 * A TransferFunctionEditor example to add, manipulate and remove control points of a volume's transfer function.
 * Further more able to generate a histogram representation of the volume data distribution to help with the transfer function setup.
 *
 * Usage: To enable the UI on the plane click once (Key '1') while hovering over the plane. Key '1' used as normal Mouse-interactions (Clicking and dragging).
 * Control Points can be dragged, added and removed. A remove happens via Ctrl-Clicking (In this example managed by using Key '2'.
 */
class TransferFunctionEditorExample : SceneryBase("TransferFunctionEditor Example", 1280, 720, false) {
    var maxCacheSize = 512
    val cam: Camera = DetachedHeadCamera()

    /**
     * Sets up the example, containing 2 light sources (PointLight), a perspective camera and a volume.
     * Also adds a SwingUINode containing a SwingBridgeFrame contained by a TransferFunctionUI to manipulate the Volume
     */
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
                position = Vector3f(0.0f, 0.0f, 2.0f)
            }
            nearPlaneDistance = 0.01f
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        val options = VolumeViewerOptions().maxCacheSizeInMB(maxCacheSize)
        //Currently only .xml volume formats are usable
        val v = Volume.fromXML(getDemoFilesPath() + "/volumes/t1-head.xml", hub, options)
        v.name = "t1-head"
        v.colormap = Colormap.get("grays")
        v.spatial().position = Vector3f(0.0f, 0.0f, 0.0f)
        v.spatial().scale = Vector3f(5.0f)
        v.setTransferFunctionRange(0.0f, 1000.0f)
        scene.addChild(v)


        val bridge = SwingBridgeFrame("1DTransferFunctionEditor")
        val tfUI = TransferFunctionEditor(v)
        bridge.addPanel(tfUI)
        tfUI.name = v.name
        val swingUiNode = bridge.uiNode
        swingUiNode.spatial() {
            position = Vector3f(2f,0f,0f)
        }

        scene.addChild(swingUiNode)
    }

    /**
     * Adds InputBehaviour -> MouseClick, Drag and Ctrl-Click to interact with the SwingUI using a Scenery Plane (SwingUINode)
     */
    override fun inputSetup() {
        super.inputSetup()

        val debugRaycast = false
        inputHandler?.addBehaviour(
            "ctrlClickObject", object : ClickBehaviour {
                override fun click(x: Int, y: Int) {
                    val ray = cam.getNodesForScreenSpacePosition(x,y, listOf<Class<*>>(BoundingGrid::class.java), debugRaycast)

                    ray.matches.firstOrNull()?.let { hit ->
                        val node = hit.node as? SwingUINode ?: return
                        val hitPos = ray.initialPosition + ray.initialDirection * hit.distance
                        node.ctrlClick(hitPos)
                    }
                }
            }
        )
        inputHandler?.addBehaviour(
            "dragObject", object : DragBehaviour {
                override fun init(x:Int, y: Int) {
                    val ray = cam.getNodesForScreenSpacePosition(x,y, listOf<Class<*>>(BoundingGrid::class.java), debugRaycast)

                    ray.matches.firstOrNull()?.let { hit ->
                        val node = hit.node as? SwingUINode ?: return
                        val hitPos = ray.initialPosition + ray.initialDirection * hit.distance
                        node.pressed(hitPos)
                    }
                }
                override fun drag(x: Int, y: Int) {
                    val ray = cam.getNodesForScreenSpacePosition(x,y, listOf<Class<*>>(BoundingGrid::class.java), debugRaycast)

                    ray.matches.firstOrNull()?.let { hit ->
                        val node = hit.node as? SwingUINode ?: return
                        val hitPos = ray.initialPosition + ray.initialDirection * hit.distance
                        node.drag(hitPos)
                    }
                }
                override fun end(x: Int, y: Int) {
                    val ray = cam.getNodesForScreenSpacePosition(x,y, listOf<Class<*>>(BoundingGrid::class.java), debugRaycast)

                    ray.matches.firstOrNull()?.let { hit ->
                        val node = hit.node as? SwingUINode ?: return
                        val hitPos = ray.initialPosition + ray.initialDirection * hit.distance
                        node.released(hitPos)
                    }
                }
            }
        )
        inputHandler?.addKeyBinding("dragObject", "1")
        inputHandler?.addKeyBinding("ctrlClickObject", "2")
    }

    /**
     * Static object for running as application
     */
    companion object {
        /**
         * Main method for the application, that instances and runs the example.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            TransferFunctionEditorExample().main()
        }
    }
}


