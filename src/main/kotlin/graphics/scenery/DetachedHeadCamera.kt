package graphics.scenery

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.backends.Display
import graphics.scenery.controls.TrackerInput
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
    override var projection: GLMatrix = GLMatrix.getIdentity()
        get() = if(tracker != null && tracker is Display) {
            (tracker as? Display)?.getEyeProjection(0) ?: super.projection
        } else {
            super.projection
        }
        set(value) {
            super.projection = value
            field = value
        }

    override var width: Float = 0.0f
        get() = if(tracker != null && tracker is Display) {
            (tracker as? Display)?.getRenderTargetSize()?.x() ?: super.width
        } else {
            super.width
        }
        set(value) {
            super.width = value
            field = value
        }

    override var height: Float = 0.0f
        get() = if(tracker != null && tracker is Display) {
            (tracker as? Display)?.getRenderTargetSize()?.y() ?: super.width
        } else {
            super.height
        }
        set(value) {
            super.height = value
            field = value
        }

    override var fov: Float = 70.0f
        get() = if(tracker != null && tracker is Display) {
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

//    override var position: GLVector = GLVector(0.0f, 0.0f, 0.0f)
//        get() = if(tracker != null) {
//            field + headPosition
//        } else {
//            field
//        }
//        set(value) {
//            field = value
//        }

    /**
     * Delegate class for getting a head rotation from a [TrackerInput].
     */
    inner class HeadOrientationDelegate {
        /**
         * Returns the TrackerInput's orientation, or a unit Quaternion.
         */
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Quaternion {
            return tracker?.getWorkingTracker()?.getOrientation() ?: Quaternion(0.0f, 0.0f, 0.0f, 1.0f)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Quaternion) {
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
        operator fun getValue(thisRef: Any?, property: KProperty<*>): GLVector {
            return tracker?.getWorkingTracker()?.getPosition() ?: GLVector(0.0f, 0.0f, 0.0f)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Quaternion) {
            throw UnsupportedOperationException()
        }
    }

    /** Position of the user's head */
    val headPosition: GLVector by HeadPositionDelegate()

    /** Orientation of the user's head */
    val headOrientation: Quaternion by HeadOrientationDelegate()

    init {
        this.nodeType = "Camera"
        this.name = "DetachedHeadCamera-${tracker ?: "${counter.getAndIncrement()}"}"
    }

    /**
     * Returns this camera's transformation matrix, taking an eventually existing [TrackerInput]
     * into consideration as well.
     */
    override fun getTransformation(): GLMatrix {
        val tr = GLMatrix.getTranslation(this.position * (-1.0f)).transpose()
//        val r = GLMatrix.fromQuaternion(this.rotation)
//        val hr = GLMatrix.fromQuaternion(this.headOrientation)

        return tracker?.getWorkingTracker()?.getPose()?.times(tr) ?: GLMatrix.fromQuaternion(rotation) * tr
    }

    /**
     * Returns this camera's transformation for eye with index [eye], taking an eventually existing [TrackerInput]
     * into consideration as well.
     */
    override fun getTransformationForEye(eye: Int): GLMatrix {
        val tr = GLMatrix.getTranslation(this.position * (-1.0f)).transpose()
//        val r = GLMatrix.fromQuaternion(this.rotation)
//        val hr = GLMatrix.fromQuaternion(this.headOrientation)

        return tracker?.getWorkingTracker()?.getPoseForEye(eye)?.times(tr) ?: GLMatrix.fromQuaternion(rotation) * tr
    }

    /**
     * Returns this camera's transformation matrix, including a
     * [preRotation] that is applied before the camera's transformation.
     */
    override fun getTransformation(preRotation: Quaternion): GLMatrix {
        val tr = GLMatrix.getTranslation(this.position * (-1.0f) + this.headPosition).transpose()
        val r = GLMatrix.fromQuaternion(preRotation.mult(this.rotation))

        return r * tr
    }

    companion object {
        protected val counter = AtomicInteger(0)
    }
}
