package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.controls.behaviours.VRGrab.Companion.createAndSet
import graphics.scenery.controls.behaviours.VRPress.Companion.createAndSet
import graphics.scenery.utils.LazyLogger
import org.scijava.ui.behaviour.DragBehaviour
import kotlin.concurrent.thread

/**
 * Behavior for scaling with the distance between the controllers.
 *
 * Use the [createAndSet] method to create.
 *
 * When triggered [setScale] is called with the relative difference of the distance between the controllers
 * of this and the previous frame.
 * For easier set-up use the convenience method in the companion object [createAndSet].
 *
 * @author Jan Tiemann
 */
class VRScale(
    private val name: String,
    private val controller: Spatial,
    private val offhand: VRScaleOffhand,
    private val setScale: (Float) -> Unit
) :
    DragBehaviour {

    private val logger by LazyLogger()

    // --- two hand drag behavior ---
    //TODO fix make order of presses irrelevant, waits on issue #432
    private var bothPressed = false
    /**
     * This function is called by the framework. Usually you don't need to call this.
     */
    override fun init(x: Int, y: Int) {
        if (offhand.pressed){
            bothPressed = true
            bothInit()
        }
    }
    /**
     * This function is called by the framework. Usually you don't need to call this.
     */
    override fun drag(x: Int, y: Int) {
        logger.debug("both drag OH:${offhand.pressed} Both:${bothPressed}")
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

    /**
     * This function is called by the framework. Usually you don't need to call this.
     */
    override fun end(x: Int, y: Int) {
        if (bothPressed){
            bothEnd()
            bothPressed = false
        }
    }


    // --- actual behavior ---
    var lastDistance: Float = 0f
    private fun bothInit() {
        logger.debug("both init")
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
        logger.debug("both end")
        lastDistance = 0f
    }


    /**
     * Contains Convenience method for adding zoom behaviour
     */
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

/**
 * Behavior for the offhand of [VRScale].
 */
class VRScaleOffhand(val name: String, val controller: Spatial) : DragBehaviour {
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
