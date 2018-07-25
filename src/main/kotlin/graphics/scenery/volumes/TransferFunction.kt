package graphics.scenery.volumes

import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Transfer function class with an optional [name]. */
open class TransferFunction(val name: String = "") {
    private val logger by LazyLogger()

    /**
     * Data class to contain control points for transfer functions.
     */
    data class ControlPoint(var value: Float, var factor: Float)

    /** Control point storage for the transfer function */
    protected val controlPoints = ArrayList<ControlPoint>()

    /** Size of the auxiliary texture. */
    val textureSize = 1024

    /** Vertical size of the auxiliary texture. */
    val textureHeight = 16

    /** The auxiliary texture where the interpolated transfer function will be stored. */
    protected val buffer: ByteBuffer = MemoryUtil.memAlloc(textureSize * 4 * textureHeight)

    /** Indicator whether the auxiliary texture needs to be reuploaded. */
    var stale = true
        protected set

    /**
     * Adds a new control point for position [value], with [factor].
     */
    fun addControlPoint(value: Float, factor: Float): ControlPoint {
        val cp = ControlPoint(value, factor)
        controlPoints.removeIf { abs(it.value - value) < 0.001f }
        controlPoints.add(cp)
        stale = true

        return cp
    }

    /**
     * Removes the control point at [value]. Returns true if points have been removed.
     */
    fun removeControlPoint(value: Float): Boolean {
        val removed = controlPoints.removeIf { abs(it.value - value) < 0.001f }

        if(removed) {
            stale = true
        }

        return removed
    }

    /**
     * Removes the [controlPoint] by reference. Returns true if it has been removed.
     */
    fun removeControlPoint(controlPoint: ControlPoint): Boolean {
        val removed = controlPoints.remove(controlPoint)

        if(removed) {
            stale = true
        }

        return removed
    }

    /**
     * Returns the control point at [index]. Internal use only.
     */
    internal fun getControlPoint(index: Int): ControlPoint {
        return controlPoints[index]
    }

    /**
     * Serialises the transfer function into a texture for evaluation
     * in the shader.
     */
    fun serialise(): ByteBuffer {
        if(!stale) {
            return buffer.duplicate()
        }

        val points = controlPoints.sortedBy { it.value }
        val tmp = FloatArray(textureSize)

        fun findExtremalControlPoint(left: Boolean): ControlPoint {
            val pos = if(left) { 0.0f } else { 1.0f }
            val candidate = points.firstOrNull { abs(it.value - pos) < 0.00001f }

            return when {
                candidate == null && left -> ControlPoint(0.0f, 1.0f)
                candidate == null && !left -> ControlPoint(1.0f, 1.0f)
                candidate != null -> candidate
                else -> throw IllegalStateException("This should not happen")
            }
        }

        for(coord in 0 until textureSize) {
            if(points.isEmpty()) {
                tmp[coord] = 1.0f
                continue
            }

            val pos = coord.toFloat()/(textureSize-1)
            val left = points.reversed().firstOrNull { it.value <= pos } ?: findExtremalControlPoint(true)
            val right = points.firstOrNull { it.value >= pos } ?: findExtremalControlPoint(false)

            var current = if(left == right) {
                left.factor
            } else {
                -(left.factor * (right.value - pos) + right.factor * (pos - left.value)) / (left.value - right.value)
            }
            current = max(0.0f, min(current, 1.0f))

            tmp[coord] = current
        }

        val fb = buffer.asFloatBuffer()
        for(copy in 0 until textureHeight) {
            fb.put(tmp)
        }

        stale = false
        return buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    }

    companion object {
        /** Returns a flat transfer function that transfers all values */
        @JvmStatic fun flat(): TransferFunction = TransferFunction("Flat")

        /** Returns a ramp transfer function, transferring nothing before [offset] (0.0f by default), and everything at the top. */
        @JvmStatic @JvmOverloads fun ramp(offset: Float = 0.0f): TransferFunction {
            val tf = TransferFunction()
            tf.addControlPoint(0.0f, 0.0f)
            tf.addControlPoint(offset, 0.0f)
            tf.addControlPoint(1.0f, 1.0f)

            return tf
        }
    }
}
