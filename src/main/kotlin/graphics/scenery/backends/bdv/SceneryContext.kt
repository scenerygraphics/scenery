package graphics.scenery.backends.bdv

import tpietzsch.backend.*
import tpietzsch.shadergen.Shader
import java.nio.Buffer
import java.util.function.BiFunction

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Tobias Pietzsch <pietzsch@mpi-cbg.de>
 */
class SceneryContext : GpuContext {
    override fun use(shader: Shader) {
        val f = BiFunction { i: Int, j: Int -> 4 }
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
