package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.utils.LazyLogger
import org.scijava.ui.behaviour.DragBehaviour
import kotlin.concurrent.thread


class VRScale(
    private val name: String,
    private val controller: Spatial,
    private val offhand: VRScaleOffhand,
    private val setScale: (Float) -> Unit
) :
    DragBehaviour {

    protected val logger by LazyLogger(System.getProperty("scenery.LogLevel", "info"))

    // --- two hand drag behavior ---
    //TODO fix make order of presses irrelevant
    private var bothPressed = false
    override fun init(x: Int, y: Int) {
        if (offhand.pressed){
            bothPressed = true
            bothInit()
        }
    }
    override fun drag(x: Int, y: Int) {
        logger.info("both drag OH:${offhand.pressed} Both:${bothPressed}")
        if (offhand.pressed && !bothPressed){
            bothPressed = true
            bothInit()
        }
        if (!offhand.pressed && bothPressed){
            bothPressed = false
            bothEnd()
        }
        if (bothPressed){
            bothDrag()
        }
    }

    override fun end(x: Int, y: Int) {
        if (bothPressed){
            bothEnd()
            bothPressed = false
        }
    }


    // --- actual behavior ---
    var lastDistance: Float = 0f
    private fun bothInit() {
        logger.warn("both init")
        lastDistance = controller.worldPosition()
            .distance(offhand.controller.worldPosition())
    }

    private fun bothDrag() {
        val newDistance = controller.worldPosition()
            .distance(offhand.controller.worldPosition())
        val scale = newDistance / lastDistance
        lastDistance = newDistance
        setScale(scale)
    }

    private fun bothEnd() {
        logger.warn("both end")
        lastDistance = 0f
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

                val offhand = VRScaleOffhand("offhand", offhandController!!.spatialOrNull()!!)
                hmd.addBehaviour("offhandZoom", offhand)
                hmd.addKeyBinding("offhandZoom", TrackerRole.RightHand, button)

                hmd.addBehaviour("mainhandZoom", VRScale("mainhand", mainhandController!!.spatialOrNull()!!, offhand, setScale))
                hmd.addKeyBinding("mainhandZoom", TrackerRole.LeftHand, button)
            }

        }
    }

}

class VRScaleOffhand(val name: String, val controller: Spatial) : DragBehaviour {
    var pressed = false
    protected val logger by LazyLogger(System.getProperty("scenery.LogLevel", "info"))
    override fun init(x: Int, y: Int) {
        logger.warn("offhand init")
        pressed = true
    }

    override fun drag(x: Int, y: Int) {
    }

    override fun end(x: Int, y: Int) {
        logger.warn("offhand end")
        pressed = false
    }
}
