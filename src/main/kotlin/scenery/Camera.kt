package scenery

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion

/**
 * Camera class that may be targeted or oriented
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates a new camera with default position and right-handed
 *  coordinate system.
 */
open class Camera : Node("Camera") {

    /** Is the camera targeted? */
    var targeted = false
    /** Is this camera active? Setting one camera active will deactivate the others */
    var active = false

    /** Target, used if [targeted] is true */
    var target: GLVector = GLVector(0.0f, 0.0f, 0.0f)
    /** Forward vector of the camera, used if not targeted */
    var forward: GLVector = GLVector(0.0f, 0.0f, 1.0f)
    /** Up vector of the camera, used if not targeted */
    var up: GLVector = GLVector(0.0f, 1.0f, 0.0f)

    /** View matrix of the camera. Setting the view matrix will re-set the forward
     *  vector of the camera according to the given matrix.
     */
    override var view: GLMatrix? = null
        set(m) {
            m?.let {
                this.forward = GLVector(m.get(0, 2), m.get(1, 2), m.get(2, 2)) * -1.0f
            }

            field = m
        }

    /** Rotation of the camera. The rotation is applied after the view matrix */
    override var rotation: Quaternion = Quaternion(0.0f, 0.0f, 0.0f, 1.0f)
        set(q) {
            field = q
        }

    init {
        this.nodeType = "Camera"
    }

    /**
     * Returns the world orientation of the camera
     *
     * FIXME: Currently broken/unused
     *
     * @return Orientation of the camera in world space
     */
    fun getWorldOrientation(): GLVector {
        if (targeted) {
            return (target - position).normalized
        } else {
            val v: GLVector = GLVector(forward.x(), forward.y(), forward.z(), 0.0f)
            //FIXME: val o: GLVector= GLMatrix.fromQuaternion(rotation).mult(v)
            return v
        }
    }
}
