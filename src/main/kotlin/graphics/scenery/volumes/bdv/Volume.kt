package graphics.scenery.volumes.bdv

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
import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.bdv.Volume.VolumeDataSource.SpimDataMinimalSource
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription
import mpicbg.spim.data.sequence.FinalVoxelDimensions
import mpicbg.spim.data.sequence.VoxelDimensions
import net.imglib2.RandomAccessibleInterval
import net.imglib2.RealRandomAccessible
import net.imglib2.Volatile
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.NumericType
import net.imglib2.util.Util
import org.joml.Matrix4f
import tpietzsch.example2.VolumeViewerOptions
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

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
    }
}
