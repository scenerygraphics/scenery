package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.Numerics
import org.junit.Test
import kotlin.math.floor

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class PBLExample: SceneryBase("PBLExample", windowWidth = 1280, windowHeight = 720) {
    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val rowSize = 10f
        val spheres = (0 until 100).map {
            val s = Sphere(0.4f, 80)
            s.position = GLVector(floor(it / rowSize), (it % rowSize.toInt()).toFloat(), 0.0f) - GLVector((rowSize - 1.0f)/2.0f, (rowSize - 1.0f)/2.0f, 0.0f)
            s.material.roughness = (it / rowSize)/rowSize
            s.material.metallic = (it % rowSize.toInt())/rowSize
            s.material.diffuse = GLVector(1.0f, 0.0f, 0.0f)

            s
        }

        spheres.forEach { scene.addChild(it) }

        val lightbox = Box(GLVector(25.0f, 25.0f, 25.0f), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material.diffuse = GLVector(0.1f, 0.1f, 0.1f)
        lightbox.material.roughness = 1.0f
        lightbox.material.metallic = 0.0f
        lightbox.material.doubleSided = true
        lightbox.material.cullingMode = Material.CullingMode.None
        scene.addChild(lightbox)

        val lights = (0 until 8).map {
            val l = PointLight(radius = 20.0f)
            l.position = GLVector(
                Numerics.randomFromRange(-rowSize/2.0f, rowSize/2.0f),
                Numerics.randomFromRange(-rowSize/2.0f, rowSize/2.0f),
                Numerics.randomFromRange(1.0f, 5.0f)
            )
            l.emissionColor = Numerics.randomVectorFromRange(3, 0.8f, 1.0f)
            l.intensity = Numerics.randomFromRange(10.0f, 75.0f)

            lightbox.addChild(l)
            l
        }

        val stageLight = PointLight(radius = 35.0f)
        stageLight.name = "StageLight"
        stageLight.intensity = 100.0f
        stageLight.position = GLVector(0.0f, 0.0f, -5.0f)
        scene.addChild(stageLight)

        val cameraLight = PointLight(radius = 5.0f)
        cameraLight.name = "CameraLight"
        cameraLight.intensity = 25.0f

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.2f, 12.0f)
            perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
            active = true

            scene.addChild(this)
        }

        cam.addChild(cameraLight)
    }

    override fun inputSetup() {
        super.inputSetup()
        setupCameraModeSwitching()
    }

    @Test override fun main() {
        super.main()
    }
}
