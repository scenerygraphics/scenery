package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.attribute.material.Material
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import kotlin.concurrent.thread


import graphics.scenery.primitives.ParticleGlyphs

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ParticleGlyphsExample : SceneryBase("ParticleGlyphsExample") {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val hull = Box(Vector3f(15.0f, 15.0f, 15.0f), insideNormals = true)
        hull.material {
            diffuse = Vector3f(0.07f, 0.07f, 0.07f)
            cullingMode = Material.CullingMode.Front
        }
        scene.addChild(hull)

        val box = Box(Vector3f(1.3f, 1.3f, 1.3f))
        box.name = "le box du win"
        box.spatial() {
            position = Vector3f(2.0f, 2.0f, 2.0f)
        }
        box.material {
            textures["diffuse"] = Texture.fromImage(Image.fromResource("textures/helix.png", TexturedCubeExample::class.java))
            metallic = 0.3f
            roughness = 0.9f
        }
        scene.addChild(box)

        val light0 = PointLight(radius = 50.0f)
        light0.spatial().position = Vector3f(0.0f, 0.0f, 0.0f)
        light0.intensity = 20.0f
        light0.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light0)

        val cam: Camera = DetachedHeadCamera()
        cam.spatial {
            position = Vector3f(0.0f, 0.0f, 15.0f)
        }
        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)
        cam.target = Vector3f(0.0f, 0.0f, 0.0f)
        scene.addChild(cam)



        val particlePositions = mutableListOf<Vector3f>()
        val particleProperties = mutableListOf<Vector3f>()
        csvReader().open("Arya41.csv") {
            readAllAsSequence().drop(1).forEach{ row: List<String> ->
                particlePositions.add(Vector3f(row[4].toFloat() - 5, row[5].toFloat() - 5,row[6].toFloat() - 5))
                val mag = Vector3f(row[0].toFloat(), row[1].toFloat(), row[2].toFloat()).length()
                particleProperties.add(Vector3f(mag / 10.0f, row[3].toFloat(), 0.0f))
            }
        }
        val particleGlyphs = ParticleGlyphs(particlePositions, particleProperties, true)
        particleGlyphs.name = "Particles?"
        scene.addChild(particleGlyphs)


        thread {
            while (running) {
                box.spatial {
                    rotation.rotateY(0.01f)
                    needsUpdate = true
                }
                Thread.sleep(50)
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ParticleGlyphsExample().main()
        }
    }
}


