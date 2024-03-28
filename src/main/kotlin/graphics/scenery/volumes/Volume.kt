@file:Suppress("DEPRECATION")

package graphics.scenery.volumes

import bdv.BigDataViewer
import bdv.ViewerImgLoader
import bdv.cache.CacheControl
import bdv.spimdata.SpimDataMinimal
import bdv.spimdata.WrapBasicImgLoader
import bdv.spimdata.XmlIoSpimDataMinimal
import bdv.tools.brightness.ConverterSetup
import bdv.util.AxisOrder
import bdv.util.AxisOrder.DEFAULT
import bdv.util.RandomAccessibleIntervalSource
import bdv.util.RandomAccessibleIntervalSource4D
import bdv.util.volatiles.VolatileView
import bdv.util.volatiles.VolatileViewData
import bdv.viewer.DisplayMode
import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import bdv.viewer.state.ViewerState
import bvv.core.VolumeViewerOptions
import graphics.scenery.*
import graphics.scenery.attribute.DelegationType
import graphics.scenery.attribute.geometry.DelegatesGeometry
import graphics.scenery.attribute.geometry.Geometry
import graphics.scenery.attribute.material.DelegatesMaterial
import graphics.scenery.attribute.material.Material
import graphics.scenery.attribute.renderable.DelegatesRenderable
import graphics.scenery.attribute.renderable.Renderable
import graphics.scenery.attribute.spatial.DefaultSpatial
import graphics.scenery.attribute.spatial.HasCustomSpatial
import graphics.scenery.net.Networkable
import graphics.scenery.numerics.OpenSimplexNoise
import graphics.scenery.numerics.Random
import graphics.scenery.utils.lazyLogger
import graphics.scenery.utils.extensions.times
import graphics.scenery.utils.forEachIndexedAsync
import graphics.scenery.volumes.Volume.VolumeDataSource.SpimDataMinimalSource
import io.scif.SCIFIO
import io.scif.filters.ReaderFilter
import io.scif.util.FormatTools
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription
import mpicbg.spim.data.sequence.FinalVoxelDimensions
import net.imagej.ops.OpService
import net.imglib2.RandomAccessibleInterval
import net.imglib2.Volatile
import net.imglib2.histogram.Histogram1d
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.NumericType
import net.imglib2.type.numeric.integer.*
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3i
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import org.scijava.io.location.FileLocation
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.name
import kotlin.properties.Delegates
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.VolatileByteType
import net.imglib2.type.volatiles.VolatileFloatType
import net.imglib2.type.volatiles.VolatileShortType
import net.imglib2.type.volatiles.VolatileUnsignedByteType
import net.imglib2.type.volatiles.VolatileUnsignedShortType
import org.jfree.data.statistics.SimpleHistogramBin
import org.jfree.data.statistics.SimpleHistogramDataset
import org.scijava.Context
import kotlin.math.*
import kotlin.streams.toList
import kotlin.time.measureTimedValue

@Suppress("DEPRECATION")
open class Volume(
    @Transient
    val dataSource: VolumeDataSource = VolumeDataSource.NullSource,
    @Transient
    val options: VolumeViewerOptions = VolumeViewerOptions(),
    hub: Hub
) : DefaultNode("Volume"),
    DelegatesRenderable, DelegatesGeometry, DelegatesMaterial, DisableFrustumCulling,
    HasCustomSpatial<Volume.VolumeSpatial>, HasTransferFunction, HasHistogram {

    // without this line the *java* serialization framework kryo does not recognize the parameter-less constructor
    // and uses dark magic to instanciate this class
    constructor() : this(VolumeDataSource.NullSource, hub = Hub("dummyVolumeHub"))

    var initalizer: VolumeInitializer? = null

    private val delegationType: DelegationType = DelegationType.OncePerDelegate
    override fun getDelegationType(): DelegationType {
        return delegationType
    }

    override fun getDelegateRenderable(): Renderable? {
        return volumeManager.renderableOrNull()
    }

    override fun getDelegateGeometry(): Geometry? {
        return volumeManager.geometryOrNull()
    }

    override fun getDelegateMaterial(): Material? {
        return volumeManager.materialOrNull()
    }

    @Transient
    val converterSetups = ArrayList<ConverterSetup>()
    var timepointCount: Int

    @Transient
    val viewerState: ViewerState

    /** The transfer function to use for the volume. Flat by default. */
    override var transferFunction: TransferFunction = TransferFunction.flat(0.5f)
        set(m) {
            field = m
            modifiedAt = System.nanoTime()
        }
    override var minDisplayRange: Float
        get() = converterSetups.getOrNull(0)?.displayRangeMin?.toFloat() ?: throw IllegalStateException()
        set(value) { setTransferFunctionRange(value, maxDisplayRange) }
    override var maxDisplayRange: Float
        get() = converterSetups.getOrNull(0)?.displayRangeMax?.toFloat() ?: throw IllegalStateException()
        set(value) { setTransferFunctionRange(minDisplayRange, value) }

    override var range: Pair<Float, Float>
        get() = when(dataSource) {
            VolumeDataSource.NullSource -> 0.0f to 0.0f
            is VolumeDataSource.RAISource<*> -> dataSource.type.toRange()
            is SpimDataMinimalSource -> (dataSource.sources.first().spimSource.type as NumericType<*>).toRange()
        }
        set(value) { logger.warn("Cannot set data range, it is automatically determined.") }

    /** The color map for the volume. */
    var colormap: Colormap = Colormap.get("viridis")
        set(m) {
            field = m
            if(::volumeManager.isInitialized) {
                volumeManager.removeCachedColormapFor(this)
            }
            modifiedAt = System.nanoTime()
        }

    /** Pixel-to-world scaling ratio. Default: 1 px = 1mm in world space*/
    var pixelToWorldRatio: Float by Delegates.observable(0.001f) { property, old, new ->
        spatial().propertyChanged(
            property,
            old,
            new,
            "pixelToWorldRatio"
        )
    }

    /** What to use as the volume's origin, scenery's default is [Origin.Center], BVV's default is [Origin.FrontBottomLeft]. **/
    var origin = Origin.Center

    /** Rendering method */
    var renderingMethod = RenderingMethod.AlphaBlending
        set(value) {
            field = value
            volumeManager.renderingMethod = value
        }

    /** Plane equations for slicing planes mapped to origin */
    var slicingPlaneEquations = mapOf<Int, Vector4f>()

    /** Modes how assigned slicing planes interact with the volume */
    var slicingMode = SlicingMode.None

    var multiResolutionLevelLimits: Pair<Int, Int>? = null

    enum class SlicingMode(val id: Int){
        // Volume is rendered as it is
        None(0),
        // Volume is cut along the assigned slicing plane and the lower half is rendered.
        // For multiple slicing planes the inner hull is rendered.
        Cropping(1),
        // Only a slice around the slicing planes is rendered with no transparency.
        Slicing(2),
        // The slice around the slicing planes is rendered with no transparency
        // while also the cropping rule applies for the rest of the volume.
        Both(3)
    }

    @Transient
    lateinit var volumeManager: VolumeManager

    // TODO IS THIS REQUIRED??
    @Transient
    var cacheControls = CacheControl.CacheControls()

    /** Current timepoint. */
    var currentTimepoint: Int = 0
        get() {
            // despite IDEAs warning this might be not be false if kryo uses its de/serialization magic
            return if (dataSource == null || dataSource is VolumeDataSource.NullSource) {
                0
            } else {
                viewerState.currentTimepoint
            }
        }
        set(value) {
            viewerState.currentTimepoint = value
            modifiedAt = System.nanoTime()
            field = value
        }

    val bytesPerVoxel: Int
        get() {
            return when(dataSource){
                VolumeDataSource.NullSource -> 1
                is VolumeDataSource.RAISource<*> -> dataSource.type.toBytesPerValue()
                is SpimDataMinimalSource -> (dataSource.sources.first().spimSource.type as NumericType<*>).toBytesPerValue()
            }
        }

    sealed class VolumeDataSource {
        class SpimDataMinimalSource(
            @Transient
            val spimData : SpimDataMinimal,
            @Transient
            val sources: List<SourceAndConverter<*>>,
            @Transient
            val converterSetups: ArrayList<ConverterSetup>,
            val numTimepoints: Int
            ) : VolumeDataSource()
        class RAISource<T: NumericType<T>>(
            @Transient
            val type: NumericType<T>,
            @Transient
            val sources: List<SourceAndConverter<T>>,
            @Transient
            val converterSetups: ArrayList<ConverterSetup>,
            val numTimepoints: Int,
            @Transient
            val cacheControl: CacheControl? = null,
            @Transient
            val spimData: SpimDataMinimal? = null) : VolumeDataSource()
        object  NullSource: VolumeDataSource()
    }

    /**
     * Class to hold constructor parameters and function for initializing a Volume
     */
    interface VolumeInitializer{
        fun initializeVolume(hub: Hub) : Volume
    }

    class VolumeFileSource(val path: VolumePath, val type: VolumeType) : VolumeInitializer{

        sealed class VolumePath {
            /**
             *  for fixed file path witch are the same on every machine (eg. network drive or something like "C://Volume")
             */
            class Given(val filePath: String) : VolumePath()

            /**
             * the file path is taken from the VM parameter "-DVolumeFile=$path$" of each individual application
             */
            class Settings(val settingsName: String = "VolumeFile") : VolumePath()

            /**
             * the volume is a resource reachable by the java loader
             */
            class Resource(val path: String) : VolumePath()
        }

        enum class VolumeType {
            /**
             * tiff file format
             */
            TIFF,

            /**
             * Spim xml data format
             */
            SPIM
        }

        override fun initializeVolume(hub: Hub): Volume {

            val path = when (this.path) {
                is VolumePath.Given -> this.path.filePath
                is VolumePath.Settings -> {
                    Settings().get<String?>(this.path.settingsName)
                        ?: throw IllegalArgumentException(
                            "Setting ${this.path.settingsName} not set! " +
                                "Can't load volume."
                        )
                }
                is VolumePath.Resource -> {
                    javaClass.getResource(this.path.path)?.path
                        ?: throw IllegalArgumentException("Cant find resource ${this.path.path}")
                }
            }

            return when (this.type) {
                VolumeType.TIFF -> fromPath(Paths.get(path), hub)
                VolumeType.SPIM -> fromXML(path, hub, VolumeViewerOptions.options())
            }
        }
    }

    /**
     * Enum class for selecting a rendering method.
     */
    enum class RenderingMethod {
        MaxProjection,
        MinProjection,
        AlphaBlending
    }

    init {
        name = "Volume"

        hub.get<Settings>()?.setIfUnset("Volume.ParallelReads", false)

        addSpatial()

        when (dataSource) {
            is SpimDataMinimalSource -> {
                val spimData = dataSource.spimData

                timepointCount = dataSource.numTimepoints
                cacheControls.addCacheControl((spimData.sequenceDescription.imgLoader as ViewerImgLoader).cacheControl)

                // wraps legacy image formats (e.g., TIFF) if referenced in BDV XML
                WrapBasicImgLoader.wrapImgLoaderIfNecessary(spimData)
                viewerState = ViewerState(dataSource.sources, timepointCount)
                converterSetups.addAll(dataSource.converterSetups)

                WrapBasicImgLoader.removeWrapperIfPresent(spimData)
            }

            is VolumeDataSource.RAISource<*> -> {
                timepointCount = dataSource.numTimepoints
                // FIXME: bigdataviewer-core > 9.0.0 doesn't enjoy having 0 timepoints anymore :-(
                // We tell it here to have a least one, so far no ill side effects from that
                viewerState = ViewerState(dataSource.sources, max(1, timepointCount))
                converterSetups.addAll(dataSource.converterSetups)
            }

            is VolumeDataSource.NullSource -> {
                viewerState = ViewerState(emptyList(), 1)
                timepointCount = 1
            }
        }

        viewerState.sources.forEach { s -> s.isActive = true }
        viewerState.displayMode = DisplayMode.FUSED
        converterSetups.forEach {
            it.color = ARGBType(Int.MAX_VALUE)
        }

        if(hub.name != "dummyVolumeHub" && dataSource !is VolumeDataSource.NullSource){
            VolumeManager.regenerateVolumeManagerWithExtraVolume(this,hub)
        }
    }

    private fun NumericType<*>.toRange(): Pair<Float, Float> {
        return when(this) {
            is UnsignedByteType -> 0.0f to 255.0f
            is VolatileUnsignedByteType -> 0.0f to 255.0f
            is ByteType -> -127.0f to 128.0f
            is VolatileByteType -> -127.0f to 128.0f
            is UnsignedShortType -> 0.0f to 65535.0f
            is VolatileUnsignedShortType -> 0.0f to 65535.0f
            is ShortType -> -32768.0f to 32767.0f
            is VolatileShortType -> -32768.0f to 32767.0f
            is FloatType -> 0.0f to 1.0f
            is VolatileFloatType -> 0.0f to 1.0f
            else -> 0.0f to 1.0f
        }
    }


    private fun NumericType<*>.toBytesPerValue(): Int {
        return when(this) {
            is UnsignedByteType -> 1
            is VolatileUnsignedByteType -> 1
            is ByteType -> 1
            is VolatileByteType -> 1
            is UnsignedShortType -> 2
            is VolatileUnsignedShortType -> 2
            is ShortType -> 2
            is VolatileShortType -> 2
            is FloatType -> 4
            is VolatileFloatType -> 4
            else -> 4
        }
    }


    override fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable, additionalData: Any?) {
        if (fresh !is Volume) throw IllegalArgumentException("Update called with object of foreign class")
        super.update(fresh, getNetworkable, additionalData)
        this.colormap = fresh.colormap
        this.transferFunction = fresh.transferFunction
        this.slicingMode = fresh.slicingMode

        if (this.currentTimepoint != fresh.currentTimepoint) {
            this.goToTimepoint(fresh.currentTimepoint)
        }
    }

    override fun getConstructorParameters(): Any? {
        return initalizer
    }

    override fun constructWithParameters(parameters: Any, hub: Hub): Networkable {
        if (parameters is VolumeInitializer) {
            val vol = parameters.initializeVolume(hub)
            vol.initalizer = parameters
            return vol
        } else {
            throw IllegalArgumentException("Volume Initializer implementation as params expected")
        }
    }


    override fun getSubcomponents(): List<Networkable> {
        val tmp = super<DefaultNode>.getSubcomponents()
        return tmp
    }

    override fun createSpatial(): VolumeSpatial {
        return VolumeSpatial(this)
    }


    /**
     *  Calculates the histogram on the CPU.
     */
    override fun generateHistogram(volumeHistogramData: SimpleHistogramDataset): Int? {
        volumeHistogramData.removeAllBins()
        val bins = 1024
        // This generates a histogram over the whole volume ignoring the display range.
        val absoluteHistogram = generateHistogramSPIMSourceOnCPU(512, bins)
        if (absoluteHistogram != null) {
            // We now need to select only the bins we care about.
            val absoluteBinSize = absoluteHistogram.max() / bins.toDouble()
            val minDisplayRange = minDisplayRange.toDouble()
            val maxDisplayRange = maxDisplayRange.toDouble()

            var max = 100
            absoluteHistogram.forEachIndexed { index, longType ->
                val startOfAbsoluteBin = index * absoluteBinSize
                val endOfAbsoluteBin = (index+1) * absoluteBinSize
                if (minDisplayRange <= startOfAbsoluteBin && endOfAbsoluteBin < maxDisplayRange) {

                    val bin = SimpleHistogramBin(
                        startOfAbsoluteBin,
                        endOfAbsoluteBin,
                        true,
                        false
                    )
                    bin.itemCount = longType.get().toInt()
                    max = max(bin.itemCount, max)
                    volumeHistogramData.addBin(bin)
                }
            }
            return max
        }
        return null
    }

    /**
     * Return a histogram over the whole volume ignoring the display range. Uses the volumes viewState source (currently only using spimSource).
     * The function will select a miplevel which has less then [maximumResolution] voxels in side lengths and divide the results into [bins]
     * different bins.
     */
    private fun generateHistogramSPIMSourceOnCPU(maximumResolution: Int, bins: Int): Histogram1d<*>? {
        val type = viewerState.sources.firstOrNull()?.spimSource?.type ?: return null
        logger.info("Volume type is ${type.javaClass.simpleName}")
        val context = if(volumeManager.hub?.getApplication()?.scijavaContext != null) {
            volumeManager.hub?.getApplication()?.scijavaContext!!
        } else {
            Context(OpService::class.java)
        }
        val ops = context.getService(OpService::class.java)

        if(ops == null) {
            logger.warn("Could not create OpService from scijava context, returning null histogram.")
            return null
        }

        val miplevels = viewerState.sources.firstOrNull()?.spimSource?.numMipmapLevels ?: 0
        logger.info("Dataset has $miplevels miplevels")

        val reducedResolutionRAI = (0 until miplevels)
            .map { it to viewerState.sources.first().spimSource.getSource(0, it) }
            .firstOrNull { it.second.dimensionsAsLongArray().all { size -> size < maximumResolution } }

        val rai = if(reducedResolutionRAI == null) {
            val r = viewerState.sources.first().spimSource.getSource(0, 0)
            logger.info("Using default miplevel with dimensions ${r.dimensionsAsLongArray().joinToString("/")} for histogram calculation.")
            r
        } else {
            logger.info("Using miplevel ${reducedResolutionRAI.first} with dimensions ${reducedResolutionRAI.second.dimensionsAsLongArray().joinToString("/")} for histogram calculation.")
            reducedResolutionRAI.second
        }

        val histogram = measureTimedValue { ops.run("image.histogram", rai, bins) as Histogram1d<*> }

        logger.info("Histogram creation took ${histogram.duration.inWholeMilliseconds}ms")

        return histogram.value
    }

    private var slicingArray = FloatArray(4 * MAX_SUPPORTED_SLICING_PLANES)

    /**
     * Returns array of slicing plane equations for planes assigned to this volume.
     */
    fun slicingArray(): FloatArray {
        if (slicingPlaneEquations.size > MAX_SUPPORTED_SLICING_PLANES) {
            logger.warn("More than $MAX_SUPPORTED_SLICING_PLANES slicing planes for ${this.name} set. Ignoring additional planes.")
        }

        slicingPlaneEquations.entries.take(MAX_SUPPORTED_SLICING_PLANES).forEachIndexed { i, entry ->
            slicingArray[0+i*4] = entry.value.x
            slicingArray[1+i*4] = entry.value.y
            slicingArray[2+i*4] = entry.value.z
            slicingArray[3+i*4] = entry.value.w
        }

        return slicingArray
    }

    /**
     * Goes to the next available timepoint, returning the number of the updated timepoint.
     */
    fun nextTimepoint(): Int {
        return goToTimepoint(viewerState.currentTimepoint + 1)
    }

    /** Goes to the previous available timepoint, returning the number of the updated timepoint. */
    fun previousTimepoint(): Int {
        return goToTimepoint(viewerState.currentTimepoint - 1)
    }

    /** Goes to the [timepoint] given, returning the number of the updated timepoint. */
    open fun goToTimepoint(timepoint: Int): Int {
        val tp = if(timepoint == -1) {
            timepointCount
        } else {
            timepoint
        }
        val current = viewerState.currentTimepoint
        currentTimepoint = min(max(tp, 0), timepointCount - 1)
        logger.debug("Going to timepoint ${viewerState.currentTimepoint+1} of $timepointCount")

        if(current != viewerState.currentTimepoint) {
            volumeManager.notifyUpdate(this)
        }

        modifiedAt = System.nanoTime()
        return viewerState.currentTimepoint
    }

    /**
     * Goes to the last timepoint.
     */
    open fun goToLastTimepoint(): Int {
        return goToTimepoint(-1)
    }

    /**
     * Goes to the first timepoint.
     */
    open fun goToFirstTimepoint(): Int {
        return goToTimepoint(0)
    }

    fun prepareNextFrame() {
        cacheControls.prepareNextFrame()
    }

    /**
     * Returns the local scaling of the volume, taking voxel size and [pixelToWorldRatio] into account.
     */
    open fun localScale(): Vector3f {
        // we are using the first visible source here, which might of course change.
        // TODO: Figure out a better way to do this. It might be an issue for multi-view datasets.

        // TODO: are the voxel sizes determined here really not used?
        // val index = viewerState.visibleSourceIndices.firstOrNull()
        // var voxelSizes: VoxelDimensions = FinalVoxelDimensions("um", 1.0, 1.0, 1.0)
//        if(index != null) {
//            val source = viewerState.sources[index]
//            voxelSizes = source.spimSource.voxelDimensions ?: voxelSizes
//        }

        return Vector3f(
//            voxelSizes.dimension(0).toFloat() * pixelToWorldRatio,
//            voxelSizes.dimension(1).toFloat() * pixelToWorldRatio,
//            voxelSizes.dimension(2).toFloat() * pixelToWorldRatio
            pixelToWorldRatio,
            -1.0f * pixelToWorldRatio,
            pixelToWorldRatio
        )
    }

    /**
     * Samples a point from the currently used volume, [uv] is the texture coordinate of the volume, [0.0, 1.0] for
     * all of the components.
     *
     * Returns the sampled value as a [Float], or null in case nothing could be sampled.
     */
    open fun sample(uv: Vector3f, interpolate: Boolean = true): Float? {
        return null
    }

    /**
     * Takes samples along the ray from [start] to [end] from the currently active volume.
     * Values beyond [0.0, 1.0] for [start] and [end] will be clamped to that interval.
     *
     * Returns the list of samples (which might include `null` values in case a sample failed),
     * as well as the delta used along the ray, or null if the start/end coordinates are invalid.
     */
    open fun sampleRay(start: Vector3f, end: Vector3f): Pair<List<Float?>, Vector3f>? {
        return null
    }

    /**
     * Returns the volume's physical (voxel) dimensions.
     */
    open fun getDimensions(): Vector3i {
        return Vector3i(0)
    }

    @JvmOverloads
    open fun setTransferFunctionRange(min: Float, max: Float, forSetupId: Int = 0) {
        converterSetups.getOrNull(forSetupId)?.setDisplayRange(min.toDouble(), max.toDouble())
    }

    companion object {
        val setupId = AtomicInteger(0)
        val scifio: SCIFIO = SCIFIO()
        private val logger by lazyLogger()

        @JvmStatic @JvmOverloads fun fromSpimData(
            spimData: SpimDataMinimal,
            hub : Hub,
            options : VolumeViewerOptions = VolumeViewerOptions()
        ): Volume {
            val seq: AbstractSequenceDescription<*, *, *> = spimData.sequenceDescription

            val timepointCount = seq.timePoints.size()
            // wraps legacy image formats (e.g., TIFF) if referenced in BDV XML
            WrapBasicImgLoader.wrapImgLoaderIfNecessary(spimData)

            val converterSetups = ArrayList<ConverterSetup>()
            val sources = ArrayList<SourceAndConverter<*>>()
            // initialises setups and converters for all channels, and creates source.
            // These are then stored in [converterSetups] and [sources_].
            BigDataViewer.initSetups(spimData, converterSetups, sources)

            WrapBasicImgLoader.removeWrapperIfPresent(spimData)
            val ds = SpimDataMinimalSource(spimData,
                sources,
                converterSetups,
                timepointCount
            )
            return RAIVolume(ds, options, hub)
        }

        @JvmStatic
        fun forNetwork(
            params: VolumeInitializer,
            hub: Hub
        ): Volume = Volume().constructWithParameters(params, hub) as Volume

        @JvmStatic
        @JvmOverloads
        fun fromXML(
            path: String,
            hub: Hub,
            options : VolumeViewerOptions = VolumeViewerOptions()
        ): Volume {
            val spimData = XmlIoSpimDataMinimal().load(path)
            return fromSpimData(spimData, hub, options)
        }

        /**
         * Creates a [RAIVolume] object from [RandomAccessibleInterval] data.
         */
        @JvmStatic
        @JvmOverloads
        fun <T : RealType<T>> fromRAI(
            img: RandomAccessibleInterval<T>,
            type: T,
            axisOrder: AxisOrder = DEFAULT,
            name: String,
            hub: Hub,
            options: VolumeViewerOptions = VolumeViewerOptions()
        ): Volume {
            val converterSetups: ArrayList<ConverterSetup> = ArrayList()
            val stacks: ArrayList<RandomAccessibleInterval<T>> =
                AxisOrder.splitInputStackIntoSourceStacks(img, AxisOrder.getAxisOrder(axisOrder, img, false))
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
                    SourceAndConverter<T>(s, BigDataViewer.createConverterToARGB(type))
                )
                converterSetups.add(BigDataViewer.createConverterSetup(source, setupId.getAndIncrement()))
                sources.add(source)
            }

            @Suppress("UNCHECKED_CAST")
            val cacheControl = if (img is VolatileView<*, *>) {
                val viewData: VolatileViewData<T, Volatile<T>> =
                    (img as VolatileView<T, Volatile<T>>).volatileViewData
                viewData.cacheControl
            } else {
                null
            }

            val ds = VolumeDataSource.RAISource<T>(type, sources, converterSetups, numTimepoints, cacheControl)
            return RAIVolume(ds, options, hub)
        }

        /**
         * Creates a [RAIVolume] object from a given [SourceAndConverter] source from BigDataViewer.
         */
        @JvmStatic @JvmOverloads fun <T: RealType<T>> fromSourceAndConverter(
            source: SourceAndConverter<T>,
            type: T,
            name: String,
            hub: Hub,
            options: VolumeViewerOptions = VolumeViewerOptions()
        ): Volume {
            val converterSetups: ArrayList<ConverterSetup> = ArrayList()
            val sources = arrayListOf(source)
            val numTimepoints = 1

            val img = source.spimSource.getSource(0, 0)

            @Suppress("UNCHECKED_CAST")
            val cacheControl = if (img is VolatileView<*, *>) {
                val viewData: VolatileViewData<T, Volatile<T>> = (img as VolatileView<T, Volatile<T>>).volatileViewData
                viewData.cacheControl
            } else {
                null
            }

            val ds = VolumeDataSource.RAISource<T>(type, sources, converterSetups, numTimepoints, cacheControl)
            val volume = RAIVolume(ds, options, hub)
            volume.name = name

            return volume
        }

        /**
         * Overloaded method that creates a [BufferedVolume] with timepoints from a hashmap of Strings and ByteBuffers.
         * Volume dimensions can be set with [width], [height] and [depth].
         * [voxelDimensions] can be set with a float array. The method also takes VolumeViewerOptions as [options].
         */
        @Deprecated("Please use the version that takes List<Timepoint> as input instead of this one.")
        @JvmStatic @JvmOverloads fun <T: RealType<T>> fromBuffer(
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
            val list = CopyOnWriteArrayList<BufferedVolume.Timepoint>()
            volumes.forEach {
                list.add(BufferedVolume.Timepoint(it.key, it.value))
            }

            return fromBuffer(list, width, height, depth, type, hub, voxelDimensions, voxelUnit, options)
        }

        /**
         * Returns a [BufferedVolume] from a list of BufferedVolume timepoints.
         * Volume dimensions can be set with [width], [height] and [depth].
         * [voxelDimensions] can be set with a float array. The method also takes VolumeViewerOptions as [options].
         */
        @JvmStatic @JvmOverloads fun <T: RealType<T>> fromBuffer(
            volumes: List<BufferedVolume.Timepoint>,
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

            val timepoints = CopyOnWriteArrayList<BufferedVolume.Timepoint>(volumes)
            val s = BufferSource(
                timepoints,
                width,
                height,
                depth,
                FinalVoxelDimensions(voxelUnit, *(voxelDimensions.map { it.toDouble() }.toDoubleArray())),
                "",
                type
            )
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

//            (0 until byteSize/bytesPerVoxel).chunked(byteSize/4).forEachParallel { subList ->
            (0 until byteSize/bytesPerVoxel).forEach {
//                subList.forEach {
                    val x = it.rem(size)
                    val y = (it / size).rem(size)
                    val z = it / (size * size)

                    val dx = center - x
                    val dy = center - y
                    val dz = center - z

                    val offset = abs(noise.random3D((x + shift.x()) * f, (y + shift.y()) * f, (z + shift.z()) * f))
                    val d = sqrt(dx * dx + dy * dy + dz * dz) / size

                    val result = if(radius > Math.ulp(1.0f)) {
                        if(d - offset < radius) { ((d-offset)*range).toInt().toShort() } else { 0 }
                    } else {
                        ((d - offset) * range).toInt().toShort()
                    }

                    if(use16bit) {
                        buffer.asShortBuffer().put(it, result)
                    } else {
                        buffer.put(it, result.toByte())
                    }
//                }
            }

            return buffer
        }

        private fun readRawFile(path: Path, dimensions: Vector3i, bytesPerVoxel: Int, offsets: Pair<Long, Long>? = null): ByteBuffer {
            val buffer: ByteBuffer by lazy {

                val buffer = ByteArray(1024 * 1024)
                val stream = FileInputStream(path.toFile())
                if(offsets != null) {
                    stream.skip(offsets.first)
                }

                val imageData: ByteBuffer = MemoryUtil.memAlloc((bytesPerVoxel * dimensions.x * dimensions.y * dimensions.z))

                logger.debug(
                    "{}: Allocated {} bytes for image of {} containing {} per voxel",
                    path.fileName,
                    imageData.capacity(),
                    dimensions,
                    bytesPerVoxel
                )

                val start = System.nanoTime()
                var bytesRead = 0
                var total = 0
                while (true) {
                    var maxReadSize = minOf(buffer.size, imageData.capacity() - total)
                    maxReadSize = maxOf(maxReadSize, 1)
                    bytesRead = stream.read(buffer, 0, maxReadSize)

                    if(bytesRead < 0) {
                        break
                    }

                    imageData.put(buffer, 0, bytesRead)

                    total += bytesRead

                    if(offsets != null && total >= (offsets.second - offsets.first)) {
                        break
                    }
                }
                val duration = (System.nanoTime() - start) / 10e5
                logger.debug("Reading took $duration ms")

                imageData.flip()
                imageData
            }

            return buffer
        }

        /**
         * Reads a volume from the given [file].
         */
        @JvmStatic @JvmOverloads
        fun fromPath(file: Path, hub: Hub, onlyLoadFirst: Int? = null): BufferedVolume {
            if(file.normalize().toString().endsWith("raw")) {
                return fromPathRaw(file, hub, UnsignedByteType())
            }
            var volumeFiles: List<Path>
            if(Files.isDirectory(file)) {
                volumeFiles = Files
                    .list(file)
                    .filter { it.toString().endsWith(".tif") && Files.isRegularFile(it) && Files.isReadable(it) }
                    .toList()

                if(onlyLoadFirst != null) {
                    volumeFiles = volumeFiles.subList(0, onlyLoadFirst)
                }

            } else {
                volumeFiles = listOf(file)
            }

            val volumes = CopyOnWriteArrayList<BufferedVolume.Timepoint>()
            val dims = Vector3i()

            var type: NumericType<*>? = null
            var reader: ReaderFilter? = null
            volumeFiles.forEach { v ->
                val id = v.fileName.toString()
                logger.debug("Reading v.toFile().toString()")
                val localReader = scifio.initializer().initializeReader(FileLocation(v.toFile()))
                with(localReader.openPlane(0, 0)) {
                    dims.x = lengths[0].toInt()
                    dims.y = lengths[1].toInt()
                    dims.z = localReader.getPlaneCount(0).toInt()
                }
                if(reader == null) {
                    reader = localReader
                }

                type = when (localReader.openPlane(0, 0).imageMetadata.pixelType) {
                    FormatTools.INT8 -> ByteType()
                    FormatTools.INT16 -> ShortType()
                    FormatTools.INT32 -> IntType()

                    FormatTools.UINT8 -> UnsignedByteType()
                    FormatTools.UINT16 -> UnsignedShortType()
                    FormatTools.UINT32 -> UnsignedIntType()

                    FormatTools.FLOAT -> FloatType()

                    else -> {
                        logger.error("Unknown scif.io pixel type ${localReader.openPlane(0, 0).imageMetadata.pixelType}, assuming unsigned byte.")
                        UnsignedByteType()
                    }
                }

                val bytesPerVoxel = localReader.openPlane(0, 0).imageMetadata.bitsPerPixel / 8
                localReader.openPlane(0, 0).imageMetadata.pixelType

                logger.debug("Loading $id from disk")
                val imageData: ByteBuffer = MemoryUtil.memAlloc((bytesPerVoxel * dims.x * dims.y * dims.z))

                logger.debug(
                    "{}: Allocated {} bytes for {} {}bit image of {}",
                    file.fileName,
                    imageData.capacity(),
                    type,
                    8 * bytesPerVoxel,
                    dims
                )

                logger.debug("Volume is little endian")
                val planeSize = bytesPerVoxel * dims.x * dims.y

                // Only do parallel reads if the settings indicate we want to do that,
                // or if the file is a NIFTi file, or contains more than 200 planes.
                val parallelReadingRequested = hub.get<Settings>()?.get("Volume.ParallelReads", false) ?: false
                        || file.name.lowercase().endsWith(".nii.gz")

                val start = System.nanoTime()
                if(parallelReadingRequested) {
                    // Cache scifio's ReaderFilters per-thread, as their initialisation is expensive
                    val readers = ConcurrentHashMap<Thread, ReaderFilter>()

                    // Each plane (read: z-slice) will be read by an async Job.
                    // These jobs are distributed among worker threads. This is the reason
                    // why the current thread object serves as an index to the [readers] hash map.
                    (0 until localReader.getPlaneCount(0)).forEachIndexedAsync { index, plane ->
                        val thread = Thread.currentThread()
                        val myReader = readers.getOrPut(thread) {
                            scifio.initializer().initializeReader(FileLocation(file.toFile()))
                        }

                        val bytes = myReader.openPlane(0, plane).bytes
                        // In order to prevent mess-ups, we're working on a duplicate of [imageData]
                        // here, so it's position(), remaining() etc. remain at the original, correct values.
                        val view = imageData.duplicate().order(ByteOrder.LITTLE_ENDIAN)

                        // For writing the image data to the view, we move the buffer's position
                        // to the place where the plane's data needs to be.
                        view.position(index * planeSize)
                        view.put(bytes)
                    }

                    val duration = (System.nanoTime() - start) / 10e5
                    logger.debug("Reading took $duration ms, used ${readers.size} parallel readers.")
                    readers.forEach { it.value.close() }
                    readers.clear()
                } else {
                    (0 until localReader.getPlaneCount(0)).forEach { plane ->
                        // Same as above, with the difference that we only use one reader to
                        // simply read bytes Plane-wise sequentially, and add them to the buffer.
                        val bytes = localReader.openPlane(0, plane).bytes
                        val view = imageData.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                        view.put(bytes)
                    }

                    val duration = (System.nanoTime() - start) / 10e5
                    logger.debug("Reading took $duration ms, no parallel readers.")
                }
            }



            // TODO: Kotlin compiler issue, see https://youtrack.jetbrains.com/issue/KT-37955
            val volume = when(type) {
                is ByteType -> fromBuffer(volumes, dims.x, dims.y, dims.z, ByteType(), hub)
                is UnsignedByteType -> fromBuffer(volumes, dims.x, dims.y, dims.z, UnsignedByteType(), hub)
                is ShortType -> fromBuffer(volumes, dims.x, dims.y, dims.z, ShortType(), hub)
                is UnsignedShortType -> fromBuffer(volumes, dims.x, dims.y, dims.z, UnsignedShortType(), hub)
                is IntType -> fromBuffer(volumes, dims.x, dims.y, dims.z, IntType(), hub)
                is UnsignedIntType -> fromBuffer(volumes, dims.x, dims.y, dims.z, UnsignedIntType(), hub)
                is FloatType -> fromBuffer(volumes, dims.x, dims.y, dims.z, FloatType(), hub)
                else -> throw UnsupportedOperationException("Image type ${type?.javaClass?.simpleName} not supported for volume data.")
            }

            reader?.metadata?.table?.forEach { key, value ->
                logger.debug("Populating volume metadata")
                volume.metadata[key] = value
            }

            return volume
        }

        /**
         * Reads raw volumetric data from a [file], assuming the input
         * data is 16bit Unsigned Int.
         *
         * Returns the new volume.
         */
        @JvmStatic
        fun <T: RealType<T>> fromPathRaw(
            file: Path,
            hub: Hub
        ): BufferedVolume {
            return fromPathRaw(file, hub, UnsignedShortType())
        }

        /**
         * Reads raw volumetric data from a [file], with the [type] being
         * explicitly specified.
         *
         * Returns the new volume.
         */
        @JvmStatic
        fun <T: RealType<T>> fromPathRaw(
            file: Path,
            hub: Hub,
            type: T
        ): BufferedVolume {

            val infoFile: Path
            val volumeFiles: List<Path>

            if(Files.isDirectory(file)) {
                volumeFiles = Files.list(file).filter { it.toString().endsWith(".raw") && Files.isRegularFile(it) && Files.isReadable(it) }.toList()
                infoFile = file.resolve("stacks.info")
            } else {
                volumeFiles = listOf(file)
                infoFile = file.resolveSibling("stacks.info")
            }

            val lines = Files.lines(infoFile).toList()

            logger.debug("reading stacks.info (${lines.joinToString()}) (${lines.size} lines)")
            val dimensions = Vector3i(lines.get(0).split(",").map { it.toInt() }.toIntArray())
            logger.debug("setting dim to ${dimensions.x}/${dimensions.y}/${dimensions.z}")
            logger.debug("Got ${volumeFiles.size} volumes")

            val volumes = CopyOnWriteArrayList<BufferedVolume.Timepoint>()
            volumeFiles.forEach { v ->
                val id = v.fileName.toString()
                logger.debug("Loading $id from disk")

                val bytesPerVoxel = type.bitsPerPixel/8
                val buffer = readRawFile(v, dimensions, bytesPerVoxel)

                volumes.add(BufferedVolume.Timepoint(id, buffer))
            }

            return fromBuffer(volumes, dimensions.x, dimensions.y, dimensions.z, type, hub)
        }

        /**
         * Reads raw volumetric data from a [file], splits it into buffers of at most, and as close as possible to,
         * [sizeLimit] bytes and creates a volume from each buffer.
         *
         * Returns the list of volumes.
         */
        @JvmStatic
        fun <T: RealType<T>> fromPathRawSplit(
            file: Path,
            type: T,
            sizeLimit: Long = 2000000000L,
            hub: Hub
        ): Pair<Node, List<Volume>> {

            val infoFile = file.resolveSibling("stacks.info")

            val lines = Files.lines(infoFile).toList()

            logger.debug("reading stacks.info (${lines.joinToString()}) (${lines.size} lines)")
            val dimensions = Vector3i(lines.get(0).split(",").map { it.toInt() }.toIntArray())
            val bytesPerVoxel = type.bitsPerPixel/8

            var slicesRemaining = dimensions.z
            var bytesRead = 0L
            var numPartitions = 0

            val slicesPerPartition = floor(sizeLimit.toFloat()/(bytesPerVoxel * dimensions.x * dimensions.y)).toInt()

            val children = ArrayList<Volume>()

            while (slicesRemaining > 0) {
                val slices = if(slicesRemaining > slicesPerPartition) {
                    slicesPerPartition
                } else {
                    slicesRemaining
                }

                val partitionDims = Vector3i(dimensions.x, dimensions.y, slices)
                val size = bytesPerVoxel * dimensions.x * dimensions.y * slices

                val window = bytesRead to bytesRead+size-1

                logger.debug("Reading raw file with offsets: $window")
                val buffer = readRawFile(file, partitionDims, bytesPerVoxel, window)

                val volume = ArrayList<BufferedVolume.Timepoint>()
                volume.add(BufferedVolume.Timepoint(file.fileName.toString(), buffer))
                children.add(fromBuffer(volume, partitionDims.x, partitionDims.y, partitionDims.z, type, hub))

                slicesRemaining -= slices
                numPartitions += 1
                bytesRead += size
            }

            val parent = RichNode()
            children.forEach { parent.addChild(it) }

            return parent to children
        }

        /** Amount of supported slicing planes per volume, see also sampling shader segments */
        internal const val MAX_SUPPORTED_SLICING_PLANES = 16

    }

    open class VolumeSpatial(val volume: Volume): DefaultSpatial(volume) {
        /**
         * Composes the world matrix for this volume node, taken voxel size and [pixelToWorldRatio]
         * into account.
         */
        override fun composeModel() {
            val shift = Vector3f(volume.getDimensions()) * (-0.5f)

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


