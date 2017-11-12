package graphics.scenery

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
    /** Right vector of the camera */
    var right: GLVector = GLVector(1.0f, 0.0f, 0.0f)
    /** FOV of the camera **/
    var fov: Float = 70.0f
    /** Z buffer near plane */
    var nearPlaneDistance = 0.05f
    /** Z buffer far plane location */
    var farPlaneDistance = 1000.0f
    /** aspect ratio */
    var aspect = 1.0f
    /** delta T from the renderer */
    var deltaT = 0.0f
    /** off-center matrices cache, in case needed */
    private val offCenterProjectionCache = HashMap<Pair<Int, Int>, GLMatrix>(2)

    /** View matrix of the camera. Setting the view matrix will re-set the forward
     *  vector of the camera according to the given matrix.
     */
    override var view: GLMatrix = GLMatrix.getIdentity()
        set(m) {
            m.let {
                this.forward = GLVector(m.get(0, 2), m.get(1, 2), m.get(2, 2)).normalize() * -1.0f
                this.right = GLVector(m.get(0, 0), m.get(1, 0), m.get(2, 0)).normalize()
                this.up = GLVector(m.get(0, 1), m.get(1, 1), m.get(2, 1)).normalize()

                if(!targeted) {
                    this.target = this.position + this.forward
                }
            }
            field = m
        }

    /** Rotation of the camera. The rotation is applied after the view matrix */
    override var rotation: Quaternion = Quaternion(0.0f, 0.0f, 0.0f, 1.0f)
        set(q) {
            q.let {
                field = q
                val m = GLMatrix.fromQuaternion(q)
                this.forward = GLVector(m.get(0, 2), m.get(1, 2), m.get(2, 2)).normalize() * -1.0f
            }
        }

    init {
        this.nodeType = "Camera"
    }

    /** Create a perspective projection camera
     *
     *
     */
    fun perspectiveCamera(fov: Float, width: Float, height: Float, nearPlaneLocation: Float = 0.1f, farPlaneLocation: Float = 1000.0f) {
        this.nearPlaneDistance = nearPlaneLocation
        this.farPlaneDistance = farPlaneLocation
        this.aspect = width/height
        this.fov = fov

        this.projection = GLMatrix().setPerspectiveProjectionMatrix(
            this.fov / 180.0f * Math.PI.toFloat(),
            width / height,
            this.nearPlaneDistance,
            this.farPlaneDistance
        )
    }

    fun getOffCenterProjection(fov: Int, eye: Int, separation: Float, convergenceDistance: Float = 1.0f): GLMatrix {
        return offCenterProjectionCache.getOrPut(eye.to(fov), {
            val top = nearPlaneDistance * Math.tan(fov/2.0).toFloat()
            val bottom = -top

            val a = aspect * Math.tan(fov/2.0).toFloat() * convergenceDistance
            val b = a - separation/2.0f
            val c = a + separation/2.0f

            val left: Float
            val right: Float

            if(eye == 0) {
                left = -b * nearPlaneDistance / convergenceDistance
                right = c * nearPlaneDistance / convergenceDistance
            } else {
                left = -c * nearPlaneDistance / convergenceDistance
                right = b * nearPlaneDistance / convergenceDistance
            }

            GLMatrix().setFrustumMatrix(left, right, bottom, top, nearPlaneDistance, farPlaneDistance)
        })
    }

    open fun getTransformation(): GLMatrix {
        val tr = GLMatrix.getTranslation(this.position * (-1.0f)).transpose()
        val r = GLMatrix.fromQuaternion(this.rotation)

        return r * tr
    }

    open fun getTransformation(preRotation: Quaternion): GLMatrix {
        val tr = GLMatrix.getTranslation(this.position * (-1.0f)).transpose()
        val r = GLMatrix.fromQuaternion(preRotation.mult(this.rotation))

        return r * tr
    }

    infix operator fun GLMatrix.times(rhs: GLMatrix): GLMatrix {
        val m = this.clone()
        m.mult(rhs)

        return m
    }
}

