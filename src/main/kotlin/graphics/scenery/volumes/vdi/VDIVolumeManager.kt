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
import graphics.scenery.volumes.vdi.VDINode
import net.imglib2.type.numeric.integer.IntType
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.integer.UnsignedShortType
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
class VDIVolumeManager (var hub: Hub, val windowWidth: Int, val windowHeight: Int, private val maxSupersegments: Int, val scene: Scene)
{
    private val logger by lazyLogger()

    var colorBuffer: ByteBuffer? = null
    var depthBuffer: ByteBuffer? = null
    var gridBuffer: ByteBuffer? = null

    var prefixBuffer: ByteBuffer? = null
    var thresholdBuffer: ByteBuffer? = null
    var numGeneratedBuffer: ByteBuffer? = null

    private var colorTexture: Texture? = null
    private var depthTexture: Texture? = null
    private var numGeneratedTexture: Texture? = null

    /**
     * Returns the width of the VDI.
     *
     * @return The width of the VDI.
     */
    fun getVDIWidth(): Int {
        return windowWidth
    }

    /**
     * Returns the height of the VDI.
     *
     * @return The height of the VDI.
     */
    fun getVDIHeight(): Int {
        return windowHeight
    }

    /**
     * Returns the maximum number of supersegments.
     *
     * @return The maximum number of supersegments.
     */
    fun getMaxSupersegments(): Int {
        return maxSupersegments
    }

    /**
     * Returns the output VDI color texture, containing supersegment colors, if it exists, otherwise logs an error.
     *
     * @return The color texture or null if it does not exist.
     */
    fun getColorTextureOrNull(): Texture? {
        if (colorTexture == null) {
            logger.error("Color texture is null. Was VDIVolumeManager created?")
        }
        return colorTexture
    }

    /**
     * Returns the output VDI depth texture, containing supersegment depths, if it exists, otherwise logs an error.
     *
     * @return The depth texture or null if it does not exist.
     */
    fun getDepthTextureOrNull(): Texture? {
        if (depthTexture == null) {
            logger.error("Depth texture is null. Was VDIVolumeManager created?")
        }
        return depthTexture
    }

    /**
     * Returns the output VDI numGenerated texture, containing the number of generated supersegments, if it exists, otherwise logs an error.
     * This texture is generated in the first pass of the compact VDI generation.
     *
     * @return The numGenerated texture or null if it does not exist.
     */
    fun getNumGeneratedTextureOrNull(): Texture? {
        if (numGeneratedTexture == null) {
            logger.error("NumGenerated texture is null. Was VDIVolumeManager created?")
        }
        return numGeneratedTexture
    }

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
        val intDepths = false
        val raycastShader = "VDIGenerator.comp"
        val accumulateShader = "AccumulateVDI.comp"
        val volumeManager = instantiateVolumeManager(raycastShader, accumulateShader, hub)

        colorBuffer = MemoryUtil.memCalloc(windowHeight*windowWidth*4*maxSupersegments*4)

        depthBuffer = MemoryUtil.memCalloc(windowHeight*windowWidth*2*maxSupersegments*2 * 2)

        val numGridCells = Vector3f(windowWidth.toFloat() / 8f, windowHeight.toFloat() / 8f, maxSupersegments.toFloat())

        gridBuffer = MemoryUtil.memCalloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)

        val vdiDimensions = VDINode.getLinearizationOrder(windowWidth, windowHeight, maxSupersegments)

        val vdiColor: Texture = Texture.fromImage(
            Image(colorBuffer!!, vdiDimensions.x, vdiDimensions.y, vdiDimensions.z, FloatType()), usage = hashSetOf( Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            channels = 4, mipmap = false,  normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        volumeManager.customTextures.add(colorTextureName)
        volumeManager.material().textures[colorTextureName] = vdiColor

        val vdiDepth: Texture = if(intDepths) {
            Texture.fromImage(
                Image(depthBuffer!!, vdiDimensions.x, vdiDimensions.y, vdiDimensions.z, UnsignedShortType()),  usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
                channels = 2, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        } else {
            Texture.fromImage(
                Image(depthBuffer!!, vdiDimensions.x, vdiDimensions.y, vdiDimensions.z, FloatType()),  usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
                channels = 2, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        }
        volumeManager.customTextures.add(depthTextureName)
        volumeManager.material().textures[depthTextureName] = vdiDepth

        val gridCells: Texture = Texture.fromImage(
            Image(gridBuffer!!, numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt(), UnsignedIntType()), channels = 1,
            usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        volumeManager.customTextures.add(accelerationTextureName)
        volumeManager.material().textures[accelerationTextureName] = gridCells

        volumeManager.customUniforms.add("doGeneration")
        volumeManager.shaderProperties["doGeneration"] = true

        colorTexture = volumeManager.material().textures[colorTextureName]
        depthTexture = volumeManager.material().textures[depthTextureName]

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

        colorTexture = volumeManager.material().textures[colorTextureName]
        depthTexture = volumeManager.material().textures[depthTextureName]
        numGeneratedTexture = volumeManager.material().textures["SupersegmentsGenerated"]

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
