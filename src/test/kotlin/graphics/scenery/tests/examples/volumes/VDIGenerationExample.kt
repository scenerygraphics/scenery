package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.volumes.VolumeManager
import tpietzsch.shadergen.generate.SegmentTemplate
import tpietzsch.shadergen.generate.SegmentType
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.vdi.*
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.utils.SystemHelpers
import graphics.scenery.volumes.*
import org.joml.*
import org.zeromq.ZContext
import java.io.*
import java.nio.ByteBuffer
import kotlin.concurrent.thread


class VDIGenerationExample : SceneryBase("Volume Manager Switching Example", 512, 512) {

    val maxSupersegments = System.getProperty("VolumeBenchmark.NumSupersegments")?.toInt()?: 20
    val context: ZContext = ZContext(4)

    override fun init() {

        // Step 1: Create Volume
        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.5f, 5.0f)
            }
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        val volume = Volume.fromPathRaw(Paths.get(getDemoFilesPath() + "/volumes/box-iso/"), hub)
        volume.name = "volume"
        volume.colormap = Colormap.get("viridis")
        volume.spatial {
            position = Vector3f(0.0f, 0.0f, -3.5f)
            rotation = rotation.rotateXYZ(0.05f, 0.05f, 0.05f)
            scale = Vector3f(20.0f, 20.0f, 20.0f)
        }
        volume.transferFunction = TransferFunction.ramp(0.1f, 0.5f)
        scene.addChild(volume)



        // Step 2: Create VDI Volume Manager
        val vdiVolumeManager =  vdiFull(windowWidth, windowHeight, maxSupersegments, scene, hub)
        logger.warn("222222222 vdi VM " + vdiVolumeManager.getUuid().toString())
        logger.warn("222222222 vdi VM in init: " + hub.get<VolumeManager>()?.getUuid())

//        // Step 3: add volume to vdi vm
        hub.get<VolumeManager>()?.add(volume)

        // Step 4: Vdi volume manager add volume
        vdiVolumeManager.add(volume)

        // Step 5: Vdi volume manager add volume
        hub.add(vdiVolumeManager)

        //step 6: creae vdiVolumeData


        // Step 3: Store VDI Generated
        thread {
            val volumeDimensions = volume.getDimensions()
            storeVDI(vdiVolumeManager, cam, volumeDimensions)
        }

    }

    private fun instantiateVolumeManager(raycastShader: String, accumulateShader: String, hub: Hub): VolumeManager {

        return VolumeManager(
            hub, useCompute = true,
            customSegments = hashMapOf(
                SegmentType.FragmentShader to SegmentTemplate(
                    this::class.java,
                    raycastShader,
                    "intersectBoundingBox", "vis", "localNear", "localFar", "SampleVolume", "Convert", "Accumulate",
                ),
                SegmentType.Accumulator to SegmentTemplate(
//                                this.javaClass,
                    accumulateShader,
                    "vis", "localNear", "localFar", "sampleVolume", "convert",
                ),
            ),
        )
    }

    private fun vdiFull(windowWidth: Int, windowHeight: Int, maxSupersegments: Int, scene: Scene, hub: Hub): VolumeManager {
        val raycastShader = "VDIGenerator.comp"
        val accumulateShader = "AccumulateVDI.comp"
        val volumeManager = instantiateVolumeManager(raycastShader, accumulateShader, hub)

        val outputSubColorBuffer = MemoryUtil.memCalloc(windowHeight*windowWidth*4*maxSupersegments * 4)

        val outputSubDepthBuffer = MemoryUtil.memCalloc(windowHeight*windowWidth*2*maxSupersegments*2 * 2)

//            val numGridCells = 2.0.pow(numOctreeLayers)
        val numGridCells = Vector3f(windowWidth.toFloat() / 8f, windowHeight.toFloat() / 8f, maxSupersegments.toFloat())
//            val numGridCells = Vector3f(256f, 256f, 256f)
        val lowestLevel = MemoryUtil.memCalloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)

        val outputSubVDIColor: Texture = Texture.fromImage(
            Image(outputSubColorBuffer, maxSupersegments, windowHeight, windowWidth), usage = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 4, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        volumeManager.customTextures.add("OutputSubVDIColor")
        volumeManager.material().textures["OutputSubVDIColor"] = outputSubVDIColor

        val outputSubVDIDepth: Texture = Texture.fromImage(
            Image(outputSubDepthBuffer, 2 * maxSupersegments, windowHeight, windowWidth),  usage = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
//                    Image(outputSubDepthBuffer, 2*maxSupersegments, windowHeight, windowWidth),  usage = hashSetOf(
//                        Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        volumeManager.customTextures.add("OutputSubVDIDepth")
        volumeManager.material().textures["OutputSubVDIDepth"] = outputSubVDIDepth


        val gridCells: Texture = Texture.fromImage(
            Image(lowestLevel, numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()), channels = 1, type = UnsignedIntType(),
            usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        volumeManager.customTextures.add("OctreeCells")
        volumeManager.material().textures["OctreeCells"] = gridCells

        volumeManager.customUniforms.add("doGeneration")
        volumeManager.shaderProperties["doGeneration"] = true

        val compute = RichNode()
        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("GridCellsToZero.comp"), this::class.java)))

        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), 1),
            invocationType = InvocationType.Permanent
        )

        compute.material().textures["GridCells"] = gridCells

        scene.addChild(compute)

        return volumeManager
    }

    private fun storeVDI(vdiVolumeManager: VolumeManager, camera: Camera, volumeDimensions: Vector3i) {
        data class Timer(var start: Long, var end: Long)

        var vdiDepthBuffer: ByteBuffer? = null
        var vdiColorBuffer: ByteBuffer?
        var gridCellsBuff: ByteBuffer?
        val separateDepth = true
        val world_abs = false

        val volumeList = ArrayList<BufferedVolume>()
        val VDIsGenerated = AtomicInteger(0)

        val dataset = System.getProperty("VolumeBenchmark.Dataset")?.toString() ?: "Simulation"
        val vo = System.getProperty("VolumeBenchmark.Vo")?.toFloat()?.toInt() ?: 0

        while (renderer?.firstImageReady == false) {
//            while (renderer?.firstImageReady == false || vdiVolumeManager.shaderProperties.isEmpty()) {
                Thread.sleep(50)
        }

        val vdiColor = vdiVolumeManager.material().textures["OutputSubVDIColor"]!!
        val colorCnt = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(vdiColor to colorCnt)

        val depthCnt = AtomicInteger(0)
        var vdiDepth: Texture? = null
        if (separateDepth) {
            vdiDepth = vdiVolumeManager.material().textures["OutputSubVDIDepth"]!!
            (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(vdiDepth to depthCnt)
        }

        val gridCells = vdiVolumeManager.material().textures["OctreeCells"]!!
        val gridTexturesCnt = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(gridCells to gridTexturesCnt)

        var prevColor = colorCnt.get()
        var prevDepth = depthCnt.get()

        var cnt = 0

        val tGeneration = Timer(0, 0)

        while (cnt<5) { //TODO: convert VDI storage also to postRenderLambda

            tGeneration.start = System.nanoTime()
            while (colorCnt.get() == prevColor || depthCnt.get() == prevDepth) {
                Thread.sleep(5)
            }
            prevColor = colorCnt.get()
            prevDepth = depthCnt.get()
            vdiColorBuffer = vdiColor.contents
            if (separateDepth) {
                vdiDepthBuffer = vdiDepth!!.contents
            }
            gridCellsBuff = gridCells.contents

            tGeneration.end = System.nanoTime()

            val timeTaken = (tGeneration.end - tGeneration.start) / 1e9

            logger.info("Time taken for generation (only correct if VDIs were not being written to disk): ${timeTaken}")

            val model = volumeList.first().spatial().world

            val vdiData = VDIData(
                VDIBufferSizes(),
                VDIMetadata(
                    index = cnt,
                    projection = camera.spatial().projection,
                    view = camera.spatial().getTransformation(),
                    volumeDimensions = volumeDimensions as Vector3f,
                    model = model,
                    nw = volumeList.first().volumeManager.shaderProperties["nw"] as Float,
                    windowDimensions = Vector2i(camera.width, camera.height)
                )
            )

            if (cnt == 4) { //store the 4th VDI

                val file = FileOutputStream(File("VDI__dump$cnt"))
                //val comp = GZIPOutputStream(file, 65536)
                VDIDataIO.write(vdiData, file)
                logger.info("written the dump")
                file.close()

                var fileName = ""
                if (!world_abs) {
                    fileName = "VDI_${cnt}_ndc"
                }

                if (separateDepth) {
                    SystemHelpers.dumpToFile(vdiColorBuffer!!, "${fileName}_col")
                    SystemHelpers.dumpToFile(vdiDepthBuffer!!, "${fileName}_depth")
                    SystemHelpers.dumpToFile(gridCellsBuff!!, "${fileName}_octree")
                }

                logger.info("Wrote VDI $cnt")
                VDIsGenerated.incrementAndGet()
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
