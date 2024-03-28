package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.vdi.VDIDataIO
import graphics.scenery.volumes.vdi.VDINode
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.ByteBuffer

/**
 * Example showing how a VDI can be rendered.
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */
class VDIRenderingExample : SceneryBase("VDI Rendering Example", 512, 512) {

    val vdiFilename = "example4"
    val skipEmpty = false

    val numSupersegments = 20

    lateinit var vdiNode: VDINode
    val numLayers = 1

    val cam: Camera = DetachedHeadCamera()

    override fun init() {

        // Step 1: create a Renderer, Point light and camera
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        with(cam) {
            spatial().position = Vector3f(0.0f, 0.5f, 5.0f)
            perspectiveCamera(50.0f, windowWidth, windowWidth)
            scene.addChild(this)
        }

        // Step 2: read files
        val file = try {
            FileInputStream(File("$vdiFilename.vdi-metadata"))
        } catch(e: FileNotFoundException) {
            logger.error("File ${vdiFilename}.vdi-metadata not found!")
            return
        }

        val vdiData = VDIDataIO.read(file)
        logger.info("Fetching file...")

        vdiNode = VDINode(windowWidth, windowHeight, numSupersegments, vdiData)

        val colorArray: ByteArray = File("$vdiFilename.vdi-color").readBytes()
        val depthArray: ByteArray = File("$vdiFilename.vdi-depth").readBytes()
        val gridArray: ByteArray = File("$vdiFilename.vdi-grid").readBytes()

        // Step 3: assigning buffer values
        val colBuffer: ByteBuffer = MemoryUtil.memCalloc(colorArray.size)
        colBuffer.put(colorArray).flip()
        colBuffer.limit(colBuffer.capacity())

        val depthBuffer = MemoryUtil.memCalloc(depthArray.size)
        depthBuffer.put(depthArray).flip()
        depthBuffer.limit(depthBuffer.capacity())

        val gridBuffer = MemoryUtil.memAlloc(gridArray.size)
        if(skipEmpty) {
            gridBuffer.put(gridArray).flip()
            gridBuffer.limit(gridBuffer.capacity())
        }

        //Step 4: Attaching the buffers to the vdi node and adding it to the scene
        vdiNode.attachTextures(colBuffer, depthBuffer, gridBuffer)

        vdiNode.skip_empty = skipEmpty

        //Attaching empty textures as placeholders for 2nd VDI buffer, which is unused here
        vdiNode.attachEmptyTextures(VDINode.DoubleBuffer.Second)

        scene.addChild(vdiNode)

        val plane = FullscreenObject()
        plane.material().textures["diffuse"] = vdiNode.material().textures["OutputViewport"]!!
        scene.addChild(plane)
    }

    /**
     * Companion object for providing a main method.
     */
    companion object {
        /**
         * The main entry point. Executes this example application when it is called.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            VDIRenderingExample().main()
        }
    }
}
