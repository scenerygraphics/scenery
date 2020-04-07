package graphics.scenery.volumes

import bdv.tools.brightness.ConverterSetup
import bdv.tools.transformation.TransformedSource
import bdv.viewer.RequestRepaint
import bdv.viewer.state.SourceState
import graphics.scenery.*
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.type.volatiles.VolatileARGBType
import net.imglib2.type.volatiles.VolatileUnsignedByteType
import net.imglib2.type.volatiles.VolatileUnsignedShortType
import org.joml.Matrix4f
import tpietzsch.backend.GpuContext
import tpietzsch.backend.Texture
import tpietzsch.backend.Texture3D
import tpietzsch.cache.*
import tpietzsch.example2.MultiVolumeShaderMip
import tpietzsch.example2.VolumeBlocks
import tpietzsch.example2.VolumeShaderSignature
import tpietzsch.multires.MultiResolutionStack3D
import tpietzsch.multires.SimpleStack3D
import tpietzsch.multires.SourceStacks
import tpietzsch.multires.Stack3D
import tpietzsch.shadergen.generate.Segment
import tpietzsch.shadergen.generate.SegmentTemplate
import tpietzsch.shadergen.generate.SegmentType
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*
import java.util.concurrent.ForkJoinPool
import java.util.function.BiConsumer
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class VolumeManager(override var hub : Hub?) : Node(), Hubable, HasGeometry, RequestRepaint {
    /** How many elements does a vertex store? */
    override val vertexSize : Int = 3
    /** How many elements does a texture coordinate store? */
    override val texcoordSize : Int = 2
    /** The [GeometryType] of the [Node] */
    override var geometryType : GeometryType = GeometryType.TRIANGLES
    /** Array of the vertices. This buffer is _required_, but may empty. */
    override var vertices : FloatBuffer = BufferUtils.allocateFloatAndPut(
        floatArrayOf(
            -1.0f, -1.0f, 0.0f,
            1.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f,
            -1.0f, 1.0f, 0.0f))
    /** Array of the normals. This buffer is _required_, and may _only_ be empty if [vertices] is empty as well. */
    override var normals : FloatBuffer = BufferUtils.allocateFloatAndPut(
        floatArrayOf(
            1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f))
    /** Array of the texture coordinates. Texture coordinates are optional. */
    override var texcoords : FloatBuffer = BufferUtils.allocateFloatAndPut(
        floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f))
    /** Array of the indices to create an indexed mesh. Optional, but advisable to use to minimize the number of submitted vertices. */
    override var indices : IntBuffer = BufferUtils.allocateIntAndPut(
        intArrayOf(0, 1, 2, 0, 2, 3))
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

    /** Flexible [ShaderProperty] storage */
    @ShaderProperty
    var shaderProperties = hashMapOf<String, Any>()

    /** Set of [VolumeBlocks]. */
    protected var outOfCoreVolumes = ArrayList<VolumeBlocks>()
    protected var bdvNodes = ArrayList<Volume>()
    protected var transferFunctionTextures = HashMap<SourceState<*>, Texture>()
    protected var colorMapTextures = HashMap<SourceState<*>, Texture>()
    /** Stacks loaded from a BigDataViewer XML file. */
    val renderConverters = ArrayList<ConverterSetup>()
    /** Cache specification. */
    private val cacheSpec = CacheSpec(Texture.InternalFormat.R16, intArrayOf(32, 32, 32))

    private val renderStacks = ArrayList<Triple<Stack3D<*>, Texture, Texture>>()
    private val simpleRenderStacks = ArrayList<SimpleStack3D<VolatileUnsignedShortType>>()

    private val stackManager = SceneryStackManager()

    /** Whether to freeze the current set of blocks in-place. */
    var freezeRequiredBlocks = false

    /** Backing shader program */
    protected var prog = ArrayList<MultiVolumeShaderMip>()
    protected var progvol: MultiVolumeShaderMip? = null
    protected var renderStateUpdated = true
    protected var cacheSizeUpdated = false

    protected var currentVolumeCount: Pair<Int, Int>

    /** Sets the maximum allowed step size in voxels. */
    var maxAllowedStepInVoxels = 1.0
    /** Numeric factor by which the step size may degrade on the far plane. */
    var farPlaneDegradation = 5.0

    // TODO: What happens when changing this? And should it change the mode for the current node only
    // or for all VolumeManager-managed nodes?
    var renderingMethod = Volume.RenderingMethod.AlphaBlending

    init {
        state = State.Created
        name = "VolumeManager"
        // fake geometry

        this.geometryType = GeometryType.TRIANGLES

        currentVolumeCount = 0 to 0

        val maxCacheSize = (hub?.get(SceneryElement.Settings) as? Settings)?.get("Renderer.MaxVolumeCacheSize", 512) ?: 512

        val cacheGridDimensions = TextureCache.findSuitableGridSize(cacheSpec, maxCacheSize)
        textureCache = TextureCache(cacheGridDimensions, cacheSpec)

        pboChain = PboChain(5, 100, textureCache)

        updateRenderState()
        needAtLeastNumVolumes(renderStacks.size)
        logger.info("renderStacks.size=${renderStacks.size}")

        logger.info("Progs: ${prog.size}")
        // TODO: this might result in NULL program, is this intended?
        progvol = prog.lastOrNull()

        if(progvol != null) {
            updateProgram()
        }

        material = ShaderMaterial(context.factory)
        material.cullingMode = Material.CullingMode.None
        material.blending.transparent = true
        material.blending.sourceColorBlendFactor = Blending.BlendFactor.One
        material.blending.destinationColorBlendFactor = Blending.BlendFactor.OneMinusSrcAlpha
        material.blending.sourceAlphaBlendFactor = Blending.BlendFactor.One
        material.blending.destinationAlphaBlendFactor = Blending.BlendFactor.OneMinusSrcAlpha
        material.blending.colorBlending = Blending.BlendOp.add
        material.blending.alphaBlending = Blending.BlendOp.add

        preDraw()
    }

    private fun updateProgram() {
        logger.info("Updating effective shader program to $progvol")
        progvol?.setTextureCache(textureCache)
        progvol?.use(context)
        progvol?.setUniforms(context)

        getScene()?.activeObserver?.let { cam ->
            progvol?.setViewportWidth(cam.width)
            progvol?.setEffectiveViewportSize(cam.width, cam.height)
        }
    }

    private fun needAtLeastNumVolumes(n: Int) {
        val outOfCoreVolumeCount = renderStacks.count { it.first is MultiResolutionStack3D }
        val regularVolumeCount = renderStacks.count { it.first is SimpleStack3D }
        logger.debug("$currentVolumeCount -> ooc:$outOfCoreVolumeCount reg:$regularVolumeCount")

        if(currentVolumeCount.first == outOfCoreVolumeCount && currentVolumeCount.second == regularVolumeCount) {
            logger.debug("Not updating shader, current one compatible with ooc:$outOfCoreVolumeCount reg:$regularVolumeCount")
            return
        }

        while (outOfCoreVolumes.size < n) {
            outOfCoreVolumes.add(VolumeBlocks(textureCache))
        }

        val signatures = renderStacks.map {
            val dataType = when(it.first.type) {
                is VolatileUnsignedByteType,
                is UnsignedByteType -> VolumeShaderSignature.PixelType.UBYTE

                is VolatileUnsignedShortType,
                is UnsignedShortType -> VolumeShaderSignature.PixelType.USHORT

                is VolatileARGBType,
                is ARGBType -> VolumeShaderSignature.PixelType.ARGB
                else -> throw IllegalStateException("Unknown volume type ${it.first.type.javaClass}")
            }

            val volumeType = when(it.first) {
                is SimpleStack3D -> SourceStacks.SourceStackType.SIMPLE
                is MultiResolutionStack3D -> SourceStacks.SourceStackType.MULTIRESOLUTION
                else -> SourceStacks.SourceStackType.UNDEFINED
            }

            VolumeShaderSignature.VolumeSignature(volumeType, dataType)
        }

        val segments = MultiVolumeShaderMip.getDefaultSegments(true)
        segments[SegmentType.VertexShader] = SegmentTemplate(
            this.javaClass,
            "BDVVolume.vert")
        segments[SegmentType.FragmentShader] = SegmentTemplate(
            this.javaClass,
            "BDVVolume.frag",
            "intersectBoundingBox", "vis", "SampleVolume", "Convert", "Accumulate")
        segments[SegmentType.MaxDepth] = SegmentTemplate(
            this.javaClass,
            "MaxDepth.frag")
        segments[SegmentType.SampleMultiresolutionVolume] = SegmentTemplate(
            "SampleBlockVolume.frag",
            "im", "sourcemin", "sourcemax", "intersectBoundingBox",
            "lutSampler", "transferFunction", "colorMap", "blockScales", "lutSize", "lutOffset", "sampleVolume", "convert")
        segments[SegmentType.SampleVolume] = SegmentTemplate(
            "SampleSimpleVolume.frag",
            "im", "sourcemax", "intersectBoundingBox",
            "volume", "transferFunction", "colorMap", "sampleVolume", "convert")
        segments[SegmentType.Convert] = SegmentTemplate(
            "Converter.frag",
            "convert", "offset", "scale")
        segments[SegmentType.AccumulatorMultiresolution] = SegmentTemplate(
            "AccumulateBlockVolume.frag",
            "vis", "sampleVolume", "convert")
        segments[SegmentType.Accumulator] = SegmentTemplate(
            "AccumulateSimpleVolume.frag",
            "vis", "sampleVolume", "convert")

        val additionalBindings = BiConsumer { _: Map<SegmentType, SegmentTemplate>, instances: Map<SegmentType, Segment> ->
            logger.debug("Connecting additional bindings")
            instances.get(SegmentType.SampleMultiresolutionVolume)?.bind("convert", instances.get(SegmentType.Convert))
            instances.get(SegmentType.SampleVolume)?.bind("convert", instances.get(SegmentType.Convert))
        }

        val newProgvol = MultiVolumeShaderMip(VolumeShaderSignature(signatures),
            true, farPlaneDegradation,
            segments,
            additionalBindings,
            "InputZBuffer")

        newProgvol.setTextureCache(textureCache)
        newProgvol.setDepthTextureName("InputZBuffer")
        logger.info("Using program for $outOfCoreVolumeCount out-of-core volumes and $regularVolumeCount regular volumes")
        prog.add(newProgvol)

        val oldKeys = this.material.textures.keys()
            .asSequence()
            .filter { it.endsWith("_") }
        oldKeys.map {
            this.material.textures.remove(it)
        }

        currentVolumeCount = outOfCoreVolumeCount to regularVolumeCount

        if(prog.size > 0) {
            logger.info("We have ${prog.size} shaders ready")
            progvol = prog.last()

            updateProgram()
            state = State.Ready
        } else {
            state = State.Created
        }
    }

    /**
     * Updates the currently-used set of blocks using [context] to
     * facilitate the updates on the GPU.
     */
    @UseExperimental(ExperimentalTime::class)
    protected fun updateBlocks(context: SceneryContext) {
        val currentProg = progvol
        if(currentProg == null) {
            logger.info("Not updating blocks, no prog")
            return
        }

        bdvNodes.forEach { bdvNode ->
            bdvNode.prepareNextFrame()
        }

        val cam = bdvNodes.firstOrNull()?.getScene()?.activeObserver ?: return
        val mvp = Matrix4f(cam.projection).mul(cam.getTransformation())

        // TODO: original might result in NULL, is this intended?
        currentProg.use(context)
        currentProg.setUniforms(context)

        var numTasks = 0
        val fillTasksPerVolume = ArrayList<VolumeAndTasks>()

        val taskCreationDuration = measureTimeMillis {
            for (i in 0 until renderStacks.size) {
                val stack = renderStacks[i].first
                if (stack !is MultiResolutionStack3D) continue
                val volume = outOfCoreVolumes[i]

                volume.init(stack, cam.width, mvp)

                val tasks = volume.fillTasks
                numTasks += tasks.size
                fillTasksPerVolume.add(VolumeAndTasks(tasks, volume, stack.resolutions().size - 1))
            }
        }

        val fillTasksDuration = measureTimeMillis {
            while (numTasks > textureCache.maxNumTiles) {
                fillTasksPerVolume.sortedByDescending { it.numTasks() }
                    .forEach {
                        val baseLevel = it.volume.baseLevel
                        if (baseLevel < it.maxLevel) {
                            numTasks -= it.numTasks()
                            it.tasks.clear()
                            it.tasks.addAll(it.volume.fillTasks)

                            // TODO: Ask Tobi -- potentially solved
                            return@forEach
                        }
                    }
                break
            }
        }

//        logger.info("Task creation took $taskCreationDuration ms")
//        logger.info("Fill Task creation took $fillTasksDuration ms")

        val duration = measureTime {
            val fillTasks = ArrayList<FillTask>()
            fillTasksPerVolume.forEach {
                fillTasks.addAll(it.tasks)
            }
            logger.debug("Got ${fillTasks.size} fill tasks (vs max=${textureCache.maxNumTiles})")

            if (fillTasks.size > textureCache.maxNumTiles) {
                fillTasks.subList(textureCache.maxNumTiles, fillTasks.size).clear()
            }

            ProcessFillTasks.parallel(textureCache, pboChain, context, forkJoinPool, fillTasks)
        }

//        logger.info("Processing fill tasks took ${duration.inMilliseconds}")

        val durationLutUpdate = measureTimeMillis {
            var repaint = false
            for (i in 0 until renderStacks.size) {
                val stack = renderStacks[i]
                if (stack.first is MultiResolutionStack3D) {
                    val volumeBlocks = outOfCoreVolumes[i]
                    val timestamp = textureCache.nextTimestamp()
                    val complete = volumeBlocks.makeLut(timestamp)
                    if (!complete) {
                        repaint = true
                    }
                    context.bindTexture(volumeBlocks.lookupTexture)
                    volumeBlocks.lookupTexture.upload(context)
                }
            }
        }

//        logger.info("Updating LUTs took ${durationLutUpdate} ms")

        var minWorldVoxelSize = Double.POSITIVE_INFINITY
        val ready = readyToRender()

        val durationBinding = measureTimeMillis {
            renderStacks
                // sort by classname, so we get MultiResolutionStack3Ds first,
                // then simple stacks
                .sortedBy { it.javaClass.simpleName }
                .forEachIndexed { i, stack ->
                    val s = stack.first
                    if (s is MultiResolutionStack3D) {
                        currentProg.setConverter(i, renderConverters.get(i))
                        currentProg.setCustomSampler(i, "transferFunction", stack.second)
                        currentProg.setCustomSampler(i, "colorMap", stack.third)
                        context.bindTexture(stack.second)
                        context.bindTexture(stack.third)

                        currentProg.setVolume(i, outOfCoreVolumes.get(i))
                        minWorldVoxelSize = min(minWorldVoxelSize, outOfCoreVolumes.get(i).baseLevelVoxelSizeInWorldCoordinates)
                    }

                    if (s is SimpleStack3D) {
                        val volume = stackManager.getSimpleVolume(context, s)
                        currentProg.setConverter(i, renderConverters.get(i))
                        currentProg.setCustomSampler(i, "transferFunction", stack.second)
                        currentProg.setCustomSampler(i, "colorMap", stack.third)
                        context.bindTexture(stack.second)
                        context.bindTexture(stack.third)

                        currentProg.setVolume(i, volume)
                        context.bindTexture(volume.volumeTexture)
                        if (ready) {
                            stackManager.upload(context, s)
                        }
                        minWorldVoxelSize = min(minWorldVoxelSize, volume.voxelSizeInWorldCoordinates)
                    }
                }

            currentProg.setViewportWidth(cam.width)
            currentProg.setEffectiveViewportSize(cam.width, cam.height)
            currentProg.setDegrade(farPlaneDegradation)
            currentProg.setProjectionViewMatrix(mvp, maxAllowedStepInVoxels * minWorldVoxelSize)
            currentProg.use(context)
            currentProg.setUniforms(context)
            currentProg.bindSamplers(context)
        }
//        logger.info("Binding took $durationBinding ms")
//        logger.info("Done updating blocks with minVoxelSize=$minWorldVoxelSize")
    }

    class SimpleTexture2D(
        val data: ByteBuffer,
        val width: Int,
        val height: Int,
        val format: Texture.InternalFormat,
        val wrap: Texture.Wrap,
        val minFilter: Texture.MinFilter,
        val magFilter: Texture.MagFilter
    ) : Texture3D {
        override fun texMinFilter(): Texture.MinFilter = minFilter
        override fun texMagFilter(): Texture.MagFilter = magFilter
        override fun texWrap(): Texture.Wrap = wrap
        override fun texWidth(): Int = width
        override fun texHeight(): Int = height
        override fun texDepth(): Int = 1
        override fun texInternalFormat(): Texture.InternalFormat = format

        fun upload(context: GpuContext) {
            context.delete(this)
            context.texSubImage3D(this, 0, 0, 0, texWidth(), texHeight(), texDepth(), data)
        }
    }

    private fun TransferFunction.toTexture(): Texture3D {
        val data = this.serialise()
        return SimpleTexture2D(data, textureSize, textureHeight,
            Texture.InternalFormat.FLOAT32, Texture.Wrap.CLAMP_TO_EDGE,
            Texture.MinFilter.LINEAR, Texture.MagFilter.LINEAR)
    }

    private fun Colormap.toTexture(): Texture3D {
        return SimpleTexture2D(buffer, width, height,
            Texture.InternalFormat.RGBA8, Texture.Wrap.CLAMP_TO_EDGE,
            Texture.MinFilter.LINEAR, Texture.MagFilter.LINEAR)
    }

    fun readyToRender(): Boolean {
        val multiResCount = renderStacks.count { it.first is MultiResolutionStack3D }
        val regularCount = renderStacks.count { it.first is SimpleStack3D }

        val multiResMatch = material.textures.count { it.key.startsWith("volumeCache") } == 1
            && material.textures.count { it.key.startsWith("lutSampler_") }  == multiResCount
        val regularMatch = material.textures.count { it.key.startsWith("volume_") } == regularCount

//        logger.info("ReadyToRender: $multiResCount->$multiResMatch/$regularCount->$regularMatch (${shaderProperties.keys.joinToString(",")})")
//        if(multiResMatch && regularMatch) {
//            state = State.Ready
//        } else {
//            state = State.
//        }
        return multiResMatch && regularMatch
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
    override fun preDraw(): Boolean {
        context.bindTexture(textureCache)

        if(bdvNodes.any { it.transferFunction.stale }) {
            transferFunctionTextures.clear()
            val keys = material.textures.filter { it.key.startsWith("transferFunction") }.keys
            keys.forEach { material.textures.remove(it) }
            renderStateUpdated = true
        }

        if(renderStateUpdated) {
            updateRenderState()
            needAtLeastNumVolumes(renderStacks.size)
            renderStateUpdated = false
        }

        val blockUpdateDuration = measureTimeMillis {
            if (freezeRequiredBlocks == false) {
                try {
                    updateBlocks(context)
                } catch (e: RuntimeException) {
                    logger.warn("Probably ran out of data, corrupt BDV file? $e")
                    e.printStackTrace()
                }
            }
        }

//        logger.info("Block updates took $blockUpdateDuration ms")

        context.runDeferredBindings()
        context.runTextureUpdates()

        return readyToRender()
    }

    /**
     * Updates the current stack given a set of [stacks] to [currentTimepoint].
     */
    protected fun updateRenderState() {
        // check if synchronized block is necessary here
//        logger.info("Updating state for ${bdvNodes.size} BDV nodes")
        renderStacks.clear()
        renderConverters.clear()

        bdvNodes.forEach { bdvNode ->
            val visibleSourceIndices = bdvNode.viewerState.visibleSourceIndices
            val currentTimepoint = bdvNode.viewerState.currentTimepoint

            logger.debug("Visible: at t=$currentTimepoint: ${visibleSourceIndices.joinToString(", ")}")
            for (i in visibleSourceIndices) {
//                val stack = stacks.getStack(
//                    stacks.timepointId(currentTimepoint),
//                    stacks.setupId(i),
//                    true) as MultiResolutionStack3D<VolatileUnsignedShortType>

                val source = bdvNode.viewerState.sources[i]
                val stack = SourceStacks.getStack3D(source.spimSource, currentTimepoint)// as MultiResolutionStack3D<*>

                val sourceTransform = AffineTransform3D()
                source.spimSource.getSourceTransform(currentTimepoint, 0, sourceTransform)

                if(stack is MultiResolutionStack3D) {
                    val o = TransformedMultiResolutionStack3D(stack, bdvNode, sourceTransform)
                    val tf = transferFunctionTextures.getOrPut(bdvNode.viewerState.sources[i], { bdvNode.transferFunction.toTexture() })
                    val colormap = colorMapTextures.getOrPut(bdvNode.viewerState.sources[i], { bdvNode.colormap.toTexture() })
                    renderStacks.add(Triple(o, tf, colormap))
                } else if(stack is SimpleStack3D) {
                    val o: SimpleStack3D<*>
                    val ss = source.spimSource as? TransformedSource
                    val wrapped = ss?.wrappedSource
                    o = if(wrapped is Volume.BufferDummySource) {
                        if(wrapped.timepoints.isEmpty()) {
                            logger.info("Timepoints is empty, skipping node")
                            return@forEach
                        }

                        val tp = min(max(0, currentTimepoint), wrapped.timepoints.size-1)
                        TransformedBufferedSimpleStack3D(
                            stack,
                            wrapped.timepoints.toList()[tp].second,
                            intArrayOf(wrapped.width, wrapped.height, wrapped.depth),
                            bdvNode,
                            sourceTransform
                        )
                    } else {
                        TransformedSimpleStack3D(stack, bdvNode, sourceTransform)
                    }

                    val tf = transferFunctionTextures.getOrPut(bdvNode.viewerState.sources[i], { bdvNode.transferFunction.toTexture() })
                    val colormap = colorMapTextures.getOrPut(bdvNode.viewerState.sources[i], { bdvNode.colormap.toTexture() })
                    logger.debug("TF for ${bdvNode.viewerState.sources[i]} is $tf")
                    renderStacks.add(Triple(o, tf, colormap))
                }

                val converter = bdvNode.converterSetups[i]
                converter.setViewer(this)
                renderConverters.add(converter)
            }
        }
    }


    /**
     * Adds a new volume [node] to the [VolumeManager]. Will trigger an update of the rendering state,
     * and recreation of the shaders.
     */
    fun add(node: Volume) {
        logger.info("Adding $node to OOC nodes")
        bdvNodes.add(node)
        updateRenderState()
        needAtLeastNumVolumes(renderStacks.size)
    }

    /**
     * Notifies the [VolumeManager] of any updates coming from [node],
     * will trigger an update of the rendering state, and potentially creation of new shaders.
     */
    fun notifyUpdate(node: Node) {
        logger.debug("Received update from {}", node)
        renderStateUpdated = true
        updateRenderState()
        needAtLeastNumVolumes(renderStacks.size)
    }

    /**
     * Requests re-rendering.
     */
    override fun requestRepaint() {
        renderStateUpdated = true
    }

    /** Companion object for Volume */
    companion object {
        /** Static [ForkJoinPool] for fill task submission. */
        protected val forkJoinPool: ForkJoinPool = ForkJoinPool(max(1, Runtime.getRuntime().availableProcessors()/2))
    }
}
