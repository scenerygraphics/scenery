package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.volumes.vdi.VDIDataIO
import net.imglib2.type.numeric.integer.IntType
import net.imglib2.type.numeric.integer.UnsignedByteType
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

class CustomNodeSimple : RichNode() {
    @ShaderProperty
    var ProjectionOriginal = Matrix4f()

    @ShaderProperty
    var invProjectionOriginal = Matrix4f()

    @ShaderProperty
    var ViewOriginal = Matrix4f()

    @ShaderProperty
    var invViewOriginal = Matrix4f()

    @ShaderProperty
    var nw = 0f

    @ShaderProperty
    var windowWidth: Int = 0

    @ShaderProperty
    var windowHeight: Int = 0

    @ShaderProperty
    var totalGeneratedSupsegs: Int = 0

    @ShaderProperty
    var array: FloatArray = FloatArray(16)
}


class VDIRendererSimple : SceneryBase("SimpleVDIRenderer", 1280, 720) {

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

    val separateDepth = true
    val colors32bit = true

    val runLengthEncoded = true

    val commSize = 2
    val rank = 0

    override fun init() {

        val numLayers = if(separateDepth) {
            1
        } else {
            3
        }

        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val numSupersegments = 20

        val buff: ByteArray
        val depthBuff: ByteArray?

        var dataset = "Kingsnake"

//        dataset += "_${commSize}_${rank}"


//        val basePath = "/home/aryaman/Repositories/DistributedVis/cmake-build-debug/"
        val basePath = "/home/aryaman/Repositories/scenery-insitu/"
//        val basePath = "/home/aryaman/Repositories/scenery_vdi/scenery/"
//        val basePath = "/home/aryaman/TestingData/"
//        val basePath = "/home/aryaman/TestingData/FromCluster/"

        val vdiParams = "_${windowWidth}_${windowHeight}_${numSupersegments}_0_"
//        val vdiParams = ""

        val file = FileInputStream(File(basePath + "${dataset}vdi${vdiParams}dump4"))
//        val comp = GZIPInputStream(file, 65536)

        val vdiData = VDIDataIO.read(file)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial().position = Vector3f( 5.699E+0f, -4.935E-1f,  5.500E+0f)
//            spatial().position = Vector3f( 6.284E+0f, -4.932E-1f, 4.787E+0f)
            spatial().rotation = Quaternionf( 1.211E-1, -3.842E-1 ,-5.090E-2,  9.139E-1)
//            spatial().rotation = Quaternionf( 1.162E-1, -4.624E-1, -6.126E-2,  8.769E-1)
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }
        cam.farPlaneDistance = 20.0f

//        vdiData.metadata.projection = cam.spatial().projection
//        vdiData.metadata.view = cam.spatial().getTransformation()


//        val vdiType = "Sub"
//        val vdiType = "Composited"
//        val vdiType = "SetOf"
//        val vdiType = "Final"
        val vdiType = ""



        if(runLengthEncoded) {
            buff = File(basePath + "${dataset}${vdiType}VDI${vdiParams}4_ndc_col_rle").readBytes()
            depthBuff = File(basePath + "${dataset}${vdiType}VDI${vdiParams}4_ndc_depth_rle").readBytes()
        } else {
            buff = File(basePath + "${dataset}${vdiType}VDI${vdiParams}4_ndc_col").readBytes()
            depthBuff = File(basePath + "${dataset}${vdiType}VDI${vdiParams}4_ndc_depth").readBytes()
        }

        val totalMaxSupersegments = if(runLengthEncoded) {
            buff.size / (4*4).toFloat()
        } else {
            (numSupersegments * windowWidth * windowHeight).toFloat()
        }

        var colBuffer: ByteBuffer
        var depthBuffer: ByteBuffer?
//        colBuffer = ByteBuffer.wrap(buff)
//        depthBuffer = ByteBuffer.wrap(depthBuff)
        colBuffer = if(runLengthEncoded) {
            MemoryUtil.memCalloc(512 * 512 * ceil((totalMaxSupersegments / (512*512)).toDouble()).toInt() * 4 * 4)
        } else {
            MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * numLayers * 4 * 4)
        }

        colBuffer.put(buff).flip()
        colBuffer.limit(colBuffer.capacity())
        logger.info("Length of color buffer is ${buff.size} and associated bytebuffer capacity is ${colBuffer.capacity()} it has remaining: ${colBuffer.remaining()}")
        logger.info("Col sum is ${buff.sum()}")


        depthBuffer = if(runLengthEncoded) {
            MemoryUtil.memCalloc(2 * 512 * 512 * ceil((totalMaxSupersegments / (512*512)).toDouble()).toInt() * 4)
        } else {
            MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * 2 * 2 * 2)
        }
        depthBuffer.put(depthBuff).flip()
        depthBuffer.limit(depthBuffer.capacity())
        logger.info("Length of depth buffer is ${depthBuff!!.size} and associated bytebuffer capacity is ${depthBuffer.capacity()} it has remaining ${depthBuffer.remaining()}")
        logger.info("Depth sum is ${depthBuff.sum()}")

        val outputBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * 4)

        val compute = CustomNodeSimple()
        compute.name = "compute node"

//        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("SimpleVDITraversal.comp"), this@VDIRendererSimple::class.java))) {
        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("SimpleVDIRenderer.comp"), this@VDIRendererSimple::class.java))) {
//        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("SimpleVDIRendererIntDepths.comp"), this@VDIRendererSimple::class.java))) {
            textures["OutputViewport"] = Texture.fromImage(Image(outputBuffer, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
            textures["OutputViewport"]!!.mipmap = false
        }

        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowWidth, windowHeight, 1),
            invocationType = InvocationType.Permanent
        )

        val bufType = if(colors32bit) {
            FloatType()
        } else {
            UnsignedByteType()
        }

        compute.windowWidth = windowWidth
        compute.windowHeight = windowHeight
        compute.totalGeneratedSupsegs = totalMaxSupersegments.toInt()

        compute.nw = vdiData.metadata.nw
        compute.ViewOriginal = vdiData.metadata.view
        compute.invViewOriginal = Matrix4f(vdiData.metadata.view).invert()
        compute.ProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem()
        compute.invProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem().invert()

//        compute.array = floatArrayOf(3f, 5f, 7f)

        compute.array[1] = 2485f
        compute.array[0] = 345f
        compute.array[2] = 65f
        compute.array[3] = 65f
//
//        compute.array.set(1, 4455f)


        logger.info("value of nw: ${vdiData.metadata.nw}")

        if(runLengthEncoded) {
            compute.material().textures["InputVDI"] = Texture(Vector3i(numLayers * 512, 512, ceil((totalMaxSupersegments / (512*512)).toDouble()).toInt()), 4, contents = colBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture)
                , type = bufType,
                mipmap = false,
//            normalized = false,
                minFilter = Texture.FilteringMode.NearestNeighbour,
                maxFilter = Texture.FilteringMode.NearestNeighbour
            )
            compute.material().textures["DepthVDI"] = Texture(Vector3i(2 * 512, 512, ceil((totalMaxSupersegments / (512*512)).toDouble()).toInt()),  channels = 1, contents = depthBuffer, usageType = hashSetOf(
                Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        } else {
            compute.material().textures["InputVDI"] = Texture(Vector3i(numSupersegments*numLayers, windowHeight, windowWidth), 4, contents = colBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture)
                , type = bufType,
                mipmap = false,
//            normalized = false,
                minFilter = Texture.FilteringMode.NearestNeighbour,
                maxFilter = Texture.FilteringMode.NearestNeighbour
            )
            compute.material().textures["DepthVDI"] = Texture(Vector3i(2 * numSupersegments, windowHeight, windowWidth),  channels = 1, contents = depthBuffer, usageType = hashSetOf(
                Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        }

        if(runLengthEncoded) {
            val prefixArray: ByteArray = File(basePath + "${dataset}${vdiType}VDI${vdiParams}4_ndc_prefix").readBytes()

            val prefixBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * 4)

            prefixBuffer.put(prefixArray).flip()

            compute.material().textures["PrefixSums"] = Texture(Vector3i(windowHeight, windowWidth, 1), 1, contents = prefixBuffer, usageType = hashSetOf(
                Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = IntType(), mipmap = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        }

        scene.addChild(compute)

        val plane = FullscreenObject()
        plane.material().textures["diffuse"] = compute.material().textures["OutputViewport"]!!

        scene.addChild(plane)

        val light = PointLight(radius = 15.0f)
        light.position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)


//        thread {
//            while (running) {
//                box.rotation.rotateY(0.01f)
//                box.needsUpdate = true
////                box.material.textures["diffuse"] = compute.material.textures["OutputViewport"]!!
//
//                Thread.sleep(20)
//            }
//        }

//        thread{
//            while (!renderer!!.firstImageReady) {
//                Thread.sleep(50)
//            }
//
//            logger.info("will write the screenshot")
//            Thread.sleep(1000)
//
//            val baseDataset = "Rotstrat"
//            val viewNumber = 2
//            val communicatorType = "_16_0"
//            val totalRotation = 10
//
//            val path = "/home/argupta/Repositories/scenery-insitu/benchmarking/${baseDataset}/View${viewNumber}/volume_rendering/reference_comp${windowWidth}_${windowHeight}_${totalRotation.toInt()}"
//
//            renderer!!.screenshot("$path.png")
//
//        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIRendererSimple().main()
        }
    }
}
