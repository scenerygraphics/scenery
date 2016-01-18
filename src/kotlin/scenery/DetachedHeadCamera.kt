@file:JvmName("DetachedHeadCamera")
package scenery

import com.jogamp.opengl.math.Quaternion

class DetachedHeadCamera : Camera() {

    var headOrientation: Quaternion = Quaternion(0.0f, 0.0f, 0.0f, 1.0f);

    init {
        this.nodeType = "Camera"
    }

}
