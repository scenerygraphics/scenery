package graphics.scenery.tests.examples.basic

import org.joml.*
import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.behaviours.Ruler
import graphics.scenery.flythroughs.Flythrough
import graphics.scenery.geometry.CatmullRomSpline
import graphics.scenery.numerics.Random

class FlythroughExample: SceneryBase("FlythroughExample", windowWidth = 1280, windowHeight = 720) {
    val spline = CatmullRomSpline(listOf(Vector3f(-5f, 0f, 0f), Vector3f(-4.5f, 0.5f, 0.4f), Vector3f(-4f, 1f, 0.8f), Vector3f(-3.5f, 1.5f, 1.2f),
        Vector3f(-3f, 2f, 1.6f), Vector3f(-2.5f, 2.5f, 2f), Vector3f(-2f, 3f, 2.4f), Vector3f(-1.5f, 3.5f, 2.8f), Vector3f(-1f, 4f, 3.2f),
        Vector3f(-0.5f, 3.5f, 3.6f), Vector3f(0f, 4f, 4f)), n = 25)
    val box = Box(sizes = Vector3f(1f, 1f, 1f))

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val rowSize = 10f



        val lightbox = Box(Vector3f(25.0f, 25.0f, 25.0f), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material {
            diffuse = Vector3f(0.1f, 0.1f, 0.1f)
            roughness = 1.0f
            metallic = 0.0f
            cullingMode = Material.CullingMode.None
        }
        scene.addChild(lightbox)
        val lights = (0 until 8).map {
            val l = PointLight(radius = 20.0f)
            l.spatial {
                position = Vector3f(
                    Random.randomFromRange(-rowSize / 2.0f, rowSize / 2.0f),
                    Random.randomFromRange(-rowSize / 2.0f, rowSize / 2.0f),
                    Random.randomFromRange(1.0f, 5.0f)
                )
            }
            l.emissionColor = Random.random3DVectorFromRange( 0.2f, 0.8f)
            l.intensity = Random.randomFromRange(0.2f, 0.8f)

            lightbox.addChild(l)
            l
        }
        lights.forEach { scene.addChild(it) }

        val stageLight = PointLight(radius = 10.0f)
        stageLight.name = "StageLight"
        stageLight.intensity = 0.5f
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
            position = Vector3f(0.0f, 0.0f, 10.0f)
        }
        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)
        scene.addChild(cam)

        cam.addChild(cameraLight)
        scene.addChild(box)
    }

    override fun inputSetup() {
        val flythrough = Flythrough({scene.activeObserver}, box, spline)
        super.inputSetup()
        inputHandler?.addBehaviour("fly", flythrough)
        inputHandler?.addKeyBinding("fly", "R")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            FlythroughExample().main()
        }
    }
}
