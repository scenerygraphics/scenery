package graphics.scenery

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.controls.HMDInput
import kotlin.reflect.KProperty

/**
 * Detached Head Camera is a Camera subclass that tracks the head orientation
 * in addition to general orientation - useful for HMDs
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

class DetachedHeadCamera(var hmd: HMDInput? = null) : Camera() {

    inner class HeadOrientationDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Quaternion {
            return hmd?.getOrientation() ?: Quaternion(0.0f, 0.0f, 0.0f, 1.0f)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Quaternion) {
            throw UnsupportedOperationException()
        }
    }

    inner class HeadPositionDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): GLVector {
            return hmd?.getPosition() ?: GLVector(0.0f, 0.0f, 0.0f)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Quaternion) {
            throw UnsupportedOperationException()
        }
    }

    /** Orientation of the user's head */
    val headOrientation: Quaternion by HeadOrientationDelegate()
    val headPosition: GLVector by HeadPositionDelegate()

    init {
        this.nodeType = "Camera"
    }

    override fun getTransformation(): GLMatrix {
        val tr = GLMatrix.getTranslation(this.position * (-1.0f)).transpose()
//        val r = GLMatrix.fromQuaternion(this.rotation)
//        val hr = GLMatrix.fromQuaternion(this.headOrientation)

        return hmd!!.getPose() * tr
    }

    override fun getTransformation(preRotation: Quaternion): GLMatrix {
        val tr = GLMatrix.getTranslation(this.position * (-1.0f) + this.headPosition).transpose()
        val r = GLMatrix.fromQuaternion(preRotation.mult(this.rotation))

        return r * tr
    }

}
