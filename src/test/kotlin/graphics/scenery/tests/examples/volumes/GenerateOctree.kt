package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.SystemHelpers
import net.imglib2.type.numeric.real.FloatType
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.pow


class GenerateOctree : SceneryBase("GenerateOctree", 1832, 1016) {

    val separateDepth = true

    override fun init() {

        val numLayers = if(separateDepth) {
            1
        } else {
            3
        }

        val numOctreeLayers = 8

        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val numSupersegments = 20

        val buff: ByteArray
        val depthBuff: ByteArray?

        val dataset = "Stagbeetle"

        if(separateDepth) {
            buff = File("/home/aryaman/Repositories/scenery-insitu/${dataset}VDI4_ndc_col").readBytes()
            depthBuff = File("/home/aryaman/Repositories/scenery-insitu/${dataset}VDI4_ndc_depth").readBytes()

        } else {
            buff = File("/home/aryaman/Repositories/scenery-insitu/VDI10_ndc").readBytes()
            depthBuff = null
        }

        var colBuffer: ByteBuffer
        var depthBuffer: ByteBuffer?
//        colBuffer = ByteBuffer.wrap(buff)
//        depthBuffer = ByteBuffer.wrap(depthBuff)
        colBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * numLayers * 4)
        colBuffer.put(buff).flip()
        logger.info("Length of color buffer is ${buff.size} and associated bytebuffer capacity is ${colBuffer.capacity()} it has remaining: ${colBuffer.remaining()}")
        logger.info("Col sum is ${buff.sum()}")

        if(separateDepth) {
            depthBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * 4 * 2)
            depthBuffer.put(depthBuff).flip()
            logger.info("Length of depth buffer is ${depthBuff!!.size} and associated bytebuffer capacity is ${depthBuffer.capacity()} it has remaining ${depthBuffer.remaining()}")
            logger.info("Depth sum is ${depthBuff.sum()}")
        } else {
            depthBuffer = null
        }

        val numVoxels = 2.0.pow(numOctreeLayers)
        val lowestLevel = MemoryUtil.memCalloc(numVoxels.pow(3).toInt() * 4)

        val compute = RichNode()
        compute.name = "compute node"

        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("OctreeGenerator.comp"), this@GenerateOctree::class.java))) {
            textures["InputVDI"] = Texture(Vector3i(numSupersegments*numLayers, windowHeight, windowWidth), 4, contents = colBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
            textures["DepthVDI"] = Texture(Vector3i(2*numSupersegments, windowHeight, windowWidth), 1, contents = depthBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType())
            textures["OctreeCells"] = Texture.fromImage(Image(lowestLevel, numVoxels.toInt(), numVoxels.toInt(), numVoxels.toInt()), channels = 4,
                usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
//            textures["OctreeCells"]!!.mipmap = false
        }

        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowWidth, windowHeight, 1),
            invocationType = InvocationType.Permanent
        )

        scene.addChild(compute)

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial().position = Vector3f(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }

        var numWrites = 0
        thread {
            var lowestBuff: ByteBuffer

            while(renderer?.firstImageReady == false) {
                Thread.sleep(50)
            }

            val octreeLowest = compute.material().textures["OctreeCells"]!!
            val cnt1 = AtomicInteger(0)

            (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (octreeLowest to cnt1)

            var prevAtomic = cnt1.get()

            while(true) {
                while(cnt1.get() == prevAtomic) {
                    Thread.sleep(5)
                }
                prevAtomic = cnt1.get()

                lowestBuff = octreeLowest.contents!!

                if(numWrites < 10) {
                    val fileName = "octree_lowest$numWrites.raw"
                    SystemHelpers.dumpToFile(lowestBuff, fileName)

                    logger.info("Wrote octree")
                    numWrites++
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GenerateOctree().main()
        }
    }
}
