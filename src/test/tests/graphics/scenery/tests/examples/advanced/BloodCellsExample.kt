package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BloodCellsExample : SceneryBase("BloodCellsExample", windowWidth = 1280, windowHeight = 720) {
    val leucocyteCount = 500
    val lightCount = 20
    val positionRange = 250.0f

    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

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

        val lights = (0 until lightCount).map { PointLight(radius = 500.0f) }

        lights.map {
            it.position = Random.randomVectorFromRange(3, -positionRange/2, positionRange/2)
            it.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            it.intensity = 1.5f

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
        erythrocyte.instancedProperties.put("ModelMatrix", { erythrocyte.model })
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
        leucocyte.instancedProperties.put("ModelMatrix", { leucocyte.model })
        scene.addChild(leucocyte)

        val container = Node("Cell container")

        val leucocytes = (0 until leucocyteCount)
            .map {
                val v = Mesh()
                v.name = "leucocyte_$it"
                v.instancedProperties.put("ModelMatrix", { v.world})
                v
            }
            .map {
                val scale = Random.randomFromRange(3.0f, 4.0f)

                it.material = leucocyte.material
                it.scale = GLVector(scale, scale, scale)
                it.children.forEach { ch -> ch.material = it.material }
                it.rotation.setFromEuler(
                    Random.randomFromRange(0.01f, 0.9f),
                    Random.randomFromRange(0.01f, 0.9f),
                    Random.randomFromRange(0.01f, 0.9f)
                )

                it.position = Random.randomVectorFromRange(3, -positionRange, positionRange)
                it.parent = container
                leucocyte.instances.add(it)

                it
            }

        // erythrocytes make up about 40% of human blood, while leucocytes make up about 1%
        val erythrocytes = (0 until leucocyteCount*40)
            .map {
                val v = Mesh()
                v.name = "erythrocyte_$it"
                v.instancedProperties.put("ModelMatrix", { v.world })

                v
            }
            .map {
                val scale = Random.randomFromRange(0.5f, 1.2f)

                it.material = erythrocyte.material
                it.scale = GLVector(scale, scale, scale)
                it.children.forEach { ch -> ch.material = it.material }
                it.rotation.setFromEuler(
                    Random.randomFromRange(0.01f, 0.9f),
                    Random.randomFromRange(0.01f, 0.9f),
                    Random.randomFromRange(0.01f, 0.9f)
                )

                it.position = Random.randomVectorFromRange(3, -positionRange, positionRange)
                it.parent = container
                erythrocyte.instances.add(it)

                it
            }

        scene.addChild(container)

        fun Node.hoverAndTumble(magnitude: Float, id: Int) {
            val axis = GLVector(Math.sin(0.1 * id).toFloat(), -Math.cos(0.1 * id).toFloat(), sin(1.0f*id)*cos(1.0f*id)).normalized
            this.rotation.rotateByAngleNormalAxis(magnitude, axis.x(), axis.y(), axis.z())
            this.rotation.rotateByAngleY(-1.0f * magnitude)
        }

        thread {
            while(true) {
                erythrocytes.mapIndexed { id, erythrocyte -> erythrocyte.hoverAndTumble(Random.randomFromRange(0.001f, 0.01f), id) }
                leucocytes.mapIndexed { id, leucocyte -> leucocyte.hoverAndTumble(0.001f, id) }

                container.position = container.position - GLVector(0.01f, 0.01f, 0.01f)

                container.updateWorld(true)

                Thread.sleep(5)
                ticks++
            }
        }
    }

    @Test override fun main() {
        super.main()
    }
}
