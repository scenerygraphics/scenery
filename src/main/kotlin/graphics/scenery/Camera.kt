package graphics.scenery

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.volumes.bdv.BDVVolume
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.tan

/**
 * Camera class that may be targeted or oriented
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates a new camera with default position and right-handed
 *  coordinate system.
 */
open class Camera : Node("Camera") {

    /** Enum class for camera projection types */
    enum class ProjectionType {
        Undefined, Perspective, Orthographic
    }

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
    open var fov: Float = 70.0f
    /** Z buffer near plane */
    var nearPlaneDistance = 0.05f
    /** Z buffer far plane location */
    var farPlaneDistance = 1000.0f
    /** delta T from the renderer */
    var deltaT = 0.0f
    /** Projection the camera uses */
    var projectionType: ProjectionType = ProjectionType.Undefined
    /** Width of the projection */
    open var width: Float = 0.0f
    /** Height of the projection */
    open var height: Float = 0.0f
    /** View-space coordinate system e.g. for frustum culling. */
    var viewSpaceTripod: Camera.Tripod
        protected set
    /** Disables culling for this camera. */
    var disableCulling: Boolean = false

    /** View matrix of the camera. Setting the view matrix will re-set the forward
     *  vector of the camera according to the given matrix.
     */
    override var view: GLMatrix = GLMatrix.getIdentity()
        set(m) {
            m.let {
                this.forward = GLVector(m.get(0, 2), m.get(1, 2), m.get(2, 2)).normalize() * -1.0f
                this.right = GLVector(m.get(0, 0), m.get(1, 0), m.get(2, 0)).normalize()
                this.up = GLVector(m.get(0, 1), m.get(1, 1), m.get(2, 1)).normalize()

                this.viewSpaceTripod = cameraTripod()

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
                this.viewSpaceTripod = cameraTripod()
            }
        }

    init {
        this.nodeType = "Camera"
        this.viewSpaceTripod = cameraTripod()
    }

    /**
     * Class to contain local coordinate systems with [x], [y], and [z] axis.
     */
    data class Tripod(val x: GLVector, val y: GLVector, val z: GLVector)

    /**
     * Returns the current aspect ratio
     */
    fun aspectRatio(): Float {
        if(projectionType == ProjectionType.Undefined) {
            logger.warn("Querying aspect ratio but projection type is undefined")
            return 1.0f
        }

        if(width < 0.0001f || height < 0.0001f) {
            logger.warn("Width or height too small, returning 1.0f")
        }

        val scaleWidth = if(this is DetachedHeadCamera && this.tracker != null) {
            0.5f
        } else {
            1.0f
        }

        return (width*scaleWidth)/height
    }

    /**
     * Create a perspective projection camera
     */
    fun perspectiveCamera(fov: Float, width: Float, height: Float, nearPlaneLocation: Float = 0.1f, farPlaneLocation: Float = 1000.0f) {
        this.nearPlaneDistance = nearPlaneLocation
        this.farPlaneDistance = farPlaneLocation
        this.fov = fov

        this.width = width
        this.height = height

        this.projection = GLMatrix().setPerspectiveProjectionMatrix(
            this.fov / 180.0f * Math.PI.toFloat(),
            width / height,
            this.nearPlaneDistance,
            this.farPlaneDistance
        )

        this.projectionType = ProjectionType.Perspective
    }

    /**
     * Returns this camera's transformation matrix.
     */
    open fun getTransformation(): GLMatrix {
        val tr = GLMatrix.getTranslation(this.position * (-1.0f)).transpose()
        val r = GLMatrix.fromQuaternion(this.rotation)

        return r * tr
    }

    /**
     * Returns this camera's transformation matrix, including a
     * [preRotation] that is applied before the camera's transformation.
     */
    open fun getTransformation(preRotation: Quaternion): GLMatrix {
        val tr = GLMatrix.getTranslation(this.position * (-1.0f)).transpose()
        val r = GLMatrix.fromQuaternion(preRotation.mult(this.rotation))

        return r * tr
    }

    /**
     * Returns this camera's transformation for eye with index [eye].
     */
    open fun getTransformationForEye(eye: Int): GLMatrix {
        val tr = GLMatrix.getTranslation(this.position * (-1.0f)).transpose()
        val r = GLMatrix.fromQuaternion(this.rotation)

        return r * tr
    }

    /**
     * Multiplies this matrix with [GLMatrix] [rhs].
     */
    infix operator fun GLMatrix.times(rhs: GLMatrix): GLMatrix {
        val m = this.clone()
        m.mult(rhs)

        return m
    }

    /**
     * Transforms a 3D/4D vector from view space to world coordinates.
     *
     * @param v - The vector to be transformed into world space.
     * @return GLVector - [v] transformed into world space.
     */
    fun viewToWorld(v: GLVector): GLVector =
        this.view.inverse.mult(if(v.dimension == 3) {
            GLVector(v.x(), v.y(), v.z(), 1.0f)
        } else {
            v
        })

    /**
     * Transforms a 2D/3D [vector] from NDC coordinates to world coordinates.
     * If the vector is 2D, [nearPlaneDistance] is assumed for the Z value, otherwise
     * the Z value from the vector is taken.
     */
    @JvmOverloads fun viewportToWorld(vector: GLVector, offset: Float = 0.01f, normalized: Boolean = false): GLVector {
        val pv = projection.clone()
        pv.mult(getTransformation())
        val ipv = pv.inverse

        var worldSpace = ipv.mult(when (vector.dimension) {
            1 -> GLVector(vector.x(), 1.0f, 0.0f, 1.0f)
            2 -> GLVector(vector.x(), vector.y(), 0.0f, 1.0f)
            3 -> GLVector(vector.x(), vector.y(), vector.z(), 1.0f)
            else -> vector
        })

        worldSpace = worldSpace.times(1.0f/worldSpace.w())
//        worldSpace.set(2, offset)
        return worldSpace.xyz()
        /*
        var x = vector.x()
        var y = vector.y()

        if(normalized) {
            x *= width
            y *= height
        }

        val pos = if(this is DetachedHeadCamera) {
            position + headPosition
        } else {
            position
        }

        val view = (target - pos).normalize()
        var h = view.cross(up).normalize()
        var v = h.cross(view)

        val fov = fov * Math.PI / 180.0f
        val lengthV = Math.tan(fov / 2.0).toFloat() * nearPlaneDistance
        val lengthH = lengthV * (width / height)

        v *= lengthV
        h *= lengthH

        val posX = (x - width / 2.0f) / (width / 2.0f)
        val posY = -1.0f * (y - height / 2.0f) / (height / 2.0f)

        val worldPos = pos + view * nearPlaneDistance + h * posX + v * posY
        val worldDir = (worldPos - pos).normalized

        return worldPos + worldDir * offset
        */
    }

    /**
     * Returns the list of objects (as [Scene.RaycastResult]) under the screen space position
     * indicated by [x] and [y], sorted by their distance to the observer.
     */
    @JvmOverloads fun getNodesForScreenSpacePosition(x: Int, y: Int,
                                                       ignoredObjects: List<Class<*>> = emptyList(),
                                                       debug: Boolean = false): List<Scene.RaycastResult> {
        val view = (target - position).normalize()
        var h = view.cross(up).normalize()
        var v = h.cross(view)

        val fov = fov * Math.PI / 180.0f
        val lengthV = Math.tan(fov / 2.0).toFloat() * nearPlaneDistance
        val lengthH = lengthV * (width / height)

        v *= lengthV
        h *= lengthH

        val posX = (x - width / 2.0f) / (width / 2.0f)
        val posY = -1.0f * (y - height / 2.0f) / (height / 2.0f)

        val worldPos = position + view * nearPlaneDistance + h * posX + v * posY
        val worldDir = (worldPos - position).normalized

        val scene = getScene()
        if(scene == null) {
            logger.warn("No scene found for $this, returning empty list for raycast.")
            return emptyList()
        }

        return scene.raycast(worldPos, worldDir, ignoredObjects, debug)
    }

    /**
     * Returns true if this camera can see the given [node], which occurs when the node is
     * in the camera's frustum.
     *
     * This method is based on the Radar Method from Game Programming Gems 5, with the code
     * adapted from [lighthouse3d.com](http://www.lighthouse3d.com/tutorials/view-frustum-culling/radar-approach-testing-points/).
     *
     * In this method, a camera-local coordinate tripod is constructed, and candidate points are projected
     * onto this tripod one after the other. For Z, the length of the vector has to be between the [nearPlaneDistance]
     * and the [farPlaneDistance] for the point to be visible, for Y it has to fit into the height of the field of
     * view at that point, and for X into its width. For the spheres we test here from [Node]'s [getMaximumBoundingBox],
     * we add to the point the radius of the bounding sphere, and take stretching/skewing according to the [aspectRatio]
     * into account.
     *
     * If a sphere is intersecting any boundary of the view frustum, the node is assumed to be visible.
     */
    fun canSee(node: Node): Boolean {
        // TODO: Figure out how to efficiently cull instances
        if(disableCulling || node.instances.size > 0 || node is BDVVolume) {
            return true
        }

        val bs = node.getMaximumBoundingBox().getBoundingSphere()
        val sphereY = 1.0f/cos(PI/180.0f*fov)
        val tanFov = tan(PI*(fov/180.0f)*0.5f).toFloat()
        val angleX = atan(tanFov * aspectRatio())
        val sphereX = 1.0f/cos(angleX)
        val (x, y, z) = viewSpaceTripod

        val v = bs.origin - position
        var result = true

        // check whether the sphere is within the Z bounds
        val az = v.times(z)
        if(az > farPlaneDistance + bs.radius || az < nearPlaneDistance - bs.radius) {
            return false
        }

        // check whether the sphere is within the height of the frustum
        val ay = v.times(y)
        val d = sphereY * bs.radius
        val dzy = az * tanFov
        if(ay > dzy + d || ay < -dzy - d) {
            return false
        }

        // check whether the sphere is within the width of the frustum
        val ax = v.times(x)
        val dzx = az * tanFov * aspectRatio()
        val dx = sphereX * bs.radius
        if(ax > dzx + dx || ax < -dzx - dx) {
            return false
        }

        // check all the three cases where the sphere might be just barely intersecting.
        if(az > farPlaneDistance - bs.radius || az < nearPlaneDistance + bs.radius) {
            result = true
        }

        if(ay > az - d || ay < -az + d) {
            result = true
        }

        if (ax > az - dx || ax < -az + dx) {
            result = true
        }

        return result
    }

    /**
     * Returns the view-space coordinate system of the camera as [Tripod].
     */
    fun cameraTripod(): Tripod {
        val z = forward.clone()
        val x = z.cross(up.clone()).normalized
        val y = x.cross(z).normalized

        return Tripod(x, y, z)
    }
}

