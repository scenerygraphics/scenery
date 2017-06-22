package graphics.scenery.tests.examples.cluster

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import graphics.scenery.volumes.Volume
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import java.io.File
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ClusterExample: SceneryDefaultApplication("Clustered Volume Rendering example") {
    var hmd: TrackedStereoGlasses? = null
    var publishedNodes = ArrayList<Node>()

    override fun init() {
        hmd = TrackedStereoGlasses("DTrack@10.1.2.201", screenConfig = "CAVEExample.yml")
        hub.add(SceneryElement.HMDInput, hmd!!)

        renderer = Renderer.createRenderer(hub, applicationName, scene, 2560, 1600)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            //position = GLVector(.4f, .4f, 1.4f)
            position = GLVector(.0f, -0.4f, 2.0f)
            perspectiveCamera(50.0f, 1.0f*windowWidth, 1.0f*windowHeight)
            active = true

            scene.addChild(this)
        }

//        val bileMesh = Mesh()
//        bileMesh.readFromRaw("Z:/data/models-inauguration/celegans_epithelium.stl", useMaterial = false)
//        bileMesh.scale = GLVector(0.01f, 0.01f, 0.01f)
//        bileMesh.visible = false
//        scene.addChild(bileMesh)
//
//        val vasculature = Mesh()
//        vasculature.readFromRaw("Z:/data/models-inauguration/Drerio.stl", useMaterial = false)
//        vasculature.scale = GLVector(0.1f, 0.1f, 0.1f)
//        bileMesh.visible = false
//        scene.addChild(vasculature)
        val box = Box(GLVector(2.0f, 2.0f, 2.0f))
        box.material.diffuse = GLVector(1.0f, 0.0f, 0.0f)
        //scene.addChild(box)

        val shell = Box(GLVector(120.0f, 120.0f, 120.0f), insideNormals = true)
        shell.material.doubleSided = true
        shell.material.diffuse = GLVector(0.0f, 0.0f, 0.0f)
        shell.material.specular = GLVector.getNullVector(3)
        shell.material.ambient = GLVector.getNullVector(3)
        scene.addChild(shell)

        val volume = Volume()

        with(volume) {
            volume.visible = true
            scene.addChild(this)
        }

        val lights = (0..3).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(4.0f * i, 4.0f * i, 4.0f)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 200.2f*(i+1)
            light.linear = 1.8f
            light.quadratic = 0.7f
            scene.addChild(light)
        }

        val folder = File("M:/CAVE_DATA/histones-isonet/stacks/default/")
        //val folder = File("M:/CAVE_DATA/box_test/aniso")


        //val folder = File("M:/CAVE_DATA/droso-royer-autopilot-transposed/")
//        val folder = File("M:/CAVE_DATA/droso-royer-long/stacks/default/")
//        val folder = File("M:/TestData/")
        val files = folder.listFiles()
        val volumes = files.filter { it.isFile && it.name.endsWith("raw") }.map { it.absolutePath }.sorted()
//
        volumes.forEach { logger.info("Volume: $it")}
//
        var currentVolume = 0
        fun nextVolume(): String {
            val v = volumes[currentVolume % volumes.size]
            currentVolume++

            return v
        }

        publishedNodes.add(cam)
//        publishedNodes.add(bileMesh)
//        publishedNodes.add(vasculature)
        publishedNodes.add(box)
        publishedNodes.add(volume)

        val publisher = hub.get<NodePublisher>(SceneryElement.NodePublisher)
        val subscriber = hub.get<NodeSubscriber>(SceneryElement.NodeSubscriber)

        publishedNodes.forEachIndexed { index, node ->
            publisher?.nodes?.put(13337 + index, node)

            subscriber?.nodes?.put(13337 + index, node)
        }

        val min_delay = 600
        //val min_delay = 0

        if(publisher != null) {
            thread {
                while (!scene.initialized) {
                    Thread.sleep(1000)
                }


                //fvolume.rotation.rotateByAngleY(1.57f)
                //volume.rotation.rotateByAngleZ(1.57f)
//                volume.rotation.rotateByAngleX(.8f)


//                volume.needsUpdate = true


                while (true) {
                    val start = System.currentTimeMillis()

                    logger.info("Reading next volume...")
                    volume.currentVolume = nextVolume()

                    val time_to_read  = System.currentTimeMillis()-start

                    logger.info("took ${time_to_read} ms")
                    Thread.sleep(Math.max(0,min_delay-time_to_read))

                }
            }


            thread {
                while (true) {
//                    volume.rotation.rotateByAngleX(0.001f)
//                    volume.needsUpdate = true

                    Thread.sleep(20)
                }
            }
        }

    }

    override fun inputSetup() {
        setupCameraModeSwitching(keybinding = "C")

        val inputHandler = hub.get(SceneryElement.Input) as InputHandler

        val cycleObjects = ClickBehaviour { _, _ ->
            val currentObject = publishedNodes.find { it.visible == true }
            val currentIndex = publishedNodes.indexOf(currentObject)

            publishedNodes.forEach { it.visible = false }
            publishedNodes[(currentIndex + 1) % (publishedNodes.size-1)].run {
                this.visible = true
                logger.info("Now visible: $this")
            }
        }

        inputHandler.addBehaviour("cycle_objects", cycleObjects)
        inputHandler.addKeyBinding("cycle_objects", "N")
    }

    @Test override fun main() {
        super.main()
    }
}
