package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackerRole
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.concurrent.CompletableFuture

/** Transform a target node [target] by pressing the same buttons defined in [createAndSet] on both VR controllers.
 * The fastest way to attach the behavior is by using [createAndSet].
 * [onEndCallback] is an optional lambda that is executed once the behavior ends.
 * @author Jan Tiemann
 * @author Samuel Pantze */
class VR2HandNodeTransform(
    name: String,
    controller: Spatial,
    offhand: VRTwoHandDragOffhand,
    val scene: Scene,
    val scaleLocked: Boolean = false,
    val rotationLocked: Boolean = false,
    val positionLocked: Boolean = false,
    val lockYaxis: Boolean = true,
    val target: Node,
    private val onStartCallback: (() -> Unit)? = null,
    private val onDragCallback: (() -> Unit)? = null,
    private val onEndCallback: (() -> Unit)? = null,
    private val resetRotationBtnManager: MultiButtonManager? = null,
    private val resetRotationButton: MultiButtonManager.ButtonConfig? = null,
) : VRTwoHandDragBehavior(name, controller, offhand) {

    /** To trigger the [onStartCallback] regardless of which order of buttons was used. */
    private var startCallbackTriggered = false

    override fun init(x: Int, y: Int) {
        super.init(x, y)
        // Find the button that doesn't lock the y Axis and indicate that it is now pressed
        val transformBtn =
            resetRotationBtnManager?.getRegisteredButtons()
                ?.filter { it.key != resetRotationButton }?.map { it.key }?.firstOrNull()
        if (transformBtn != null) {
            resetRotationBtnManager?.pressButton(transformBtn)
        }
        if (bothPressed) {
            onStartCallback?.invoke()
            startCallbackTriggered = true
        }
    }

    override fun dragDelta(
        currentPositionMain: Vector3f,
        currentPositionOff: Vector3f,
        lastPositionMain: Vector3f,
        lastPositionOff: Vector3f
    ) {

        // Test whether we now press both buttons but the startCallback wasn't triggered yet.
        if (bothPressed && !startCallbackTriggered) {
            onStartCallback?.invoke()
            startCallbackTriggered = true
        }

        val scaleDelta =
            VRScale.getScaleDelta(currentPositionMain, currentPositionOff, lastPositionMain, lastPositionOff)

        val currentDirection = (currentPositionMain - currentPositionOff).normalize()
        val lastDirection = (lastPositionMain - lastPositionOff).normalize()
        if (lockYaxis) {
            lastDirection.y = 0f
            currentDirection.y = 0f
        }

        // Rotation implementation: https://discussions.unity.com/t/two-hand-grabbing-of-objects-in-virtual-reality/219972

        target.let {
            if (!rotationLocked) {
                it.ifSpatial {
                    val rotationDelta = Quaternionf().rotationTo(lastDirection, currentDirection)
                    if (resetRotationBtnManager?.isTwoHandedActive() == true) {
                        // Reset the rotation when the reset button was pressed too
                        rotation = Quaternionf()
                    } else {
                        // Rotate node with respect to the world space delta
                        rotation = Quaternionf(rotationDelta).mul(Quaternionf(rotation))
                    }
                }
            }
            if (!scaleLocked) {
                target.ifSpatial {
                    scale * scaleDelta
                }
            }
            if (!positionLocked) {
                val positionDelta =
                    (currentPositionMain + currentPositionOff) / 2f - (lastPositionMain + lastPositionOff) / 2f
                target.ifSpatial {
                    position.add(positionDelta)
                }
            }
        }
        onDragCallback?.invoke()
    }

    override fun end(x: Int, y: Int) {
        super.end(x, y)
        onEndCallback?.invoke()
        // Find the button that doesn't lock the y Axis and indicate that it is now released
        val transformBtn = resetRotationBtnManager?.getRegisteredButtons()?.filter { it.key != resetRotationButton }?.map {it.key}?.firstOrNull()
        if (transformBtn != null) {
            resetRotationBtnManager?.releaseButton(transformBtn)
        }
        // Reset this flag for the next event
        startCallbackTriggered = false
    }

    companion object {
        /**
         * Convenience method for adding scale behaviour
         */
        fun createAndSet(
            hmd: OpenVRHMD,
            button: OpenVRHMD.OpenVRButton,
            scene: Scene,
            scaleLocked: Boolean = false,
            rotationLocked: Boolean = false,
            positionLocked: Boolean = false,
            lockYaxis: Boolean = true,
            target: Node,
            onStartCallback: (() -> Unit)? = null,
            onDragCallback: (() -> Unit)? = null,
            onEndCallback: (() -> Unit)? = null,
            resetRotationBtnManager: MultiButtonManager? = null,
            resetRotationButton: MultiButtonManager.ButtonConfig? = null,
        ): CompletableFuture<VR2HandNodeTransform> {
            @Suppress("UNCHECKED_CAST") return createAndSet(
                hmd, button
            ) { controller: Spatial, offhand: VRTwoHandDragOffhand ->
                // Assign the yLock button and the right grab button to the button manager to handle multi-button events
                resetRotationButton?.let {
                    resetRotationBtnManager?.registerButtonConfig(it.button, it.trackerRole)
                }
                resetRotationBtnManager?.registerButtonConfig(button, TrackerRole.RightHand)
                VR2HandNodeTransform(
                    "Scaling",
                    controller,
                    offhand,
                    scene,
                    scaleLocked,
                    rotationLocked,
                    positionLocked,
                    lockYaxis,
                    target,
                    onStartCallback,
                    onDragCallback,
                    onEndCallback,
                    resetRotationBtnManager,
                    resetRotationButton
                )
            } as CompletableFuture<VR2HandNodeTransform>
        }
    }
}
