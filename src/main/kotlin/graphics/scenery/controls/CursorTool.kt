package graphics.scenery.controls

import graphics.scenery.BoundingGrid
import graphics.scenery.Node
import graphics.scenery.Sphere
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import graphics.scenery.utils.lazyLogger
import org.joml.Vector3f
import kotlin.getValue

/**
 * A spherical cursor that can be attached to VR controllers via [attachCursor].
 * You can access the cursor's world position via [getPosition].
 * The cursor can be scaled up or down with [scaleByFactor] or set directly with [radius].
 * [visualScale] is decoupled from the actual radius and only affects the sphere scale, not the reported radius.
 * The radius is constrained by [minRadius] and [maxRadius].
 * Its color is defined by [defaultColor], but can be adjusted with [setColor] and reset with [resetColor].
 * @author Samuel Pantze
 * */
class CursorTool(
    radius: Float = 0.007f,
    val initPos: Vector3f = Vector3f(-0.01f, -0.05f, -0.03f),
    val defaultColor: Vector3f = Vector3f(0.15f, 0.2f, 1f),
    minRadius: Float = 0.001f,
    maxRadius: Float = 0.15f,
    visualScale: Float = 1f
) {
    private val logger by lazyLogger()
    val initRadius = radius
    var visualScale = visualScale
        set(value) {
            field = value
            updateTransforms()
        }
    var radius = radius
        set(value) {
            field = value
            updateTransforms()
        }
    var minRadius = minRadius
        private set
    var maxRadius = maxRadius
        private set

    val cursorSphere = Sphere(radius)

    private fun updateTransforms() {
        cursorSphere.spatial().scale = Vector3f(radius / initRadius *  visualScale)
        cursorSphere.spatial().position = Vector3f(initPos) +
            Vector3f(initPos).normalize().times(radius - initRadius)
    }

    /** Get the current world space position of this cursor. */
    fun getPosition() = cursorSphere.spatial().worldPosition()

    /** Attach the cursor to another object, typically a VR controller.
     * Enabling [debug] will also attach the cursor's bounding grid to the [parent]. */
    fun attachCursor(parent: Node, debug: Boolean = false) {
        cursorSphere.name = "VR Cursor"
        cursorSphere.material {
            diffuse = Vector3f(0.15f, 0.2f, 1f)
        }
        cursorSphere.spatial().position = initPos
        parent.addChild(cursorSphere)

        if (debug) {
            val bb = BoundingGrid()
            bb.node = cursorSphere
            bb.name = "Cursor BB"
            bb.lineWidth = 2f
            bb.gridColor = Vector3f(1f, 0.3f, 0.25f)
            parent.addChild(bb)
        }
        logger.info("Attached cursor to controller.")
    }

    /** Scale the cursor's [radius] by some [factor]. The radius is constrained by [minRadius] and [maxRadius]. */
    fun scaleByFactor(factor: Float) {
        var clampedFac = 1f
        // Only apply the factor if we are in the radius range 0.001f - 0.1f
        if ((factor < 1f && radius > minRadius) || (factor > 1f && radius < maxRadius)) {
            clampedFac = factor
        }
        radius *= clampedFac

    }

    /** Reset the radius to its initial value. */
    fun resetRadius() {
        radius = initRadius
    }

    /** Set the cursor to a specified [color]. */
    fun setColor(color: Vector3f) {
        cursorSphere.material().diffuse = color
    }

    /** Reset the cursor color to [defaultColor]. */
    fun resetColor() {
        cursorSphere.material().diffuse = defaultColor
    }

    /** Set the minimum radius the cursor can be scaled down to. */
    fun setMinRadius(radius: Float) {
        minRadius = radius
    }

    /** Set the maximum radius the cursor can be scaled up to. */
    fun setMaxRadius(radius: Float) {
        maxRadius = radius
    }
}
