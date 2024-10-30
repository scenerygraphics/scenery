package graphics.scenery.volumes

import bdv.tools.transformation.TransformedSource
import bvv.core.VolumeViewerOptions
import graphics.scenery.Hub
import graphics.scenery.OrientedBoundingBox
import graphics.scenery.SceneryElement
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import net.imglib2.type.numeric.integer.*
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.type.numeric.real.FloatType
import org.jfree.data.statistics.SimpleHistogramBin
import org.jfree.data.statistics.SimpleHistogramDataset
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.*

/**
 * Convenience class to handle buffer-based volumes. Data descriptor is stored in [ds], similar
 * to [Volume.VolumeDataSource.RAISource], with [options] and a required [hub].
 */
class BufferedVolume(val ds: VolumeDataSource.RAISource<*>, options: VolumeViewerOptions, hub: Hub): Volume(
    ds,
    options,
    hub
) {
    init {
        name = "Volume (Buffer source)"
        logger.debug("Data source is $ds")

        boundingBox = generateBoundingBox()
    }

    override fun generateBoundingBox(): OrientedBoundingBox {
        val source = (ds.sources[0].spimSource as TransformedSource).wrappedSource as? BufferSource<*>
        val sizes = if(source != null) {
            val min = Vector3f(0.0f)
            val max = Vector3f(source.width.toFloat(), source.height.toFloat(), source.depth.toFloat())
            max - min
        } else {
            Vector3f(1.0f, 1.0f, 1.0f)
        }

        return OrientedBoundingBox(this,
            Vector3f(-0.0f, -0.0f, -0.0f),
            sizes)
    }

    data class Timepoint(val name: String, val contents: ByteBuffer)

    /**
     * Access all the timepoints this volume has attached.
     */
    @Suppress("UNNECESSARY_SAFE_CALL", "UNUSED_PARAMETER")
    val timepoints: CopyOnWriteArrayList<Timepoint>?
        get() = ((ds?.sources?.firstOrNull()?.spimSource as? TransformedSource)?.wrappedSource as? BufferSource)?.timepoints

    /**
     * Adds a new timepoint with a given [name], with data stored in [buffer].
     */
    fun addTimepoint(name: String, buffer: ByteBuffer) {
        timepoints?.removeIf { it.name == name }
        timepoints?.add(Timepoint(name, buffer))
        timepointCount = timepoints?.size ?: 0
        viewerState.numTimepoints = timepointCount

        volumeManager.notifyUpdate(this)
    }

    /**
     * Removes the timepoint with the given [name].
     */
    @JvmOverloads fun removeTimepoint(name: String, deallocate: Boolean = false): Boolean {
        val tp = timepoints?.find { it.name == name }
        val result = timepoints?.removeIf { it.name == name }
        if(deallocate) {
            tp?.contents?.let { MemoryUtil.memFree(it) }
        }
        timepointCount = timepoints?.size ?: 0
        viewerState.numTimepoints = timepointCount

        volumeManager.notifyUpdate(this)
        return result != null
    }

    /**
     * Purges the first [count] timepoints, while always leaving [leave] timepoints
     * in the list.
     */
    @JvmOverloads fun purgeFirst(count: Int, leave: Int = 0, deallocate: Boolean = false) {
        val elements = if(timepoints?.size ?: 0 - count < leave) {
            0
        } else {
            max(1, count - leave)
        }

        repeat(elements) {
            val tp = timepoints?.removeAt(0)
            if(deallocate && tp != null) {
                MemoryUtil.memFree(tp.contents)
            }
        }

        timepointCount = timepoints?.size ?: 0
        viewerState.numTimepoints = timepointCount
    }

    /**
     * Purges the last [count] timepoints, while always leaving [leave] timepoints
     * in the list.
     */
    @JvmOverloads fun purgeLast(count: Int, leave: Int = 0, deallocate: Boolean = false) {
        val elements = if(timepoints?.size ?: 0 - count < leave) {
            0
        } else {
            max(1, count - leave)
        }

        val n = timepoints?.size ?: 0 - elements
        repeat(n) {
            val tp = timepoints?.removeLast()
            if(deallocate && tp != null) {
                MemoryUtil.memFree(tp.contents)
            }
        }

        timepointCount = timepoints?.size ?: 0
        viewerState.numTimepoints = timepointCount
    }

    /**
     * Samples a point from the currently used volume, [uv] is the texture coordinate of the volume, [0.0, 1.0] for
     * all of the components.
     *
     * Returns the sampled value as a [Float], or null in case nothing could be sampled.
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    override fun sample(uv: Vector3f, interpolate: Boolean): Float? {
        var texture = timepoints?.get(currentTimepoint)
        if (texture == null) {
            texture = timepoints?.lastOrNull() ?: throw IllegalStateException("Could not find timepoint")
        }
        val d = getDimensions()
        val dimensions = Vector3f(d.x.toFloat(), d.y.toFloat(), d.z.toFloat())

        val bpp = when (ds.type) {
            is UnsignedByteType, is ByteType -> 1
            is UnsignedShortType, is ShortType -> 2
            is UnsignedIntType, is IntType -> 4
            is FloatType -> 4
            is DoubleType -> 8
            else -> throw IllegalStateException("Data type ${ds.type.javaClass.simpleName} is not supported for sampling")
        }

        if (uv.x() < 0.0f || uv.x() > 1.0f || uv.y() < 0.0f || uv.y() > 1.0f || uv.z() < 0.0f || uv.z() > 1.0f) {
            logger.warn("Invalid UV coords for volume access: $uv")
            return null
        }

        val absoluteCoords = Vector3f(uv.x() * dimensions.x(), uv.y() * dimensions.y(), uv.z() * dimensions.z())
        val absoluteCoordsD = Vector3f(floor(absoluteCoords.x()), floor(absoluteCoords.y()), floor(absoluteCoords.z()))
        val diff = absoluteCoords - absoluteCoordsD

        // Turn the absolute volume coordinates into a sampling index,
        // clamping the value to 1 below the maximum dimension to prevent overflow conditions.
        fun clampedToIndex(absoluteCoords: Vector3f): Int {
            val x = absoluteCoords.x().coerceIn(0f, dimensions.x() - 1f)
            val y = absoluteCoords.y().coerceIn(0f, dimensions.y() - 1f)
            val z = absoluteCoords.z().coerceIn(0f, dimensions.z() - 1f)
            return (x.roundToInt() +
                (dimensions.x() * y).roundToInt() +
                (dimensions.x() * dimensions.y() * z).roundToInt())
        }

        val index = clampedToIndex(absoluteCoordsD)

        val contents = texture.contents.duplicate().order(ByteOrder.LITTLE_ENDIAN)

        if (contents.limit() < index * bpp) {
            logger.warn("Absolute index ${index * bpp} for data type ${ds.type.javaClass.simpleName} from $uv exceeds data buffer limit of ${contents.limit()} (capacity=${contents.capacity()}), coords=$absoluteCoords/${dimensions}")
            return 0.0f
        }

        fun density(index: Int): Float {
            if (index * bpp >= contents.limit()) {
                logger.warn("Sampling beyond limit")
                return 0.0f
            }
            val s = when (ds.type) {
                is ByteType -> contents.get(index).toFloat()
                is UnsignedByteType -> contents.get(index).toUByte().toFloat()
                is ShortType -> contents.asShortBuffer().get(index).toFloat()
                is UnsignedShortType -> contents.asShortBuffer().get(index).toUShort().toFloat()
                is IntType -> contents.asIntBuffer().get(index).toFloat()
                is UnsignedIntType -> contents.asIntBuffer().get(index).toUInt().toFloat()
                is FloatType -> contents.asFloatBuffer().get(index)
                is DoubleType -> contents.asDoubleBuffer().get(index).toFloat()
                else -> throw java.lang.IllegalStateException("Can't determine density for ${ds.type.javaClass.simpleName} data")
            }

            val transferRangeMax = displayRangeLimits.second

            val final = transferFunction.evaluate(s / transferRangeMax)
            logger.debug("Sample at $index is $s, final is $final $transferRangeMax")
            return final
        }

        return if (interpolate) {
            val offset = 1.0f
            // first step: interpolate between the four parallel sides of the voxel
            val d00 = lerp(
                diff.x(),
                density(index),
                density(clampedToIndex(absoluteCoordsD + Vector3f(offset, 0.0f, 0.0f)))
            )
            val d10 = lerp(
                diff.x(),
                density(clampedToIndex(absoluteCoordsD + Vector3f(0.0f, offset, 0.0f))),
                density(clampedToIndex(absoluteCoordsD + Vector3f(offset, offset, 0.0f)))
            )
            val d01 = lerp(
                diff.x(),
                density(clampedToIndex(absoluteCoordsD + Vector3f(0.0f, 0.0f, offset))),
                density(clampedToIndex(absoluteCoordsD + Vector3f(offset, 0.0f, offset)))
            )
            val d11 = lerp(
                diff.x(),
                density(clampedToIndex(absoluteCoordsD + Vector3f(0.0f, offset, offset))),
                density(clampedToIndex(absoluteCoordsD + Vector3f(offset, offset, offset)))
            )
            // then interpolate between the two calculated lines
            val d0 = lerp(diff.y(), d00, d10)
            val d1 = lerp(diff.y(), d01, d11)
            // lastly, interpolate along the single remaining line
            lerp(diff.z(), d0, d1)
        } else {
            density(index)
        }
    }

    private fun lerp(t: Float, v0: Float, v1: Float): Float {
        return (1.0f - t) * v0 + t * v1
    }

    /**
     * Takes samples along the ray from [start] to [end] from the currently active volume.
     * Values beyond [0.0, 1.0] for [start] and [end] will be clamped to that interval.
     *
     * Returns the list of samples (which might include `null` values in case a sample failed),
     * as well as the delta used along the ray, or null if the start/end coordinates are invalid.
     */
    override fun sampleRay(start: Vector3f, end: Vector3f): Pair<List<Float?>, Vector3f>? {
        val dimensions = Vector3f(getDimensions())
        val rayStart = start / dimensions
        val rayEnd = end / dimensions
        if (start.x() < 0.0f || start.x() > 1.0f || start.y() < 0.0f || start.y() > 1.0f || start.z() < 0.0f || start.z() > 1.0f) {
            logger.debug("Invalid UV coords for ray start: {} -- will clamp values to [0.0, 1.0].", start)
        }

        if (end.x() < 0.0f || end.x() > 1.0f || end.y() < 0.0f || end.y() > 1.0f || end.z() < 0.0f || end.z() > 1.0f) {
            logger.debug("Invalid UV coords for ray end: {} -- will clamp values to [0.0, 1.0].", end)
        }

        val startClamped = Vector3f(
                min(max(rayStart.x(), 0.0f), 1.0f),
                min(max(rayStart.y(), 0.0f), 1.0f),
                min(max(rayStart.z(), 0.0f), 1.0f)
        )

        val endClamped = Vector3f(
                min(max(rayEnd.x(), 0.0f), 1.0f),
                min(max(rayEnd.y(), 0.0f), 1.0f),
                min(max(rayEnd.z(), 0.0f), 1.0f)
        )

        val direction = (endClamped - startClamped)
        val maxSteps = (Vector3f(direction).mul(dimensions).length() * 2.0f).roundToInt()
        val delta = direction * (1.0f / maxSteps.toFloat())

        logger.info("Sampling from $startClamped to ${startClamped + maxSteps.toFloat() * delta}")
        direction.normalize()

        return (0 until maxSteps).map {
            sample(startClamped + (delta * it.toFloat()))
        } to delta
    }

    /**
     * Returns the volume's physical (voxel) dimensions.
     */
    override fun getDimensions(): Vector3i {
        val source = ((ds.sources.first().spimSource as? TransformedSource)?.wrappedSource as? BufferSource) ?: throw IllegalStateException("No source found")
        return Vector3i(source.width, source.height, source.depth)
    }

    /**
     * Generates a histogram using GPU acceleration via [VolumeHistogramComputeNode].
     */
    override fun generateHistogram(volumeHistogramData: SimpleHistogramDataset): Int?  {

        val volumeHistogram = VolumeHistogramComputeNode.generateHistogram(
            this,
            timepoints?.get(currentTimepoint)!!.contents,
            getScene() ?: return null
        )

        val histogram = volumeHistogram.fetchHistogram(
            getScene()!!, volumeManager.hub!!.get<Renderer>(
                SceneryElement.Renderer
            )!!
        )

        val displayRange = abs(maxDisplayRange - minDisplayRange)
        val binSize = displayRange / volumeHistogram.numBins
        val minDisplayRange = minDisplayRange.toDouble()

        var max = 0
        histogram.forEachIndexed { index, value ->
            val bin = SimpleHistogramBin(
                minDisplayRange + index * binSize,
                minDisplayRange + (index + 1) * binSize,
                true,
                false
            )
            bin.itemCount = value
            volumeHistogramData.addBin(bin)
            max = max(max, value)
        }
        return max
    }
}
