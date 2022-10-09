package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.attribute.material.Material
import graphics.scenery.volumes.BufferedVolume
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.*
import org.lwjgl.system.MemoryUtil
import java.io.FileInputStream
import java.lang.Math
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.streams.toList

/**
 * Standard volume rendering example, with a volume loaded from a file.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class VolumeExample: SceneryBase("Volume Rendering example", 512, 512) {
    var hmd: TrackedStereoGlasses? = null

    /**
     * Reads raw volumetric data from a [file].
     *
     * Returns the new volume.
     *
     * Based on Volume.fromPathRaw
     */
    fun fromPathRaw(file: Path, is16bit: Boolean = true): BufferedVolume {

        val infoFile: Path
        val volumeFiles: List<Path>

        if(Files.isDirectory(file)) {
            volumeFiles = Files.list(file).filter { it.toString().endsWith(".raw") && Files.isRegularFile(it) && Files.isReadable(it) }.toList()
            infoFile = file.resolve("stacks.info")
        } else {
            volumeFiles = listOf(file)
            infoFile = file.resolveSibling("stacks.info")
        }

        val lines = Files.lines(infoFile).toList()

        logger.debug("reading stacks.info (${lines.joinToString()}) (${lines.size} lines)")
        val dimensions = Vector3i(lines.get(0).split(",").map { it.toInt() }.toIntArray())
        logger.debug("setting dim to ${dimensions.x}/${dimensions.y}/${dimensions.z}")
        logger.debug("Got ${volumeFiles.size} volumes")

        val volumes = CopyOnWriteArrayList<BufferedVolume.Timepoint>()
        volumeFiles.forEach { v ->
            val id = v.fileName.toString()
            val buffer: ByteBuffer by lazy {

                logger.debug("Loading $id from disk")
                val buffer = ByteArray(1024 * 1024)
                val stream = FileInputStream(v.toFile())
                val numBytes = if(is16bit) {
                    2
                } else {
                    1
                }
                val imageData: ByteBuffer = MemoryUtil.memAlloc((numBytes * dimensions.x * dimensions.y * dimensions.z))

                logger.debug("${v.fileName}: Allocated ${imageData.capacity()} bytes for image of $dimensions containing $numBytes per voxel")

                val start = System.nanoTime()
                var bytesRead = stream.read(buffer, 0, buffer.size)
                while (bytesRead > -1) {
                    imageData.put(buffer, 0, bytesRead)
                    bytesRead = stream.read(buffer, 0, buffer.size)
                }
                val duration = (System.nanoTime() - start) / 10e5
                logger.debug("Reading took $duration ms")

                imageData.flip()
                imageData
            }

            volumes.add(BufferedVolume.Timepoint(id, buffer))
        }

        return if(is16bit) {
            Volume.fromBuffer(volumes, dimensions.x, dimensions.y, dimensions.z, UnsignedShortType(), hub)
        } else {
            Volume.fromBuffer(volumes, dimensions.x, dimensions.y, dimensions.z, UnsignedByteType(), hub)
        }
    }

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
//                rotation = Quaternionf().rotationXYZ( 4.33334f * (Math.PI.toFloat() / 180f), 199.666f * (Math.PI.toFloat() / 180f), 0f)
//                position = Vector3f(17.5f, 12.5f, 883f)

//                rotation = Quaternionf().rotationXYZ( 4.33334f * (Math.PI.toFloat() / 180f), 199.666f * (Math.PI.toFloat() / 180f), 0f)
//                position = Vector3f(-313.645f, 74.846016f, -822.299500f)
            }
            perspectiveCamera(33.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        val shell = Box(Vector3f(10.0f, 10.0f, 10.0f), insideNormals = true)
        shell.material {
            cullingMode = Material.CullingMode.None
            diffuse = Vector3f(0.2f, 0.2f, 0.2f)
            specular = Vector3f(0.0f)
            ambient = Vector3f(0.0f)
        }
//        scene.addChild(shell)

        val volume = fromPathRaw(Paths.get("/home/aryaman/Datasets/Volume/Engine"), false)
        volume.name = "volume"
        volume.colormap = Colormap.get("viridis")
        volume.spatial {
            position = Vector3f(0f)
//            rotation = rotation.rotateXYZ(0.05f, 0.05f, 0.05f)
            scale = Vector3f(2.0f, 2.0f, 2.0f)
        }
        volume.origin = Origin.FrontBottomLeft
        volume.transferFunction = TransferFunction.ramp(0.1f, 0.5f)
        volume.pixelToWorldRatio = 1f
        scene.addChild(volume)

        val lights = (0 until 3).map {
            PointLight(radius = 15.0f)
        }

        lights.mapIndexed { i, light ->
            light.spatial().position = Vector3f(2.0f * i - 4.0f,  i - 1.0f, 0.0f)
            light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            light.intensity = 0.5f
            scene.addChild(light)
        }

        val matrix = Matrix4f(-0.941669f,	-0.025429f,	0.335579f,	0.000000f,
            0.000000f,	0.997141f,	0.075559f,	0.000000f,
            -0.336541f,	0.071151f,	-0.938977f,	0.000000f,
            -313.644989f,	74.846016f,	-822.299500f,	1.000000f
        )

        var a = Vector3f(0f)

        matrix.getTranslation(a)

        logger.info("Got translation: $a")

        val rot = AxisAngle4f()

        matrix.getRotation(rot)

        logger.info("Got rotation: $rot")

        cam.spatial().rotation = Quaternionf(rot)
        cam.spatial().position = a

//        cam.spatial().updateWorld(true)

        thread {
            while (true) {
                Thread.sleep(1000)
                logger.info("volume model matrix is: ${volume.spatial().world}")
                logger.info("camera view matrix is: ${cam.spatial().getTransformation()}")
            }
        }

    }

//    override fun inputSetup() {
//        setupCameraModeSwitching()
//    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VolumeExample().main()
        }
    }
}
