package scenery.rendermodules.opengl

import cleargl.GLFramebuffer
import cleargl.GLMatrix
import cleargl.GLProgram
import com.jogamp.opengl.GL
import com.jogamp.opengl.GL4
import scenery.*
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class DeferredLightingRenderer {
    protected var gl: GL4
    protected var width: Int
    protected var height: Int
    protected var geometryBuffer: GLFramebuffer
    protected var lightingPassProgram: GLProgram

    protected var debugBuffers = 0;

    constructor(gl: GL4, width: Int, height: Int) {
        this.gl = gl
        this.width = width
        this.height = height

        geometryBuffer = GLFramebuffer(this.gl, width, height)
        geometryBuffer.addFloatRGBBuffer(this.gl, 16)
        geometryBuffer.addFloatRGBBuffer(this.gl, 16)
        geometryBuffer.addUnsignedByteRGBABuffer(this.gl, 16)
        geometryBuffer.addDepthBuffer(this.gl, 32)

        geometryBuffer.checkAndSetDrawBuffers(this.gl)
        System.out.println(geometryBuffer.toString())

        lightingPassProgram = GLProgram.buildProgram(gl, RenderGeometricalObject::class.java,
                arrayOf("shaders/Dummy.vs", "shaders/FullscreenQuadGenerator.gs", "shaders/DeferredLighting.fs"))
    }

    fun toggleDebug() {
        if(debugBuffers == 0) {
            debugBuffers = 1;
        } else {
            debugBuffers = 0;
        }
    }

    fun getOpenGLObjectStateFromNode(node: Node): OpenGLObjectState {
        return node.metadata.find { it.consumers.contains("DeferredLightingRenderer") } as OpenGLObjectState
    }

    fun initializeScene(scene: Scene) {
        scene.initList.filter { it is HasGeometry }.forEach {
            it.metadata.add(OpenGLObjectState())
            initializeNode(it)
        }
    }

    fun render(scene: Scene) {
        geometryBuffer.checkAndSetDrawBuffers(gl)

        val renderOrderList = ArrayList<Node>()
        val cam: Camera = scene.findObserver()
        var view: GLMatrix
        var mv: GLMatrix
        var mvp: GLMatrix
        var proj: GLMatrix

        gl.glEnable(GL.GL_DEPTH_TEST)
        gl.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)

        gl.glEnable(GL.GL_CULL_FACE)
        gl.glFrontFace(GL.GL_CCW)
        gl.glCullFace(GL.GL_BACK)

        gl.glDepthFunc(GL.GL_LEQUAL)

        scene.discover(scene, { n -> n is Renderable && n is HasGeometry }).forEach {
            renderOrderList.add(it)
        }

        renderOrderList.sort { a, b -> (a.position.z() - b.position.z()).toInt() }

        cam.view?.setCamera(cam.position, cam.position + cam.forward, cam.up)

        for (n in renderOrderList) {
            val s = getOpenGLObjectStateFromNode(n)
            n.model = GLMatrix.getIdentity()
            n.model.translate(n.position.x(), n.position.y(), n.position.z())
            n.model.scale(n.scale.x(), n.scale.y(), n.scale.z())
            n.updateWorld(true, false)

            mv = cam.view!!.clone().mult(cam.rotation)
            mv.mult(n.world)

            proj = cam.projection!!.clone()
            mvp = proj.clone()
            mvp.mult(mv)

            val program: GLProgram? = s.program

            program?.let {
                program.use(gl)
                program.getUniform("ModelMatrix")!!.setFloatMatrix(n.world, false);
                program.getUniform("ModelViewMatrix")!!.setFloatMatrix(mv, false)
                program.getUniform("ProjectionMatrix")!!.setFloatMatrix(cam.projection, false)
                program.getUniform("MVP")!!.setFloatMatrix(mvp, false)

                program.getUniform("Light.Ld").setFloatVector3(1.0f, 1.0f, 0.8f);
                program.getUniform("Light.Position").setFloatVector3(5.0f, 5.0f, 5.0f);
                program.getUniform("Light.La").setFloatVector3(0.4f, 0.4f, 0.4f);
                program.getUniform("Light.Ls").setFloatVector3(0.0f, 0.0f, 0.0f);
                program.getUniform("Material.Shinyness").setFloat(0.001f);

                if (n.material != null) {
                    program.getUniform("Material.Ka").setFloatVector(n.material!!.ambient);
                    program.getUniform("Material.Kd").setFloatVector(n.material!!.diffuse);
                    program.getUniform("Material.Ks").setFloatVector(n.material!!.specular);
                } else {
                    program.getUniform("Material.Ka").setFloatVector3(n.position.toFloatBuffer());
                    program.getUniform("Material.Kd").setFloatVector3(n.position.toFloatBuffer());
                    program.getUniform("Material.Ks").setFloatVector3(n.position.toFloatBuffer());
                }
            }

            drawNode(n)
        }

        geometryBuffer.bindTexturesToUnitsWithOffset(gl, 0);
        geometryBuffer.revertToDefaultFramebuffer(gl);

        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);

        gl.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT);

        lightingPassProgram.bind()

        val lights = scene.discover(scene, {it is PointLight})

        for(i in 0..lights.size-1) {
            lightingPassProgram.getUniform("lights[$i].Position").setFloatVector(lights[i].position)
            lightingPassProgram.getUniform("lights[$i].Color").setFloatVector((lights[i] as PointLight).emissionColor)
        }

        lightingPassProgram.getUniform("gPosition").setInt(0)
        lightingPassProgram.getUniform("gNormal").setInt(1)
        lightingPassProgram.getUniform("gAlbedoSpec").setInt(2)
        lightingPassProgram.getUniform("gDepth").setInt(3)

        lightingPassProgram.getUniform("debugDeferredBuffers").setInt(debugBuffers);

        renderFullscreenQuad(lightingPassProgram)
    }

    fun renderFullscreenQuad(quadGenerator: GLProgram) {
       val quadId: IntBuffer = IntBuffer.allocate(1)

        quadGenerator.gl.gL4.glGenVertexArrays(1, quadId)
        quadGenerator.gl.gL4.glBindVertexArray(quadId.get(0))

        // fake call to draw one point, geometry is generated in shader pipeline
        quadGenerator.gl.gL4.glDrawArrays(GL.GL_POINTS, 0, 1)
        quadGenerator.gl.gL4.glBindTexture(GL.GL_TEXTURE_2D, 0)

        quadGenerator.gl.gL4.glBindVertexArray(0)
        quadGenerator.gl.gL4.glDeleteVertexArrays(1, quadId)
    }

    fun initializeNode(node: Node): Boolean {
        val s: OpenGLObjectState = node.metadata.find { it.consumers.contains("DeferredLightingRenderer") } as OpenGLObjectState

        // generate VAO for attachment of VBO and indices
        gl.gL3.glGenVertexArrays(1, s.mVertexArrayObject, 0)

        // generate three VBOs for coords, normals, texcoords
        gl.glGenBuffers(3, s.mVertexBuffers, 0)
        gl.glGenBuffers(1, s.mIndexBuffer, 0)

        if (node.material == null || node.material !is OpenGLMaterial || (node.material as OpenGLMaterial).program == null) {
            s.program = GLProgram.buildProgram(gl, RenderGeometricalObject::class.java,
                    arrayOf("shaders/DefaultDeferred.vs", "shaders/DefaultDeferred.fs"))
        } else {
            s.program = (node.material as OpenGLMaterial).program
        }

        if(node is HasGeometry) {

            setVerticesAndCreateBufferForNode(node)
            setNormalsAndCreateBufferForNode(node)

            if (node.texcoords.size > 0) {
                setTextureCoordsAndCreateBufferForNode(node)
            }

            if (node.indices.size > 0) {
                setIndicesAndCreateBufferForNode(node)
            }
        }

        s.initialized = true
        return true
    }

    fun setVerticesAndCreateBufferForNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node);
        val pVertexBuffer: FloatBuffer = FloatBuffer.wrap((node as HasGeometry).vertices)

        s.mStoredPrimitiveCount = pVertexBuffer.remaining() / node.vertexSize

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, s.mVertexBuffers[0])

        gl.gL3.glEnableVertexAttribArray(0)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
                (pVertexBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pVertexBuffer,
                if (s.isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(0,
                node.vertexSize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }

    /*fun setArbitraryAndCreateBuffer(name: String,
                                    pBuffer: FloatBuffer,
                                    pBufferGeometrySize: Int) {
        // create additional buffers
        if (!additionalBufferIds.containsKey(name)) {
            gl.glGenBuffers(1,
                    mVertexBuffers,
                    mVertexBuffers.size - 1)
            additionalBufferIds.put(name,
                    mVertexBuffers[mVertexBuffers.size - 1])
        }

        mStoredPrimitiveCount = pBuffer.remaining() / geometryObject.vertexSize

        gl.gL3.glBindVertexArray(mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER,
                mVertexBuffers[mVertexBuffers.size - 1])

        gl.gL3.glEnableVertexAttribArray(0)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
                (pBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pBuffer,
                if (isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(mVertexBuffers.size - 1,
                pBufferGeometrySize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }*/

    /*fun updateVertices(pVertexBuffer: FloatBuffer) {
        mStoredPrimitiveCount = pVertexBuffer.remaining() / geometryObject.vertexSize

        if (!isDynamic)
            throw UnsupportedOperationException("Cannot update non dynamic buffers!")

        gl.gL3.glBindVertexArray(mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, mVertexBuffers[0])

        gl.gL3.glEnableVertexAttribArray(0)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
                (pVertexBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pVertexBuffer,
                if (isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(0,
                geometryObject.vertexSize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }*/

    fun setNormalsAndCreateBufferForNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node);
        val pNormalBuffer: FloatBuffer = FloatBuffer.wrap((node as HasGeometry).normals)

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, s.mVertexBuffers[1])

        gl.gL3.glEnableVertexAttribArray(1)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
                (pNormalBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pNormalBuffer,
                if (s.isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(1,
                node.vertexSize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }

    /*fun updateNormals(pNormalBuffer: FloatBuffer) {
        if (!isDynamic)
            throw UnsupportedOperationException("Cannot update non dynamic buffers!")

        gl.gL3.glBindVertexArray(mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, mVertexBuffers[1])

        gl.gL3.glEnableVertexAttribArray(1)
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER,
                0,
                (pNormalBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pNormalBuffer)

        gl.gL3.glVertexAttribPointer(1,
                geometryObject.vertexSize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }*/

    fun setTextureCoordsAndCreateBufferForNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node);
        val pTextureCoordsBuffer: FloatBuffer = FloatBuffer.wrap((node as HasGeometry).texcoords)

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, s.mVertexBuffers[2])

        gl.gL3.glEnableVertexAttribArray(2)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
                (pTextureCoordsBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pTextureCoordsBuffer,
                if (s.isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(2,
                node.texcoordSize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }

    /*fun updateTextureCoords(pTextureCoordsBuffer: FloatBuffer) {
        if (!isDynamic)
            throw UnsupportedOperationException("Cannot update non dynamic buffers!")

        gl.gL3.glBindVertexArray(mVertexArrayObject[0])
        GLError.printGLErrors(gl, "1")

        gl.gL3.glBindBuffer(GL.GL_ARRAY_BUFFER,
                mVertexBuffers[2])
        GLError.printGLErrors(gl, "2")

        gl.gL3.glEnableVertexAttribArray(2)
        GLError.printGLErrors(gl, "3")

        gl.glBufferSubData(GL.GL_ARRAY_BUFFER,
                0,
                (pTextureCoordsBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pTextureCoordsBuffer)
        GLError.printGLErrors(gl, "4")

        gl.gL3.glVertexAttribPointer(2,
                geometryObject.texcoordSize,
                GL.GL_FLOAT,
                false,
                0,
                0)
        GLError.printGLErrors(gl, "5")

        gl.gL3.glBindVertexArray(0)
        GLError.printGLErrors(gl, "6")

        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
        GLError.printGLErrors(gl, "7")

    }*/

    fun setIndicesAndCreateBufferForNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node);
        val pIndexBuffer: IntBuffer = IntBuffer.wrap((node as HasGeometry).indices)

        s.mStoredIndexCount = pIndexBuffer.remaining()

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, s.mIndexBuffer[0])

        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER,
                (pIndexBuffer.limit() * (Integer.SIZE / java.lang.Byte.SIZE)).toLong(),
                pIndexBuffer,
                if (s.isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    /*fun updateIndices(pIndexBuffer: IntBuffer) {
        if (!isDynamic)
            throw UnsupportedOperationException("Cannot update non dynamic buffers!")

        mStoredIndexCount = pIndexBuffer.remaining()

        gl.gL3.glBindVertexArray(mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, mIndexBuffer[0])

        gl.glBufferSubData(GL.GL_ELEMENT_ARRAY_BUFFER,
                0,
                (pIndexBuffer.limit() * (Integer.SIZE / java.lang.Byte.SIZE)).toLong(),
                pIndexBuffer)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0)
    }*/

    fun drawNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node);

        if (s.mStoredIndexCount > 0) {
            drawNode(node, 0, s.mStoredIndexCount)
        } else {
            drawNode(node, 0, s.mStoredPrimitiveCount)
        }
    }

    fun drawNode(node: Node, pOffset: Int, pCount: Int) {
        val s = getOpenGLObjectStateFromNode(node);

        s.program?.use(gl)

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])

        if (s.mStoredIndexCount > 0) {
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER,
                    s.mIndexBuffer[0])
            gl.glDrawElements((node as HasGeometry).geometryType.toOpenGLType(),
                    pCount,
                    GL.GL_UNSIGNED_INT,
                    pOffset.toLong())

            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0)
        } else {
            gl.glDrawArrays((node as HasGeometry).geometryType.toOpenGLType(), pOffset, pCount)
        }

        gl.gL3.glUseProgram(0)
    }
}