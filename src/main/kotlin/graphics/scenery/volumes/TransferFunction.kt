package graphics.scenery.volumes

import graphics.scenery.utils.lazyLogger
import org.lwjgl.system.MemoryUtil
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


/** Transfer function class with an optional [name]. */
open class TransferFunction(val name: String = "") {
    private val logger by lazyLogger()

    /**
     * Data class to contain control points for transfer functions.
     */
    data class ControlPoint(var value: Float, var factor: Float)

    /** Control point storage for the transfer function */
    protected val controlPoints = CopyOnWriteArrayList<ControlPoint>()

    /** Size of the auxiliary texture. */
    val textureSize = 1024

    /** Vertical size of the auxiliary texture. */
    val textureHeight = 16

    /** The auxiliary texture where the interpolated transfer function will be stored. */
    @Transient protected val buffer: ByteBuffer = MemoryUtil.memCalloc(textureSize * 4 * textureHeight)

    /** Indicator whether the auxiliary texture needs to be reuploaded. */
    @Transient var stale = true
        protected set

    /**
     * @return control points of this tf
     */
    fun controlPoints() = controlPoints.toList()

    /**
     * Adds a new control point for position [value], with [factor].
     */
    fun addControlPoint(value: Float, factor: Float): ControlPoint {
        val v = min(max(value, 0.0f), 1.0f)
        val f = min(max(factor, 0.0f), 1.0f)

        val cp = ControlPoint(v, f)
        controlPoints.removeIf { abs(it.value - v) < 0.001f }
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
     * Clears all control points.
     */
    fun clear() {
        controlPoints.clear()
    }

    /**
     * Finds extremal control points in a list of [points], with [left] given
     * as option to indicate whether the control point is on the left or right
     * end of the interval.
     */
    protected fun findExtremalControlPoint(points: Iterable<ControlPoint>, left: Boolean): ControlPoint {
        val pos = if(left) { 0.0f } else { 1.0f }
        val candidate = points.firstOrNull { abs(it.value - pos) < 0.00001f }

        return when {
            candidate == null && left -> ControlPoint(0.0f, 1.0f)
            candidate == null && !left -> ControlPoint(1.0f, 1.0f)
            candidate != null -> candidate
            else -> throw IllegalStateException("This should not happen")
        }
    }

    /**
     * Serialises the transfer function into a texture for evaluation
     * in the shader.
     */
    fun serialise(): ByteBuffer {
        if(!stale) {
            return buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        }

        val points = controlPoints.sortedBy { it.value }
        val tmp = FloatArray(textureSize)

        for(coord in 0 until textureSize) {
            if(points.isEmpty()) {
                tmp[coord] = 1.0f
                continue
            }

            val pos = coord.toFloat()/(textureSize-1)
            val left = points.reversed().firstOrNull { it.value <= pos } ?: findExtremalControlPoint(points, true)
            val right = points.firstOrNull { it.value >= pos } ?: findExtremalControlPoint(points, false)

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

    /**
     * Evaluates the transfer function for the given [value] and returns the result.
     */
    fun evaluate(value: Float): Float {
        val points = controlPoints.sortedBy { it.value }

        val left = points.reversed().firstOrNull { it.value <= value } ?: findExtremalControlPoint(points, true)
        val right = points.firstOrNull { it.value >= value } ?: findExtremalControlPoint(points, false)

        var current = if(left == right) {
            left.factor
        } else {
            -(left.factor * (right.value - value) + right.factor * (value - left.value)) / (left.value - right.value)
        }
        current = max(0.0f, min(current, 1.0f))

        return current * value
    }

    /**
     * Returns a string representation of the transfer function.
     */
    override fun toString(): String {
        return "TransferFunction: ${controlPoints.sortedBy { it.value }.joinToString { "@${it.value}: alpha=${it.factor}" }}"
    }

    companion object {
        /** Returns a flat transfer function that transfers all values */
        @JvmStatic @JvmOverloads fun flat(factor: Float = 1.0f): TransferFunction {
            val tf = TransferFunction("Flat")
            tf.addControlPoint(0.0f, factor)
            tf.addControlPoint(1.0f, factor)

            return tf
        }

        /** Returns a ramp transfer function, transferring nothing before [offset] (0.0f by default),
         * and everything after a distance of [offset] + [distance]. If this sum exceeds 1.0f, then
         * 1.0f (the maximum value) is taken.
         */
        @JvmStatic @JvmOverloads fun ramp(offset: Float = 0.0f, rampMax: Float = 1.0f, distance: Float = 0.1f): TransferFunction {
            val tf = TransferFunction()
            tf.addControlPoint(0.0f, 0.0f)
            tf.addControlPoint(offset, 0.0f)
            tf.addControlPoint(minOf(offset + distance, 1.0f), rampMax)

            return tf
        }
    }
}
