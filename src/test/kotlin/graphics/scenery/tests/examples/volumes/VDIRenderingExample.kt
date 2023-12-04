package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.vdi.VDIDataIO
import graphics.scenery.volumes.vdi.VDINode
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

class VDIRenderingExample(applicationName: String, windowWidth: Int, windowHeight: Int): SceneryBase(applicationName, windowWidth,windowHeight) {

    val skipEmpty = false

    val numSupersegments = 20

    lateinit var vdiNode: VDINode
    val numLayers = 1

    val cam: Camera = DetachedHeadCamera()

    override fun init() {

        //Step 1: create a Renderer, Point light and camera
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        with(cam) {
            spatial().position = Vector3f( 4.622E+0f, -9.060E-1f, -1.047E+0f)
            spatial().rotation = Quaternionf( 5.288E-2, -9.096E-1, -1.222E-1,  3.936E-1)
            perspectiveCamera(50.0f, windowWidth, windowWidth)
            scene.addChild(this)
        }

        //Step 2: read files
        val file = FileInputStream(File("VDI_dump4"))
        val vdiData = VDIDataIO.read(file)
        logger.info("Fetching file...")

        vdiNode = VDINode(windowWidth, windowHeight, numSupersegments, vdiData)

        val colorArray: ByteArray = File("VDI_col").readBytes()
        val depthArray: ByteArray = File("VDI_depth").readBytes()
        val octArray: ByteArray = File("VDI_octree").readBytes()

        //Step  3: assigning buffer values
        val colBuffer: ByteBuffer = MemoryUtil.memCalloc(vdiNode.vdiHeight * vdiNode.vdiWidth * numSupersegments * numLayers * 4 * 4)
        colBuffer.put(colorArray).flip()
        colBuffer.limit(colBuffer.capacity())

        val depthBuffer = MemoryUtil.memCalloc(vdiNode.vdiHeight * vdiNode.vdiWidth * numSupersegments * 2 * 2 * 2)
        depthBuffer.put(depthArray).flip()
        depthBuffer.limit(depthBuffer.capacity())

        val gridBuffer = MemoryUtil.memCalloc(vdiNode.numGridCells.x.toInt() * vdiNode.numGridCells.y.toInt() * vdiNode.numGridCells.z.toInt() * 4)
        if(skipEmpty) {
            gridBuffer.put(octArray).flip()
        }

        //Step 4: Creating compute node and attach shader and vdi Files to
        vdiNode.attachTextures(colBuffer, depthBuffer, gridBuffer)

        vdiNode.skip_empty = skipEmpty

        //Attaching empty textures as placeholders for 2nd VDI buffer, which is unused here
        vdiNode.attachEmptyTextures(VDINode.DoubleBuffer.Second)

        scene.addChild(vdiNode)

        val plane = FullscreenObject()
        plane.material().textures["diffuse"] = vdiNode.material().textures["OutputViewport"]!!
        scene.addChild(plane)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIRenderingExample("VDI Rendering Example", 1280, 720).main()
        }
    }
}
