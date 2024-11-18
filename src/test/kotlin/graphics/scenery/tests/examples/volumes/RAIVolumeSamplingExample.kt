package graphics.scenery.tests.examples.volumes

import bvv.core.VolumeViewerOptions
import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.primitives.Cylinder
import graphics.scenery.primitives.Line
import graphics.scenery.utils.MaybeIntersects
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import org.joml.Vector3f
import java.nio.file.Paths
import java.text.DecimalFormat
import kotlin.concurrent.thread
import kotlin.math.roundToInt


class RAIVolumeSamplingExample: SceneryBase("RAIVolume Sampling example" , 1280, 720) {
    lateinit var volume: Volume
    var playing = true

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            scene.addChild(this)
        }

        val shell = Box(Vector3f(10.0f, 10.0f, 10.0f), insideNormals = true)
        shell.material {
            cullingMode = Material.CullingMode.None
            diffuse = Vector3f(0.2f, 0.2f, 0.2f)
            specular = Vector3f(0.0f)
            ambient = Vector3f(0.0f)
        }
        scene.addChild(shell)

        val p1 = Icosphere(0.1f, 2)
        p1.spatial().position = Vector3f(0.0f, 0.5f, -5.0f)
        p1.material().diffuse = Vector3f(0.3f, 0.3f, 2.8f)
        scene.addChild(p1)

        val p2 = Icosphere(0.1f, 2)
        p2.spatial().position = Vector3f(0.0f,0.5f,3.0f)//Vector3f(0.0f, 0.5f, 2.0f)
        p2.material().diffuse = Vector3f(0.3f, 0.8f, 0.3f)

        scene.addChild(p2)

        val connector = Cylinder.betweenPoints(p1.spatial().position, p2.spatial().position)
        connector.material().diffuse = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(connector)

        p1.update.add {
            connector.spatial().orientBetweenPoints(p1.spatial().position, p2.spatial().position, true, true)
        }

        p2.update.add {
            connector.spatial().orientBetweenPoints(p1.spatial().position, p2.spatial().position, true, true)
        }

        volume = Volume.fromXML(Paths.get(getDemoFilesPath() + "/volumes/t1-head.xml").toString(), hub, VolumeViewerOptions())

        volume.spatial {
            position = Vector3f(-0.5f, 1.0f, 0.0f)
            scale = Vector3f(10.0f)
        }
        volume.colormap = Colormap.get("jet")
        val tf: TransferFunction = volume.transferFunction
        tf.clear()
        tf.addControlPoint(0.00f, 0.0f)
        tf.addControlPoint(0.001f, 0.0f)
        tf.addControlPoint(0.01f,1.0f)
        tf.addControlPoint(1.00f, 1.0f)

        scene.addChild(volume)


        Light.createLightTetrahedron<PointLight>(spread = 4.0f, radius = 15.0f, intensity = 2f)
            .forEach { scene.addChild(it) }

        val origin = Box(Vector3f(0.1f, 0.1f, 0.1f))
        origin.material().diffuse = Vector3f(0.8f, 0.0f, 0.0f)
        scene.addChild(origin)

        val p3 = Icosphere(0.1f, 2)
        p3.material().diffuse = Vector3f(0.3f, 0.5f, 0.8f)
        scene.addChild(p3)

        val p4 = Icosphere(0.1f, 2)
        p4.material().diffuse = Vector3f(0.3f, 0.5f, 0.3f)
        scene.addChild(p4)


        thread {
            while (!scene.initialized) {
                Thread.sleep(200)
            }
            while(running) {
                if (playing ) {
                    volume.previousTimepoint()
                }
                val intersection = volume.spatial()
                    .intersectAABB(p1.spatial().position, (p2.spatial().position - p1.spatial().position).normalize())
                if (intersection is MaybeIntersects.Intersection) {

                    val scale = volume.localScale()
                    val localEntry = (intersection.relativeEntry)
                    val localExit = (intersection.relativeExit)
                    p3.spatial().position = intersection.entry
                    p4.spatial().position = intersection.exit
                    val nf = DecimalFormat("0.0000")
                    logger.debug(
                        "Ray intersects volume at world=${intersection.entry.toString(nf)}/${
                            intersection.exit.toString(
                                nf
                            )
                        } local=${localEntry.toString(nf)}/${localExit.toString(nf)} localScale=${scale.toString(nf)}"
                    )

                    val (samples, _) = volume.sampleRay(localEntry, localExit) ?: (null to null)
                    logger.debug("${samples?.size} samples: ${samples?.joinToString(",") ?: "(no samples returned)"}")

                    if (samples == null) {
                        continue
                    }

                    val diagram = if (scene.getChildrenByName("diagram").isNotEmpty()) {
                        scene.getChildrenByName("diagram").first() as Line
                    } else {
                        val l = Line(capacity = (volume.getDimensions().length() * 2).roundToInt())
                        scene.addChild(l)
                        l
                    }

                    val offset = 1f
                    diagram.clearPoints()
                    diagram.name = "diagram"
                    diagram.edgeWidth = 0.005f
                    diagram.material().diffuse = Vector3f(1f, 1f, 1f)
                    diagram.spatial().position = Vector3f(0.0f, offset, 0f)
//                    diagram.addPoint(Vector3f(0.0f, 0.0f, 0.0f))
                    val p4p3 = p4.spatial().position - p3.spatial().position
                    p4p3.absolute()
                    diagram.addPoint(p1.spatial().position)
                    diagram.addPoint(p3.spatial().position)
                    var point: Vector3f
                    var z: Float
                    samples.filterNotNull().forEachIndexed { i, sample ->
                        // scale the Z value to fit between p3 and p4
                        z = i.toFloat()/samples.size * p4p3.z + p3.spatial().position.z
                        // stretch the sampled value with a large value, because the samples are really small
                        point = Vector3f(0.0f, sample * 100f + offset/2f, z)
                        diagram.addPoint(point)
                    }
                    diagram.addPoints(p4.spatial().position)
                    diagram.addPoint(p2.spatial().position)
                    // Adding two more points because Line never renders the last two points
                    diagram.addPoint(Vector3f(0f))
                    diagram.addPoints(Vector3f(0f))

                }
                Thread.sleep(500)
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            RAIVolumeSamplingExample().main()
        }
    }

}
