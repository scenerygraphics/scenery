package graphics.scenery

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.controls.TrackerInput
import java.io.Serializable
import kotlin.reflect.KProperty

/**
 * Detached Head Camera is a Camera subclass that tracks the head orientation
 * in addition to general orientation - useful for HMDs
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

class DetachedHeadCamera(@Transient var tracker: TrackerInput? = null) : Camera() {

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

    /** Orientation of the user's head */
    val headPosition: GLVector by HeadPositionDelegate()

    init {
        this.nodeType = "Camera"
        this.name = "DetachedHeadCamera-${tracker ?: "0"}"
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
}
