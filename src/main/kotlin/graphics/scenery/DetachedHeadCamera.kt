package graphics.scenery

import graphics.scenery.backends.Display
import graphics.scenery.controls.TrackerInput
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.io.Serializable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.atan
import kotlin.reflect.KProperty

/**
 * Detached Head Camera is a Camera subclass that tracks the head orientation
 * in addition to general orientation - useful for HMDs
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

class DetachedHeadCamera(@Transient var tracker: TrackerInput? = null) : Camera() {

    override var width: Int = 0
        get() = if (tracker != null && tracker is Display && tracker?.initializedAndWorking() == true) {
            (tracker as? Display)?.getRenderTargetSize()?.x() ?: super.width
        } else {
            super.width
        }
        set(value) {
            super.width = value
            field = value
        }

    override var height: Int = 0
        get() = if(tracker != null && tracker is Display && tracker?.initializedAndWorking() == true) {
            (tracker as? Display)?.getRenderTargetSize()?.y() ?: super.width
        } else {
            super.height
        }
        set(value) {
            super.height = value
            field = value
        }

    override var fov: Float = 70.0f
        get() = if(tracker != null && tracker is Display && tracker?.initializedAndWorking() == true) {
            val proj = (tracker as? Display)?.getEyeProjection(0, nearPlaneDistance, farPlaneDistance)
            if(proj != null) {
                atan(1.0f / proj.get(1, 1)) * 2.0f * 180.0f / PI.toFloat()
            } else {
                super.fov
            }
        } else {
            super.fov
        }
        set(value) {
            super.fov = value
            field = value
        }

    /**
     * Delegate class for getting a head rotation from a [TrackerInput].
     */
    inner class HeadOrientationDelegate {
        /**
         * Returns the TrackerInput's orientation, or a unit Quaternion.
         */
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Quaternionf {
            return tracker?.getWorkingTracker()?.getOrientation() ?: Quaternionf(0.0f, 0.0f, 0.0f, 1.0f)
        }

        /**
         * Sets the TrackerInput's orientation to a given Quaternion.
         */
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Quaternionf) {
            throw UnsupportedOperationException()
        }
    }

    /**
     * Delegate class for getting a head translation from a [TrackerInput].
     */
    inner class HeadPositionDelegate : Serializable {
        /**
         * Returns the TrackerInput's translation, or a zero vector.
         */
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Vector3f {
            return tracker?.getWorkingTracker()?.getPosition() ?: Vector3f(0.0f, 0.0f, 0.0f)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Quaternionf) {
            throw UnsupportedOperationException()
        }
    }

    /** Position of the user's head */
    @delegate:Transient
    val headPosition: Vector3f by HeadPositionDelegate()

    /** Orientation of the user's head */
    @delegate:Transient
    val headOrientation: Quaternionf by HeadOrientationDelegate()

    init {
        this.name = "DetachedHeadCamera-${tracker ?: "${counter.getAndIncrement()}"}"
    }

    override fun createSpatial(): CameraSpatial = DetachedHeadCameraSpatial(this)
    
    class DetachedHeadCameraSpatial(private val cam: DetachedHeadCamera) : Camera.CameraSpatial(cam) {

        override var projection: Matrix4f = Matrix4f().identity()
            get() = if (cam.tracker != null && cam.tracker is Display && cam.tracker!!.initializedAndWorking()) {
                (cam.tracker as? Display)?.getEyeProjection(0, cam.nearPlaneDistance, cam.farPlaneDistance)
                    ?: super.projection
            } else {
                super.projection
            }
            set(value) {
                super.projection = value
                field = value
            }

        /**
         * Returns this camera's transformation matrix, taking an eventually existing [TrackerInput]
         * into consideration as well.
         */
        override fun getTransformation(): Matrix4f {
            val tr = Matrix4f().translate(this.position * (-1.0f))
            return cam.tracker?.getWorkingTracker()?.getPose()?.times(tr) ?: (Matrix4f().set(this.rotation) * tr)
        }

        /**
         * Returns this camera's transformation for eye with index [eye], taking an eventually existing [TrackerInput]
         * into consideration as well.
         */
        override fun getTransformationForEye(eye: Int): Matrix4f {
            val tr = Matrix4f().translate(this.position * (-1.0f))
            return cam.tracker?.getWorkingTracker()?.getPoseForEye(eye)?.times(tr)
                ?: (Matrix4f().set(this.rotation) * tr)
        }

        /**
         * Returns this camera's transformation matrix, including a
         * [preRotation] that is applied before the camera's transformation.
         */
        override fun getTransformation(preRotation: Quaternionf): Matrix4f {
            val tr = Matrix4f().translate(this.position * (-1.0f) + cam.headPosition)
            val r = Matrix4f().set(preRotation.mul(this.rotation))
            return r * tr
        }
    }

    companion object {
        protected val counter = AtomicInteger(0)
    }
}
