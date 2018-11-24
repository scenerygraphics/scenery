package graphics.scenery.volumes.bdv

import cleargl.GLMatrix
import cleargl.GLTypeEnum
import cleargl.GLVector
import graphics.scenery.GenericTexture
import graphics.scenery.TextureExtents
import graphics.scenery.TextureUpdate
import graphics.scenery.backends.ShaderType
import graphics.scenery.utils.LazyLogger
import graphics.scenery.volumes.Volume
import org.lwjgl.system.MemoryUtil
import tpietzsch.backend.*
import tpietzsch.cache.TextureCache
import tpietzsch.shadergen.Shader
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Tobias Pietzsch <pietzsch@mpi-cbg.de>
 */
class SceneryContext(val node: Volume) : GpuContext {
    private val logger by LazyLogger()

    data class BindingState(var binding: Int, var uniformName: String?, var reallocate: Boolean = false)

    private val pboBackingStore = HashMap<StagingBuffer, ByteBuffer>()
    val factory = VolumeShaderFactory()
    var currentlyBound: GenericTexture? = null
    var currentlyBoundLuts = ConcurrentHashMap<String, GenericTexture>()
    var bindings = ConcurrentHashMap<Texture, BindingState>()

    inner class SceneryUniformSetter: SetUniforms {
        private var modified: Boolean = false
        override fun shouldSet(modified: Boolean): Boolean = modified

        override fun setUniform1i(name: String, v0: Int) {
//            logger.debug("Binding 1i $name to $v0")
            if(name.startsWith("volumeCache") || name.startsWith("lutSampler")) {
                val binding = bindings.entries.find { it.value.binding == v0 }
                if(binding != null) {
                    bindings[binding.key] = BindingState(v0, name)
                } else {
                    logger.warn("Binding for $name slot $v0 not found.")
                }
            } else {
                node.shaderProperties[name] = v0
            }
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
            // in UBOs, arrays of vectors need to be padded, such that they start on
            // word boundaries, e.g. a 3-vector needs to start on byte 16.
            val padded = ArrayList<Float>(4*count)
            value.asSequence().windowed(3, 3).forEach {
                padded.addAll(it)
                padded.add(0.0f)
            }

            val paddedArray = padded.toFloatArray()
            node.shaderProperties[name] = GLVector(*paddedArray)
            modified = true
        }

        override fun setUniformMatrix3f(name: String, transpose: Boolean, value: FloatBuffer) {
            val matrix = value.duplicate()
            if(matrix.position() == matrix.capacity()) {
                matrix.flip()
            }

            val array = FloatArray(matrix.remaining())
            matrix.get(array)

            val m = GLMatrix(array)
            if(transpose) {
                m.transpose()
            }

            node.shaderProperties[name] = m
            modified = true
        }

        override fun setUniformMatrix4f(name: String, transpose: Boolean, value: FloatBuffer) {
            val matrix = value.duplicate()
            if(matrix.position() == matrix.capacity()) {
                matrix.flip()
            }

            val array = FloatArray(matrix.remaining())
            matrix.get(array)

            val m = GLMatrix(array)
            if(transpose) {
                m.transpose()
            }

            node.shaderProperties[name] = m
            modified = true
        }
    }

    override fun use(shader: Shader) {
//        logger.info("Shader factory updating")
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
//        logger.info("Binding texture $texture, w=${texture.texWidth()} h=${texture.texHeight()} d=${texture.texDepth()}")
//        if(node.material.textures.contains("3D-volume")) {
//            return 0
//        }


        val (channels, type, normalized) = when(texture.texInternalFormat()) {
            Texture.InternalFormat.R16 -> Triple(1, GLTypeEnum.UnsignedShort, true)
            Texture.InternalFormat.RGBA8UI -> Triple(4, GLTypeEnum.UnsignedByte, false)
            Texture.InternalFormat.UNKNOWN -> TODO()
            else -> throw UnsupportedOperationException("Unknown internal format ${texture.texInternalFormat()}")
        }

        val repeat = when(texture.texWrap()) {
            Texture.Wrap.CLAMP_TO_EDGE -> false
            Texture.Wrap.REPEAT -> true
            else -> throw UnsupportedOperationException("Unknown wrapping mode: ${texture.texWrap()}")
        }

        if (texture is TextureCache) {
            if(currentlyBound != null && node.material.transferTextures.get("volumeCache") == currentlyBound) {
//                logger.info("Not rebinding, fitting cache $currentlyBound already bound")
                return 0
            }

            val gt = GenericTexture("volumeCache",
                GLVector(texture.texWidth().toFloat(), texture.texHeight().toFloat(), texture.texDepth().toFloat()),
                channels,
                type,
                null,
                repeat, repeat, repeat,
                normalized,
                false,
                minFilterLinear = true,
                maxFilterLinear = true)

            node.material.transferTextures.put("volumeCache", gt)

            currentlyBound = gt

            node.material.textures.put("volumeCache", "fromBuffer:volumeCache")
            node.material.needsTextureReload = true
        } else {
            val lutName = bindings[texture]?.uniformName

            val db = { lut: String ->
                if (node.material.transferTextures.get(lut) != null
                    && currentlyBoundLuts.get(lut) != null
                    && node.material.transferTextures.get(lut) == currentlyBoundLuts[lut]) {
//                    logger.info("Not rebinding, fitting LUT already bound")
                } else {
                    val gt = GenericTexture(lut,
                        GLVector(texture.texWidth().toFloat(), texture.texHeight().toFloat(), texture.texDepth().toFloat()),
                        channels,
                        type,
                        null,
                        repeat, repeat, repeat,
                        normalized,
                        false,
                        minFilterLinear = false,
                        maxFilterLinear = false)

                    node.material.transferTextures.put(lut, gt)

                    currentlyBoundLuts[lut] = gt

                    node.material.textures.put(lut, "fromBuffer:$lut")
                    node.material.needsTextureReload = true
                }
            }

            if(lutName == null) {
//                logger.debug("Could not determine binding for $texture, adding deferred binding")
                deferredBindings.put(texture, db)
                return -1
            } else {
                db.invoke(lutName)
            }
        }
        return 0
    }

    var deferredBindings = ConcurrentHashMap<Texture, (String) -> Unit>()

    fun runDeferredBindings() {
        val removals = ArrayList<Texture>(deferredBindings.size)

        deferredBindings.forEach { texture, func ->
            val binding = bindings[texture]
            val samplerName = binding?.uniformName
            if(binding != null && samplerName != null) {
                func.invoke(samplerName)
                removals.add(texture)
            } else {
                logger.error("Binding for $texture not found, despite trying deferred binding.")
            }
        }

        removals.forEach { deferredBindings.remove(it) }
    }

    /**
     * @param texture texture to bind
     * @param unit texture unit to bind to
     */
    override fun bindTexture(texture: Texture?, unit: Int) {
        if(texture != null) {
            val binding = bindings[texture]
            if(binding != null) {
                bindings[texture] = BindingState(unit, binding.uniformName)
            } else {
                bindings[texture] = BindingState(unit, null)
            }
        }

//        logger.info("Binding texture $texture to unit $unit")
    }

    /**
     * @param id texture id to bind
     * @param numTexDimensions texture target: 1, 2, or 3
     * @return id of previously bound texture
     */
    override fun bindTextureId(id: Int, numTexDimensions: Int): Int {
//        logger.info("Binding texture with id $id and dimensions=$numTexDimensions")
        return 0
    }

    override fun map(pbo: StagingBuffer): Buffer {
        logger.debug("Mapping $pbo... (${pboBackingStore.size} total)")
        return pboBackingStore.computeIfAbsent(pbo) {
            MemoryUtil.memAlloc(pbo.sizeInBytes)
        }
    }

    override fun unmap(pbo: StagingBuffer) {
        logger.debug("Unmapping $pbo...")
    }

    override fun delete(texture: Texture) {
        logger.debug("Marking $texture for reallocation")
        bindings[texture]?.reallocate = true
    }

    override fun texSubImage3D(pbo: StagingBuffer, texture: Texture3D, xoffset: Int, yoffset: Int, zoffset: Int, width: Int, height: Int, depth: Int, pixels_buffer_offset: Long) {
        logger.info("Updating 3D texture via PBO from $texture: dx=$xoffset dy=$yoffset dz=$zoffset w=$width h=$height d=$depth offset=$pixels_buffer_offset")
        val tex = bindings.entries.find { it.key == texture }
        if(tex == null) {
            logger.warn("No binding found for $texture")
            return
        }
        val texname = tex.value.uniformName

        if(texname == null) {
            logger.warn("Binding not initialiased for $texture")
            return
        }

        val tmpStorage = (map(pbo) as ByteBuffer).duplicate().order(ByteOrder.LITTLE_ENDIAN)
        tmpStorage.position(pixels_buffer_offset.toInt())

        val tmp = MemoryUtil.memCalloc(width*height*depth*2)
        tmpStorage.limit(tmpStorage.position() + width*height*depth*2)
        tmp.put(tmpStorage)
        tmp.flip()

        val gt = node.material.transferTextures[texname]

        if(tex.value.reallocate) {
            if (gt == null) {
                logger.warn("$texname does not have an associated GenericTexture, cannot reallocate.")
                return
            }

            gt.clearUpdates()
            gt.dimensions = GLVector(width.toFloat(), height.toFloat(), depth.toFloat())
            gt.contents = null
            gt.normalized = false

            val update = TextureUpdate(
                TextureExtents(xoffset, yoffset, zoffset, width, height, depth),
                tmp, deallocate = true)
            gt.updates.add(update)
        } else {
            if (gt == null) {
                logger.warn("$texname does not have an associated GenericTexture, cannot update.")
                return
            }

            val update = TextureUpdate(
                TextureExtents(xoffset, yoffset, zoffset, width, height, depth),
                tmp, deallocate = true)

            gt.updates.add(update)
        }

        node.material.textures[texname] = "fromBuffer:$texname"
        node.material.needsTextureReload = true
    }

    override fun texSubImage3D(texture: Texture3D, xoffset: Int, yoffset: Int, zoffset: Int, width: Int, height: Int, depth: Int, pixels: Buffer) {
        logger.info("Updating 3D texture via Texture3D from $texture: dx=$xoffset dy=$yoffset dz=$zoffset w=$width h=$height d=$depth")
        val tex = bindings.entries.find { it.key == texture }
        if(tex == null) {
            logger.warn("No binding found for $texture")
            return
        }
        val texname = tex.value.uniformName

        if(texname == null) {
            logger.warn("Binding not initialiased for $texture")
            return
        }

        if(pixels is ByteBuffer) {
            val p = pixels.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val tmp = MemoryUtil.memCalloc(width*height*depth*4)
            p.limit(p.position() + width*height*depth*4)
            tmp.put(p)
            tmp.flip()

            // TODO: add support for different data types
            val gt = node.material.transferTextures[texname]

            if(tex.value.reallocate) {
                if (gt == null) {
                    logger.warn("$texname does not have an associated GenericTexture, cannot reallocate.")
                    return
                }

                gt.clearUpdates()
                gt.dimensions = GLVector(width.toFloat(), height.toFloat(), depth.toFloat())
                gt.contents = null
                gt.normalized = false

                val update = TextureUpdate(
                    TextureExtents(xoffset, yoffset, zoffset, width, height, depth),
                    tmp, deallocate = true)
                gt.updates.add(update)
            } else {
                if (gt == null) {
                    logger.warn("$texname does not have an associated GenericTexture, cannot update.")
                    return
                }

                val update = TextureUpdate(
                    TextureExtents(xoffset, yoffset, zoffset, width, height, depth),
                    tmp, deallocate = true)

                gt.updates.add(update)
            }

            node.material.textures[texname] = "fromBuffer:$texname"
            node.material.needsTextureReload = true
        }
    }
}
