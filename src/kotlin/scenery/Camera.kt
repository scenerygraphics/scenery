@file:JvmName("Camera")
package scenery

import cleargl.GLMatrix
import cleargl.GLVector

open class Camera : Node("Camera") {

    var targeted = false
    var active = false

    protected var target: GLVector = GLVector(0.0f, 0.0f, 0.0f)
    protected var forward: GLVector = GLVector(0.0f, 1.0f, 0.0f)

    init {
        this.nodeType = "Camera"
    }

    fun getWorldOrientation(): GLVector {
        if(targeted) {
            return (target - position!!).getNormalized()
        }
        else {
            val v: GLVector = GLVector(forward.x(), forward.y(), forward.z(), 0.0f)
            //FIXME: val o: GLVector= GLMatrix.fromQuaternion(rotation).mult(v)
            return GLVector(1.0f, 0.0f, 0.0f)
        }
    }
}
