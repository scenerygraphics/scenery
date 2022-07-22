package graphics.scenery.backends

import graphics.scenery.backends.vulkan.toHexString
import graphics.scenery.utils.LazyLogger
import org.joml.Vector3i
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.spvc.Spv.*
import org.lwjgl.util.spvc.Spvc
import org.lwjgl.util.spvc.SpvcBufferRange
import org.lwjgl.util.spvc.SpvcErrorCallback
import org.lwjgl.util.spvc.SpvcReflectedResource
import java.nio.IntBuffer

/**
 * Class for shader introspection. Needs to be handed SPIRV bytecode as the [spirv] parameter.
 * [vulkanSemantics] are enabled by default, and the default GLSL [version] is 450.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class ShaderIntrospection(
    val spirv: IntArray,
    val vulkanSemantics: Boolean = true,
    val version: Int = 450
): AutoCloseable {
    private val logger by LazyLogger()

    protected val resources: Long
    protected val compiler: Long
    protected var context: Long
    protected val bytecode: IntBuffer

    /**
     * Specification of UBO members, storing [name], [index] in the buffer, [offset] from the beginning,
     * and size of the member as [range].
     */
    data class UBOMemberSpec(
        val name: String,
        val index: Long,
        val offset: Long,
        val range: Long
    ) {
        /**
         * Returns a string representation of a [UBOMemberSpec], with all details.
         */
        override fun toString(): String {
            return "$name (index=$index, offset=$offset, range=$range)"
        }
    }

    /** Types an UBO can have */
    enum class UBOSpecType {
        UniformBuffer,
        SampledImage1D, SampledImage2D, SampledImage3D,
        Image1D, Image2D, Image3D,
        StorageBuffer, StorageBufferDynamic
    }

    /**
     * Specification of an UBO, storing [name], descriptor [set], [binding], [type], and a set of [members].
     * Can be an array, in that case, [size] > 1.
     */
    data class UBOSpec(
        val name: String,
        var set: Long,
        var binding: Long,
        val type: UBOSpecType,
        val members: LinkedHashMap<String, UBOMemberSpec>,
        val size: Int = 1
    ) {
        /**
         * Returns a string representation of a [UBOSpec] with all details.
         */
        override fun toString(): String {
            return "$name ($type[$size], set=$set, binding=$binding) members ${members.entries.joinToString { it.toString() }}"
        }
    }

    /**
     * Specification for push constants, containing [name] and [members].
     */
    data class PushConstantSpec(
        val name: String,
        val members: LinkedHashMap<String, UBOMemberSpec>
    )

    init {
        stackPush().use { stack ->
            logger.debug("Using spirv-cross, ${Spvc.spvc_get_commit_revision_and_timestamp()}")

            bytecode = MemoryUtil.memAllocInt(spirv.size)
            bytecode.put(spirv, 0, spirv.size)
            bytecode.flip()

            val contextPtr = stack.callocPointer(1)
            val compilerPtr = stack.callocPointer(1)
            val ir = stack.callocPointer(1)

            Spvc.spvc_context_create(contextPtr)
            context = contextPtr.get(0)

            val callback = object: SpvcErrorCallback() {
                /** Get notified in a callback when an error triggers. Useful for debugging.  */
                override fun invoke(userdata: Long, error: Long) {
                    logger.error(MemoryUtil.memUTF8(error))
                }

            }
            Spvc.spvc_context_set_error_callback(context, callback, 0)

            Spvc.spvc_context_parse_spirv(context, bytecode, bytecode.remaining().toLong(), ir)

            if(ir.get(0) == 0L) {
                throw ShaderIntrospectionException("Shader introspection returned NULL IR: ${Spvc.spvc_context_get_last_error_string(context) ?: "Unknown failure in shader introspection"}", vulkanSemantics, version)
            }

            Spvc.spvc_context_create_compiler(
                context,
                Spvc.SPVC_BACKEND_GLSL,
                ir.get(0),
                Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP,
                compilerPtr
            )
            logger.debug("Parsed ${bytecode.remaining().toLong()} SPIR-V ops on context ${context.toHexString()} and created compiler.")

            compiler = compilerPtr.get(0)

            val resourcesPtr = MemoryUtil.memAllocPointer(1)
            Spvc.spvc_compiler_create_shader_resources(compiler, resourcesPtr)
            resources = resourcesPtr.get(0)

            resourcesPtr.free()
        }
    }

    /**
     * Compiles the bytecode given in [spirv] to a string representation.
     */
    fun compile(): String {
        stackPush().use { stack ->
            logger.debug("Compiling SPIRV, version $version, with${if(!vulkanSemantics) { "out" } else { "" }} Vulkan semantics ...")
            val optionsPtr = stack.callocPointer(1)
            Spvc.spvc_compiler_create_compiler_options(compiler, optionsPtr)
            val options = optionsPtr.get(0)

            Spvc.spvc_compiler_options_set_uint(options, Spvc.SPVC_COMPILER_OPTION_GLSL_VERSION, version)
            Spvc.spvc_compiler_options_set_bool(options, Spvc.SPVC_COMPILER_OPTION_GLSL_ES, false)
            Spvc.spvc_compiler_options_set_bool(
                options,
                Spvc.SPVC_COMPILER_OPTION_GLSL_VULKAN_SEMANTICS,
                vulkanSemantics
            )
            Spvc.spvc_compiler_install_compiler_options(compiler, options)

            val result = stack.callocPointer(1)
            Spvc.spvc_compiler_compile(compiler, result)

            val shaderCode = MemoryUtil.memUTF8(result.get(0))
            logger.trace("Compiled code:\n{}", shaderCode)

            return shaderCode
        }
    }

    private fun resourceListForType(type: Int, converter: (SpvcReflectedResource) -> Any): List<Any> {
        stackPush().use { stack ->
            val list = stack.callocPointer(1)
            val count = stack.callocPointer(1)

            Spvc.spvc_resources_get_resource_list_for_type(resources, type, list, count)

            val reflectedResources = SpvcReflectedResource.create(list.get(0), count.get(0).toInt())
            val result = (0 until count.get(0).toInt()).map { i ->
                val resource = reflectedResources.get(i)
                converter.invoke(resource)
            }.toList()

            return result
        }
    }

    /**
     * Returns a list of specifications of uniforms buffer objects from what's given in [spirv].
     */
    fun uniformBuffers(): List<UBOSpec> {
        stackPush().use { stack ->
            val resources = resourceListForType(Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER) { resource ->
                val descriptorSet =
                    Spvc.spvc_compiler_get_decoration(compiler, resource.id(), SpvDecorationDescriptorSet)
                val binding = Spvc.spvc_compiler_get_decoration(compiler, resource.id(), SpvDecorationBinding)

                val ranges = stack.callocPointer(1)
                val rangesCount = stack.callocPointer(1)

                Spvc.spvc_compiler_get_active_buffer_ranges(compiler, resource.id(), ranges, rangesCount)

                val members = LinkedHashMap<String, UBOMemberSpec>()
                val uboRange = SpvcBufferRange.create(ranges.get(0), rangesCount.get(0).toInt())
                members.putAll((0 until uboRange.capacity()).map {
                    val memberRange = uboRange.get(it)
                    val name = Spvc.spvc_compiler_get_member_name(compiler, resource.base_type_id(), memberRange.index())!!

                    name to UBOMemberSpec(
                        name,
                        memberRange.index().toLong(),
                        memberRange.offset(),
                        memberRange.range()
                    )
                }.sortedBy { it.second.index })

                UBOSpec(
                    resource.nameString(),
                    set = descriptorSet.toLong(),
                    binding = binding.toLong(),
                    type = UBOSpecType.UniformBuffer,
                    members = members
                )
            } as List<UBOSpec>

            if(logger.isDebugEnabled) {
                logger.debug(
                    "Returning {} UBOs, {}",
                    resources.size,
                    resources.joinToString { it.toString() })
            }

            return resources
        }
    }

    /**
     * Returns a list of specifications of storage buffer objects from what's given in [spirv].
     */
    fun storageBuffers(): List<UBOSpec> {
        stackPush().use { stack ->
            val resources = resourceListForType(Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER) { resource ->
                val descriptorSet =
                    Spvc.spvc_compiler_get_decoration(compiler, resource.id(), SpvDecorationDescriptorSet)
                val binding = Spvc.spvc_compiler_get_decoration(compiler, resource.id(), SpvDecorationBinding)

                val ranges = stack.callocPointer(1)
                val rangesCount = stack.callocPointer(1)

                Spvc.spvc_compiler_get_active_buffer_ranges(compiler, resource.id(), ranges, rangesCount)

                val members = LinkedHashMap<String, UBOMemberSpec>()
                val uboRange = SpvcBufferRange.create(ranges.get(0), rangesCount.get(0).toInt())
                members.putAll((0 until uboRange.capacity()).map {
                    val memberRange = uboRange.get(it)
                    val name = Spvc.spvc_compiler_get_member_name(compiler, resource.base_type_id(), memberRange.index())!!

                    name to UBOMemberSpec(
                        name,
                        memberRange.index().toLong(),
                        memberRange.offset(),
                        memberRange.range()
                    )
                }.sortedBy { it.second.index })

                UBOSpec(
                    resource.nameString(),
                    set = descriptorSet.toLong(),
                    binding = binding.toLong(),
                    type = UBOSpecType.StorageBuffer,
                    members = members
                )
            } as List<UBOSpec>

            if(logger.isDebugEnabled) {
                logger.debug(
                    "Returning {} storage buffers, {}",
                    resources.size,
                    resources.joinToString { it.toString() })
            }

            return resources
        }
    }

    /**
     * Returns a list of specifications of push constants objects from what's given in [spirv].
     */
    fun pushContants(): List<PushConstantSpec> {
        stackPush().use { stack ->
            val pushConstants = resourceListForType(Spvc.SPVC_RESOURCE_TYPE_PUSH_CONSTANT) { resource ->
                val ranges = stack.callocPointer(1)
                val rangesCount = stack.callocPointer(1)

                Spvc.spvc_compiler_get_active_buffer_ranges(compiler, resource.id(), ranges, rangesCount)

                val members = LinkedHashMap<String, UBOMemberSpec>()
                val pcRange = SpvcBufferRange.create(ranges.get(0), rangesCount.get(0).toInt())
                members.putAll((0 until pcRange.capacity()).map {
                    val memberRange = pcRange.get(it)
                    val name = Spvc.spvc_compiler_get_member_name(compiler, resource.base_type_id(), memberRange.index())!!

                    name to UBOMemberSpec(
                        name,
                        memberRange.index().toLong(),
                        memberRange.offset(),
                        memberRange.range()
                    )
                }.sortedBy { it.second.index })

                PushConstantSpec(
                    resource.nameString(),
                    members = members
                )
            } as List<PushConstantSpec>

            if(logger.isDebugEnabled) {
                logger.debug(
                    "Returning {} push contants, {}",
                    pushConstants.size,
                    pushConstants.joinToString { it.toString() })
            }

            return pushConstants
        }
    }

    /**
     * Returns a list of specifications of sampled images from what's given in [spirv].
     */
    fun sampledImages(): List<UBOSpec> {
        val sampledImages = resourceListForType(Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE) { resource ->
            val descriptorSet = Spvc.spvc_compiler_get_decoration(compiler, resource.id(), SpvDecorationDescriptorSet)
            val binding = Spvc.spvc_compiler_get_decoration(compiler, resource.id(), SpvDecorationBinding)
            val type = Spvc.spvc_compiler_get_type_handle(compiler, resource.type_id())
            val arraySize = maxOf(Spvc.spvc_type_get_array_dimension(type, 0), 1)

            val samplerDim = Spvc.spvc_type_get_image_dimension(type)

            UBOSpec(
                resource.nameString(),
                set = descriptorSet.toLong(),
                binding = binding.toLong(),
                type = when(samplerDim) {
                    0 -> UBOSpecType.SampledImage1D
                    1 -> UBOSpecType.SampledImage2D
                    2 -> UBOSpecType.SampledImage3D
                    else -> throw IllegalArgumentException("samplerDim cannot be $samplerDim")
                },
                size = arraySize,
                members = LinkedHashMap()
            )
        } as List<UBOSpec>

        if(logger.isDebugEnabled) {
            logger.debug(
                "Returning {} sampled images, {}",
                sampledImages.size,
                sampledImages.joinToString { it.toString() })
        }

        return sampledImages
    }

    /**
     * Returns a list of specifications of storage images from what's given in [spirv].
     */
    fun storageImages(): List<UBOSpec> {
        val storageImages = resourceListForType(Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE) { resource ->
            val descriptorSet = Spvc.spvc_compiler_get_decoration(compiler, resource.id(), SpvDecorationDescriptorSet)
            val binding = Spvc.spvc_compiler_get_decoration(compiler, resource.id(), SpvDecorationBinding)
            val type = Spvc.spvc_compiler_get_type_handle(compiler, resource.type_id())
            val arraySize = Spvc.spvc_type_get_array_dimension(type, 0)

            val samplerDim = Spvc.spvc_type_get_image_dimension(type)

            UBOSpec(
                resource.nameString(),
                set = descriptorSet.toLong(),
                binding = binding.toLong(),
                type = when(samplerDim) {
                    0 -> UBOSpecType.Image1D
                    1 -> UBOSpecType.Image2D
                    2 -> UBOSpecType.Image3D
                    else -> throw IllegalArgumentException("samplerDim cannot be $samplerDim")
                },
                size = arraySize,
                members = LinkedHashMap()
            )
        } as List<UBOSpec>

        if(logger.isDebugEnabled) {
            logger.debug("Returning {} storage images, {}",
                storageImages.size,
                storageImages.joinToString { it.toString() })
        }

        return storageImages
    }

    /**
     * Returns the local sizes given in the (compute) shader.
     */
    fun localSizes(): Vector3i {
        return Vector3i(
            Spvc.spvc_compiler_get_execution_mode_argument_by_index(compiler, SpvExecutionModeLocalSize, 0),
            Spvc.spvc_compiler_get_execution_mode_argument_by_index(compiler, SpvExecutionModeLocalSize, 1),
            Spvc.spvc_compiler_get_execution_mode_argument_by_index(compiler, SpvExecutionModeLocalSize, 2)
        )
    }

    /**
     * Closes this SPIRVcross instance, freeing all resources.
     */
    override fun close() {
        Spvc.spvc_context_destroy(context)
        MemoryUtil.memFree(bytecode)
    }
}
