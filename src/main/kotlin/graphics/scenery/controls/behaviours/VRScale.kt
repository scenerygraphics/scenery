package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import org.scijava.ui.behaviour.DragBehaviour
import kotlin.concurrent.thread


class VRScale(
    private val name: String,
    private val controller: Node,
    private val offhand: VRScaleOffhand,
    private val setScale: (Float) -> Unit
) :
    DragBehaviour {

    // --- two hand drag behavior ---
    private var bothPressed = false
    override fun init(x: Int, y: Int) {}
    override fun drag(x: Int, y: Int) {
        if (!offhand.pressed) {
            bothPressed = false
            bothEnd()
        } else if (!bothPressed) {
            bothPressed = true
            bothInit()
        } else {
            bothDrag()
        }
    }

    override fun end(x: Int, y: Int) {}


    // --- actual behavior ---
    var startDistance: Float = 0f
    private fun bothInit() {
        startDistance = controller.spatialOrNull()!!.worldPosition()
            .distance(offhand.controller.spatialOrNull()!!.worldPosition())
    }

    private fun bothDrag() {
        val newDistance = controller.spatialOrNull()!!.worldPosition()
            .distance(offhand.controller.spatialOrNull()!!.worldPosition())
        val scale = newDistance / startDistance
        startDistance = controller.spatialOrNull()!!.worldPosition()
            .distance(offhand.controller.spatialOrNull()!!.worldPosition())
        setScale(scale)
    }

    private fun bothEnd() {
        startDistance = 0f
    }


    companion object {

        /**
         * Convenience method for adding zoom behaviour
         */
        fun createAndSet(
            hmd: OpenVRHMD,
            button: OpenVRHMD.OpenVRButton,
            setScale: (Float) -> Unit
        ) {
            var mainhandController: Node? = null
            var offhandController: Node? = null

            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    device.model?.let { controller ->

                        when (device.role) {
                            TrackerRole.RightHand -> {
                                offhandController = controller.children.first()
                            }
                            TrackerRole.LeftHand -> {
                                mainhandController = controller.children.first()
                            }
                            else -> {
                            }
                        }
                    }

                }
            }

            thread {
                while (offhandController == null || mainhandController == null) {
                    Thread.sleep(1000)
                }

                val offhand = VRScaleOffhand("offhand", offhandController!!)
                hmd.addBehaviour("offhandZoom", offhand)
                hmd.addKeyBinding("offhandZoom", TrackerRole.RightHand, button)

                hmd.addBehaviour("mainhandZoom", VRScale("mainhand", mainhandController!!, offhand, setScale))
                hmd.addKeyBinding("mainhandZoom", TrackerRole.LeftHand, button)
            }

        }
    }

}

class VRScaleOffhand(val name: String, val controller: Node) : DragBehaviour {
    var pressed = false
    override fun init(x: Int, y: Int) {
        pressed = true
    }

    override fun drag(x: Int, y: Int) {
    }

    override fun end(x: Int, y: Int) {
        pressed = false
    }
}
