package graphics.scenery.tests.examples.volumes.vdi

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.backends.vulkan.VulkanTexture
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.textures.Texture
import graphics.scenery.textures.UpdatableTexture
import graphics.scenery.utils.DataCompressor
import graphics.scenery.utils.Image
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.vdi.VDIData
import graphics.scenery.volumes.vdi.VDIDataIO
import graphics.scenery.volumes.vdi.VDINode
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.scijava.ui.behaviour.ClickBehaviour
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import java.io.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.min
import kotlin.system.measureNanoTime

/**
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */


class VDIClient : SceneryBase("VDI Rendering", 1280, 720, wantREPL = false) {
    var hmd: TrackedStereoGlasses? = null

    val compute = VDINode()
    val plane = FullscreenObject()

    val context = ZContext(4)

    val numSupersegments = 20
    val skipEmpty = true
    val vdiStreaming = true

    val subsampling = false
    var desiredFrameRate = 45

    var startPrinting = false
    var sendCamera = true

    var subsamplingFactorImage = 1.0f

    var newVDI = false

    val dataset = "Simulation"

    val dynamicSubsampling = false

    val subsampleRay = false

    val storeCamera = false
    val loadCamera = true

    val saveImages = false
    val imageFrequency = 10

    val totalFrames = 2000

    val recordVideo = false

    var firstVDI = true

    val cam: Camera = DetachedHeadCamera(hmd)

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
            Vector3f( 1.920E+0f, -1.920E+0f,  1.800E+0f)
        }
        "Isotropic" -> {
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

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        with(cam) {
            spatial().position = Vector3f(-4.365f, 0.38f, 0.62f)
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        cam.spatial {
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
                position = Vector3f( 2.799E+0f, -6.156E+0f, -2.641E+0f) //V1 for Rotstrat
                rotation = Quaternionf(-3.585E-2, -9.257E-1,  3.656E-1,  9.076E-2)
            } else  if (dataset == "Isotropic") {
                position = Vector3f( 2.799E+0f, -6.156E+0f, -2.641E+0f) //V1 for Isotropic
                rotation = Quaternionf(-3.585E-2, -9.257E-1,  3.656E-1,  9.076E-2)
            }


//            position = Vector3f(-2.607E+0f, -5.973E-1f,  2.415E+0f) // V1 for Beechnut
//            rotation = Quaternionf(-9.418E-2, -7.363E-1, -1.048E-1, -6.618E-1)
//
//            position = Vector3f( 1.897E+0f, -5.994E-1f, -1.899E+0f) //V1 for Boneplug
//            rotation = Quaternionf( 5.867E-5,  9.998E-1,  1.919E-2,  4.404E-3)
        }

        cam.farPlaneDistance = 20.0f

        val opBuffer = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)

        compute.name = "vdi node"
        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("VDIRenderer.comp"), this@VDIClient::class.java)))
        compute.material().textures["OutputViewport"] = Texture.fromImage(Image(opBuffer, windowWidth, windowHeight),
            usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))

        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowWidth, windowHeight, 1),
            invocationType = InvocationType.Permanent
        )

        compute.visible = false

        scene.addChild(compute)

        scene.addChild(plane)
        plane.material().textures["diffuse"] = compute.material().textures["OutputViewport"]!!

        if(recordVideo) {
//            settings.set("VideoEncoder.Format", "HEVC")
            settings.set("VideoEncoder.Bitrate", 20000000)
            renderer?.recordMovie("${dataset}VDIRendering.mp4")

            thread {
                Thread.sleep(56000)
                renderer?.recordMovie()
            }

        }

        thread {
            receiveAndUpdateVDI(compute)
        }

        val objectMapper = ObjectMapper(MessagePackFactory())

        var publisher: ZMQ.Socket = context.createSocket(SocketType.PUB)
        publisher.isConflate = true

        val address: String = "tcp://0.0.0.0:6655"
        try {
            publisher.bind(address)
        } catch (e: ZMQException) {
            logger.warn("Binding failed, trying random port: $e")
        }

        var prevPos = floatArrayOf(0f, 0f, 0f)
        var prevRot = floatArrayOf(0f, 0f, 0f, 0f)

        (renderer as VulkanRenderer).postRenderLambdas.add {
            val list: MutableList<Any> = ArrayList()
            val rotArray = floatArrayOf(cam.spatial().rotation.x, cam.spatial().rotation.y, cam.spatial().rotation.z, cam.spatial().rotation.w)
            val posArray = floatArrayOf(cam.spatial().position.x(), cam.spatial().position.y(), cam.spatial().position.z())

            if(!((rotArray.contentEquals(prevRot)) && (posArray.contentEquals(prevPos)))) {
                list.add(rotArray)
                list.add(posArray)

                val bytes = objectMapper.writeValueAsBytes(list)

                publisher.send(bytes)

                prevPos = posArray
                prevRot = rotArray

//                sendCamera = false
            }


            compute.printData = startPrinting
            startPrinting = false
        }

        if(storeCamera || loadCamera) {
            thread {
                camFlyThrough()
            }
        }
    }

    fun downsampleImage(factor: Float, wholeFrameBuffer: Boolean = false) {
        compute.downImage = factor
        plane.downImage = factor
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

    private fun stringToQuaternion(inputString: String): Quaternionf {
        val elements = inputString.removeSurrounding("[", "]").split(",").map { it.toFloat() }
        return Quaternionf(elements[0], elements[1], elements[2], elements[3])
    }

    private fun stringToVector3f(inputString: String): Vector3f {
        val mElements = inputString.removeSurrounding("[", "]").split(",").map { it.toFloat() }
        return Vector3f(mElements[0], mElements[1], mElements[2])
    }

    fun followCamera() {
        val posBytes = Files.readAllBytes(Paths.get("${dataset}camera_pos.txt"))
        val rotBytes = Files.readAllBytes(Paths.get("${dataset}camera_rot.txt"))

        val objectMapper = ObjectMapper(MessagePackFactory())

        val list_pos: List<Any> = objectMapper.readValue(posBytes, object : TypeReference<List<Any>>() {})
        val list_rot: List<Any> = objectMapper.readValue(rotBytes, object : TypeReference<List<Any>>() {})

        var frameCount = 0

        val frameTimeList = mutableListOf<Float>()
        val vdiLatencyList = mutableListOf<Float>()
        var frameStart = System.nanoTime()
        var firstFrame = true
        var frameEnd: Long
        var frameTime: Float

        //for dynamic control
        val targetFrameTime = 1.0f / desiredFrameRate
        val toleranceFPS = 5f
        val minFPS = desiredFrameRate - toleranceFPS
        val toleranceTime = abs(targetFrameTime - (1.0f / minFPS))

        var avgFrameTime = 0f
        var avgLength = 100

        val kP = 20.5f
        val kI = 5.5f

        subsamplingFactorImage = 1.0f
        var prevFactor = 1.0f


        (renderer as VulkanRenderer).postRenderLambdas.add {

            cam.spatial().position = stringToVector3f(list_pos[frameCount].toString())
            cam.spatial().rotation = stringToQuaternion(list_rot[frameCount].toString())

            if(saveImages && (frameCount % imageFrequency == 0)) {
                renderer?.screenshot("/home/aryaman/ownCloud/VDI_Benchmarks/Remote_${dataset}VDI_${windowHeight}_${frameCount}_${dynamicSubsampling}_${subsampleRay}.png")
            }

            if (!firstFrame) {
                frameEnd = System.nanoTime()
                frameTime = (frameEnd - frameStart) / 1e9f

                frameTime -= VulkanTexture.copyTime
                VulkanTexture.copyTime = 0f

                frameTimeList.add(frameTime)

                if(newVDI) {
                    vdiLatencyList.add(frameTime)
                } else {
                    vdiLatencyList.add(0f)
                }

                if((frameCount == totalFrames - 1) && !saveImages) {
                    val fw = FileWriter("/home/aryaman/ownCloud/VDI_Benchmarks/test_${dataset}VDI_${windowHeight}_${dynamicSubsampling}_${subsampleRay}_frame_times.csv", false)
                    val fw2 = FileWriter("${dataset}VDI_${windowHeight}_${dynamicSubsampling}_${subsampleRay}_vdi_received.csv", false)
                    val bw = BufferedWriter(fw)
                    val bw2 = BufferedWriter(fw2)

                    frameTimeList.forEach {
                        bw.append("${it}, ")
                    }

                    vdiLatencyList.forEach {
                        bw2.append("${it}, ")
                    }

                    bw.flush()
                    bw2.flush()
//
                    logger.warn("Frame rates and vdi latency files have been written!")

//                    renderer!!.recordMovie()
                    Thread.sleep(1000)

                    renderer!!.shouldClose = true
                }

                if(dynamicSubsampling) {
                    val error = frameTime - targetFrameTime

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


                    val output = kP * error + kI * avgError

                    subsamplingFactorImage -= output

                    subsamplingFactorImage = java.lang.Float.max(0.05f, subsamplingFactorImage)
                    subsamplingFactorImage = min(1.0f, subsamplingFactorImage)

                    val downImage = subsamplingFactorImage

                    logger.info("Frame time: $frameTime. error was: $error, avg error: $avgError and therefore setting factor to: $subsamplingFactorImage")

                    if (abs(downImage - prevFactor) > 0.05) {
                        logger.warn("changing the factor")
                        downsampleImage(downImage)
                        prevFactor = downImage
                    }
                }
            }

            firstFrame = false

            frameCount++

            frameStart = System.nanoTime()
        }
    }

    fun  recordCamera() {
        val list_pos: MutableList<Any> = ArrayList()
        val list_rot: MutableList<Any> = ArrayList()

        var frameCount = 0

        (renderer as VulkanRenderer).postRenderLambdas.add {
            if(frameCount < totalFrames) {
                val rotArray = floatArrayOf(cam.spatial().rotation.x, cam.spatial().rotation.y, cam.spatial().rotation.z, cam.spatial().rotation.w)
                val posArray = floatArrayOf(cam.spatial().position.x(), cam.spatial().position.y(), cam.spatial().position.z())

                list_pos.add(posArray)
                list_rot.add(rotArray)
            }

            if(frameCount == totalFrames) {
                val objectMapper = ObjectMapper(MessagePackFactory())

                val bytesPos = objectMapper.writeValueAsBytes(list_pos)

                Files.write(Paths.get("/home/aryaman/ownCloud/VDI_Benchmarks/CameraPose/${dataset}camera_pos.txt"), bytesPos)

                val bytesRot = objectMapper.writeValueAsBytes(list_rot)
                Files.write(Paths.get("/home/aryaman/ownCloud/VDI_Benchmarks/CameraPose/${dataset}camera_rot.txt"), bytesRot)

                logger.warn("Files have been written!")

            }
            frameCount++
        }


        //Phase 1: steady navigation 8 seconds

        //rotate somewhat
        var cnt = 0
        while (cnt < 50) {
            rotateCamera(0.32f, true)
            Thread.sleep(80)
            cnt++
        }

        lookAround()

        //Phase 2: fast rotation
        cnt = 0
        while (cnt < 60) {
            rotateCamera(1.2f, true)
            Thread.sleep(45)
            cnt++
        }

        cnt = 0
        while (cnt < 60) {
            rotateCamera(1.2f, false)
            Thread.sleep(45)
            cnt++
        }

        cnt = 0
        while (cnt < 40) {
            rotateCamera(1.2f, true)
            Thread.sleep(45)
            cnt++
        }

        Thread.sleep(45 * 20 - 10)


        //Phase 3: zoom in and look at some detail

        zoomCamera(0.99f, 2000f)

        lookAround()

        zoomCamera(1.01f, 2000f)

        //Phase 4: more steady navigation

        lookAround()


        cnt = 0
        while (cnt < 50) {
            rotateCamera(0.32f, true)
            Thread.sleep(80)
            cnt++
        }


        lookAround()

        cnt = 0
        while (cnt < 100) {
            rotateCamera(0.3f, true)
            Thread.sleep(75)
            cnt++
        }

        zoomCamera(0.99f, 750f)

        lookAround()

        zoomCamera(1.01f, 250f)


        Thread.sleep(1000)
    }

    public fun camFlyThrough() {
        val r = (hub.get(SceneryElement.Renderer) as Renderer)

        while(!r.firstImageReady) {
            Thread.sleep(200)
        }

        Thread.sleep(1000)

        while(firstVDI) {
            Thread.sleep(100)
        }

        if(storeCamera) {
            recordCamera()
        } else if(loadCamera) {
            followCamera()
        }
    }

    private fun lookAround() {
        var cnt = 0
        while (cnt < 20) {
            rotateCamera(0.3f, true)
            Thread.sleep(50)
            cnt++
        }
        cnt = 0
        while (cnt < 20) {
            rotateCamera(0.3f)
            Thread.sleep(50)
            cnt++
        }
        cnt = 0
        while (cnt < 20) {
            rotateCamera(-0.35f, true)
            Thread.sleep(50)
            cnt++
        }
        cnt = 0
        while (cnt < 20) {
            rotateCamera(-0.25f)
            Thread.sleep(50)
            cnt++
        }
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
        }
    }

    fun zoomCamera(factor: Float, duration: Float) {
        logger.info("zooming camera")
        cam.targeted = true

        val startTime = System.nanoTime()
        while (true) {
            val distance = (camTarget - cam.spatial().position).length()

            cam.spatial().position = camTarget + cam.forward * distance * (-1.0f * factor)

            Thread.sleep(50)

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

    fun updateTextures(color: ByteBuffer, depth: ByteBuffer, accelGridBuffer: ByteBuffer, vdiData: VDIData, accelSize: Int, firstVDI: Boolean) {

        compute.ProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem()
        compute.invProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem().invert()
        compute.nw = vdiData.metadata.nw
        compute.invModel = Matrix4f(vdiData.metadata.model).invert()
        compute.volumeDims = vdiData.metadata.volumeDimensions
        compute.do_subsample = false
        compute.vdiWidth = vdiData.metadata.windowDimensions.x
        compute.vdiHeight = vdiData.metadata.windowDimensions.y

        val colorTexture = UpdatableTexture(Vector3i(numSupersegments, windowHeight, windowWidth), 4, contents = null, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad),
            type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val colorUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, numSupersegments, windowHeight, windowWidth),
            color.slice()
        )
        colorTexture.addUpdate(colorUpdate)


        val depthTexture = UpdatableTexture(Vector3i(2*numSupersegments, windowHeight, windowWidth),  channels = 1, contents = null, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val depthUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, 2 * numSupersegments, windowHeight, windowWidth),
            depth.slice()
        )
        depthTexture.addUpdate(depthUpdate)

        val numGridCells = Vector3f(vdiData.metadata.windowDimensions.x.toFloat() / 8f, vdiData.metadata.windowDimensions.y.toFloat() / 8f, numSupersegments.toFloat())

        val accelTexture = UpdatableTexture(Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()),  channels = 1, contents = null, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad), type = UnsignedIntType(), mipmap = false, normalized = true, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val accelUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, windowWidth / 8, windowHeight / 8, numSupersegments),
            accelGridBuffer
        )
        accelTexture.addUpdate(accelUpdate)

        if(firstVDI || compute.useSecondBuffer) { //if this is the first VDI or the second buffer was being used so far
            compute.ViewOriginal = vdiData.metadata.view
            compute.invViewOriginal = Matrix4f(vdiData.metadata.view).invert()

            compute.material().textures["InputVDI"] = colorTexture
            compute.material().textures["DepthVDI"] = depthTexture
            compute.material().textures["OctreeCells"] = accelTexture

            logger.info("Uploading data for buffer 1")
        } else {
            compute.ViewOriginal2 = vdiData.metadata.view
            compute.invViewOriginal2 = Matrix4f(vdiData.metadata.view).invert()

            compute.material().textures["InputVDI2"] = colorTexture
            compute.material().textures["DepthVDI2"] = depthTexture
            compute.material().textures["OctreeCells2"] = accelTexture

            logger.info("Uploading data for buffer 2")
        }

        while(!colorTexture.availableOnGPU() || !depthTexture.availableOnGPU()) {
            logger.debug("Waiting for texture transfer. color: ${colorTexture.availableOnGPU()} and depth: ${depthTexture.availableOnGPU()}")
            Thread.sleep(10)
        }

        logger.debug("Data has been detected to be uploaded to GPU")
        newVDI = true

        if(!firstVDI) {
            compute.useSecondBuffer = !compute.useSecondBuffer
        }

    }

    private fun receiveAndUpdateVDI(compute: VDINode) {

        while (!renderer!!.firstImageReady) {
            Thread.sleep(100)
        }

        var subscriber: ZMQ.Socket = context.createSocket(SocketType.SUB)
        subscriber.setConflate(true)
//        val address = "tcp://localhost:6655"
//        val address = "tcp://172.24.150.81:6655"
        val address = "tcp://10.1.224.71:6655"
        try {
            subscriber.connect(address)
        } catch (e: ZMQException) {
            logger.warn("ZMQ Binding failed.")
        }
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)

        var vdiData = VDIData()

        val compressor = DataCompressor()
        val compressionTool = DataCompressor.CompressionTool.LZ4

        val colorSize = windowWidth * windowHeight * numSupersegments * 4 * 4
        val depthSize = windowWidth * windowHeight * numSupersegments * 2 * 4
        val accelSize = (windowWidth/8) * (windowHeight/8) * numSupersegments * 4

        val decompressionBuffer = 1024

        val color = MemoryUtil.memCalloc(colorSize + decompressionBuffer)
        val depth = MemoryUtil.memCalloc(depthSize + decompressionBuffer)

        val compressedColor: ByteBuffer =
            MemoryUtil.memAlloc(compressor.returnCompressBound(colorSize.toLong(), compressionTool))
        val compressedDepth: ByteBuffer =
            MemoryUtil.memAlloc(compressor.returnCompressBound(depthSize.toLong(), compressionTool))
        val accelGridBuffer =
            MemoryUtil.memAlloc(accelSize)

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

        compute.material().textures["InputVDI"] = emptyColorTexture
        compute.material().textures["InputVDI2"] = emptyColorTexture
        compute.material().textures["DepthVDI"] = emptyDepthTexture
        compute.material().textures["DepthVDI2"] = emptyDepthTexture
        compute.material().textures["OctreeCells"] = emptyAccelTexture
        compute.material().textures["OctreeCells2"] = emptyAccelTexture

        compute.visible = true

        //upload one texture to buffer 2
        val numGridCells = Vector3f(windowWidth.toFloat() / 8f, windowHeight.toFloat() / 8f, numSupersegments.toFloat())

        val initColor = MemoryUtil.memAlloc(numSupersegments * windowHeight * windowWidth * 4 * 4)
        val initDepth = MemoryUtil.memAlloc(2 * numSupersegments * windowHeight * windowWidth * 4)
        val initAccel = MemoryUtil.memAlloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)

        val initialColorTexture = UpdatableTexture(Vector3i(numSupersegments, windowHeight, windowWidth), 4, contents = null, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad),
            type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val initialcolorUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, numSupersegments, windowHeight, windowWidth),
            initColor
        )
        initialColorTexture.addUpdate(initialcolorUpdate)


        val initialDepthTexture = UpdatableTexture(Vector3i(2*numSupersegments, windowHeight, windowWidth),  channels = 1, contents = null, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val initialDepthUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, 2 * numSupersegments, windowHeight, windowWidth),
            initDepth
        )
        initialDepthTexture.addUpdate(initialDepthUpdate)


        val initialAccelTexture = UpdatableTexture(Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()),  channels = 1, contents = null, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad), type = UnsignedIntType(), mipmap = false, normalized = true, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val initalAccelUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, windowWidth / 8, windowHeight / 8, numSupersegments),
            initAccel
        )
        initialAccelTexture.addUpdate(initalAccelUpdate)

        compute.material().textures["InputVDI2"] = initialColorTexture
        compute.material().textures["DepthVDI2"] = initialDepthTexture
        compute.material().textures["OctreeCells2"] = initialAccelTexture

        while(!initialColorTexture.availableOnGPU() || !initialDepthTexture.availableOnGPU() ||     !initialAccelTexture.availableOnGPU()) {
            logger.debug("Waiting for texture transfer.")
            Thread.sleep(10)
        }

        logger.info("done with initial textures")

//        val colorTexture = UpdatableTexture(
//            Vector3i(1024, 1024, 256),
//            channels = 1,
//            type = UnsignedByteType(),
//            usageType = hashSetOf(Texture.UsageType.Texture, Texture.UsageType.AsyncLoad, Texture.UsageType.LoadStoreImage),
//            contents = null
//        )
//
//        val colorUpdate = UpdatableTexture.TextureUpdate(
//            UpdatableTexture.TextureExtents(0, 0, 0, 1024, 1024, 256),
//            MemoryUtil.memAlloc(256*1024*1024)
//        )
//        colorTexture.addUpdate(colorUpdate)
//
//        compute.material().textures["InputVDI"] = colorTexture
//
//        val waitTime = measureTimeMillis {
//            // Here, we wait until the texture is marked as available on the GPU
//            while(!colorTexture.availableOnGPU()) {
//                logger.info("Texture not available yet, uploaded=${colorTexture.uploaded.get()}/permits=${colorTexture.gpuMutex.availablePermits()}")
//                Thread.sleep(10)
//            }
//        }
//
//        logger.info("Texture is available now, waited $waitTime ms")


        val buff: ByteArray
        val depthBuff: ByteArray
        val octBuff: ByteArray
        var colBuffer: ByteBuffer? = null
        var depthBuffer: ByteBuffer? = null
        var accelGridBuff: ByteBuffer? = null

        val basePath = "/home/aryaman/Repositories/scenery-insitu/"
//        val basePath = "e:/vdis/Simulation/"

        if(!vdiStreaming) {
            val vdiParams = "_${windowWidth}_${windowHeight}_${numSupersegments}_0_"

            val file = FileInputStream(File(basePath + "${dataset}vdi${vdiParams}dump4"))

            vdiData = VDIDataIO.read(file)

            //preparing the files for loading from disk
            buff = File(basePath + "${dataset}VDI${vdiParams}4_ndc_col").readBytes()
            depthBuff = File(basePath + "${dataset}VDI${vdiParams}4_ndc_depth").readBytes()
            octBuff = File(basePath + "${dataset}VDI${vdiParams}4_ndc_octree").readBytes()



            colBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * 4 * 4)

            colBuffer.put(buff).flip()
            colBuffer.limit(colBuffer.capacity())

            depthBuffer =
                MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * 2 * 2 * 2)

            depthBuffer.put(depthBuff).flip()
            depthBuffer.limit(depthBuffer.capacity())

            val numGridCells = Vector3f(vdiData.metadata.windowDimensions.x.toFloat() / 8f, vdiData.metadata.windowDimensions.y.toFloat() / 8f, numSupersegments.toFloat())
            accelGridBuff = MemoryUtil.memCalloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)
            if(skipEmpty) {
                accelGridBuff.put(octBuff).flip()
            }
            compute.skip_empty = skipEmpty
        }

        while(true) {

            if(vdiStreaming) {

                val payload: ByteArray?

                logger.info("Waiting for VDI")

                val receiveTime = measureNanoTime {
                    payload = subscriber.recv()
                }

                logger.info("Time taken for the receive: ${receiveTime/1e9}")


                if (payload != null) {
                    val metadataSize = payload.sliceArray(0 until 3).toString(Charsets.US_ASCII).toInt() //hardcoded 3 digit number


                    val metadata = ByteArrayInputStream(payload.sliceArray(3 until (metadataSize + 3)))
                    vdiData = VDIDataIO.read(metadata)
                    logger.info("Index of received VDI: ${vdiData.metadata.index}")

                    val compressedColorLength = vdiData.bufferSizes.colorSize
                    val compressedDepthLength = vdiData.bufferSizes.depthSize

                    compressedColor.put(payload.sliceArray((metadataSize + 3) until (metadataSize + 3 + compressedColorLength.toInt())))
                    compressedColor.flip()
                    compressedDepth.put(payload.sliceArray((metadataSize + 3) + compressedColorLength.toInt() until (metadataSize + 3) + compressedColorLength.toInt() + compressedDepthLength.toInt()))
                    compressedDepth.flip()

                    accelGridBuffer.put(payload.sliceArray((metadataSize + 3) + compressedColorLength.toInt() + compressedDepthLength.toInt() until payload.size))
                    accelGridBuffer.flip()

                    val colorDone = AtomicInteger(0)

                    thread {
                        compressedColor.limit(compressedColorLength.toInt())
                        val decompressedColorLength = compressor.decompress(color, compressedColor.slice(), compressionTool)
                        compressedColor.limit(compressedColor.capacity())
                        if (decompressedColorLength.toInt() != colorSize) {
                            logger.warn("Error decompressing color message. Decompressed length: $decompressedColorLength and desired size: $colorSize")
                        }

                        colorDone.incrementAndGet()

                    }

                    compressedDepth.limit(compressedDepthLength.toInt())
                    val decompressedDepthLength = compressor.decompress(depth, compressedDepth.slice(), compressionTool)
                    compressedDepth.limit(compressedDepth.capacity())
                    if (decompressedDepthLength.toInt() != depthSize) {
                        logger.warn("Error decompressing depth message. Decompressed length: $decompressedDepthLength and desired size: $depthSize")
                    }

                    while (colorDone.get() == 0) {
                        Thread.sleep(20)
                    }

                    color.limit(color.remaining() - decompressionBuffer)
                    depth.limit(depth.remaining() - decompressionBuffer)

                    updateTextures(color.slice(), depth.slice(), accelGridBuffer, vdiData, accelSize, firstVDI)

                    color.limit(color.capacity())
                    depth.limit(depth.capacity())

                    firstVDI = false

                    compute.visible = true

                } else {
                    logger.info("Payload received but is null")
                }

                logger.info("Received and updated VDI data")
            } else {
                Thread.sleep(1000)
                updateTextures(colBuffer!!, depthBuffer!!, accelGridBuff!!, vdiData, accelSize, firstVDI)
                firstVDI = false
                compute.visible = true
            }


//            Thread.sleep(2000)
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()

        inputHandler?.addBehaviour("send_camera", ClickBehaviour { _, _ ->
            sendCamera = true
        })
        inputHandler?.addKeyBinding("send_camera", "O")

        inputHandler?.addBehaviour("print_debug", ClickBehaviour { _, _ ->
            rotateCamera(0.3f)
        })
        inputHandler?.addKeyBinding("print_debug", "H")

        inputHandler?.addBehaviour("rotate_camera", ClickBehaviour { r_, _ ->
            rotateCamera(0.3f, true)
        })
        inputHandler?.addKeyBinding("rotate_camera", "R")

        inputHandler?.addBehaviour("rotate_camera_yaw_negative", ClickBehaviour { _, _ ->
            rotateCamera(-0.3f)
        })
        inputHandler?.addKeyBinding("rotate_camera_yaw_negative", "N")

        inputHandler?.addBehaviour("rotate_camera_pitch_negative", ClickBehaviour { r_, _ ->
            rotateCamera(-0.3f, true)
        })
        inputHandler?.addKeyBinding("rotate_camera_pitch_negative", "B")

        inputHandler?.addBehaviour("zoom", ClickBehaviour { r_, _ ->
            zoomCamera(0.99f, 100f)
        })
        inputHandler?.addKeyBinding("zoom", "Z")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIClient().main()
        }
    }
}
