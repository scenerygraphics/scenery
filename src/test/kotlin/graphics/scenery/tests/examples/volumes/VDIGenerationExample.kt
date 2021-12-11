package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.numerics.Random
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.RingBuffer
import graphics.scenery.utils.SystemHelpers
import graphics.scenery.utils.extensions.plus
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.VolumeManager
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import tpietzsch.shadergen.generate.SegmentTemplate
import tpietzsch.shadergen.generate.SegmentType
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class VDIGenerationExample : SceneryBase("VDI Generation") {

    val bitsPerVoxel = 8
    val volumeSize = 128L

    lateinit var volumeManager: VolumeManager

    override fun init() {
        windowWidth = 1280
        windowHeight = 720

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val raycastShader: String
        val accumulateShader: String
        val compositeShader: String

        raycastShader = "VDIGenerator.comp"
        accumulateShader = "AccumulateVDI.comp"
        compositeShader = "VDICompositor.comp"
        val maxSupersegments = 20
        val maxOutputSupersegments = 40
        val numLayers = 3 // VDI supersegments require both front and back depth values, along with color

        volumeManager = VolumeManager(
            hub, useCompute = true, customSegments = hashMapOf(
                SegmentType.FragmentShader to SegmentTemplate(
                    this.javaClass,
                    raycastShader,
                    "intersectBoundingBox", "vis", "SampleVolume", "Convert", "Accumulate",
                ),
                SegmentType.Accumulator to SegmentTemplate(
//                                this.javaClass,
                    accumulateShader,
                    "vis", "sampleVolume", "convert",
                ),
            ),
        )

        val outputSubColorBuffer = MemoryUtil.memCalloc(windowHeight*windowWidth*4*maxSupersegments*numLayers)
        val outputSubDepthBuffer = MemoryUtil.memCalloc(windowHeight*windowWidth*4*maxSupersegments)
        val outputSubVDIColor: Texture
        val outputSubVDIDepth: Texture

        outputSubVDIColor = Texture.fromImage(Image(outputSubColorBuffer, 3*maxSupersegments, windowHeight, windowWidth), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))

        volumeManager.customTextures.add("OutputSubVDIColor")
        volumeManager.material.textures["OutputSubVDIColor"] = outputSubVDIColor
        hub.add(volumeManager)

        val cam = DetachedHeadCamera()
        with(cam) {
            position = Vector3f(-4.365f, 0.38f, 0.62f)
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        cam.position = Vector3f(3.213f, 8.264E-1f, -9.844E-1f)
        cam.rotation = Quaternionf(3.049E-2, 9.596E-1, -1.144E-1, -2.553E-1)

        cam.farPlaneDistance = 20.0f

        val shell = Box(Vector3f(10.0f, 10.0f, 10.0f), insideNormals = true)
        shell.material.cullingMode = Material.CullingMode.None
        shell.material.diffuse = Vector3f(0.1f, 0.1f, 0.1f)
        shell.material.specular = Vector3f(0.0f)
        shell.material.ambient = Vector3f(0.0f)
        shell.position = Vector3f(0.0f, 4.0f, 0.0f)
        scene.addChild(shell)
        shell.visible = false

        val volume = if(bitsPerVoxel == 8) {
            Volume.fromBuffer(emptyList(), volumeSize.toInt(), volumeSize.toInt(), volumeSize.toInt(), UnsignedByteType(), hub)
        } else {
            Volume.fromBuffer(emptyList(), volumeSize.toInt(), volumeSize.toInt(), volumeSize.toInt(), UnsignedShortType(), hub)
        }

        volume.name = "volume"
        volume.position = Vector3f(2.0f, 2.0f, 4.0f)
        volume.colormap = Colormap.get("hot")
        volume.pixelToWorldRatio = 0.03f

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

        lights.mapIndexed { i, light ->
            light.position = Vector3f(2.0f * i - 4.0f,  i - 1.0f, 0.0f)
            light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            light.intensity = 0.2f
            scene.addChild(light)
        }

        thread {
            val volumeBuffer = RingBuffer<ByteBuffer>(2) { MemoryUtil.memAlloc((volumeSize*volumeSize*volumeSize*bitsPerVoxel/8).toInt()) }

//            val seed = Random.randomFromRange(0.0f, 133333337.0f).toLong()
            val seed = 1000L
            var shift = Vector3f(0.0f)
//            val shiftDelta = Random.random3DVectorFromRange(-1.5f, 1.5f)
            val shiftDelta = Vector3f(1.0f)

            var count = 0
            while(running && !shouldClose) {
                if(volume.metadata["animating"] == true) {
                    val currentBuffer = volumeBuffer.get()

                    Volume.generateProceduralVolume(volumeSize, 0.35f, seed = seed,
                        intoBuffer = currentBuffer, shift = shift, use16bit = bitsPerVoxel > 8)

                    volume.addTimepoint("t-${count}", currentBuffer)
                    volume.goToLastTimepoint()

                    volume.purgeFirst(10, 10)

                    shift += shiftDelta
                    count++
                    if(count == 5) {
                        break
                    }
                }

                Thread.sleep(kotlin.random.Random.nextLong(10L, 200L))
            }
        }
        thread {
            manageVDIGeneration()
        }
    }

    private fun manageVDIGeneration() {
        var subVDIDepthBuffer: ByteBuffer? = null
        var subVDIColorBuffer: ByteBuffer?

        while(renderer?.firstImageReady == false) {
            Thread.sleep(50)
        }

        val subVDIColor = volumeManager.material.textures["OutputSubVDIColor"]!!

        val subvdi = AtomicInteger(0)

        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (subVDIColor to subvdi)

        var prevAtomic = subvdi.get()

        var cnt = 0
        while (true) {
            while(subvdi.get() == prevAtomic) {
                Thread.sleep(5)
            }
            prevAtomic = subvdi.get()
            subVDIColorBuffer = subVDIColor.contents

//            SystemHelpers.dumpToFile(subVDIColorBuffer!!, "VDI${cnt}_ndc")
            logger.info("Wrote VDI $cnt")
            cnt++
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIGenerationExample().main()
        }
    }
}
