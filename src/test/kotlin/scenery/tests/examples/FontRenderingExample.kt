package scenery.tests.examples

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.GLAutoDrawable
import org.junit.Test
import scenery.*
import scenery.rendermodules.opengl.DeferredLightingRenderer

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class FontRenderingExample: SceneryDefaultApplication("FontRenderingExample") {

    val size = 64
    val fontsize = size*0.85



    fun createFontMeshFromString(contents: String): Mesh {
        val m = Mesh()

        return m
    }

    override fun init(pDrawable: GLAutoDrawable) {
        deferredRenderer = DeferredLightingRenderer(pDrawable.gl.gL4, glWindow!!.width, glWindow!!.height)
        hub.add(SceneryElement.RENDERER, deferredRenderer!!)

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
            position = GLVector(0.0f, 0.0f, -25.0f)
            view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)
            projection = GLMatrix()
                    .setPerspectiveProjectionMatrix(
                            70.0f / 180.0f * Math.PI.toFloat(),
                            1024f / 1024f, 0.1f, 1000.0f)
            active = true

            scene.addChild(this)
        }

        val board = FontBoard()
        board.text = "hello, world!"

        scene.addChild(board)


/*        val map = LinkedHashMap<Char, Pair<Float, ByteBuffer>>()

        val start = System.nanoTime()
        val charset = (32..127)

        val evts = charset.map {
            var start = System.nanoTime()
            val character =  genCharImage(it.toChar(), Font("Proxima Nova", 0, fontsize.toInt()), size)
            var dur = System.nanoTime() - start
            System.err.println("${it.toChar()}: Generation took ${dur/10e6} ms")

            start = System.nanoTime()
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
            val buf = outputBuffer.duplicate()
            buf.rewind()
            map.put(it.toChar(), Pair(character.first, buf))
            dur = System.nanoTime() - start

            System.err.println("${it.toChar()}: DT took ${dur/10e6} ms")
        }





        val end = System.nanoTime()
        System.err.println("\nDTs took ${(end-start)/10e6} ms")

        var pos = 0.0f
        var charlead = 0.0f
        planes.forEachIndexed { i, plane ->
            pos -= charlead
            val char = map.get(string[i])!!
            charlead = char.first
            System.err.println("Shift for $i is ${char.first}")
            System.err.println("pos=$pos")
            plane.position = GLVector(pos, 0.0f, 0.0f)
            plane.material?.textures?.put("diffuse", "fromBuffer:DT")
            plane.material?.transferTextures?.put("DT",
                    GenericTexture("DT", GLVector(size.toFloat(), size.toFloat(), 0.0f), 1, NativeTypeEnum.Float,
                            char.second))
            plane.material?.needsTextureReload = true
        }
        */

        deferredRenderer?.initializeScene(scene)

        repl.addAccessibleObject(scene)
        repl.addAccessibleObject(deferredRenderer!!)
        repl.addAccessibleObject(board)
        repl.start()
        repl.eval("var text = objectLocator(\"FontBoard\");")

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
