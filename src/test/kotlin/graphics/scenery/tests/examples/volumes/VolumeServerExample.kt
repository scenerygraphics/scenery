package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.tests.examples.cluster.SimpleNetworkExample
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.volumes.*
import org.joml.Vector3f
import java.nio.file.Paths
import kotlin.concurrent.thread

class VolumeServerExample : SceneryBase ("Volume Server Example", 512, 512) {
    var hmd: TrackedStereoGlasses? = null

    override fun init() {
        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        logger.warn(Settings().get("RemoteCamera",false).toString())
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

        settings.set("VideoEncoder.StreamVideo", true)
        settings.set("VideoEncoder.StreamingAddress", "rtp://127.0.0.2:5004")
        renderer?.recordMovie()

        thread {
            while (true) {
                val dummyVolume = scene.find("DummyVolume") as? DummyVolume
                val clientCam = scene.find("ClientCamera") as? DetachedHeadCamera
                if (dummyVolume != null && clientCam != null ) {
                    volume.transferFunction = dummyVolume.transferFunction
                    volume.maxDisplayRange = dummyVolume.maxDisplayRange
                    volume.minDisplayRange = dummyVolume.minDisplayRange
                }
            }
        }

        thread {
            while(true) {
                volume.spatial {
                    rotation = rotation.rotateY(0.003f)
                }
                Thread.sleep(5)
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VolumeServerExample().main()
        }
    }
}
