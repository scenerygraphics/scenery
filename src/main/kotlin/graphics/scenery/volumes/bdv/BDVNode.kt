package graphics.scenery.volumes.bdv

import bdv.BigDataViewer
import bdv.spimdata.SpimDataMinimal
import bdv.spimdata.XmlIoSpimDataMinimal
import bdv.tools.brightness.ConverterSetup
import bdv.tools.brightness.SetupAssignments
import bdv.viewer.DisplayMode
import bdv.viewer.SourceAndConverter
import bdv.viewer.VisibilityAndGrouping
import bdv.viewer.state.SourceGroup
import bdv.viewer.state.ViewerState
import graphics.scenery.Hub
import graphics.scenery.Node
import net.imglib2.type.numeric.ARGBType
import tpietzsch.example2.VolumeViewerOptions
import tpietzsch.multires.MultiResolutionStack3D
import tpietzsch.multires.SpimDataStacks
import java.util.ArrayList
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class BDVNode(val source: String, val options: VolumeViewerOptions, val hub: Hub) : Node("BDVNode") {
    val setupAssignments: SetupAssignments
    val converterSetups = ArrayList<ConverterSetup>()
    val visibilityAndGrouping: VisibilityAndGrouping
    val maxTimepoint: Int
    val state: ViewerState
    val stacks: SpimDataStacks
    var renderStateUpdated: Boolean = false

    val volumeManager: VolumeManager

    /** Current timepoint in the set of [stacks]. */
    var currentTimepoint: Int
        get() { return state.currentTimepoint }
        set(value) {state.currentTimepoint = value}


    init {
        volumeManager = hub.get<VolumeManager>() ?: hub.add(VolumeManager(hub))

        val spimData: SpimDataMinimal = XmlIoSpimDataMinimal().load(source)
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
        state = ViewerState(sources, groups, numTimepoints)
        for (i in Math.min(numGroups, sources.size) - 1 downTo 0)
            state.sourceGroups[i].addSource(i)

        visibilityAndGrouping = VisibilityAndGrouping(state)
        for (i in 0 until sources.size) {
            visibilityAndGrouping.sources[i].isActive = true
        }

        state.displayMode = DisplayMode.FUSED

        converterSetups.forEach {
            it.color = ARGBType(Random.nextInt(0, 255*255*255))
        }

        volumeManager.add(this)
    }

    fun getStack(timepoint: Int, setupId: Int, volatile: Boolean): MultiResolutionStack3D<*> {
        return stacks.getStack(timepoint, setupId, volatile)
    }

    /**
     * Goes to the next available timepoint, returning the number of the updated timepoint.
     */
    fun nextTimepoint(): Int {
        return goToTimePoint(state.currentTimepoint + 1)
    }

    /** Goes to the previous available timepoint, returning the number of the updated timepoint. */
    fun previousTimepoint(): Int {
        return goToTimePoint(state.currentTimepoint - 1)
    }

    /** Goes to the [timepoint] given, returning the number of the updated timepoint. */
    fun goToTimePoint(timepoint: Int): Int {
        val current = state.currentTimepoint
        state.currentTimepoint = min(max(timepoint, 0), maxTimepoint)
        logger.info("Going to timepoint ${state.currentTimepoint} of $maxTimepoint")

        if(current != state.currentTimepoint) {
            volumeManager.notifyUpdate(this)
        }

        return state.currentTimepoint
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
