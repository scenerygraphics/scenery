package graphics.scenery.volumes.bdv

import bdv.spimdata.SpimDataMinimal
import bdv.spimdata.XmlIoSpimDataMinimal
import bdv.tools.brightness.ConverterSetup
import bdv.tools.brightness.RealARGBColorConverterSetup
import cleargl.GLTypeEnum
import cleargl.GLVector
import coremem.enums.NativeTypeEnum
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
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/**
 * Volume Rendering Node for scenery.
 * If [autosetProperties] is true, the node will automatically determine
 * the volumes' transfer function range.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Martin Weigert <mweigert@mpi-cbg.de>
 */
@Suppress("unused")
open class BDVVolume(bdvXMLFile: String = "", maxMemoryMB: Int = 1024) : Volume(false) {
    data class VolumeDescriptor(val path: Path?,
                                val width: Long,
                                val height: Long,
                                val depth: Long,
                                val dataType: NativeTypeEnum,
                                val bytesPerVoxel: Int,
                                val data: ByteBuffer)

    /**
     * Histogram class.
     */
    class Histogram<T : Comparable<T>>(histogramSize: Int) {
        /** Bin storage for the histogram. */
        val bins: HashMap<T, Long> = HashMap(histogramSize)

        /** Adds a new value, putting it in the corresponding bin. */
        fun add(value: T) {
            bins[value] = (bins[value] ?: 0L) + 1L
        }

        /** Returns the minimum value contained in the histogram. */
        fun min(): T = bins.keys.minBy { it } ?: (0 as T)
        /** Returns the maximum value contained in the histogram. */
        fun max(): T = bins.keys.maxBy { it } ?: (0 as T)
    }

    /**
     *  The rendering method used in the shader, can be
     *
     *  0 -- Local Maximum Intensity Projection
     *  1 -- Maximum Intensity Projection
     *  2 -- Alpha compositing
     */

    var context = SceneryContext(this)
        protected set
    var maxTimepoint: Int = 0
    var textureCache: TextureCache? = null
    var pboChain: PboChain? = null
    var outOfCoreVolumes = ArrayList<VolumeBlocks>()
    var stacks: SpimDataStacks? = null
    private val cacheSpec = CacheSpec(Texture.InternalFormat.R16, intArrayOf(32, 32, 32))

    private var currentTimepoint = 0
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
    var freezeRequiredBlocks = false
    var prog: MultiVolumeShaderMip? = null

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

            val cacheGridDimensions = TextureCache.findSuitableGridSize(cacheSpec, maxMemoryMB)
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

    fun updateBlocks(context: SceneryContext) {
        if(stacks == null) {
            return
        }

        stacks?.cacheControl?.prepareNextFrame()

        val cam = getScene()?.activeObserver ?: return
        val viewProjection = cam.projection.clone()
        viewProjection.mult(cam.getTransformation())
        val vp = Matrix4f().set(viewProjection.floatArray.clone())

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

    override fun preDraw() {
        if (transferFunction.stale) {
            logger.debug("Transfer function is stale, updating")
            material.transferTextures["transferFunction"] = GenericTexture(
                "transferFunction", GLVector(transferFunction.textureSize.toFloat(), transferFunction.textureHeight.toFloat(), 1.0f),
                channels = 1, type = GLTypeEnum.Float, contents = transferFunction.serialise())

            material.textures["diffuse"] = "fromBuffer:transferFunction"
            material.needsTextureReload = true

            time = System.nanoTime().toFloat()
        }

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


    fun updateCurrentStack(stacks: SpimDataStacks) {
        logger.info("Updating current stack")
        for (i in 0 until outOfCoreVolumes.size) {
            aMultiResolutionStacks[i].set(
                stacks.getStack(
                    stacks.timepointId(currentTimepoint),
                    stacks.setupId(i),
                    true) as MultiResolutionStack3D<VolatileUnsignedShortType>)
        }
    }

    companion object {
        val forkJoinPool: ForkJoinPool = ForkJoinPool(max(1, Runtime.getRuntime().availableProcessors()/2))
    }
}
