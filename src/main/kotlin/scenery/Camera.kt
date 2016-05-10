@file:JvmName("Camera")
package scenery

import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion

open class Camera : Node("Camera") {

    var targeted = false
    var active = false

    var target: GLVector = GLVector(0.0f, 0.0f, 0.0f)
    var forward: GLVector = GLVector(0.0f, 0.0f, 1.0f)
    var up: GLVector = GLVector(0.0f, 1.0f, 0.0f)

    override var rotation: Quaternion = Quaternion(0.0f, 0.0f, 0.0f, 1.0f)
        set(q) {
            field = q
        }

    init {
        this.nodeType = "Camera"
    }

    fun getWorldOrientation(): GLVector {
        if(targeted) {
            return (target - position).normalized
        }
        else {
            val v: GLVector = GLVector(forward.x(), forward.y(), forward.z(), 0.0f)
            //FIXME: val o: GLVector= GLMatrix.fromQuaternion(rotation).mult(v)
            return GLVector(1.0f, 0.0f, 0.0f)
        }
    }
}
