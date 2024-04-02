package graphics.scenery.volumes.vdi

import bvv.core.shadergen.generate.SegmentTemplate
import bvv.core.shadergen.generate.SegmentType
import graphics.scenery.Hub
import graphics.scenery.RichNode
import graphics.scenery.Scene
import graphics.scenery.ShaderMaterial
import graphics.scenery.backends.Shaders
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.lazyLogger
import graphics.scenery.volumes.VolumeManager
import net.imglib2.type.numeric.integer.IntType
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.jetbrains.annotations.ApiStatus.Experimental
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import kotlin.math.ceil

/**
 * Class for creating and maintaining a [VolumeManager] for generating Volumetric Depth Images (VDIs).
 *
 * @param[hub] The hub to which the VolumeManager is to be attached
 * @param[windowWidth] The rendering resolution along the x (horizontal) axis
 * @param[windowHeight] The rendering resolution along the y (vertical) axis
 * @param[maxSupersegments] The number of supersegments along each list (ray or pixel) in the generated VDI
 * @param[scene] The scene to which the generated VolumeManager will belong
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de> and Wissal Salhi
 */

@Experimental
class VDIVolumeManager (var hub: Hub, val windowWidth: Int, val windowHeight: Int, val maxSupersegments: Int, val scene: Scene)
{
    private val logger by lazyLogger()

    var colorBuffer: ByteBuffer? = null
    var depthBuffer: ByteBuffer? = null
    var gridBuffer: ByteBuffer? = null

    var prefixBuffer: ByteBuffer? = null
    var thresholdBuffer: ByteBuffer? = null
    var numGeneratedBuffer: ByteBuffer? = null

    /**
     * Creates a [VolumeManager] for generating VDIs
     *
     * @param[vdiFull] Boolean variable to indicate whether VDIs are to be generated in regular (i.e. full) resolution or compact.
     *
     * @return The generated [VolumeManager]
     *
     */
    fun createVDIVolumeManager(vdiFull: Boolean = true) : VolumeManager {
        return if (vdiFull)
            vdiFull(windowWidth, windowHeight, maxSupersegments, scene, hub)
        else
            vdiCompact(windowWidth, windowHeight, maxSupersegments, scene, hub)
    }

    private fun instantiateVolumeManager(raycastShader: String, accumulateShader: String, hub: Hub): VolumeManager {
        return VolumeManager(
            hub, useCompute = true,
            customSegments = hashMapOf(
                SegmentType.FragmentShader to SegmentTemplate(
                    this::class.java,
                    raycastShader,
                    "intersectBoundingBox", "vis", "localNear", "localFar", "SampleVolume", "Convert", "Accumulate",
                ),
                SegmentType.Accumulator to SegmentTemplate(
                    accumulateShader,
                    "vis", "localNear", "localFar", "sampleVolume", "convert", "sceneGraphVisibility"
                ),
            ),
        )
    }

    private fun vdiFull(windowWidth: Int, windowHeight: Int, maxSupersegments: Int, scene: Scene, hub: Hub): VolumeManager {
        val raycastShader = "VDIGenerator.comp"
        val accumulateShader = "AccumulateVDI.comp"
        val volumeManager = instantiateVolumeManager(raycastShader, accumulateShader, hub)

        colorBuffer = MemoryUtil.memCalloc(windowHeight*windowWidth*4*maxSupersegments*4)

        depthBuffer = MemoryUtil.memCalloc(windowHeight*windowWidth*2*maxSupersegments*2 * 2)

        val numGridCells = Vector3f(windowWidth.toFloat() / 8f, windowHeight.toFloat() / 8f, maxSupersegments.toFloat())

        gridBuffer = MemoryUtil.memCalloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)

        val vdiColor: Texture = Texture.fromImage(
            Image(colorBuffer!!, maxSupersegments, windowHeight, windowWidth, FloatType()), usage = hashSetOf( Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            channels = 4, mipmap = false,  normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        volumeManager.customTextures.add(colorTextureName)
        volumeManager.material().textures[colorTextureName] = vdiColor

        val vdiDepth: Texture = Texture.fromImage(
            Image(depthBuffer!!, 2 * maxSupersegments, windowHeight, windowWidth, FloatType()),  usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        volumeManager.customTextures.add(depthTextureName)
        volumeManager.material().textures[depthTextureName] = vdiDepth

        val gridCells: Texture = Texture.fromImage(
            Image(gridBuffer!!, numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt(), UnsignedIntType()), channels = 1,
            usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        volumeManager.customTextures.add(accelerationTextureName)
        volumeManager.material().textures[accelerationTextureName] = gridCells

        volumeManager.customUniforms.add("doGeneration")
        volumeManager.shaderProperties["doGeneration"] = true

        val compute = RichNode()
        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("GridCellsToZero.comp"), this::class.java)))

        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), 1),
            invocationType = InvocationType.Permanent
        )

        compute.material().textures["GridCells"] = gridCells

        scene.addChild(compute)

        return volumeManager
    }

    private fun vdiCompact(windowWidth: Int, windowHeight: Int, maxSupersegments: Int, scene: Scene, hub: Hub): VolumeManager {
        val raycastShader = "AdaptiveVDIGenerator.comp"
        val accumulateShader = "AccumulateVDI.comp"

        val volumeManager = instantiateVolumeManager(raycastShader, accumulateShader, hub)

        val totalMaxSupersegments = maxSupersegments * windowWidth * windowHeight

        colorBuffer = MemoryUtil.memCalloc(512 * 512 * ceil((totalMaxSupersegments / (512*512)).toDouble()).toInt() * 4 * 4)
        depthBuffer = MemoryUtil.memCalloc(2 * 512 * 512 * ceil((totalMaxSupersegments / (512*512)).toDouble()).toInt() * 4)


        val numGridCells = Vector3f(windowWidth.toFloat() / 8f, windowHeight.toFloat() / 8f, maxSupersegments.toFloat())
        gridBuffer = MemoryUtil.memCalloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)

        prefixBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * 4)
        thresholdBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * 4)
        numGeneratedBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * 4)

        val vdiColor: Texture = Texture.fromImage(
            Image(colorBuffer!!, 512, 512, ceil((totalMaxSupersegments / (512*512)).toDouble()).toInt(), FloatType()), usage = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), channels = 4, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        volumeManager.customTextures.add(colorTextureName)
        volumeManager.material().textures[colorTextureName] = vdiColor

        val vdiDepth: Texture = Texture.fromImage(
            Image(depthBuffer!!, 2 * 512, 512, ceil((totalMaxSupersegments / (512*512)).toDouble()).toInt(), FloatType()),  usage = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        volumeManager.customTextures.add(depthTextureName)
        volumeManager.material().textures[depthTextureName] = vdiDepth

        val gridCells: Texture =
            Texture.fromImage(Image(gridBuffer!!, numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt(), FloatType()), channels = 1,
            usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        volumeManager.customTextures.add(accelerationTextureName)
        volumeManager.material().textures[accelerationTextureName] = gridCells

        volumeManager.customTextures.add("PrefixSums")
        volumeManager.material().textures["PrefixSums"] = Texture(
            Vector3i(windowHeight, windowWidth, 1), 1, contents = prefixBuffer, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture)
            , type = IntType(),
            mipmap = false,
            minFilter = Texture.FilteringMode.NearestNeighbour,
            maxFilter = Texture.FilteringMode.NearestNeighbour
        )

        volumeManager.customTextures.add("SupersegmentsGenerated")
        volumeManager.material().textures["SupersegmentsGenerated"] = Texture(
            Vector3i(windowHeight, windowWidth, 1), 1, contents = numGeneratedBuffer, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture)
            , type = IntType(),
            mipmap = false,
            minFilter = Texture.FilteringMode.NearestNeighbour,
            maxFilter = Texture.FilteringMode.NearestNeighbour
        )

        volumeManager.customTextures.add("Thresholds")
        volumeManager.material().textures["Thresholds"] = Texture(
            Vector3i(windowWidth, windowHeight, 1), 1, contents = thresholdBuffer, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture)
            , type = FloatType(),
            mipmap = false,
            minFilter = Texture.FilteringMode.NearestNeighbour,
            maxFilter = Texture.FilteringMode.NearestNeighbour
        )

        volumeManager.customUniforms.add("doGeneration")
        volumeManager.shaderProperties["doGeneration"] = false

        volumeManager.customUniforms.add("doThreshSearch")
        volumeManager.shaderProperties["doThreshSearch"] = true

        volumeManager.customUniforms.add("windowWidth")
        volumeManager.shaderProperties["windowWidth"] = windowWidth

        volumeManager.customUniforms.add("windowHeight")
        volumeManager.shaderProperties["windowHeight"] = windowHeight

        volumeManager.customUniforms.add("maxSupersegments")
        volumeManager.shaderProperties["maxSupersegments"] = maxSupersegments

        val compute = RichNode()
        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("GridCellsToZero.comp"), this::class.java)))
        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), 1),
            invocationType = InvocationType.Permanent
        )
        compute.material().textures["GridCells"] = gridCells

        scene.addChild(compute)

        return volumeManager
    }

    /**
     * Frees the memory allocated to the buffers used to generate VDIs.
     */
    fun close() {
        colorBuffer?.let {
            MemoryUtil.memFree(it)
        }

        depthBuffer?.let {
            MemoryUtil.memFree(it)
        }

        gridBuffer?.let {
            MemoryUtil.memFree(it)
        }

        prefixBuffer?.let {
            MemoryUtil.memFree(it)
        }

        thresholdBuffer?.let {
            MemoryUtil.memFree(it)
        }

        numGeneratedBuffer?.let {
            MemoryUtil.memFree(it)
        }
    }

    companion object {
        const val colorTextureName = "VDIColor"
        const val depthTextureName = "VDIDepth"
        const val accelerationTextureName = "AccelerationGrid"

    }
}
