package graphics.scenery.tests.examples.basic

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.extensions.times
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
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class TexturedCubeExample : SceneryBase("TexturedCubeExample") {

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
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 700, 700))

//        renderer?.recordMovie("")

//        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
//        box.name = "le box du win"
//        box.material {
//            textures["diffuse"] = Texture.fromImage(Image.fromResource("textures/helix.png", TexturedCubeExample::class.java))
//            metallic = 0.3f
//            roughness = 0.9f
//        }
//        box.spatial().scale = Vector3f(2f, 2f, 2f)
//        box.spatial().position = Vector3f(-256f)
//        scene.addChild(box)

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

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

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                rotation = Quaternionf().rotationXYZ( 4.33334f * (Math.PI.toFloat() / 180f), 199.666f * (Math.PI.toFloat() / 180f), 0f)
                position = Vector3f(-313.645f, 74.846016f, -822.299500f)
            }
            perspectiveCamera(33.0f, 512, 512, nearPlaneLocation = 10f, farPlaneLocation = 10000f)

            scene.addChild(this)
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

        cam.spatial().rotation = Quaternionf(rot).normalize()
        cam.spatial().position = -1.0f * a

        cam.spatial().updateWorld(true)

        thread {
            while (running) {

                Thread.sleep(1000)
//                cam.spatial().view.invert()
                val t = Matrix4f(cam.spatial().getTransformation()).invert()
                logger.info("cam transformation is: $t")
                logger.info("camera position is: ${cam.spatial().position}")
                logger.info("volume model matrix is: ${volume.spatial().world}")

                logger.info("camera can see volume is: ${cam.canSee(volume)}")
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            TexturedCubeExample().main()
        }
    }
}

