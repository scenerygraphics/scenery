package graphics.scenery.tests.examples.volumes

import com.fasterxml.jackson.databind.ObjectMapper
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.Statistics
import graphics.scenery.utils.SystemHelpers
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.vdi.VDIDataIO
import net.imglib2.type.numeric.integer.IntType
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.scijava.ui.behaviour.ClickBehaviour
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.lang.Float.max
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */

class CustomNode : RichNode() {

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
}

class VDIRenderingExample : SceneryBase("VDI Rendering", System.getProperty("VDIBenchmark.WindowWidth")?.toInt()?: 1280, System.getProperty("VDIBenchmark.WindowHeight")?.toInt() ?: 720, wantREPL = false) {
    var hmd: TrackedStereoGlasses? = null

    val separateDepth = true
    val runLengthEncoded = System.getProperty("VDIBenchmark.RLE")?.toBoolean()?: false
    val recordMovie = false
    val profileMemoryAccesses = false
    val compute = CustomNode()
    val closeAfter = 600000L
    val autoClose = false
    var dataset = System.getProperty("VDIBenchmark.Dataset")?.toString()?: "Rayleigh_Taylor"
    var baseDataset = dataset
    val numOctreeLayers = 8.0
    val numSupersegments = System.getProperty("VDIBenchmark.NumSupersegments")?.toInt()?: 20
    val distributedVDI = System.getProperty("VDIBenchmark.Distributed")?.toBoolean()?: true
    val vo = System.getProperty("VDIBenchmark.Vo")?.toInt()?: 0
    var benchmarking = false
    val skipEmpty = false
    val viewNumber = 1

    var dynamicBenchmark = false
    var subsampling_benchmarks = true
    var desiredFrameRate = 72

    var maxFrameRate = 30

    val dynamicSubsampling = false

    val storeCamera = false
    val storeFrameTime = true
    val subsampleRay = false

    var subsamplingFactorImage = 1.0f

    var cameraMoving = false
    var cameraStopped = false

    val commSize = System.getProperty("VDIBenchmark.DistributedSize")?.toInt()?: 4
    val rank = System.getProperty("VDIBenchmark.DistributedRank")?.toInt()?: 0
    val communicatorType = "_${commSize}_${rank}"
//    val communicatorType = ""

    val cam: Camera = DetachedHeadCamera(hmd)
    val plane = FullscreenObject()

    val camTarget = when (dataset) {
        "Kingsnake" -> {
            Vector3f(1.920E+0f, -1.920E+0f,  1.491E+0f)
        }
        "Beechnut" -> {
            Vector3f(1.920E+0f, -1.920E+0f,  2.899E+0f)
        }
        "Simulation" -> {
            Vector3f(1.920E+0f, -1.920E+0f,  1.800E+0f)
        }
        "Rayleigh_Taylor" -> {
            Vector3f(1.920E+0f, -1.920E+0f,  1.920E+0f)
        }
        "BonePlug" -> {
            Vector3f(1.920E+0f, -6.986E-1f,  6.855E-1f)
        }
        "Rotstrat" -> {
            Vector3f( 1.920E+0f, -1.920E+0f,  1.920E+0f)
        }
        "Isotropic" -> {
            Vector3f(  1.920E+0f, -1.920E+0f,  1.912E+0f)
        }
        else -> {
            Vector3f(0f)
        }
    }


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

    override fun init () {

        val numLayers = if(separateDepth) {
            1
        } else {
            3
        }

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val effectiveWindowWidth: Int = (windowWidth * settings.get<Float>("Renderer.SupersamplingFactor")).toInt()
        val effectiveWindowHeight: Int = (windowHeight * settings.get<Float>("Renderer.SupersamplingFactor")).toInt()

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        with(cam) {
            spatial().position = Vector3f(-4.365f, 0.38f, 0.62f)
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        cam.spatial{

            if(dataset == "Kingsnake") {
                position = Vector3f( 4.622E+0f, -9.060E-1f, -1.047E+0f) //V1 for kingsnake
                rotation = Quaternionf( 5.288E-2, -9.096E-1, -1.222E-1,  3.936E-1)
            } else if (dataset == "Beechnut") {
                position = Vector3f(-2.607E+0f, -5.973E-1f,  2.415E+0f) // V1 for Beechnut
                rotation = Quaternionf(-9.418E-2, -7.363E-1, -1.048E-1, -6.618E-1)
            } else if (dataset == "Simulation") {
                position = Vector3f(2.041E-1f, -5.253E+0f, -1.321E+0f) //V1 for Simulation
                rotation = Quaternionf(9.134E-2, -9.009E-1,  3.558E-1, -2.313E-1)
            } else if (dataset == "Rayleigh_Taylor") {
                position = Vector3f( -2.300E+0f, -6.402E+0f,  1.100E+0f) //V1 for Rayleigh_Taylor
                rotation = Quaternionf(2.495E-1, -7.098E-1,  3.027E-1, -5.851E-1)
            } else if (dataset == "BonePlug") {
                position = Vector3f( 1.897E+0f, -5.994E-1f, -1.899E+0f) //V1 for Boneplug
                rotation = Quaternionf( 5.867E-5,  9.998E-1,  1.919E-2,  4.404E-3)
            } else if (dataset == "Rotstrat") {
                position = Vector3f( 7.234E+0f, -2.031E+0f,  4.331E+0f) //V1 for Rotstrat
                rotation = Quaternionf(8.013E-3,  5.417E-1, -5.146E-3, -8.405E-1)
            } else  if (dataset == "Isotropic") {
                position = Vector3f( -2.662E+0f, -1.730E+0f, -2.030E+0f) //V1 for Isotropic
                rotation = Quaternionf(9.088E-1, -6.552E-3, -4.170E-1,  1.429E-2)
            }
//            position = Vector3f( 4.458E+0f, -9.057E-1f,  4.193E+0f) //V2 for Kingsnake
//            rotation = Quaternionf( 1.238E-1, -3.649E-1,-4.902E-2,  9.215E-1)

//            position = Vector3f( 6.284E+0f, -4.932E-1f,  4.787E+0f) //V2 for Simulation
//            rotation = Quaternionf( 1.162E-1, -4.624E-1, -6.126E-2,  8.769E-1)
//
//            position = Vector3f(4.505E+0f, -5.993E-1f,  6.627E-1f) //V2 for BonePlug
//            rotation = Quaternionf(-1.353E-2,  7.101E-1,  1.361E-2, -7.039E-1)

        }

        cam.farPlaneDistance = 20.0f
        cam.target = camTarget

        if(distributedVDI) {
            dataset += communicatorType
        }

        val buff: ByteArray
        val depthBuff: ByteArray?
        val octBuff: ByteArray?

//        val basePath = "/home/aryaman/TestingData/"
        val basePath = "/home/aryaman/TestingData/FromCluster/"
//        val basePath = "/scratch/ws/1/argupta-vdi_generation/vdi_dumps/"
//        val basePath = "/home/aryaman/Repositories/DistributedVis/cmake-build-debug/"
//        val basePath = "/home/aryaman/Repositories/scenery-insitu/"
//        val basePath = "/scratch/ws/1/argupta-vdi_generation/vdi_dumps/"

        val vdiParams = "_${windowWidth}_${windowHeight}_${numSupersegments}_${vo}_"


        val file = FileInputStream(File(basePath + "${dataset}vdi${vdiParams}dump4"))
//        val comp = GZIPInputStream(file, 65536)

        val vdiData = VDIDataIO.read(file)

//        val vdiType = "Composited"
//        val vdiType = "SetOf"
        val vdiType = "Final"
//        val vdiType = "Sub"
//        val vdiType = ""

        logger.info("Fetching file with params: $vdiParams")

        if(runLengthEncoded) {
            buff = File(basePath + "${dataset}${vdiType}VDI${vdiParams}4_ndc_col_rle").readBytes()
            depthBuff = File(basePath + "${dataset}${vdiType}VDI${vdiParams}4_ndc_depth_rle").readBytes()
        } else {
            buff = File(basePath + "${dataset}${vdiType}VDI${vdiParams}4_ndc_col").readBytes()
            depthBuff = File(basePath + "${dataset}${vdiType}VDI${vdiParams}4_ndc_depth").readBytes()
        }
        if(skipEmpty) {
            octBuff = File(basePath + "${dataset}VDI${vdiParams}4_ndc_octree").readBytes()
            compute.skip_empty = true
        } else {
            octBuff = null
            compute.skip_empty = false
        }

        val opBuffer = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)
        val opNumSteps = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)
        val opNumIntersect = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)
        val opNumLists = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)
//        val opNumEmptyL = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)
//        val opNumSkipped = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)
//        val opNumBefFirst = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)
//        val opNumAfterLast = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)

        val totalMaxSupersegments = if(runLengthEncoded) {
            buff.size / (4*4).toFloat()
        } else {
            (numSupersegments * windowWidth * windowHeight).toFloat()
        }

        var colBuffer: ByteBuffer
        var depthBuffer: ByteBuffer?

        colBuffer = if(runLengthEncoded) {
            MemoryUtil.memCalloc(512 * 512 * ceil((totalMaxSupersegments / (512*512)).toDouble()).toInt() * 4 * 4)
        } else {
            MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * numLayers * 4 * 4)
        }
        colBuffer.put(buff).flip()
        colBuffer.limit(colBuffer.capacity())

        depthBuffer = if(runLengthEncoded) {
            MemoryUtil.memCalloc(2 * 512 * 512 * ceil((totalMaxSupersegments / (512*512)).toDouble()).toInt() * 4)
        } else {
            MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * 2 * 2 * 2)
        }
        depthBuffer.put(depthBuff).flip()
        depthBuffer.limit(depthBuffer.capacity())

//        val numVoxels = 2.0.pow(numOctreeLayers)
        val numGridCells = Vector3f(vdiData.metadata.windowDimensions.x.toFloat() / 8f, vdiData.metadata.windowDimensions.y.toFloat() / 8f, numSupersegments.toFloat())
//        val numGridCells = Vector3f(256f, 256f, 256f)
        val lowestLevel = MemoryUtil.memCalloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)
        if(skipEmpty) {
            lowestLevel.put(octBuff).flip()
        }

        compute.name = "compute node"

//        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("EfficientVDIRaycast.comp"), this@VDIRenderingExample::class.java))) {
        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("AmanatidesJumps.comp"), this@VDIRenderingExample::class.java))) {
            textures["OutputViewport"] = Texture.fromImage(Image(opBuffer, effectiveWindowWidth, effectiveWindowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))

            if (profileMemoryAccesses) {
                textures["NumSteps"] = Texture.fromImage(Image(opNumSteps, effectiveWindowWidth, effectiveWindowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
                textures["NumIntersectedSupsegs"] = Texture.fromImage(Image(opNumIntersect, effectiveWindowWidth, effectiveWindowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
                textures["NumLists"] = Texture.fromImage(Image(opNumLists, effectiveWindowWidth, effectiveWindowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
//                textures["NumEmptyLists"] = Texture.fromImage(Image(opNumEmptyL, effectiveWindowWidth, effectiveWindowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
//                textures["NumNotIntLists"] = Texture.fromImage(Image(opNumSkipped, effectiveWindowWidth, effectiveWindowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
//                textures["EmptyBeforeFirst"] = Texture.fromImage(Image(opNumBefFirst, effectiveWindowWidth, effectiveWindowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
//                textures["EmptyAfterLast"] = Texture.fromImage(Image(opNumAfterLast, effectiveWindowWidth, effectiveWindowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
            }
        }

        if(runLengthEncoded) {
            compute.material().textures["InputVDI"] = Texture(Vector3i(numLayers * 512, 512, ceil((totalMaxSupersegments / (512*512)).toDouble()).toInt()), 4, contents = colBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture)
                , type = FloatType(),
                mipmap = false,
//            normalized = false,
                minFilter = Texture.FilteringMode.NearestNeighbour,
                maxFilter = Texture.FilteringMode.NearestNeighbour
            )
            compute.material().textures["DepthVDI"] = Texture(Vector3i(2 * 512, 512, ceil((totalMaxSupersegments / (512*512)).toDouble()).toInt()),  channels = 1, contents = depthBuffer, usageType = hashSetOf(
                Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        } else {
            compute.material().textures["InputVDI"] = Texture(Vector3i(numSupersegments*numLayers, windowHeight, windowWidth), 4, contents = colBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture)
                , type = FloatType(),
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
            compute.totalGeneratedSupsegs = totalMaxSupersegments.toInt()
        }

//        if(skipEmpty) {
            compute.material().textures["OctreeCells"] = Texture(Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()), 1, type = UnsignedIntType(), contents = lowestLevel, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
//        }

        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(effectiveWindowWidth, effectiveWindowHeight, 1),
            invocationType = InvocationType.Permanent
        )

        compute.ProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem()
        compute.invProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem().invert()
        compute.ViewOriginal = vdiData.metadata.view
        compute.nw = vdiData.metadata.nw
        compute.vdiWidth = vdiData.metadata.windowDimensions.x
        compute.vdiHeight = vdiData.metadata.windowDimensions.y
        compute.invViewOriginal = Matrix4f(vdiData.metadata.view).invert()
        compute.invModel = Matrix4f(vdiData.metadata.model).invert()
        compute.volumeDims = vdiData.metadata.volumeDimensions
        compute.do_subsample = false

        logger.info("Projection: ${Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem()}")
        logger.info("View: ${vdiData.metadata.view}")
        logger.info("Actual view: ${cam.spatial().getTransformation()}")
        logger.info("nw: ${vdiData.metadata.nw}")


        scene.addChild(compute)

        scene.addChild(plane)
        plane.material().textures["diffuse"] = compute.material().textures["OutputViewport"]!!

        if(autoClose) {
            thread {
                Thread.sleep(closeAfter)
                renderer?.shouldClose = true
            }
        }

        if(recordMovie) {
            settings.set("VideoEncoder.Format", "HEVC")
            settings.set("VideoEncoder.Bitrate", 2000)
            settings.set("VideoEncoder.Quality", "Ultra")
            renderer?.recordMovie("${dataset}VDIRenderingTest.mp4")
        }


//        val opTexture = compute.material.textures["OutputViewport"]!!
//        var cnt = AtomicInteger(0)
//        (renderer as VulkanRenderer).persistentTextureRequests.add(opTexture to cnt)
//
//        thread {
//            if(cnt.get() == 1) {
//                val buf = opTexture.contents
//                if (buf != null) {
//                    SystemHelpers.dumpToFile(buf, "bruteforce.raw")
//                }
//            }
//        }

        thread {
            if (profileMemoryAccesses) {
                manageDebugTextures()
            }
        }

        if(subsampling_benchmarks && benchmarking) {
            logger.info("Only one type of benchmarks can be performed at a time!")
            benchmarking = false
        }

//        thread {
////            if(dynamicSubsampling) {
//                dynamicSubsampling()
////            }
//        }

        thread {
            if(dynamicBenchmark) {
                dynamicProfiling()
            }
        }

        if(recordMovie) {
            thread {
                camFlyThrough()
            }

            thread {
                Thread.sleep(10000)
                logger.info("The movie should be written!")
                renderer?.recordMovie()
            }
        }

        thread {
            Thread.sleep(2000)
            logger.info("${cam.spatial().position}")
            logger.info("${cam.spatial().rotation}")
        }

    }

    fun downsampleImage(factor: Float, wholeFrameBuffer: Boolean = false) {

        if(wholeFrameBuffer) {
            settings.set("Renderer.SupersamplingFactor", factor)
            settings.set("Renderer.SupersamplingFactor", factor)

            (compute.metadata["ComputeMetadata"] as ComputeMetadata).active = false

            (renderer as VulkanRenderer).swapchainRecreator.mustRecreate = true

//        Thread.sleep(5000)

            val effectiveWindowWidth: Int = (windowWidth * factor).toInt()
            val effectiveWindowHeight: Int = (windowHeight * factor).toInt()

            logger.info("effective window width has been set to: $effectiveWindowWidth and height to: $effectiveWindowHeight")

            val opBuffer = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)

            compute.material().textures["OutputViewport"] = Texture.fromImage(
                Image(opBuffer, effectiveWindowWidth, effectiveWindowHeight),
                usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture)
            )

            compute.metadata["ComputeMetadata"] = ComputeMetadata(
                workSizes = Vector3i(effectiveWindowWidth, effectiveWindowHeight, 1),
                invocationType = InvocationType.Permanent
            )

            (compute.metadata["ComputeMetadata"] as ComputeMetadata).active = true

            plane.material().textures["diffuse"] = compute.material().textures["OutputViewport"]!!
        } else {
            compute.downImage = factor
            plane.downImage = factor
        }
    }

    fun setStratifiedDownsampling(stratified: Boolean) {
        compute.stratified_downsampling = stratified
    }

    fun setEmptySpaceSkipping(skip: Boolean) {
        compute.skip_empty = skip
    }

    fun setDownsamplingFactor(factor: Float) {
        compute.sampling_factor = factor
    }

    fun setMaxDownsampleSteps(steps: Int) {
        compute.max_samples = steps
    }

    fun doDownsampling(downsample: Boolean) {
        setEmptySpaceSkipping(true)
        compute.do_subsample = downsample
    }

    private fun dynamicProfiling() {
        val r = (hub.get(SceneryElement.Renderer) as Renderer)

        val targetFrameTime = 1.0f / desiredFrameRate

        val toleranceFPS = 5f
        val minFPS = desiredFrameRate - toleranceFPS
        val toleranceTime = abs(targetFrameTime - (1.0f / minFPS))

        var frameEnd: Long

        var frameTime: Float
        var avgFrameTime = 0f
        var avgLength = 100

        val kP = 20.5f
        val kI = 5.5f
//        val kI = 0f
        val kD = 1

        var subsampleAlongImage = true

        val loopCycle = 1
        var totalLoss = 0f

        subsamplingFactorImage = 1.0f
        var subsamplingFactorRay = 0.1f
        var prevFactor = 1.0f

        val d_iChange = 0.0f
        val d_rChange = 0.2f

        var frameCount = 0

//        doDownsampling(true)
//        setDownsamplingFactor(0.1f)

        val frameTimeList = mutableListOf<Float>()

//        renderer!!.recordMovie("/datapot/aryaman/owncloud/VDI_Benchmarks/VDI_basic.mp4")

        Thread.sleep(5000)
        var frameStart = System.nanoTime()
        var firstFrame = true

        (r as VulkanRenderer).postRenderLambdas.add {
            if(frameCount%10 == 0) {
                rotateCamera(1f, dataset=="Simulation")
            }
            if(frameCount == 100) {
                if(subsampleRay) {
                    doDownsampling(true)
                    setDownsamplingFactor(0.3f)
                    logger.info("Downsampling factor set")
                }
            }
        }

        (r as VulkanRenderer).postRenderLambdas.add {

            if (!firstFrame) {
                frameEnd = System.nanoTime()
                frameTime = (frameEnd - frameStart) / 1e9f


                frameCount++
                frameTimeList.add(frameTime)


                if (frameCount == 500) {
                    val fw = FileWriter("/datapot/aryaman/owncloud/VDI_Benchmarks/${dataset}_${dynamicSubsampling}_${subsampleRay}_frame_times.csv", false)
                    val bw = BufferedWriter(fw)

                    frameTimeList.forEach {
                        bw.append("${it}, ")
                    }

                    bw.flush()
//
                    logger.warn("The file has been written!")

//                    renderer!!.recordMovie()
                    Thread.sleep(1000)

                    renderer!!.shouldClose = true
                }

                if(dynamicSubsampling) {
                    val error =
//                if((frameTime >= (targetFrameTime - toleranceTime)) && (frameTime <= (targetFrameTime + toleranceTime))) {
//                    0f
//                } else {
                        frameTime - targetFrameTime
//                }

                    avgFrameTime = if (avgFrameTime == 0f) {
                        frameTime
                    } else {
                        avgFrameTime - avgFrameTime / avgLength + frameTime / avgLength
                    }

                    val avgError =
                        if ((avgFrameTime >= (targetFrameTime - toleranceTime)) && (avgFrameTime <= (targetFrameTime + toleranceTime))) {
                            0f
                        } else {
                            avgFrameTime - targetFrameTime
                        }

//                logger.info("Frame time: $frameTime, avg frame time: $avgFrameTime and target: $targetFrameTime and tolerance: $toleranceTime")

                    val output = kP * error + kI * avgError

//                doDownsampling(false)

                    subsamplingFactorImage -= output

                    subsamplingFactorImage = max(0.05f, subsamplingFactorImage)
                    subsamplingFactorImage = min(1.0f, subsamplingFactorImage)

//                    val downImage = max(d_iChange, subsamplingFactorImage)
                    val downImage = subsamplingFactorImage

                    logger.info("Frame time: $frameTime. error was: $error, avg error: $avgError and therefore setting factor to: $subsamplingFactorImage")

                    if (abs(downImage - prevFactor) > 0.05) {
                        logger.warn("changing the factor")
                        downsampleImage(downImage)
                        prevFactor = downImage
                    }
                }
            }

            frameStart = System.nanoTime()
            firstFrame = false
        }
    }

    private fun lookAround(speedFactor: Long = 1L) {
        val waitTime: Long = 5 * speedFactor
        val iterations = 100

        logger.info("starting look around")

        var cnt = 0
        while (cnt < iterations) {
            rotateCamera(-0.2f, true)
            Thread.sleep(waitTime)
            cnt++
        }
        cnt = 0
        while (cnt < iterations) {
            rotateCamera(0.2f)
            Thread.sleep(waitTime)
            cnt++
        }
        cnt = 0
        while (cnt < iterations) {
            rotateCamera(0.2f, true)
            Thread.sleep(waitTime)
            cnt++
        }
        cnt = 0
        while (cnt < 100) {
            rotateCamera(-0.2f)
            Thread.sleep(waitTime)
            cnt++
        }
    }

    public fun camFlyThrough() {
        val r = (hub.get(SceneryElement.Renderer) as Renderer)

        while(!r.firstImageReady) {
            Thread.sleep(200)
        }

        Thread.sleep(20000)
//        renderer!!.recordMovie("/datapot/aryaman/owncloud/VDI_Benchmarks/${dataset}_VDI.mp4")

        var speedFactor = 2L

        lookAround(speedFactor)
//        lookAround(speedFactor)

        var cnt = 0
        while (cnt < 25) {
            rotateCamera(0.2f)
            Thread.sleep(5 * speedFactor)
            cnt++
        }

        Thread.sleep(2000)

//        r.screenshot("/datapot/aryaman/owncloud/Final_PacificVis_Supplement/${dataset}_VDI_first.png")
//
//        Thread.sleep(4000)

        cnt = 0
        while (cnt < 125) {
            rotateCamera(0.2f)
            Thread.sleep(5 * speedFactor)
            cnt++
        }

        speedFactor = 3L

        lookAround()
        Thread.sleep(2000)

//        zoomCamera(0.995f, 1000f, speedFactor)

        Thread.sleep(500)

//        r.screenshot("/datapot/aryaman/owncloud/Final_PacificVis_Supplement/${dataset}_VDI_second.png")

//        Thread.sleep(4000)

//        lookAround(speedFactor)

//        r.recordMovie()

//        lookAround()
//
//        cnt = 0
//        while (cnt < 100) {
//            rotateCamera(-0.32f, true)
//            Thread.sleep(80)
//            cnt++
//        }
//
//        Thread.sleep(2500)
//
//        r.screenshot("/datapot/aryaman/owncloud/Final_PacificVis_Supplement/${dataset}_VDI_second.png")
//
//        Thread.sleep(2500)
//
//        lookAround()
        Thread.sleep(2500)

        Thread.sleep(1000)
    }

    private fun moveCamera(yawRot: Float, pitchRot: Float, maxYaw: Float, maxPitch: Float, minPitch: Float, minYaw: Float, totalY: Float, totalP: Float, duration: Float) {

        var totalYaw = totalY
        var totalPitch = totalP

        var yaw = yawRot
        var pitch = pitchRot

        val startTime = System.nanoTime()

        var cnt = 0

        val list: MutableList<Any> = ArrayList()
        val listDi: MutableList<Any> = ArrayList()

        while (true) {

            cnt += 1

            if(totalYaw < maxYaw && totalYaw > minYaw) {
                rotateCamera(yaw)
                totalYaw += yaw
            } else {
                yaw *= -1f
                rotateCamera(yaw)
                totalYaw += yaw
            }

            if (totalPitch < maxPitch && totalPitch > minPitch) {
                rotateCamera(pitch, true)
                totalPitch += pitch
            } else {
                pitch *= -1f
                rotateCamera(pitch, true)
                totalPitch += pitch
            }
            Thread.sleep(50)

            val currentTime = System.nanoTime()

            if ((currentTime - startTime)/1e6 > duration) {
                break
            }

            if(cnt % 5 == 0 && storeCamera) {
                //save the camera and Di

                val rotArray = floatArrayOf(cam.spatial().rotation.x, cam.spatial().rotation.y, cam.spatial().rotation.z, cam.spatial().rotation.w)
                val posArray = floatArrayOf(cam.spatial().position.x(), cam.spatial().position.y(), cam.spatial().position.z())

                list.add(rotArray)
                list.add(posArray)

                listDi.add(subsamplingFactorImage)

                logger.info("Added to the list $cnt")

            }
        }

        if(storeCamera) {
            val objectMapper = ObjectMapper(MessagePackFactory())

            val bytes = objectMapper.writeValueAsBytes(list)

            Files.write(Paths.get("${dataset}_${subsampleRay}_camera.txt"), bytes)

            val bytesDi = objectMapper.writeValueAsBytes(listDi)

            Files.write(Paths.get("${dataset}_${subsampleRay}_di.txt"), bytesDi)

            logger.warn("The file has been written")
        }

    }

    private fun recordCamera() {
        val r = (hub.get(SceneryElement.Renderer) as Renderer)

        while(!r.firstImageReady) {
            Thread.sleep(200)
        }

        Thread.sleep(2000)

        val list: MutableList<Any> = ArrayList()

        val iterations = 200

        for (i in 1..iterations) {

            val rotArray = floatArrayOf(cam.spatial().rotation.x, cam.spatial().rotation.y, cam.spatial().rotation.z, cam.spatial().rotation.w)
            val posArray = floatArrayOf(cam.spatial().position.x(), cam.spatial().position.y(), cam.spatial().position.z())

            list.add(rotArray)
            list.add(posArray)

            logger.info("Added to the list $i")

            Thread.sleep(50)
        }

        val objectMapper = ObjectMapper(MessagePackFactory())

        val bytes = objectMapper.writeValueAsBytes(list)

        Files.write(Paths.get("CameraPoses.txt"), bytes)

        logger.warn("The file has been written")

    }

    private fun doBenchmarks() {
        val r = (hub.get(SceneryElement.Renderer) as Renderer)
        var stats = hub.get<Statistics>()!!

        while(!r.firstImageReady) {
            Thread.sleep(200)
        }

        val rotationInterval = 5f
        var totalRotation = 0f

        for(i in 1..9) {
            val path = if(skipEmpty) {
                "benchmarking/${baseDataset}/View${viewNumber}/vdi$numSupersegments/empty/vdi" + communicatorType + "_${windowWidth}_${windowHeight}_${totalRotation.toInt()}"
            } else {
                "benchmarking/${baseDataset}/View${viewNumber}/vdi$numSupersegments/vdi"+ communicatorType +"_${windowWidth}_${windowHeight}_${totalRotation.toInt()}"
            }
            // take screenshot and wait for async writing
            r.screenshot("$path.png")
            Thread.sleep(1000L)
            stats.clear("Renderer.fps")

            // collect data for a few secs
            Thread.sleep(1000)

            // write out CSV with fps data
            val fps = stats.get("Renderer.fps")!!
            File("$path.csv").writeText("${fps.avg()};${fps.min()};${fps.max()};${fps.stddev()};${fps.data.size}")

            rotateCamera(rotationInterval)
            totalRotation = i * rotationInterval
            Thread.sleep(1000)
        }
    }

    fun zoomCamera(factor: Float, duration: Float, speedFactor: Long = 1) {
        cam.targeted = true

        val startTime = System.nanoTime()
        while (true) {
            val distance = (camTarget - cam.spatial().position).length()

            cam.spatial().position = camTarget + cam.forward * distance * (-1.0f * factor)

            Thread.sleep(50 * speedFactor)

            if((System.nanoTime() - startTime)/1e6 > duration) {
                break
            }
        }
    }

    fun rotateCamera(degrees: Float, pitch: Boolean = false) {
        cam.targeted = true
        val frameYaw: Float
        val framePitch: Float

        if(pitch) {
            framePitch = degrees / 180.0f * Math.PI.toFloat()
            frameYaw = 0f
        } else {
            frameYaw = degrees / 180.0f * Math.PI.toFloat()
            framePitch = 0f
        }

        // first calculate the total rotation quaternion to be applied to the camera
        val yawQ = Quaternionf().rotateXYZ(0.0f, frameYaw, 0.0f).normalize()
        val pitchQ = Quaternionf().rotateXYZ(framePitch, 0.0f, 0.0f).normalize()

//        logger.info("cam target: ${camTarget}")

        val distance = (camTarget - cam.spatial().position).length()
        cam.spatial().rotation = pitchQ.mul(cam.spatial().rotation).mul(yawQ).normalize()
        cam.spatial().position = camTarget + cam.forward * distance * (-1.0f)
//        logger.info("new camera pos: ${cam.spatial().position}")
//        logger.info("new camera rotation: ${cam.spatial().rotation}")
//        logger.info("camera forward: ${cam.forward}")
    }


    override fun inputSetup() {
        setupCameraModeSwitching()

        inputHandler?.addBehaviour("rotate_camera", ClickBehaviour { _, _ ->
            rotateCamera(5f, dataset.contains("Simulation"))
        })
        inputHandler?.addKeyBinding("rotate_camera", "R")

        inputHandler?.addBehaviour("rotate_camera_pitch", ClickBehaviour { _, _ ->
            rotateCamera(10f, true)
        })
        inputHandler?.addKeyBinding("rotate_camera_pitch", "T")

        inputHandler?.addBehaviour("downsample_image", ClickBehaviour { _, _ ->
            downsampleImage(0.5f)
        })
        inputHandler?.addKeyBinding("downsample_image", "O")
    }



    private fun manageDebugTextures() {
        var numStepsBuff: ByteBuffer?
        var numIntersectedBuff: ByteBuffer?
        var numLists: ByteBuffer?
//        var numSkippedBuff: ByteBuffer?
//        var numEmptyLBuff: ByteBuffer?
//        var emptyBeforeFirstBuff: ByteBuffer?
//        var emptyAfterLastBuff: ByteBuffer?

        while(renderer?.firstImageReady == false) {
            Thread.sleep(50)
        }

        logger.info("First image is ready")

        val numSteps = compute.material().textures["NumSteps"]!!
        val thirdAtomic = AtomicInteger(0)
//
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (numSteps to thirdAtomic)

        val numIntersectedLists = compute.material().textures["NumLists"]!!
        val firstAtomic = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (numIntersectedLists to firstAtomic)

        val numIntersectedSupsegs = compute.material().textures["NumIntersectedSupsegs"]!!
        val secondAtomic = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (numIntersectedSupsegs to secondAtomic)

//        val numEmptyLists = compute.material().textures["NumEmptyLists"]!!
//        val thirdAtomic = AtomicInteger(0)
//        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (numEmptyLists to thirdAtomic)

//        val numNotIntLists = compute.material().textures["NumNotIntLists"]!!
//        val fourthAtomic = AtomicInteger(0)
//        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (numNotIntLists to fourthAtomic)

//        val emptyBeforeFirst = compute.material().textures["EmptyBeforeFirst"]!!
//        val fifthAtomic = AtomicInteger(0)
//        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (numNotIntLists to fifthAtomic)

//        val emptyAfterLast = compute.material().textures["EmptyAfterLast"]!!
//        val sixthAtomic = AtomicInteger(0)
//        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (numNotIntLists to sixthAtomic)

        var prevAtomic1 = firstAtomic.get()
        var prevAtomic2 = secondAtomic.get()
        var prevAtomic3 = secondAtomic.get()

//        val angles = listOf(5f, 10f, 15f, 20f, 25f, 30f, 35f, 45f)
        val angles = listOf(30f, 45f)

        var curRotation = 0f

        for (angle in angles) {
            while(firstAtomic.get() == prevAtomic1 && secondAtomic.get() == prevAtomic2 && thirdAtomic.get() == prevAtomic3) {
                Thread.sleep(20)
            }

            numStepsBuff = numSteps.contents
            numIntersectedBuff = numIntersectedSupsegs.contents
            numLists = numIntersectedLists.contents
//            numEmptyLBuff = numEmptyLists.contents
//            numSkippedBuff = numNotIntLists.contents
//            emptyBeforeFirstBuff = emptyBeforeFirst.contents
//            emptyAfterLastBuff = emptyAfterLast.contents

            SystemHelpers.dumpToFile(numStepsBuff!!, "num_steps${curRotation.toInt()}.raw")
//            SystemHelpers.dumpToFile(numSkippedBuff!!, "num_skipped.raw")
            SystemHelpers.dumpToFile(numIntersectedBuff!!, "${dataset}_num_intersected${curRotation.toInt()}.raw")
            SystemHelpers.dumpToFile(numLists!!, "${dataset}_num_list${curRotation.toInt()}.raw")
//            SystemHelpers.dumpToFile(numEmptyLBuff!!, "num_empty_lists.raw")
//            SystemHelpers.dumpToFile(emptyBeforeFirstBuff!!, "num_empty_before_first.raw")
//            SystemHelpers.dumpToFile(emptyAfterLastBuff!!, "num_empty_after_last.raw")
            logger.info("Written the textures at angle $curRotation")

            rotateCamera(angle - curRotation)
            curRotation = angle

            Thread.sleep(1000)

            prevAtomic1 = firstAtomic.get()
            prevAtomic2 = secondAtomic.get()
            prevAtomic3 = thirdAtomic.get()
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIRenderingExample().main()
        }
    }

}
