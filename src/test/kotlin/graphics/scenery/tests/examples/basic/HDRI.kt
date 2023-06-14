package graphics.scenery.tests.examples.basic

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyexr.EXRHeader
import org.lwjgl.util.tinyexr.EXRImage
import org.lwjgl.util.tinyexr.EXRVersion
import org.lwjgl.util.tinyexr.TinyEXR
import org.lwjgl.util.tinyexr.TinyEXR.*
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer


/**
 * <Description>
 *
 * @author Samuel Pantze
 */
class HDRI : SceneryBase("HDRI") {

    private fun check(result: Int, err: PointerBuffer) {
        if (result < 0) {
            val msg = err.getStringASCII(0)
            nFreeEXRErrorMessage(err[0])
            throw IllegalStateException("EXR error: $result | $msg")
        }
    }

    override fun init() {
        val filename = "C:/Users/Samuel/Pictures/test2.exr"
        val memory: ByteBuffer
        try {
            val f = File(filename)
            val array = f.inputStream().readAllBytes()
            memory = MemoryUtil.memAlloc(array.size)
            memory.put(array)
            memory.flip()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        var result: Int
        val image: EXRImage
        val header: EXRHeader
        //MemoryStack.stackPush().use { stack ->
            val stack = MemoryStack.stackPush()
            header = EXRHeader.malloc(stack)
            val version = EXRVersion.malloc(stack)
            val err: PointerBuffer = stack.mallocPointer(1)
            result = ParseEXRVersionFromMemory(version, memory)
            check(result >= 0) { "Failed to parse EXR image version: $result" }
            logger.info("Parsed EXR image version successfully.")
            logger.info("--------------------------------------")
            logger.info("Version : " + version.version())
            logger.info("Tiled : " + version.tiled())
            logger.info("Long name : " + version.long_name())
            logger.info("Non-image : " + version.non_image())
            logger.info("Multipart : " + version.multipart())
            InitEXRHeader(header)
            result = ParseEXRHeaderFromMemory(header, version, memory, err)
            check(result, err)
            logger.info("Parsed EXR image header successfully.")
            logger.info("-------------------------------------")
            logger.info("Pixel aspect ratio : " + header.pixel_aspect_ratio())
            logger.info("Line order : " + header.line_order())
            logger.info("Data window : " + header.data_window().min_x() + ", " + header.data_window().min_y() + " -> " + header.data_window().max_x() + ", " + header.data_window().max_y())
            logger.info("Display window : " + header.display_window().min_x() + ", " + header.display_window().min_y() + " -> " + header.display_window().max_x() + ", " + header.display_window().max_y())
            logger.info("Screen window center : " + header.screen_window_center()[0] + ", " + header.screen_window_center()[1])
            logger.info("Screen window width : " + header.screen_window_width())
            logger.info("Chunk count : " + header.chunk_count())
            logger.info("Tiled : " + header.tiled())
            logger.info("Tile size x : " + header.tile_size_x())
            logger.info("Tile size y : " + header.tile_size_y())
            logger.info("Tile level mode : " + header.tile_level_mode())
            logger.info("Tile rounding mode : " + header.tile_rounding_mode())
            logger.info("Long name : " + header.long_name())
            logger.info("Non-image : " + header.non_image())
            logger.info("Multipart : " + header.multipart())
            logger.info("Header length : " + header.header_len())
            logger.info("Number of custom attributes : " + header.num_custom_attributes())
            logger.info("Custom attributes : " + header.custom_attributes())
            logger.info("Channels : " + header.channels().remaining())
            logger.info("Pixel types : " + header.pixel_types().remaining())
            logger.info("Number of channels : " + header.num_channels())
            logger.info("Compression type : " + header.compression_type())
            logger.info("Requested pixel types : " + header.requested_pixel_types().remaining())
            logger.info("Name : " + header.nameString())

            image = EXRImage.malloc(stack)
            InitEXRImage(image)
            result = LoadEXRImageFromMemory(image, header, memory, err)
            check(result, err)
            logger.info("Parsed EXR image successfully.")
            logger.info("------------------------------")
            logger.info("Level x: " + image.level_x())
            logger.info("Level y: " + image.level_y())
            logger.info("Images: " + image.images()!!.remaining())
            logger.info("Width: " + image.width())
            logger.info("Height: " + image.height())
            logger.info("Number of channels: " + image.num_channels())
            logger.info("Number of tiles: " + image.num_tiles())

            //FreeEXRImage(image)
            FreeEXRHeader(header)
        //}

        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 1000, 800))

        val sphereHDRI = Icosphere(30f, 3, insideNormals = true)
        sphereHDRI.name = "HDRI Sphere"

        val pb = image.images()
        val bb = MemoryUtil.memByteBuffer(pb!!.get(), image.width()*image.height()*3*4)

        val tex = Texture(
            Vector3i(image.width(), image.height(), 1),
            image.num_channels(),
            FloatType(),
            bb,
            normalized = false
        )


        sphereHDRI.material {
            //textures["diffuse"] = Texture.fromImage(Image.fromResource("textures/hdri.jpg", HDRI::class.java))
            textures["diffuse"] = tex
            cullingMode = Material.CullingMode.None
            Texture()
        }
        scene.addChild(sphereHDRI)

        val sphereMetal = Sphere(0.5f)
        sphereMetal.name = "Sphere"
        sphereMetal.material {
            diffuse = Vector3f(0.8f, 0.9f, 1.0f)
            metallic = 0.1f
            roughness = 0.7f
            specular = Vector3f(1.0f, 1.0f, 1.0f)
        }
        sphereMetal.spatial {
            position = (Vector3f(0f, 0f, 0f))
        }
        scene.addChild(sphereMetal)
        /*val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        box.name = "le box du win"
        box.material {
            textures["diffuse"] = Texture.fromImage(Image.fromResource("textures/helix.png", TexturedCubeExample::class.java))
            metallic = 0.3f
            roughness = 0.9f
        }
        scene.addChild(box)
         */
        val light = PointLight(radius = 50.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 10.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(70.0f, 512, 512)

            scene.addChild(this)
        }

        /*thread {
            while (running) {
                box.spatial {
                    rotation.rotateY(0.01f)
                    needsUpdate = true
                }

                Thread.sleep(20)
            }
        }*/
    }

    override fun inputSetup() {
        super.inputSetup()

        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            HDRI().main()
        }
    }
}

