package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.utils.forEachIndexedAsync
import graphics.scenery.utils.forEachParallel
import kotlinx.coroutines.delay
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BloodCellsExample : SceneryBase("BloodCellsExample", windowWidth = 1280, windowHeight = 720) {
    val leucocyteCount = 1000
    val lightCount = 20
    val positionRange = 250.0f

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        cam.position = GLVector(0.0f, 20.0f, -20.0f)
        cam.perspectiveCamera(50.0f, 1.0f * windowWidth, 1.0f * windowHeight, 2.0f, 5000.0f)
        cam.active = true

        scene.addChild(cam)

        val hull = Box(GLVector(2*positionRange, 2*positionRange, 2*positionRange), insideNormals = true)
        hull.material.ambient = GLVector(0.0f, 0.0f, 0.0f)
        hull.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        hull.material.specular = GLVector(0.0f, 0.0f, 0.0f)
        hull.material.cullingMode = Material.CullingMode.Front
        hull.name = "hull"

        scene.addChild(hull)

        val lights = (0 until lightCount).map { PointLight(radius = positionRange) }

        lights.map {
            it.position = Random.randomVectorFromRange(3, -positionRange/2, positionRange/2)
            it.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            it.intensity = 0.5f

            scene.addChild(it)
        }

        val erythrocyte = Mesh()
        erythrocyte.readFromOBJ(getDemoFilesPath() + "/erythrocyte_simplified.obj")
        erythrocyte.material = ShaderMaterial.fromFiles("DefaultDeferredInstanced.vert", "DefaultDeferred.frag")
        erythrocyte.material.ambient = GLVector(0.1f, 0.0f, 0.0f)
        erythrocyte.material.diffuse = GLVector(0.9f, 0.0f, 0.02f)
        erythrocyte.material.specular = GLVector(0.05f, 0f, 0f)
        erythrocyte.material.metallic = 0.01f
        erythrocyte.material.roughness = 0.9f
        erythrocyte.name = "Erythrocyte_Master"
        erythrocyte.instancedProperties["ModelMatrix"] = { erythrocyte.model }
        scene.addChild(erythrocyte)

        val leucocyte = Mesh()
        leucocyte.readFromOBJ(getDemoFilesPath() + "/leukocyte_simplified.obj")
        leucocyte.name = "leucocyte_Master"
        leucocyte.material = ShaderMaterial.fromFiles("DefaultDeferredInstanced.vert", "DefaultDeferred.frag")
        leucocyte.material.ambient = GLVector(0.1f, 0.0f, 0.0f)
        leucocyte.material.diffuse = GLVector(0.8f, 0.7f, 0.7f)
        leucocyte.material.specular = GLVector(0.05f, 0f, 0f)
        leucocyte.material.metallic = 0.01f
        leucocyte.material.roughness = 0.5f
        leucocyte.instancedProperties["ModelMatrix"] = { leucocyte.model }
        scene.addChild(leucocyte)

        val container = Node("Cell container")

        val leucocytes = (0 until leucocyteCount).map {
            val v = Mesh()
            v.name = "leucocyte_$it"
            v.instancedProperties["ModelMatrix"] = { v.world }
            v.metadata["axis"] = GLVector(sin(0.1 * it).toFloat(), -cos(0.1 * it).toFloat(), sin(1.0f*it)*cos(1.0f*it)).normalized
            v.parent = container

            val scale = Random.randomFromRange(3.0f, 4.0f)
            v.scale = GLVector(scale, scale, scale)
            v.position = Random.randomVectorFromRange(3, -positionRange, positionRange)
            v.rotation.setFromEuler(
                Random.randomFromRange(0.01f, 0.9f),
                Random.randomFromRange(0.01f, 0.9f),
                Random.randomFromRange(0.01f, 0.9f)
            )

            v
        }
        leucocyte.instances.addAll(leucocytes)

        // erythrocytes make up about 40% of human blood, while leucocytes make up about 1%
        val erythrocytes = (0 until leucocyteCount*40).map {
            val v = Mesh()
            v.name = "erythrocyte_$it"
            v.instancedProperties["ModelMatrix"] = { v.world }
            v.metadata["axis"] = GLVector(sin(0.1 * it).toFloat(), -cos(0.1 * it).toFloat(), sin(1.0f*it)*cos(1.0f*it)).normalized
            v.parent = container

            val scale = Random.randomFromRange(0.5f, 1.2f)
            v.scale = GLVector(scale, scale, scale)
            v.position = Random.randomVectorFromRange(3, -positionRange, positionRange)
            v.rotation.setFromEuler(
                Random.randomFromRange(0.01f, 0.9f),
                Random.randomFromRange(0.01f, 0.9f),
                Random.randomFromRange(0.01f, 0.9f)
            )

            v
        }
        erythrocyte.instances.addAll(erythrocytes)

        scene.addChild(container)

        fun Node.hoverAndTumble(magnitude: Float) {
            val axis = this.metadata["axis"] as? GLVector ?: return
            this.rotation.rotateByAngleNormalAxis(magnitude, axis.x(), axis.y(), axis.z())
            this.rotation.rotateByAngleY(-1.0f * magnitude)
            this.needsUpdate = true
        }

        thread {
            while(!sceneInitialized()) {
                Thread.sleep(200)
            }

            while(true) {
                erythrocytes.parallelStream().forEach { erythrocyte -> erythrocyte.hoverAndTumble(Random.randomFromRange(0.001f, 0.01f)) }
                leucocytes.parallelStream().forEach { leucocyte -> leucocyte.hoverAndTumble(0.001f) }

                container.position = container.position - GLVector(0.01f, 0.01f, 0.01f)
                container.updateWorld(false)

                Thread.sleep(5)
                ticks++
            }
        }
    }

    @Test override fun main() {
        super.main()
    }
}
