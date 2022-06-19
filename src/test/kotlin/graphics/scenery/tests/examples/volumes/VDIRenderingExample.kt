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
import graphics.scenery.volumes.VolumeManager
import graphics.scenery.volumes.vdi.VDIData
import graphics.scenery.volumes.vdi.VDIDataIO
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import org.scijava.ui.behaviour.ClickBehaviour
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread
import kotlin.math.pow

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
}

class VDIRenderingExample : SceneryBase("VDI Rendering", 1280, 720, wantREPL = false) {
    var hmd: TrackedStereoGlasses? = null

    val separateDepth = true
    val profileMemoryAccesses = false
    val compute = CustomNode()
    val closeAfter = 10000L
    var dataset = "Kingsnake"
    var baseDataset = dataset
    val numOctreeLayers = 8.0
    val numSupersegments = 20
    var benchmarking = true
    val skipEmpty = false
    val viewNumber = 1
    val subsampling = false
    var subsampling_benchmarks = false
    var desiredFrameRate = 30
    var maxFrameRate = 90

    val commSize = 4
    val rank = 0
    val communicatorType = "_${commSize}_${rank}"

    val cam: Camera = DetachedHeadCamera(hmd)

//    val camTarget = Vector3f(1.920E+0f, -1.920E+0f,  1.140E+0f)
    //    val camTarget = Vector3f(1.920E+0f, -1.920E+0f,  2.899E+0f) //beechnut
//    val camTarget = Vector3f(1.920E+0f, -1.920E+0f,  1.800E+0f) //simulation
    val camTarget = Vector3f(1.920E+0f, -1.920E+0f,  1.491E+0f) //kingsnake

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
            //The original viewpoint for procedural volume data
//            position = Vector3f(3.213f, 8.264E-1f, -9.844E-1f)
//            rotation = Quaternionf(3.049E-2, 9.596E-1, -1.144E-1, -2.553E-1)

            //         optimized depth calculation working at this view point, opacity calculation looking reasonable
//        rotation = Quaternionf(5.449E-2,  8.801E-1, -1.041E-1, -4.601E-1)
//        position = Vector3f(6.639E+0f,  1.092E+0f, -1.584E-1f)

//        same as previous
//        cam.position = Vector3f(1.881E+0f,  5.558E+0f, -7.854E-1f)
//        cam.rotation = Quaternionf(-2.733E-2, 9.552E-1, 2.793E-1, -9.365E-2)

//        position = Vector3f(-3.435E+0f,  1.109E+0f,  6.433E+0f)
//        rotation = Quaternionf(-3.985E-2,  5.315E-1, -2.510E-2,  8.457E-1)

//        cam.position = Vector3f(3.729E+0f,  8.263E-1f, -6.808E-1f)
//        cam.rotation = Quaternionf(2.731E-2,  9.596E-1, -9.999E-2, -2.616E-1)

//        cam.position = Vector3f(3.729E+0f,  8.263E-1f, -6.808E-1f)
//        cam.rotation = Quaternionf(1.499E-2,  9.660E-1, -5.738E-2, -2.517E-1)

//        cam.position = Vector3f(4.374E+0f, 8.262E-1f,-3.773E-1f)
//        cam.rotation = Quaternionf(2.247E-2,  9.707E-1, -1.003E-1, -2.171E-1)

//        position = Vector3f( 3.853E+0f,  7.480E-1f, -9.672E-1f)
//        rotation = Quaternionf( 4.521E-2,  9.413E-1, -1.398E-1, -3.040E-1)
//
//        cam.position = Vector3f( 3.853E+0f,  7.480E-1f, -9.672E-1f)
//        cam.rotation = Quaternionf( 4.521E-2,  9.413E-1, -1.398E-1, -3.040E-1)

//            position = Vector3f( 5.286E+0f,  8.330E-1f,  3.088E-1f) //This is the viewpoint at which I recorded 13 fps with float depths (Stagbeetle, 1070)
//            rotation = Quaternionf( 4.208E-2,  9.225E-1, -1.051E-1, -3.690E-1)
//
//            position = Vector3f( 2.869E+0f,  8.955E-1f, -9.165E-1f)
//            rotation = Quaternionf( 2.509E-2,  9.739E-1, -1.351E-1, -1.805E-1)

            position = Vector3f(3.345E+0f, -8.651E-1f, -2.857E+0f)
            rotation = Quaternionf(3.148E-2, -9.600E-1, -1.204E-1,  2.509E-1)

            position = Vector3f(5.436E+0f, -8.650E-1f, -7.923E-1f)
            rotation = Quaternionf(7.029E-2, -8.529E-1, -1.191E-1,  5.034E-1)

            position = Vector3f(4.004E+0f, -1.398E+0f, -2.170E+0f) //this is the actual 0 degree
            rotation = Quaternionf(-1.838E-2,  9.587E-1,  6.367E-2, -2.767E-1)

            position = Vector3f(3.174E+0f, -1.326E+0f, -2.554E+0f)
            rotation = Quaternionf(-1.276E-2,  9.791E-1,  6.503E-2, -1.921E-1)

//            position = Vector3f(3.174E+0f, -1.326E+0f, -2.554E+0f)
//            rotation = Quaternionf(-1.484E-2,  9.737E-1,  6.638E-2, -2.176E-1)

//            position = Vector3f(-2.607E+0f, -5.973E-1f,  2.415E+0f) //this is the actual 0 degree (maybe beechnut)
//            rotation = Quaternionf(-9.418E-2, -7.363E-1, -1.048E-1, -6.618E-1)

//            position = Vector3f(4.908E+0f, -4.931E-1f, -2.563E+0f) //V1 for Simulation
//            rotation = Quaternionf( 3.887E-2, -9.470E-1, -1.255E-1,  2.931E-1)

            position = Vector3f( 4.622E+0f, -9.060E-1f, -1.047E+0f) //V1 for kingsnake
            rotation = Quaternionf( 5.288E-2, -9.096E-1, -1.222E-1,  3.936E-1)

        }

        cam.farPlaneDistance = 20.0f
        cam.target = camTarget

        dataset += communicatorType

        val buff: ByteArray
        val depthBuff: ByteArray?
        val octBuff: ByteArray?

//        val basePath = "/home/aryaman/TestingData/"
        val basePath = "/home/aryaman/TestingData/FromCluster/"
//        val basePath = "/home/aryaman/Repositories/DistributedVis/cmake-build-debug/"
//        val basePath = "/home/aryaman/Repositories/scenery-insitu/"

        val file = FileInputStream(File(basePath + "${dataset}vdidump4"))
//        val comp = GZIPInputStream(file, 65536)

        val vdiData = VDIDataIO.read(file)

//        val vdiType = "Composited"
//        val vdiType = "SetOf"
        val vdiType = "Final"
//        val vdiType = "Sub"
//        val vdiType = ""

        if(separateDepth) {
            buff = File(basePath + "${dataset}${vdiType}VDI4_ndc_col").readBytes()
            depthBuff = File(basePath + "${dataset}${vdiType}VDI4_ndc_depth").readBytes()

        } else {

            buff = File(basePath + "${dataset}VDI10_ndc").readBytes()
            depthBuff = null
        }
        if(skipEmpty) {
            octBuff = File(basePath + "${dataset}VDI4_ndc_octree").readBytes()
        } else {
            octBuff = null
        }

        val opBuffer = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)
        val opNumSteps = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)
        val opNumIntersect = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)
        val opNumEmptyL = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)
        val opNumSkipped = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)
        val opNumBefFirst = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)
        val opNumAfterLast = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)

        var colBuffer: ByteBuffer
        var depthBuffer: ByteBuffer?

        colBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * numLayers * 4 * 4)
        colBuffer.put(buff).flip()

        if(separateDepth) {
//            depthBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * 4 * 2 * 2)
            depthBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * 4 * 2) //TODO: IMP! This should be 2*2 for uint
            depthBuffer.put(depthBuff).flip()
        } else {
            depthBuffer = null
        }

//        val numVoxels = 2.0.pow(numOctreeLayers)
        val numGridCells = Vector3f(windowWidth.toFloat() / 8f, windowHeight.toFloat() / 8f, numSupersegments.toFloat())
//        val numGridCells = Vector3f(256f, 256f, 256f)
        val lowestLevel = MemoryUtil.memCalloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)
        if(skipEmpty) {
            lowestLevel.put(octBuff).flip()
        }

        compute.name = "compute node"

//        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("EfficientVDIRaycast.comp"), this@VDIRenderingExample::class.java))) {
        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("AmanatidesJumps.comp"), this@VDIRenderingExample::class.java))) {
            textures["OutputViewport"] = Texture.fromImage(Image(opBuffer, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))

            if (profileMemoryAccesses) {
                textures["NumSteps"] = Texture.fromImage(Image(opNumSteps, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
                textures["NumIntersectedSupsegs"] = Texture.fromImage(Image(opNumIntersect, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
                textures["NumEmptyLists"] = Texture.fromImage(Image(opNumEmptyL, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
                textures["NumNotIntLists"] = Texture.fromImage(Image(opNumSkipped, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
                textures["EmptyBeforeFirst"] = Texture.fromImage(Image(opNumBefFirst, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
                textures["EmptyAfterLast"] = Texture.fromImage(Image(opNumAfterLast, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
            }

            textures["InputVDI"] = Texture(Vector3i(numLayers*numSupersegments, windowHeight, windowWidth), 4, contents = colBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
                type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        }

        if(separateDepth) {
            compute.material().textures["DepthVDI"] = Texture(Vector3i(2*numSupersegments, windowHeight, windowWidth),  channels = 1, contents = depthBuffer, usageType = hashSetOf(
                Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
//            compute.material().textures["DepthVDI"] = Texture(Vector3i(2 * numSupersegments, windowHeight, windowWidth),  channels = 1, contents = depthBuffer, usageType = hashSetOf(
//                Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        }
        if(skipEmpty) {
            compute.material().textures["OctreeCells"] = Texture(Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()), 1, type = UnsignedIntType(), contents = lowestLevel, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        }
        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowWidth, windowHeight, 1),
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

        val plane = FullscreenObject()
        scene.addChild(plane)
        plane.material().textures["diffuse"] = compute.material().textures["OutputViewport"]!!
        thread {
            Thread.sleep(closeAfter)
            renderer?.shouldClose = true
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
            while(true)
            {
                Thread.sleep(2000)
                println("${cam.spatial().position}")
                println("${cam.spatial().rotation}")
            }
        }

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
            if(subsampling) {
                dynamicSubsampling()
            }
        }

        thread {
            if(benchmarking) {
                doBenchmarks()
            }
        }
    }

    private fun dynamicSubsampling() {
        val r = (hub.get(SceneryElement.Renderer) as Renderer)
        var stats = hub.get<Statistics>()!!
        val tolerance = 5

        val totalRotation = 30f
        if(subsampling_benchmarks) {
            rotateCamera(totalRotation)
        }

        val path = "benchmarking/${dataset}/View${viewNumber}/vdi$numSupersegments/subsampling/vdi${windowWidth}_${windowHeight}_${totalRotation.toInt()}"

        while(!r.firstImageReady) {
            Thread.sleep(200)
        }

        var currentSamples = 50.0
        compute.max_samples = currentSamples.toInt()

        compute.do_subsample = true

        while(!r.shouldClose) {
            //gather some data
            Thread.sleep(2000)
            val fps = stats.get("Renderer.fps")!!
            val scaleFactor = fps.avg() / desiredFrameRate
            val newSamples = scaleFactor * currentSamples
            if((newSamples < (currentSamples - tolerance)) || (newSamples > (currentSamples + tolerance))) {
                currentSamples = newSamples
                compute.max_samples = currentSamples.toInt()
            } else if(subsampling_benchmarks) {
                r.screenshot("${path}_$desiredFrameRate.png")
                //wait for screenshot
                Thread.sleep(1000)
                desiredFrameRate += 10
                if(desiredFrameRate >= maxFrameRate) {
                    subsampling_benchmarks = false
                }
            }

            stats.clear("Renderer.fps")
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

    private fun rotateCamera(degrees: Float) {
        cam.targeted = true
        val frameYaw = degrees / 180.0f * Math.PI.toFloat()
        val framePitch = 0f

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
            rotateCamera(5f)
        })
        inputHandler?.addKeyBinding("rotate_camera", "R")
    }



    private fun manageDebugTextures() {
        var numStepsBuff: ByteBuffer?
        var numIntersectedBuff: ByteBuffer?
        var numSkippedBuff: ByteBuffer?
        var numEmptyLBuff: ByteBuffer?
        var emptyBeforeFirstBuff: ByteBuffer?
        var emptyAfterLastBuff: ByteBuffer?

        while(renderer?.firstImageReady == false) {
            Thread.sleep(50)
        }

        logger.info("First image is ready")

        val numSteps = compute.material().textures["NumSteps"]!!
        val firstAtomic = AtomicInteger(0)

        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (numSteps to firstAtomic)

        val numIntersectedSupsegs = compute.material().textures["NumIntersectedSupsegs"]!!
        val secondAtomic = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (numIntersectedSupsegs to secondAtomic)

        val numEmptyLists = compute.material().textures["NumEmptyLists"]!!
        val thirdAtomic = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (numEmptyLists to thirdAtomic)

        val numNotIntLists = compute.material().textures["NumNotIntLists"]!!
        val fourthAtomic = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (numNotIntLists to fourthAtomic)

        val emptyBeforeFirst = compute.material().textures["EmptyBeforeFirst"]!!
        val fifthAtomic = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (numNotIntLists to fifthAtomic)

        val emptyAfterLast = compute.material().textures["EmptyAfterLast"]!!
        val sixthAtomic = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (numNotIntLists to sixthAtomic)

        var prevAtomic = firstAtomic.get()

        var cnt = 0
        while (true) {
            while(firstAtomic.get() == prevAtomic) {
                Thread.sleep(20)
            }
            prevAtomic = firstAtomic.get()

            numStepsBuff = numSteps.contents
            numIntersectedBuff = numIntersectedSupsegs.contents
            numEmptyLBuff = numEmptyLists.contents
            numSkippedBuff = numNotIntLists.contents
            emptyBeforeFirstBuff = emptyBeforeFirst.contents
            emptyAfterLastBuff = emptyAfterLast.contents

            if(cnt < 2) {
                SystemHelpers.dumpToFile(numStepsBuff!!, "num_steps.raw")
                SystemHelpers.dumpToFile(numSkippedBuff!!, "num_skipped.raw")
                SystemHelpers.dumpToFile(numIntersectedBuff!!, "num_intersected.raw")
                SystemHelpers.dumpToFile(numEmptyLBuff!!, "num_empty_lists.raw")
                SystemHelpers.dumpToFile(emptyBeforeFirstBuff!!, "num_empty_before_first.raw")
                SystemHelpers.dumpToFile(emptyAfterLastBuff!!, "num_empty_after_last.raw")
                logger.info("Wrote VDI $cnt")
            }
            cnt++
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIRenderingExample().main()
        }
    }

}
