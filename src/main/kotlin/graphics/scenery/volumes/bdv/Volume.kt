package graphics.scenery.volumes.bdv

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
import bdv.viewer.DisplayMode
import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import bdv.viewer.state.ViewerState
import coremem.enums.NativeTypeEnum
import graphics.scenery.DelegatesRendering
import graphics.scenery.GeometryType
import graphics.scenery.HasGeometry
import graphics.scenery.Hub
import graphics.scenery.Node
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.bdv.Volume.VolumeDataSource.SpimDataMinimalSource
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription
import net.imglib2.RandomAccessibleInterval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.NumericType
import net.imglib2.type.numeric.integer.ByteType
import net.imglib2.type.numeric.integer.ShortType
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import tpietzsch.example2.VolumeViewerOptions
import tpietzsch.multires.MultiResolutionStack3D
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class Volume(val dataSource: VolumeDataSource, val options: VolumeViewerOptions, val hub: Hub) : DelegatesRendering(), HasGeometry {
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
    val maxTimepoint: Int
    val viewerState: ViewerState
    val regularStacks: List<BufferedSimpleStack3D<*>>?
    var renderStateUpdated: Boolean = false

    /** The transfer function to use for the volume. Flat by default. */
    var transferFunction: TransferFunction = TransferFunction.flat(1.0f)

    /** The color map for the volume. */
    var colormap: Colormap = Colormap.get("viridis")

    val volumeManager: VolumeManager

    // TODO IS THIS REQUIRED??
    var cacheControl: CacheControl? = null

    /** Current timepoint in the set of [stacks]. */
    var currentTimepoint: Int
        get() { return viewerState.currentTimepoint }
        set(value) {viewerState.currentTimepoint = value}

    sealed class VolumeDataSource {
        class SpimDataMinimalSource(val spimData : SpimDataMinimal) : VolumeDataSource()
        class RAIISource<T: NumericType<T>>(
            val type: NumericType<T>,
            val sources: List<SourceAndConverter<T>>,
            val converterSetups: ArrayList<ConverterSetup>,
            val numTimepoints: Int ) : VolumeDataSource()
        class BufferSource(val volumes: HashMap<String, ByteBuffer>, val descriptor: VolumeDescriptor) : VolumeDataSource()
    }

    data class VolumeDescriptor(val width: Int,
                                val height: Int,
                                val depth: Int,
                                val dataType: NativeTypeEnum,
                                val bytesPerVoxel: Int)


    init {
        when(dataSource) {
            is SpimDataMinimalSource -> {
                val spimData = dataSource.spimData

                val seq: AbstractSequenceDescription<*, *, *> = spimData.getSequenceDescription()
                maxTimepoint = seq.getTimePoints().size()
                cacheControl = (seq.getImgLoader() as ViewerImgLoader).cacheControl

                // wraps legacy image formats (e.g., TIFF) if referenced in BDV XML
                WrapBasicImgLoader.wrapImgLoaderIfNecessary(spimData)

                val sources = ArrayList<SourceAndConverter<*>>()
                // initialises setups and converters for all channels, and creates source.
                // These are then stored in [converterSetups] and [sources_].
                BigDataViewer.initSetups(spimData, converterSetups, sources)

                viewerState = ViewerState(sources, maxTimepoint)

                WrapBasicImgLoader.removeWrapperIfPresent(spimData)
                regularStacks = null
            }

            is VolumeDataSource.RAIISource<*> -> {
                maxTimepoint = dataSource.numTimepoints
                viewerState = ViewerState(dataSource.sources, maxTimepoint)
                converterSetups.addAll( dataSource.converterSetups );
//                TODO("How to generate SpimDataStacks here?")
//                outOfCoreStacks = SpimDataStacks(SpimDataMinimal)

                regularStacks = null
            }

            is VolumeDataSource.BufferSource -> {
                val dimensions = intArrayOf(
                    dataSource.descriptor.width,
                    dataSource.descriptor.height,
                    dataSource.descriptor.depth)

                regularStacks = when(dataSource.descriptor.dataType) {
                    NativeTypeEnum.Byte ->
                        dataSource.volumes
                            .map { BufferedSimpleStack3D<ByteType>(it.value, ByteType(), dimensions) }
                            .toList()

                    NativeTypeEnum.UnsignedByte ->
                        dataSource.volumes
                            .map { BufferedSimpleStack3D<UnsignedByteType>(it.value, UnsignedByteType(), dimensions) }
                            .toList()

                    NativeTypeEnum.Short ->
                        dataSource.volumes
                            .map { BufferedSimpleStack3D<ShortType>(it.value, ShortType(), dimensions) }
                            .toList()

                    NativeTypeEnum.UnsignedShort ->
                        dataSource.volumes
                            .map { BufferedSimpleStack3D<UnsignedShortType>(it.value, UnsignedShortType(), dimensions) }
                            .toList()

                    NativeTypeEnum.Int -> TODO()
                    NativeTypeEnum.UnsignedInt -> TODO()
                    NativeTypeEnum.Long -> TODO()
                    NativeTypeEnum.UnsignedLong -> TODO()
                    NativeTypeEnum.HalfFloat -> TODO()
                    NativeTypeEnum.Float -> TODO()
                    NativeTypeEnum.Double -> TODO()
                }

                // TODO: Correctly generate ConverterSetups
                val converterSetups = ArrayList<ConverterSetup>()
                maxTimepoint = dataSource.volumes.size

                val sources_ = ArrayList<SourceAndConverter<*>>()
                viewerState = ViewerState(sources_, maxTimepoint)
            }
        }

        viewerState.sources.forEach { s -> s.isActive = true }
        viewerState.displayMode = DisplayMode.FUSED

        converterSetups.forEach {
            it.color = ARGBType(Random.nextInt(0, 255*255*255))
        }

        volumeManager = hub.get<VolumeManager>() ?: hub.add(VolumeManager(hub))
        volumeManager.add(this)
        delegate = volumeManager
    }

    fun getStack(timepoint: Int, setupId: Int, volatile: Boolean): MultiResolutionStack3D<*>? {
//        return outOfCoreStacks?.getStack(timepoint, setupId, volatile)
        //FIXME
        return null
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
    fun goToTimePoint(timepoint: Int): Int {
        val current = viewerState.currentTimepoint
        viewerState.currentTimepoint = min(max(timepoint, 0), maxTimepoint)
        logger.info("Going to timepoint ${viewerState.currentTimepoint} of $maxTimepoint")

        if(current != viewerState.currentTimepoint) {
            volumeManager.notifyUpdate(this)
        }

        return viewerState.currentTimepoint
    }

    fun prepareNextFrame() {
        cacheControl?.prepareNextFrame()
    }

    fun shuffleColors() {
        converterSetups.forEach {
            it.color = ARGBType(Random.nextInt(0, 255*255*255))
        }
    }

    companion object {
        val setupId = AtomicInteger(0)

        fun fromSpimData(spimData: SpimDataMinimal, hub : Hub, options : VolumeViewerOptions = VolumeViewerOptions()): Volume {
            val ds = VolumeDataSource.SpimDataMinimalSource(spimData)
            return Volume(ds, options, hub)
        }

        fun fromXML(path: String, hub: Hub, options : VolumeViewerOptions = VolumeViewerOptions()): Volume {
            val spimData = XmlIoSpimDataMinimal().load(path)
            val ds = VolumeDataSource.SpimDataMinimalSource(spimData)
            return Volume(ds, options, hub)
        }

        fun <T: NumericType<T>> fromRAII(img: RandomAccessibleInterval<T>, type: T, axisOrder: AxisOrder = DEFAULT, name: String, hub: Hub, options: VolumeViewerOptions = VolumeViewerOptions()): Volume {
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

            val ds = VolumeDataSource.RAIISource<T>(type, sources, converterSetups, numTimepoints)
            return Volume(ds, options, hub)
        }

        fun fromBuffer(): Volume {
            TODO("Still need to implement buffer support")
        }
    }
}
