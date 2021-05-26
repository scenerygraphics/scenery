package graphics.scenery.tests.examples.basic

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import org.joml.Vector3f

class ProteinRollercoasterExample: SceneryBase("RollerCoaster", wantREPL = true, windowWidth = 1280, windowHeight = 720) {
    private val protein = Protein.fromID("2zzm")
    private val ribbon = RibbonDiagram(protein)


    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val rowSize = 20f

        val matFaint = Material()
        matFaint.diffuse  = Vector3f(0.0f, 0.6f, 0.6f)
        matFaint.ambient  = Vector3f(1.0f, 1.0f, 1.0f)
        matFaint.specular = Vector3f(1.0f, 1.0f, 1.0f)
        matFaint.cullingMode = Material.CullingMode.None

        val protein = Protein.fromID("6e60")

        val ribbon = RibbonDiagram(protein)

        /*
        ribbon.children.forEach { subprotein ->
            subprotein.children.forEach{ residue ->
                if(residue is Curve) {
                    residue.frenetFrames.forEachIndexed{ index, frame ->
                        if(index%5 == 0) {
                            val binoArrow = Arrow(frame.binormal.normalize() - Vector3f(0f, 0f, 0f))
                            binoArrow.position = frame.translation
                            binoArrow.edgeWidth = 0.5f
                            binoArrow.material = matFaint
                            val noArrow = Arrow(frame.normal.normalize() - Vector3f(0f, 0f, 0f))
                            noArrow.position = frame.translation
                            noArrow.edgeWidth = 0.5f
                            noArrow.material = matFaint
                            val taArrow = Arrow(frame.tangent.normalize()  - Vector3f(0f, 0f, 0f))
                            taArrow.position = frame.translation
                            taArrow.edgeWidth = 0.5f
                            taArrow.material = matFaint
                            scene.addChild(binoArrow)
                            scene.addChild(noArrow)
                            scene.addChild(taArrow)
                        }
                    }
                }
            }
        }


         */

        //scene.addChild(ribbon)

        val lightbox = Box(Vector3f(500.0f, 500.0f, 500.0f), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material.diffuse = Vector3f(0.1f, 0.1f, 0.1f)
        lightbox.material.roughness = 1.0f
        lightbox.material.metallic = 0.0f
        lightbox.material.cullingMode = Material.CullingMode.None
        scene.addChild(lightbox)
        val lights = (0 until 8).map {
            val l = PointLight(radius = 80.0f)
            l.position = Vector3f(
                Random.randomFromRange(-rowSize/2.0f, rowSize/2.0f),
                Random.randomFromRange(-rowSize/2.0f, rowSize/2.0f),
                Random.randomFromRange(1.0f, 5.0f)
            )
            l.emissionColor = Random.random3DVectorFromRange( 0.2f, 0.8f)
            l.intensity = Random.randomFromRange(0.2f, 0.8f)

            lightbox.addChild(l)
            l
        }

        lights.forEach { lightbox.addChild(it) }

        val stageLight = PointLight(radius = 350.0f)
        stageLight.name = "StageLight"
        stageLight.intensity = 0.5f
        stageLight.position = Vector3f(0.0f, 0.0f, 5.0f)
        scene.addChild(stageLight)

        val cameraLight = PointLight(radius = 5.0f)
        cameraLight.name = "CameraLight"
        cameraLight.emissionColor = Vector3f(1.0f, 1.0f, 0.0f)
        cameraLight.intensity = 0.8f

        val cam: Camera = DetachedHeadCamera()
        cam.position = Vector3f(0.0f, 0.0f, 15.0f)
        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)
        scene.addChild(cam)

        cam.addChild(cameraLight)

        val vec1 = Vector3f(3f, 0f, 3f)
        val vec2 = Vector3f(-4f, 0f, 4f)
        val arrow1 = Arrow(vec1 - Vector3f(0f, 0f, 0f))
        arrow1.edgeWidth = 1f
        arrow1.material = matFaint
        val arrow2 = Arrow(vec2 - Vector3f(0f, 0f, 0f))
        arrow2.edgeWidth = 0.5f
        arrow2.material = matFaint
        cam.rotation.lookAlong(vec1.normalize(), vec2.normalize())
        cam.position = vec1
        scene.addChild(arrow1)
        scene.addChild(arrow2)
    }

    override fun inputSetup() {
        super.inputSetup()
        //inputHandler?.addBehaviour("rollercoaster", Rollercoaster(ribbon) { scene.activeObserver })
        //inputHandler?.addKeyBinding("rollercoaster", "E")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ProteinRollercoasterExample().main()
        }
    }
}


