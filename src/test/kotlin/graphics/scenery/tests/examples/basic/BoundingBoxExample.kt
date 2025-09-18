package graphics.scenery.tests.examples.basic

import graphics.scenery.Box
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.Mesh
import graphics.scenery.Node
import graphics.scenery.PointLight
import graphics.scenery.SceneryBase
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import org.joml.Vector3f
import kotlin.concurrent.thread
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Demo for testing bounding box intersection in world space between children of nodes.
 *
 * @author Samuel Pantze
 */
class BoundingBoxExample : SceneryBase("BoundingBoxExample") {

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0f, 0f, 40.0f)
            }
            perspectiveCamera(70.0f, windowWidth, windowHeight, 1.0f, 1000.0f)
            scene.addChild(this)
        }

        val boundaryWidth = 13.0f
        val spacing = 2f

        val boxParent = Mesh()
        val boxes = (0 until boundaryWidth.pow(3).toInt()).map {
            Box(Vector3f(0.5f))
        }

        boxes.mapIndexed {
                index, box ->

            val k = index % boundaryWidth
            val j = (index / boundaryWidth) % boundaryWidth
            val i = index / (boundaryWidth * boundaryWidth)

            box.spatial {
                position = Vector3f(
                    spacing * (floor(i).toFloat() - boundaryWidth / 2f),
                    spacing * (floor(j).toFloat() - boundaryWidth / 2f),
                    spacing * (floor(k).toFloat() - boundaryWidth / 2f)
                )
            }
            box.material {
                diffuse = Vector3f(1.0f, 1.0f, 1.0f)
            }

            boxParent.addChild(box)
        }

        scene.addChild(boxParent)

        val intersectionParent = Mesh()

        scene.addChild(intersectionParent)

        // Big rotating box for testing intersections
        val intersectionChild = Box(Vector3f(4f))

        intersectionChild.spatial {
            scale = Vector3f(2f, 3f, 5f)
        }

        intersectionChild.material {
            wireframe = true
            wireframeWidth = 5.0f
            diffuse = Vector3f(0.5f, 1f, 0.4f)

        }

        intersectionParent.addChild(intersectionChild)

        var selected = emptyList<Node>()

        var frame = 0

        thread {
            while (true) {
                intersectionParent.spatial {
                    rotation.rotateXYZ(0.006f, 0.004f, 0.003f)
                    needsUpdate = true
                    scale.mul(
                        sin(frame/140f)/400f+1f,
                        sin(frame/100f)/400f+1f,
                        sin(frame/80f)/400f+1f,
                    )
                }

                val hit = boxes.filter { node ->
                    // Only interact with visible nodes
                    if (!node.visible) { return@filter false }
                    // Equality check is also a null check here
                    intersectionChild.spatialOrNull()?.intersects(node, true) == true
                }.toList()

                val new = hit.filter { !selected.contains(it) }
                val released = selected.filter { !hit.contains(it) }
                selected = hit

                new.forEach {
                    it.material {
                        diffuse = Vector3f(1.0f, 0.3f, 0.2f)
                    }
                }
                released.forEach {
                    it.ifMaterial {
                        diffuse = Vector3f(1.0f, 1f, 1f)
                    }
                }

                frame++
                Thread.sleep(100)
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
