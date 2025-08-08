package graphics.scenery.tests.examples.cluster.old

import graphics.scenery.Mesh
import graphics.scenery.SceneryElement
import graphics.scenery.controls.InputHandler
import graphics.scenery.tests.examples.cluster.CaveBaseScene
import graphics.scenery.utils.lazyLogger
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour

/**
 * Demo reel example to be run on a CAVE system.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class DemoReelExample: CaveBaseScene("Demo Reel") {

    var bileScene = Mesh(name = "bile")
    var histoneScene = Mesh(name = "histone")
    var drosophilaScene = Mesh(name = "drosophila")
    var retinaScene = Mesh(name = "retina")

    val scenes = listOf(bileScene, histoneScene, drosophilaScene, retinaScene)

    override fun init() {
        super.init()

        /*val histoneVolume = Volume.fromPathRaw(
            Paths.get("$driveLetter:/ssd-backup-inauguration/CAVE_DATA/histones-isonet/stacks/default/"),
            hub
        )
        histoneVolume.transferFunction = TransferFunction.ramp(0.1f, 1.0f)
        histoneVolume.colormap = Colormap.get("hot")
        histoneScene.addChild(histoneVolume)
        histoneScene.visible = false
        scene.addChild(histoneScene)

        val drosophilaVolume = Volume.fromPathRaw(
            Paths.get("$driveLetter:/ssd-backup-inauguration/CAVE_DATA/droso-royer-autopilot-transposed/"),
            hub
        )
        drosophilaVolume.spatial {
            rotation.rotateX(1.57f)
        }
        drosophilaVolume.transferFunction = TransferFunction.ramp(0.1f, 1.0f)
        drosophilaVolume.colormap = Colormap.get("hot")
        drosophilaScene.addChild(drosophilaVolume)
        drosophilaScene.visible = false
        scene.addChild(drosophilaScene)

        val retinaVolume = Volume.fromPathRaw(
            Paths.get("$driveLetter:/ssd-backup-inauguration/CAVE_DATA/retina_test2/"),
            hub
        )
        retinaScene.addChild(retinaVolume)
        retinaScene.visible = false
        scene.addChild(retinaScene)
*/
        val bile = Mesh()
        val canaliculi = Mesh()
        canaliculi.readFrom("$driveLetter:/ssd-backup-inauguration/meshes/bile-canaliculi.obj")
        canaliculi.spatial {
            scale = Vector3f(0.1f, 0.1f, 0.1f)
            position = Vector3f(-80.0f, -60.0f, 10.0f)
        }
        canaliculi.material {
            diffuse = Vector3f(0.5f, 0.7f, 0.1f)
        }
        bile.addChild(canaliculi)

        val nuclei = Mesh()
        nuclei.readFrom("$driveLetter:/ssd-backup-inauguration/meshes/bile-nuclei.obj")
        nuclei.spatial {
            scale = Vector3f(0.1f, 0.1f, 0.1f)
            position = Vector3f(-80.0f, -60.0f, 10.0f)
        }
        nuclei.material {
            diffuse = Vector3f(0.8f, 0.8f, 0.8f)
        }
        bile.addChild(nuclei)

        val sinusoidal = Mesh()
        sinusoidal.readFrom("$driveLetter:/ssd-backup-inauguration/meshes/bile-sinus.obj")
        sinusoidal.spatial {
            scale = Vector3f(0.1f, 0.1f, 0.1f)
            position = Vector3f(-80.0f, -60.0f, 10.0f)
        }
        sinusoidal.material {
            ambient = Vector3f(0.1f, 0.0f, 0.0f)
            diffuse = Vector3f(0.4f, 0.0f, 0.02f)
            specular = Vector3f(0.05f, 0f, 0f)
        }
        bile.addChild(sinusoidal)
        bileScene.addChild(bile)
        scene.addChild(bileScene)

        //val publisher = hub.get<NodePublisher>(SceneryElement.NodePublisher)
        //val subscriber = hub.get<NodeSubscriber>(SceneryElement.NodeSubscriber)


    }

    /**
     * Standard input setup, plus additional key bindings to
     * switch scenes.
     */
    override fun inputSetup() {
        super.inputSetup()

        val inputHandler = (hub.get(SceneryElement.Input) as? InputHandler) ?: return

        fun gotoScene(sceneName: String) = ClickBehaviour { _, _ ->
            scenes.filter { it.name == sceneName }.forEach { scene -> scene.runRecursive { it.visible = true } }
            scenes.filter { it.name != sceneName }.forEach { scene -> scene.runRecursive { it.visible = false } }

            scene.findObserver()?.spatial()?.position = Vector3f(0.0f, 0.0f, 3.0f)
        }

        inputHandler.addBehaviour("goto_scene_bile", gotoScene("bile"))
        inputHandler.addBehaviour("goto_scene_histone", gotoScene("histone"))
        inputHandler.addBehaviour("goto_scene_drosophila", gotoScene("drosophila"))
        inputHandler.addBehaviour("goto_scene_retina", gotoScene("retina"))


        inputHandler.addKeyBinding("goto_scene_bile", "shift 1")
        inputHandler.addKeyBinding("goto_scene_histone", "shift 2")
        inputHandler.addKeyBinding("goto_scene_drosophila", "shift 3")
        inputHandler.addKeyBinding("goto_scene_retina", "shift 4")
    }

    companion object {
        private val logger by lazyLogger()
        //var client: AutofabClient? = null
        @JvmStatic
        fun main(args: Array<String>) {
            DemoReelExample().main()
        }
    }
}
