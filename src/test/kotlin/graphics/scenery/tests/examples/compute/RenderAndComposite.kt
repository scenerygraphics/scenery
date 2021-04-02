package graphics.scenery.tests.examples.compute

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.backends.vulkan.VulkanTexture
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.numerics.Random
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.RingBuffer
import graphics.scenery.utils.Statistics
import graphics.scenery.utils.SystemHelpers
import graphics.scenery.utils.extensions.plus
import graphics.scenery.volumes.BufferedVolume
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.VolumeManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Quaternionf
import org.joml.Vector2i
import org.joml.Vector3i
import org.junit.Test
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.memAlloc
import org.scijava.ui.behaviour.ClickBehaviour
import tpietzsch.shadergen.generate.SegmentTemplate
import tpietzsch.shadergen.generate.SegmentType
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Example that renders procedurally generated volumes.
 * [bitsPerVoxel] can be set to 8 or 16, to generate Byte or UnsignedShort volumes.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class RenderAndComposite: SceneryBase("Volume Rendering example", 1200, 1200, wantREPL = false) {
    val bitsPerVoxel = 16
    val volumeSize = 512L

    lateinit var volumeManager: VolumeManager
    lateinit var volume: BufferedVolume

    data class Timer(var start: Long, internal var end: Long)

    val tRend = Timer(0,0)

    val tTotal = Timer(0,0)
    val tGPU = Timer(0,0)

    var imgFetchTime: Long = 0
    var compositeTime: Long = 0
    var distrTime: Long = 0
    var gathTime: Long = 0
    var streamTime: Long = 0
    var totalTime: Long = 0
    var gpuSendTime: Long = 0
    var rendPrev: Long = 0

    val compute = Box()
    var volumeInitialized = false

    @ExperimentalCoroutinesApi
    override fun init() {
        logger.info("In init function")
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        logger.info("Init 1")

        volumeManager = VolumeManager(hub,
            useCompute = true,
            customSegments = hashMapOf(
                SegmentType.FragmentShader to SegmentTemplate(
                    this.javaClass,
                    "Distr_VolumeRaycaster.comp",
                    "intersectBoundingBox", "vis", "SampleVolume", "Convert", "Accumulate"),
            ))

        logger.info("Init 2")

        volumeManager.customTextures.add("OutputSubVDIColor");
        volumeManager.customTextures.add("OutputSubVDIDepth");

        val outputSubColorBuffer = MemoryUtil.memCalloc(windowHeight*windowWidth*4)
        val outputSubDepthBuffer = MemoryUtil.memCalloc(windowHeight*windowWidth*4)
        val outputSubVDIColor = Texture.fromImage(Image(outputSubColorBuffer,  windowHeight, windowWidth), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        val outputSubVDIDepth = Texture.fromImage(Image(outputSubDepthBuffer,  windowHeight, windowWidth), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        volumeManager.material.textures["OutputSubVDIColor"] = outputSubVDIColor
        volumeManager.material.textures["OutputSubVDIDepth"] = outputSubVDIDepth
        hub.add(volumeManager)

        logger.info("Init 3")

        compute.name = "compositor node"

        compute.material = ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("Distr_Compositor.comp"), this::class.java))
        val outputColours = MemoryUtil.memCalloc(windowHeight*windowWidth*4)
        val alphaComposited = Texture.fromImage(Image(outputColours, windowHeight,  windowWidth), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        compute.material.textures["AlphaComposited"] = alphaComposited

        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowHeight, windowWidth, 1)
        )
        compute.visible = true
        scene.addChild(compute)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = Vector3f(0.0f, 0.5f, 5.0f)
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        cam.position = Vector3f(3.213f, 8.264E-1f, -9.844E-1f)

        cam.rotation = Quaternionf(3.049E-2,  9.596E-1, -1.144E-1, -2.553E-1)

        val shell = Box(Vector3f(10.0f, 10.0f, 10.0f), insideNormals = true)
        shell.material.cullingMode = Material.CullingMode.None
        shell.material.diffuse = Vector3f(0.1f, 0.1f, 0.1f)
        shell.material.specular = Vector3f(0.0f)
        shell.material.ambient = Vector3f(0.0f)
        shell.position = Vector3f(0.0f, 4.0f, 0.0f)
        scene.addChild(shell)

        volume = Volume.fromBuffer(emptyList(), volumeSize.toInt(), volumeSize.toInt(), volumeSize.toInt(), UnsignedShortType(), hub)
        volume.name = "volume"
        volume.position = Vector3f(0.0f, 0.0f, 0.0f)
        volume.colormap = Colormap.get("hot")
        volume.pixelToWorldRatio = 0.0036f

        logger.info("Init 4")

        with(volume.transferFunction) {
            addControlPoint(0.0f, 0.0f)
            addControlPoint(0.2f, 0.0f)
            addControlPoint(0.4f, 0.5f)
            addControlPoint(0.8f, 0.5f)
            addControlPoint(1.0f, 0.0f)
        }

        volume.metadata["animating"] = true
        scene.addChild(volume)

        val lights = (0 until 3).map {
            PointLight(radius = 15.0f)
        }

        logger.info("Init 5")

        lights.mapIndexed { i, light ->
            light.position = Vector3f(2.0f * i - 4.0f,  i - 1.0f, 0.0f)
            light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            light.intensity = 0.2f
            scene.addChild(light)
        }

//        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
//        box.name = "le box du win"
//        box.material.textures["diffuse"] = outputTexture
//        box.material.metallic = 0.0f
//        box.material.roughness = 1.0f

//        scene.addChild(box)

        logger.info("Init 6")

        thread{
            getImages()
        }

    }
    var shift = Vector3f(0.0f)

    fun updateVolumes(){
        logger.info("Trying to generate data")
        val volumeBuffer = RingBuffer<ByteBuffer>(2) { memAlloc((volumeSize*volumeSize*volumeSize*bitsPerVoxel/8).toInt()) }

        val seed = Random.randomFromRange(0.0f, 133333337.0f).toLong()
        val shiftDelta = Random.random3DVectorFromRange(-1.5f, 1.5f)
        shift += shiftDelta

        val currentBuffer = volumeBuffer.get()

        graphics.scenery.volumes.Volume.generateProceduralVolume(volumeSize, 0.35f, seed = seed,
            intoBuffer = currentBuffer, shift = shift, use16bit = bitsPerVoxel > 8)

        logger.info("Adding the timepoint to the volume")
        volume.addTimepoint("t-${0}", currentBuffer)
        volume.goToLastTimepoint()

        volume.purgeFirst(10, 10)

        volumeInitialized = true
    }

    @ExperimentalCoroutinesApi
    fun getImages() {
        var subVDIColorBuffer: ByteBuffer?
        var subVDIDepthBuffer: ByteBuffer?
        var compositedVDIColorBuffer: ByteBuffer?

        while (renderer?.firstImageReady == false) {
            Thread.sleep(50)
        }

        updateVolumes()

        while (!volumeInitialized) {
            Thread.sleep(100)
            logger.info("Waiting for the volume to be initialized")
        }

        var cnt = 0

        val subVDIColor = volumeManager.material.textures["OutputSubVDIColor"]!!
        val subVDIDepth = volumeManager.material.textures["OutputSubVDIDepth"]!!

        val compositedColor = compute.material.textures["AlphaComposited"]!!

        val composited = AtomicInteger(0)

//        val r = renderer
        val subvdi = AtomicInteger(0)
        val subdepth = AtomicInteger(0)

        (renderer as? VulkanRenderer)?.postRenderLambdas?.add {
            subVDIColor to subvdi
        }

        (renderer as? VulkanRenderer)?.postRenderLambdas?.add {
            subVDIDepth to subdepth
        }

        (renderer as? VulkanRenderer)?.postRenderLambdas?.add {
            compositedColor to composited
        }

        var prevAtomic = subvdi.get()

        while (true) {

            val temp = VulkanTexture.getReference(volumeManager.material.textures["OutputSubVDIColor"]!!)

            if(temp == null) {
                logger.info("Yes it is indeed null")
            }
            else {
                logger.info("No, it is not null")
            }

//            subVDIColorBuffer = null
            if (cnt % 1000 == 0) {
                tGPU.start = System.nanoTime()
                updateVolumes()
                tGPU.end = System.nanoTime()

                gpuSendTime += tGPU.end - tGPU.start
            }
//            Thread.sleep(100)

            //Start here
            tTotal.start = System.nanoTime()
            tRend.start = System.nanoTime()

            while(subvdi.get() == prevAtomic) {
                Thread.sleep(5)
            }

            logger.warn("Previous value was: $prevAtomic and the new value is ${subvdi.get()}")

            prevAtomic = subvdi.get()

            subVDIColorBuffer = subVDIColor.contents
            subVDIDepthBuffer = subVDIDepth.contents

            compositedVDIColorBuffer = compositedColor.contents


            // Get the rendered VDIs
            // after that,
            logger.info("Getting the rendered subVDIs")



//            runBlocking {
//                logger.info("In runBlocking!")
//                val request = r?.requestTexture(subVDIColor) { colTex ->
//                    logger.info("Fetched color VDI from GPU")
//
//                    colTex.contents?.let { colVDI ->
//                        subVDIColorBuffer = colVDI
//                    }
//                }
//
//                request?.await()
//            }


//            if(subVDIColorBuffer!=null) {
//                logger.info("Got the rendered image!")
//            }

            tRend.end = System.nanoTime()
            if (cnt > 0) {
                imgFetchTime += tRend.end - tRend.start
            }

            compute.material.textures["VDIsColor"] = Texture(Vector3i(windowHeight, windowWidth, 1), 4, contents = subVDIColorBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
            compute.material.textures["VDIsDepth"] = Texture(Vector3i(windowHeight, windowWidth, 1), 4, contents = subVDIDepthBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))

//            Thread.sleep(50)

            tTotal.end = System.nanoTime()
            if(cnt>0) {totalTime += tTotal.end - tTotal.start}

            if(cnt != 0 && cnt % 100 == 0) {

                logger.info("Dumping to file")
//                SystemHelpers.dumpToFile(subVDIColorBuffer!!, "$cnt-textureSubCol.raw")
                SystemHelpers.dumpToFile(compositedVDIColorBuffer!!, "$cnt-textureCompCol.raw")
                logger.info("File dumped")


                logger.warn("Total vis time steps so far: $cnt. Printing vis timers now.")
                logger.warn((hub.get<Statistics>())?.toString())
                logger.warn("Total time: $totalTime. Average is: ${(totalTime.toDouble()/cnt.toFloat())/1000000.0f}")
                logger.warn("Total communication time: ${distrTime + gathTime}. Average is: ${((distrTime + gathTime).toDouble()/cnt.toDouble())/1000000.0f}")
                logger.warn("Total all_to_all time: $distrTime. Average is: ${(distrTime.toDouble()/cnt.toDouble())/1000000.0f}")
                logger.warn("Total gather time: ${gathTime}. Average is: ${(gathTime.toDouble()/cnt.toDouble())/1000000.0f}")
                logger.warn("Total streaming time: ${streamTime}. Average is: ${(streamTime.toDouble()/cnt.toDouble())/1000000.0f}")


                logger.warn("Total rendering (image fetch) time: $imgFetchTime. Average is: ${(imgFetchTime.toDouble()/cnt.toDouble())/1000000.0f}")
                logger.warn("Total rendering (image fetch) time in the last 100 is: ${imgFetchTime-rendPrev}. Average is: ${((imgFetchTime-rendPrev).toDouble()/100.0)/1000000.0f}")

                rendPrev = imgFetchTime;

                logger.warn("Total compositing time: $compositeTime. Average is: ${(compositeTime.toDouble()/cnt.toDouble())/1000000.0f}")
                logger.warn("Total GPU-send time: $gpuSendTime.")
            }
            cnt++

        }
    }


    override fun inputSetup() {
        setupCameraModeSwitching()

        val toggleRenderingMode = object : ClickBehaviour {
            var modes = Volume.RenderingMethod.values()
            var currentMode = (scene.find("volume") as? Volume)?.renderingMethod?.ordinal ?: 0

            override fun click(x: Int, y: Int) {
                currentMode = (currentMode + 1) % modes.size

                (scene.find("volume") as? Volume)?.renderingMethod = Volume.RenderingMethod.values().get(currentMode)
                logger.info("Switched volume rendering mode to ${modes[currentMode]} (${(scene.find("volume") as? Volume)?.renderingMethod})")
            }
        }

        inputHandler?.addBehaviour("toggle_rendering_mode", toggleRenderingMode)
        inputHandler?.addKeyBinding("toggle_rendering_mode", "M")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            RenderAndComposite().main()
        }
    }
}
