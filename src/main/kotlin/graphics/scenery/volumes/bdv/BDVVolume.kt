package graphics.scenery.volumes.bdv

import bdv.spimdata.SpimDataMinimal
import bdv.spimdata.XmlIoSpimDataMinimal
import graphics.scenery.*
import graphics.scenery.volumes.Volume
import net.imglib2.display.ColorConverter
import net.imglib2.display.RealARGBColorConverter
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.VolatileUnsignedShortType
import org.joml.Matrix4f
import tpietzsch.backend.Texture
import tpietzsch.cache.*
import tpietzsch.example2.MultiVolumeShaderMip
import tpietzsch.example2.VolumeBlocks
import tpietzsch.multires.MultiResolutionStack3D
import tpietzsch.multires.SpimDataStacks
import java.util.*
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/**
 * BigDataViewer-backed out-of-core volume rendering [Node] for scenery.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Tobias Pietzsch <pietzsch@mpi-cbg.de>
 */
@Suppress("unused")
open class BDVVolume(bdvXMLFile: String = "", maxGPUMemoryMB: Int = 1024) : Volume(false) {
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
    protected var textureCache: TextureCache? = null
    /** PBO chain for temporary data storage. */
    protected var pboChain: PboChain? = null
    /** Set of [VolumeBlocks]. */
    protected var outOfCoreVolumes = ArrayList<VolumeBlocks>()
    /** Stacks loaded from a BigDataViewer XML file. */
    protected var stacks: SpimDataStacks? = null
    /** Cache specification. */
    private val cacheSpec = CacheSpec(Texture.InternalFormat.R16, intArrayOf(32, 32, 32))

    /** Current timepoint in the set of [stacks]. */
    var currentTimepoint = 0
        protected set

    private val aMultiResolutionStacks = ArrayList(Arrays.asList(
        AtomicReference(),
        AtomicReference(),
        AtomicReference<MultiResolutionStack3D<VolatileUnsignedShortType>>()))

    private val multiResolutionStacks = ArrayList(
        Arrays.asList<MultiResolutionStack3D<VolatileUnsignedShortType>>(null, null, null))
    private val convs = ArrayList<ColorConverter>(Arrays.asList<RealARGBColorConverter.Imp0<RealType<*>>>(
        RealARGBColorConverter.Imp0(0.0, 1.0),
        RealARGBColorConverter.Imp0(0.0, 1.0),
        RealARGBColorConverter.Imp0(0.0, 1.0)))
    /** Whether to freeze the current set of blocks in-place. */
    var freezeRequiredBlocks = false

    /** Backing shader program */
    protected var prog: MultiVolumeShaderMip? = null

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

        if(bdvXMLFile != "") {
            val spimData: SpimDataMinimal = XmlIoSpimDataMinimal().load(bdvXMLFile)
            stacks = SpimDataStacks(spimData)

            maxTimepoint = spimData.sequenceDescription.timePoints.timePointsOrdered.size - 1

            val cacheGridDimensions = TextureCache.findSuitableGridSize(cacheSpec, maxGPUMemoryMB)
            textureCache = TextureCache(cacheGridDimensions, cacheSpec)

            pboChain = PboChain(5, 100, textureCache)

            val numVolumes = 3

            for(i in 0 until numVolumes) {
                outOfCoreVolumes.add(VolumeBlocks(textureCache))
            }

            prog = MultiVolumeShaderMip(numVolumes)
            prog?.setTextureCache(textureCache)
            prog?.init(context)

            stacks?.let {
                updateCurrentStack(it)
                updateBlocks(context)
            }

            convs[0].color = ARGBType(0xff8888)
            convs[1].color = ARGBType(0x88ff88)
            convs[2].color = ARGBType(0x8888ff)

            convs.forEach {
                it.min = 962.0
                it.max = 6201.0
            }
        }
    }

    /**
     * Updates the currently-used set of blocks using [context] to
     * facilitate the updates on the GPU.
     */
    protected fun updateBlocks(context: SceneryContext) {
        if(stacks == null) {
            return
        }

        stacks?.cacheControl?.prepareNextFrame()

        val cam = getScene()?.activeObserver ?: return
        val viewProjection = cam.projection.clone()
        viewProjection.mult(cam.getTransformation())
        val vp = Matrix4f().set(viewProjection.floatArray)

        prog?.setViewportSize(cam.width.toInt(), cam.height.toInt())
        prog?.setProjectionViewMatrix(vp)
        prog?.use(context)

        val fillTasks = ArrayList<FillTask>()
        for(i in 0 until outOfCoreVolumes.size) {
            val volume = outOfCoreVolumes[i]
            volume.init(multiResolutionStacks[i], cam.width.toInt(), vp)
            fillTasks.addAll(volume.fillTasks)
        }

        ProcessFillTasks.parallel(textureCache, pboChain, context, forkJoinPool, fillTasks)

        var repaint = false
        for(i in 0 until outOfCoreVolumes.size) {
            val volumeBlocks = outOfCoreVolumes[i]
            val complete = volumeBlocks.makeLut()
            if(!complete) {
                repaint = true
            }
            context.bindTexture(volumeBlocks.lookupTexture)
            volumeBlocks.lookupTexture.upload(context)
        }

        for (i in 0 until outOfCoreVolumes.size) {
            prog?.setConverter(i, convs[i])
            prog?.setVolume(i, outOfCoreVolumes[i])
        }

        prog?.setViewportSize(cam.width.toInt(), cam.height.toInt())
        prog?.setProjectionViewMatrix(vp)
        prog?.use(context)
    }

    /**
     * Pre-draw routine to be called by the rendered just before drawing.
     * Updates texture cache and used blocks.
     */
    override fun preDraw() {
        if(stacks == null) {
            logger.info("Don't have stacks, returning")
            return
        }

        textureCache?.let {
            context.bindTexture(it)
        }

        for(i in 0 until outOfCoreVolumes.size){
            val stack = aMultiResolutionStacks[i].get()

            if(stack == null) {
                logger.warn("Stack $i is null")
                continue
            }

            multiResolutionStacks[i] = stack
        }

        if(freezeRequiredBlocks == false) {
            updateBlocks(context)
        }

        context.runDeferredBindings()
    }

    /**
     * Goes to the next available timepoint, returning the number of the updated timepoint.
     */
    fun nextTimepoint(): Int {
        return goToTimePoint(currentTimepoint + 1)
    }

    /** Goes to the previous available timepoint, returning the number of the updated timepoint. */
    fun previousTimepoint(): Int {
        return goToTimePoint(currentTimepoint - 1)
    }

    /** Goes to the [timepoint] given, returning the number of the updated timepoint. */
    fun goToTimePoint(timepoint: Int): Int {
        stacks?.let { s ->
            currentTimepoint = min(max(timepoint, 0), maxTimepoint)
            updateCurrentStack(s)
        }

        return currentTimepoint
    }

    /**
     * Updates the current stack given a set of [stacks] to [currentTimepoint].
     */
    protected fun updateCurrentStack(stacks: SpimDataStacks) {
        logger.info("Updating current stack, timepoint=$currentTimepoint")
        for (i in 0 until outOfCoreVolumes.size) {
            aMultiResolutionStacks[i].set(
                stacks.getStack(
                    stacks.timepointId(currentTimepoint),
                    stacks.setupId(i),
                    true) as MultiResolutionStack3D<VolatileUnsignedShortType>)
        }
    }

    /** Companion object for BDVVolume */
    companion object {
        /** Static [ForkJoinPool] for fill task submission. */
        protected val forkJoinPool: ForkJoinPool = ForkJoinPool(max(1, Runtime.getRuntime().availableProcessors()/2))
    }
}
