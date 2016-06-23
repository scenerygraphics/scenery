package scenery.tests.examples

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.GLAutoDrawable
import coremem.types.NativeTypeEnum
import org.jocl.cl_mem
import org.junit.Test
import scenery.*
import scenery.compute.OpenCLContext
import scenery.rendermodules.opengl.DeferredLightingRenderer
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class FontRenderingExample: SceneryDefaultApplication("FontRenderingExample") {

    fun genCharImage(c: Char, font: Font, size: Int): ByteBuffer {
        /* Creating temporary image to extract character size */
        var image = BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY)
        var g: Graphics2D = image.createGraphics();
        g.setFont(font);
        val metrics = g.getFontMetrics();
        g.dispose();

        /* Get char charWidth and charHeight */
        val charWidth = metrics.charWidth(c);
        val charHeight = metrics.getHeight();

        /* Create image for holding the char */
        image = BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY)
        g = image.createGraphics()
        g.setFont(font);
        g.setPaint(java.awt.Color.WHITE);
        g.drawString(c.toString(), size/2 - metrics.charWidth(c)/2, size/2 + metrics.maxAscent/2)
        g.dispose();

        val data = (image.getRaster().getDataBuffer() as DataBufferByte).data

        var imageBuffer: ByteBuffer = ByteBuffer.allocateDirect(data.size)
        imageBuffer.order(ByteOrder.nativeOrder())
        imageBuffer.put(data, 0, data.size)
        imageBuffer.rewind()

        return imageBuffer
    }

    override fun init(pDrawable: GLAutoDrawable) {
        deferredRenderer = DeferredLightingRenderer(pDrawable.gl.gL4, glWindow!!.width, glWindow!!.height)
        hub.add(SceneryElement.RENDERER, deferredRenderer!!)

        var boxmaterial = Material()
        with(boxmaterial) {
            ambient = GLVector(1.0f, 0.0f, 0.0f)
            diffuse = GLVector(0.0f, 1.0f, 0.0f)
            specular = GLVector(1.0f, 1.0f, 1.0f)
        }

        var box = Box(GLVector(1.0f, 1.0f, 1.0f))

        with(box) {
            box.material = boxmaterial
            box.position = GLVector(0.0f, 0.0f, 0.0f)
            scene.addChild(this)
        }

        var lights = (0..2).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i, 2.0f * i, 2.0f * i)
            light.emissionColor = GLVector(1.0f, 0.0f, 1.0f)
            light.intensity = 0.2f*(i+1);
            scene.addChild(light)
        }

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, -5.0f)
            view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)
            projection = GLMatrix()
                    .setPerspectiveProjectionMatrix(
                            70.0f / 180.0f * Math.PI.toFloat(),
                            1024f / 1024f, 0.1f, 1000.0f)
            active = true

            scene.addChild(this)
        }


        val ocl = OpenCLContext(hub)
        val size = 64
        var input: cl_mem
        var output: cl_mem

        val map = ConcurrentHashMap<Char, ByteBuffer>()

        val start = System.nanoTime()

        val evts = (32..127).map {
            input = ocl.wrapInput(genCharImage(it.toChar(), Font("Source Code Pro", 0, 44), size))
            val outputBuffer = ByteBuffer.allocate(size*size)
            output = ocl.wrapOutput(outputBuffer)

            ocl.loadKernel(OpenCLContext::class.java.getResource("DistanceTransform.cl"), "SignedDistanceTransformByte")
                    .runKernel("SignedDistanceTransformByte",
                            size*size,
                            false,
                            input,
                            output,
                            size,
                            size)

            ocl.readBuffer(output, outputBuffer)
            map.put(it.toChar(), outputBuffer.duplicate())
        }

        val end = System.nanoTime()
        System.err.println("\nDT took ${(end-start)/10e6} ms")

        boxmaterial.textures.put("diffuse", "fromBuffer:DT");
        boxmaterial.transferTextures.put("DT",
                GenericTexture("DT", GLVector(64.0f, 64.0f, 0.0f), 1, NativeTypeEnum.UnsignedByte,
                map.get("\""[0])!!))
        boxmaterial.needsTextureReload = true

        deferredRenderer?.initializeScene(scene)

        repl.addAccessibleObject(scene)
        repl.addAccessibleObject(deferredRenderer!!)
        repl.showConsoleWindow()

        thread {
            while (true) {
                box.rotation.rotateByAngleY(0.01f)
                box.needsUpdate = true

                Thread.sleep(20)
            }
        }

        thread {
            Thread.sleep(2500)
            val range = (32..127).toList()
            var index = 0

            while (true) {
                val char: String = String.format("%c", range[index % range.count()])
                System.err.println(char)

                this.glWindow?.windowTitle = "Char: $char"

                if (box.lock.tryLock()) {
                    boxmaterial.textures.put("diffuse", "fromBuffer:DT");
                    boxmaterial.transferTextures.put("DT",
                            GenericTexture("DT", GLVector(64.0f, 64.0f, 0.0f), 1, NativeTypeEnum.UnsignedByte,
                                    map[char[0]]!!))
                    boxmaterial.needsTextureReload = true

                    index++

                    box.lock.unlock()
                    Thread.sleep(750)
                } else {
                    Thread.sleep(500)
                }
            }
        }
    }

    @Test override fun main() {
        super.main()
    }
}
