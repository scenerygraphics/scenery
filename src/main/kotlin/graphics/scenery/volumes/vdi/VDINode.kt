package graphics.scenery.volumes.vdi

import graphics.scenery.RichNode
import graphics.scenery.ShaderMaterial
import graphics.scenery.ShaderProperty
import graphics.scenery.backends.Shaders
import graphics.scenery.backends.vulkan.VulkanTexture
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import graphics.scenery.textures.UpdatableTexture
import graphics.scenery.utils.Image
import graphics.scenery.utils.extensions.applyVulkanCoordinateSystem
import net.imglib2.type.numeric.NumericType
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.type.numeric.real.FloatType
import org.jetbrains.annotations.ApiStatus.Experimental
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * A class defining the properties and textures required to render a Volumetric Depth Image (VDI). Rendering a VDI requires generating
 * a VDINode and adding it to the scene. The class provides public functions to update the VDI being rendered and its properties. Double
 * buffering is supported so that VDIs can be updated without interrupting the rendering.
 *
 * @param[windowWidth] The width (resolution in pixels along x-axis) of the current rendering window.
 * @param[windowHeight] The height (resolution in pixels along y-axis) of the current rendering window.
 * @param[numSupersegments] The maximum number of supersegments along any ray/pixel of the VDI, i.e., the z-dimension of the VDI.
 * @param[vdiData] The metadata ([VDIData]) associated with the VDI.
 */

@Experimental
class VDINode(windowWidth: Int, windowHeight: Int, val numSupersegments: Int, vdiData: VDIData) : RichNode() {

    /** The projection matrix of the camera viewport that was used to generate the VDI in the first (default) buffer */
    @ShaderProperty
    private var ProjectionOriginal = Matrix4f()

    /** The inverse of the projection matrix of the camera viewport that was used to generate the VDI in the first (default) buffer */
    @ShaderProperty
    private var invProjectionOriginal = Matrix4f()

    /** The view matrix of the camera viewport that was used to generate the VDI in the first (default) buffer */
    @ShaderProperty
    private var ViewOriginal = Matrix4f()

    /** The inverse of the view matrix of the camera viewport that was used to generate the VDI in the first (default) buffer */
    @ShaderProperty
    private var invViewOriginal = Matrix4f()

    /** The view matrix of the camera viewport that was used to generate the VDI in the second buffer */
    @ShaderProperty
    private var ViewOriginal2 = Matrix4f()

    /** The inverse of the view matrix of the camera viewport that was used to generate the VDI in the second buffer */
    @ShaderProperty
    private var invViewOriginal2 = Matrix4f()

    /** Indicates whether the second buffer should be used for rendering. The first buffer is used if false. */
    @ShaderProperty
    private var useSecondBuffer = false

    /** Inverse of the model matrix of the volume in the scene on which the VDI was generated. */
    @ShaderProperty
    private var invModel = Matrix4f()

    /** The dimensions (in voxels, x, y and z) of the volume on which the VDI was generated. */
    @ShaderProperty
    private var volumeDims = Vector3f()

    /** BigVolumeViewer property storing the voxel side length world space of the volume on which the VDI was generated. */
    @ShaderProperty
    private var nw = 0f

    /** The total supersegments in the entire VDI. */
    @ShaderProperty
    private var totalGeneratedSupsegs: Int = 0

    /** Should the rendering be subsampled along the ray? Subsampling leads to better performance but worse rendering quality. */
    @ShaderProperty
    private var do_subsample = false

    /** The maximum permitted samples along the ray. Only relevant if [do_subsample] is true. */
    @ShaderProperty
    private var max_samples = 50

    /** The sampling factor along the ray. Only relevant if [do_subsample] is true. Larger value leads to better quality at the cost of performance */
    @ShaderProperty
    private var sampling_factor = 0.1f

    /** Controls the downsampling of the display resolution. Lower values accelerate rendering at the cost of quality. Value 1.0f represents full resolution rendering. */
    @ShaderProperty
    private var downImage = 1f

    /** The rendering window width (x-axis of the display resolution) for which the VDI was generated. */
    @ShaderProperty
    var vdiWidth: Int = 0

    /** The rendering window height (y-axis of the display resolution) for which the VDI was generated. */
    @ShaderProperty
    var vdiHeight: Int = 0

    /** Whether empty regions should be skipped or not. Accelerates rendering without loss of quality. */
    @ShaderProperty
    var skip_empty = true

    // Texture properties for the VDINode class

    /**
     * The color texture of the VDINode for the first buffer.
     * This property is initially set to null and is expected to be set later.
     */
    private var colorTexture: Texture? = null

    /**
     * The color texture of the VDINode for the second buffer.
     * This property is initially set to null and is expected to be set later.
     */
    private var colorTexture2: Texture? = null

    /**
     * The depth texture of the VDINode for the first buffer.
     * This property is initially set to null and is expected to be set later.
     */
    private var depthTexture: Texture? = null

    /**
     * The depth texture of the VDINode for the second buffer.
     * This property is initially set to null and is expected to be set later.
     */
    private var depthTexture2: Texture? = null

    /**
     * The acceleration texture of the VDINode for the first buffer.
     * This property is initially set to null and is expected to be set later.
     */
    private var accelerationTexture: Texture? = null

    /**
     * The acceleration texture of the VDINode for the second buffer.
     * This property is initially set to null and is expected to be set later.
     */
    private var accelerationTexture2: Texture? = null

    /**
     * Enum class recording which of the two VDIs is currently being rendered.
     *
     * VDI rendering supports double buffering on the GPU so that one VDI can be updated while the other is being rendered.
     */
    enum class DoubleBuffer {
        First,
        Second
    }

    /** An object of the enum class [DoubleBuffer]. Tracks which buffer is currently being rendered. */
    private var currentBuffer = DoubleBuffer.First

    var wantsSync = false
    override fun wantsSync(): Boolean = wantsSync

    init {
        name = "vdi node"
        setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("RaycastVDI.comp"), this@VDINode::class.java)))

        val opBuffer = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)

        material().textures["OutputViewport"] = Texture.fromImage(
            Image(opBuffer, windowWidth, windowHeight),
            usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowWidth, windowHeight, 1),
            invocationType = InvocationType.Permanent
        )
        visible = true

        updateMetadata(vdiData)
    }

    /**
     * Attaches textures containing the VDI data for rendering.
     *
     * @param[colBuffer] A [ByteBuffer] containing the colors of the supsersegments in the VDI
     * @param[depthBuffer] A [ByteBuffer] containing the depths of the supsersegments in the VDI
     * @param[gridBuffer] A [ByteBuffer] containing the acceleration grid for the VDI
     * @param[toBuffer] Defines which of the VDI buffers in the double-buffering system the textures should be attached to.
     * Defaults to [DoubleBuffer.First]
     */
    fun attachTextures(colBuffer: ByteBuffer, depthBuffer: ByteBuffer, gridBuffer: ByteBuffer, toBuffer: DoubleBuffer = DoubleBuffer.First) {

        if(toBuffer == DoubleBuffer.First) {
            colorTexture = generateColorTexture(vdiWidth, vdiHeight, numSupersegments, colBuffer)
            depthTexture = generateDepthTexture(vdiWidth, vdiHeight, numSupersegments, depthBuffer)
            accelerationTexture = generateAccelerationTexture(vdiWidth, vdiHeight, numSupersegments, gridBuffer)

            material().textures[inputColorTexture] = colorTexture!!
            material().textures[inputDepthTexture] = depthTexture!!

            material().textures[inputAccelerationTexture] = accelerationTexture!!
        } else {
            colorTexture2 = generateColorTexture(vdiWidth, vdiHeight, numSupersegments, colBuffer)
            depthTexture2 = generateDepthTexture(vdiWidth, vdiHeight, numSupersegments, depthBuffer)
            accelerationTexture2 = generateAccelerationTexture(vdiWidth, vdiHeight, numSupersegments, gridBuffer)

            material().textures["${inputColorTexture}2"] = colorTexture2!!
            material().textures["${inputDepthTexture}2"] = depthTexture2!!

            material().textures["${inputAccelerationTexture}2"] = accelerationTexture2!!
        }
    }

    /**
     * Attaches empty textures so that the VDI node can be placed into the scene before contents of a VDI are available
     * (e.g. in streaming applications).
     *
     * @param[toBuffer] Defines which of the VDI buffers in the double-buffering system the textures should be attached to.
     */
    fun attachEmptyTextures(toBuffer: DoubleBuffer) {
        val emptyColor = MemoryUtil.memCalloc(4 * 4)
        val emptyColorTexture = generateColorTexture(1, 1, 1, emptyColor)
        val emptyDepth = MemoryUtil.memCalloc(1 * 8)
        val emptyDepthTexture = generateDepthTexture(1, 1, 1, emptyDepth)

        val emptyAccel = MemoryUtil.memCalloc(4)
        val emptyAccelTexture = generateAccelerationTexture(1, 1, 1, emptyAccel)

        if (toBuffer == DoubleBuffer.First ) {

            colorTexture = emptyColorTexture
            depthTexture = emptyDepthTexture
            accelerationTexture = emptyAccelTexture

            material().textures[inputColorTexture] = emptyColorTexture
            material().textures[inputDepthTexture] = emptyDepthTexture
            material().textures[inputAccelerationTexture] = emptyAccelTexture
        } else {

            colorTexture2 = emptyColorTexture
            depthTexture2 = emptyDepthTexture
            accelerationTexture2 = emptyAccelTexture

            material().textures["${inputColorTexture}2"] = emptyColorTexture
            material().textures["${inputDepthTexture}2"] = emptyDepthTexture
            material().textures["${inputAccelerationTexture}2"] = emptyAccelTexture
        }
    }

    /**
     * Asynchronously updates the VDI on the GPU using double buffering and [UpdatableTexture]s.
     */
    private fun updateTextures(color: ByteBuffer, depth: ByteBuffer, accelGridBuffer: ByteBuffer) {

        val colorTexture = UpdatableTexture(Vector3i(numSupersegments, vdiHeight, vdiWidth), 4, contents = null, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad),
            type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val colorUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, numSupersegments, vdiHeight, vdiWidth),
            color.slice()
        )
        colorTexture.addUpdate(colorUpdate)


        val depthTexture = UpdatableTexture(Vector3i(2 * numSupersegments, vdiHeight, vdiWidth), channels = 1, contents = null, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val depthUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, 2 * numSupersegments, vdiHeight, vdiWidth),
            depth.slice()
        )
        depthTexture.addUpdate(depthUpdate)


        val numGridCells = getAccelerationGridSize(vdiWidth, vdiHeight, numSupersegments)

        val accelTexture = UpdatableTexture(Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()), channels = 1, contents = null, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad), type = UnsignedIntType(), mipmap = false, normalized = true, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val accelUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, vdiWidth / 8, vdiHeight / 8, numSupersegments),
            accelGridBuffer
        )
        accelTexture.addUpdate(accelUpdate)


        if(currentBuffer == DoubleBuffer.First) {
            material().textures[inputColorTexture] = colorTexture
            material().textures[inputDepthTexture] = depthTexture
            material().textures[inputAccelerationTexture] = accelTexture
        } else {
            material().textures["${inputColorTexture}2"] = colorTexture
            material().textures["${inputDepthTexture}2"] = depthTexture
            material().textures["${inputAccelerationTexture}2"] = accelTexture
        }

        while (!colorTexture.availableOnGPU() || !depthTexture.availableOnGPU() || !accelTexture.availableOnGPU()) {
            logger.debug("Waiting for texture transfer. color: ${colorTexture.availableOnGPU()}, depth: ${depthTexture.availableOnGPU()}, grid: ${accelTexture.availableOnGPU()}")
            Thread.sleep(10)
        }

        logger.debug("Data has been detected to be uploaded to GPU")
    }

    private fun updateMetadata(vdiData: VDIData) {
        this.ProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem()
        this.invProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem().invert()
        this.nw = vdiData.metadata.nw
        this.vdiWidth = vdiData.metadata.windowDimensions.x
        this.vdiHeight = vdiData.metadata.windowDimensions.y
        this.invModel = Matrix4f(vdiData.metadata.model).invert()
        this.volumeDims = vdiData.metadata.volumeDimensions

        if(currentBuffer == DoubleBuffer.First) {
            this.ViewOriginal = vdiData.metadata.view
            this.invViewOriginal = Matrix4f(vdiData.metadata.view).invert()
        } else {
            this.ViewOriginal2 = vdiData.metadata.view
            this.invViewOriginal2 = Matrix4f(vdiData.metadata.view).invert()
        }
    }

    /**
     * Update the VDI currently being rendered with a new one. The contents of the new VDI are to be provided in the form of
     * [UpdatableTexture]s.
     *
     * The function transparently handles double buffering - the new VDI is uploaded to the GPU in a different buffer than the
     * VDI currently being rendered. The rendering buffer is switched once the upload is complete. The function returns after the
     * upload is complete.
     *
     * @param[vdiData] The metadata ([VDIData]) associated with the new VDI
     * @param[color] A [ByteBuffer] containing the colors of the supersegments in the new VDI
     * @param[depth] A [ByteBuffer] containing the depths of the supersegments in the new VDI
     * @param[accelGridBuffer] A [ByteBuffer] containing the grid acceleration data structure for the new VDI
     */
    fun updateVDI(vdiData: VDIData, color: ByteBuffer, depth: ByteBuffer, accelGridBuffer: ByteBuffer) {
        updateMetadata(vdiData)
        updateTextures(color, depth, accelGridBuffer)

        if(currentBuffer == DoubleBuffer.First) {
            useSecondBuffer = false
            //The next buffer to which data should be uploaded is the second one
            currentBuffer = DoubleBuffer.Second
        } else {
            useSecondBuffer = true
            //The next buffer to which data should be uploaded is the first one
            currentBuffer = DoubleBuffer.First
        }
    }

    /**
     * Closes the VDINode by releasing the GPU resources associated with the textures used in the node.
     * This function is typically called when the VDINode is no longer needed, to free up GPU memory.
     * It iterates over all textures in the material of the node, retrieves the VulkanTexture reference
     * for each texture, and calls the close() method on it to release the GPU resources.
     */
    override fun close() {
        material().textures.forEach { (name, texture) ->
            logger.debug("For VDINode: closing texture $name.")
            VulkanTexture.getReference(texture)?.close()
            MemoryUtil.memFree(texture.contents)
        }
    }

    companion object {
        const val inputColorTexture = "InputVDI"
        const val inputDepthTexture = "DepthVDI"
        const val inputAccelerationTexture = "AccelerationGrid"

        fun getLinearizationOrder(vdiWidth: Int, vdiHeight: Int, numSupersegments: Int) : Vector3i {
            return Vector3i(vdiWidth, vdiHeight, numSupersegments)
        }

        private fun getColorTextureType(): NumericType<*> {
            return FloatType()
        }

        private fun getDepthTextureType(): NumericType<*> {
            val intDepths = false
            return if (intDepths) {
                UnsignedShortType()
            } else {
                FloatType()
            }
        }

        private fun getColorTextureChannels(): Int {
            return 4
        }

        /**
         * Calculates the size of the color buffer.
         *
         * @param vdiWidth The width of the VDI.
         * @param vdiHeight The height of the VDI.
         * @param numSupersegments The number of supersegments in the VDI.
         * @return The size of the color buffer in bytes.
         */
        fun getColorBufferSize(vdiWidth: Int, vdiHeight: Int, numSupersegments: Int): Int {
            return vdiWidth * vdiHeight * numSupersegments * getColorTextureChannels() * if (getColorTextureType() is FloatType) {
                4
            } else {
                //else we assume it is an unsigned byte
                1
            }
        }

        /**
         * Calculates the size of the depth buffer.
         *
         * @param vdiWidth The width of the VDI.
         * @param vdiHeight The height of the VDI.
         * @param numSupersegments The number of supersegments in the VDI.
         * @return The size of the depth buffer in bytes.
         */
        fun getDepthBufferSize(vdiWidth: Int, vdiHeight: Int, numSupersegments: Int): Int {
            return vdiWidth * vdiHeight * numSupersegments * 2 * if (getDepthTextureType() is FloatType) {
                4
            } else {
                //else we assume it is an unsigned short
                2
            }
        }

        /**
         * Calculates the size of the acceleration buffer, given the dimensions of the VDI.
         *
         * @param vdiWidth The width of the VDI.
         * @param vdiHeight The height of the VDI.
         * @param numSupersegments The number of supersegments in the VDI.
         * @return The size of the acceleration buffer in bytes.
         */
        fun getAccelerationGridSize(vdiWidth: Int, vdiHeight: Int, numSupersegments: Int) : Vector3f {
            return Vector3f(max(vdiWidth/8f, 1f), max(vdiHeight/8f, 1f), max(numSupersegments.toFloat(), 1f))
        }

        /**
         * Generates a color texture for the VDI.
         *
         * @param vdiWidth The width of the VDI.
         * @param vdiHeight The height of the VDI.
         * @param numSupersegments The number of supersegments in the VDI.
         * @param buffer The buffer containing the color data.
         * @return The generated color texture.
         */
        fun generateColorTexture(vdiWidth: Int, vdiHeight: Int, numSupersegments: Int, buffer: ByteBuffer) : Texture {
            val dimensions = getLinearizationOrder(vdiWidth, vdiHeight, numSupersegments)
            return Texture(dimensions, 4, contents = buffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture)
                , type = getColorTextureType(),
                minFilter = Texture.FilteringMode.NearestNeighbour,
                maxFilter = Texture.FilteringMode.NearestNeighbour
            )
        }

        /**
         * Generates a depth texture for the VDI.
         *
         * @param vdiWidth The width of the VDI.
         * @param vdiHeight The height of the VDI.
         * @param numSupersegments The number of supersegments in the VDI.
         * @param buffer The buffer containing the depth data.
         * @return The generated depth texture.
         */
        fun generateDepthTexture(vdiWidth: Int, vdiHeight: Int, numSupersegments: Int, buffer: ByteBuffer) : Texture {
            val dimensions = getLinearizationOrder(vdiWidth, vdiHeight, numSupersegments)
            return Texture(dimensions,  channels = 2, contents = buffer, usageType = hashSetOf(
                Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = getDepthTextureType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        }

        /**
         * Generates an acceleration texture for the VDI.
         *
         * @param vdiWidth The width of the VDI.
         * @param vdiHeight The height of the VDI.
         * @param numSupersegments The number of supersegments in the VDI.
         * @param buffer The buffer containing the acceleration data.
         * @return The generated acceleration texture.
         */
        fun generateAccelerationTexture(vdiWidth: Int, vdiHeight: Int, numSupersegments: Int, buffer: ByteBuffer) : Texture {
            val numGridCells = getAccelerationGridSize(vdiWidth, vdiHeight, numSupersegments)
            return Texture(Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()),
                1, type = UnsignedIntType(), contents = buffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        }
    }
}
