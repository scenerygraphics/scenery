package graphics.scenery.controls.behaviours

import graphics.scenery.Camera
import graphics.scenery.controls.OpenXRHMD
import graphics.scenery.controls.TrackedDevice
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.utils.lazyLogger
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.pow

/**
 * Smooth camera movement with a thumbstick, supports all direction vectors and dynamic speed.
 * Hook up the behavior to the [hmd] either by calling [registerBehavior] or [createAndSet] (for convenience).
 * Deregister again with [deregisterBehavior].
 * @param hmd the [OpenXRHMD] to use
 * @param role whether to use the left or right controller's thumbstick
 * @param cam the camera to move
 * @param speedCurveFactor power factor for creating nonlinearly accelerated movement, along e.g. for more thumbstick range
 * to be reserved for slow movement
 * @param maxSpeed the allowed maximum speed
 * @param gripOffset not all controllers have the thumbstick plane on their controller's XZ plane, so the movement plane can
 * be rotated here with a [Quaternionf]
 * @author Samuel Pantze
 * */
class ThumbstickMovement(
    val hmd: OpenXRHMD,
    val role: TrackerRole,
    val cam: Camera,
    val speedCurveFactor: Float = 1.0f,
    val maxSpeed: Float = 1.5f,
    gripOffset: Quaternionf = Quaternionf()
) {

    val logger by lazyLogger()

    lateinit var controller: TrackedDevice
    lateinit var behaviorName: String
    var isRegistered = false

    private val thumbstickMovement = { vector: Vector2f ->

        val len = vector.length()
        val scaled = Vector2f(vector).mul(len.pow(speedCurveFactor))

        val moveVec = Vector3f(scaled.x, 0f, -scaled.y)

        val controllerQuat = controller.orientation

        val ctrlAim = Quaternionf(controllerQuat).conjugate().mul(gripOffset)
        ctrlAim.transform(moveVec)

        moveVec.mul(0.01f * maxSpeed)
        if (cam.lock.tryLock()) {
            try {
                cam.spatial { position.add(moveVec) }
            } finally {
                cam.lock.unlock()
            }
        }
    }

    /** Register the movement behavior to the [hmd]. */
    fun registerBehavior() {
        val controllers = hmd.getTrackedDevices(TrackedDeviceType.Controller).map { it.value }
        controller = controllers.firstOrNull { it.role == role } ?: throw IllegalStateException("Controller not registered")
        behaviorName = "${if (role == TrackerRole.LeftHand) "left" else "right"} hand movement"
        isRegistered = true
        hmd.registerThumbStickEvent(role, thumbstickMovement, behaviorName)
        logger.debug("Successfully registered thumbstick $behaviorName.")
    }

    /** Removes this behavior from the [hmd]. */
    fun deregisterBehavior() {
        if (!isRegistered) {
            logger.warn("Behavior isn't registered yet, can't deregister it.")
            return
        }
        isRegistered = false
        hmd.clearThumbStickEvent(behaviorName)
        logger.debug("Successfully deregistered thumbstick $behaviorName.")
    }

    companion object {
        /** Convenience function to create a new instance of [ThumbstickMovement] and register it in one go. */
        fun createAndSet(
            hmd: OpenXRHMD,
            role: TrackerRole,
            cam: Camera,
            speedCurveFactor: Float = 1.0f,
            maxSpeed: Float = 1.5f,
            gripOffset: Quaternionf = Quaternionf()
        ): ThumbstickMovement {
            val behavior = ThumbstickMovement(hmd, role, cam, speedCurveFactor, maxSpeed, gripOffset)
            behavior.registerBehavior()
            return behavior
        }
    }
}
