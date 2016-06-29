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
import scenery.rendermodules.opengl.OpenGLShaderPreference
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class FontRenderingExample: SceneryDefaultApplication("FontRenderingExample") {

    val size = 64
    val fontsize = 48

    fun genCharImage(c: Char, font: Font, size: Int): Pair<Float, ByteBuffer> {
        /* Creating temporary image to extract character size */
        var image = BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY)
        var g: Graphics2D = image.createGraphics();
        g.setFont(font);
        val metrics = g.getFontMetrics();
        g.dispose();

        /* Get char charWidth and charHeight */
        val charWidth = metrics.charWidth(c);
        System.err.println("With of $c is ${charWidth.toFloat()/size.toFloat()}")
        val charHeight = metrics.getHeight();

        /* Create image for holding the char */
        image = BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY)
        g = image.createGraphics()
        g.setFont(font);
        g.setPaint(java.awt.Color.WHITE);
        g.drawString(c.toString(), size/2 - metrics.charWidth(c)/2, size/2 + metrics.maxAscent/2)
        g.dispose();

        val flipped = BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY)
        val gf = flipped.createGraphics()
        val at = AffineTransform()
        at.concatenate(AffineTransform.getScaleInstance(1.0, -1.0))
        at.concatenate(AffineTransform.getTranslateInstance(0.0, -size*1.0))
        gf.transform(at)
        gf.drawImage(image, 0, 0, null)
        gf.dispose()

        val data = (flipped.getRaster().getDataBuffer() as DataBufferByte).data

        var imageBuffer: ByteBuffer = ByteBuffer.allocateDirect(data.size)
        imageBuffer.order(ByteOrder.nativeOrder())
        imageBuffer.put(data, 0, data.size)
        imageBuffer.rewind()

        return Pair(charWidth.toFloat()/size.toFloat(), imageBuffer)
    }

    override fun init(pDrawable: GLAutoDrawable) {
        deferredRenderer = DeferredLightingRenderer(pDrawable.gl.gL4, glWindow!!.width, glWindow!!.height)
        hub.add(SceneryElement.RENDERER, deferredRenderer!!)

        val string = "hello, scenery"
        var planes = (0..string.length-1).map {
            val b = Plane(GLVector(2.0f, 2.0f, 0.1f))
            b.material = Material()
            b.isBillboard = true
            b
        }

        planes.forEachIndexed { i, plane ->
            plane.name = "UI_plane$i"
            plane.metadata.put(
                    "ShaderPreference",
                    OpenGLShaderPreference(
                            arrayListOf("DefaultDeferred.vert", "SDFFont.frag"),
                            HashMap<String, String>(),
                            arrayListOf("DeferredShadingRenderer")))

            scene.addChild(plane)
        }

        var lights = (0..5).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i, 2.0f * i, 2.0f * i)
            light.emissionColor = GLVector(1.0f, 0.0f, 1.0f)
            light.intensity = 0.2f*(i+1);
            scene.addChild(light)
        }

        val hullbox = Box(GLVector(900.0f, 900.0f, 900.0f))
        hullbox.position = GLVector(0.1f, 0.1f, 0.1f)
        val hullboxM = Material()
        hullboxM.ambient = GLVector(1.0f, 1.0f, 1.0f)
        hullboxM.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        hullboxM.specular = GLVector(1.0f, 1.0f, 1.0f)
        hullboxM.doubleSided = true
        hullbox.material = hullboxM

        scene.addChild(hullbox)

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


        val ocl = OpenCLContext(hub, devicePreference = "0,0")
        var input: cl_mem
        var output: cl_mem

        val map = ConcurrentHashMap<Char, Pair<Float, ByteBuffer>>()

        val start = System.nanoTime()

        val evts = (32..127).map {
            val character =  genCharImage(it.toChar(), Font("Helvetica Neue", 0, fontsize), size)
            input = ocl.wrapInput(character.second)
            val outputBuffer = ByteBuffer.allocate(4*size*size)
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
            map.put(it.toChar(), Pair(character.first, outputBuffer.duplicate()))
        }

        val end = System.nanoTime()
        System.err.println("\nDT took ${(end-start)/10e6} ms")

        var pos = 0.0f
        planes.forEachIndexed { i, plane ->
            val char = map.get(string[i])!!
            System.err.println("Shift for $i is ${char.first}")
            pos = pos - char.first
            plane.position = GLVector(pos + 1.0f, 0.0f, 0.0f)
            plane.material?.textures?.put("diffuse", "fromBuffer:DT")
            plane.material?.transferTextures?.put("DT",
                    GenericTexture("DT", GLVector(size.toFloat(), size.toFloat(), 0.0f), 1, NativeTypeEnum.Float,
                            char.second))
            plane.material?.needsTextureReload = true
        }

        deferredRenderer?.initializeScene(scene)

        repl.addAccessibleObject(scene)
        repl.addAccessibleObject(deferredRenderer!!)
        repl.showConsoleWindow()

//        thread {
//            while (true) {
//                box.rotation.rotateByAngleY(0.01f)
//                box.needsUpdate = true
//
//                Thread.sleep(20)
//            }
//        }
//
//        thread {
//            Thread.sleep(2500)
//            val range = (32..127).toList()
//            var index = 0
//
//            while (true) {
//                val char: String = String.format("%c", range[index % range.count()])
//                System.err.println(char)
//
//                this.glWindow?.windowTitle = "Char: $char"
//
//                if (box.lock.tryLock()) {
//                    boxmaterial.textures.put("diffuse", "fromBuffer:DT");
//                    boxmaterial.transferTextures.put("DT",
//                            GenericTexture("DT", GLVector(64.0f, 64.0f, 0.0f), 1, NativeTypeEnum.UnsignedByte,
//                                    map[char[0]]!!))
//                    boxmaterial.needsTextureReload = true
//
//                    index++
//
//                    box.lock.unlock()
//                    Thread.sleep(750)
//                } else {
//                    Thread.sleep(500)
//                }
//            }
//        }
    }

    @Test override fun main() {
        super.main()
    }
}
