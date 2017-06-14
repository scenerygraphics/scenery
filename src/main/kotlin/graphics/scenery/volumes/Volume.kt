package graphics.scenery.volumes

import cleargl.GLTypeEnum
import cleargl.GLVector
import coremem.enums.NativeTypeEnum
import graphics.scenery.*
import graphics.scenery.backends.ShaderPreference
import org.lwjgl.system.MemoryUtil.memAlloc
import org.lwjgl.system.MemoryUtil.memFree
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.toList

/**
 * Volume Rendering Node for scenery
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Martin Weigert <mweigert@mpi-cbg.de>
 */
class Volume(var autosetProperties: Boolean = true) : Mesh("DirectVolume") {
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

    val boxwidth = 1.0f

    @ShaderProperty var trangemin = 0.00f
    @ShaderProperty var trangemax = 1.0f

    @ShaderProperty var boxMin_x = -boxwidth
    @ShaderProperty var boxMin_y = -boxwidth
    @ShaderProperty var boxMin_z = -boxwidth

    @ShaderProperty var boxMax_x = boxwidth
    @ShaderProperty var boxMax_y = boxwidth
    @ShaderProperty var boxMax_z = boxwidth

    @ShaderProperty var maxsteps = 256
    @ShaderProperty var alpha_blending = 0.06f
    @ShaderProperty var gamma = 1.0f

    @ShaderProperty var sizeX = 256
    @ShaderProperty var sizeY = 256
    @ShaderProperty var sizeZ = 256
    @ShaderProperty var voxelSizeX = 1.0f
    @ShaderProperty var voxelSizeY = 1.0f
    @ShaderProperty var voxelSizeZ = 1.0f

    val logger: Logger = LoggerFactory.getLogger("Volume")

    @Transient val volumes = ConcurrentHashMap<String, VolumeDescriptor>()

    var currentVolume: String = ""
        set(value) {
            field = value
            if (value != "") {
                readFromRaw(Paths.get(field), true)
            }
        }

    val parameters = VolumeRenderingParameters()

    var hub: Hub? = null

    init {
        // fake geometry
        val b = Box(GLVector(1.0f, 1.0f, 1.0f))
        this.vertices = BufferUtils.allocateFloatAndPut(
            floatArrayOf(
                -1.0f, -1.0f, 0.0f,
                1.0f, -1.0f, 0.0f,
                1.0f, 1.0f, 0.0f,
                -1.0f, 1.0f, 0.0f))
        this.vertices.flip()

        this.normals = BufferUtils.allocateFloatAndPut(
            floatArrayOf(
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f))
        this.normals.flip()

        this.texcoords = BufferUtils.allocateFloatAndPut(
            floatArrayOf(
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f))
        this.texcoords.flip()

        this.indices = BufferUtils.allocateIntAndPut(
            intArrayOf(0, 1, 2, 0, 2, 3))
        this.indices.flip()

        this.geometryType = GeometryType.TRIANGLE_STRIP
        this.vertexSize = 3
        this.texcoordSize = 2

        this.material.transparent = true

        metadata.put(
            "ShaderPreference",
            ShaderPreference(
                arrayListOf("Volume.vert", "Volume.frag"),
                HashMap<String, String>(),
                arrayListOf("DeferredShadingRenderer")))
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
    }

    fun readFromRaw(file: Path, replace: Boolean = false): String {
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

        val vol = if (volumes.containsKey(id)) {
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
                NativeTypeEnum.UnsignedInt, 2, data = imageData
            )

            volumes.put(id, descriptor)
            descriptor
        }

        assignVolumeTexture(dimensions.toLongArray(), vol, replace)

        return id
    }

    private fun NativeTypeEnum.toGLType() =
        when (this) {
            NativeTypeEnum.UnsignedInt -> GLTypeEnum.UnsignedInt
            NativeTypeEnum.Byte -> TODO()
            NativeTypeEnum.UnsignedByte -> TODO()
            NativeTypeEnum.Short -> TODO()
            NativeTypeEnum.UnsignedShort -> TODO()
            NativeTypeEnum.Int -> TODO()
            NativeTypeEnum.Long -> TODO()
            NativeTypeEnum.UnsignedLong -> TODO()
            NativeTypeEnum.HalfFloat -> TODO()
            NativeTypeEnum.Float -> TODO()
            NativeTypeEnum.Double -> TODO()
        }

    private fun assignVolumeTexture(dimensions: LongArray, descriptor: VolumeDescriptor, replace: Boolean) {
        val dim = GLVector(dimensions[0].toFloat(), dimensions[1].toFloat(), dimensions[2].toFloat())
        val gtv = GenericTexture("volume", dim,
            -1, descriptor.dataType.toGLType(), descriptor.data, false, false)

        if (this.lock.tryLock()) {
            this.material.textures.put("3D-volume", "fromBuffer:volume")
            this.material.transferTextures.put("volume", gtv)?.let {
                if (replace) {
                    memFree(it.contents)
                }
            }

            this.material.textures.put("normal", "m:/colormaps/colormap-hot.png")
            this.material.needsTextureReload = true

            this.lock.unlock()
        }
    }

    fun render() {
    }

    fun purge(id: String) {
        volumes.remove(id)
    }
}
