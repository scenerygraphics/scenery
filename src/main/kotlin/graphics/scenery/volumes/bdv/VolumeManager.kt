package graphics.scenery.volumes.bdv

import bdv.tools.brightness.ConverterSetup
import bdv.viewer.RequestRepaint
import coremem.enums.NativeTypeEnum
import graphics.scenery.Blending
import graphics.scenery.BufferUtils
import graphics.scenery.GeometryType
import graphics.scenery.HasGeometry
import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.Material
import graphics.scenery.Node
import graphics.scenery.SceneryElement
import graphics.scenery.Settings
import graphics.scenery.ShaderMaterial
import graphics.scenery.ShaderProperty
import graphics.scenery.State
import graphics.scenery.volumes.Volume
import net.imglib2.RandomAccessibleInterval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.type.volatiles.VolatileARGBType
import net.imglib2.type.volatiles.VolatileUnsignedByteType
import net.imglib2.type.volatiles.VolatileUnsignedShortType
import org.joml.Matrix4f
import tpietzsch.backend.Texture
import tpietzsch.cache.CacheSpec
import tpietzsch.cache.FillTask
import tpietzsch.cache.PboChain
import tpietzsch.cache.ProcessFillTasks
import tpietzsch.cache.TextureCache
import tpietzsch.example2.MultiVolumeShaderMip
import tpietzsch.example2.VolumeBlocks
import tpietzsch.example2.VolumeShaderSignature
import tpietzsch.multires.MultiResolutionStack3D
import tpietzsch.multires.ResolutionLevel3D
import tpietzsch.multires.SimpleStack3D
import tpietzsch.multires.SourceStacks
import tpietzsch.multires.Stack3D
import tpietzsch.shadergen.generate.SegmentTemplate
import tpietzsch.shadergen.generate.SegmentType
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*
import java.util.concurrent.ForkJoinPool
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class VolumeManager(override var hub : Hub?) : Node(), Hubable, HasGeometry {
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
    protected var bdvNodes = ArrayList<graphics.scenery.volumes.bdv.Volume>()
    protected var regularVolumeNodes = ArrayList<Volume>()
    /** Stacks loaded from a BigDataViewer XML file. */
    val renderConverters = ArrayList<ConverterSetup>()
    /** Cache specification. */
    private val cacheSpec = CacheSpec(Texture.InternalFormat.R16, intArrayOf(32, 32, 32))

    private val renderStacks = ArrayList<Stack3D<*>>()
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

    var maxAllowedStepInVoxels = 1.0
    var farPlaneDegradation = 5.0

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

    fun updateProgram() {
        logger.info("Updating effective shader program to $progvol")
        progvol?.setTextureCache(textureCache)
        progvol?.use(context)
        progvol?.setUniforms(context)

        getScene()?.activeObserver?.let { cam ->
            progvol?.setViewportWidth(cam.width.toInt())
            progvol?.setEffectiveViewportSize(cam.width.toInt(), cam.height.toInt())
        }
    }

    private fun needAtLeastNumVolumes(n: Int) {
        val outOfCoreVolumeCount = renderStacks.count { it is MultiResolutionStack3D }
        val regularVolumeCount = renderStacks.count { it is SimpleStack3D }
        logger.info("$currentVolumeCount -> ooc:$outOfCoreVolumeCount reg:$regularVolumeCount")

        if(currentVolumeCount.first == outOfCoreVolumeCount && currentVolumeCount.second == regularVolumeCount) {
            return
        }

        while (outOfCoreVolumes.size < n) {
            outOfCoreVolumes.add(VolumeBlocks(textureCache))
        }

        val signatures = renderStacks.map {
            val dataType = when(it.type) {
                is VolatileUnsignedByteType,
                is UnsignedByteType -> VolumeShaderSignature.PixelType.UBYTE

                is VolatileUnsignedShortType,
                is UnsignedShortType -> VolumeShaderSignature.PixelType.USHORT

                is VolatileARGBType,
                is ARGBType -> VolumeShaderSignature.PixelType.ARGB
                else -> throw IllegalStateException("Unknown volume type ${it.type}")
            }

            val volumeType = when(it) {
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

        val newProgvol = MultiVolumeShaderMip(VolumeShaderSignature(signatures),
            true, farPlaneDegradation,
            segments,
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
            this.material.transferTextures.remove(it)
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
        val viewProjection = cam.projection.clone()
        viewProjection.mult(cam.getTransformation())
        val mvp = Matrix4f().set(viewProjection.floatArray)

        // TODO: original might result in NULL, is this intended?
        currentProg.use(context)
        currentProg.setUniforms(context)

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
            val stack = renderStacks[i]
            if(stack is MultiResolutionStack3D) {
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

        var minWorldVoxelSize = Double.POSITIVE_INFINITY
        val ready = readyToRender()

        renderStacks
            // sort by classname, so we get MultiResolutionStack3Ds first,
            // then simple stacks
            .sortedBy { it.javaClass.simpleName }
            .forEachIndexed { i, stack ->
                if (stack is MultiResolutionStack3D) {
                    currentProg.setConverter(i, renderConverters.get(i))
                    currentProg.setVolume(i, outOfCoreVolumes.get(i))
                    minWorldVoxelSize = min(minWorldVoxelSize, outOfCoreVolumes.get(i).baseLevelVoxelSizeInWorldCoordinates)
                }

                if (stack is SimpleStack3D) {
                    val volume = stackManager.getSimpleVolume(context, stack)
                    currentProg.setConverter(i, renderConverters.get(i))
                    currentProg.setVolume(i, volume)
                    context.bindTexture(volume.volumeTexture)
                    if(ready) {
                        stackManager.upload(context, stack)
                    }
                    minWorldVoxelSize = min(minWorldVoxelSize, volume.voxelSizeInWorldCoordinates)
                }
            }

        currentProg.setViewportWidth(cam.width.toInt())
        currentProg.setEffectiveViewportSize(cam.width.toInt(), cam.height.toInt())
        currentProg.setDegrade(farPlaneDegradation)
        currentProg.setProjectionViewMatrix(mvp, maxAllowedStepInVoxels * minWorldVoxelSize)
        currentProg.use(context)
        currentProg.setUniforms(context)
        currentProg.bindSamplers(context)
        logger.info("Done updating blocks with minVoxelSize=$minWorldVoxelSize")
    }

    fun readyToRender(): Boolean {
        val multiResCount = renderStacks.count { it is MultiResolutionStack3D }
        val regularCount = renderStacks.count { it is SimpleStack3D }

        val multiResMatch = material.textures.count { it.key.startsWith("volumeCache") } == 1
            && material.textures.count { it.key.startsWith("lutSampler_") }  == multiResCount
        val regularMatch = material.textures.count { it.key.startsWith("volume_") } == regularCount

//        logger.info("$multiResCount->$multiResMatch/$regularCount->$regularMatch (${shaderProperties.keys.joinToString(",")})")
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

        return readyToRender()
    }

    /**
     * Updates the current stack given a set of [stacks] to [currentTimepoint].
     */
    protected fun updateRenderState() {
        // check if synchronized block is necessary here
        logger.info("Updating state for ${bdvNodes.size} BDV nodes")
        renderStacks.clear()
        renderConverters.clear()

        bdvNodes.forEach { bdvNode ->
            val visibleSourceIndices = bdvNode.viewerState.visibleSourceIndices
            val currentTimepoint = bdvNode.viewerState.currentTimepoint

            logger.info("Visible: at t=$currentTimepoint: ${visibleSourceIndices.joinToString(", ")}")
            for (i in visibleSourceIndices) {
//                val stack = stacks.getStack(
//                    stacks.timepointId(currentTimepoint),
//                    stacks.setupId(i),
//                    true) as MultiResolutionStack3D<VolatileUnsignedShortType>

                val stack = SourceStacks.getStack3D(bdvNode.viewerState.sources[i].spimSource, currentTimepoint)// as MultiResolutionStack3D<*>

                val sourceTransform = AffineTransform3D()
                bdvNode.viewerState.sources[i].spimSource.getSourceTransform(currentTimepoint, 0, sourceTransform)

                if(stack is MultiResolutionStack3D) {
                    val o = object<T> : MultiResolutionStack3D<T> {
                        override fun getType(): T {
                            return stack.type as T
                        }

                        override fun getSourceTransform(): AffineTransform3D {
                            val w = AffineTransform3D()
                            w.set(*bdvNode.world.transposedFloatArray.map { it.toDouble() }.toDoubleArray())
                            return w.concatenate(sourceTransform)
                        }

                        override fun resolutions(): List<ResolutionLevel3D<T>> {
                            return stack.resolutions() as List<ResolutionLevel3D<T>>
                        }

                        override fun equals(other: Any?): Boolean {
                            return stack.equals(other)
                        }

                        override fun hashCode(): Int {
                            return stack.hashCode()
                        }
                    }

                    renderStacks.add(o)
                } else if(stack is SimpleStack3D) {
                    val o = object<T> : SimpleStack3D<T> {
                        override fun getType(): T {
                            return stack.type as T
                        }

                        override fun getSourceTransform(): AffineTransform3D {
                            val w = AffineTransform3D()
                            w.set(*bdvNode.world.transposedFloatArray.map { it.toDouble() }.toDoubleArray())
                            return w.concatenate(sourceTransform)
                        }

                        override fun getImage(): RandomAccessibleInterval<T> {
                            return stack.image as RandomAccessibleInterval<T>
                        }

                        override fun equals(other: Any?): Boolean {
                            return stack.equals(other)
                        }

                        override fun hashCode(): Int {
                            return stack.hashCode()
                        }
                    }

                    renderStacks.add(o)
                }

                val converter = bdvNode.converterSetups[i]
                renderConverters.add(converter)
            }
        }

        regularVolumeNodes.forEach { volume ->
            val vol = volume.getDescriptor() ?: return@forEach

            if(vol.dataType == NativeTypeEnum.UnsignedShort) {
                val simpleStack = object : BufferedSimpleStack3D<UnsignedShortType>(vol.data,
                    UnsignedShortType(),
                    intArrayOf(vol.width.toInt(), vol.height.toInt(), vol.depth.toInt())) {

                    override fun getSourceTransform(): AffineTransform3D {
                        val w = AffineTransform3D()
//                            val m = AffineTransform3D()
//                            m.setTranslation(0.5, 0.5, 0.5)
                        w.set(*volume.world.transposedFloatArray.map { it.toDouble() }.toDoubleArray())
                        return w
                    }
                }
                logger.info("Added SimpleStack: $simpleStack")
                renderStacks.add(simpleStack)

                renderConverters.add(object: ConverterSetup {
                    val converterColor = ARGBType(Random.nextInt(0, 255*255*255))

                    override fun getSetupId(): Int {
                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                    }

                    override fun setColor(p0: ARGBType?) {
                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                    }

                    override fun supportsColor(): Boolean {
                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                    }

                    override fun getColor(): ARGBType {
                        return converterColor
                    }

                    override fun getDisplayRangeMin(): Double {
                        return 0.0
                    }

                    override fun setDisplayRange(p0: Double, p1: Double) {
                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                    }

                    override fun getDisplayRangeMax(): Double {
                        return 65535.0
                    }

                    override fun setViewer(p0: RequestRepaint?) {
                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                    }

                })

            }
        }
    }


    fun add(node: graphics.scenery.volumes.bdv.Volume) {
        logger.info("Adding $node to OOC nodes")
        bdvNodes.add(node)
        updateRenderState()
        needAtLeastNumVolumes(renderStacks.size)
    }

    fun add(node: Volume) {
        logger.info("Adding $node to regular volume nodes")
        regularVolumeNodes.add(node)
        updateRenderState()
        needAtLeastNumVolumes(renderStacks.size)
    }

    fun notifyUpdate(node: Node) {
        renderStateUpdated = true
    }

    /** Companion object for BDVVolume */
    companion object {
        /** Static [ForkJoinPool] for fill task submission. */
        protected val forkJoinPool: ForkJoinPool = ForkJoinPool(max(1, Runtime.getRuntime().availableProcessors()/2))
    }
}
