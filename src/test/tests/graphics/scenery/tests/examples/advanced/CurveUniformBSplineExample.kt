package graphics.scenery.tests.examples.advanced

import graphics.scenery.UniformBSpline
import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.Curve

import org.junit.Test

/**
 * Just a quick example of the UniformBSpline with a triangle as a baseShape.
 *
 * @author Justin Bürger
 */
class CurveUniformBSplineExample: SceneryBase("CurveUniformBSplineExample", windowWidth = 1280, windowHeight = 720) {

    override fun init() {

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val rowSize = 10f

        val points = ArrayList<GLVector>()
        points.add(GLVector(-8f, -9f, -9f))
        points.add(GLVector(-7f, -5f, -7f))
        points.add(GLVector(-5f, -5f, -5f))
        points.add(GLVector(-4f, -2f, -3f))
        points.add(GLVector(-2f, -3f, -4f))
        points.add(GLVector(-1f, -1f, -1f))
        points.add(GLVector(0f, 0f, 0f))
        points.add(GLVector(2f, 1f, 0f))

        fun triangle(): ArrayList<GLVector> {
            val list = ArrayList<GLVector>()
            list.add(GLVector(0.3f, 0.3f, 0f))
            list.add(GLVector(0.3f, -0.3f, 0f))
            list.add(GLVector(-0.3f, -0.3f, 0f))
            return list
        }

        val bSpline = UniformBSpline(points)
        val geo = Curve(bSpline) { triangle() }

        scene.addChild(geo)

        val lightbox = Box(GLVector(25.0f, 25.0f, 25.0f), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material.diffuse = GLVector(0.1f, 0.1f, 0.1f)
        lightbox.material.roughness = 1.0f
        lightbox.material.metallic = 0.0f
        lightbox.material.cullingMode = Material.CullingMode.None
        scene.addChild(lightbox)
        val lights = (0 until 8).map {
            val l = PointLight(radius = 20.0f)
            l.position = GLVector(
                Random.randomFromRange(-rowSize / 2.0f, rowSize / 2.0f),
                Random.randomFromRange(-rowSize / 2.0f, rowSize / 2.0f),
                Random.randomFromRange(1.0f, 5.0f)
            )
            l.emissionColor = Random.randomVectorFromRange(3, 0.2f, 0.8f)
            l.intensity = Random.randomFromRange(0.2f, 0.8f)

            lightbox.addChild(l)
            l
        }

        val stageLight = PointLight(radius = 10.0f)
        stageLight.name = "StageLight"
        stageLight.intensity = 0.5f
        stageLight.position = GLVector(0.0f, 0.0f, 5.0f)
        scene.addChild(stageLight)

        val cameraLight = PointLight(radius = 5.0f)
        cameraLight.name = "CameraLight"
        cameraLight.emissionColor = GLVector(1.0f, 1.0f, 0.0f)
        cameraLight.intensity = 0.8f

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.2f, 12.0f)
            perspectiveCamera(25.0f, windowWidth.toFloat(), windowHeight.toFloat())
            active = true

            scene.addChild(this)
        }

        cam.addChild(cameraLight)

    }

    override fun inputSetup() {
        super.inputSetup()
        setupCameraModeSwitching()
    }

    @Test
    override fun main() {
        super.main()
    }
}

