package graphics.scenery.volumes.bdv

import cleargl.GLMatrix
import cleargl.GLTypeEnum
import cleargl.GLVector
import graphics.scenery.GenericTexture
import graphics.scenery.ShaderMaterial
import graphics.scenery.TextureExtents
import graphics.scenery.backends.ShaderType
import graphics.scenery.utils.LazyLogger
import graphics.scenery.volumes.Volume
import org.lwjgl.system.MemoryUtil
import tpietzsch.backend.*
import tpietzsch.shadergen.Shader
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Tobias Pietzsch <pietzsch@mpi-cbg.de>
 */
class SceneryContext(val node: Volume) : GpuContext {
    private val logger by LazyLogger()

    private val pboBackingStore = HashMap<StagingBuffer, ByteBuffer>()
    val factory = VolumeShaderFactory()

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

        override fun setUniformMatrix3f(name: String, transpose: Boolean, value: FloatBuffer) {
            val array = FloatArray(value.remaining())
            value.get(array)

            val m = GLMatrix(array)
            if(transpose) {
                m.transpose()
            }

            node.shaderProperties[name] = m
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
        logger.info("Shader factory updating")
        factory.updateShaders(
            hashMapOf(
                ShaderType.VertexShader to shader,
                ShaderType.FragmentShader to shader))

//        node.material = ShaderMaterial(factory)
    }

    override fun getUniformSetter(shader: Shader): SetUniforms {
        return SceneryUniformSetter()
    }

    /**
     * @param pbo StagingBuffer to bind
     * @return id of previously bound pbo
     */
    override fun bindStagingBuffer(pbo: StagingBuffer): Int {
        logger.info("Binding PBO $pbo")
        return 0
    }

    /**
     * @param id pbo id to bind
     * @return id of previously bound pbo
     */
    override fun bindStagingBufferId(id: Int): Int {
        logger.info("Binding PBO $id")
        return id
    }

    /**
     * @param texture texture to bind
     * @return id of previously bound texture
     */
    override fun bindTexture(texture: Texture): Int {
        logger.info("Binding texture $texture")
        if(node.material.textures.contains("3D-volume")) {
            return 0
        }

        val (channels, type) = when(texture.texInternalFormat()) {
            Texture.InternalFormat.R16 -> 1 to GLTypeEnum.UnsignedShort
            Texture.InternalFormat.RGBA8UI -> 4 to GLTypeEnum.UnsignedByte
            Texture.InternalFormat.UNKNOWN -> TODO()
        }

        val repeat = when(texture.texWrap()) {
            Texture.Wrap.CLAMP_TO_EDGE -> false
            Texture.Wrap.REPEAT -> true
        }

        node.material.transferTextures.put("textureCache",
            GenericTexture("3D-volume",
                GLVector(texture.texWidth().toFloat(), texture.texHeight().toFloat(), texture.texDepth().toFloat()),
                channels,
                type,
                null,
                repeat, repeat, repeat,
                true,
                false))

        node.material.textures.put("3D-volume", "fromBuffer:textureCache")
        return 0
    }

    /**
     * @param texture texture to bind
     * @param unit texture unit to bind to
     */
    override fun bindTexture(texture: Texture, unit: Int) {
        logger.info("Binding texture $texture to unit $unit")
    }

    /**
     * @param id texture id to bind
     * @param numTexDimensions texture target: 1, 2, or 3
     * @return id of previously bound texture
     */
    override fun bindTextureId(id: Int, numTexDimensions: Int): Int {
        logger.info("Binding texture with id $id and dimensions=$numTexDimensions")
        return 0
    }

    override fun map(pbo: StagingBuffer): Buffer {
        logger.info("Mapping $pbo...")
        return pboBackingStore.computeIfAbsent(pbo) {
            MemoryUtil.memAlloc(pbo.sizeInBytes)
        }
    }

    override fun unmap(pbo: StagingBuffer) {
        logger.info("Unmapping $pbo...")
    }

    override fun delete(texture: Texture) {
        logger.info("Deleting texture")
    }

    override fun texSubImage3D(pbo: StagingBuffer, texture: Texture3D, xoffset: Int, yoffset: Int, zoffset: Int, width: Int, height: Int, depth: Int, pixels_buffer_offset: Long) {
        logger.info("Updating 3D texture from $texture: $xoffset $yoffset $zoffset $width $height $depth")
        val tmpStorage = (map(pbo) as ByteBuffer).duplicate().order(ByteOrder.LITTLE_ENDIAN)
        tmpStorage.position(pixels_buffer_offset.toInt())

        val channels = 1
        val format = GLTypeEnum.UnsignedShort

        node.material.transferTextures.put("subimage3D",
            GenericTexture("subimage3D",
                GLVector(width.toFloat(), height.toFloat(), depth.toFloat()),
                channels,
                format,
                tmpStorage,
                false,
                false,
                false,
                true,
                false,
                TextureExtents(xoffset, yoffset, zoffset, width, height, depth)))
    }

    override fun texSubImage3D(texture: Texture3D, xoffset: Int, yoffset: Int, zoffset: Int, width: Int, height: Int, depth: Int, pixels: Buffer) {
        logger.info("Updating 3D texture from $texture: $xoffset $yoffset $zoffset $width $height $depth")
        if(pixels is ByteBuffer) {
            // TODO: add support for different data types
            val channels = 1
            val format = GLTypeEnum.UnsignedShort

            node.material.transferTextures.put("subimage3D",
                GenericTexture("subimage3D",
                    GLVector(width.toFloat(), height.toFloat(), depth.toFloat()),
                    channels,
                    format,
                    pixels,
                    false,
                    false,
                    false,
                    true,
                    false,
                    TextureExtents(xoffset, yoffset, zoffset, width, height, depth)))
        }
    }
}
