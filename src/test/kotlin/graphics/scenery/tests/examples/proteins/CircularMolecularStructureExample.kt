package graphics.scenery.tests.examples.proteins

import org.joml.*
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.attribute.material.Material
import graphics.scenery.proteins.chemistry.*

/**
 *
 *
 * @author Justin Buerger <burger@mpi-cbg.de>
 */
class CircularMolecularStructureExample: SceneryBase("RainbowRibbon", windowWidth = 1280, windowHeight = 720) {
    override fun init() {

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val rowSize = 20f
        val circularBondTree = BondTreeCycle("F", listOf(listOf(BondTree("C", 1, null),
            BondTree("C", 1, null),
            BondTree("C", 1, null), BondTree("C", 1, null),
            BondTree("C", 1, null), BondTree("C", 1, null)),
            listOf(BondTree("O", 2, null))), 1, null)

        val threeDStructure = CircularMolecularStructure(circularBondTree, kotlin.math.PI.toFloat()/2f, Matrix3f(1f, 0f, 0f,
                                                                                                    0f, 1f, 0f,
                                                                                                    0f, 0f, 1f),
        1f, Vector3f()
        )

        scene.addChild(threeDStructure)


        val lightbox = Box(Vector3f(500.0f, 500.0f, 500.0f), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material {
            diffuse = Vector3f(0.1f, 0.1f, 0.1f)
            roughness = 1.0f
            metallic = 0.0f
            cullingMode = Material.CullingMode.None
        }
        scene.addChild(lightbox)
        val lights = (0 until 8).map {
            val l = PointLight(radius = 80.0f)
            l.spatial {
                position = Vector3f(
                    Random.randomFromRange(-rowSize/2.0f, rowSize/2.0f),
                    Random.randomFromRange(-rowSize/2.0f, rowSize/2.0f),
                    Random.randomFromRange(1.0f, 5.0f)
                )
            }
            l.emissionColor = Random.random3DVectorFromRange( 0.2f, 0.8f)
            l.intensity = Random.randomFromRange(0.2f, 0.8f)

            lightbox.addChild(l)
            l
        }

        lights.forEach { lightbox.addChild(it) }

        val stageLight = PointLight(radius = 350.0f)
        stageLight.name = "StageLight"
        stageLight.intensity = 15f
        stageLight.spatial {
            position = Vector3f(0.0f, 0.0f, 5.0f)
        }
        scene.addChild(stageLight)

        val cameraLight = PointLight(radius = 5.0f)
        cameraLight.name = "CameraLight"
        cameraLight.emissionColor = Vector3f(1.0f, 1.0f, 0.0f)
        cameraLight.intensity = 0.8f

        val cam: Camera = DetachedHeadCamera()
        cam.spatial {
            position = Vector3f(0.0f, 0.0f, 15.0f)
        }
        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)
        scene.addChild(cam)

        cam.addChild(cameraLight)
    }

    override fun inputSetup() {
        super.inputSetup()
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            CircularMolecularStructureExample().main()
        }
    }
}

