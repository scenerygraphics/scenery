package graphics.scenery.volumes

import bdv.BigDataViewer
import bdv.ViewerImgLoader
import bdv.cache.CacheControl
import bdv.spimdata.SpimDataMinimal
import bdv.spimdata.WrapBasicImgLoader
import bdv.spimdata.XmlIoSpimDataMinimal
import bdv.tools.brightness.ConverterSetup
import bdv.tools.transformation.TransformedSource
import bdv.util.AxisOrder
import bdv.util.AxisOrder.DEFAULT
import bdv.util.RandomAccessibleIntervalSource
import bdv.util.RandomAccessibleIntervalSource4D
import bdv.util.volatiles.VolatileView
import bdv.util.volatiles.VolatileViewData
import bdv.viewer.DisplayMode
import bdv.viewer.Interpolation
import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import bdv.viewer.state.ViewerState
import coremem.enums.NativeTypeEnum
import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.numerics.OpenSimplexNoise
import graphics.scenery.numerics.Random
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.times
import graphics.scenery.utils.forEachParallel
import graphics.scenery.volumes.Volume.VolumeDataSource.SpimDataMinimalSource
import io.scif.SCIFIO
import io.scif.util.FormatTools
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription
import mpicbg.spim.data.sequence.FinalVoxelDimensions
import mpicbg.spim.data.sequence.VoxelDimensions
import net.imglib2.RandomAccessibleInterval
import net.imglib2.RealRandomAccessible
import net.imglib2.Volatile
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.Type
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.NumericType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.*
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import tpietzsch.example2.VolumeViewerOptions
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.streams.toList

open class Volume(val dataSource: VolumeDataSource, val options: VolumeViewerOptions, val hub: Hub) : DelegatesRendering(), HasGeometry {
    /** How many elements does a vertex store? */
    override val vertexSize : Int = 3
    /** How many elements does a texture coordinate store? */
    override val texcoordSize : Int = 2
    /** The [GeometryType] of the [Node] */
    override var geometryType : GeometryType = GeometryType.TRIANGLES
    /** Array of the vertices. This buffer is _required_, but may empty. */
    override var vertices : FloatBuffer = FloatBuffer.allocate(0)
    /** Array of the normals. This buffer is _required_, and may _only_ be empty if [vertices] is empty as well. */
    override var normals : FloatBuffer = FloatBuffer.allocate(0)
    /** Array of the texture coordinates. Texture coordinates are optional. */
    override var texcoords : FloatBuffer = FloatBuffer.allocate(0)
    /** Array of the indices to create an indexed mesh. Optional, but advisable to use to minimize the number of submitted vertices. */
    override var indices : IntBuffer = IntBuffer.allocate(0)

    val converterSetups = ArrayList<ConverterSetup>()
    open val maxTimepoint: Int
    val viewerState: ViewerState

    /** The transfer function to use for the volume. Flat by default. */
    var transferFunction: TransferFunction = TransferFunction.flat(0.5f)

    /** The color map for the volume. */
    var colormap: Colormap = Colormap.get("viridis")

    /** Pixel-to-world scaling ratio. Default: 1 px = 1mm in world space*/
    var pixelToWorldRatio = 0.001f

    /** Rendering method */
    var renderingMethod = RenderingMethod.AlphaBlending
        set(value) {
            field = value
            volumeManager.renderingMethod = value
        }

    val volumeManager: VolumeManager

    // TODO IS THIS REQUIRED??
    var cacheControls = CacheControl.CacheControls()

    /** Current timepoint. */
    var currentTimepoint: Int
        get() { return viewerState.currentTimepoint }
        set(value) {viewerState.currentTimepoint = value}

    sealed class VolumeDataSource {
        class SpimDataMinimalSource(val spimData : SpimDataMinimal) : VolumeDataSource()
        class RAISource<T: NumericType<T>>(
            val type: NumericType<T>,
            val sources: List<SourceAndConverter<T>>,
            val converterSetups: ArrayList<ConverterSetup>,
            val numTimepoints: Int,
            val cacheControl: CacheControl? = null) : VolumeDataSource()
    }

    /**
     * Enum class for selecting a rendering method.
     */
    enum class RenderingMethod {
        MaxProjection,
        MinProjection,
        AlphaBlending
    }

    class BufferDummySource<T: NumericType<T>>(val timepoints: LinkedHashMap<String, ByteBuffer>,
                                               val width: Int,
                                               val height: Int,
                                               val depth: Int,
                               val dimensions: VoxelDimensions,
                               val sourceName: String,
                               val sourceType: T): Source<T> {
        override fun isPresent(t: Int): Boolean {
            return t in 0 .. timepoints.size
        }

        override fun getNumMipmapLevels(): Int {
            return 1
        }

        override fun getInterpolatedSource(t: Int, level: Int, method: Interpolation?): RealRandomAccessible<T> {
            TODO("Can't get interpolated source for BufferDummySource")
        }

        override fun getSourceTransform(t: Int, level: Int, transform: AffineTransform3D?) {
            transform?.set(AffineTransform3D())
        }

        override fun getVoxelDimensions(): VoxelDimensions {
            return dimensions
        }

        override fun getSource(t: Int, level: Int): RandomAccessibleInterval<T> {
            TODO("Can't get source for BufferDummySource")
        }

        override fun getName(): String {
            return sourceName
        }

        override fun getType(): T {
            return sourceType
        }
    }

    init {
        when(dataSource) {
            is SpimDataMinimalSource -> {
                val spimData = dataSource.spimData

                val seq: AbstractSequenceDescription<*, *, *> = spimData.sequenceDescription
                maxTimepoint = seq.getTimePoints().size() - 1
                cacheControls?.addCacheControl((seq.getImgLoader() as ViewerImgLoader).cacheControl)

                // wraps legacy image formats (e.g., TIFF) if referenced in BDV XML
                WrapBasicImgLoader.wrapImgLoaderIfNecessary(spimData)

                val sources = ArrayList<SourceAndConverter<*>>()
                // initialises setups and converters for all channels, and creates source.
                // These are then stored in [converterSetups] and [sources_].
                BigDataViewer.initSetups(spimData, converterSetups, sources)

                viewerState = ViewerState(sources, maxTimepoint)

                WrapBasicImgLoader.removeWrapperIfPresent(spimData)
            }

            is VolumeDataSource.RAISource<*> -> {
                maxTimepoint = dataSource.numTimepoints
                viewerState = ViewerState(dataSource.sources, maxTimepoint)
                converterSetups.addAll( dataSource.converterSetups );
            }
        }

        viewerState.sources.forEach { s -> s.isActive = true }
        viewerState.displayMode = DisplayMode.FUSED

        converterSetups.forEach {
            it.color = ARGBType(Int.MAX_VALUE)
        }

        volumeManager = hub.get<VolumeManager>() ?: hub.add(VolumeManager(hub))
        volumeManager.add(this)
        delegate = volumeManager
    }

    /**
     * Goes to the next available timepoint, returning the number of the updated timepoint.
     */
    fun nextTimepoint(): Int {
        return goToTimePoint(viewerState.currentTimepoint + 1)
    }

    /** Goes to the previous available timepoint, returning the number of the updated timepoint. */
    fun previousTimepoint(): Int {
        return goToTimePoint(viewerState.currentTimepoint - 1)
    }

    /** Goes to the [timepoint] given, returning the number of the updated timepoint. */
    open fun goToTimePoint(timepoint: Int): Int {
        val current = viewerState.currentTimepoint
        viewerState.currentTimepoint = min(max(timepoint, 0), maxTimepoint)
        logger.info("Going to timepoint ${viewerState.currentTimepoint} of $maxTimepoint")

        if(current != viewerState.currentTimepoint) {
            volumeManager.notifyUpdate(this)
        }

        return viewerState.currentTimepoint
    }

    fun prepareNextFrame() {
        cacheControls?.prepareNextFrame()
    }

    /**
     * Returns the local scaling of the volume, taking voxel size and [pixelToWorldRatio] into account.
     */
    open fun localScale(): Vector3f {
        // we are using the first visible source here, which might of course change.
        // TODO: Figure out a better way to do this. It might be an issue for multi-view datasets.
        var voxelSizes: VoxelDimensions = FinalVoxelDimensions("um", 1.0, 1.0, 1.0)

        val index = viewerState.visibleSourceIndices.firstOrNull()
        if(index != null) {
            val source = viewerState.sources[index]
            voxelSizes = source.spimSource.voxelDimensions ?: voxelSizes
        }

        return Vector3f(
//            voxelSizes.dimension(0).toFloat() * pixelToWorldRatio,
//            voxelSizes.dimension(1).toFloat() * pixelToWorldRatio,
//            voxelSizes.dimension(2).toFloat() * pixelToWorldRatio
            pixelToWorldRatio,
            pixelToWorldRatio,
            pixelToWorldRatio
        )
    }

    /**
     * Composes the world matrix for this volume node, taken voxel size and [pixelToWorldRatio]
     * into account.
     */
    override fun composeModel() {
        logger.info("Composing model for $this")
        @Suppress("SENSELESS_COMPARISON")
        if(position != null && rotation != null && scale != null) {
            val L = localScale() * (1.0f/2.0f)
            model.identity()
            model.translate(this.position.x(), this.position.y(), this.position.z())
            model.mul(Matrix4f().set(this.rotation))
            model.scale(this.scale.x(), this.scale.y(), this.scale.z())
            model.scale(L.x(), L.y(), L.z())
        }
    }

    /**
     * Samples a point from the currently used volume, [uv] is the texture coordinate of the volume, [0.0, 1.0] for
     * all of the components.
     *
     * Returns the sampled value as a [Float], or null in case nothing could be sampled.
     */
    fun sample(uv: Vector2f, interpolate: Boolean = true): Float? {
        return null
        /*
        val gt = material.transferTextures["VolumeTextures"] ?: return null

        val bpp = when(gt.type) {
            GLTypeEnum.Byte -> 1
            GLTypeEnum.UnsignedByte -> 1
            GLTypeEnum.Short -> 2
            GLTypeEnum.UnsignedShort -> 2
            GLTypeEnum.Int -> 4
            GLTypeEnum.UnsignedInt -> 4
            GLTypeEnum.Float -> 4
            GLTypeEnum.Double -> 8
        }

        if(uv.x() < 0.0f || uv.x() > 1.0f || uv.y() < 0.0f || uv.y() > 1.0f || uv.z() < 0.0f || uv.z() > 1.0f) {
            logger.debug("Invalid UV coords for volume access: $uv")
            return null
        }

        val absoluteCoords = GLVector(uv.x() * gt.dimensions.x(), uv.y() * gt.dimensions.y(), uv.z() * gt.dimensions.z())
//        val index: Int = (floor(gt.dimensions.x() * gt.dimensions.y() * absoluteCoords.z()).toInt()
//            + floor(gt.dimensions.x() * absoluteCoords.y()).toInt()
//            + floor(absoluteCoords.x()).toInt())
        val absoluteCoordsD = GLVector(floor(absoluteCoords.x()), floor(absoluteCoords.y()), floor(absoluteCoords.z()))
        val diff = absoluteCoords - absoluteCoordsD

        fun toIndex(absoluteCoords: GLVector): Int = (
            absoluteCoords.x().roundToInt().dec()
                + (gt.dimensions.x() * absoluteCoords.y()).roundToInt().dec()
                + (gt.dimensions.x() * gt.dimensions.y() * absoluteCoords.z()).roundToInt().dec()
            )

        val index = toIndex(absoluteCoordsD)

        val contents = gt.contents
        if(contents == null) {
            logger.error("Volume contents are empty for sampling at $uv")
            return null
        }

        if(contents.limit() < index*bpp) {
            logger.debug("Absolute index ${index*bpp} for data type ${gt.type} from $uv exceeds data buffer limit of ${contents.limit()} (capacity=${contents.capacity()}), coords=$absoluteCoords/${gt.dimensions}")
            return 0.0f
        }


        fun density(index:Int): Float {
            if(index*bpp >= contents.limit()) {
                return 0.0f
            }

            val s = when(gt.type) {
                GLTypeEnum.Byte -> contents.get(index).toFloat()
                GLTypeEnum.UnsignedByte -> contents.get(index).toUByte().toFloat()
                GLTypeEnum.Short -> contents.asShortBuffer().get(index).toFloat()
                GLTypeEnum.UnsignedShort -> contents.asShortBuffer().get(index).toUShort().toFloat()
                GLTypeEnum.Int -> contents.asIntBuffer().get(index).toFloat()
                GLTypeEnum.UnsignedInt -> contents.asIntBuffer().get(index).toUInt().toFloat()
                GLTypeEnum.Float -> contents.asFloatBuffer().get(index)
                GLTypeEnum.Double -> contents.asDoubleBuffer().get(index).toFloat()
            }

            return transferFunction.evaluate(s/trangemax)
        }

        return if(interpolate) {
            val offset = 1.0f

            val d00 = lerp(diff.x(), density(index), density(toIndex(absoluteCoordsD + GLVector(offset, 0.0f, 0.0f))))
            val d10 = lerp(diff.x(), density(toIndex(absoluteCoordsD + GLVector(0.0f, offset, 0.0f))), density(toIndex(absoluteCoordsD + GLVector(offset, offset, 0.0f))))
            val d01 = lerp(diff.x(), density(toIndex(absoluteCoordsD + GLVector(0.0f, 0.0f, offset))), density(toIndex(absoluteCoordsD + GLVector(offset, 0.0f, offset))))
            val d11 = lerp(diff.x(), density(toIndex(absoluteCoordsD + GLVector(0.0f, offset, offset))), density(toIndex(absoluteCoordsD + GLVector(offset, offset, offset))))
            val d0 = lerp(diff.y(), d00, d10)
            val d1 = lerp(diff.y(), d01, d11)
            lerp(diff.z(), d0, d1)
        } else {
            density(index)
        }
         */
    }

    private inline fun lerp(t: Float, v0: Float, v1: Float): Float {
        return (1.0f - t) * v0 + t * v1
    }

    /**
     * Takes samples along the ray from [start] to [end] from the currently active volume.
     * Values beyond [0.0, 1.0] for [start] and [end] will be clamped to that interval.
     *
     * Returns the list of samples (which might include `null` values in case a sample failed),
     * as well as the delta used along the ray, or null if the start/end coordinates are invalid.
     */
    fun sampleRay(start: Vector3f, end: Vector3f): Pair<List<Float?>, Vector3f>? {
        return null
        /*
        val gt = material.transferTextures["VolumeTextures"] ?: return null

        if(start.x() < 0.0f || start.x() > 1.0f || start.y() < 0.0f || start.y() > 1.0f || start.z() < 0.0f || start.z() > 1.0f) {
            logger.debug("Invalid UV coords for ray start: {} -- will clamp values to [0.0, 1.0].", start)
        }

        if(end.x() < 0.0f || end.x() > 1.0f || end.y() < 0.0f || end.y() > 1.0f || end.z() < 0.0f || end.z() > 1.0f) {
            logger.debug("Invalid UV coords for ray end: {} -- will clamp values to [0.0, 1.0].", end)
        }

        val startClamped = GLVector(
            min(max(start.x(), 0.0f), 1.0f),
            min(max(start.y(), 0.0f), 1.0f),
            min(max(start.z(), 0.0f), 1.0f)
        )

        val endClamped = GLVector(
            min(max(end.x(), 0.0f), 1.0f),
            min(max(end.y(), 0.0f), 1.0f),
            min(max(end.z(), 0.0f), 1.0f)
        )

        val direction = (endClamped - startClamped).normalize()
        val maxSteps = (direction.hadamard(gt.dimensions).magnitude() * 2.0f).roundToInt()
        val delta = direction * (1.0f/maxSteps.toFloat())

        return (0 until maxSteps).map {
            sample(startClamped + (delta * it.toFloat()))
        } to delta
         */
    }


    class RAIVolume(val ds: VolumeDataSource.RAISource<*>, options: VolumeViewerOptions, hub: Hub): Volume(ds, options, hub) {
        init {
            if(ds.cacheControl != null) {
                logger.info("Adding cache control")
                cacheControls?.addCacheControl(ds.cacheControl)
            }
        }

        override fun localScale(): Vector3f {
            var size = Vector3f(1.0f, 1.0f, 1.0f)
            val source = ds.sources.firstOrNull()

            if(source != null) {
                val s = source.spimSource.getSource(0, 0)
                val min = Vector3f(s.min(0).toFloat(), s.min(1).toFloat(), s.min(2).toFloat())
                val max = Vector3f(s.max(0).toFloat(), s.max(1).toFloat(), s.max(2).toFloat())
                size = max - min
            }
            logger.info("Sizes are $size")

            return Vector3f(
                size.x() * pixelToWorldRatio / 10.0f,
                size.y() * pixelToWorldRatio / 10.0f,
                size.z() * pixelToWorldRatio / 10.0f
            )
        }

        override fun composeModel() {
            logger.info("Composing model for $this")
            @Suppress("SENSELESS_COMPARISON")
            if(position != null && rotation != null && scale != null) {
                val L = localScale()
                logger.info("Local scale is $L")
                val Lh = L * (1.0f/2.0f)
                model.identity()
                model.translate(this.position.x(), this.position.y(), this.position.z())
                model.mul(Matrix4f().set(this.rotation))
                model.scale(this.scale.x(), this.scale.y(), this.scale.z())
                model.scale(Lh.x(), Lh.y(), Lh.z())
                model.translate(-L.x()/pixelToWorldRatio*5.0f, -L.y()/pixelToWorldRatio*5.0f, -L.z()/pixelToWorldRatio*5.0f)
            }
        }
    }

    /**
     * Convenience class to handle buffer-based volumes. Data descriptor is stored in [ds], similar
     * to [VolumeDataSource.RAISource], with [options] and a required [hub].
     */
    class BufferedVolume(val ds: VolumeDataSource.RAISource<*>, options: VolumeViewerOptions, hub: Hub): Volume(ds, options, hub) {
        init {
            logger.debug("Data source is $ds")
        }

        /**
         * Access all the timepoints this volume has attached.
         */
        @Suppress("UNNECESSARY_SAFE_CALL")
        var timepoints: LinkedHashMap<String, ByteBuffer>?
            get() = ((ds?.sources?.firstOrNull()?.spimSource as? TransformedSource)?.wrappedSource as? BufferDummySource)?.timepoints
            set(value) {}

        /**
         * Returns the maximum timepoint.
         */
        override var maxTimepoint: Int
            get() = (timepoints?.size) ?: 0
            set(value) {}

        /**
         * Adds a new timepoint with a given [name], with data stored in [buffer].
         */
        fun addTimepoint(name: String, buffer: ByteBuffer) {
            timepoints?.put(name, buffer)
            volumeManager.notifyUpdate(this)
        }

        /**
         * Removes the timepoint with the given [name].
         */
        fun removeTimepoint(name: String): Boolean {
            val result = timepoints?.remove(name)
            return result != null
        }

        /**
         * Purges the first [count] timepoints, while always leaving [leave] timepoints
         * in the list.
         */
        fun purgeFirst(count: Int, leave: Int = 0) {
            val elements = if(timepoints?.size ?: 0 - count < leave) {
                0
            } else {
                max(1, count - leave)
            }

            val keys = timepoints?.keys?.take(elements)
            keys?.forEach { removeTimepoint(it) }
        }

        /**
         * Purges the last [count] timepoints, while always leaving [leave] timepoints
         * in the list.
         */
        fun purgeLast(count: Int, leave: Int = 0) {
            val elements = if(timepoints?.size ?: 0 - count < leave) {
                0
            } else {
                max(1, count - leave)
            }

            val n = timepoints?.size ?: 0 - elements
            val keys = timepoints?.keys?.drop(n)
            keys?.forEach { removeTimepoint(it) }
        }
    }

    companion object {
        val setupId = AtomicInteger(0)
        val scifio: SCIFIO = SCIFIO()
        
        private val logger by LazyLogger()

        @JvmStatic @JvmOverloads fun fromSpimData(
            spimData: SpimDataMinimal,
            hub : Hub,
            options : VolumeViewerOptions = VolumeViewerOptions()
        ): Volume {
            val ds = SpimDataMinimalSource(spimData)
            return Volume(ds, options, hub)
        }

        @JvmStatic @JvmOverloads fun fromXML(
            path: String,
            hub: Hub,
            options : VolumeViewerOptions = VolumeViewerOptions()
        ): Volume {
            val spimData = XmlIoSpimDataMinimal().load(path)
            val ds = SpimDataMinimalSource(spimData)
            return Volume(ds, options, hub)
        }

        @JvmStatic @JvmOverloads fun <T: NumericType<T>> fromRAII(
            img: RandomAccessibleInterval<T>,
            type: T,
            axisOrder: AxisOrder = DEFAULT,
            name: String,
            hub: Hub,
            options: VolumeViewerOptions = VolumeViewerOptions()
        ): Volume {
            val converterSetups: ArrayList<ConverterSetup> = ArrayList()
            val stacks: ArrayList<RandomAccessibleInterval<T>> = AxisOrder.splitInputStackIntoSourceStacks(img, AxisOrder.getAxisOrder(axisOrder, img, false))
            val sourceTransform = AffineTransform3D()
            val sources: ArrayList<SourceAndConverter<T>> = ArrayList()

            var numTimepoints = 1
            for (stack in stacks) {
                val s: Source<T>
                if (stack.numDimensions() > 3) {
                    numTimepoints = stack.max(3).toInt() + 1
                    s = RandomAccessibleIntervalSource4D<T>(stack, type, sourceTransform, name)
                } else {
                    s = RandomAccessibleIntervalSource<T>(stack, type, sourceTransform, name)
                }
                val source: SourceAndConverter<T> = BigDataViewer.wrapWithTransformedSource(
                    SourceAndConverter<T>(s, BigDataViewer.createConverterToARGB(type)))
                converterSetups.add(BigDataViewer.createConverterSetup(source, setupId.getAndIncrement()))
                sources.add(source)
            }

            val cacheControl = if (img is VolatileView<*, *>) {
                logger.info("Got a nice volatile view!")
                val viewData: VolatileViewData<T, Volatile<T>> = (img as VolatileView<T, Volatile<T>>).volatileViewData
                viewData.getCacheControl()
            } else {
                null
            }

            val ds = VolumeDataSource.RAISource<T>(type, sources, converterSetups, numTimepoints, cacheControl)
            return RAIVolume(ds, options, hub)
        }

        @JvmStatic @JvmOverloads fun <T: NumericType<T>> fromBuffer(
            volumes: LinkedHashMap<String, ByteBuffer>,
            width: Int,
            height: Int,
            depth: Int,
            type: T,
            hub: Hub,
            voxelDimensions: FloatArray = floatArrayOf(1.0f, 1.0f, 1.0f),
            voxelUnit: String = "um",
            options: VolumeViewerOptions = VolumeViewerOptions()
        ): BufferedVolume {
            val converterSetups: ArrayList<ConverterSetup> = ArrayList()
            val sources: ArrayList<SourceAndConverter<T>> = ArrayList()

            val s = BufferDummySource(volumes, width, height, depth, FinalVoxelDimensions(voxelUnit, *(voxelDimensions.map { it.toDouble() }.toDoubleArray())), "", type)
            val source: SourceAndConverter<T> = BigDataViewer.wrapWithTransformedSource(
                    SourceAndConverter<T>(s, BigDataViewer.createConverterToARGB(type)))
           converterSetups.add(BigDataViewer.createConverterSetup(source, setupId.getAndIncrement()))
           sources.add(source)

            val ds = VolumeDataSource.RAISource<T>(type, sources, converterSetups, volumes.size)
            return BufferedVolume(ds, options, hub)
        }

        /**
         * Generates a procedural volume based on the open simplex noise algorithm, with [size]^3 voxels.
         * [radius] sets the blob radius, while [shift] can be used to move through the continuous noise
         * volume, essentially offsetting the volume by the value given. [intoBuffer] can be used to
         * funnel the data into a pre-existing buffer, otherwise one will be allocated. [seed] can be
         * used to choose a seed for the PRNG.
         *
         * Returns the newly-allocated [ByteBuffer], or the one given in [intoBuffer], set to position 0.
         */
        @JvmStatic fun generateProceduralVolume(size: Long, radius: Float = 0.0f,
                                                seed: Long = Random.randomFromRange(0.0f, 133333337.0f).toLong(),
                                                shift: Vector3f = Vector3f(0.0f),
                                                intoBuffer: ByteBuffer? = null, use16bit: Boolean = false): ByteBuffer {
            val f = 3.0f / size
            val center = size / 2.0f + 0.5f
            val noise = OpenSimplexNoise(seed)
            val (range, bytesPerVoxel) = if(use16bit) {
                65535 to 2
            } else {
                255 to 1
            }
            val byteSize = (size*size*size*bytesPerVoxel).toInt()

            val buffer = intoBuffer ?: MemoryUtil.memAlloc(byteSize * bytesPerVoxel)

            (0 until byteSize/bytesPerVoxel).chunked(byteSize/4).forEachParallel { subList ->
                subList.forEach {
                    val x = it.rem(size)
                    val y = (it / size).rem(size)
                    val z = it / (size * size)

                    val dx = center - x
                    val dy = center - y
                    val dz = center - z

                    val offset = abs(noise.random3D((x + shift.x()) * f, (y + shift.y()) * f, (z + shift.z()) * f))
                    val d = sqrt(dx * dx + dy * dy + dz * dz) / size

                    val result = if(radius > Math.ulp(1.0f)) {
                        if(d - offset < radius) { ((d-offset)*range).toShort() } else { 0 }
                    } else {
                        ((d - offset) * range).toShort()
                    }

                    if(use16bit) {
                        buffer.asShortBuffer().put(it, result)
                    } else {
                        buffer.put(it, result.toByte())
                    }
                }
            }

            return buffer
        }

        /**
         * Reads a volume from the given [file].
         */
        @JvmStatic @JvmOverloads  fun  fromPath(file: Path, hub: Hub): BufferedVolume {
            if(file.normalize().toString().endsWith("raw")) {
                return fromPathRaw(file, hub)
            }

            val id = file.fileName.toString()

            val reader = scifio.initializer().initializeReader(file.normalize().toString())

            val dims = Vector3i()
            with(reader.openPlane(0, 0)) {
                dims.x = lengths[0].toInt()
                dims.y = lengths[1].toInt()
                dims.z = reader.getPlaneCount(0).toInt()
            }

            val bytesPerVoxel = reader.openPlane(0, 0).imageMetadata.bitsPerPixel/8
            reader.openPlane(0, 0).imageMetadata.pixelType

            val type: NumericType<*> = when(reader.openPlane(0, 0).imageMetadata.pixelType) {
                FormatTools.INT8 -> ByteType()
                FormatTools.INT16 -> ShortType()
                FormatTools.INT32 -> IntType()

                FormatTools.UINT8 -> UnsignedByteType()
                FormatTools.UINT16 -> UnsignedShortType()
                FormatTools.UINT32 -> UnsignedIntType()

                FormatTools.FLOAT -> FloatType()

                else -> {
                    logger.error("Unknown scif.io pixel type ${reader.openPlane(0, 0).imageMetadata.pixelType}, assuming unsigned byte.")
                    UnsignedByteType()
                }
            }

            logger.debug("Loading $id from disk")
            val imageData: ByteBuffer = MemoryUtil.memAlloc((bytesPerVoxel * dims.x * dims.y * dims.z))

            logger.debug("${file.fileName}: Allocated ${imageData.capacity()} bytes for $type ${8*bytesPerVoxel}bit image of $dims")

            val start = System.nanoTime()

//            if(reader.openPlane(0, 0).imageMetadata.isLittleEndian) {
            logger.debug("Volume is little endian")
            (0 until reader.getPlaneCount(0)).forEach { plane ->
                imageData.put(reader.openPlane(0, plane).bytes)
            }
//            } else {
//                logger.info("Volume is big endian")
//                (0 until reader.getPlaneCount(0)).forEach { plane ->
//                    imageData.put(swapEndianUnsafe(reader.openPlane(0, plane).bytes))
//                }
//            }

            val duration = (System.nanoTime() - start) / 10e5
            logger.debug("Reading took $duration ms")

            imageData.flip()

            val volumes = LinkedHashMap<String, ByteBuffer>()
            volumes[id] = imageData
            // TODO: Kotlin compiler issue, see https://youtrack.jetbrains.com/issue/KT-37955
            return fromBuffer(volumes, dims.x, dims.y, dims.z, UnsignedByteType(), hub)
        }

        /**
         * Reads raw volumetric data from a [file]. If [autorange] is set, the transfer function range
         * will be determined automatically, if [cache] is true, the volume's data will be stored in [volumes] for
         * future use. If [replace] is set, the current volumes' buffer will be replace and marked for deallocation.
         *
         * Returns the new volumes' id.
         */
        @JvmStatic fun fromPathRaw(file: Path, hub: Hub): BufferedVolume {
            val infoFile: Path
            val volumeFiles: List<Path>
            
            if(Files.isDirectory(file)) {
                volumeFiles = Files.list(file).filter { it.endsWith(".raw") && Files.isRegularFile(it) && Files.isReadable(it) }.toList()
                infoFile = file.resolveSibling("stacks.info")
            } else {
                volumeFiles = listOf(file)
                infoFile = file.resolve("stacks.info")
            }

            val lines = Files.lines(infoFile).toList()

            logger.debug("reading stacks.info (${lines.joinToString()}) (${lines.size} lines)")
            val dimensions = Vector3i(lines.get(0).split(",").map { it.toInt() }.toIntArray())
            logger.debug("setting dim to ${dimensions.x}/${dimensions.y}/${dimensions.z}")

            val volumes = LinkedHashMap(volumeFiles.map { v ->
                val id = file.fileName.toString()
                val buffer: ByteBuffer by lazy {

                    logger.debug("Loading $id from disk")
                    val buffer = ByteArray(1024 * 1024)
                    val stream = FileInputStream(file.toFile())
                    val imageData: ByteBuffer = MemoryUtil.memAlloc((2 * dimensions.x * dimensions.y * dimensions.z))

                    logger.debug("${file.fileName}: Allocated ${imageData.capacity()} bytes for UINT16 image of $dimensions")

                    val start = System.nanoTime()
                    var bytesRead = stream.read(buffer, 0, buffer.size)
                    while (bytesRead > -1) {
                        imageData.put(buffer, 0, bytesRead)
                        bytesRead = stream.read(buffer, 0, buffer.size)
                    }
                    val duration = (System.nanoTime() - start) / 10e5
                    logger.debug("Reading took $duration ms")

                    imageData.flip()
                    imageData
                }

                id to buffer
            }.toMap())

            return fromBuffer(volumes, dimensions.x, dimensions.y, dimensions.z, UnsignedShortType(), hub)
        }
    }
}
