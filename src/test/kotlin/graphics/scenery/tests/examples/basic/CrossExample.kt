package graphics.scenery.tests.examples.basic

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.primitives.Cylinder
import org.joml.Vector3f

/**
 * Just a quick example of a CatmullRomSpline with a triangle as a baseShape.
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */
class CrossExample: SceneryBase("CrossExample", windowWidth = 1280, windowHeight = 720) {

    override fun init() {

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val rowSize = 10f

        val sphere = Icosphere(0.3f, 4)
        sphere.material {
            diffuse = Vector3f(1f, 0f, 0f)
        }
        scene.addChild(sphere)


        val cylinder1 = Cylinder(0.02f, 2f, 4)
        cylinder1.spatial {
            orientBetweenPoints(Vector3f(-0.3f, -0f,0f), Vector3f(0f, 0f, 0f))
            position = Vector3f(-1f, 0f, 0f)
        }
        scene.addChild(cylinder1)

        val cylinder2 = Cylinder(0.02f, 2f, 4)
        cylinder2.spatial {
            orientBetweenPoints(Vector3f(0f, -0.3f,0f), Vector3f(0f, 0f, 0f))
            position = Vector3f(0f, -1f, 0f)
        }
        scene.addChild(cylinder2)

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
                Random.randomFromRange(1.0f, 5.0f))
            }
            l.emissionColor = Random.random3DVectorFromRange(0.2f, 0.8f)
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
            CrossExample().main()
        }
    }
}
