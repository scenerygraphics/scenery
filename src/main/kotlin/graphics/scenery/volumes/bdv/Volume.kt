package graphics.scenery.volumes.bdv

import bdv.BigDataViewer
import bdv.ViewerImgLoader
import bdv.cache.CacheControl
import bdv.img.cache.VolatileGlobalCellCache
import bdv.spimdata.SpimDataMinimal
import bdv.spimdata.WrapBasicImgLoader
import bdv.spimdata.XmlIoSpimDataMinimal
import bdv.tools.brightness.ConverterSetup
import bdv.tools.brightness.SetupAssignments
import bdv.util.AxisOrder
import bdv.util.RandomAccessibleIntervalSource
import bdv.util.RandomAccessibleIntervalSource4D
import bdv.viewer.DisplayMode
import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import bdv.viewer.VisibilityAndGrouping
import bdv.viewer.state.SourceGroup
import bdv.viewer.state.SourceState
import bdv.viewer.state.ViewerState
import bvv.util.BvvStackSource
import coremem.enums.NativeTypeEnum
import graphics.scenery.*
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

    val setupAssignments: SetupAssignments
    val converterSetups = ArrayList<ConverterSetup>()
    val visibilityAndGrouping: VisibilityAndGrouping
    val maxTimepoint: Int
    val viewerState: ViewerState
    val regularStacks: List<BufferedSimpleStack3D<*>>?
    var renderStateUpdated: Boolean = false

    /** The transfer function to use for the volume. Flat by default. */
    var transferFunction: TransferFunction = TransferFunction.flat(1.0f)

    /** The color map for the volume. */
    var colormap: Colormap = Colormap.get("viridis")

    val volumeManager: VolumeManager

    val sources = ArrayList<SourceState<*>>()

    var cacheControl: CacheControl? = null

    /** Current timepoint in the set of [stacks]. */
    var currentTimepoint: Int
        get() { return viewerState.currentTimepoint }
        set(value) {viewerState.currentTimepoint = value}

    /*sealed class VolumeDataSource() {
        abstract val setupAssignments: SetupAssignments
        abstract val converterSetups: ArrayList<ConverterSetup>
        abstract val viewerState: ViewerState
        abstract val visibilityAndGrouping: VisibilityAndGrouping
        abstract val maxTimepoint: Int

        class SpimData(val spimData: SpimDataMinimal, val options: VolumeViewerOptions) {
            val converterSetups = ArrayList<ConverterSetup>()
            val setupAssignments: SetupAssignments
            val viewerState: ViewerState
            val visibilityAndGrouping: VisibilityAndGrouping
            val stacks: SpimDataStacks?
            val maxTimepoint: Int

            init {
                stacks = SpimDataStacks(spimData)

                val sources = ArrayList<SourceAndConverter<*>>()

                bdv.BigDataViewer.initSetups(spimData, converterSetups, sources)

                // TODO: Fix color ranges
                setupAssignments = SetupAssignments(converterSetups, 0.0, 65535.0)
                if(setupAssignments.minMaxGroups.size > 0) {
                    val group = setupAssignments.minMaxGroups[0]
                    setupAssignments.converterSetups.forEach {
                        setupAssignments.moveSetupToGroup(it, group)
                    }
                }
                maxTimepoint = spimData.sequenceDescription.timePoints.timePointsOrdered.size - 1

                val opts = options.values

                val numGroups = opts.numSourceGroups
                val groups = ArrayList<SourceGroup>(numGroups)
                for (i in 0 until numGroups)
                    groups.add(SourceGroup("group " + Integer.toString(i + 1)))
                val numTimepoints = stacks.numTimepoints
                viewerState = ViewerState(sources, groups, numTimepoints)
                for (i in Math.min(numGroups, sources.size) - 1 downTo 0)
                    viewerState.sourceGroups[i].addSource(i)

                visibilityAndGrouping = VisibilityAndGrouping(viewerState)
                for (i in 0 until sources.size) {
                    visibilityAndGrouping.sources[i].isActive = true
                }

                viewerState.displayMode = DisplayMode.FUSED

                converterSetups.forEach {
                    it.color = ARGBType(Random.nextInt(0, 255*255*255))
                }

            }
        }

        class RAII<T: NumericType<T>>(val img: RandomAccessibleInterval<T>, val options: VolumeViewerOptions, val axisOrder: AxisOrder, val name: String = "") {
            val converterSetups = ArrayList<ConverterSetup>()
            val numTimepoints: Int
            val stacks = ArrayList<Stack3D<T>>()

            init {
                val type: T = Util.getTypeFromInterval(img)
                if (img is VolatileView<*, *>) {
                    // TODO: Add cache control
                }

                val axisOrder = AxisOrder.getAxisOrder(axisOrder, img, false)
                val split = AxisOrder.splitInputStackIntoSourceStacks(img, axisOrder)
                val sourceTransform = AffineTransform3D()

                var tp = 1
                split.forEach { stack ->
                    val source: Source<T> = if(stack.numDimensions() > 3) {
                        tp = stack.max(3).toInt() + 1
                        RandomAccessibleIntervalSource4D<T>(stack, type, sourceTransform, name)
                    } else {
                        tp = 1
                        RandomAccessibleIntervalSource<T>(stack, type, sourceTransform, name)
                    }

                    val s = object: SimpleStack3D<T> {
                        override fun getSourceTransform() : AffineTransform3D {
                            return sourceTransform
                        }

                        override fun getType() : T {
                            return type
                        }

                        override fun getImage() : RandomAccessibleInterval<T> {
                            return stack
                        }
                    }

                    stacks.add(s)
                }

                numTimepoints = tp
            }
        }

        class BufferedVolume(val volumes: HashMap<String, ByteBuffer>, val descriptor : VolumeDescriptor) {
            val numTimepoints: Int = volumes.size
            val converterSetups = ArrayList<ConverterSetup>()
            val setupAssignments: SetupAssignments
            val viewerState: ViewerState
            val visibilityAndGrouping: VisibilityAndGrouping
            val stacks: SpimDataStacks? = null


            init {
                // TODO: fix color ranges
                setupAssignments = SetupAssignments(converterSetups, 0.0, 65535.0)

                val sources = ArrayList<SourceAndConverter<*>>()

                if(setupAssignments.minMaxGroups.size > 0) {
                    val group = setupAssignments.minMaxGroups[0]
                    setupAssignments.converterSetups.forEach {
                        setupAssignments.moveSetupToGroup(it, group)
                    }
                }

                val groups = ArrayList<SourceGroup>(1)
                groups.add(SourceGroup("group 1"))
                viewerState = ViewerState(sources, groups, numTimepoints)

                visibilityAndGrouping = VisibilityAndGrouping(viewerState)
                for (i in 0 until sources.size) {
                    visibilityAndGrouping.sources[i].isActive = true
                }
            }
        }
     */

    sealed class VolumeDataSource {
        class SpimDataMinimalSource(val spimData : SpimDataMinimal) : VolumeDataSource()
        class RAIISource<T: NumericType<T>>(val img: RandomAccessibleInterval<T>, val type: NumericType<T>, val sources: List<SourceAndConverter<T>>, val converterSetups: ArrayList<ConverterSetup>, val axisOrder: AxisOrder, val numTimepoints: Int, val name: String = "") : VolumeDataSource()
        class BufferSource(val volumes: HashMap<String, ByteBuffer>, val descriptor: VolumeDescriptor) : VolumeDataSource()
    }

    data class VolumeDescriptor(val width: Int,
                                val height: Int,
                                val depth: Int,
                                val dataType: NativeTypeEnum,
                                val bytesPerVoxel: Int)


    init {
        val socs = ArrayList<SourceAndConverter<*>>()

        // data-source independent part
        val opts = options.values

        val numGroups = opts.numSourceGroups
        val groups = ArrayList<SourceGroup>(numGroups)
        for (i in 0 until numGroups)
            groups.add(SourceGroup("group " + Integer.toString(i + 1)))

        when(dataSource) {
            is SpimDataMinimalSource -> {
                val spimData = dataSource.spimData

                val seq: AbstractSequenceDescription<*, *, *> = spimData.getSequenceDescription()
                maxTimepoint = seq.getTimePoints().size()
                cacheControl = (seq.getImgLoader() as ViewerImgLoader).cacheControl
                val cache = (seq.getImgLoader() as ViewerImgLoader).cacheControl as VolatileGlobalCellCache
//                handle.getBvvHandle().getCacheControls().addCacheControl(cache)
                cache.clearCache()

                WrapBasicImgLoader.wrapImgLoaderIfNecessary(spimData)
                val s = ArrayList<SourceAndConverter<*>>()
                BigDataViewer.initSetups(spimData, ArrayList(), s)

                viewerState = ViewerState(socs, groups, maxTimepoint)
                val bvvSources: MutableList<BvvStackSource<*>> = ArrayList()
                for (source in s) {
                    val setup = BigDataViewer.createConverterSetup(source, setupId.getAndIncrement())
                    val setups = listOf(setup)

                    converterSetups.add(setup)
                    sources.add(SourceState.create(source, viewerState))
                }

                setupAssignments = SetupAssignments(converterSetups, 0.0, 65535.0)

                WrapBasicImgLoader.removeWrapperIfPresent(spimData)
                regularStacks = null
            }

            is VolumeDataSource.RAIISource<*> -> {
                maxTimepoint = dataSource.numTimepoints
                setupAssignments = SetupAssignments(dataSource.converterSetups, 0.0, 65535.0)
                if (setupAssignments.minMaxGroups.size > 0) {
                    val group = setupAssignments.minMaxGroups[0]
                    setupAssignments.converterSetups.forEach {
                        setupAssignments.moveSetupToGroup(it, group)
                    }
                }

                viewerState = ViewerState(socs, groups, maxTimepoint)
                socs.addAll(dataSource.sources)
                TODO("How to generate SpimDataStacks here?")
//                outOfCoreStacks = SpimDataStacks(SpimDataMinimal)
                dataSource.sources.forEach { source ->
                    sources.add(SourceState.create(source, viewerState))
                }

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
                setupAssignments = SetupAssignments(converterSetups, 0.0, 65535.0)
                maxTimepoint = dataSource.volumes.size

                viewerState = ViewerState(socs, groups, maxTimepoint)
            }
        }


        for (i in Math.min(numGroups, socs.size) - 1 downTo 0)
            viewerState.sourceGroups[i].addSource(i)

        visibilityAndGrouping = VisibilityAndGrouping(viewerState)
        for (i in 0 until socs.size) {
            visibilityAndGrouping.sources[i].isActive = true
        }

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

        fun fromSpimData(spimData: SpimDataMinimal, hub : Hub, options : VolumeViewerOptions = VolumeViewerOptions()): graphics.scenery.volumes.bdv.Volume {
            val ds = VolumeDataSource.SpimDataMinimalSource(spimData)
            return graphics.scenery.volumes.bdv.Volume(ds, options, hub)
        }

        fun fromXML(path: String, hub: Hub, options : VolumeViewerOptions = VolumeViewerOptions()): graphics.scenery.volumes.bdv.Volume {
            val spimData = XmlIoSpimDataMinimal().load(path)
            val ds = VolumeDataSource.SpimDataMinimalSource(spimData)
            return graphics.scenery.volumes.bdv.Volume(ds, options, hub)
        }

        fun <T: NumericType<T>> fromRAII(img: RandomAccessibleInterval<T>, type: T, axisOrder: AxisOrder, name: String, hub: Hub, options: VolumeViewerOptions = VolumeViewerOptions()): graphics.scenery.volumes.bdv.Volume {
            val converterSetups: ArrayList<ConverterSetup> = ArrayList()
            val stacks: ArrayList<RandomAccessibleInterval<T>> = AxisOrder.splitInputStackIntoSourceStacks(img, axisOrder)
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

            val ds = VolumeDataSource.RAIISource<T>(img, type, sources, converterSetups, axisOrder, numTimepoints)
            return Volume(ds, options, hub)
        }

        fun fromBuffer(): graphics.scenery.volumes.bdv.Volume {
            TODO("Still need to implement buffer support")
        }
    }
}
