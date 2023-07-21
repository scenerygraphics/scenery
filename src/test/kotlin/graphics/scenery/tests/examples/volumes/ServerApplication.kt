package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.volumes.*
import org.joml.Vector3f
import org.zeromq.ZContext
import java.nio.file.Paths
import kotlin.concurrent.thread

class ServerApplication : SceneryBase("Volume Server Example", 512, 512) {

    var hmd: TrackedStereoGlasses? = null
    val maxSupersegments = 20
    val context: ZContext = ZContext(4)
    lateinit var cam : Camera
    var firstVDI = true

    override fun init() {
        //Step 1: Create common elements for VDI streaming and volumeRendering
        renderer = hub.add( SceneryElement.Renderer, Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(50.0f, 512, 512)
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
        val vdiVolumeManager = VDIVolumeManager(hub, windowWidth, windowHeight, maxSupersegments, scene).createVDIVolumeManger()
        vdiVolumeManager.add(volume)

        //Step 3:  save the standard volume manger, the one who was first created with the volume
        val standardVolumeManager: VolumeManager = hub.get<VolumeManager>() as VolumeManager

        thread {
            while (true) {
                volume.spatial {
                    rotation = rotation.rotateY(0.003f)
                }
                Thread.sleep(5)
            }
        }

        //Step 4: render volume first time
        renderVolume(volume)

        //Step 5: switch between different modes
        var currentlyVolumeRendering = true

        thread {
            while (true) {
                val switchMode = scene.find("EmptyNode") as? EmptyNode

                if (switchMode != null) {

                    if (!currentlyVolumeRendering && switchMode.value.equals("toVR")) {

                        logger.warn("Volume Rendering")

                        renderer?.vdiStreaming = false
                        standardVolumeManager.replace(standardVolumeManager)
                        renderVolume(volume)

                        currentlyVolumeRendering = !currentlyVolumeRendering

                    }
                    else if (currentlyVolumeRendering && switchMode.value.equals("toVDI")) {

                        logger.warn("VDI Streaming")

                        renderer?.recordMovie()
                        vdiVolumeManager.replace(vdiVolumeManager)

                        renderer?.vdiStreaming = true

                        if (firstVDI) {
                            val volumeDimensions3i = Vector3f(volume.getDimensions().x.toFloat(), volume.getDimensions().y.toFloat(), volume.getDimensions().z.toFloat())
                            val model = volume.spatial().world
                            renderer?.streamVDI("tcp://0.0.0.0:6655", cam, volumeDimensions3i, model, context)
                            firstVDI = false
                        }

                        currentlyVolumeRendering = !currentlyVolumeRendering
                    }
                }
            }
        }
    }

    fun renderVolume(volume: Volume) {
        settings.set("VideoEncoder.StreamVideo", true)
        settings.set("VideoEncoder.StreamingAddress", "rtp://127.0.0.2:5004")
        renderer?.recordMovie()
        thread {
            while (true) {
                val dummyVolume = scene.find("DummyVolume") as? DummyVolume
                val clientCam = scene.find("ClientCamera") as? DetachedHeadCamera
                if (dummyVolume != null && clientCam != null) {
                    volume.transferFunction = dummyVolume.transferFunction
                    volume.maxDisplayRange = dummyVolume.maxDisplayRange
                    volume.minDisplayRange = dummyVolume.minDisplayRange
                    volume.colormap = dummyVolume.colormap
                }
            }
        }
    }
    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ServerApplication().main()
        }
    }
}
