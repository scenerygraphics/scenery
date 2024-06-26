package graphics.scenery.tests.applications.vdi

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.volumes.*
import graphics.scenery.volumes.vdi.VDIStreamer
import graphics.scenery.volumes.vdi.VDIVolumeManager
import org.joml.Vector3f
import org.zeromq.ZContext
import java.nio.file.Paths
import kotlin.concurrent.thread

/**
 * An example application for a volume rendering server capable of performing both volume rendering and generation
 * and streaming of Volumetric Depth Images (VDIs). When volume rendering is performed, an encoded video is streamed.
 *
 * [ClientApplication] can be used as a client with this application as server.
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de> and Wissal Salhi
 */
class ServerApplication : SceneryBase("Volume Server Example", 512, 512) {

    var hmd: TrackedStereoGlasses? = null
    val maxSupersegments = 20
    val context: ZContext = ZContext(4)
    var firstVDI = true

    override fun init() {
        //Step 1: Create common elements for VDI streaming and volumeRendering
        renderer = hub.add( SceneryElement.Renderer, Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        if(!Settings().get("RemoteCamera",false)) {
            val cam: Camera = DetachedHeadCamera()
            with(cam) {
                spatial {
                    position = Vector3f(0.0f, 0.0f, 5.0f)
                }
                perspectiveCamera(50.0f, 512, 512)

                scene.addChild(this)
            }
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

        //Step 2: create a volume manager for vdi and add the volume to it:
        var vdiVolumeManager = VDIVolumeManager(hub, windowWidth, windowHeight, maxSupersegments, scene).createVDIVolumeManager()

        vdiVolumeManager.customUniforms.add("doGeneration")
        vdiVolumeManager.shaderProperties["doGeneration"] = true

        //Step 3:  save the standard volume manger, the one that was first created with the volume
        val standardVolumeManager: VolumeManager = hub.get<VolumeManager>() as VolumeManager

        renderer!!.runAfterRendering.add {
            val dummyVolume = scene.find("DummyVolume") as? DummyVolume
            if (dummyVolume != null) {
                volume.transferFunction = dummyVolume.transferFunction
                volume.maxDisplayRange = dummyVolume.maxDisplayRange
                volume.minDisplayRange = dummyVolume.minDisplayRange
                volume.colormap = dummyVolume.colormap
            }
        }

        //Step 4: switch between different modes
        var currentMode = RemoteRenderingProperties.StreamType.None

        val vdiStreamer = VDIStreamer()

        thread {

            while (!renderer!!.firstImageReady) {
                Thread.sleep(50)
            }

            val volumeDimensions3i = Vector3f(volume.getDimensions().x.toFloat(), volume.getDimensions().y.toFloat(), volume.getDimensions().z.toFloat())

            scene.findObserver()?.let { cam ->
                vdiStreamer.setup("tcp://0.0.0.0:6655", cam, volumeDimensions3i, volume, maxSupersegments, vdiVolumeManager, renderer!!)
            }

            renderer!!.runAfterRendering.add {
                val switchMode = scene.find("RemoteRenderingProperties") as? RemoteRenderingProperties

                if (switchMode != null) {
                    logger.info("Value of switch mode: ${switchMode.streamType.toString()}")

                    if (currentMode != RemoteRenderingProperties.StreamType.VolumeRendering
                        && switchMode.streamType == RemoteRenderingProperties.StreamType.VolumeRendering) {

                        logger.info("Server will switch to Volume Rendering")

                        vdiStreamer.vdiStreaming.set(false)
                        standardVolumeManager.replace(vdiVolumeManager)
                        startVideoStream()

                        currentMode = RemoteRenderingProperties.StreamType.VolumeRendering

                    }
                    else if (currentMode != RemoteRenderingProperties.StreamType.VDI
                        && switchMode.streamType == RemoteRenderingProperties.StreamType.VDI) {
                        //TODO: investigating glitching in VDI generation/streaming and comparing with VDIStreamingExample where they do not happen
                        logger.info("Server will switch to VDI Streaming")

                        if(currentMode == RemoteRenderingProperties.StreamType.VolumeRendering) {
                            //stop the video streaming
                            renderer?.recordMovie()
                        }
                        vdiVolumeManager.replace(standardVolumeManager)

                        vdiStreamer.vdiStreaming.set(true)

                        currentMode = RemoteRenderingProperties.StreamType.VDI
                    }
                    else if (currentMode != RemoteRenderingProperties.StreamType.None
                        && switchMode.streamType == RemoteRenderingProperties.StreamType.None) {

                        logger.info("Server will stop streaming")

                        vdiStreamer.vdiStreaming.set(false)
                        if(currentMode == RemoteRenderingProperties.StreamType.VolumeRendering) {
                            renderer?.recordMovie()
                        }

                        currentMode = RemoteRenderingProperties.StreamType.None
                    }
                }
            }
        }

    }

    private fun startVideoStream() {
        settings.set("VideoEncoder.StreamVideo", true)
        settings.set("VideoEncoder.StreamingAddress", "rtp://127.0.0.2:5004")
        renderer!!.recordMovie()
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
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
            ServerApplication().main()
        }
    }
}
