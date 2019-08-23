package graphics.scenery.volumes.bdv

import bdv.tools.brightness.ConverterSetup
import bdv.viewer.RequestRepaint
import coremem.enums.NativeTypeEnum
import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.Node
import graphics.scenery.SceneryElement
import graphics.scenery.Settings
import graphics.scenery.ShaderProperty
import graphics.scenery.volumes.Volume
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
import java.util.*
import java.util.concurrent.ForkJoinPool
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class VolumeManager(override var hub : Hub?) : Node(), Hubable {
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
    protected var bdvNodes = ArrayList<BDVNode>()
    protected var regularVolumeNodes = ArrayList<Volume>()
    /** Stacks loaded from a BigDataViewer XML file. */
    val renderConverters = ArrayList<ConverterSetup>()
    /** Cache specification. */
    private val cacheSpec = CacheSpec(Texture.InternalFormat.R16, intArrayOf(32, 32, 32))

    private val renderStacks = ArrayList<Stack3D<*>>()
    private val simpleRenderStacks = ArrayList<SimpleStack3D<VolatileUnsignedShortType>>()

    private val stackManager = SceneryStackManager()

    private val multiResolutionStacks = ArrayList(
        Arrays.asList<MultiResolutionStack3D<VolatileUnsignedShortType>>(null, null, null))
    /** Whether to freeze the current set of blocks in-place. */
    var freezeRequiredBlocks = false

    /** Backing shader program */
    protected var prog = ArrayList<MultiVolumeShaderMip>()
    protected var progvol: MultiVolumeShaderMip? = null
    protected var renderStateUpdated = false
    protected var cacheSizeUpdated = false

    protected var currentVolumeCount: Pair<Int, Int>

    init {
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
        progvol = prog.last()//get(renderStacks.size)
        progvol?.setTextureCache(textureCache)
        progvol?.use(context)
        progvol?.setUniforms(context)

        getScene()?.activeObserver?.let { cam ->
            progvol?.setViewportWidth(cam.width.toInt())
            progvol?.setEffectiveViewportSize(cam.width.toInt(), cam.height.toInt())
        }

        preDraw()
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

        val progvol = MultiVolumeShaderMip(VolumeShaderSignature(signatures),
            true, 1.0,
            this.javaClass,
            "BDVVolume.vert",
            "BDVVolume.frag",
            "MaxDepth.frag",
            "InputZBuffer")

        progvol.setTextureCache(textureCache)
        progvol.setDepthTextureName("InputZBuffer")
        logger.info("Using program for $outOfCoreVolumeCount out-of-core volumes and $regularVolumeCount regular volumes")
        prog.add(progvol)

        val oldKeys = this.material.textures.keys()
            .asSequence()
            .filter { it.endsWith("_") }
        oldKeys.map {
            this.material.textures.remove(it)
            this.material.transferTextures.remove(it)
        }

        currentVolumeCount = outOfCoreVolumeCount to regularVolumeCount
    }

    /**
     * Updates the currently-used set of blocks using [context] to
     * facilitate the updates on the GPU.
     */
    protected fun updateBlocks(context: SceneryContext) {
        logger.debug("Updating blocks")
        bdvNodes.forEach { bdvNode ->
            bdvNode.prepareNextFrame()
        }

        val cam = getScene()?.activeObserver ?: return
        val viewProjection = cam.projection.clone()
        viewProjection.mult(cam.getTransformation())
        val mvp = Matrix4f().set(viewProjection.floatArray)

        // TODO: original might result in NULL, is this intended?
        val progvol = prog.last()//get(renderStacks.size)
        progvol.use(context)
        progvol.setUniforms(context)

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
                    context.bindTexture(volume.volumeTexture)
                    val uploaded = stackManager.upload(context, stack)
                    minWorldVoxelSize = min(minWorldVoxelSize, volume.voxelSizeInWorldCoordinates)
                }
            }

        progvol.setViewportWidth(cam.width.toInt())
        progvol.setEffectiveViewportSize(cam.width.toInt(), cam.height.toInt())
        progvol.setProjectionViewMatrix(mvp, minWorldVoxelSize)
        progvol.use(context)
        progvol.setUniforms(context)
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
     * Updates the current stack given a set of [stacks] to [currentTimepoint].
     */
    protected fun updateRenderState() {
        val visibleSourceIndices: List<Int>
        val currentTimepoint: Int

        // check if synchronized block is necessary here
        bdvNodes.forEach { bdvNode ->
            val visibleSourceIndices = bdvNode.state.visibleSourceIndices
            val currentTimepoint = bdvNode.state.currentTimepoint

            logger.info("Visible: at t=$currentTimepoint: ${visibleSourceIndices.joinToString(", ")}")
            renderStacks.clear()
            renderConverters.clear()
            for (i in visibleSourceIndices) {
                val stack = bdvNode.stacks.getStack(
                    bdvNode.stacks.timepointId(currentTimepoint),
                    bdvNode.stacks.setupId(i),
                    true) as MultiResolutionStack3D<VolatileUnsignedShortType>
                val sourceTransform = AffineTransform3D()
                bdvNode.state.sources[i].spimSource.getSourceTransform(currentTimepoint, 0, sourceTransform)
                val wrappedStack = object : MultiResolutionStack3D<VolatileUnsignedShortType> {
                    override fun getType() : VolatileUnsignedShortType {
                        return stack.type
                    }

                    override fun getSourceTransform() : AffineTransform3D {
                        val w = AffineTransform3D()
                        w.set(*world.transposedFloatArray.map { it.toDouble() }.toDoubleArray())
                        return w.concatenate(sourceTransform)
                    }

                    override fun resolutions() : List<ResolutionLevel3D<VolatileUnsignedShortType>> {
                        return stack.resolutions()
                    }
                }
                renderStacks.add(wrappedStack)
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
                        w.set(*world.transposedFloatArray.map { it.toDouble() }.toDoubleArray())
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


    fun add(node: BDVNode) {
        bdvNodes.add(node)
    }

    fun add(node: Volume) {
        regularVolumeNodes.add(node)
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
