package graphics.scenery.volumes.bdv

import bdv.BigDataViewer
import bdv.spimdata.SpimDataMinimal
import bdv.spimdata.XmlIoSpimDataMinimal
import bdv.tools.brightness.ConverterSetup
import bdv.tools.brightness.SetupAssignments
import bdv.util.AxisOrder
import bdv.util.RandomAccessibleIntervalSource
import bdv.util.RandomAccessibleIntervalSource4D
import bdv.util.volatiles.VolatileView
import bdv.viewer.DisplayMode
import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import bdv.viewer.VisibilityAndGrouping
import bdv.viewer.state.SourceGroup
import bdv.viewer.state.ViewerState
import coremem.enums.NativeTypeEnum
import graphics.scenery.DelegatesRendering
import graphics.scenery.GeometryType
import graphics.scenery.HasGeometry
import graphics.scenery.Hub
import graphics.scenery.Node
import graphics.scenery.State
import graphics.scenery.volumes.Volume
import net.imglib2.Interval
import net.imglib2.RandomAccessible
import net.imglib2.RandomAccessibleInterval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.NumericType
import net.imglib2.util.Util
import tpietzsch.example2.VolumeViewerOptions
import tpietzsch.multires.MultiResolutionStack3D
import tpietzsch.multires.ResolutionLevel3D
import tpietzsch.multires.SimpleStack3D
import tpietzsch.multires.SpimDataStacks
import tpietzsch.multires.Stack3D
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.file.Path
import java.util.ArrayList
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class BDVNode(val spimData: SpimDataMinimal, val options: VolumeViewerOptions, val hub: Hub) : DelegatesRendering(), HasGeometry {
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
    val stacks: SpimDataStacks
    var renderStateUpdated: Boolean = false

    val volumeManager: VolumeManager

    /** Current timepoint in the set of [stacks]. */
    var currentTimepoint: Int
        get() { return viewerState.currentTimepoint }
        set(value) {viewerState.currentTimepoint = value}

    sealed class VolumeDataSource() {
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
    }

    data class VolumeDescriptor(val width: Long,
                                val height: Long,
                                val depth: Long,
                                val dataType: NativeTypeEnum,
                                val bytesPerVoxel: Int,
                                val data: ByteBuffer)


    init {
        stacks = SpimDataStacks(spimData)

        val sources = ArrayList<SourceAndConverter<*>>()

        BigDataViewer.initSetups(spimData, converterSetups, sources)

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

        volumeManager = hub.get<VolumeManager>() ?: hub.add(VolumeManager(hub))
        volumeManager.add(this)
        delegate = volumeManager
    }

    fun getStack(timepoint: Int, setupId: Int, volatile: Boolean): MultiResolutionStack3D<*> {
        return stacks.getStack(timepoint, setupId, volatile)
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
        stacks.cacheControl.prepareNextFrame()
    }

    fun shuffleColors() {
        converterSetups.forEach {
            it.color = ARGBType(Random.nextInt(0, 255*255*255))
        }
    }
}
