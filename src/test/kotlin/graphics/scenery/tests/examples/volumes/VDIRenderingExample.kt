package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.volumes.VolumeManager
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.io.File
import kotlin.concurrent.thread

/**
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */
class VDIRenderingExample : SceneryBase("VDI Rendering", 600, 600, wantREPL = false) {

    override fun init () {
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

        val numSupersegments = 40

        val buff: ByteArray = File("/home/aryaman/Repositories/openfpm_pdata/example/Grid/3_gray_scott_3d/size:1Final_VDICol100.raw").readBytes()

        val opBuffer = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)
        val bufferVDI = MemoryUtil.memCalloc(3 * windowWidth * windowHeight * numSupersegments * 4)

        logger.info("Actual size is: ${buff.size}")

        bufferVDI.put(buff).flip()

        val compute = Node()
        compute.name = "compute node"
        compute.material = ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("RaycastVDI.comp"), this::class.java))
        compute.material.textures["OutputViewport"] = Texture.fromImage(Image(opBuffer, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        compute.material.textures["InputVDI"] = Texture(Vector3i(3*numSupersegments, windowHeight, windowWidth), 4, contents = bufferVDI, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowWidth, windowHeight, 1),
            invocationType = InvocationType.Once
        )

        scene.addChild(compute)

        val plane = FullscreenObject()
        scene.addChild(plane)
        plane.material.textures["diffuse"] = compute.material.textures["OutputViewport"]!!
//    thread {
//        while(true) {
//
//            logger.info(cam.view.toString())
//
//            logger.info("The projection matrix is:")
//
//            logger.info(cam.projection.toString())
//            Thread.sleep(2000)
//        }
//
//    }

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIRenderingExample().main()
        }
    }

}