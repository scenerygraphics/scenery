package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import net.imglib2.type.numeric.real.FloatType
import org.joml.Vector3f
import org.joml.Vector3i
import org.junit.Test
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer
import kotlin.concurrent.thread

val separateDepth = true

class VDIRendererSimple : SceneryBase("SimpleVDIRenderer", 1832, 1016) {
    override fun init() {

        val numLayers = if(separateDepth) {
            1
        } else {
            3
        }

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

        val outputBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * 4)

        val compute = RichNode()
        compute.name = "compute node"

        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("SimpleVDIRenderer.comp"), this@VDIRendererSimple::class.java))) {
            textures["OutputViewport"] = Texture.fromImage(Image(outputBuffer, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
            textures["OutputViewport"]!!.mipmap = false
        }

        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowWidth, windowHeight, 1),
            invocationType = InvocationType.Permanent
        )
        compute.material().textures["InputVDI"] = Texture(Vector3i(numSupersegments*numLayers, windowHeight, windowWidth), 4, contents = colBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        compute.material().textures["DepthVDI"] = Texture(Vector3i(2*numSupersegments, windowHeight, windowWidth), 1, contents = depthBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType())

        scene.addChild(compute)

        val plane = FullscreenObject()
        plane.material().textures["diffuse"] = compute.material().textures["OutputViewport"]!!

        scene.addChild(plane)

        val light = PointLight(radius = 15.0f)
        light.position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = Vector3f(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }

//        thread {
//            while (running) {
//                box.rotation.rotateY(0.01f)
//                box.needsUpdate = true
////                box.material.textures["diffuse"] = compute.material.textures["OutputViewport"]!!
//
//                Thread.sleep(20)
//            }
//        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIRendererSimple().main()
        }
    }
}
