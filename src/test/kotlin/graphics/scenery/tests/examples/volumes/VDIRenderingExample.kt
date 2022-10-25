package graphics.scenery.tests.examples.volumes

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
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import org.scijava.ui.behaviour.ClickBehaviour
import java.io.File
import java.io.FileInputStream
import java.lang.Float.max
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.abs
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
    var do_subsample = false

    @ShaderProperty
    var max_samples = 50

    @ShaderProperty
    var sampling_factor = 0.1f

    @ShaderProperty
    var downImage = 0.5f

    @ShaderProperty
    var skip_empty = true

    @ShaderProperty
    var stratified_downsampling = false
}

class VDIRenderingExample : SceneryBase("VDI Rendering", System.getProperty("VDIBenchmark.WindowWidth")?.toInt()?: 1280, System.getProperty("VDIBenchmark.WindowHeight")?.toInt() ?: 720, wantREPL = false) {
    var hmd: TrackedStereoGlasses? = null

    val separateDepth = true
    val profileMemoryAccesses = false
    val compute = CustomNode()
    val closeAfter = 60000L
    val autoClose = false
    var dataset = System.getProperty("VDIBenchmark.Dataset")?.toString()?: "Kingsnake"
    var baseDataset = dataset
    val numOctreeLayers = 8.0
    val numSupersegments = System.getProperty("VDIBenchmark.NumSupersegments")?.toInt()?: 30
    val vo = System.getProperty("VDIBenchmark.Vo")?.toInt()?: 0
    var benchmarking = false
    val skipEmpty = true
    val viewNumber = 1
    val dynamicSubsampling = false
    var subsampling_benchmarks = false
    var desiredFrameRate = 60
    var maxFrameRate = 30

    val commSize = 4
    val rank = 0
//    val communicatorType = "_${commSize}_${rank}"
    val communicatorType = ""

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
        "BonePlug" -> {
            Vector3f(1.920E+0f, -6.986E-1f,  6.855E-1f)
        }
        "Rotstrat" -> {
            Vector3f( 1.920E+0f, -1.920E+0f,  1.800E+0f)
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
            } else if (dataset == "BonePlug") {
                position = Vector3f( 1.897E+0f, -5.994E-1f, -1.899E+0f) //V1 for Boneplug
                rotation = Quaternionf( 5.867E-5,  9.998E-1,  1.919E-2,  4.404E-3)
            } else if (dataset == "Rotstrat") {
                position = Vector3f(2.041E-1f, -5.253E+0f, -1.321E+0f) //V1 for Simulation
                rotation = Quaternionf(9.134E-2, -9.009E-1,  3.558E-1, -2.313E-1)
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

        dataset += communicatorType

        val buff: ByteArray
        val depthBuff: ByteArray?
        val octBuff: ByteArray?
        val noiseData: ByteArray

//        val basePath = "/home/aryaman/TestingData/"
//        val basePath = "/home/aryaman/TestingData/FromCluster/"
//        val basePath = "/home/aryaman/Repositories/DistributedVis/cmake-build-debug/"
        val basePath = "/home/aryaman/Repositories/scenery-insitu/"

//        val vdiParams = "_${windowWidth}_${windowHeight}_${numSupersegments}_${vo}_"
        val vdiParams = ""

        val file = FileInputStream(File(basePath + "${dataset}vdi${vdiParams}dump4"))
//        val comp = GZIPInputStream(file, 65536)

        val vdiData = VDIDataIO.read(file)

//        val vdiType = "Composited"
//        val vdiType = "SetOf"
//        val vdiType = "Final"
//        val vdiType = "Sub"
        val vdiType = ""

        if(separateDepth) {
            buff = File(basePath + "${dataset}${vdiType}VDI${vdiParams}4_ndc_col").readBytes()
            depthBuff = File(basePath + "${dataset}${vdiType}VDI${vdiParams}4_ndc_depth").readBytes()

        } else {

            buff = File(basePath + "${dataset}VDI10_ndc").readBytes()
            depthBuff = null
        }
        if(skipEmpty) {
            octBuff = File(basePath + "${dataset}VDI${vdiParams}4_ndc_octree").readBytes()
        } else {
            octBuff = null
        }

        noiseData = File(basePath + "NoiseTexture_Random.raw").readBytes()

        val opBuffer = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)
        val opNumSteps = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)
        val opNumIntersect = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)
        val opNumLists = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)
//        val opNumEmptyL = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)
//        val opNumSkipped = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)
//        val opNumBefFirst = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)
//        val opNumAfterLast = MemoryUtil.memCalloc(effectiveWindowWidth * effectiveWindowHeight * 4)

        var colBuffer: ByteBuffer
        var depthBuffer: ByteBuffer?

        colBuffer = MemoryUtil.memCalloc(vdiData.metadata.windowDimensions.y, vdiData.metadata.windowDimensions.x * numSupersegments * numLayers * 4 * 4)
        colBuffer.put(buff).flip()

        if(separateDepth) {
            depthBuffer = MemoryUtil.memCalloc(vdiData.metadata.windowDimensions.y, vdiData.metadata.windowDimensions.x * numSupersegments * 4 * 2) //TODO: IMP! This should be 2*2 for uint
            depthBuffer.put(depthBuff).flip()
        } else {
            depthBuffer = null
        }

//        val numVoxels = 2.0.pow(numOctreeLayers)
        val numGridCells = Vector3f(vdiData.metadata.windowDimensions.x.toFloat() / 8f, vdiData.metadata.windowDimensions.y.toFloat() / 8f, numSupersegments.toFloat())
//        val numGridCells = Vector3f(256f, 256f, 256f)
        val lowestLevel = MemoryUtil.memCalloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)
        if(skipEmpty) {
            lowestLevel.put(octBuff).flip()
        }

        val noiseTexture = MemoryUtil.memCalloc(1920*1080 * 4)

        noiseTexture.put(noiseData).flip()

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

            textures["InputVDI"] = Texture(Vector3i(numLayers*numSupersegments, vdiData.metadata.windowDimensions.y, vdiData.metadata.windowDimensions.x), 4, contents = colBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
                type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        }

        if(separateDepth) {
            compute.material().textures["DepthVDI"] = Texture(Vector3i(2*numSupersegments, vdiData.metadata.windowDimensions.y, vdiData.metadata.windowDimensions.x),  channels = 1, contents = depthBuffer, usageType = hashSetOf(
                Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
//            compute.material().textures["DepthVDI"] = Texture(Vector3i(2 * numSupersegments, windowHeight, windowWidth),  channels = 1, contents = depthBuffer, usageType = hashSetOf(
//                Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        }
        if(skipEmpty) {
            compute.material().textures["OctreeCells"] = Texture(Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()), 1, type = UnsignedIntType(), contents = lowestLevel, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        }

        compute.material().textures["NoiseTexture"] = Texture(Vector3i(1920, 1080, 1),  channels = 1, contents = noiseTexture, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)


        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(effectiveWindowWidth, effectiveWindowHeight, 1),
            invocationType = InvocationType.Permanent
        )

        compute.ProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem()
        compute.invProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem().invert()
        compute.ViewOriginal = vdiData.metadata.view
        compute.nw = vdiData.metadata.nw
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

        thread {
            if(dynamicSubsampling) {
                dynamicSubsampling()
            }
        }

        thread {
            if(benchmarking) {
                doBenchmarks()
            }
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

    private fun dynamicSubsampling() {
        val r = (hub.get(SceneryElement.Renderer) as Renderer)

        val targetFrameTime = 1.0f / desiredFrameRate

        val toleranceFPS = 10f
        val minFPS = desiredFrameRate - toleranceFPS
        val toleranceTime = abs(targetFrameTime - (1.0f / minFPS))

        var frameStart = System.nanoTime()
        var frameEnd: Long

        var frameTime: Float
        var avgFrameTime = 0f
        var avgLength = 100

        val kP = 0.7f
        val kI = 1.5f
        val kD = 1

        val loopCycle = 1
        var totalLoss = 0f

        var subsamplingFactor = 1.0f
        var prevFactor = 1.0f

        val d_iChange = 0.5f
        val d_rStart = 0.3f

        var frameCount = 0

        (r as VulkanRenderer).postRenderLambdas.add {
            frameCount++

            if(frameCount > 100) {
                frameEnd = System.nanoTime()
                frameTime = (frameEnd - frameStart)/1e9f
                val error = if((frameTime >= (targetFrameTime - toleranceTime)) && (frameTime <= (targetFrameTime + toleranceTime))) {
                    0f
                } else {
                    frameTime - targetFrameTime
                }

                avgFrameTime = if(avgFrameTime == 0f) {
                    frameTime
                } else {
                    avgFrameTime - avgFrameTime/avgLength + frameTime/avgLength
                }

                val avgError = if((avgFrameTime >= (targetFrameTime - toleranceTime)) && (avgFrameTime <= (targetFrameTime + toleranceTime))) {
                    0f
                } else {
                    avgFrameTime - targetFrameTime
                }

                logger.info("Frame time: $frameTime, avg frame time: $avgFrameTime and target: $targetFrameTime and tolerance: $toleranceTime")

                if(subsamplingFactor < 1.0f) {
                    totalLoss += error
                }

                val output = kP * error + kI * avgError

                subsamplingFactor -= output

                subsamplingFactor = max(0.001f, subsamplingFactor)
                subsamplingFactor = min(1.0f, subsamplingFactor)

                val downImage = max(d_iChange, subsamplingFactor)

                logger.info("error was: $error, avg error: $avgError and therefore setting factor to: $subsamplingFactor")

                if(subsamplingFactor < d_iChange) {
                    val fUnit = abs(subsamplingFactor - 0.001f) / (d_iChange - 0.001f)
                    val downRay = fUnit * (d_rStart - 0.01f) + 0.01f

                    doDownsampling(true)
                    setDownsamplingFactor(downRay)
                } else {
                    doDownsampling(false)
                }

                if(abs(downImage - prevFactor) > 0.1) {
                    logger.warn("changing the factor")
                    downsampleImage(downImage)
                    prevFactor = downImage
                }
            }
            frameStart = System.nanoTime()
        }

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

        logger.info("cam target: ${camTarget}")

        val distance = (camTarget - cam.spatial().position).length()
        cam.spatial().rotation = pitchQ.mul(cam.spatial().rotation).mul(yawQ).normalize()
        cam.spatial().position = camTarget + cam.forward * distance * (-1.0f)
        logger.info("new camera pos: ${cam.spatial().position}")
        logger.info("new camera rotation: ${cam.spatial().rotation}")
        logger.info("camera forward: ${cam.forward}")
    }


    override fun inputSetup() {
        setupCameraModeSwitching()

        inputHandler?.addBehaviour("rotate_camera", ClickBehaviour { _, _ ->
            rotateCamera(10f)
        })
        inputHandler?.addKeyBinding("rotate_camera", "R")

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
