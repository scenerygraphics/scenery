package graphics.scenery.volumes.vdi

import graphics.scenery.RichNode
import graphics.scenery.ShaderMaterial
import graphics.scenery.ShaderProperty
import graphics.scenery.backends.Shaders
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

class VDINode(windowWidth: Int, windowHeight: Int, val numSupersegments: Int, vdiData: VDIData) : RichNode() {

    @ShaderProperty
    var ProjectionOriginal = Matrix4f()

    @ShaderProperty
    var invProjectionOriginal = Matrix4f()

    @ShaderProperty
    var ViewOriginal = Matrix4f()

    @ShaderProperty
    var invViewOriginal = Matrix4f()

    @ShaderProperty
    var ViewOriginal2 = Matrix4f()

    @ShaderProperty
    var invViewOriginal2 = Matrix4f()

    @ShaderProperty
    var useSecondBuffer = false

    @ShaderProperty
    var invModel = Matrix4f()

    @ShaderProperty
    var volumeDims = Vector3f()

    @ShaderProperty
    var nw = 0f

    @ShaderProperty
    var vdiWidth: Int = 0

    @ShaderProperty
    var vdiHeight: Int = 0

    @ShaderProperty
    var totalGeneratedSupsegs: Int = 0

    @ShaderProperty
    var do_subsample = false

    @ShaderProperty
    var max_samples = 50

    @ShaderProperty
    var sampling_factor = 0.1f

    @ShaderProperty
    var downImage = 1f

    @ShaderProperty
    var skip_empty = true

    @ShaderProperty
    var stratified_downsampling = false

    @ShaderProperty
    var printData = true

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

    enum class DoubleBuffer {
        First,
        Second
    }

    var numGridCells = Vector3f()

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

    fun setAccelGridSize(size: Vector3i? = null) {
        numGridCells = Vector3f(vdiWidth/8f, vdiHeight/8f, numSupersegments.toFloat())
    }

    fun attachTextures(colBuffer: ByteBuffer, depthBuffer: ByteBuffer, gridBuffer: ByteBuffer) {
        material().textures["InputVDI"] = Texture(Vector3i(numSupersegments, vdiHeight, vdiWidth), 4, contents = colBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture)
            , type = FloatType(),
            mipmap = false,
            minFilter = Texture.FilteringMode.NearestNeighbour,
            maxFilter = Texture.FilteringMode.NearestNeighbour
        )
        material().textures["DepthVDI"] = Texture(Vector3i(2 * numSupersegments, vdiHeight, vdiWidth),  channels = 1, contents = depthBuffer, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        this.material().textures["OctreeCells"] = Texture(Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()), 1, type = UnsignedIntType(), contents = gridBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))

    }

    fun attachEmptyTextures(doubleBuffer: DoubleBuffer) {
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

        if (doubleBuffer == DoubleBuffer.First ) {
            material().textures["InputVDI"] = emptyColorTexture
            material().textures["DepthVDI"] = emptyDepthTexture
            material().textures["OctreeCells"] = emptyAccelTexture
        } else {
            material().textures["InputVDI2"] = emptyColorTexture
            material().textures["DepthVDI2"] = emptyDepthTexture
            material().textures["OctreeCells2"] = emptyAccelTexture
        }
    }

    fun updateMetadata(vdiData: VDIData) {
        this.ProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem()
        this.invProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem().invert()
        this.ViewOriginal = vdiData.metadata.view
        this.nw = vdiData.metadata.nw
        this.vdiWidth = vdiData.metadata.windowDimensions.x
        this.vdiHeight = vdiData.metadata.windowDimensions.y
        this.invViewOriginal = Matrix4f(vdiData.metadata.view).invert()
        this.invModel = Matrix4f(vdiData.metadata.model).invert()
        this.volumeDims = vdiData.metadata.volumeDimensions

        setAccelGridSize()
    }
}
