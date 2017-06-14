package graphics.scenery.volumes

import cleargl.GLTypeEnum
import cleargl.GLVector
import coremem.enums.NativeTypeEnum
import graphics.scenery.*
import graphics.scenery.backends.ShaderPreference
import org.lwjgl.system.MemoryUtil.memAlloc
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
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class Volume(var autosetProperties: Boolean = true) : Mesh("DirectVolume") {
    data class VolumeDescriptor(val path: Path,
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
    @ShaderProperty var trangemax = 0.01f //for histones
    //@ShaderProperty var trangemax = 0.01f // for droso-autopilot

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
                readFrom(Paths.get(field), true)
            }
        }

    val parameters = VolumeRenderingParameters()

    var hub: Hub? = null

    init {
        // fake geometry
        val b = Box(GLVector(1.0f, 1.0f, 1.0f))
        this.vertices = BufferUtils.allocateFloat(12)
        this.vertices.put(-1.0f)
        this.vertices.put(-1.0f)
        this.vertices.put(0.0f)

        this.vertices.put(1.0f)
        this.vertices.put(-1.0f)
        this.vertices.put(0.0f)

        this.vertices.put(1.0f)
        this.vertices.put(1.0f)
        this.vertices.put(0.0f)

        this.vertices.put(-1.0f)
        this.vertices.put(1.0f)
        this.vertices.put(0.0f)

        this.vertices.flip()
        this.normals = BufferUtils.allocateFloat(12)
        this.normals.put(1.0f)
        this.normals.put(0.0f)
        this.normals.put(0.0f)
        this.normals.put(0.0f)
        this.normals.put(1.0f)
        this.normals.put(0.0f)
        this.normals.put(0.0f)
        this.normals.put(0.0f)
        this.normals.put(1.0f)
        this.normals.put(0.0f)
        this.normals.put(0.0f)
        this.normals.put(1.0f)
        this.normals.flip()
//        this.vertices = b.vertices
//        this.normals = b.normals
//        this.texcoords = b.texcoords
//        this.indices = b.indices
        this.texcoords = BufferUtils.allocateFloat(8)
        this.texcoords.put(0.0f)
        this.texcoords.put(0.0f)

        this.texcoords.put(1.0f)
        this.texcoords.put(0.0f)

        this.texcoords.put(1.0f)
        this.texcoords.put(1.0f)

        this.texcoords.put(0.0f)
        this.texcoords.put(1.0f)
        this.texcoords.flip()

        this.vertexSize = 3
        this.texcoordSize = 2
        this.indices = BufferUtils.allocateInt(6)
        this.indices.put(0)
        this.indices.put(1)
        this.indices.put(2)
        this.indices.put(0)
        this.indices.put(2)
        this.indices.put(3)
        this.indices.flip()
        this.geometryType = GeometryType.TRIANGLE_STRIP

        this.material.transparent = true

        metadata.put(
            "ShaderPreference",
            ShaderPreference(
                arrayListOf("Volume.vert", "Volume.frag"),
                HashMap<String, String>(),
                arrayListOf("DeferredShadingRenderer")))
    }

    fun preload(file: Path) {
        val id = file.fileName.toString()

        val infoFile = file.resolveSibling("stacks" + ".info")

        //val dimensions = Files.lines(infoFile).toList().first().split(",").map { it.toLong() }.toTypedArray()

        val lines = Files.lines(infoFile).toList()

        logger.info("reading stacks.info (${lines.joinToString()}) (${lines.size} lines)")
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

        logger.info("setting voxelsize to $voxelSizeX x $voxelSizeY x $voxelSizeZ")
        logger.info("setting min max to ${this.trangemin}, ${this.trangemax} ")
        logger.info("setting alpha blending to ${this.alpha_blending}")
        logger.info("setting dim to ${sizeX}, ${sizeY}, ${sizeZ}")


        if (volumes.containsKey(id)) {
            logger.info("$id is already in cache")
        } else {
            logger.info("Preloading $id from disk")
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

    fun readFrom(file: Path, replace: Boolean = false): String {
        val infoFile = file.resolveSibling("stacks" + ".info")

        //val dimensions = Files.lines(infoFile).toList().first().split(",").map { it.toLong() }.toTypedArray()

        val lines = Files.lines(infoFile).toList()

        logger.info("reading stacks.info (${lines.joinToString()}) (${lines.size} lines)")
        val dimensions = lines.get(0).split(",").map { it.toLong() }.toTypedArray()
        logger.info("setting dim to ${dimensions.joinToString()}")

        if (autosetProperties) {
            this.trangemax = 0.03f
            voxelSizeX = 1.0f
            voxelSizeY = 1.0f
            voxelSizeZ = 1.0f
        }
        sizeX = dimensions[0].toInt()
        sizeY = dimensions[1].toInt()
        sizeZ = dimensions[2].toInt()



        logger.info("setting voxelsize to $voxelSizeX x $voxelSizeY x $voxelSizeZ")
        logger.info("setting min max to ${this.trangemin}, ${this.trangemax} ")
        logger.info("setting alpha blending to ${this.alpha_blending}")
        logger.info("setting dim to ${sizeX}, ${sizeY}, ${sizeZ}")

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

//            if (replace) {
//                volumes.clear()
//            }

            val descriptor = VolumeDescriptor(
                file,
                dimensions[0], dimensions[1], dimensions[2],
                NativeTypeEnum.UnsignedInt, 2, data = imageData
            )

            volumes.put(id, descriptor)
            descriptor
        }

        val dim = GLVector(dimensions[0].toFloat(), dimensions[1].toFloat(), dimensions[2].toFloat())
        val gtv = GenericTexture("volume", dim,
            -1, GLTypeEnum.UnsignedInt, vol.data, false, false)

        if (this.lock.tryLock()) {
            this.material.textures.put("3D-volume", "fromBuffer:volume")
            this.material.transferTextures.put("volume", gtv)?.let {
                //                if (replace) {
//                    memFree(it.contents)
//                }
            }
//            this.material.textures.put("normal", this.javaClass.getResource("colormap-viridis.png").file)
            this.material.textures.put("normal", "m:/colormaps/colormap-hot.png")
            this.material.needsTextureReload = true

            this.lock.unlock()
        }
        return id
    }

    fun render() {
    }


    fun purge(id: String) {
        volumes.remove(id)
    }
}
