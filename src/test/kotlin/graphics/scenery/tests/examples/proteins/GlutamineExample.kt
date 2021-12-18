package graphics.scenery.tests.examples.proteins

import org.joml.*
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.attribute.material.Material
import graphics.scenery.proteins.chemistry.AminoAcidBondTreeMap
import graphics.scenery.proteins.chemistry.BondTree
import graphics.scenery.proteins.chemistry.ThreeDimensionalMolecularStructure

/**
 *
 *
 * @author Justin Buerger <burger@mpi-cbg.de>
 */
class GlutamineExample: SceneryBase("RainbowRibbon", windowWidth = 1280, windowHeight = 720) {
    override fun init() {

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val rowSize = 20f
        val glutamineBondTree = AminoAcidBondTreeMap().aminoMap["ALA"]
        if(glutamineBondTree != null) {
            //val glutamine = ThreeDimensionalMolecularStructure(glutamineBondTree)
            //scene.addChild(glutamine)
        }

        val ethanol = BondTree("C", 0, null)
        ethanol.addhydrogen(3)
        val c = BondTree("C", 1, ethanol)
        val o1 = BondTree("O", 2, c)
        val o2 = BondTree("O", 1, c)
        o2.addhydrogen(1)
        c.addBoundMolecule(o1)
        c.addBoundMolecule(o2)
        ethanol.addBoundMolecule(c)

        val methane = BondTree("C", 0, null)
        methane.addhydrogen(4)

        scene.addChild(ThreeDimensionalMolecularStructure(ethanol))


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
            GlutamineExample().main()
        }
    }
}
