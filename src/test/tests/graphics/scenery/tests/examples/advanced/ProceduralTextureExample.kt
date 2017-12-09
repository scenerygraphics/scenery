package graphics.scenery.tests.examples.advanced

import cleargl.GLTypeEnum
import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Example demonstrating procedural texturing using [GenericTexture].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ProceduralTextureExample : SceneryBase("ProceduralTextureExample") {
    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, 512, 512)
        hub.add(SceneryElement.Renderer, renderer!!)

        val boxmaterial = Material()
        with(boxmaterial) {
            ambient = GLVector(1.0f, 0.0f, 0.0f)
            diffuse = GLVector(0.0f, 1.0f, 0.0f)
            specular = GLVector(1.0f, 1.0f, 1.0f)
        }

        val box = Box(GLVector(1.0f, 1.0f, 1.0f))
        box.name = "le box du procedurale"

        with(box) {
            box.material = boxmaterial
            scene.addChild(this)
        }

        val lights = (0..2).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i, 2.0f * i, 2.0f * i)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 500.2f*(i+1)
            scene.addChild(light)
        }

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, 3.0f)
            perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
            active = true

            scene.addChild(this)
        }

        thread {
            val imageSizeX = 256
            val imageSizeY = 256
            val imageChannels = 4
            val textureBuffer = BufferUtils.allocateByte(imageSizeX * imageSizeY * imageChannels)
            var ticks = 0L

            while(true) {
                if(box.lock.tryLock()) {
                    box.rotation.rotateByAngleY(0.01f)
                    box.needsUpdate = true

                    textureBuffer.generateProceduralTextureAtTick(ticks,
                        imageSizeX, imageSizeY, imageChannels)

                    box.material.transferTextures.put("diffuse",
                        GenericTexture(
                            "myProceduralTexture",
                            GLVector(imageSizeX.toFloat(), imageSizeY.toFloat(), 1.0f),
                            channels = imageChannels, contents = textureBuffer,
                            type = GLTypeEnum.UnsignedByte))
                    box.material.textures.put("diffuse", "fromBuffer:diffuse")
                    box.material.needsTextureReload = true


                    box.lock.unlock()
                } else {
                    logger.info("unsuccessful lock")
                }

                Thread.sleep(50)
                ticks++
            }
        }
    }

    /**
     * Generates a procedural texture inside the [ByteBuffer].
     *
     * @param[tick] The time parameter for the generated texture.
     */
    private fun ByteBuffer.generateProceduralTextureAtTick(tick: Long, width: Int, height: Int, channels: Int) {
        val rgba = byteArrayOf(0, 0, 0, 255.toByte())

        (0 until width * height).forEach {
            val x = it % width
            val y = it / height

            val g = (255*Math.sin(0.1*x + 0.1*y + tick/10.0f)).toByte()
            val m = (Math.sin(tick/100.0) * g).toByte()
            rgba[0] = g
            rgba[1] = m
            rgba[2] = m

            this.put(rgba.take(channels).toByteArray())
        }

        this.flip()
    }

    @Test override fun main() {
        super.main()
    }
}

