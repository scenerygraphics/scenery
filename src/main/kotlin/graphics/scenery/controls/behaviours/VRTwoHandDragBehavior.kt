package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.utils.lazyLogger
import org.joml.Vector3f
import org.scijava.ui.behaviour.DragBehaviour
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

/**
 * Abstract class to offer two hand drag behavior hooks.
 */
abstract class VRTwoHandDragBehavior(
    private val name: String,
    private val controller: Spatial,
    private val offhand: VRTwoHandDragOffhand,
) : DragBehaviour, Enablable {

    private val logger by lazyLogger()

    // --- two hand drag behavior ---
    //TODO fix make order of presses irrelevant, waits on issue #432
    var bothPressed = false
        private set

    override var enabled: Boolean = true

    /**
     * This function is called by the framework. Usually you don't need to call this.
     */
    override fun init(x: Int, y: Int) {
        if (!enabled) return
        if (offhand.pressed) {
            bothPressed = true
            bothInit()
        }
    }

    /**
     * This function is called by the framework. Usually you don't need to call this.
     */
    override fun drag(x: Int, y: Int) {
        if (!enabled) return
        logger.debug("both drag OH:${offhand.pressed} Both:${bothPressed}")
        if (offhand.pressed && !bothPressed) {
            bothPressed = true
            bothInit()
        }
        if (!offhand.pressed && bothPressed) {
            bothPressed = false
            bothEnd()
        }
        if (bothPressed) {
            bothDrag()
        }
    }

    /**
     * This function is called by the framework. Usually you don't need to call this.
     */
    override fun end(x: Int, y: Int) {
        if (!enabled) return
        if (bothPressed) {
            bothEnd()
            bothPressed = false
        }
    }

    // --- actual behavior ---
    var lastPosMain: Vector3f = Vector3f()
    var lastPosOff: Vector3f = Vector3f()

    private fun bothInit() {
        logger.debug("both init")
        lastPosMain = controller.worldPosition()
        lastPosOff = offhand.controller.worldPosition()
    }

    private fun bothDrag() {
        val curPosMain = controller.worldPosition()
        val curPosOff = offhand.controller.worldPosition()

        dragDelta(curPosMain, curPosOff, lastPosMain, lastPosOff)

        lastPosMain = curPosMain
        lastPosOff = curPosOff
    }

    private fun bothEnd() {
        logger.debug("both end")
    }


    /**
     * Implementation of you drag behavior goes here. Time between current and last should be one frame.
     */
    abstract fun dragDelta(
        currentPositionMain: Vector3f,
        currentPositionOff: Vector3f,
        lastPositionMain: Vector3f,
        lastPositionOff: Vector3f
    )

    companion object {

        /**
         * Convenience method for adding behaviour
         */
        fun createAndSet(
            hmd: OpenVRHMD, button: OpenVRHMD.OpenVRButton, createBehavior: (
                controller: Spatial, offhand: VRTwoHandDragOffhand
            ) -> VRTwoHandDragBehavior
        ): CompletableFuture<VRTwoHandDragBehavior> {
            var mainhandController: Node? = null
            var offhandController: Node? = null
            val future = CompletableFuture<VRTwoHandDragBehavior>()

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

                val offhand = VRTwoHandDragOffhand("offhand", offhandController!!.spatialOrNull()!!)
                hmd.addBehaviour("offhandZoom", offhand)
                hmd.addKeyBinding("offhandZoom", TrackerRole.RightHand, button)

                val tmp = createBehavior(mainhandController!!.spatialOrNull()!!, offhand)
                hmd.addBehaviour("mainhandZoom", tmp)
                hmd.addKeyBinding("mainhandZoom", TrackerRole.LeftHand, button)
                future.complete(tmp)
            }
            return future
        }
    }
}


/**
 * Behavior for the offhand of [VRTwoHandDragBehavior].
 */
class VRTwoHandDragOffhand(val name: String, val controller: Spatial) : DragBehaviour {
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
