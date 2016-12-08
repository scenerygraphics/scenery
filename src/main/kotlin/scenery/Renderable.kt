package scenery

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import java.util.concurrent.locks.ReentrantLock

/**
 * Generic interface for objects that can be rendered
 *
 * Matrices that are set to null shall be treated as identity matrix
 * by the renderer. See e.g. [projection] or [view].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface Renderable {
    /** Model matrix **/
    var model: GLMatrix
    /** Inverse [model] matrix */
    var imodel: GLMatrix

    /** World transform matrix */
    var world: GLMatrix
    /** Inverse of [world] */
    var iworld: GLMatrix

    /** View matrix. May be null. */
    var view: GLMatrix
    /** Inverse of [view] matrix. May be null. */
    var iview: GLMatrix
    /** Projection matrix. May be null. */
    var projection: GLMatrix
    /** Inverse of [projection]. May be null. */
    var iprojection: GLMatrix
    /** modelView matrix. May be null. */
    var modelView: GLMatrix
    /** Inverse of [modelView]. May be null. */
    var imodelView: GLMatrix
    /** ModelViewProjection matrix. May be null. */
    var mvp: GLMatrix

    /** World position of the [Renderable] object. */
    var position: GLVector
    /** X/Y/Z scale of the object. */
    var scale: GLVector
    /** Quaternion defining the rotation of the object in local coordinates. */
    var rotation: Quaternion

    /** Whether the object has been initialized. Used by renderers. */
    var initialized: Boolean
    /** Whether the object is dirty and somehow needs to be updated. Used by renderers. */
    var dirty: Boolean
    /** Flag to set whether the object is visible or not. */
    var visible: Boolean
    /** Flag to set whether the object is a billboard and will always face the camera. */
    var isBillboard: Boolean

    /** The [Material] of the object. */
    var material: Material?

    /** [ReentrantLock] to be used if the object is being updated and should not be
     * touched in the meantime. */
    var lock: ReentrantLock

    /** Initialisation function for the object */
    fun init(): Boolean
}
