package graphics.scenery.volumes

import bdv.tools.brightness.ConverterSetup
import bdv.tools.transformation.TransformedSource
import graphics.scenery.Hub
import graphics.scenery.OrientedBoundingBox
import graphics.scenery.Origin
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import net.imglib2.type.numeric.NumericType
import net.imglib2.type.numeric.integer.*
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3i
import tpietzsch.example2.VolumeViewerOptions
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class RAIVolume(val ds: VolumeDataSource.RAISource<*>, options: VolumeViewerOptions, hub: Hub): Volume(ds, options, hub) {
    private constructor() : this(VolumeDataSource.RAISource(UnsignedByteType(), emptyList(), ArrayList<ConverterSetup>(), 0, null), VolumeViewerOptions.options(), Hub()) {

    }

    init {
        name = "Volume (RAI source)"
        if(ds.cacheControl != null) {
            logger.debug("Adding cache control")
            cacheControls.addCacheControl(ds.cacheControl)
        }

        timepointCount = ds.numTimepoints

        boundingBox = generateBoundingBox()
    }

    override fun generateBoundingBox(): OrientedBoundingBox {
        val source = ds.sources.firstOrNull()
        val sizes = if(source != null) {
            val d = getDimensions()
            d
        } else {
            Vector3f(1.0f, 1.0f, 1.0f)
        }

        return OrientedBoundingBox(this,
            Vector3f(-0.0f, -0.0f, -0.0f),
            sizes)
    }


    override fun localScale(): Vector3f {
        val d = getDimensions()
        logger.info("Sizes are $d")

        return Vector3f(
                d.x() * pixelToWorldRatio / 10.0f,
                -1.0f * d.y() * pixelToWorldRatio / 10.0f,
                d.z() * pixelToWorldRatio / 10.0f
        )
    }

    override fun createSpatial(): VolumeSpatial {
        return object: VolumeSpatial(this) {
            override fun composeModel() {
                @Suppress("SENSELESS_COMPARISON")
                if (position != null && rotation != null && scale != null) {
                    val source = ds.sources.firstOrNull()

                    val shift = if (source != null) {
                        val s = source.spimSource.getSource(0, 0)
                        val min = Vector3f(s.min(0).toFloat(), s.min(1).toFloat(), s.min(2).toFloat())
                        val max = Vector3f(s.max(0).toFloat(), s.max(1).toFloat(), s.max(2).toFloat())
                        (max - min) * (-0.5f)
                    } else {
                        Vector3f(0.0f, 0.0f, 0.0f)
                    }

                    model.translation(position)
                    model.mul(Matrix4f().set(this.rotation))
                    model.scale(scale)
                    model.scale(localScale())
                    if (origin == Origin.Center) {
                        model.translate(shift)
                    }
                }
            }
        }
    }



    override fun sampleRay(rayStart: Vector3f, rayEnd: Vector3f): Pair<List<Float?>, Vector3f>? {
        val d = getDimensions()
        val dimensions = Vector3f(d.x, d.y, d.z)

        val start = rayStart/dimensions
        val end = rayEnd/dimensions

        if (start.x() < 0.0f || start.x() > 1.0f || start.y() < 0.0f || start.y() > 1.0f || start.z() < 0.0f || start.z() > 1.0f) {
            logger.debug("Invalid UV coords for ray start: {} -- will clamp values to [0.0, 1.0].", start)
        }

        if (end.x() < 0.0f || end.x() > 1.0f || end.y() < 0.0f || end.y() > 1.0f || end.z() < 0.0f || end.z() > 1.0f) {
            logger.debug("Invalid UV coords for ray end: {} -- will clamp values to [0.0, 1.0].", end)
        }

        val startClamped = Vector3f(
            min(max(start.x(), 0.0f), 1.0f),
            min(max(start.y(), 0.0f), 1.0f),
            min(max(start.z(), 0.0f), 1.0f)
        )

        val endClamped = Vector3f(
            min(max(end.x(), 0.0f), 1.0f),
            min(max(end.y(), 0.0f), 1.0f),
            min(max(end.z(), 0.0f), 1.0f)
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

    private fun NumericType<*>.maxValue(): Float = when(this) {
        is UnsignedByteType -> 255.0f
        is UnsignedShortType -> 65536.0f
        is FloatType -> 1.0f
        else -> 1.0f
    }

    override fun sample(uv: Vector3f, interpolate: Boolean): Float? {
         val d = getDimensions()

        val absoluteCoords = Vector3f(uv.x() * d.x(), uv.y() * d.y(), uv.z() * d.z())
        val absoluteCoordsD = Vector3i(floor(absoluteCoords.x()).toInt(), floor(absoluteCoords.y()).toInt(), floor(absoluteCoords.z()).toInt())

        val r = ds.sources.get(currentTimepoint).spimSource.getSource(currentTimepoint,0).randomAccess()
        r.setPosition(absoluteCoordsD.x(),0)
        r.setPosition(absoluteCoordsD.y(),1)
        r.setPosition(absoluteCoordsD.z(),2)

        val value = r.get()

         val finalresult = when(r.get()) {
            is UnsignedShortType -> r.get().realFloat
            else -> throw java.lang.IllegalStateException("Can't determine density for ${value.javaClass} data")
        }

        val transferRangeMax = ds.converterSetups.firstOrNull()?.displayRangeMax?.toFloat() ?: ds.type.maxValue()
        return finalresult/transferRangeMax
        //return transferFunction.evaluate(finalresult/transferRangeMax)
    }


}
