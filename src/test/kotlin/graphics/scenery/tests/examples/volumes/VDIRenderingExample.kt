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
import graphics.scenery.volumes.VolumeManager
import net.imglib2.type.numeric.real.FloatType
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */
class VDIRenderingExample : SceneryBase("VDI Rendering", 1280, 720, wantREPL = false) {

    val separateDepth = true

    override fun init () {

        val numLayers = if(separateDepth) {
            1
        } else {
            3
        }

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val light = PointLight(radius = 15.0f)
        light.position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam = DetachedHeadCamera()
        with(cam) {
            position = Vector3f(-4.365f, 0.38f, 0.62f)
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        cam.position = Vector3f(3.213f, 8.264E-1f, -9.844E-1f)
        cam.rotation = Quaternionf(3.049E-2, 9.596E-1, -1.144E-1, -2.553E-1)

//         optimized depth calculation working at this view point, opacity calculation looking reasonable
//        cam.rotation = Quaternionf(5.449E-2,  8.801E-1, -1.041E-1, -4.601E-1)
//        cam.position = Vector3f(6.639E+0f,  1.092E+0f, -1.584E-1f)

//        same as previous
//        cam.position = Vector3f(1.881E+0f,  5.558E+0f, -7.854E-1f)
//        cam.rotation = Quaternionf(-2.733E-2, 9.552E-1, 2.793E-1, -9.365E-2)

//        cam.position = Vector3f(-3.435E+0f,  1.109E+0f,  6.433E+0f)
//        cam.rotation = Quaternionf(-3.985E-2,  5.315E-1, -2.510E-2,  8.457E-1)

//        cam.position = Vector3f(3.729E+0f,  8.263E-1f, -6.808E-1f)
//        cam.rotation = Quaternionf(2.731E-2,  9.596E-1, -9.999E-2, -2.616E-1)

//        cam.position = Vector3f(3.729E+0f,  8.263E-1f, -6.808E-1f)
//        cam.rotation = Quaternionf(1.499E-2,  9.660E-1, -5.738E-2, -2.517E-1)

//        cam.position = Vector3f(4.374E+0f, 8.262E-1f,-3.773E-1f)
//        cam.rotation = Quaternionf(2.247E-2,  9.707E-1, -1.003E-1, -2.171E-1)

//        cam.position = Vector3f( 3.853E+0f,  7.480E-1f, -9.672E-1f)
//        cam.rotation = Quaternionf( 4.521E-2,  9.413E-1, -1.398E-1, -3.040E-1)

        cam.position = Vector3f( 3.216E+0f,  8.251E-1f, -9.890E-1f)
        cam.rotation = Quaternionf( 3.049E-2,  9.596E-1, -1.144E-1, -2.553E-1)

        cam.farPlaneDistance = 20.0f

        val numSupersegments = 20

        val buff: ByteArray
        val depthBuff: ByteArray?

        if(separateDepth) {
            buff = File("/home/aryaman/Repositories/scenery-insitu/VDI10_ndc_col").readBytes()
            depthBuff = File("/home/aryaman/Repositories/scenery-insitu/VDI10_ndc_depth").readBytes()

        } else {
            buff = File("/home/aryaman/Repositories/scenery-insitu/VDI10_ndc").readBytes()
            depthBuff = null
        }

        val opBuffer = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)

        var colBuffer: ByteBuffer
        var depthBuffer: ByteBuffer?

        colBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * numLayers * 4)
        colBuffer.put(buff).flip()

        if(separateDepth) {
            depthBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * 4 * 2)
            depthBuffer.put(depthBuff).flip()
        } else {
            depthBuffer = null
        }

        val compute = Node()
        compute.name = "compute node"
//        compute.material = ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("EfficientVDIRaycast.comp"), this::class.java))
        compute.material = ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("AmanatidesJumps.comp"), this::class.java))
        compute.material.textures["OutputViewport"] = Texture.fromImage(Image(opBuffer, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        compute.material.textures["InputVDI"] = Texture(Vector3i(numLayers*numSupersegments, windowHeight, windowWidth), 4, contents = colBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        if(separateDepth) {
            compute.material.textures["DepthVDI"] = Texture(Vector3i(2*numSupersegments, windowHeight, windowWidth), 1, contents = depthBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType())
        }
        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowWidth, windowHeight, 1),
            invocationType = InvocationType.Permanent
        )

        scene.addChild(compute)

        val plane = FullscreenObject()
        scene.addChild(plane)
        plane.material.textures["diffuse"] = compute.material.textures["OutputViewport"]!!
        thread {
            Thread.sleep(50000)
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
//        thread {
//            while(true)
//            {
//                Thread.sleep(2000)
//                println("${cam.position}")
//                println("${cam.rotation}")
//            }
//        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIRenderingExample().main()
        }
    }

}
