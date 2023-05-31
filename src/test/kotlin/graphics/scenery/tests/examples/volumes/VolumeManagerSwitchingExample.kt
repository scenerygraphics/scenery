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

    override fun init() {
        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))


//        Test 1: To test if the id changes only after volume created (line 35 an 34 are equal) or regularly (the lines are diff)
//          also to test if hub can contain more than one VM (line 22,36,50)

//        logger.warn("*************"+hub.getAll().toString())
//        val volume = Volume.fromPathRaw(Paths.get(getDemoFilesPath() + "/volumes/box-iso/"), hub)
//        volume.name = "volume"
//        volume.colormap = Colormap.get("viridis")
//        volume.spatial {
//            position = Vector3f(0.0f, 0.0f, -3.5f)
//            rotation = rotation.rotateXYZ(0.05f, 0.05f, 0.05f)
//            scale = Vector3f(20.0f, 20.0f, 20.0f)
//        }
//        volume.transferFunction = TransferFunction.ramp(0.1f, 0.5f)
//        scene.addChild(volume)
//
//        logger.warn("111111111111111111111111"+ hub.get<VolumeManager>()?.getUuid())
//        logger.warn("*************"+hub.getAll().toString())
//        logger.warn("111111111111111111111111"+ hub.get<VolumeManager>()?.getUuid())
//
//        val volume1 = Volume.fromPathRaw(Paths.get(getDemoFilesPath() + "/volumes/box-iso/"), hub)
//        volume1.name = "volume"
//        volume1.colormap = Colormap.get("viridis")
//        volume1.spatial {
//            position = Vector3f(0.0f, 0.0f, -3.5f)
//            rotation = rotation.rotateXYZ(0.05f, 0.05f, 0.05f)
//            scale = Vector3f(20.0f, 20.0f, 20.0f)
//        }
//        volume1.transferFunction = TransferFunction.ramp(0.1f, 0.5f)
//        scene.addChild(volume1)
//
//        logger.warn("22222222222222222222222222"+hub.get<VolumeManager>()?.getUuid())
//        logger.warn("*************"+hub.getAll().toString())



//            Test 2: create another vm with init function
//                     check if current vm can be removed
//                    can hub contains more than one volume manager? (line 97)
//                    can we create our own VM? (line 81 -> 94)
//                    check if vm created is the same in hub (line 95 and 96 )
//
//        Process: create a volume and log the current hub volume manager then create new VM manager and add it to hub
//        and then log the current hub,s volume managers
//
//
//        val volume = Volume.fromPathRaw(Paths.get(getDemoFilesPath() + "/volumes/box-iso/"), hub)
//        volume.name = "volume"
//        volume.colormap = Colormap.get("viridis")
//        volume.spatial {
//            position = Vector3f(0.0f, 0.0f, -3.5f)
//            rotation = rotation.rotateXYZ(0.05f, 0.05f, 0.05f)
//            scale = Vector3f(20.0f, 20.0f, 20.0f)
//        }
//        volume.transferFunction = TransferFunction.ramp(0.1f, 0.5f)
//        scene.addChild(volume)
//
//        logger.warn("111111111111111111111111"+ hub.get<VolumeManager>()?.getUuid())
//        logger.warn("*************"+hub.getAll().toString())
//
//
//
//
//        val vm1 = VolumeManager(
//                hub, useCompute = true, null, null
//            )
//
//        logger.warn("33333333333333333"+vm1.getUuid().toString())
//        hub.add(vm1)
//
////        check if adding vm1 remove the original one
//
//        logger.warn("33333333333333333"+vm1.getUuid().toString())
//        logger.warn("33333333333333333"+ hub.get<VolumeManager>()?.getUuid())
//        logger.warn("*************"+hub.getAll().toString())
//
////        check if current has been removed
//        val current = hub?.get<VolumeManager>()
//        if(current != null) {
//            hub?.remove(current)
//        }
//        logger.warn("22222222222222222222"+ hub.get<VolumeManager>())
//        logger.warn("*************"+hub.getAll().toString())




//          Test 3: create a new volume manager with replace
//
//        Process: create a volume and log the current hub volume manager then create a vm instance to
//        take the curent one (does it really take it id check: line 124)
//

//        val volume = Volume.fromPathRaw(Paths.get(getDemoFilesPath() + "/volumes/box-iso/"), hub)
//        volume.name = "volume"
//        volume.colormap = Colormap.get("viridis")
//        volume.spatial {
//            position = Vector3f(0.0f, 0.0f, -3.5f)
//            rotation = rotation.rotateXYZ(0.05f, 0.05f, 0.05f)
//            scale = Vector3f(20.0f, 20.0f, 20.0f)
//        }
//        volume.transferFunction = TransferFunction.ramp(0.1f, 0.5f)
//        scene.addChild(volume)
//
//        val vm1 =  hub.get<VolumeManager>();
//
//        logger.warn("*************"+hub.getAll().toString())
//
//        logger.warn("111111111111111111111111"+ hub.get<VolumeManager>()?.getUuid())
//        logger.warn("111111111111111111111111"+ vm1?.getUuid())
//        var  bol = hub.get<VolumeManager>()==vm1
//        logger.warn("11111111111111111111  $bol")
//
////        close volume manager and check if the id is the same as the old one or no
//
//        vm1?.close()
//
//        logger.warn("*************"+hub.getAll().toString())
//
//        logger.warn("2222222222222222222"+ hub.get<VolumeManager>()?.getUuid())
//        logger.warn("2222222222222222222222"+ vm1?.getUuid())
//        bol = hub.get<VolumeManager>()?.getUuid()==vm1?.getUuid()
//        logger.warn("2222222222222222222  $bol")


//          Test 4: Try and create a loop for switching managers

//        First create a volume (to create a VM) or just create a VM


//        var vdi : Boolean = false  // what we are currently rendering
//        var updatedVdi : Boolean = false // what the user want to render : the one who is going to be updated by the user
//
//        val volume = Volume.fromPathRaw(Paths.get(getDemoFilesPath() + "/volumes/box-iso/"), hub)
//        volume.name = "volume"
//        volume.colormap = Colormap.get("viridis")
//        volume.spatial {
//            position = Vector3f(0.0f, 0.0f, -3.5f)
//            rotation = rotation.rotateXYZ(0.05f, 0.05f, 0.05f)
//            scale = Vector3f(20.0f, 20.0f, 20.0f)
//        }
//        volume.transferFunction = TransferFunction.ramp(0.1f, 0.5f)
//        scene.addChild(volume)

//        create a volume manager for vdi:
//
//        val vm1vdi = VolumeManager(
//            hub, useCompute = true, null, null
//        )

//        In we should keep the volume manager and not destroy and create new one each time
//
//        val currentStandardVM : VolumeManager? = hub.get<VolumeManager>()
//        var currentVDIVm = vm1vdi
//
//        thread {
//            while (true){
//                if (!vdi && updatedVdi!=vdi){
//                    val current = hub?.get<VolumeManager>()
//                    if(current != null) {
//                        hub?.remove(current)
//                    }
//                    hub.add(currentVDIVm)
//                    vdi = true
//                }
//                else if (vdi && updatedVdi!=vdi){
//                    val current = hub?.get<VolumeManager>()
//                    if(current != null) {
//                        hub?.remove(current)
//                    }
//                    hub.add(currentStandardVM as VolumeManager)
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
