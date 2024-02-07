package graphics.scenery.volumes.vdi

import graphics.scenery.RichNode
import graphics.scenery.ShaderMaterial
import graphics.scenery.ShaderProperty
import graphics.scenery.backends.Shaders
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import graphics.scenery.textures.UpdatableTexture
import graphics.scenery.utils.Image
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

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

    private val vulkanProjectionFix =
        Matrix4f(
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, -1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.5f, 0.0f,
            0.0f, 0.0f, 0.5f, 1.0f)

    fun Matrix4f.applyVulkanCoordinateSystem(): Matrix4f {
        val m = Matrix4f(vulkanProjectionFix)
        m.mul(this)

        return m
    }

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
        setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("AmanatidesJumps.comp"), this@VDINode::class.java)))

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
     * Returns the dimensions of the acceleration grid data structure of the VDI. These dimensions depend on the resolution of the VDI. When
     * a parameter [vdiData] is supplied, the resolution of the VDI is inferred based on the metadata contained within. When the parameter is
     * not provided, the resolution of the VDI represented by this object is used.
     *
     * @param[vdiData] Optional parameter containing the metadata ([VDIData]) associated with the VDI to which the acceleration grid belongs.
     *
     * @return The dimensions of the acceleration grid.
     */
    fun getAccelerationGridSize(vdiData: VDIData? = null) : Vector3f {
        return if(vdiData == null) {
            Vector3f(vdiWidth/8f, vdiHeight/8f, numSupersegments.toFloat())
        } else {
            Vector3f(vdiData.metadata.windowDimensions.x/8f, vdiData.metadata.windowDimensions.y/8f, numSupersegments.toFloat())
        }
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

        val numGridCells = getAccelerationGridSize()

        if(toBuffer == DoubleBuffer.First) {
            material().textures["InputVDI"] = Texture(Vector3i(numSupersegments, vdiHeight, vdiWidth), 4, contents = colBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture)
                , type = FloatType(),
                mipmap = false,
                minFilter = Texture.FilteringMode.NearestNeighbour,
                maxFilter = Texture.FilteringMode.NearestNeighbour
            )
            material().textures["DepthVDI"] = Texture(Vector3i(2 * numSupersegments, vdiHeight, vdiWidth),  channels = 1, contents = depthBuffer, usageType = hashSetOf(
                Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

            material().textures["OctreeCells"] = Texture(Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()), 1, type = UnsignedIntType(), contents = gridBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        } else {
            material().textures["InputVDI2"] = Texture(Vector3i(numSupersegments, vdiHeight, vdiWidth), 4, contents = colBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture)
                , type = FloatType(),
                mipmap = false,
                minFilter = Texture.FilteringMode.NearestNeighbour,
                maxFilter = Texture.FilteringMode.NearestNeighbour
            )
            material().textures["DepthVDI2"] = Texture(Vector3i(2 * numSupersegments, vdiHeight, vdiWidth),  channels = 1, contents = depthBuffer, usageType = hashSetOf(
                Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

            material().textures["OctreeCells2"] = Texture(Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()), 1, type = UnsignedIntType(), contents = gridBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
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
        val emptyColorTexture = Texture(Vector3i(1, 1, 1), 4, contents = emptyColor, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val emptyDepth = MemoryUtil.memCalloc(1 * 4)
        val emptyDepthTexture = Texture(Vector3i(1, 1, 1), 1, contents = emptyDepth, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            type = FloatType(), mipmap = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val emptyAccel = MemoryUtil.memCalloc(4)
        val emptyAccelTexture = Texture(
            Vector3i(1, 1, 1), 1, contents = emptyAccel, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            type = UnsignedIntType(), mipmap = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour
        )

        if (toBuffer == DoubleBuffer.First ) {
            material().textures["InputVDI"] = emptyColorTexture
            material().textures["DepthVDI"] = emptyDepthTexture
            material().textures["OctreeCells"] = emptyAccelTexture
        } else {
            material().textures["InputVDI2"] = emptyColorTexture
            material().textures["DepthVDI2"] = emptyDepthTexture
            material().textures["OctreeCells2"] = emptyAccelTexture
        }
    }

    private fun updateTextures(colorTexture: UpdatableTexture, depthTexture: UpdatableTexture, gridTexture: UpdatableTexture) {
        if(currentBuffer == DoubleBuffer.First) {
            material().textures["InputVDI"] = colorTexture
            material().textures["DepthVDI"] = depthTexture
            material().textures["OctreeCells"] = gridTexture
        } else {
            material().textures["InputVDI2"] = colorTexture
            material().textures["DepthVDI2"] = depthTexture
            material().textures["OctreeCells2"] = gridTexture
        }

        while (!colorTexture.availableOnGPU() || !depthTexture.availableOnGPU() || !gridTexture.availableOnGPU()) {
            logger.debug("Waiting for texture transfer. color: ${colorTexture.availableOnGPU()}, depth: ${depthTexture.availableOnGPU()}, grid: ${gridTexture.availableOnGPU()}")
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
     * @param[colorTexture] An [UpdatableTexture] containing the colors of the supersegments in the new VDI
     * @param[depthTexture] An [UpdatableTexture] containing the depths of the supersegments in the new VDI
     * @param[gridTexture] An [UpdatableTexture] containing the grid acceleration data structure for the new VDI
     */
    fun updateVDI(vdiData: VDIData, colorTexture: UpdatableTexture, depthTexture: UpdatableTexture, gridTexture: UpdatableTexture) {
        updateMetadata(vdiData)
        updateTextures(colorTexture, depthTexture, gridTexture)

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
}
