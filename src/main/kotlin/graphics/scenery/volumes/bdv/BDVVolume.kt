package graphics.scenery.volumes.bdv

import bdv.BigDataViewer.initSetups
import bdv.spimdata.SpimDataMinimal
import bdv.spimdata.XmlIoSpimDataMinimal
import bdv.tools.brightness.ConverterSetup
import bdv.tools.brightness.SetupAssignments
import bdv.viewer.DisplayMode
import bdv.viewer.SourceAndConverter
import bdv.viewer.VisibilityAndGrouping
import bdv.viewer.state.SourceGroup
import bdv.viewer.state.ViewerState
import coremem.enums.NativeTypeEnum
import graphics.scenery.*
import graphics.scenery.volumes.Volume
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.volatiles.VolatileUnsignedShortType
import org.joml.Matrix4f
import tpietzsch.backend.Texture
import tpietzsch.cache.*
import tpietzsch.example2.*
import tpietzsch.multires.*
import java.util.*
import java.util.concurrent.ForkJoinPool
import kotlin.math.max
import kotlin.math.min

/**
 * BigDataViewer-backed out-of-core volume rendering [Node] for scenery.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Tobias Pietzsch <pietzsch@mpi-cbg.de>
 */
@Suppress("unused")
open class BDVVolume(bdvXMLFile: String = "", val options: VolumeViewerOptions) : Volume() {
    /**
     *  The rendering method used in the shader, can be
     *
     *  0 -- Local Maximum Intensity Projection
     *  1 -- Maximum Intensity Projection
     *  2 -- Alpha compositing
     */

    /** BDV shader context for this volume */
    var context = SceneryContext(this)
        protected set
    var maxTimepoint: Int = 0
        protected set
    /** Texture cache. */
    protected var textureCache: TextureCache
    /** PBO chain for temporary data storage. */
    protected var pboChain: PboChain
    /** Set of [VolumeBlocks]. */
    protected var outOfCoreVolumes = ArrayList<VolumeBlocks>()
    /** Stacks loaded from a BigDataViewer XML file. */
    protected var stacks: SpimDataStacks
    protected var setupAssignments: SetupAssignments
    val converterSetups = ArrayList<ConverterSetup>()
    val renderConverters = ArrayList<ConverterSetup>()
    /** Cache specification. */
    private val cacheSpec = CacheSpec(Texture.InternalFormat.R16, intArrayOf(32, 32, 32))

    /** Current timepoint in the set of [stacks]. */
    var currentTimepoint: Int
        get() { return state.currentTimepoint }
        set(value) {state.currentTimepoint = value}

    protected var state: ViewerState
    protected var visibilityAndGrouping: VisibilityAndGrouping

    private val renderStacks = ArrayList<Stack3D<*>>()
    private val simpleRenderStacks = ArrayList<SimpleStack3D<VolatileUnsignedShortType>>()

    private val stackManager: SimpleStackManager = DefaultSimpleStackManager()

    private val multiResolutionStacks = ArrayList(
        Arrays.asList<MultiResolutionStack3D<VolatileUnsignedShortType>>(null, null, null))
    /** Whether to freeze the current set of blocks in-place. */
    var freezeRequiredBlocks = false

    /** Backing shader program */
    protected var prog = ArrayList<SceneryMultiVolumeShaderMip>()
    protected var progvol: SceneryMultiVolumeShaderMip? = null
    protected var renderStateUpdated = false
    protected var cacheSizeUpdated = false

    init {
        // fake geometry
        this.vertices = BufferUtils.allocateFloatAndPut(
            floatArrayOf(
                -1.0f, -1.0f, 0.0f,
                1.0f, -1.0f, 0.0f,
                1.0f, 1.0f, 0.0f,
                -1.0f, 1.0f, 0.0f))

        this.normals = BufferUtils.allocateFloatAndPut(
            floatArrayOf(
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f))

        this.texcoords = BufferUtils.allocateFloatAndPut(
            floatArrayOf(
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f))

        this.indices = BufferUtils.allocateIntAndPut(
            intArrayOf(0, 1, 2, 0, 2, 3))

        this.geometryType = GeometryType.TRIANGLES
        this.vertexSize = 3
        this.texcoordSize = 2

        material = ShaderMaterial(context.factory)

        material.cullingMode = Material.CullingMode.None
        material.blending.transparent = true
        material.blending.sourceColorBlendFactor = Blending.BlendFactor.One
        material.blending.destinationColorBlendFactor = Blending.BlendFactor.OneMinusSrcAlpha
        material.blending.sourceAlphaBlendFactor = Blending.BlendFactor.One
        material.blending.destinationAlphaBlendFactor = Blending.BlendFactor.OneMinusSrcAlpha
        material.blending.colorBlending = Blending.BlendOp.add
        material.blending.alphaBlending = Blending.BlendOp.add

        colormaps["grays"] = Colormap.ColormapFile(Volume::class.java.getResource("colormap-grays.png").file)
        colormaps["hot"] = Colormap.ColormapFile(Volume::class.java.getResource("colormap-hot.png").file)
        colormaps["jet"] = Colormap.ColormapFile(Volume::class.java.getResource("colormap-jet.png").file)
        colormaps["plasma"] = Colormap.ColormapFile(Volume::class.java.getResource("colormap-plasma.png").file)
        colormaps["viridis"] = Colormap.ColormapFile(Volume::class.java.getResource("colormap-viridis.png").file)

        if(bdvXMLFile == "") throw IllegalStateException("No file given, sorry")

        val spimData: SpimDataMinimal = XmlIoSpimDataMinimal().load(bdvXMLFile)
        stacks = SpimDataStacks(spimData)

        val sources = ArrayList<SourceAndConverter<*>>()

        initSetups(spimData, converterSetups, sources)

        setupAssignments = SetupAssignments(converterSetups, 0.0, 65535.0)
        if(setupAssignments.minMaxGroups.size > 0) {
            val group = setupAssignments.minMaxGroups[0]
            setupAssignments.converterSetups.forEach {
                setupAssignments.moveSetupToGroup(it, group)
            }
        }
        maxTimepoint = spimData.sequenceDescription.timePoints.timePointsOrdered.size - 1

        val cacheGridDimensions = TextureCache.findSuitableGridSize(cacheSpec, options.values.maxCacheSizeInMB)
        textureCache = TextureCache(cacheGridDimensions, cacheSpec)

        pboChain = PboChain(5, 100, textureCache)

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

        logger.info("sources=${sources.size} timepoints=${numTimepoints}, groups=${numGroups}, converterSetups=${converterSetups.size}")

        if (!sources.isEmpty()) {
            state.currentSource = 0
        }

        updateRenderState()
        needAtLeastNumVolumes(renderStacks.size)
        logger.info("renderStacks.size=${renderStacks.size}")

        logger.info("Progs: ${prog.size}")
        // TODO: this might result in NULL program, is this intended?
        progvol = prog.last()//get(renderStacks.size)
        progvol?.setTextureCache(textureCache)
        progvol?.init(context)

//        updateBlocks(context)
        preDraw()

        converterSetups.forEach {
            it.color = ARGBType(kotlin.random.Random.nextInt(0, 255*255*255))
        }
//        convs[0].color = ARGBType(0xff8888)
//        convs[1].color = ARGBType(0x88ff88)
//        convs[2].color = ARGBType(0x8888ff)

//        convs.forEach {
//            it.min = 962.0
//            it.max = 6201.0
//        }
    }

    private fun needAtLeastNumVolumes(n: Int) {
        val outOfCoreVolumeCount = renderStacks.count { it is MultiResolutionStack3D }
        val regularVolumeCount = renderStacks.count { it is SimpleStack3D }

        while (outOfCoreVolumes.size < n) {
            outOfCoreVolumes.add(VolumeBlocks(textureCache))
        }


        val progvol = SceneryMultiVolumeShaderMip(outOfCoreVolumeCount, regularVolumeCount, true, 1.0)
        progvol.setTextureCache(textureCache)
        progvol.setDepthTextureName("InputZBuffer")
        logger.info("Using program for $outOfCoreVolumeCount out-of-core volumes and $regularVolumeCount regular volumes")
        prog.add(progvol)
    }

    fun resizeCache(newSize: Int) {
        logger.warn("Resizing cache is not stable yet, here be dragons!")
        options.maxCacheSizeInMB(newSize)
        cacheSizeUpdated = true
        renderStateUpdated = true
    }

    private fun resizeCacheInternal() {
        val cacheGridDimensions = TextureCache.findSuitableGridSize(cacheSpec, options.values.maxCacheSizeInMB)
        textureCache = TextureCache(cacheGridDimensions, cacheSpec)
        pboChain = PboChain(5, 100, textureCache)

        context.clearBindings()
//        progvol = prog.last()
//        progvol?.setTextureCache(textureCache)
//        progvol?.init(context)
//        progvol?.use(context)

        progvol?.bindSamplers(context)
    }

    /**
     * Updates the currently-used set of blocks using [context] to
     * facilitate the updates on the GPU.
     */
    protected fun updateBlocks(context: SceneryContext) {
        if(cacheSizeUpdated) {
            resizeCacheInternal()
            cacheSizeUpdated = false
        }

        logger.debug("Updating blocks")
        stacks.cacheControl.prepareNextFrame()

        val cam = getScene()?.activeObserver ?: return
        val viewProjection = cam.projection.clone()
        viewProjection.mult(cam.getTransformation())
        val mvp = Matrix4f().set(viewProjection.floatArray)

        // TODO: original might result in NULL, is this intended?
        val progvol = prog.last()//get(renderStacks.size)
        progvol.use(context)

        var numTasks = 0
        val fillTasksPerVolume = ArrayList<VolumeAndTasks>()
        for(i in 0 until renderStacks.size) {
            val stack = renderStacks[i]
            if(stack !is MultiResolutionStack3D) continue
            val volume = outOfCoreVolumes[i]

            volume.init(stack, cam.width.toInt(), mvp)

            val tasks = volume.fillTasks
            numTasks += tasks.size
            fillTasksPerVolume.add(VolumeAndTasks(tasks, volume, stack.resolutions().size - 1))
        }

        while(numTasks > textureCache.maxNumTiles) {
           fillTasksPerVolume.sortedBy { it.numTasks() }
               .reversed()
               .forEach {
               val baseLevel = it.volume.baseLevel
               if(baseLevel < it.maxLevel) {
                   numTasks -= it.numTasks()
                   it.tasks.clear()
                   it.tasks.addAll(it.volume.fillTasks)

                   // TODO: Ask Tobi -- potentially solved
                   return@forEach
               }
           }
           break
        }

        val fillTasks = ArrayList<FillTask>()
        fillTasksPerVolume.forEach {
            fillTasks.addAll(it.tasks)
        }

        if(fillTasks.size > textureCache.maxNumTiles) {
            fillTasks.subList(textureCache.maxNumTiles, fillTasks.size).clear()
        }

        ProcessFillTasks.parallel(textureCache, pboChain, context, forkJoinPool, fillTasks)

        var repaint = false
        for(i in 0 until renderStacks.size) {
            if(renderStacks[i] is MultiResolutionStack3D) {
                val volumeBlocks = outOfCoreVolumes[i]
                val complete = volumeBlocks.makeLut()
                if (!complete) {
                    repaint = true
                }
                context.bindTexture(volumeBlocks.lookupTexture)
                volumeBlocks.lookupTexture.upload(context)
            }
        }

        var minWorldVoxelSize = Double.POSITIVE_INFINITY

        renderStacks
            // sort by classname, so we get MultiResolutionStack3Ds first,
            // then simple stacks
            .sortedBy { it.javaClass.simpleName }
            .forEachIndexed { i, stack ->
                if(stack is MultiResolutionStack3D) {
                    progvol.setConverter(i, renderConverters.get(i))
                    progvol.setVolume(i, outOfCoreVolumes.get(i))
                    minWorldVoxelSize = min(minWorldVoxelSize, outOfCoreVolumes.get(i).baseLevelVoxelSizeInWorldCoordinates)
                }

                if(stack is SimpleStack3D) {
                    val volume = stackManager.getSimpleVolume( context, stack )
                    progvol.setConverter(i, renderConverters.get(i))
                    progvol.setVolume(i, volume)
                    minWorldVoxelSize = min(minWorldVoxelSize, volume.voxelSizeInWorldCoordinates)
                }
            }

        progvol.setViewportWidth(cam.width.toInt())
        progvol.setEffectiveViewportSize(cam.width.toInt(), cam.height.toInt())
        progvol.setProjectionViewMatrix(mvp, minWorldVoxelSize)
        progvol.use(context)
        progvol.bindSamplers(context)
        logger.debug("Done updating blocks")
    }

    internal class VolumeAndTasks(tasks: List<FillTask>, val volume: VolumeBlocks, val maxLevel: Int) {
        val tasks: ArrayList<FillTask> = ArrayList(tasks)

        fun numTasks(): Int {
            return tasks.size
        }

    }

    /**
     * Pre-draw routine to be called by the rendered just before drawing.
     * Updates texture cache and used blocks.
     */
    override fun preDraw() {
        context.bindTexture(textureCache)

        if(renderStateUpdated) {
            updateRenderState()
            needAtLeastNumVolumes(renderStacks.size)
            renderStateUpdated = false
        }

        if(freezeRequiredBlocks == false) {
            try {
                updateBlocks(context)
            } catch (e: RuntimeException) {
                logger.warn("Probably ran out of data, corrupt BDV file? $e")
                e.printStackTrace()
            }
        }

        context.runDeferredBindings()
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
        state.currentTimepoint = min(max(timepoint, 0), maxTimepoint)
        logger.info("Going to timepoint ${state.currentTimepoint} of $maxTimepoint")

        renderStateUpdated = true

        return state.currentTimepoint
    }

    /**
     * Updates the current stack given a set of [stacks] to [currentTimepoint].
     */
    protected fun updateRenderState() {
        val visibleSourceIndices: List<Int>
        val currentTimepoint: Int

        synchronized(state) {
            visibleSourceIndices = state.visibleSourceIndices
            currentTimepoint = state.currentTimepoint

            logger.info("Visible: at t=$currentTimepoint: ${visibleSourceIndices.joinToString(", ")}")
            renderStacks.clear()
            renderConverters.clear()
            for (i in visibleSourceIndices) {
                val stack = stacks.getStack(
                    stacks.timepointId(currentTimepoint),
                    stacks.setupId(i),
                    true) as MultiResolutionStack3D<VolatileUnsignedShortType>
                val sourceTransform = AffineTransform3D()
                state.sources[i].spimSource.getSourceTransform(currentTimepoint, 0, sourceTransform)
                val wrappedStack = object : MultiResolutionStack3D<VolatileUnsignedShortType> {
                    override fun getType(): VolatileUnsignedShortType {
                        return stack.type
                    }

                    override fun getSourceTransform(): AffineTransform3D {
                        val w = AffineTransform3D()
                        w.set(*world.transposedFloatArray.map { it.toDouble() }.toDoubleArray())
                        return w.concatenate(sourceTransform)
                    }

                    override fun resolutions(): List<ResolutionLevel3D<VolatileUnsignedShortType>> {
                        return stack.resolutions()
                    }
                }
                renderStacks.add(wrappedStack)
                val converter = converterSetups[i]
                renderConverters.add(converter)
            }

            if(volumes.size > 0) {
                val vol = volumes.entries.first().value
                if(vol.dataType == NativeTypeEnum.UnsignedByte) {
                    val simpleStack = object : BufferedSimpleStack3D(vol.data,
                        UnsignedByteType(),
                        intArrayOf(vol.width.toInt(), vol.height.toInt(), vol.depth.toInt())) {

                        override fun getSourceTransform(): AffineTransform3D {
                            val w = AffineTransform3D()
                            w.set(*world.transposedFloatArray.map { it.toDouble() }.toDoubleArray())
                            return w
                        }
                    }
                    logger.info("Added SimpleStack: $simpleStack")
                    renderStacks.add(simpleStack)
                    renderConverters.add(converterSetups.first())
                }
            }
        }
    }

    /** Companion object for BDVVolume */
    companion object {
        /** Static [ForkJoinPool] for fill task submission. */
        protected val forkJoinPool: ForkJoinPool = ForkJoinPool(max(1, Runtime.getRuntime().availableProcessors()/2))
    }
}
