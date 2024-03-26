package graphics.scenery.tests.applications.vdi

import graphics.scenery.SceneryBase
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.vdi.VDIData
import graphics.scenery.volumes.vdi.VDINode
import graphics.scenery.volumes.vdi.VDIStreamer
import org.joml.Vector3f
import org.zeromq.ZContext
import kotlin.concurrent.thread

/**
 * Example application showing how to create a client application for receiving VDIs across a network
 * and rendering them.
 *
 * To launch, the following VM parameter needs to be set: -Dscenery.Server=true
 *
 * Though this is a client application for streaming VDIs, it is a server in scenery's networking code
 * as it controls the scene configuration (e.g., camera pose).
 *
 * Can be used with [VDIStreamingExample]
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */
class VDIClientExample : SceneryBase("VDI Client", 512, 512, wantREPL = false) {

    val cam: Camera = DetachedHeadCamera()
    val plane = FullscreenObject()
    val context = ZContext(4)
    val numSupersegments = 20

    lateinit var vdiNode: VDINode
    val skipEmpty = true

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        //Step1: create Camera
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.5f, 5.0f)
            }
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            scene.addChild(this)
        }
        cam.farPlaneDistance = 20.0f

        val vdiData = VDIData()

        //Step 2: Create vdi node and its properties
        vdiNode = VDINode(windowWidth, windowHeight, numSupersegments, vdiData)
        scene.addChild(vdiNode)

        //Attaching empty textures as placeholders for VDIs before actual VDIs arrive so that rendering can begin
        vdiNode.attachEmptyTextures(VDINode.DoubleBuffer.First)
        vdiNode.attachEmptyTextures(VDINode.DoubleBuffer.Second)

        vdiNode.skip_empty = false
        vdiNode.visible = true

        //Step3: set plane properties
        scene.addChild(plane)
        plane.material().textures["diffuse"] = vdiNode.material().textures["OutputViewport"]!!

        val vdiStreamer = VDIStreamer()

        //Step 4: call receive and update VDI
        thread {
            while (!renderer!!.firstImageReady) {
                Thread.sleep(100)
            }
            vdiStreamer.receiveAndUpdate(vdiNode, "tcp://localhost:6655", renderer!!, windowWidth, windowHeight, numSupersegments)
        }
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
            VDIClientExample().main()
        }
    }
}
