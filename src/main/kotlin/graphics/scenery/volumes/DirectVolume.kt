package graphics.scenery.volumes

import clearcl.*
import clearcl.backend.ClearCLBackends
import clearcl.backend.jocl.ClearCLBackendJOCL
import clearcl.enums.*
import cleargl.GLTypeEnum
import cleargl.GLVector
import coremem.enums.NativeTypeEnum
import coremem.offheap.OffHeapMemory
import graphics.scenery.*
import graphics.scenery.backends.ShaderPreference
import graphics.scenery.backends.vulkan.VulkanTexture
import org.lwjgl.system.MemoryUtil.memAlloc
import org.lwjgl.system.MemoryUtil.memFree
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import javax.imageio.ImageIO
import kotlin.streams.toList

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class DirectVolume : Mesh("DirectVolume") {
    data class VolumeDescriptor(val path: Path,
                                val width: Long,
                                val height: Long,
                                val depth: Long,
                                val dataType: NativeTypeEnum,
                                val bytesPerVoxel: Int)

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

    val logger: Logger = LoggerFactory.getLogger("Volume")

    val volumes = LinkedHashMap<String, VolumeDescriptor>()

    val parameters = VolumeRenderingParameters()

    var hub: Hub? = null
    var context: ClearCLContext? = null

    init {
        // fake geometry
        val b = Box(GLVector(1.0f, 1.0f, 1.0f))
        this.vertices = b.vertices
        this.normals = b.normals
        this.texcoords = b.texcoords
        this.indices = b.indices
        this.vertexSize = 3
        this.texcoordSize = 2
        this.geometryType = b.geometryType

        this.material.doubleSided = true

        this.material.transparent = true

        metadata.put(
            "ShaderPreference",
            ShaderPreference(
                arrayListOf("DefaultForward.vert", "DirectVolume.frag"),
                HashMap<String, String>(),
                arrayListOf("DeferredShadingRenderer")))
    }

    fun readFrom(file: Path, replace: Boolean = false): String {

        val infoFile = file.resolveSibling("stacks" + ".info")
        val dimensions = Files.lines(infoFile).toList().first().split(",").map { it.toLong() }.toTypedArray()

        val buffer = ByteArray(1024*1024)
        val stream = FileInputStream(file.toFile())
        val imageData: ByteBuffer = memAlloc((2 * dimensions[0] * dimensions[1] * dimensions[2]).toInt())

        logger.info("${file.fileName}: Allocated ${imageData.capacity()} bytes for UINT16 image of ${dimensions.joinToString("x")}")

        val start = System.nanoTime()
        var bytesRead = stream.read(buffer, 0, buffer.size)
        while(bytesRead > -1) {
            imageData.put(buffer, 0, bytesRead)
            bytesRead = stream.read(buffer, 0, buffer.size)
        }
        val duration = (System.nanoTime() - start)/10e5
        logger.info("Reading took $duration ms")

        imageData.flip()

        if (replace) {
            volumes.clear()
        }

        val id = file.fileName.toString()

        volumes.put(id, VolumeDescriptor(
            file,
            dimensions[0], dimensions[1], dimensions[2],
            NativeTypeEnum.UnsignedInt, 2
        ))

        val dim = GLVector(dimensions[0].toFloat(), dimensions[1].toFloat(), dimensions[2].toFloat())
        val gtv = GenericTexture("volume", dim,
            -1, GLTypeEnum.UnsignedInt, imageData, false, false)

        this.material.textures.put("3D-volume", "fromBuffer:volume")
        this.material.transferTextures.put("volume", gtv)?.let {
            if(replace) {
                memFree(it.contents)
            }
        }
        this.material.textures.put("normal", this.javaClass.getResource("colormap-viridis.png").file)
        this.material.needsTextureReload = true

        this.scale = dim*0.01f

        return id
    }

    fun render() {
    }


    fun purge(id: String) {
        volumes.remove(id)
    }
}
