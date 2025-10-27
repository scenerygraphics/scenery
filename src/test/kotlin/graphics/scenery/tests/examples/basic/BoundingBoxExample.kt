package graphics.scenery.tests.examples.basic

import graphics.scenery.BoundingGrid
import graphics.scenery.Box
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.Node
import graphics.scenery.PointLight
import graphics.scenery.SceneryBase
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import org.joml.Vector3f
import kotlin.concurrent.thread
import kotlin.math.sin

/**
 * Demo for testing bounding box intersection in world space between children of nodes.
 *
 * @author Samuel Pantze
 */
class BoundingBoxExample : SceneryBase("BoundingBoxExample") {

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, 1700, 1000))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0f, 0f, 40.0f)
            }
            perspectiveCamera(70.0f, windowWidth, windowHeight, 1.0f, 1000.0f)
            scene.addChild(this)
        }

        val intersectorBox = Box(Vector3f(7f, 4f, 2f))
        scene.addChild(intersectorBox)

        val baseBox = Box(Vector3f(4f, 1.5f, 0.5f))
        scene.addChild(baseBox)

        baseBox.spatial {
            position = Vector3f(-7f, -2f, -2.0f)
            scale = Vector3f(3f)
        }

        var frame = 0
        var prevHit = false

        thread {
            while (true) {
                baseBox.spatial {
                    rotation.rotateXYZ(0.006f, 0.004f, 0.003f)
                    needsUpdate = true
                    scale.mul(
                        sin(frame/140f)/200f+1f,
                        sin(frame/100f)/200f+1f,
                        sin(frame/80f)/200f+1f,
                    )
                }
                intersectorBox.spatial {
                    rotation.rotateXYZ(-0.002f, -0.007f, -0.004f)
                    needsUpdate = true
                    scale.mul(
                        sin(frame/100f+2)/200f+1f,
                        sin(frame/120f)/200f+1f,
                        sin(frame/200f)/200f+1f,
                    )
                }
                baseBox.spatial().updateWorld(true, true)
                intersectorBox.spatial().updateWorld(true, true)

                val hit = baseBox.spatialOrNull()?.intersects(intersectorBox, true) == true

                // If a change in intersections is detected, briefly pause the simulation so the user can investigate
                // whether the intersection happened at the right moment.
                if (hit != prevHit) {
                    Thread.sleep(4000)
                }

                if (hit && !prevHit) {
                    baseBox.material {
                        diffuse = Vector3f(1.0f, 0.3f, 0.2f)
                    }
                }

                if (!hit && prevHit) {
                    baseBox.material {
                        diffuse = Vector3f(1.0f, 1f, 1f)
                    }
                }

                prevHit = hit
                frame++
                Thread.sleep(10)
            }
        }


        val lights = (0..10).map {
            PointLight(radius = 200.0f)
        }.map {
            it.spatial {
                position = Random.random3DVectorFromRange(-100.0f, 100.0f)
            }
            it.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            it.intensity = Random.randomFromRange(0.5f, 2f)
            it
        }

        lights.forEach { scene.addChild(it) }

        val hullbox = Box(Vector3f(100.0f, 100.0f, 100.0f), insideNormals = true)
        with(hullbox) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 0.0f)
            }

            material {
                ambient = Vector3f(0.6f, 0.6f, 0.6f)
                diffuse = Vector3f(0.4f, 0.4f, 0.4f)
                specular = Vector3f(0.0f, 0.0f, 0.0f)
                cullingMode = Material.CullingMode.Front
            }

            scene.addChild(this)
        }
    }

    override fun inputSetup() {
        super.inputSetup()
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            BoundingBoxExample().main()
        }
    }

}
