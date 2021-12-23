package graphics.scenery.tests.examples.volumes


import graphics.scenery.*
import graphics.scenery.attribute.material.Material
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
import net.imglib2.type.numeric.real.FloatType
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

    val separateDepth = true
    val world_abs = false

    val old_viewpoint = true

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
        val numLayers = if(separateDepth) {
            1
        } else {
            3         // VDI supersegments require both front and back depth values, along with color
        }

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
        val outputSubDepthBuffer = if(separateDepth) {
            MemoryUtil.memCalloc(windowHeight*windowWidth*4*maxSupersegments*2)
        } else {
            MemoryUtil.memCalloc(0)
        }
        val outputSubVDIColor: Texture
        val outputSubVDIDepth: Texture

        outputSubVDIColor = Texture.fromImage(Image(outputSubColorBuffer, numLayers*maxSupersegments, windowHeight, windowWidth), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))

        volumeManager.customTextures.add("OutputSubVDIColor")
        volumeManager.material().textures["OutputSubVDIColor"] = outputSubVDIColor

        if(separateDepth) {
            outputSubVDIDepth = Texture.fromImage(Image(outputSubDepthBuffer, 2*maxSupersegments, windowHeight, windowWidth),  usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
            volumeManager.customTextures.add("OutputSubVDIDepth")
            volumeManager.material().textures["OutputSubVDIDepth"] = outputSubVDIDepth
        }

        hub.add(volumeManager)

        val cam = DetachedHeadCamera()
        with(cam) {
//            position = Vector3f(-1.0f, -0.2f, -20f)
            if(old_viewpoint) {
                spatial().position = Vector3f(-4.365f, 0.38f, 0.62f)
                perspectiveCamera(50.0f, windowWidth, windowHeight)
            } else {
                spatial().position = Vector3f(0f, 0f, 0f)
                perspectiveCamera(33.716797f, windowWidth, windowHeight, 1f, 10000f)
            }

            scene.addChild(this)
        }

        if(old_viewpoint) {
            cam.spatial{
                position = Vector3f(3.213f, 8.264E-1f, -9.844E-1f)
                rotation = Quaternionf(3.049E-2, 9.596E-1, -1.144E-1, -2.553E-1)
            }

            cam.farPlaneDistance = 20.0f
        } else {
            cam.spatial {
                rotation.rotateZ(-0f)
                rotation.rotateY(-3.484829104f)
                rotation.rotateX(0.07563105061f)

                logger.info("After rot and before translation, Inv view matrix was: ${cam.getTransformationForEye(0).invert()}")
                position = Vector3f(-313.644989f, -74.846016f, -822.299500f)
            }
        }


        val shell = Box(Vector3f(10.0f, 10.0f, 10.0f), insideNormals = true)
        shell.material {
            cullingMode = Material.CullingMode.None
            diffuse = Vector3f(0.1f, 0.1f, 0.1f)
            specular = Vector3f(0.0f)
            ambient = Vector3f(0.0f)
        }
        shell.spatial().position = Vector3f(0.0f, 4.0f, 0.0f)
        scene.addChild(shell)
        shell.visible = false

        val volume = if(bitsPerVoxel == 8) {
            Volume.fromBuffer(emptyList(), volumeSize.toInt(), volumeSize.toInt(), volumeSize.toInt(), UnsignedByteType(), hub)
        } else {
            Volume.fromBuffer(emptyList(), volumeSize.toInt(), volumeSize.toInt(), volumeSize.toInt(), UnsignedShortType(), hub)
        }

        volume.name = "volume"
        volume.spatial().position = Vector3f(2.0f, 6.0f, 4.0f)
//        volume.position = cam.position + cam.forward.mul(6f)
        volume.colormap = Colormap.get("hot")
        if(old_viewpoint) {
            volume.pixelToWorldRatio = 0.03f
        } else {
            volume.pixelToWorldRatio = 1f
            volume.spatial().position = Vector3f(-128f)
        }

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
//                    if(count == 5) {
//                        break
//                    }
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

        val subVDIColor = volumeManager.material().textures["OutputSubVDIColor"]!!
        val subvdi = AtomicInteger(0)

        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (subVDIColor to subvdi)

        val subvdiCnt = AtomicInteger(0)
        var subVDIDepth: Texture? = null

        if(separateDepth) {
            subVDIDepth = volumeManager.material().textures["OutputSubVDIDepth"]!!
            (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (subVDIDepth to subvdiCnt)
        }

        var prevAtomic = subvdi.get()

        var cnt = 0
        while (true) {
            while(subvdi.get() == prevAtomic) {
                Thread.sleep(5)
            }
            prevAtomic = subvdi.get()
            subVDIColorBuffer = subVDIColor.contents
            if(separateDepth) {
                subVDIDepthBuffer = subVDIDepth!!.contents
            }

            if(cnt < 20) {
                var fileName = ""
                if(world_abs) {
                    fileName = "VDI${cnt}_world_new"
                } else {
                    fileName = "VDI${cnt}_ndc"
                }
                if(separateDepth) {
                    SystemHelpers.dumpToFile(subVDIColorBuffer!!, "${fileName}_col")
                    SystemHelpers.dumpToFile(subVDIDepthBuffer!!, "${fileName}_depth")
                } else {
                    SystemHelpers.dumpToFile(subVDIColorBuffer!!, fileName)
                }
                logger.info("Wrote VDI $cnt")
            }
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
