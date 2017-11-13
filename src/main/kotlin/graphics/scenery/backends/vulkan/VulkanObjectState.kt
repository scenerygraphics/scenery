package graphics.scenery.backends.vulkan

import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import graphics.scenery.NodeMetadata
import graphics.scenery.utils.LazyLogger
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

    var textureDescriptorSet: Long = -1L

    private val currentInCommandBuffer = HashMap<VulkanCommandBuffer, Boolean>(3)

    fun setCommandBufferUpdated(commandBuffer: VulkanCommandBuffer, isUpdated: Boolean) {
        currentInCommandBuffer.put(commandBuffer, isUpdated)
    }

    fun setAllCommandBufferUpdated(isUpdated: Boolean) {
        currentInCommandBuffer.entries.forEach { it.setValue(isUpdated) }
    }

    fun isCurrentInCommandBuffer(commandBuffer: VulkanCommandBuffer): Boolean {
        return currentInCommandBuffer.getOrPut(commandBuffer, { true })
    }

    fun texturesToDescriptorSet(device: VulkanDevice, descriptorSetLayout: Long, descriptorPool: Long, targetBinding: Int = 0): Long {
        val descriptorSet = if(textureDescriptorSet == -1L) {
            val pDescriptorSetLayout = memAllocLong(1)
            pDescriptorSetLayout.put(0, descriptorSetLayout)

            val allocInfo = VkDescriptorSetAllocateInfo.calloc()
                .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .pNext(NULL)
                .descriptorPool(descriptorPool)
                .pSetLayouts(pDescriptorSetLayout)

            VU.getLong("vkAllocateDescriptorSets",
                { VK10.vkAllocateDescriptorSets(device.vulkanDevice, allocInfo, this) },
                { allocInfo.free(); memFree(pDescriptorSetLayout) })
        } else {
            textureDescriptorSet
        }

        val d = (1..textures.count()).map { VkDescriptorImageInfo.calloc(1) }.toTypedArray()
        val wd = VkWriteDescriptorSet.calloc(textures.count())
        var i = 0

        textures.forEach { type, texture ->
            d[i]
                .imageView(texture.image!!.view)
                .sampler(texture.image!!.sampler)
                .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            wd[i]
                .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .pNext(NULL)
                .dstSet(descriptorSet)
                .dstBinding(if(type.contains("3D")) { targetBinding+1 } else { targetBinding })
                .dstArrayElement(textureTypeToSlot(type))
                .pImageInfo(d[i])
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)

            i++
        }

        VK10.vkUpdateDescriptorSets(device.vulkanDevice, wd, null)
        wd.free()
        d.forEach { it.free() }

        logger.trace("Creating texture descriptor $descriptorSet set with 1 bindings, DSL=$descriptorSetLayout")
        this.textureDescriptorSet = descriptorSet
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
                "alphamask" -> 4
                "displacement" -> 5
                "3D-volume" -> 0
                else -> { logger.warn("Unknown texture type: $type"); 0 }
            }
        }
    }
}
