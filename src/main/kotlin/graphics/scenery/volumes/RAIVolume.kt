package graphics.scenery.volumes

import bdv.tools.brightness.ConverterSetup
import bdv.viewer.SourceAndConverter
import bvv.core.VolumeViewerOptions
import graphics.scenery.Hub
import graphics.scenery.OrientedBoundingBox
import graphics.scenery.Origin
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import graphics.scenery.utils.extensions.toFloatArray
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.NumericType
import net.imglib2.type.numeric.integer.*
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3i
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

class RAIVolume(@Transient val ds: VolumeDataSource, options: VolumeViewerOptions, hub: Hub): Volume(
    ds,
    options,
    hub
) {
    private constructor() : this(VolumeDataSource.RAISource(UnsignedByteType(), emptyList(), ArrayList<ConverterSetup>(), 0, null), VolumeViewerOptions.options(), Hub()) {

    }

    init {
        name = "Volume (RAI source)"
        if((ds as? VolumeDataSource.RAISource<*>)?.cacheControl != null) {
            logger.debug("Adding cache control")
            cacheControls.addCacheControl(ds.cacheControl)
        }

        timepointCount = when(ds) {
            is VolumeDataSource.RAISource<*> -> ds.numTimepoints
            is VolumeDataSource.SpimDataMinimalSource -> ds.numTimepoints
            else -> throw UnsupportedOperationException("Can't determine timepoint count of ${ds.javaClass}")
        }

        boundingBox = generateBoundingBox()
    }

    override fun generateBoundingBox(): OrientedBoundingBox {
        val scale = Vector3f(1.0f)
        firstSource()?.let { s ->
            val transform = AffineTransform3D()
            s.spimSource.getSourceTransform(0, 0, transform)
            scale.set(transform.get(0, 0), transform.get(1, 1), transform.get(2, 2))
        }

        return OrientedBoundingBox(this,
            Vector3f(-0.0f, -0.0f, -0.0f),
            scale * Vector3f(getDimensions()))
    }


    override fun localScale(): Vector3f {
        return Vector3f(pixelToWorldRatio, -pixelToWorldRatio, pixelToWorldRatio)
    }

    private fun firstSource(): SourceAndConverter<out Any>? {
        return when(ds) {
            is VolumeDataSource.RAISource<*> -> ds.sources.firstOrNull()
            is VolumeDataSource.SpimDataMinimalSource -> ds.sources.firstOrNull()
            else -> throw UnsupportedOperationException("Can't handle data source of type ${ds.javaClass}")
        }
    }

    override fun getDimensions(): Vector3i {
        val source = firstSource()

        return if(source != null) {
            val s = source.spimSource.getSource(0, 0)
            val min = Vector3i(s.min(0).toInt(), s.min(1).toInt(), s.min(2).toInt())
            val max = Vector3i(s.max(0).toInt(), s.max(1).toInt(), s.max(2).toInt())
            val d = max.sub(min)
            logger.debug("Dimensions are $d")
            d
        } else {
            Vector3i(1, 1, 1)
        }
    }

    override fun createSpatial(): VolumeSpatial {
        return RAIVolumeSpatial(this)
    }

    /**
    *   Extension of [VolumeSpatial] for RAI volumes
     */
    class RAIVolumeSpatial(volume: RAIVolume): VolumeSpatial(volume) {
        override fun composeModel() {
            @Suppress("SENSELESS_COMPARISON")
            if (position != null && rotation != null && scale != null ) {
                val volume = (node as? RAIVolume) ?: return
                val source = volume.firstSource()

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
                model.scale(volume.localScale())
                if (volume.origin == Origin.Center) {
                    model.translate(shift)
                }
            }
        }
    }


    override fun sampleRay(rayStart: Vector3f, rayEnd: Vector3f): Pair<List<Float?>, Vector3f>? {
        val d = getDimensions()
        val dimensions = Vector3f(d.x.toFloat(), d.y.toFloat(), d.z.toFloat())

        val start = rayStart / dimensions
        val end = rayEnd / dimensions

        if (start.x !in 0.0f..1.0f || start.y !in 0.0f..1.0f || start.z !in 0.0f..1.0f) {
            logger.debug("Invalid UV coords for ray start: {} -- will clamp values to [0.0, 1.0].", start)
        }

        if (end.x !in 0.0f..1.0f || end.y !in 0.0f..1.0f || end.z !in 0.0f..1.0f) {
            logger.debug("Invalid UV coords for ray end: {} -- will clamp values to [0.0, 1.0].", end)
        }

        val startClamped = Vector3f(
            start.x.coerceIn(0.0f, 1.0f),
            start.y.coerceIn(0.0f, 1.0f),
            start.z.coerceIn(0.0f, 1.0f)
        )

        val endClamped = Vector3f(
            end.x.coerceIn(0.0f, 1.0f),
            end.y.coerceIn(0.0f, 1.0f),
            end.z.coerceIn(0.0f, 1.0f)
        )

        val direction = (endClamped - startClamped)
        val maxSteps = (Vector3f(direction).mul(dimensions).length() * 2.0f).roundToInt()
        val delta = direction * (1.0f / maxSteps.toFloat())

        logger.debug("Sampling from $startClamped to ${startClamped + maxSteps.toFloat() * delta}")
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


    override fun sampleRayGridTraversal(rayStart: Vector3f, rayEnd: Vector3f): Pair<List<Float?>, List<Vector3f?>> {
        val d = getDimensions()
        val dimensions = Vector3f(d.x.toFloat(), d.y.toFloat(), d.z.toFloat())
        val voxelSize = Vector3f(1f)
        val ray = rayEnd - rayStart
        val rayDir = Vector3f(ray).normalize()

        val rayLength = ray.length()

        // determine the initial grid direction
        val stepX = if (rayDir.x >= 0) 1 else -1
        val stepY = if (rayDir.y >= 0) 1 else -1
        val stepZ = if (rayDir.z >= 0) 1 else -1

        val tDeltaX = abs(voxelSize.x / rayDir.x)
        val tDeltaY = abs(voxelSize.y / rayDir.y)
        val tDeltaZ = abs(voxelSize.z / rayDir.z)

        // this is where it all started
        val voxelPos = Vector3f(
            floor(rayStart.x),
            floor(rayStart.y),
            floor(rayStart.z)
        )
        logger.info("starting with voxel pos $voxelPos")
        val tMax = Vector3f(
            if (stepX > 0) ((voxelPos.x + 1) * voxelSize.x - rayStart.x) / rayDir.x
            else (voxelPos.x * voxelSize.x - rayStart.x) / rayDir.x,
            if (stepY > 0) ((voxelPos.y + 1) * voxelSize.y - rayStart.y) / rayDir.y
            else (voxelPos.y * voxelSize.y - rayStart.y) / rayDir.y,
            if (stepZ > 0) ((voxelPos.z + 1) * voxelSize.z - rayStart.z) / rayDir.z
            else (voxelPos.z * voxelSize.z - rayStart.z) / rayDir.z
        )

        val samplesList = mutableListOf<Float?>()
        val samplesPosList = mutableListOf<Vector3f>()

        // Start traversing the grid, with t being the already traversed length
        var t = 0f
        while (true) {
            val currentPos = rayStart + rayDir * t
            val samplePos = voxelPos * voxelSize
            logger.info("sampled pos $samplePos at t $t")
            // For sampling, we need coordinates in UV space, so we normaliye
            val sampleValue = sample(samplePos / dimensions, false)
            logger.info("got sample value $sampleValue")
            samplesList.add(sampleValue)
            samplesPosList.add(samplePos)

            if ((currentPos - rayStart).length() > rayLength) break

            // decide the voxel direction to travel to next
            if (tMax.x <= tMax.y && tMax.x <= tMax.z) {
                t = tMax.x
                voxelPos.x += stepX
                tMax.x += tDeltaX
            }
            if (tMax.y <= tMax.z && tMax.y <= tMax.x) {
                t = tMax.y
                voxelPos.y += stepY
                tMax.y += tDeltaY
            }
            if (tMax.z <= tMax.x && tMax.z <= tMax.y) {
                t = tMax.z
                voxelPos.z += stepZ
                tMax.z += tDeltaZ
            }
        }

        return Pair(samplesList, samplesPosList)
    }

    /**
    This sample function is not finished yet, transferRangeMax function should be improved to fit different data type
     **/
    override fun sample(uv: Vector3f, interpolate: Boolean): Float? {
         val d = getDimensions()

        val absoluteCoords = Vector3f(uv.x() * d.x(), uv.y() * d.y(), uv.z() * d.z())
        val absoluteCoordsD = Vector3i(floor(absoluteCoords.x()).toInt(), floor(absoluteCoords.y()).toInt(), floor(absoluteCoords.z()).toInt())

        val r = when(ds) {
            is VolumeDataSource.RAISource<*> -> ds.sources.get(0).spimSource.getSource(
                currentTimepoint,
                0
            ).randomAccess()
            is VolumeDataSource.SpimDataMinimalSource -> ds.sources.get(0).spimSource.getSource(
                currentTimepoint,
                0
            ).randomAccess()
            else -> throw UnsupportedOperationException("Can't handle data source of type ${ds.javaClass}")
        }
        r.setPosition(absoluteCoordsD.x(),0)
        r.setPosition(absoluteCoordsD.y(),1)
        r.setPosition(absoluteCoordsD.z(),2)

        val value = r.get()
        val finalresult = when(value) {
            is UnsignedShortType -> value.realFloat
            else -> throw java.lang.IllegalStateException("Can't determine density for ${value?.javaClass} data")
        }

        val transferRangeMax = when(ds)
        {
            is VolumeDataSource.RAISource<*> -> ds.converterSetups.firstOrNull()?.displayRangeMax?.toFloat()?:ds.type.maxValue()
            is VolumeDataSource.SpimDataMinimalSource -> ds.converterSetups.firstOrNull()?.displayRangeMax?.toFloat()?:255.0f
            else -> throw UnsupportedOperationException("Can't handle data source of type ${ds.javaClass}")
        }

        val tf = transferFunction.evaluate(finalresult/transferRangeMax)
        logger.debug("Sampled at $uv: $finalresult/$transferRangeMax/$tf")
        return tf
    }
}
