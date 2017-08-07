package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import org.junit.Test

/**
 * Simple example to demonstrate the drawing of a 3D point cloud.
 *
 * This example will draw a point cloud while 3 lights
 * circle around the scene.
 *
 * @author Kyle Harrington <kharrington@uidaho.edu>
 */
class PointCloudExample : SceneryBase("PointCloudExample") {
    protected var lineAnimating = true

    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val hull = Box(GLVector(50.0f, 50.0f, 50.0f), insideNormals = true)
        val hullmaterial = Material()
        hullmaterial.diffuse = GLVector(0.2f, 0.2f, 0.2f)
        hullmaterial.doubleSided = true
        hull.material = hullmaterial
        scene.addChild(hull)


        val colors = arrayOf(
            GLVector(1.0f, 0.0f, 0.0f),
            GLVector(0.0f, 1.0f, 0.0f),
            GLVector(0.0f, 0.0f, 1.0f)
        )

        val lights = (0..2).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i, 2.0f * i, 2.0f * i)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 5000.2f*(i+1)
            scene.addChild(light)
        }

        val cam: Camera = DetachedHeadCamera()
        cam.position = GLVector(0.0f, 0.0f, 15.0f)
        cam.perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
        cam.active = true

        scene.addChild(cam)

        val pcmaterial = Material()
        pcmaterial.ambient = GLVector(1.0f, 1.0f, 1.0f)
        pcmaterial.diffuse = GLVector(1.0f, 1.0f, 0.0f)
        pcmaterial.specular = GLVector(1.0f, 1.0f, 1.0f)
        pcmaterial.doubleSided = true

        val pointCloud = PointCloud(radius=0.025f)
        with(pointCloud) {
            readFromOBJ( TexturedCubeExample::class.java.getResource("models/sphere.obj").file, useMTL = false)
            position = GLVector(0.0f, 0.0f, 0.0f)
            name = "Sphere Mesh"
            material = pcmaterial
            setupPointCloud()

            scene.addChild(this)
        }

    }

    override fun inputSetup() {
        setupCameraModeSwitching(keybinding = "C")
    }

    @Test override fun main() {
        super.main()
    }
}
