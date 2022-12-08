package microscenery.VRUI.swingBridge

import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.controls.behaviours.Selectable
import graphics.scenery.primitives.Cylinder
import graphics.scenery.primitives.LineBetweenNodes
import graphics.scenery.ui.SwingUiNode
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.extensions.plus
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.ui.behaviour.DragBehaviour
import kotlin.math.PI

open class VRUICursor(
    protected val name: String,
    protected val controller: Node,
    protected val scene: Scene
) : DragBehaviour, ClickBehaviour {

    private val laser = Cylinder(0.0025f, 3f, 20)
    private val selectionIndicator: LineBetweenNodes

    private val logger by LazyLogger()

    init {
        laser.material().diffuse = Vector3f(1.0f, 0.0f, 0.0f)
        laser.material().metallic = 0.0f
        laser.material().roughness = 1.0f
        laser.spatial().rotation.rotateX(-PI.toFloat() * 1.25f / 2.0f)
        laser.visible = true

        if (controller.spatialOrNull() == null) {
            throw IllegalArgumentException("The controller needs to have a spatial property!")
        }

        controller.addChild(laser)
        selectionIndicator = LineBetweenNodes(
            laser.spatial(), laser.spatial(),
            transparent = false,
            simple = true
        )
        selectionIndicator.visible = false
        scene.addChild(selectionIndicator)
        logger.info("hi")
    }

    override fun click(x: Int, y: Int) {
        val ray = scene.raycast(
            controller.spatialOrNull()!!.worldPosition(),
            laser.spatial().worldRotation().transform(Vector3f(0f, 1f, 0f))
        )
        ray.matches.firstOrNull()?.let { hit ->
            val node = hit.node as? SwingUiNode ?: hit.node.parent as? SwingUiNode ?: return //backside might get hit first
            val hitPos = ray.initialPosition + ray.initialDirection.normalize(hit.distance)
            node.ctrlClick(hitPos)
            logger.info("CtrlClicked on $hitPos")
        }
        logger.info("CtrlClick")
    }

    /**
     * Activates the target las0r.
     */
    override fun init(x: Int, y: Int) {
        laser.material().diffuse = Vector3f(0.0f, 1.0f, 0.0f)

        val ray = scene.raycast(
            controller.spatialOrNull()!!.worldPosition(),
            laser.spatial().worldRotation().transform(Vector3f(0f, 1f, 0f))
        )
        ray.matches.firstOrNull()?.let { hit ->
            val node = hit.node as? SwingUiNode ?: hit.node.parent as? SwingUiNode ?: return //backside might get hit first
            val hitPos = ray.initialPosition + ray.initialDirection.normalize(hit.distance)
            node.pressed(hitPos)
            logger.info("Pressed on $hitPos")
        }
        logger.info("Pressed")

    }

    /**
     * Adjust the length of the target laser visualisation.
     */
    override fun drag(x: Int, y: Int) {
        val ray = scene.raycast(
            controller.spatialOrNull()!!.worldPosition(),
            laser.spatial().worldRotation().transform(Vector3f(0f, 1f, 0f))
        )
        var distance : Float? = null
        ray.matches.firstOrNull()?.let { hit ->
            hit.node.getAttributeOrNull(Selectable::class.java) != null
            val node = hit.node as? SwingUiNode ?: hit.node.parent as? SwingUiNode ?: return //backside might get hit first
            val hitPos = ray.initialPosition + ray.initialDirection.normalize(hit.distance)
            distance = hit.distance
            node.drag(hitPos)
            logger.info("dragged on $hitPos")
        }
        logger.info("Dragged")


        laser.spatial().scale.y = distance ?: 1000f
    }

    /**
     * Performs the selection
     */
    override fun end(x: Int, y: Int) {
        laser.material().diffuse = Vector3f(1.0f, 0.0f, 0.0f)
        logger.info("Released")

        val ray = scene.raycast(
            controller.spatialOrNull()!!.worldPosition(),
            laser.spatial().worldRotation().transform(Vector3f(0f, 1f, 0f))
        )
        ray.matches.firstOrNull()?.let { hit ->
            val node = hit.node as? SwingUiNode ?: hit.node.parent as? SwingUiNode ?: return //backside might get hit first
            val hitPos = ray.initialPosition + ray.initialDirection.normalize(hit.distance)
            node.released(hitPos)
            logger.info("Released on $hitPos")
        }

    }

    companion object {
        /**
         * Convenience method for adding grab behaviour
         */
        fun createAndSet(
            scene: Scene,
            hmd: OpenVRHMD,
            buttons: List<OpenVRHMD.OpenVRButton>,
            controllerSide: List<TrackerRole>
        ) {

            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    device.model?.let { controller ->
                        if (controllerSide.contains(device.role)) {
                            buttons.forEach { button ->
                                val name = "VRUiCursor:${hmd.trackingSystemName}:${device.role}:$button"
                                val behaviour = VRUICursor(
                                    name,
                                    controller.children.first(),
                                    scene
                                )
                                hmd.addBehaviour(name, behaviour)
                                hmd.addKeyBinding(name, device.role, button)
                            }
                        }
                    }
                }
            }
        }
    }
}
