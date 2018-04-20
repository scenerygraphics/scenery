package graphics.scenery.backends.vulkan

import glfw_.appBuffer
import graphics.scenery.NodeMetadata
import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.VK10
import vkn.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Vulkan Object State class. Saves texture, UBO, pipeline and vertex buffer state.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanObjectState : NodeMetadata {
    protected val logger by LazyLogger()

    override val consumers: MutableList<String> = ArrayList(setOf("VulkanRenderer"))

    var initialized = false
    var isIndexed = false
    var indexOffset = 0L
    var indexCount = 0
    var vertexCount = 0
    var instanceCount = 1

    var vertexBuffers = ConcurrentHashMap<String, VulkanBuffer>()

    var UBOs = LinkedHashMap<String, Pair<Long, VulkanUBO>>()

    var textures = ConcurrentHashMap<String, VulkanTexture>()

    var blendingHashCode = 0

    var defaultTexturesFor = HashSet<String>()

    var requiredDescriptorSets = HashMap<String, Long>()

    var vertexInputType = VulkanRenderer.VertexDataKinds.PositionNormalTexcoord
    var vertexDescription: VulkanRenderer.VertexDescription? = null

    var textureDescriptorSet: VkDescriptorSet = NULL

    fun texturesToDescriptorSet(device: VulkanDevice, descriptorSetLayout: VkDescriptorSetLayout, descriptorPool: VkDescriptorPool,
                                targetBinding: Int = 0): VkDescriptorSet {
        val descriptorSet: VkDescriptorSet = when (textureDescriptorSet) {
            NULL -> device.vulkanDevice allocateDescriptorSets vk.DescriptorSetAllocateInfo {
                this.descriptorPool = descriptorPool
                setLayouts = appBuffer longBufferOf descriptorSetLayout
            }
            else -> textureDescriptorSet
        }

        val wd = vk.WriteDescriptorSet(textures.count())
        var i = 0

        textures.forEach { type, texture ->
            val info = vk.DescriptorImageInfo(1) {
                imageView = texture.image!!.view
                sampler = texture.image!!.sampler
                imageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
            }
            wd[i++].apply {
                dstSet = descriptorSet
                dstBinding = targetBinding + if (type.contains("3D")) 1 else 0
                dstArrayElement = textureTypeToSlot(type)
                imageInfo = info
                descriptorType = VkDescriptorType.COMBINED_IMAGE_SAMPLER
            }
        }
        device.vulkanDevice updateDescriptorSets wd

        logger.debug("Creating texture descriptor {} set with 1 bindings, DSL={}", descriptorSet.toHexString(), descriptorSetLayout.toHexString())
        textureDescriptorSet = descriptorSet
        return descriptorSet
    }

    companion object {
        protected val logger by LazyLogger()

        fun textureTypeToSlot(type: String): Int {
            return when (type) {
                "ambient" -> 0
                "diffuse" -> 1
                "specular" -> 2
                "normal" -> 3
                "alphamask" -> 4 // TODO rename camelCase?
                "displacement" -> 5
                "3D-volume" -> 0
                else -> {
                    logger.warn("Unknown texture type: $type"); 0
                }
            }
        }
    }
}
