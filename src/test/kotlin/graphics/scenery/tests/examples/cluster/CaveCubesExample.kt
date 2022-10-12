package graphics.scenery.tests.examples.cluster

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.attribute.material.Material
import graphics.scenery.tests.examples.volumes.IJVolumeInitializer
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class CaveCubesExample: SceneryBase("Bile Canaliculi example", wantREPL = false) {
    var hmd: TrackedStereoGlasses? = null

    override fun init() {
        logger.warn("*** WARNING - EXPERIMENTAL ***")
        logger.warn("This is an experimental example, which might need additional configuration on your computer")
        logger.warn("or might not work at all. You have been warned!")

        hmd = hub.add(TrackedStereoGlasses("fake:", screenConfig = "CAVEExample.yml"))

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, 512, 320))

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            wantsSync = false
            spatial {
                position = Vector3f(.0f, -0.4f, 5.0f)
            }
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        val shell = Box(Vector3f(120.0f, 120.0f, 120.0f), insideNormals = true)
        shell.material {
            cullingMode = Material.CullingMode.Front
            diffuse = Vector3f(0.0f, 0.0f, 0.0f)
            specular = Vector3f(0.0f)
            ambient = Vector3f(0.0f)
        }
        scene.addChild(shell)

        val lights = (0..4).map {
            PointLight(radius = 200.0f)
        }

        val tetrahedron = listOf(
            Vector3f(1.0f, 0f, -1.0f/Math.sqrt(2.0).toFloat()),
            Vector3f(-1.0f,0f,-1.0f/Math.sqrt(2.0).toFloat()),
            Vector3f(0.0f,1.0f,1.0f/Math.sqrt(2.0).toFloat()),
            Vector3f(0.0f,-1.0f,1.0f/Math.sqrt(2.0).toFloat()))

        tetrahedron.mapIndexed { i, position ->
            lights[i].spatial().position = position * 50.0f
            lights[i].emissionColor = Vector3f(1.0f, 0.5f,0.3f)//Random.random3DVectorFromRange(0.2f, 0.8f)
            lights[i].intensity = 20.2f
            scene.addChild(lights[i])
        }

        /*
        val online = IJVolumeInitializer("https://imagej.nih.gov/ij/images/t1-head.zip")
        val choice = online
        val volume = Volume.forNetwork(choice, hub)
        scene.addChild(volume)
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
        volume.spatial {
            position.y += 3f
            scale = scale.times(3f)
            needsUpdate = true
        }*/

        listOf(Vector3f(0f,0f,-5f),
            Vector3f(5f,0f,0f),
            Vector3f(-5f,0f,0f),
            Vector3f(0f,0f,5f))
            .forEach {
                scene.addChild(Box().apply {
                    spatial().position = it
                })
            }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            CaveCubesExample().main()
        }
    }
}
