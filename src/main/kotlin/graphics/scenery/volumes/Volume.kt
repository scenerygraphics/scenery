package graphics.scenery.volumes

import cleargl.GLTypeEnum
import cleargl.GLVector
import coremem.enums.NativeTypeEnum
import graphics.scenery.*
import graphics.scenery.numerics.OpenSimplexNoise
import graphics.scenery.numerics.Random
import graphics.scenery.utils.forEachAsync
import graphics.scenery.utils.forEachParallel
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
import kotlin.streams.toList




/**
 * Volume Rendering Node for scenery
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Martin Weigert <mweigert@mpi-cbg.de>
 */
class Volume(var autosetProperties: Boolean = true) : Mesh("Volume") {
    data class VolumeDescriptor(val path: Path?,
                                val width: Long,
                                val height: Long,
                                val depth: Long,
                                val dataType: NativeTypeEnum,
                                val bytesPerVoxel: Int,
                                val data: ByteBuffer)

    data class VolumeRenderingParameters(
        val boundingBox: FloatArray = floatArrayOf(-1.0f, 1.0f, -1.0f, 1.0f, -1.0f, 1.0f),
        val gamma: Float = 2.2f,
        val alpha: Float = -1.0f,
        val brightness: Float = 1.0f,
        val maxSteps: Int = 128,
        val dithering: Float = 0.0f,
        val transferFunction: TransferFunction = TransferFunction()
    )

    data class TransferFunction(
        var min: Float = 0.0f,
        var max: Float = 1.0f,
        var function: FloatArray = floatArrayOf()
    )

    class Histogram<T : Comparable<T>>(histogramSize: Int) {
        val bins: HashMap<T, Long> = HashMap(histogramSize)

        fun add(value: T) {
            bins.put(value, (bins.get(value) ?: 0L) + 1L)
        }

        fun min(): T = bins.keys.minBy { it } ?: (0 as T)
        fun max(): T = bins.keys.maxBy { it } ?: (0 as T)
    }

    val boxwidth = 1.0f

    @ShaderProperty var trangemin = 0.00f
    @ShaderProperty var trangemax = 1.0f

    @ShaderProperty var boxMin_x = -boxwidth
    @ShaderProperty var boxMin_y = -boxwidth
    @ShaderProperty var boxMin_z = -boxwidth

    @ShaderProperty var boxMax_x = boxwidth
    @ShaderProperty var boxMax_y = boxwidth
    @ShaderProperty var boxMax_z = boxwidth

    @ShaderProperty var maxsteps = 128
    @ShaderProperty var alpha_blending = 0.06f
    @ShaderProperty var gamma = 1.0f

    @ShaderProperty var sizeX = 256
    @ShaderProperty var sizeY = 256
    @ShaderProperty var sizeZ = 256
    @ShaderProperty var voxelSizeX = 1.0f
    @ShaderProperty var voxelSizeY = 1.0f
    @ShaderProperty var voxelSizeZ = 1.0f

    var colormaps = HashMap<String, String>()

    var colormap: String = "viridis"
        set(name) {
            colormaps.get(name)?.let { cm ->
                field = name
                this@Volume.material.textures.put("normal", cm)
                this@Volume.material.needsTextureReload = true
            }
        }

    @Transient val volumes = ConcurrentHashMap<String, VolumeDescriptor>()

    var currentVolume: String = ""
        set(value) {
            field = value
            if (value != "") {
                readFromRaw(Paths.get(field), true)
            }
        }

    val parameters = VolumeRenderingParameters()

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

        material.blending.transparent = true
        material.blending.sourceColorBlendFactor = Blending.BlendFactor.One
        material.blending.destinationColorBlendFactor = Blending.BlendFactor.OneMinusSrcAlpha
        material.blending.sourceAlphaBlendFactor = Blending.BlendFactor.One
        material.blending.destinationAlphaBlendFactor = Blending.BlendFactor.OneMinusSrcAlpha
        material.blending.colorBlending = Blending.BlendOp.add
        material.blending.alphaBlending = Blending.BlendOp.add

        this.useClassDerivedShader = true
        this.material.cullingMode = Material.CullingMode.None

        colormaps.put("grays", this.javaClass.getResource("colormap-grays.png").file)
        colormaps.put("hot", this.javaClass.getResource("colormap-hot.png").file)
        colormaps.put("jet", this.javaClass.getResource("colormap-jet.png").file)
        colormaps.put("plasma", this.javaClass.getResource("colormap-plasma.png").file)
        colormaps.put("viridis", this.javaClass.getResource("colormap-viridis.png").file)

        assignEmptyVolumeTexture()
    }

    fun preloadRawFromPath(file: Path) {
        val id = file.fileName.toString()

        val infoFile = file.resolveSibling("stacks" + ".info")

        val lines = Files.lines(infoFile).toList()

        logger.debug("reading stacks.info (${lines.joinToString()}) (${lines.size} lines)")
        val dimensions = lines.get(0).split(",").map { it.toLong() }.toTypedArray()


        if (autosetProperties) {
            trangemax = 0.03f
            voxelSizeX = 1.0f
            voxelSizeY = 1.0f
            voxelSizeZ = 1.0f
        }


        sizeX = dimensions[0].toInt()
        sizeY = dimensions[1].toInt()
        sizeZ = dimensions[2].toInt()

        logger.debug("setting voxelsize to $voxelSizeX x $voxelSizeY x $voxelSizeZ")
        logger.debug("setting min max to ${this.trangemin}, ${this.trangemax} ")
        logger.debug("setting alpha blending to ${this.alpha_blending}")
        logger.debug("setting dim to ${sizeX}, ${sizeY}, ${sizeZ}")


        if (volumes.containsKey(id)) {
            logger.debug("$id is already in cache")
        } else {
            logger.debug("Preloading $id from disk")
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
                NativeTypeEnum.UnsignedInt, 2, data = imageData
            )

            volumes.put(id, descriptor)
        }
    }

    fun readFromBuffer(id: String, buffer: ByteBuffer,
                       x: Long, y: Long, z: Long,
                       voxelX: Float, voxelY: Float, voxelZ: Float,
                       dataType: NativeTypeEnum = NativeTypeEnum.UnsignedInt, bytesPerVoxel: Int = 2) {
        if (autosetProperties) {
            voxelSizeX = voxelX
            voxelSizeY = voxelY
            voxelSizeZ = voxelZ
        }

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
            assignVolumeTexture( longArrayOf( x, y, z ), vol, true)
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

    fun readFrom(file: Path, replace: Boolean = false): String {
        if(file.normalize().toString().endsWith("raw")) {
            return readFromRaw(file, replace)
        }

        val reader = scifio.initializer().initializeReader(file.normalize().toString())

        if(autosetProperties) {
            voxelSizeX = 1.0f
            voxelSizeY = 1.0f
            voxelSizeZ = 1.0f
        }

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

            if(reader.openPlane(0, 0).imageMetadata.isLittleEndian) {
                logger.info("Volume is little endian")
                (0 until reader.getPlaneCount(0)).forEach { plane ->
                    imageData.put(reader.openPlane(0, plane).bytes)
                }
            } else {
                logger.info("Volume is big endian")
                (0 until reader.getPlaneCount(0)).forEach { plane ->
                    imageData.put(swapEndianUnsafe(reader.openPlane(0, plane).bytes))
                }
            }

            val duration = (System.nanoTime() - start) / 10e5
            logger.info("Reading took $duration ms")

            imageData.flip()

            val descriptor = VolumeDescriptor(
                file,
                sizeX.toLong(), sizeY.toLong(), sizeZ.toLong(),
                dataType, bytesPerVoxel, data = imageData
            )

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

            volumes.put(id, descriptor)
            descriptor
        }

        assignVolumeTexture(longArrayOf(sizeX.toLong(), sizeY.toLong(), sizeZ.toLong()), vol, replace)

        return id
    }

    fun readFromRaw(file: Path, replace: Boolean = false, autorange: Boolean = true, cache: Boolean = true): String {
        val infoFile = file.resolveSibling("stacks" + ".info")

        val lines = Files.lines(infoFile).toList()

        logger.debug("reading stacks.info (${lines.joinToString()}) (${lines.size} lines)")
        val dimensions = lines.get(0).split(",").map { it.toLong() }.toTypedArray()
        logger.debug("setting dim to ${dimensions.joinToString()}")

        if (autosetProperties) {
            this.trangemax = 0.03f
            voxelSizeX = 1.0f
            voxelSizeY = 1.0f
            voxelSizeZ = 1.0f
        }

        sizeX = dimensions[0].toInt()
        sizeY = dimensions[1].toInt()
        sizeZ = dimensions[2].toInt()

        logger.debug("setting voxelsize to $voxelSizeX x $voxelSizeY x $voxelSizeZ")
        logger.debug("setting min max to ${this.trangemin}, ${this.trangemax} ")
        logger.debug("setting alpha blending to ${this.alpha_blending}")
        logger.debug("setting dim to ${sizeX}, ${sizeY}, ${sizeZ}")

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

    private fun NativeTypeEnum.toGLType() =
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

    private fun assignEmptyVolumeTexture() {
        val emptyBuffer = BufferUtils.allocateByteAndPut(byteArrayOf(0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                                     0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0))
        val dim = GLVector(2.0f, 2.0f, 2.0f)
        val gtv = GenericTexture("volume", dim, 1, GLTypeEnum.UnsignedByte, emptyBuffer, false, false, normalized = false)

        this.material.transferTextures.put("volume", gtv)
        this.material.textures.put("3D-volume", "fromBuffer:volume")
        this.material.textures.put("normal", colormaps.values.first())
    }

    private val deallocations = ArrayDeque<ByteBuffer>()

    private fun assignVolumeTexture(dimensions: LongArray, descriptor: VolumeDescriptor, replace: Boolean) {
        while(deallocations.size > 20) {
            MemoryUtil.memFree(deallocations.pollLast())
        }

        val dim = GLVector(dimensions[0].toFloat(), dimensions[1].toFloat(), dimensions[2].toFloat())
        val gtv = GenericTexture("volume", dim,
            1, descriptor.dataType.toGLType(), descriptor.data, false, false, normalized = false)

//        if (this.lock.tryLock()) {
            logger.debug("$name: Assigning volume texture")
            this.material.transferTextures.put("volume", gtv)?.let {
                if (replace) {
                    deallocations.add(it.contents)
                }
            }
            this.material.textures.put("3D-volume", "fromBuffer:volume")
            this.material.textures.put("normal", colormaps[colormap]!!)
            this.material.needsTextureReload = true

//            this.lock.unlock()
//        }
    }

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

        fun generateProceduralVolume(size: Long, radius: Float = 0.0f,
                                     seed: Long = Random.randomFromRange(0.0f, 133333337.0f).toLong(),
                                     shift: GLVector = GLVector.getNullVector(3),
                                     intoBuffer: ByteBuffer? = null): ByteBuffer {
            val byteSize = (size*size*size).toInt()
            val f = 3.0f / size
            val center = size / 2.0f + 0.5f
            val noise = OpenSimplexNoise(seed)

            val buffer = intoBuffer ?: memAlloc(byteSize)

            (0 until byteSize).chunked(byteSize/4).forEachParallel { subList ->
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
                        if(d - offset < radius) { ((d-offset)*255).toByte() } else { 0.toByte() }
                    } else {
                        ((d - offset) * 255).toByte()
                    }

                    buffer.put(it, result)
                }
            }

            return buffer
        }
    }
}
