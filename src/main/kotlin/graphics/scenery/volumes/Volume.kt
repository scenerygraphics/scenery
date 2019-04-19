package graphics.scenery.volumes

import cleargl.GLTypeEnum
import cleargl.GLVector
import coremem.enums.NativeTypeEnum
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.vulkan.toHexString
import graphics.scenery.numerics.OpenSimplexNoise
import graphics.scenery.numerics.Random
import graphics.scenery.utils.forEachParallel
import graphics.scenery.volumes.Volume.Colormap.ColormapBuffer
import graphics.scenery.volumes.Volume.Colormap.ColormapFile
import io.scif.SCIFIO
import io.scif.util.FormatTools
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.memAlloc
import sun.misc.Unsafe
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.properties.Delegates
import kotlin.reflect.KProperty
import kotlin.streams.toList

/**
 * Volume Rendering Node for scenery.
 * If [autosetProperties] is true, the node will automatically determine
 * the volumes' transfer function range.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Martin Weigert <mweigert@mpi-cbg.de>
 */
@Suppress("unused")
open class Volume : Mesh("Volume") {
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

    val boxwidth = 1.0f

    /** Whether to allow setting the transfer range or not */
    var lockTransferRange = false

    /** Flexible [ShaderProperty] storage */
    @ShaderProperty var shaderProperties = hashMapOf<String, Any>()

    /**
     *  The rendering method used in the shader, can be
     *
     *  0 -- Local Maximum Intensity Projection
     *  1 -- Maximum Intensity Projection
     *  2 -- Alpha compositing
     */
    @ShaderProperty var renderingMethod: Int = 2

    /** Transfer function minimum */
    @ShaderProperty var trangemin = 0.00f
        set(value) {
            if(!lockTransferRange) {
                field = value
            }
        }

    /** Transfer function maximum */
    @ShaderProperty var trangemax = 1.0f
        set(value) {
            if(!lockTransferRange) {
                field = value
            }
        }

    /** Bounding box minimum in x direction */
    @ShaderProperty var boxMin_x = -boxwidth
    /** Bounding box minimum in y direction */
    @ShaderProperty var boxMin_y = -boxwidth
    /** Bounding box minimum in z direction */
    @ShaderProperty var boxMin_z = -boxwidth

    /** Bounding box maximum in x direction */
    @ShaderProperty var boxMax_x = boxwidth
    /** Bounding box maximum in y direction */
    @ShaderProperty var boxMax_y = boxwidth
    /** Bounding box maximum in z direction */
    @ShaderProperty var boxMax_z = boxwidth

    /** Maximum steps to take along a single ray through the volume */
    @ShaderProperty var stepSize = 0.01f
    /** Alpha blending factor */
    @ShaderProperty var alphaBlending = 1.0f
    /** Gamma exponent */
    @ShaderProperty var gamma = 1.0f

    /** Volume size in voxels along x direction */
    @ShaderProperty var sizeX by Delegates.observable(256) { property, old, new -> volumePropertyChanged(property, old, new) }
    /** Volume size in voxels along y direction */
    @ShaderProperty var sizeY by Delegates.observable(256) { property, old, new -> volumePropertyChanged(property, old, new) }
    /** Volume size in voxels along z direction */
    @ShaderProperty var sizeZ by Delegates.observable(256) { property, old, new -> volumePropertyChanged(property, old, new) }

    /** Voxel size in x direction */
    @ShaderProperty var voxelSizeX by Delegates.observable(1.0f) { property, old, new -> volumePropertyChanged(property, old, new) }
    /** Voxel size in y direction */
    @ShaderProperty var voxelSizeY by Delegates.observable(1.0f) { property, old, new -> volumePropertyChanged(property, old, new) }
    /** Voxel size in z direction */
    @ShaderProperty var voxelSizeZ by Delegates.observable(1.0f) { property, old, new -> volumePropertyChanged(property, old, new) }

    @ShaderProperty protected var dataRangeMin: Int = 0
    @ShaderProperty protected var dataRangeMax: Int = 255

    @ShaderProperty var kernelSize: Float = 0.01f
    @ShaderProperty var maxOcclusionDistance: Float = 0.01f
    @ShaderProperty var occlusionSteps: Int = 0

    @ShaderProperty var time: Float = System.nanoTime().toFloat()

    /** The transfer function to use for the volume. Flat by default. */
    var transferFunction: TransferFunction = TransferFunction.flat(1.0f)

    /**
     * Regenerates the [boundingBox] in case any relevant properties have changed.
     */
    protected fun <R> volumePropertyChanged(property: KProperty<*>, old: R, new: R) {
        boundingBox = generateBoundingBox()
    }

    /**
     * Color map class to contain lookup tables.
     * These can be file-based ([ColormapFile]), or buffer-based ([ColormapBuffer]).
     */
    sealed class Colormap {
        /**
         * File-based color map.
         */
        class ColormapFile(val filename: String) : Colormap()

        /**
         * Buffer-based color map.
         */
        class ColormapBuffer(val texture: GenericTexture) : Colormap()
    }

    /** Stores the available colormaps for transfer functions */
    var colormaps = HashMap<String, Colormap>()

    /** The active colormap, setting this will automatically update the color map texture */
    var colormap: String = "viridis"
        set(name) {
            colormaps[name]?.let { cm ->
                field = name
                when (cm) {
                    is Colormap.ColormapFile -> {
                        this@Volume.material.textures["normal"] = cm.filename
                    }

                    is Colormap.ColormapBuffer -> {
                        this@Volume.material.transferTextures["colormap"] = cm.texture
                        this@Volume.material.textures["normal"] = "fromBuffer:colormap"
                    }
                }

                this@Volume.material.needsTextureReload = true
                return
            }

            logger.error("Could not find colormap '$name'.")
        }

    /** Temporary storage for volume data */
    @Transient protected val volumes = ConcurrentHashMap<String, VolumeDescriptor>()

    /** Stores the current volume's name. Can be set to the path of a new volume */
    var currentVolume: String = ""
        set(value) {
            field = value
            if (value != "") {
                readFromRaw(Paths.get(field), true)
            }
        }

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

        material = ShaderMaterial.fromClass(Volume::class.java)

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

        assignEmptyVolumeTexture()
    }

    /**
     * Preloads all volumes found in the path indicated by [file].
     * The folder is assumed to contain a `stacks.info` file containing volume metadata.
     */
    @JvmOverloads fun preloadRawFromPath(file: Path, dataType: NativeTypeEnum = NativeTypeEnum.UnsignedByte) {
        val id = file.fileName.toString()

        val infoFile = file.resolveSibling("stacks" + ".info")

        val lines = Files.lines(infoFile).toList()

        logger.debug("reading stacks.info (${lines.joinToString()}) (${lines.size} lines)")
        val dimensions = lines.get(0).split(",").map { it.toLong() }.toTypedArray()


        sizeX = dimensions[0].toInt()
        sizeY = dimensions[1].toInt()
        sizeZ = dimensions[2].toInt()

        logger.debug("setting voxelsize to $voxelSizeX x $voxelSizeY x $voxelSizeZ")
        logger.debug("setting min max to ${this.trangemin}, ${this.trangemax} ")
        logger.debug("setting alpha blending to ${this.alphaBlending}")
        logger.debug("setting dim to $sizeX, $sizeY, $sizeZ")


        if (volumes.containsKey(id)) {
            logger.info("$id is already in cache")
        } else {
            logger.info("Preloading $id from disk")
            val buffer = ByteArray(1024 * 1024)
            val stream = FileInputStream(file.toFile())
            val imageData: ByteBuffer = memAlloc((2 * dimensions[0] * dimensions[1] * dimensions[2]).toInt())

            logger.debug("${file.fileName}: Allocated ${imageData.capacity()} bytes for UINT16 image of ${dimensions.joinToString("x")}")

            val start = System.nanoTime()
            var bytesRead = stream.read(buffer, 0, buffer.size)
            while (bytesRead > -1) {
                imageData.put(buffer, 0, bytesRead)
                bytesRead = stream.read(buffer, 0, buffer.size)
            }
            val duration = (System.nanoTime() - start) / 10e5
            logger.debug("Reading took $duration ms")

            imageData.flip()

//            if (replace) {
//                volumes.clear()
//            }

            val descriptor = VolumeDescriptor(
                file,
                dimensions[0], dimensions[1], dimensions[2],
                dataType, dataType.bytesPerVoxel(), data = imageData
            )

            volumes.put(id, descriptor)
        }
    }

    private fun NativeTypeEnum.bytesPerVoxel(): Int {
        return when(this) {
            NativeTypeEnum.Byte -> 1
            NativeTypeEnum.UnsignedByte -> 1
            NativeTypeEnum.Short -> 2
            NativeTypeEnum.UnsignedShort -> 2
            NativeTypeEnum.Int -> 4
            NativeTypeEnum.UnsignedInt -> 4
            NativeTypeEnum.Long -> 8
            NativeTypeEnum.UnsignedLong -> 8
            NativeTypeEnum.HalfFloat -> 2
            NativeTypeEnum.Float -> 4
            NativeTypeEnum.Double -> 8
        }
    }

    /**
     * Reads volumetric data from a [buffer]. The volume will be given the name [id], and the
     * [buffer] is assumed to contain [x]*[y]*[z]*[bytesPerVoxel] bytes and be of the type
     * [dataType]. For anisotropic volumes, [voxelX], [voxelY] and [voxelZ] can be set appropriately.
     */
    @JvmOverloads fun readFromBuffer(id: String, buffer: ByteBuffer,
                       x: Long, y: Long, z: Long,
                       voxelX: Float, voxelY: Float, voxelZ: Float,
                       dataType: NativeTypeEnum = NativeTypeEnum.UnsignedInt, bytesPerVoxel: Int = 2,
                       allowDeallocation: Boolean = false) {
        sizeX = x.toInt()
        sizeY = y.toInt()
        sizeZ = z.toInt()

        val vol = if (volumes.containsKey(id)) {
            volumes.get(id)
        } else {
            val descriptor = VolumeDescriptor(
                null,
                x, y, z, dataType, bytesPerVoxel,
                buffer
            )

            volumes.put(id, descriptor)
            descriptor
        }

        if (vol != null) {
            assignVolumeTexture( longArrayOf( x, y, z ), vol, allowDeallocation)
        }
    }

    // Endianness conversion via Unsafe
    // by Peter Lawrey, https://stackoverflow.com/a/21089527/2129040
    private fun swapEndianUnsafe(bytes: ByteArray): ByteArray {
        assert(bytes.size % 4 == 0)
        var i = 0
        while (i < bytes.size) {
            UNSAFE.putInt(bytes, 1L*BYTES_OFFSET + i, Integer.reverseBytes(UNSAFE.getInt(bytes, 1L*BYTES_OFFSET + i)))
            i += 4
        }

        return bytes
    }

    /**
     * Reads volumetric data from a [file] using scifio, and if [replace] is true, will
     * replace the current volumes' buffer and mark it for deallocation.
     */
    fun readFrom(file: Path, replace: Boolean = false): String {
        if(file.normalize().toString().endsWith("raw")) {
            return readFromRaw(file, replace)
        }

        val reader = scifio.initializer().initializeReader(file.normalize().toString())

        with(reader.openPlane(0, 0)) {
            sizeX = lengths[0].toInt()
            sizeY = lengths[1].toInt()
            sizeZ = reader.getPlaneCount(0).toInt()
        }

        val id = file.fileName.toString()
        val bytesPerVoxel = reader.openPlane(0, 0).imageMetadata.bitsPerPixel/8
        reader.openPlane(0, 0).imageMetadata.pixelType

        val dataType = when(reader.openPlane(0, 0).imageMetadata.pixelType) {
            FormatTools.INT8 -> NativeTypeEnum.Byte
            FormatTools.INT16 -> NativeTypeEnum.Short
            FormatTools.INT32 -> NativeTypeEnum.Int

            FormatTools.UINT8 -> NativeTypeEnum.UnsignedByte
            FormatTools.UINT16 -> NativeTypeEnum.UnsignedShort
            FormatTools.UINT32 -> NativeTypeEnum.UnsignedInt

            FormatTools.FLOAT -> NativeTypeEnum.Float
            else -> {
                logger.error("Unknown scif.io pixel type ${reader.openPlane(0, 0).imageMetadata.pixelType}, assuming unsigned byte.")
                NativeTypeEnum.UnsignedByte
            }
        }

        val vol = if (volumes.containsKey(id)) {
            logger.info("Getting $id from cache")
            volumes.get(id)!!
        } else {
            logger.info("Loading $id from disk")
            val imageData: ByteBuffer = memAlloc((bytesPerVoxel * sizeX * sizeY * sizeZ))

            logger.info("${file.fileName}: Allocated ${imageData.capacity()} bytes for $dataType ${8*bytesPerVoxel}bit image of $sizeX/$sizeY/$sizeZ")

            val start = System.nanoTime()

//            if(reader.openPlane(0, 0).imageMetadata.isLittleEndian) {
                logger.info("Volume is little endian")
                (0 until reader.getPlaneCount(0)).forEach { plane ->
                    imageData.put(reader.openPlane(0, plane).bytes)
                }
//            } else {
//                logger.info("Volume is big endian")
//                (0 until reader.getPlaneCount(0)).forEach { plane ->
//                    imageData.put(swapEndianUnsafe(reader.openPlane(0, plane).bytes))
//                }
//            }

            val duration = (System.nanoTime() - start) / 10e5
            logger.info("Reading took $duration ms")

            imageData.flip()

            val descriptor = VolumeDescriptor(
                file,
                sizeX.toLong(), sizeY.toLong(), sizeZ.toLong(),
                dataType, bytesPerVoxel, data = imageData
            )

//            thread {
//                val histogram = Histogram<Int>(65536)
//                val buf = imageData.asShortBuffer()
//                while (buf.hasRemaining()) {
//                    histogram.add(buf.get().toInt() + Short.MAX_VALUE + 1)
//                }
//
//                logger.info("Min/max of $id: ${histogram.min()}/${histogram.max()} in ${histogram.bins.size} bins")
//
//                this.trangemin = histogram.min().toFloat()
//                this.trangemax = histogram.max().toFloat()
//            }

            volumes.put(id, descriptor)
            descriptor
        }

        assignVolumeTexture(longArrayOf(sizeX.toLong(), sizeY.toLong(), sizeZ.toLong()), vol, replace)

        return id
    }

    /**
     * Reads raw volumetric data from a [file]. If [autorange] is set, the transfer function range
     * will be determined automatically, if [cache] is true, the volume's data will be stored in [volumes] for
     * future use. If [replace] is set, the current volumes' buffer will be replace and marked for deallocation.
     *
     * Returns the new volumes' id.
     */
    fun readFromRaw(file: Path, replace: Boolean = false, autorange: Boolean = true, cache: Boolean = true): String {
        val infoFile = file.resolveSibling("stacks" + ".info")

        val lines = Files.lines(infoFile).toList()

        logger.debug("reading stacks.info (${lines.joinToString()}) (${lines.size} lines)")
        val dimensions = lines.get(0).split(",").map { it.toLong() }.toTypedArray()
        logger.debug("setting dim to ${dimensions.joinToString()}")

        sizeX = dimensions[0].toInt()
        sizeY = dimensions[1].toInt()
        sizeZ = dimensions[2].toInt()

        logger.debug("setting voxelsize to $voxelSizeX x $voxelSizeY x $voxelSizeZ")
        logger.debug("setting min max to $trangemin, $trangemax ")
        logger.debug("setting alpha blending to $alphaBlending")
        logger.debug("setting dim to $sizeX, $sizeY, $sizeZ")

        val id = file.fileName.toString()

        val vol = if (volumes.containsKey(id) && cache) {
            logger.info("Getting $id from cache")
            volumes.get(id)!!
        } else {
            logger.info("Loading $id from disk")
            val buffer = ByteArray(1024 * 1024)
            val stream = FileInputStream(file.toFile())
            val imageData: ByteBuffer = memAlloc((2 * dimensions[0] * dimensions[1] * dimensions[2]).toInt())

            logger.info("${file.fileName}: Allocated ${imageData.capacity()} bytes for UINT16 image of ${dimensions.joinToString("x")}")

            val start = System.nanoTime()
            var bytesRead = stream.read(buffer, 0, buffer.size)
            while (bytesRead > -1) {
                imageData.put(buffer, 0, bytesRead)
                bytesRead = stream.read(buffer, 0, buffer.size)
            }
            val duration = (System.nanoTime() - start) / 10e5
            logger.info("Reading took $duration ms")

            imageData.flip()

            val descriptor = VolumeDescriptor(
                file,
                dimensions[0], dimensions[1], dimensions[2],
                NativeTypeEnum.UnsignedShort, 2, data = imageData
            )

            if(autorange) {
                thread {
                    val histogram = Histogram<Int>(65536)
                    val buf = imageData.asShortBuffer()
                    while (buf.hasRemaining()) {
                        histogram.add(buf.get().toInt() + Short.MAX_VALUE + 1)
                    }

                    logger.info("Min/max of $id: ${histogram.min()}/${histogram.max()} in ${histogram.bins.size} bins")

                    this.trangemin = histogram.min().toFloat()
                    this.trangemax = histogram.max().toFloat()
                }
            }

            if(cache) {
                volumes.put(id, descriptor)
            }

            descriptor
        }

        assignVolumeTexture(dimensions.toLongArray(), vol, replace)

        return id
    }

    var assignment: (() -> Unit)? = null

    override fun preDraw() {
        if(transferFunction.stale) {
            logger.debug("Transfer function is stale, updating")
            material.transferTextures["transferFunction"] = GenericTexture(
                "transferFunction", GLVector(transferFunction.textureSize.toFloat(), transferFunction.textureHeight.toFloat(), 1.0f),
                channels = 1, type = GLTypeEnum.Float, contents = transferFunction.serialise(),
                repeatS = false, repeatT = false, repeatU = false)

            material.textures["diffuse"] = "fromBuffer:transferFunction"
            material.needsTextureReload = true

            time = System.nanoTime().toFloat()
        }

        assignment?.invoke()
    }

    protected fun NativeTypeEnum.toGLType() =
        when (this) {
            NativeTypeEnum.UnsignedInt -> GLTypeEnum.UnsignedInt
            NativeTypeEnum.Byte -> GLTypeEnum.Byte
            NativeTypeEnum.UnsignedByte -> GLTypeEnum.UnsignedByte
            NativeTypeEnum.Short -> GLTypeEnum.Short
            NativeTypeEnum.UnsignedShort -> GLTypeEnum.UnsignedShort
            NativeTypeEnum.Int -> GLTypeEnum.Int
            NativeTypeEnum.Long -> TODO()
            NativeTypeEnum.UnsignedLong -> TODO()
            NativeTypeEnum.HalfFloat -> TODO()
            NativeTypeEnum.Float -> GLTypeEnum.Float
            NativeTypeEnum.Double -> TODO()
        }

    protected fun assignEmptyVolumeTexture() {
        val emptyBuffer = BufferUtils.allocateByteAndPut(byteArrayOf(0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                                     0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0))
        val dim = GLVector(2.0f, 2.0f, 2.0f)
        val gtv = GenericTexture("empty-volume", dim, 1, GLTypeEnum.UnsignedByte, emptyBuffer, false, false, normalized = true)

        material.transferTextures.put("empty-volume", gtv)
        material.textures.put("VolumeTextures", "fromBuffer:empty-volume")

        colormap = "viridis"
    }

    @Transient private val deallocations = ArrayDeque<ByteBuffer>()
    var deallocationThreshold = 50000

    protected fun assignVolumeTexture(dimensions: LongArray, descriptor: VolumeDescriptor, replace: Boolean) {
//        while(deallocations.size > deallocationThreshold) {
//            val last = deallocations.pollLast()
//            logger.debug("Time series: deallocating $last from ${deallocations.map { it.hashCode() }.joinToString(", ")}")
//            logger.trace("Address is ${MemoryUtil.memAddress(last).toHexString()}")
//        }

        val (min: Int, max: Int) = when(descriptor.dataType) {
             NativeTypeEnum.Byte -> 0 to 255
             NativeTypeEnum.UnsignedByte -> 0 to 255
             NativeTypeEnum.Short -> 0 to 65536
             NativeTypeEnum.UnsignedShort -> 0 to 65536
             NativeTypeEnum.Int -> 0 to Integer.MAX_VALUE
             NativeTypeEnum.UnsignedInt -> 0 to Integer.MAX_VALUE
             NativeTypeEnum.HalfFloat -> 0 to Float.MAX_VALUE.toInt()
             NativeTypeEnum.Float -> 0 to Float.MAX_VALUE.toInt()

             NativeTypeEnum.Long,
             NativeTypeEnum.UnsignedLong,
             NativeTypeEnum.Double -> throw UnsupportedOperationException("64bit volumes are not supported")
        }

        dataRangeMin = min
        dataRangeMax = max

        trangemin = min.toFloat()
        trangemax = max.toFloat()

        val dim = GLVector(dimensions[0].toFloat(), dimensions[1].toFloat(), dimensions[2].toFloat())
        val gtv = GenericTexture("VolumeTextures", dim,
            1, descriptor.dataType.toGLType(), descriptor.data, false, false, normalized = true)

        boundingBox = generateBoundingBox()

        logger.debug("$name: Assigning volume texture")
        this.material.transferTextures.put("VolumeTextures", gtv)?.let {
            if (replace && it.name != "empty-volume" && !deallocations.contains(it.contents)) {
                deallocations.add(it.contents)
            }
        }

        this.material.textures.put("VolumeTextures", "fromBuffer:VolumeTextures")
        this.material.needsTextureReload = true
    }

    /**
     * Sets the volume's transfer function range to [min] and [max].
     * Optionally, the range can be locked by setting [lock].
     */
    fun setTransfer(min: Float, max: Float, lock: Boolean = true) {
        lockTransferRange = false
        trangemin = min
        trangemax = max

        if(lock) {
            lockTransferRange = true
        }
    }

    /**
     * Creates this volume's [Node.OrientedBoundingBox], giving 2cm slack around the edges.
     * The volume's bounding box is calculated from voxel and physical size such that
     * 1 pixel = 1mm in world units.
     */
    override fun generateBoundingBox(): OrientedBoundingBox? {
        val slack = 0.02f
        val min = GLVector(
            -0.5f * sizeX * voxelSizeX * 0.001f - slack,
            -0.5f * sizeY * voxelSizeY * 0.001f - slack,
            -0.5f * sizeZ * voxelSizeZ * 0.001f - slack)
        val max = GLVector(
            1.5f * sizeX * voxelSizeX * 0.001f + slack,
            1.5f * sizeY * voxelSizeY * 0.001f + slack,
            1.5f * sizeZ * voxelSizeZ * 0.001f + slack)

        return OrientedBoundingBox(min, max)
    }

    /**
     * Volume node companion object for static functions.
     */
    companion object {
        val scifio: SCIFIO = SCIFIO()
        val UNSAFE: Unsafe
        val BYTES_OFFSET: Int

        init {
            try {
                val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
                theUnsafe.isAccessible = true
                UNSAFE = theUnsafe.get(null) as Unsafe
                BYTES_OFFSET = UNSAFE.arrayBaseOffset(ByteArray::class.java)

            } catch (e: Exception) {
                throw AssertionError(e)
            }

        }

        /**
         * Generates a procedural volume based on the open simplex noise algorithm, with [size]^3 voxels.
         * [radius] sets the blob radius, while [shift] can be used to move through the continuous noise
         * volume, essentially offsetting the volume by the value given. [intoBuffer] can be used to
         * funnel the data into a pre-existing buffer, otherwise one will be allocated. [seed] can be
         * used to choose a seed for the PRNG.
         *
         * Returns the newly-allocated [ByteBuffer], or the one given in [intoBuffer], set to position 0.
         */
        @JvmStatic fun generateProceduralVolume(size: Long, radius: Float = 0.0f,
                                     seed: Long = Random.randomFromRange(0.0f, 133333337.0f).toLong(),
                                     shift: GLVector = GLVector.getNullVector(3),
                                     intoBuffer: ByteBuffer? = null, use16bit: Boolean = false): ByteBuffer {
            val f = 3.0f / size
            val center = size / 2.0f + 0.5f
            val noise = OpenSimplexNoise(seed)
            val (range, bytesPerVoxel) = if(use16bit) {
                65535 to 2
            } else {
                255 to 1
            }
            val byteSize = (size*size*size*bytesPerVoxel).toInt()

            val buffer = intoBuffer ?: memAlloc(byteSize * bytesPerVoxel)

            (0 until byteSize/bytesPerVoxel).chunked(byteSize/4).forEachParallel { subList ->
                subList.forEach {
                    val x = it.rem(size)
                    val y = (it / size).rem(size)
                    val z = it / (size * size)

                    val dx = center - x
                    val dy = center - y
                    val dz = center - z

                    val offset = abs(noise.random3D((x + shift.x()) * f, (y + shift.y()) * f, (z + shift.z()) * f))
                    val d = sqrt(dx * dx + dy * dy + dz * dz) / size

                    val result = if(radius > Math.ulp(1.0f)) {
                        if(d - offset < radius) { ((d-offset)*range).toShort() } else { 0 }
                    } else {
                        ((d - offset) * range).toShort()
                    }

                    if(use16bit) {
                        buffer.asShortBuffer().put(it, result)
                    } else {
                        buffer.put(it, result.toByte())
                    }
                }
            }

            return buffer
        }
    }
}
