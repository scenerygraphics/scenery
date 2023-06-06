package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.volumes.VolumeManager
import graphics.scenery.volumes.vdi.VDIDataIO
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.math.ceil

class VDINode : RichNode() {

    @ShaderProperty
    var ProjectionOriginal = Matrix4f()

    @ShaderProperty
    var invProjectionOriginal = Matrix4f()

    @ShaderProperty
    var ViewOriginal = Matrix4f()

    @ShaderProperty
    var invViewOriginal = Matrix4f()

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
    var do_subsample = false

    @ShaderProperty
    var windowWidth: Int = 0

    @ShaderProperty
    var windowHeight: Int = 0

}

class VDIRenderingExample : SceneryBase("VDI Rendering Example", 1080, 1080) {

    var hmd: TrackedStereoGlasses? = null
    val vdiNode = VDINode()

    val separateDepth = true
//    val runLengthEncoded = false
    val skipEmpty = false

    val numSupersegments = 20
    val numLayers = 1

    private val vulkanProjectionFix =
        Matrix4f(
            1.0f,  0.0f, 0.0f, 0.0f,
            0.0f, -1.0f, 0.0f, 0.0f,
            0.0f,  0.0f, 0.5f, 0.0f,
            0.0f,  0.0f, 0.5f, 1.0f)

    fun Matrix4f.applyVulkanCoordinateSystem(): Matrix4f {
        val m = Matrix4f(vulkanProjectionFix)
        m.mul(this)
        return m
    }

    override fun init() {

        //Step 1: create a Renderer, Point light and camera
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial().position = Vector3f( 5.699E+0f, -4.935E-1f,  5.500E+0f)
            spatial().rotation = Quaternionf( 1.211E-1, -3.842E-1 ,-5.090E-2,  9.139E-1)
            perspectiveCamera(50.0f, windowWidth, windowWidth)
            scene.addChild(this)
        }

        //Step 2: read files
        val buff: ByteArray?
        val depthBuff: ByteArray?
        val octBuff: ByteArray?

        val file = FileInputStream(File("VDI_dump4"))
        val vdiData = VDIDataIO.read(file)
        logger.info("Fetching file...")

        buff = File("VDI_4_ndc_col").readBytes()
        depthBuff = File("VDI_4_ndc_depth").readBytes()
        octBuff = File("VDI_4_ndc_octree").readBytes()

        //Step  3: assigning buffer values
        val colBuffer: ByteBuffer
        val depthBuffer: ByteBuffer?
        val opBuffer = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)

        val totalMaxSupersegments = (numSupersegments * windowWidth * windowHeight).toFloat()

        colBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * numLayers * 4 * 4)
        colBuffer.put(buff).flip()
        colBuffer.limit(colBuffer.capacity())
        logger.info("Length of color buffer is ${buff.size} and associated bytebuffer capacity is ${colBuffer.capacity()} it has remaining: ${colBuffer.remaining()}")
        logger.info("Col sum is ${buff.sum()}")

        depthBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * 2 * 2 * 2)
        depthBuffer.put(depthBuff).flip()
        depthBuffer.limit(depthBuffer.capacity())
        logger.info("Length of depth buffer is ${depthBuff!!.size} and associated bytebuffer capacity is ${depthBuffer.capacity()} it has remaining ${depthBuffer.remaining()}")
        logger.info("Depth sum is ${depthBuff.sum()}")

        val numGridCells = Vector3f(vdiData.metadata.windowDimensions.x/ 8f, vdiData.metadata.windowDimensions.y/ 8f, numSupersegments.toFloat())
        val lowestLevel = MemoryUtil.memCalloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)
        if(skipEmpty) {
            lowestLevel.put(octBuff).flip()
        }

        //Step 4: Creating compute node and attach shader and vdi Files to
        vdiNode.name = "compute node"

        vdiNode.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("AmanatidesJumps.comp"),
            this@VDIRenderingExample::class.java))) {
            textures["OutputViewport"] = Texture.fromImage(Image(opBuffer, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        }


        vdiNode.material().textures["InputVDI"] = Texture(Vector3i(numSupersegments*numLayers, windowHeight, windowWidth), 4, contents = colBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture)
            , type = FloatType(),
            mipmap = false,
            minFilter = Texture.FilteringMode.NearestNeighbour,
            maxFilter = Texture.FilteringMode.NearestNeighbour
        )
        vdiNode.material().textures["DepthVDI"] = Texture(Vector3i(2 * numSupersegments, windowHeight, windowWidth),  channels = 1, contents = depthBuffer, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        vdiNode.material().textures["OctreeCells"] = Texture(Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()), 1, type = UnsignedIntType(), contents = lowestLevel, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))

        vdiNode.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowWidth, windowHeight, 1),
            invocationType = InvocationType.Permanent
        )

        vdiNode.ProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem()
        vdiNode.invProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem().invert()
        vdiNode.ViewOriginal = vdiData.metadata.view
        vdiNode.nw = vdiData.metadata.nw
        vdiNode.vdiWidth = vdiData.metadata.windowDimensions.x
        vdiNode.vdiHeight = vdiData.metadata.windowDimensions.y
        vdiNode.invViewOriginal = Matrix4f(vdiData.metadata.view).invert()
        vdiNode.invModel = Matrix4f(vdiData.metadata.model).invert()
        vdiNode.volumeDims = vdiData.metadata.volumeDimensions
        vdiNode.do_subsample = false


        logger.info("Projection: ${Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem()}")
        logger.info("View: ${vdiData.metadata.view}")
        logger.info("Actual view: ${cam.spatial().getTransformation()}")
        logger.info("nw: ${vdiData.metadata.nw}")

        scene.addChild(vdiNode)

        val plane = FullscreenObject()
        plane.material().textures["diffuse"] = vdiNode.material().textures["OutputViewport"]!!
        scene.addChild(plane)

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIRenderingExample().main()
        }
    }


}
