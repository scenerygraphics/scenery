package graphics.scenery.tests.examples.volumes.vdi

import com.fasterxml.jackson.databind.ObjectMapper
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.backends.vulkan.VulkanRenderer
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
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.min
import kotlin.system.measureNanoTime

/**
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */


class VDIClient : SceneryBase("VDI Rendering", 1920, 1080, wantREPL = false) {
    var hmd: TrackedStereoGlasses? = null

    val compute = VDINode()
    val plane = FullscreenObject()

    val context = ZContext(4)

    val numSupersegments = 20
    val skipEmpty = true
    val vdiStreaming = false

    val subsampling = false
    var desiredFrameRate = 30
    var maxFrameRate = 90

    var startPrinting = false
    var sendCamera = false

    var subsamplingFactorImage = 1.0f

    val dataset = "Simulation"

    val dynamicSubsampling = false

    val subsampleRay = false

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

            if(!((rotArray.contentEquals(prevRot)) && (posArray.contentEquals(prevPos))) && sendCamera) {
                list.add(rotArray)
                list.add(posArray)

                val bytes = objectMapper.writeValueAsBytes(list)

                logger.info("Sent camera details\n position: ${cam.spatial().position}\nrotation: ${cam.spatial().rotation}")

                publisher.send(bytes)

                prevPos = posArray
                prevRot = rotArray

//                sendCamera = false
            }


            compute.printData = startPrinting
            startPrinting = false
        }

        thread {
            while (true) {
                Thread.sleep(2000)
                logger.info("cam pos: ${cam.spatial().position}")
                logger.info("cam rot: ${cam.spatial().rotation}")
            }
        }

//        thread {
//            camFlyThrough()
//        }
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

                    subsamplingFactorImage = java.lang.Float.max(0.05f, subsamplingFactorImage)
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

    public fun camFlyThrough() {
        val r = (hub.get(SceneryElement.Renderer) as Renderer)

        while(!r.firstImageReady) {
            Thread.sleep(200)
        }

        Thread.sleep(1000)

        val maxPitch = 10f
        val maxYaw = 10f

        val minYaw = -10f
        val minPitch = -10f

//        rotateCamera(40f, true)
        var pitchRot = 0.12f
        var yawRot = 0.075f

        var totalYaw = 0f
        var totalPitch = 0f

//        rotateCamera(20f, true)

        moveCamera(yawRot, pitchRot * 2, maxYaw, maxPitch * 2, minPitch * 2, minYaw, totalYaw, totalPitch, 2000f)
        logger.info("Moving to phase 2")
        moveCamera(yawRot, pitchRot * 2, maxYaw, maxPitch * 2, minPitch * 2, minYaw, totalYaw, totalPitch, 2000f)

        Thread.sleep(1000)

        zoomCamera(0.99f, 1000f)
        logger.info("Moving to phase 3")
        moveCamera(yawRot * 3, pitchRot * 3, maxYaw, maxPitch, minPitch, minYaw, totalYaw, totalPitch, 4000f)
//
//        if(subsampleRay) {
//            doDownsampling(true)
//            setDownsamplingFactor(0.2f)
//        }
//
//        moveCamera(yawRot *2, pitchRot * 5, 40f, 40f, -60f, -50f, totalYaw, totalPitch, 6000f)

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
        }
    }

    fun zoomCamera(factor: Float, duration: Float) {
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

        val gridArray = ByteArray(accelSize)
        accelGridBuffer.get(gridArray)
        accelGridBuffer.flip()

        val gridUInt = accelGridBuffer.asIntBuffer()

        val atPos = gridUInt.get(24 * 50 * 24 + 2 * 50 * 50).toUInt()

        logger.warn("the value is : $atPos")

        logger.warn("Sum at receipt: ${gridArray.sum()}")

        val accelUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, windowWidth / 8, windowHeight / 8, numSupersegments),
            accelGridBuffer
        )
        accelTexture.addUpdate(accelUpdate)

        logger.info("Before assignment, color mutex is: ${colorTexture.gpuMutex.availablePermits()} and depth: ${depthTexture.gpuMutex.availablePermits()}")
        logger.info("Before assignment, color mutex is: ${colorTexture.mutex.availablePermits()} and depth: ${depthTexture.mutex.availablePermits()}")

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

//                logger.info("color mutex is: ${colorTexture.gpuMutex.availablePermits()} and depth: ${depthTexture.gpuMutex.availablePermits()}")

        while(!colorTexture.availableOnGPU() || !depthTexture.availableOnGPU()) {
            logger.info("Waiting for texture transfer. color: ${colorTexture.availableOnGPU()} and depth: ${depthTexture.availableOnGPU()}")
            Thread.sleep(1000)
        }

        logger.warn("Data has been detected to be uploaded to GPU")

        if(!firstVDI) {
            compute.useSecondBuffer = !compute.useSecondBuffer
        }

    }

    private fun receiveAndUpdateVDI(compute: VDINode) {
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

        var firstVDI = true

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

        compute.visible = true

        val buff: ByteArray
        val depthBuff: ByteArray
        val octBuff: ByteArray
        var colBuffer: ByteBuffer? = null
        var depthBuffer: ByteBuffer? = null
        var accelGridBuff: ByteBuffer? = null

        val basePath = "/home/aryaman/Repositories/scenery-insitu/"

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

                    logger.info("Received payload of size ${payload.size}. Sum is: ${payload.sum()}")
                }

                logger.info("Time taken for the receive: ${receiveTime/1e9}")


                if (payload != null) {
                    val metadataSize = payload.sliceArray(0 until 3).toString(Charsets.US_ASCII).toInt() //hardcoded 3 digit number

                    logger.info("vdi data size is: $metadataSize")

                    val metadata = ByteArrayInputStream(payload.sliceArray(3 until (metadataSize + 3)))
                    vdiData = VDIDataIO.read(metadata)
                    logger.info("Received metadata has nw: ${vdiData.metadata.nw}")
                    logger.info("Index of received VDI: ${vdiData.metadata.index}")

                    val compressedColorLength = vdiData.bufferSizes.colorSize
                    val compressedDepthLength = vdiData.bufferSizes.depthSize

                    compressedColor.put(payload.sliceArray((metadataSize + 3) until (metadataSize + 3 + compressedColorLength.toInt())))
                    compressedColor.flip()
                    compressedDepth.put(payload.sliceArray((metadataSize + 3) + compressedColorLength.toInt() until (metadataSize + 3) + compressedColorLength.toInt() + compressedDepthLength.toInt()))
                    compressedDepth.flip()

                    logger.info("bytes to be put into grid buffer: ${(payload.size - ((metadataSize + 3) + compressedColorLength.toInt() + compressedDepthLength.toInt()))}")

                    accelGridBuffer.put(payload.sliceArray((metadataSize + 3) + compressedColorLength.toInt() + compressedDepthLength.toInt() until payload.size))
                    accelGridBuffer.flip()

                    compressedColor.limit(compressedColorLength.toInt())
                    val decompressedColorLength = compressor.decompress(color, compressedColor.slice(), compressionTool)
                    compressedColor.limit(compressedColor.capacity())
                    if (decompressedColorLength.toInt() != colorSize) {
                        logger.warn("Error decompressing color message. Decompressed length: $decompressedColorLength and desired size: $colorSize")
                    }

                    compressedDepth.limit(compressedDepthLength.toInt())
                    val decompressedDepthLength = compressor.decompress(depth, compressedDepth.slice(), compressionTool)
                    compressedDepth.limit(compressedDepth.capacity())
                    if (decompressedDepthLength.toInt() != depthSize) {
                        logger.warn("Error decompressing depth message. Decompressed length: $decompressedDepthLength and desired size: $depthSize")
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
            startPrinting = true
        })
        inputHandler?.addKeyBinding("print_debug", "H")

        inputHandler?.addBehaviour("rotate_camera", ClickBehaviour { r_, _ ->
            rotateCamera(10f, true)
        })
        inputHandler?.addKeyBinding("rotate_camera", "R")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIClient().main()
        }
    }
}
