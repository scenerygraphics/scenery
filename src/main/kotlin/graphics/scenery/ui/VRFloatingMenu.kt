package graphics.scenery.ui

import graphics.scenery.Box
import graphics.scenery.Camera
import graphics.scenery.Node
import graphics.scenery.OrientedBoundingBox
import graphics.scenery.Scene
import graphics.scenery.controls.CursorTool
import graphics.scenery.controls.OpenXRHMD
import graphics.scenery.controls.TrackedDevice
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.controls.behaviours.Grabable
import graphics.scenery.controls.behaviours.Touchable
import graphics.scenery.controls.behaviours.VRGrab
import graphics.scenery.controls.behaviours.VRTouch
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.atan2

/**
 * Floating VR menu with a grab handle at the bottom. Extension of [VRButtonMenu]
 * */
class VRFloatingMenu(
    /** Scale applied to every column when it is attached to the parent. */
    columnScale: Float = 0.04f,
    /** Gap between the top of the grab handle and the bottom of the menu. */
    positionOffset: Vector3f = Vector3f(0f, 0.02f, 0f),

    defaultColor: Vector3f = Vector3f(0.8f),
    defaultPressedColor: Vector3f = Vector3f(0.95f, 0.35f, 0.25f),
    defaultTouchingColor: Vector3f = Vector3f(0.8f, 0.71f, 0.71f),
    debug: Boolean = false,
    private val grabHandle: Box = Box(Vector3f(0.2f, 0.02f, 0.02f)),
    scene: Scene,
    private val hmd: OpenXRHMD,
    cursor: CursorTool,
    private val camera: Camera?
) : VRButtonMenu(
    grabHandle, columnScale,
    Vector3f(positionOffset).add(0f, grabHandle.sizes.y / 2f, 0f),
    Quaternionf(),
    defaultColor, defaultPressedColor, defaultTouchingColor, debug,
    Gui3DElement.Anchor.Bottom, true
) {
    private val handleBaseColor = Vector3f(0.6f, 0.6f, 1.0f)
    private val handleTouchColor = Vector3f(0.5f, 0.5f, 1.0f)
    private val handleGrabColor = Vector3f(0.4f, 0.4f, 1.0f)

    private val spawnOffset = Vector3f(-0.15f, -.3f, -.3f)

    var isGrabbing = false
        private set

    init {
        grabHandle.material().diffuse = handleBaseColor

        if (grabHandle.boundingBox == null) {grabHandle.generateBoundingBox()}
        scene.addChild(grabHandle)

        grabHandle.addAttribute(Touchable::class.java, Touchable(
            onTouch = { grabHandle.material().diffuse = handleTouchColor },
            onRelease = { grabHandle.material().diffuse = handleBaseColor },
            onHoldChangeDiffuseTo = null
        ))

        VRTouch.createAndSet(scene, hmd, listOf(TrackerRole.RightHand), true,
            customTip = cursor.cursorPointer
        )

        grabHandle.addAttribute(Grabable::class.java, Grabable(lockRotation = false))

        VRGrab.createAndSet(scene, hmd, listOf(OpenXRHMD.OpenXRButton.Trigger),
            listOf(TrackerRole.RightHand),
            onGrab = { _, _ ->
                isGrabbing = true
                grabHandle.material().diffuse = handleGrabColor
            },
            onDrag = { node, _ ->
                lockToYaw(node)
            },
            onRelease = { _, _ ->
                grabHandle.material().diffuse = handleBaseColor
                isGrabbing = false
            }, customTip = cursor.cursorPointer, targets = listOf(grabHandle))
    }


    /** Head position in world space */
    private fun headWorldPos(): Vector3f {
        camera?.let {
            return Vector3f(it.spatial().position).add(hmd.getPosition())
        }
        return Vector3f()
    }

    /** Places the menu next to the user once on first toggle */
    override fun onVisibilityChanged(visible: Boolean) {
        if (!visible) return
        // Regenerate this column's bounding box to be safe
        generateBoundingBox()
        grabHandle.spatial {
            position = headWorldPos().add(headYaw().transform(Vector3f(spawnOffset)))
            needsUpdate = true
        }
        faceHmd(grabHandle)
    }

    /** Keep only the twist around Y (swing-twist decomposition), so the menu always stays upright. */
    private fun lockToYaw(node: Node) = node.ifSpatial {
        val yaw = Quaternionf(0f, rotation.y, 0f, rotation.w)
        // Degenerate case: pure 180° flip around a horizontal axis, keep the old rotation
        if (yaw.lengthSquared() > 1e-6f) {
            rotation = yaw.normalize()
            needsUpdate = true
        }
    }

    /** Rotation around Y matching where the user is looking horizontally. */
    private fun headYaw(): Quaternionf {
        val fwd = hmd.getOrientation().conjugate().transform(Vector3f(0f, 0f, -1f))
        if (fwd.lengthSquared() < 1e-6f) return Quaternionf()
        return Quaternionf().rotationY(atan2(-fwd.x, -fwd.z))
    }

    private fun faceHmd(node: Node) {
        node.ifSpatial {
            val dir = headWorldPos().sub(position).mul(1f, 0f, 1f)
            if (dir.lengthSquared() > 1e-6f) {
                rotation = Quaternionf().rotationY(atan2(dir.x, dir.z))
                needsUpdate = true
            }
        }
    }

    /** True if [worldPos] is within [threshold] meters of the handle or the current column. */
    fun isCursorNear(worldPos: Vector3f, threshold: Float = 0.03f): Boolean {
        if (!isMenuVisible) return false
        if (isGrabbing) return true
        val col = currentColumn()
        return distanceToBox(grabHandle, grabHandle.boundingBox, worldPos) < threshold ||
            distanceToBox(col, boundingBox, worldPos) < threshold
    }

    /** World-space distance from [worldPos] to [bb], where [bb] is in [node]'s local space. Handles scale/rotation. */
    private fun distanceToBox(node: Node, bb: OrientedBoundingBox?, worldPos: Vector3f): Float {
        if (bb == null) return Float.MAX_VALUE
        val world = Matrix4f(node.spatialOrNull()?.world ?: return Float.MAX_VALUE)
        val local = Matrix4f(world).invert().transformPosition(Vector3f(worldPos))
        local.set(
            local.x.coerceIn(bb.min.x, bb.max.x),
            local.y.coerceIn(bb.min.y, bb.max.y),
            local.z.coerceIn(bb.min.z, bb.max.z)
        )
        return world.transformPosition(local).distance(worldPos)
    }
}
