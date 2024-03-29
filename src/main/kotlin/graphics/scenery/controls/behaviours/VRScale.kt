package graphics.scenery.controls.behaviours

import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import org.joml.Vector3f
import java.util.concurrent.CompletableFuture

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
    name: String, controller: Spatial, offhand: VRTwoHandDragOffhand, private val setScale: (Float) -> Unit
) : VRTwoHandDragBehavior(
    name,
    controller,
    offhand) {

    override fun dragDelta(
        currentPositionMain: Vector3f,
        currentPositionOff: Vector3f,
        lastPositionMain: Vector3f,
        lastPositionOff: Vector3f
    ) {
        setScale(getScaleDelta(currentPositionMain, currentPositionOff, lastPositionMain, lastPositionOff))
    }

    /**
     * Contains Convenience method for adding zoom behaviour
     */
    companion object {

        /**
         * Get relative distance delta between controller positions of two time points
         */
        fun getScaleDelta(
            currentPositionMain: Vector3f,
            currentPositionOff: Vector3f,
            lastPositionMain: Vector3f,
            lastPositionOff: Vector3f
        ): Float {
            val lastDistance = lastPositionMain.distance(lastPositionOff)
            val newDistance = currentPositionMain.distance(currentPositionOff)
            val scale = newDistance / lastDistance
            return scale
        }

        /**
         * Convenience method for adding scale behaviour
         */
        fun createAndSet(
            hmd: OpenVRHMD, button: OpenVRHMD.OpenVRButton, setScale: (Float) -> Unit
        ): CompletableFuture<VRScale> {
            @Suppress("UNCHECKED_CAST") return createAndSet(
                hmd,
                button
            ) { controller: Spatial, offhand: VRTwoHandDragOffhand ->
                VRScale("Scaling", controller, offhand, setScale)
            } as CompletableFuture<VRScale>
        }
    }
}

