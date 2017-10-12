package graphics.scenery.volumes

import clearcl.*
import clearcl.backend.jocl.ClearCLBackendJOCL
import clearcl.enums.*
import cleargl.GLTypeEnum
import cleargl.GLVector
import coremem.enums.NativeTypeEnum
import coremem.offheap.OffHeapMemory
import graphics.scenery.GenericTexture
import graphics.scenery.Hub
import graphics.scenery.Mesh
import graphics.scenery.Plane
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.image.BufferedImage
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
@Deprecated("This class is only kept for reference purposes, please use Volume instead", ReplaceWith("Volume"))
class ProxiedVolume : Mesh("Volume") {
    data class VolumeDescriptor(val path: Path,
                                val width: Long,
                                val height: Long,
                                val depth: Long,
                                val dataType: NativeTypeEnum,
                                val bytesPerVoxel: Int,
                                val buffer: OffHeapMemory,
                                val image: ClearCLImage?)

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

    private val logger: Logger = LoggerFactory.getLogger("Volume")

    val volumes = LinkedHashMap<String, VolumeDescriptor>()
    val kernelNames = arrayListOf(
        "VolumeRenderer.cl".to("maxproj_render"),
        "VolumeRenderer.cl".to("isosurface_render"))
    val kernels = HashMap<String, ClearCLKernel?>()

    val parameters = VolumeRenderingParameters()

    val defaultKernel = "maxproj_render"
    var hub: Hub? = null
    var context: ClearCLContext? = null
    var outputBuffer: ClearCLBuffer? = null
    var textureBuffer: ByteBuffer? = null
    var projectionBuffer: ClearCLBuffer? = null
    var worldBuffer: ClearCLBuffer? = null

    var offheapBuffers = arrayOf(OffHeapMemory.allocateFloats(16), OffHeapMemory.allocateFloats(16))

    var transferFunction: ClearCLImage? = null

    init {
        // fake geometry
        val b = Plane(GLVector(1.0f, 1.0f, 1.0f))
        this.vertices = b.vertices
        this.normals = b.normals
        this.texcoords = b.texcoords
        this.indices = b.indices
        this.vertexSize = 3
        this.texcoordSize = 2
        this.geometryType = b.geometryType
        this.isBillboard = true

        val ccl = ClearCL(ClearCLBackendJOCL())
        val device = ccl.bestGPUDevice
        context = device.createContext()
        textureBuffer = ByteBuffer.allocateDirect(1024 * 1024 * 4 * 4).order(ByteOrder.nativeOrder())

        context?.let { cl ->
            kernels.putAll(kernelNames.map {
                logger.info("Loaded kernel ${it.first}::${it.second}")
                val program = context?.createProgram(ProxiedVolume::class.java, it.first)
                program?.buildAndLog()
                it.second.to(program?.createKernel(it.second))
            }.toMap())

            outputBuffer = context?.createBuffer(
                HostAccessType.ReadOnly,
                KernelAccessType.WriteOnly,
                NativeTypeEnum.Float,
                4 * 1024 * 1024L)

            projectionBuffer = cl.createBuffer(
                MemAllocMode.Best,
                HostAccessType.WriteOnly,
                KernelAccessType.ReadOnly,
                NativeTypeEnum.Float,
                16
            )

            worldBuffer = cl.createBuffer(
                MemAllocMode.Best,
                HostAccessType.WriteOnly,
                KernelAccessType.ReadOnly,
                NativeTypeEnum.Float,
                16
            )

            textureBuffer?.let { texture ->
                this@ProxiedVolume.material.transferTextures.put("volume",
                    GenericTexture("diffuse", GLVector(1024.0f, 1024.0f), 4, GLTypeEnum.Float,
                        texture, false, false))
                this@ProxiedVolume.material.textures.put("diffuse", "fromBuffer:volume")
            }

            val image = ImageIO.read(this.javaClass.getResourceAsStream("transferFunction.png"))

            transferFunction = context?.createImage(
                MemAllocMode.Best,
                HostAccessType.ReadWrite,
                KernelAccessType.ReadWrite, ImageChannelOrder.RGBA, ImageChannelDataType.UnsignedInt32,
                image.width.toLong(), image.height.toLong(), 1L)

            logger.info("${image.width*image.height*4} vs. ${image.getRGBA().size}")

            val mem = OffHeapMemory.allocateInts(image.getRGBA().size.toLong())
            mem.copyFrom(image.getRGBA())

            transferFunction?.readFrom(mem, longArrayOf(0, 0, 0), longArrayOf(image.width.toLong(), image.height.toLong(), 1), true)
        }

        logger.info("Kernels: ${kernels.keys.joinToString(", ")}")
        this.update = {
            render()
        }
    }

    private fun BufferedImage.getRGBA(): IntArray {
        val result = ArrayList<Int>(this.width*this.height*4)
        for (x in 0..this.width - 1) {
            for (y in 0..this.height - 1) {
                val c = Color(this.getRGB(x, y), true)
                result.add(c.red)
                result.add(c.green)
                result.add(c.blue)
                result.add(c.alpha)
            }
        }

        return result.toIntArray()
    }

    fun readFrom(file: Path, replace: Boolean = false): String {

        val buffer = OffHeapMemory.allocateBytes(Files.size(file))
        val infoFile = file.resolveSibling(file.fileName.toString().substringBeforeLast(".") + ".info")
        val dimensions = Files.lines(infoFile).toList().first().split(",").map { it.toLong() }.toTypedArray()

        val fc = FileChannel.open(file, EnumSet.of(StandardOpenOption.READ))
        val bytesRead = buffer.readBytesFromFileChannel(fc, 0, Files.size(file))
        fc.close()

        logger.info("Read ${bytesRead} from $file")

        if (replace) {
            volumes.clear()
        }

        val image = context?.createSingleChannelImage(
            ImageChannelDataType.UnsignedNormalizedInt16,
            dimensions[0], dimensions[1], dimensions[2])

        logger.info("image: $image")

        image?.readFrom(buffer, longArrayOf(0, 0, 0), longArrayOf(dimensions[0], dimensions[1], dimensions[2]), true)

        val id = file.fileName.toString()

        volumes.put(id, VolumeDescriptor(
            file,
            dimensions[0], dimensions[1], dimensions[2],
            NativeTypeEnum.UnsignedInt, 2, buffer, image
        ))

        return id
    }

    fun render() {
        kernels[defaultKernel]?.let { kernel ->
            if (volumes.values.last().image != null) {
                offheapBuffers[0].copyFrom(projection.inverse.transposedFloatArray)
                offheapBuffers[1].copyFrom(modelView.inverse.transposedFloatArray)

                projectionBuffer?.readFrom(offheapBuffers[0], 0, 16, true)
                worldBuffer?.readFrom(offheapBuffers[1], 0, 16, true)

                val clearBeforeRendering = 0
                val phase = 0.0f

                kernel.setArguments(
                    outputBuffer,
                    1024,
                    1024,
                    parameters.brightness,
                    parameters.transferFunction.min,
                    parameters.transferFunction.max,
                    parameters.gamma,
                    parameters.alpha,
                    parameters.maxSteps,
                    parameters.dithering,
                    phase,
                    clearBeforeRendering,
                    parameters.boundingBox[0],
                    parameters.boundingBox[1],
                    parameters.boundingBox[2],
                    parameters.boundingBox[3],
                    parameters.boundingBox[4],
                    parameters.boundingBox[5],
                    transferFunction,
                    projectionBuffer,
                    worldBuffer,
                    volumes.values.last().image)

                kernel.setGlobalSizes(
                    1024, 1024)
                kernel.run(true)

                outputBuffer!!.writeTo(textureBuffer, 0, 4 * 1024 * 1024, true)

                textureBuffer!!.let { texture ->
                    this@ProxiedVolume.material.needsTextureReload = true
                }
            }
        }
    }


    fun purge(id: String) {
        volumes.remove(id)
    }
}
