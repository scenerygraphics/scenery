package graphics.scenery.ui

import graphics.scenery.utils.extensions.plus
import graphics.scenery.RichNode
import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerInput
import graphics.scenery.controls.TrackerRole
import graphics.scenery.controls.behaviours.WheelMenu
import org.joml.Quaternionf
import org.joml.Vector3f
import org.scijava.ui.behaviour.DragBehaviour


/**
 *  @author Jan Tiemann
 *  */
class VR3DGui(
    val controller: Spatial,
    val scene: Scene,
    val hmd: TrackerInput,
    val trackingMode: WheelMenu.TrackingMode,
    var offset: Vector3f,
    var scale: Float,
    val ui: Column,
) : DragBehaviour {

    class VR3DGuiRootNode(val owner: VR3DGui) : RichNode("VRUi3D") {
        val grabDummy = RichNode()

        init {
            grabDummy.update += {
                grabDummy.spatial {
                    owner.offset += position
                    if (owner.trackingMode == WheelMenu.TrackingMode.START) /*rootNode.*/ spatial().position += position
                    position = Vector3f()
                }
            }
        }
    }

    val root = VR3DGuiRootNode(this)
    val scalePivot = RichNode().apply { root.addChild(this) }
    val longPressTime = 5000
    var startPressTime = 0L

    init {
        scalePivot.addChild(ui)
        root.update.add {
            if (trackingMode == WheelMenu.TrackingMode.LIVE) {
                ui.spatial().rotation = Quaternionf(hmd.getOrientation()).conjugate().normalize()
                root.spatial().position = controller.worldPosition() + offset
            } else {
                offset = Vector3f()
            }
        }
    }


    /**
     * This function is called by the framework. Usually you don't need to call this.
     */
    override fun init(x: Int, y: Int) {
        startPressTime = System.currentTimeMillis()
    }

    /**
     * This function is called by the framework. Usually you don't need to call this.
     */
    override fun drag(x: Int, y: Int) {
        if (root.parent != null && startPressTime + longPressTime < System.currentTimeMillis()) {
            offset = Vector3f()
            root.spatial().position = controller.worldPosition() + offset
        }
    }

    /**
     * This function is called by the framework. Usually you don't need to call this.
     */
    override fun end(x: Int, y: Int) {
        if (startPressTime + longPressTime < System.currentTimeMillis()) return // long press happend

        if (root.parent == null) {
            root.spatial().position = controller.worldPosition() + offset

            if (trackingMode == WheelMenu.TrackingMode.START) {
                ui.spatial().rotation = Quaternionf(hmd.getOrientation()).conjugate().normalize()
            }
            scalePivot.spatial().scale = Vector3f(scale)

            scene.addChild(root)
            if (ui is TabbedMenu) {
                ui.onActivate()
            }
        } else {
            root.detach()
        }
    }

    /**
     * Contains Convenience method for adding tool select behaviour
     */
    companion object {

        /**
         * Convenience method for adding tool select behaviour
         */
        fun createAndSet(
            scene: Scene,
            hmd: OpenVRHMD,
            buttons: List<OpenVRHMD.OpenVRButton>,
            controllerSide: List<TrackerRole>,
            trackingMode: WheelMenu.TrackingMode = WheelMenu.TrackingMode.START,
            offset: Vector3f = Vector3f(0.15f, 0f, 0.1f),
            scale: Float = 0.05f,
            ui: Column,
        ): List<VR3DGui> {
            val future = mutableListOf<VR3DGui>()
            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    device.model?.let { controller ->
                        if (controllerSide.contains(device.role)) {
                            val behavior = VR3DGui(
                                controller.children.first().spatialOrNull()
                                    ?: throw IllegalArgumentException("The target controller needs a spatial."),
                                scene,
                                hmd,
                                trackingMode,
                                offset,
                                scale,
                                ui
                            )
                            buttons.forEach { button ->
                                val name = "Ui3DWindow:${hmd.trackingSystemName}:${device.role}:$button"
                                hmd.addBehaviour(name, behavior)
                                hmd.addKeyBinding(name, device.role, button)
                                future.add(behavior)
                            }
                        }
                    }
                }
            }
            return future
        }
    }
}
