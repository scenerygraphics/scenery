package graphics.scenery.volumes

import graphics.scenery.RichNode
import graphics.scenery.Scene
import graphics.scenery.ShaderMaterial
import graphics.scenery.ShaderProperty
import graphics.scenery.backends.Renderer
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import net.imglib2.type.numeric.integer.IntType
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

class VolumeHistogram(val volume: Volume, data: ByteBuffer): RichNode() {

    var dataType = UnsignedByteType()
    private val buffer: ByteBuffer

    val histogramTexture: Texture
    var result: Texture? = null

    @ShaderProperty
    var numBins = 100

    @ShaderProperty
    val volumeIs8Bit: Boolean = true

    @ShaderProperty
    val numVoxels: Int

    @ShaderProperty
    val maxDisplayVal: Float

    @ShaderProperty
    val minDisplayVal: Float

    init {
        this.name = "compute node"

        numVoxels = volume.getDimensions().z

        maxDisplayVal = volume.maxDisplayRange
        minDisplayVal = volume.minDisplayRange

        buffer = MemoryUtil.memCalloc(numBins * 4 * 2 * 2)

        histogramTexture = Texture(
            Vector3i(numBins, 2, 2),
            1,
            contents = buffer,
            usageType = hashSetOf(
                Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture
            ),
            type = IntType(),
            mipmap = false,
            minFilter = Texture.FilteringMode.NearestNeighbour,
            maxFilter = Texture.FilteringMode.NearestNeighbour
        )

        this.setMaterial(ShaderMaterial.fromFiles(this::class.java, "ComputeHistogram.comp")) {
            textures["Volume8Bit"] = Texture(
                Vector3i(volume.getDimensions()),
                1,
                contents = data,
                usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
                type = dataType,
                mipmap = false,
                minFilter = Texture.FilteringMode.NearestNeighbour,
                maxFilter = Texture.FilteringMode.NearestNeighbour
            )

            textures["Volume16Bit"] = Texture(
                Vector3i(1),
                1,
                contents = MemoryUtil.memCalloc(2),
                usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
                type = UnsignedShortType(),
                mipmap = false,
                minFilter = Texture.FilteringMode.NearestNeighbour,
                maxFilter = Texture.FilteringMode.NearestNeighbour
            )


            textures["Histogram"] = histogramTexture
        }

        this.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(volume.getDimensions().x, volume.getDimensions().y, 1),
            invocationType = InvocationType.Once
        )
    }

    fun fetchHistogram(scene: Scene, renderer: Renderer): ByteBuffer {

        var byteBuffer: ByteBuffer? = null

        renderer.requestTexture(histogramTexture){
            byteBuffer = it.contents!!
        }

        while (byteBuffer == null) {
            Thread.sleep(100)
        }

        scene.removeChild(this)

        return byteBuffer!!
    }

    companion object {
        fun generateHistogram(volume: Volume, data: ByteBuffer, scene: Scene): VolumeHistogram {
            val volumeHistogram = VolumeHistogram(volume, data)
            scene.addChild(volumeHistogram)
            return volumeHistogram
        }
    }

}
