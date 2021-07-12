package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.Scene
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
 * Select nodes with the attribute [Selectable] with a VR controller.
 *
 * @author Jan Tiemann
 */
open class VRSelect(
    protected val name: String,
    protected val controller: Node,
    protected val scene: Scene,
    protected val showIndicator: Boolean = true,
    protected val selected: (Node) -> Unit
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

    override fun init(x: Int, y: Int) {
        laser.visible = true
    }

    override fun drag(x: Int, y: Int) {
        val hit = scene.raycast(
            controller.spatialOrNull()!!.worldPosition(),
            laser.spatial().worldRotation().transform(Vector3f(0f, 1f, 0f)),
            emptyList()
        )
            .matches.firstOrNull { it.node.getAttributeOrNull(Selectable::class.java) != null }


        laser.spatial().scale.y = hit?.distance ?: 1000f

        val hitSpatial = hit?.node?.spatialOrNull()

        if (hitSpatial != activeWiggler?.target) {
            //new target or null
            activeWiggler?.deativate()
            activeWiggler = null

            if (hitSpatial != null) {
                activeWiggler = Wiggler(hitSpatial)
            }
        }
    }

    override fun end(x: Int, y: Int) {
        activeWiggler?.deativate()
        activeWiggler = null

        laser.visible = false

        scene.raycast(
            controller.spatialOrNull()!!.worldPosition(),
            laser.spatial().worldRotation().transform(Vector3f(0f, 1f, 0f)),
            emptyList()
        )
            .matches.firstOrNull { it.node.getAttributeOrNull(Selectable::class.java) != null }
            ?.let {
                if (showIndicator) {
                    it.node.ifSpatial {
                        selectionIndicator.to = this
                        selectionIndicator.visible = true
                    }
                }
                selected(it.node)
            }
    }

    class SelectionStorage {
        var selected: Node? = null
    }

    companion object {

        /**
         * Convenience method for adding selection behaviour
         */
        fun createAndSet(
            scene: Scene,
            hmd: OpenVRHMD,
            button: List<OpenVRHMD.OpenVRButton>,
            controllerSide: List<TrackerRole>
        ): SelectionStorage {
            val selectionStorage = SelectionStorage()

            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    device.model?.let { controller ->
                        if (controllerSide.contains(device.role)) {
                            val name = "VRDrag:${hmd.trackingSystemName}:${controllerSide}"
                            val select = VRSelect(
                                name,
                                controller.children.first(),
                                scene
                            ) { selectionStorage.selected = it }

                            hmd.addBehaviour(name, select)
                            button.forEach {
                                hmd.addKeyBinding(name, device.role, it)
                            }
                        }
                    }
                }
            }
            return selectionStorage
        }

    }

}

open class Selectable
