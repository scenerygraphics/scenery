package graphics.scenery.backends.bdv

import cleargl.GLMatrix
import cleargl.GLVector
import graphics.scenery.volumes.Volume
import tpietzsch.backend.*
import tpietzsch.shadergen.Shader
import java.nio.Buffer
import java.nio.FloatBuffer

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Tobias Pietzsch <pietzsch@mpi-cbg.de>
 */
class SceneryContext(val node: Volume) : GpuContext {

    inner class SceneryUniformSetter: SetUniforms {
        private var modified: Boolean = false
        override fun shouldSet(modified: Boolean): Boolean = modified

        override fun setUniform1i(name: String, v0: Int) {
            node.shaderProperties[name] = v0
            modified = true
        }

        override fun setUniform2i(name: String, v0: Int, v1: Int) {
            node.shaderProperties[name] = GLVector(v0.toFloat(), v1.toFloat())
            modified = true
        }

        override fun setUniform3i(name: String, v0: Int, v1: Int, v2: Int) {
            node.shaderProperties[name] = GLVector(v0.toFloat(), v1.toFloat(), v2.toFloat())
            modified = true
        }

        override fun setUniform4i(name: String, v0: Int, v1: Int, v2: Int, v3: Int) {
            node.shaderProperties[name] = GLVector(v0.toFloat(), v1.toFloat(), v2.toFloat(), v3.toFloat())
            modified = true
        }

        override fun setUniform1f(name: String, v0: Float) {
            node.shaderProperties[name] = v0
            modified = true
        }

        override fun setUniform2f(name: String, v0: Float, v1: Float) {
            node.shaderProperties[name] = GLVector(v0, v1)
            modified = true
        }

        override fun setUniform3f(name: String, v0: Float, v1: Float, v2: Float) {
            node.shaderProperties[name] = GLVector(v0, v1, v2)
            modified = true
        }

        override fun setUniform4f(name: String, v0: Float, v1: Float, v2: Float, v3: Float) {
            node.shaderProperties[name] = GLVector(v0, v1, v2, v3)
            modified = true
        }

        override fun setUniform1fv(name: String, count: Int, value: FloatArray) {
            node.shaderProperties[name] = GLVector(*value)
            modified = true
        }

        override fun setUniform2fv(name: String, count: Int, value: FloatArray) {
            node.shaderProperties[name] = GLVector(*value)
            modified = true
        }

        override fun setUniform3fv(name: String, count: Int, value: FloatArray) {
            node.shaderProperties[name] = GLVector(*value)
            modified = true
        }

        override fun setUniformMatrix4f(name: String, transpose: Boolean, value: FloatBuffer) {
            val array = FloatArray(value.remaining())
            value.get(array)

            val m = GLMatrix(array)
            if(transpose) {
                m.transpose()
            }

            node.shaderProperties[name] = m
            modified = true
        }
    }

    override fun use(shader: Shader) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getUniformSetter(shader: Shader): SetUniforms {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * @param pbo Pbo to bind
     * @return id of previously bound pbo
     */
    override fun bindPbo(pbo: Pbo): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * @param id pbo id to bind
     * @return id of previously bound pbo
     */
    override fun bindPboId(id: Int): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * @param texture texture to bind
     * @return id of previously bound texture
     */
    override fun bindTexture(texture: Texture): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * @param texture texture to bind
     * @param unit texture unit to bind to
     */
    override fun bindTexture(texture: Texture, unit: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * @param id texture id to bind
     * @param numTexDimensions texture target: 1, 2, or 3
     * @return id of previously bound texture
     */
    override fun bindTextureId(id: Int, numTexDimensions: Int): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun map(pbo: Pbo): Buffer {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unmap(pbo: Pbo) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun delete(texture: Texture) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun texSubImage3D(pbo: Pbo, texture: Texture3D, xoffset: Int, yoffset: Int, zoffset: Int, width: Int, height: Int, depth: Int, pixels_buffer_offset: Long) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun texSubImage3D(texture: Texture3D, xoffset: Int, yoffset: Int, zoffset: Int, width: Int, height: Int, depth: Int, pixels: Buffer?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
