package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import net.imglib2.type.numeric.integer.UnsignedIntType
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer

class OctreeInspector : SceneryBase("Octree Inscpector", 256, 256, wantREPL = false) {
    override fun init() {

        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val numOctreeLayers = 8

        val buff: ByteArray

        buff = File("/home/aryaman/Repositories/scenery-insitu/StagbeetleVDI4_ndc_octree").readBytes()

        val octBuffer: ByteBuffer

        octBuffer = MemoryUtil.memCalloc(windowHeight * windowHeight * windowHeight * 4)
        octBuffer.put(buff).flip()

        val outputBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * 4)

        val compute = RichNode()
        compute.name = "compute node"

        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("StepThroughOctree.comp"), this@OctreeInspector::class.java))) {
            textures["OutputViewport"] = Texture.fromImage(Image(outputBuffer, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
            textures["OutputViewport"]!!.mipmap = false
        }

        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowWidth, windowHeight, 1),
            invocationType = InvocationType.Permanent
        )
        compute.material().textures["OctreeCells"] = Texture(Vector3i(windowHeight, windowHeight, windowWidth), 1, type = UnsignedIntType(), contents = octBuffer, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))

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

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            OctreeInspector().main()
        }
    }
}
