package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.HasSpatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.primitives.Cylinder
import graphics.scenery.primitives.LineBetweenNodes
import graphics.scenery.utils.Wiggler
import org.joml.Vector3f
import org.scijava.ui.behaviour.DragBehaviour
import kotlin.math.PI


/**
 * Point and select nodes with the attribute [Selectable] with a VR controller.
 *
 * Use the [createAndSet] method to create.
 *
 * When triggered a ray will shoot out from the controller. The ray stops at the first collision with a node with a
 *  attribute and wiggles it to indicate a hit. Then when the user releases the trigger button [onSelect] and
 *  [Selectable.onSelect] of the node are called.
 *
 *  @param showIndicator whether a thin line should indicate the last selection
 *
 * @author Jan Tiemann
 */
open class VRSelect(
    protected val name: String,
    protected val controller: Node,
    protected val scene: Scene,
    protected val showIndicator: Boolean = true,
    protected val onSelect: ((Node) -> Unit)?
) : DragBehaviour {

    private var activeWiggler: Wiggler? = null

    private val laser = Cylinder(0.0025f, 1f, 20)
    private val selectionIndicator: LineBetweenNodes

    init {
        laser.material().diffuse = Vector3f(5.0f, 0.0f, 0.02f)
        laser.material().metallic = 0.0f
        laser.material().roughness = 1.0f
        laser.spatial().rotation.rotateX(-PI.toFloat() * 1.25f / 2.0f)
        laser.visible = false

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
    }

    /**
     * Activates the target las0r.
     */
    override fun init(x: Int, y: Int) {
        laser.visible = true
    }

    /**
     * Wiggles potential targets and adjust the length of the target laser visualisation.
     */
    override fun drag(x: Int, y: Int) {
        val hit = scene.raycast(
            controller.spatialOrNull()!!.worldPosition(),
            laser.spatial().worldRotation().transform(Vector3f(0f, 1f, 0f))
        )
            .matches.firstOrNull { it.node.getAttributeOrNull(Selectable::class.java) != null }


        laser.spatial().scale.y = hit?.distance ?: 1000f

        val hitNode = hit?.node as? HasSpatial

        if (hitNode != activeWiggler?.target) {
            //new target or null
            activeWiggler?.deativate()
            activeWiggler = null

            if (hitNode != null) {
                activeWiggler = Wiggler(hitNode)
            }
        }
    }

    /**
     * Performs the selection
     */
    override fun end(x: Int, y: Int) {
        activeWiggler?.deativate()
        activeWiggler = null

        laser.visible = false

        scene.raycast(
            controller.spatialOrNull()!!.worldPosition(),
            laser.spatial().worldRotation().transform(Vector3f(0f, 1f, 0f))
        )
            .matches.firstOrNull { it.node.getAttributeOrNull(Selectable::class.java) != null }
            ?.let {
                if (showIndicator) {
                    it.node.ifSpatial {
                        selectionIndicator.to = this
                        selectionIndicator.visible = true
                    }
                }
                it.node.getAttributeOrNull(Selectable::class.java)?.onSelect?.invoke()
                onSelect?.invoke(it.node)
            }
    }

    /**
     * Contains Convenience method for adding selection behaviour.
     */
    companion object {

        /**
         * Convenience method for adding selection behaviour. [action] is performed with a successfully selected node.
         * @param showIndicator whether a thin red line should indicate the last selection.
         */
        fun createAndSet(
            scene: Scene,
            hmd: OpenVRHMD,
            button: List<OpenVRHMD.OpenVRButton>,
            controllerSide: List<TrackerRole>,
            action: (Node) -> Unit = { },
            showIndicator: Boolean = false
        ) {
            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    device.model?.let { controller ->
                        if (controllerSide.contains(device.role)) {
                            val name = "VRDrag:${hmd.trackingSystemName}:${device.role}:$button"
                            val select = VRSelect(
                                name,
                                controller.children.first(),
                                scene,
                                showIndicator
                            ) { node ->
                                hmd.vibrate(device)
                                action(node)
                            }
                            hmd.addBehaviour(name, select)
                            button.forEach {
                                hmd.addKeyBinding(name, device.role, it)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Attribute which marks a node than can be selected by the [VRSelect] behavior.
 *
 * @param onSelect called upon a successful selection.
 */
open class Selectable(
    val onSelect: (() -> Unit)? = null
)
