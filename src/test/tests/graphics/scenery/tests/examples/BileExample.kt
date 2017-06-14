package graphics.scenery.tests.examples

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import org.junit.Test

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BileExample: SceneryDefaultApplication("Clustered Volume Rendering example") {
    var hmd: TrackedStereoGlasses? = null
    var publishedNodes = ArrayList<Node>()

    override fun init() {
        hmd = TrackedStereoGlasses("DTrack@10.1.2.201", screenConfig = "CAVEExample.yml")
        hub.add(SceneryElement.HMDInput, hmd!!)

        renderer = Renderer.createRenderer(hub, applicationName, scene, 2560, 1600)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            position = GLVector(.0f, -0.4f, 5.0f)
            perspectiveCamera(50.0f, 1.0f*windowWidth, 1.0f*windowHeight)
            active = true

            scene.addChild(this)
        }

        val shell = Box(GLVector(120.0f, 120.0f, 120.0f), insideNormals = true)
        shell.material.doubleSided = true
        shell.material.diffuse = GLVector(0.0f, 0.0f, 0.0f)
        shell.material.specular = GLVector.getNullVector(3)
        shell.material.ambient = GLVector.getNullVector(3)
        scene.addChild(shell)

        val lights = (0..4).map {
            PointLight()
        }

        val tetrahedron = listOf(
            GLVector(1.0f, 0f, -1.0f/Math.sqrt(2.0).toFloat()),
            GLVector(-1.0f,0f,-1.0f/Math.sqrt(2.0).toFloat()),
            GLVector(0.0f,1.0f,1.0f/Math.sqrt(2.0).toFloat()),
            GLVector(0.0f,-1.0f,1.0f/Math.sqrt(2.0).toFloat()))

        tetrahedron.mapIndexed { i, position ->
            lights[i].position = position * 50.0f
            lights[i].emissionColor = GLVector(1.0f, 0.5f,0.3f)//Numerics.randomVectorFromRange(3, 0.2f, 0.8f)
            lights[i].intensity = 200.2f
            lights[i].linear = 0.0f
            lights[i].quadratic = 0.7f
            scene.addChild(lights[i])
        }

        val bile = Mesh()
        bile.readFrom("M:/meshes/adult_mouse_bile_canaliculi_network_2.stl")
        bile.scale = GLVector(0.1f, 0.1f, 0.1f)
        bile.position = GLVector(-600.0f, -800.0f, -20.0f)
        bile.material.diffuse = GLVector(0.8f, 0.5f, 0.5f)
        bile.material.specular = GLVector(1.0f, 1.0f, 1.0f)
        bile.material.specularExponent = 0.5f
        scene.addChild(bile)


        publishedNodes.add(cam)
        publishedNodes.add(bile)
        publishedNodes.add(shell)

        val publisher = hub.get<NodePublisher>(SceneryElement.NodePublisher)
        val subscriber = hub.get<NodeSubscriber>(SceneryElement.NodeSubscriber)

        publishedNodes.forEachIndexed { index, node ->
            publisher?.nodes?.put(13337 + index, node)

            subscriber?.nodes?.put(13337 + index, node)
        }
    }

    @Test override fun main() {
        super.main()
    }
}
