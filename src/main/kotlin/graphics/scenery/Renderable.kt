package graphics.scenery

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
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
    var model: Matrix4f
    /** Inverse [model] matrix */
    var imodel: Matrix4f

    /** World transform matrix */
    var world: Matrix4f
    /** Inverse of [world] */
    var iworld: Matrix4f

    /** View matrix. May be null. */
    var view: Matrix4f
    /** Inverse of [view] matrix. May be null. */
    var iview: Matrix4f
    /** Projection matrix. May be null. */
    var projection: Matrix4f
    /** Inverse of [projection]. May be null. */
    var iprojection: Matrix4f
    /** modelView matrix. May be null. */
    var modelView: Matrix4f
    /** Inverse of [modelView]. May be null. */
    var imodelView: Matrix4f
    /** ModelViewProjection matrix. May be null. */
    var mvp: Matrix4f

    /** World position of the [Renderable] object. */
    var position: Vector3f
    /** X/Y/Z scale of the object. */
    var scale: Vector3f
    /** Quaternion defining the rotation of the object in local coordinates. */
    var rotation: Quaternionf

    /** Whether the object has been initialized. Used by renderers. */
    var initialized: Boolean

    /** State of the Node **/
    var state: State
    /** Whether the object is dirty and somehow needs to be updated. Used by renderers. */
    var dirty: Boolean
    /** Flag to set whether the object is visible or not. */
    var visible: Boolean
    /** Flag to set whether the object is a billboard and will always face the camera. */
    var isBillboard: Boolean

    /** The [Material] of the object. */
    var material: Material

    /** [ReentrantLock] to be used if the object is being updated and should not be
     * touched in the meantime. */
    var lock: ReentrantLock

    /** Initialisation function for the object */
    fun init(): Boolean
}
