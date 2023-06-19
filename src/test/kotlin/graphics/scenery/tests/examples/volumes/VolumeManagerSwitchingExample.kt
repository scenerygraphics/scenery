package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.volumes.*
import org.joml.Vector3f
import tpietzsch.shadergen.generate.SegmentTemplate
import tpietzsch.shadergen.generate.SegmentType
import java.nio.file.Paths
import kotlin.concurrent.thread

class VolumeManagerSwitchingExample : SceneryBase("Volume Manager Switching Example", 512, 512) {

    val maxSupersegments = System.getProperty("VolumeBenchmark.NumSupersegments")?.toInt()?: 20
    override fun init() {

        var vdi : Boolean = false  // what we are currently rendering
        var updatedVdi : Boolean = false // what the user want to render : the one who is going to be updated by the user


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
        logger.warn("volume: ${volume.getUuid()}")

        //Step 2: create a volume manager for vdi and add the volume to it:
        val vdiVolumeManager = VDIVolumeManager( hub, windowWidth, windowHeight, maxSupersegments, scene).createVDIVolumeManger()
        vdiVolumeManager.add(volume)
        logger.warn("before adding it to hub "+vdiVolumeManager.nodes.first().getUuid().toString())
        logger.warn("vdi volume manager id ${vdiVolumeManager.getUuid()}")


        //Step 3:  save the standard volume manger, the one who was first created with the volume
        val standardVolumeManager : VolumeManager = hub.get<VolumeManager>() as VolumeManager
        logger.warn("standard volume manager id ${standardVolumeManager?.getUuid()}")



        thread {
            while (true) {
                Thread.sleep(4000)
                var current = hub?.get<VolumeManager>()
                logger.warn("current volume manager id for hub ${current?.getUuid()}")
                logger.warn("current volume manager id for volume ${volume?.volumeManager?.getUuid()}")

                vdiVolumeManager.replace(vdiVolumeManager)

                var currentAfter = hub.get<VolumeManager>()
                logger.warn("current volume manager id for hub ${currentAfter?.getUuid()}")
                logger.warn("current volume manager id for volume ${volume.volumeManager.getUuid()}")

                Thread.sleep(4000)

                standardVolumeManager.replace(standardVolumeManager)

                currentAfter = hub.get<VolumeManager>()
                logger.warn("current volume manager id for hub ${currentAfter?.getUuid()}")
                logger.warn("current volume manager id for volume ${volume?.volumeManager?.getUuid()}")
            }
        }




        var i : Int = 0
//        thread {
//            while (true){
//                Thread.sleep(1000)
//                logger.warn("i = ${i}")
//                i++
//                if (i == 10) {
//                    updatedVdi = true
//                }else if (i == 20){
//                    updatedVdi = false
//                }else if (i == 30){
//                    updatedVdi = true
//                }
//                if (!vdi && updatedVdi!=vdi){
//                    val current = hub?.get<VolumeManager>()
//                    if(current != null) {
//                        hub?.remove(current)
//                    }
//
//                    logger.warn("VDI: before adding it to hub"+vdiVolumeManager.children[0].getUuid().toString())
//                    volume.volumeManager = vdiVolumeManager
//                    hub.add(vdiVolumeManager)
//                    logger.warn("VDI: after adding it to hub"+vdiVolumeManager.children[0].getUuid().toString())
//
//                    vdi = true
//                }
//                else if (vdi && updatedVdi!=vdi){
//                    val current = hub?.get<VolumeManager>()
//                    if(current != null) {
//                        hub?.remove(current)
//                    }
//
//                    logger.warn("before adding it to hub"+vdiVolumeManager.children[0].getUuid().toString())
//                    volume.volumeManager = vdiVolumeManager
//                    hub.add(standardVolumeManager as VolumeManager)
//                    logger.warn("afer adding it to hub"+vdiVolumeManager.children[0].getUuid().toString())
//
//                    vdi = false
//                }
//            }
//        }

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VolumeManagerSwitchingExample().main()
        }
    }
}
