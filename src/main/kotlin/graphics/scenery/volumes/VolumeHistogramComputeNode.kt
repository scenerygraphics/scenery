package graphics.scenery.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import kotlinx.coroutines.runBlocking
import net.imglib2.type.numeric.integer.IntType
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.jfree.data.statistics.SimpleHistogramBin
import org.jfree.data.statistics.SimpleHistogramDataset
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.IntBuffer
import kotlin.math.abs
import kotlin.math.max

/**
 * A compute node to calculate a histogram of a volume on the gpu. To use call [VolumeHistogramComputeNode.generateHistogramComputeNode].
 *
 * @author Aryaman Gupta <aryaman.gupta@tu-dresden.de>
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 */
class VolumeHistogramComputeNode(
    displayRange: Pair<Float, Float>,
    dimensions: Vector3i,
    bytesPerVoxel: Int,
    data: ByteBuffer
) : RichNode() {

    var dataType = UnsignedByteType()
    private val buffer: ByteBuffer

    val histogramTexture: Texture
    var result: Texture? = null

    @ShaderProperty
    var numBins = 100

    @ShaderProperty
    val volumeIs8Bit: Boolean = when (bytesPerVoxel) {
        1 -> true
        2 -> false
        else -> throw IllegalArgumentException("only 8 and 16 bit data supported for histograms")
    }

    @ShaderProperty
    val numVoxels: Int

    @ShaderProperty
    val maxDisplayVal: Float

    @ShaderProperty
    val minDisplayVal: Float

    init {
        this.name = "compute node"

        numVoxels = dimensions.z

        maxDisplayVal = displayRange.second
        minDisplayVal = displayRange.first

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
            textures["Volume8Bit"] = if (volumeIs8Bit) {
                Texture(
                    Vector3i(dimensions),
                    1,
                    contents = data,
                    usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
                    type = dataType,
                    mipmap = false,
                    minFilter = Texture.FilteringMode.NearestNeighbour,
                    maxFilter = Texture.FilteringMode.NearestNeighbour
                )
            } else {
                Texture()
            }


            textures["Volume16Bit"] = if (!volumeIs8Bit) {
                Texture(
                    Vector3i(dimensions),
                    1,
                    contents = data,
                    usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
                    type = UnsignedShortType(),
                    mipmap = false,
                    minFilter = Texture.FilteringMode.NearestNeighbour,
                    maxFilter = Texture.FilteringMode.NearestNeighbour
                )
            } else {
                Texture()
            }


            textures["Histogram"] = histogramTexture
        }

        this.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(dimensions.x, dimensions.y, 1),
            invocationType = InvocationType.Once
        )

    }

    /**
     * waits a bit and then fetches the histogram from the node.
     * @return list of number of items in the histogram bins, sorted by ascending bin label
     */
    fun fetchHistogram(scene: Scene, renderer: Renderer): List<Int> {

        var buf: IntBuffer? = null
        Thread.sleep(500) // dunno why this is required. Probably some scenery update stuff
        runBlocking {
            renderer.requestTexture(histogramTexture) {
                buf = it.contents!!.asIntBuffer()
            }.join()
        }
        scene.removeChild(this)

        val list = mutableListOf<Int>()
        buf?.limit(numBins)
        buf?.let {
            while (it.hasRemaining()) {
                list.add(it.get())
            }
        }

        return list
    }

    companion object {

        fun generateHistogram(volume: BufferedVolume, volumeHistogramData: SimpleHistogramDataset): Int {
            return generateHistogram(
                volume.minDisplayRange to volume.maxDisplayRange,
                volume.getDimensions(),
                volume.bytesPerVoxel,
                volume.getScene()!!,
                volume.timepoints?.get(volume.currentTimepoint)!!.contents,
                volume.volumeManager.hub!!.get<Renderer>(
                    SceneryElement.Renderer
                )!!,
                volumeHistogramData
            )
        }

        /**
         * Generates a histogram using GPU acceleration via [VolumeHistogramComputeNode].
         */
        fun generateHistogram(
            displayRange: Pair<Float, Float>,
            dimensions: Vector3i,
            bytesPerVoxel: Int,
            scene: Scene,
            data: ByteBuffer,
            renderer: Renderer,
            volumeHistogramData: SimpleHistogramDataset
        ): Int {

            val volumeHistogramComputeNode = VolumeHistogramComputeNode(displayRange, dimensions, bytesPerVoxel, data)
            scene.addChild(volumeHistogramComputeNode)

            val histogram = volumeHistogramComputeNode.fetchHistogram(scene, renderer)

            val displayRangeSpan = abs(displayRange.second - displayRange.first)
            val binSize = displayRangeSpan / volumeHistogramComputeNode.numBins
            val minDisplayRange = displayRange.first.toDouble()

            var max = 0
            histogram.forEachIndexed { index, value ->
                val bin = SimpleHistogramBin(
                    minDisplayRange + index * binSize,
                    minDisplayRange + (index + 1) * binSize,
                    true,
                    false
                )
                bin.itemCount = value
                volumeHistogramData.addBin(bin)
                max = max(max, value)
            }
            return max
        }
    }
}
