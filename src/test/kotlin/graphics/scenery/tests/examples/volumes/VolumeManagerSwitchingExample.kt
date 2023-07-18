package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.*
import org.joml.Vector3f
import java.nio.file.Paths
import kotlin.concurrent.thread

class VolumeManagerSwitchingExample : SceneryBase("Volume Manager Switching Example", 512, 512) {

    val maxSupersegments = System.getProperty("VolumeBenchmark.NumSupersegments")?.toInt()?: 20
    override fun init() {

        //Step 1:  First create a volume, camera , renderer ...
        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.5f, 5.0f)
            }
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            scene.addChild(this)
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
        val vdiVolumeManager = VDIVolumeManager( hub, windowWidth, windowHeight, maxSupersegments, scene).createVDIVolumeManger()
        vdiVolumeManager.add(volume)

        //Step 3:  save the standard volume manger, the one who was first created with the volume
        val standardVolumeManager : VolumeManager = hub.get<VolumeManager>() as VolumeManager

        //Step 4: switch between different volume managers
        thread {
            while (true) {
                Thread.sleep(4000)
                vdiVolumeManager.replace(vdiVolumeManager)
                Thread.sleep(4000)
                standardVolumeManager.replace(standardVolumeManager)
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VolumeManagerSwitchingExample().main()
        }
    }
}
