package scenery.rendermodules.opengl

import cleargl.GLError
import cleargl.GLProgram
import com.jogamp.opengl.GL
import scenery.GeometryType
import scenery.HasGeometry
import scenery.Node
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*

/**
 * Created by ulrik on 20/01/16.
 */

fun GeometryType.toOpenGLType(): Int {
    return when(this) {
        GeometryType.TRIANGLE_STRIP -> GL.GL_TRIANGLE_STRIP
        GeometryType.POLYGON -> GL.GL_TRIANGLES
        GeometryType.TRIANGLES -> GL.GL_TRIANGLES
        GeometryType.TRIANGLE_FAN -> GL.GL_TRIANGLE_FAN
        GeometryType.POINTS -> GL.GL_POINTS
    }
}

open class RenderGeometricalObject : OpenGLRenderModule {
    override var node: Node
    protected val additionalBufferIds = Hashtable<String, Int>()

    protected val mVertexArrayObject = IntArray(1)
    protected val mVertexBuffers = IntArray(3)
    protected val mIndexBuffer = IntArray(1)
    override var program: GLProgram?
    protected val gl: GL
    protected val geometryObject: HasGeometry

    var isDynamic = false
    var initialized: Boolean = false

    protected var mStoredIndexCount = 0
    protected var mStoredPrimitiveCount = 0

    protected val mId: Int

    constructor(gl: GL, program: GLProgram?, geometryObject: Node) {
        mId = -1
        this.gl = gl
        this.program = program
        this.geometryObject = geometryObject as HasGeometry
        this.node = geometryObject
    }

    override fun initialize(): Boolean {
        // generate VAO for attachment of VBO and indices
        gl.gL3.glGenVertexArrays(1, mVertexArrayObject, 0)

        // generate three VBOs for coords, normals, texcoords
        gl.glGenBuffers(3, mVertexBuffers, 0)
        gl.glGenBuffers(1, mIndexBuffer, 0)

        if (program == null) {
            program = GLProgram.buildProgram(gl, RenderGeometricalObject::class.java,
                    "shaders/Default.vs", "shaders/Default.fs")
        }

        setVerticesAndCreateBuffer(FloatBuffer.wrap(geometryObject.vertices))
        setNormalsAndCreateBuffer(FloatBuffer.wrap(geometryObject.normals))

        if(geometryObject.texcoords.size > 0) {
            setTextureCoordsAndCreateBuffer(FloatBuffer.wrap(geometryObject.texcoords))
        }

        if(geometryObject.indices.size > 0) {
            setIndicesAndCreateBuffer(IntBuffer.wrap(geometryObject.indices))
        }

        initialized = true
        return true
    }

    fun setVerticesAndCreateBuffer(pVertexBuffer: FloatBuffer) {
        mStoredPrimitiveCount = pVertexBuffer.remaining() / geometryObject.vertexSize

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
    }

    fun setArbitraryAndCreateBuffer(name: String,
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
    }

    fun updateVertices(pVertexBuffer: FloatBuffer) {
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
    }

    fun setNormalsAndCreateBuffer(pNormalBuffer: FloatBuffer) {
        gl.gL3.glBindVertexArray(mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, mVertexBuffers[1])

        gl.gL3.glEnableVertexAttribArray(1)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
                (pNormalBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pNormalBuffer,
                if (isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(1,
                geometryObject.vertexSize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }

    fun updateNormals(pNormalBuffer: FloatBuffer) {
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
    }

    fun setTextureCoordsAndCreateBuffer(pTextureCoordsBuffer: FloatBuffer) {
        gl.gL3.glBindVertexArray(mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, mVertexBuffers[2])

        gl.gL3.glEnableVertexAttribArray(2)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
                (pTextureCoordsBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pTextureCoordsBuffer,
                if (isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(2,
                geometryObject.texcoordSize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }

    fun updateTextureCoords(pTextureCoordsBuffer: FloatBuffer) {
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

    }

    fun setIndicesAndCreateBuffer(pIndexBuffer: IntBuffer) {

        mStoredIndexCount = pIndexBuffer.remaining()

        gl.gL3.glBindVertexArray(mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, mIndexBuffer[0])

        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER,
                (pIndexBuffer.limit() * (Integer.SIZE / java.lang.Byte.SIZE)).toLong(),
                pIndexBuffer,
                if (isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun updateIndices(pIndexBuffer: IntBuffer) {
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
    }

    override fun draw() {
        if (mStoredIndexCount > 0) {
            draw(0, mStoredIndexCount)
        } else {
            draw(0, mStoredPrimitiveCount)
        }
    }

    fun draw(pOffset: Int, pCount: Int) {
        program?.use(gl)

        gl.gL3.glBindVertexArray(mVertexArrayObject[0])

        if (mStoredIndexCount > 0) {
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER,
                    mIndexBuffer[0])
            gl.glDrawElements(geometryObject.geometryType.toOpenGLType(),
                    pCount,
                    GL.GL_UNSIGNED_INT,
                    pOffset.toLong())

            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0)
        } else {
            gl.glDrawArrays(geometryObject.geometryType.toOpenGLType(), pOffset, pCount)
        }

        gl.gL3.glUseProgram(0)
    }

    fun close() {
        gl.gL3.glDeleteVertexArrays(mVertexArrayObject.size,
                mVertexArrayObject,
                0)

        gl.glDeleteBuffers(mVertexBuffers.size, mVertexBuffers, 0)
        gl.glDeleteBuffers(mIndexBuffer.size, mIndexBuffer, 0)
    }

    fun getId(): Int {
        return mId
    }

    companion object {
        protected var counter = 0

        private fun printBuffer(buf: FloatBuffer) {
            buf.rewind()
            System.err.print(buf.toString() + ": ")
            for (i in 0..buf.remaining() - 1) {
                System.err.print(buf.get(i).toString() + " ")
            }

            System.err.println(" ")

            buf.rewind()
        }

        private fun printBuffer(buf: IntBuffer) {
            buf.rewind()
            System.err.print(buf.toString() + ": ")
            for (i in 0..buf.remaining() - 1) {
                System.err.print(buf.get(i).toString() + " ")
            }

            System.err.println(" ")

            buf.rewind()
        }
    }
}
