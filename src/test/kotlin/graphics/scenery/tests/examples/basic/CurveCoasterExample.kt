package graphics.scenery.tests.examples.basic

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.flythroughs.CurveCoaster
import graphics.scenery.geometry.CatmullRomSpline
import graphics.scenery.geometry.Curve
import graphics.scenery.numerics.Random
import org.joml.Vector3f

class CurveCoasterExample: SceneryBase("CurveRollerCoaster", wantREPL = true, windowWidth = 1280, windowHeight = 720) {


    override fun init() {

        val points = ArrayList<Vector3f>()
        points.add(Vector3f(-8f, -9f, -9f))
        points.add(Vector3f(-7f, -5f, -7f))
        points.add(Vector3f(-5f, -5f, -5f))
        points.add(Vector3f(-4f, -2f, -3f))
        points.add(Vector3f(-2f, -3f, -4f))
        points.add(Vector3f(-1f, -1f, -1f))
        points.add(Vector3f(0f, 0f, 0f))
        points.add(Vector3f(2f, 1f, 0f))

        fun triangle(splineVerticesCount: Int): ArrayList<ArrayList<Vector3f>> {
            val shapeList = ArrayList<ArrayList<Vector3f>>(splineVerticesCount)
            for (i in 0 until splineVerticesCount) {
                val list = ArrayList<Vector3f>()
                list.add(Vector3f(0.03f, 0.03f, 0f))
                list.add(Vector3f(0.03f, -0.03f, 0f))
                list.add(Vector3f(-0.03f, -0.03f, 0f))
                shapeList.add(list)
            }
            return shapeList
        }

        val catmullRom = CatmullRomSpline(points)
        val splineSize = catmullRom.splinePoints().size
        val geo = Curve(catmullRom) { triangle(splineSize) }
        geo.name = "curve"

        scene.addChild(geo)

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val rowSize = 20f


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
                    Random.randomFromRange(1.0f, 5.0f))
                }
                l.emissionColor = Random.random3DVectorFromRange( 0.2f, 0.8f)
                l.intensity = Random.randomFromRange(0.2f, 0.8f)

                lightbox.addChild(l)
                l
            }

            lights.forEach { lightbox.addChild(it) }

            val stageLight = PointLight(radius = 500.0f)
            stageLight.name = "StageLight"
            stageLight.intensity = 0.5f
            stageLight.spatial { position = Vector3f(0.0f, 0.0f, 5.0f) }
            scene.addChild(stageLight)

            val cameraLight = PointLight(radius = 50.0f)
            cameraLight.name = "CameraLight"
            cameraLight.emissionColor = Vector3f(1.0f, 1.0f, 0.0f)
            cameraLight.intensity = 2.0f

            val cam: Camera = DetachedHeadCamera()
            cam.name = "camera"
            cam.spatial {
                position = Vector3f(0.0f, 0.0f, 15.0f)
            }
            cam.perspectiveCamera(50.0f, windowWidth, windowHeight)


            scene.addChild(cam)

            cam.addChild(cameraLight)
    }

    override fun inputSetup() {
        super.inputSetup()
        val curve = scene.children.filter{it.name == "curve"}[0]
        if(curve is Curve) {
            inputHandler?.addBehaviour(
                "rollercoaster",
                CurveCoaster(curve, { scene.activeObserver })
            )
            inputHandler?.addKeyBinding("rollercoaster", "E")

        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            CurveCoasterExample().main()
        }
    }
}
