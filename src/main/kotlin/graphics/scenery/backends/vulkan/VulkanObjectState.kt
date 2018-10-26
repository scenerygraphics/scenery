package graphics.scenery.backends.vulkan

import graphics.scenery.NodeMetadata
import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryUtil.NULL
import vkk.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Vulkan Object State class. Saves texture, UBO, pipeline and vertex buffer state.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanObjectState : NodeMetadata {
    protected val logger by LazyLogger()

    /** Consumers for this metadata object. */
    override val consumers: MutableList<String> = ArrayList(setOf("VulkanRenderer"))

    /** Whether this metadata object has been fully initialised. */
    var initialized = false
    /** Indicates whether the mesh is using indexed vertex storage. */
    var isIndexed = false
    /** Indicates the offset to the indices in the vertex buffer in bytes. */
    var indexOffset = 0L
    /** The number of indices stored. */
    var indexCount = 0
    /** The number of vertices stored. */
    var vertexCount = 0
    /** The number of instances the [graphics.scenery.Node] this metadata object is attached to has. */
    var instanceCount = 1

    /** Hash map storing necessary vertex buffers. */
    var vertexBuffers = ConcurrentHashMap<String, VulkanBuffer>()

    /** UBOs required by the [graphics.scenery.Node] this metadata object is attached to. */
    var UBOs = LinkedHashMap<String, Pair<Long, VulkanUBO>>()

    /** [VulkanTexture]s used by the [graphics.scenery.Node] this metadata object is attached to. */
    var textures = ConcurrentHashMap<String, VulkanTexture>()

    /** Hash code for the blending options used in the last command buffer recording. */
    var blendingHashCode = 0

    /** Whether this [graphics.scenery.Node] will use any default textures for any of its texture slots. */
    var defaultTexturesFor = HashSet<String>()

    /** Descriptor sets required */
    var requiredDescriptorSets = HashMap<String, Long>()

    /** The vertex input type defining what are going to be inputs to the vertex shader. */
    var vertexInputType = VulkanRenderer.VertexDataKinds.PositionNormalTexcoord
    /** The vertex description, if necessary (can be null, e.g. for generative geometry). */
    var vertexDescription: VulkanRenderer.VertexDescription? = null

    /** Descriptor set for the textures this [graphics.scenery.Node] will be rendered with. */
    var textureDescriptorSet = VkDescriptorSet(NULL)
        protected set

    /**
     * Creates or updates the [textureDescriptorSet] describing the textures used.
     * The set will reside on [device] and obey layout [descriptorSetLayout]. The set will be allocated from
     * [descriptorPool] and refer a certain [targetBinding].
     */
    fun texturesToDescriptorSet(device: VulkanDevice,
                                descriptorSetLayout: VkDescriptorSetLayout,
                                descriptorPool: VkDescriptorPool,
                                targetBinding: Int = 0): VkDescriptorSet {

        if (textureDescriptorSet.L == NULL) {
            val allocInfo = vk.DescriptorSetAllocateInfo {
                this.descriptorPool = descriptorPool
                setLayout = descriptorSetLayout
            }
            textureDescriptorSet = device.vulkanDevice allocateDescriptorSets allocInfo
        }

        val d = vk.DescriptorImageInfo(textures.size)
        val wd = vk.WriteDescriptorSet(textures.size)

        var i = 0
        textures.forEach { type, texture ->
            d[i].apply {
                imageView = VkImageView(texture.image!!.view)
                sampler = VkSampler(texture.image!!.sampler)
                imageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
            }
            wd[i].apply {
                dstSet = textureDescriptorSet
                dstBinding = targetBinding + if ("3D" in type) 1 else 0
                dstArrayElement = textureTypeToSlot(type)
                descriptorType = VkDescriptorType.COMBINED_IMAGE_SAMPLER
                imageInfo_ = d[i]
            }
            i++
        }

        device.vulkanDevice updateDescriptorSets wd

        logger.debug("Creating texture descriptor {} set with 1 bindings, DSL={}", textureDescriptorSet.asHexString, descriptorSetLayout.asHexString)
        return textureDescriptorSet
    }

    /**
     * Utility class for [VulkanObjectState].
     */
    companion object {
        protected val logger by LazyLogger()

        /**
         * Returns the array index of a texture [type].
         */
        fun textureTypeToSlot(type: String): Int {
            return when (type) {
                "ambient" -> 0
                "diffuse" -> 1
                "specular" -> 2
                "normal" -> 3
                "alphamask" -> 4
                "displacement" -> 5
                "3D-volume" -> 0
                else -> {
                    logger.warn("Unknown texture type: $type"); 0
                }
            }
        }
    }
}
