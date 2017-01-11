package graphics.scenery

import com.jogamp.opengl.math.Quaternion

/**
 * Detached Head Camera is a Camera subclass that tracks the head orientation
 * in addition to general orientation - useful for HMDs
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class DetachedHeadCamera : Camera() {

    /** Orientation of the user's head */
    var headOrientation: Quaternion = Quaternion(0.0f, 0.0f, 0.0f, 1.0f);

    init {
        this.nodeType = "Camera"
    }

}
