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

    inner class HeadOrientationDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Quaternion {
            return tracker?.getWorkingTracker()?.getOrientation() ?: Quaternion(0.0f, 0.0f, 0.0f, 1.0f)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Quaternion) {
            throw UnsupportedOperationException()
        }
    }

    inner class HeadPositionDelegate : Serializable {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): GLVector {
            return tracker?.getWorkingTracker()?.getPosition() ?: GLVector(0.0f, 0.0f, 0.0f)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Quaternion) {
            throw UnsupportedOperationException()
        }
    }

    /** Orientation of the user's head */
//    val headOrientation: Quaternion by HeadOrientationDelegate()
    val headPosition: GLVector by HeadPositionDelegate()

    init {
        this.nodeType = "Camera"
        this.name = "DetachedHeadCamera-${tracker ?: "0"}"
    }

    override fun getTransformation(): GLMatrix {
        val tr = GLMatrix.getTranslation(this.position * (-1.0f)).transpose()
//        val r = GLMatrix.fromQuaternion(this.rotation)
//        val hr = GLMatrix.fromQuaternion(this.headOrientation)

        return tracker?.getWorkingTracker()?.getPose()?.times(tr) ?: GLMatrix.fromQuaternion(rotation) * tr
    }

    override fun getTransformation(preRotation: Quaternion): GLMatrix {
        val tr = GLMatrix.getTranslation(this.position * (-1.0f) + this.headPosition).transpose()
        val r = GLMatrix.fromQuaternion(preRotation.mult(this.rotation))

        return r * tr
    }

}
