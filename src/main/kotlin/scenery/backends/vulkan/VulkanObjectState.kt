package scenery.backends.vulkan

import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.NativeResource
import org.lwjgl.vulkan.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scenery.NodeMetadata
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by ulrik on 9/27/2016.
 */
class VulkanObjectState : NodeMetadata {
    protected var logger: Logger = LoggerFactory.getLogger("VulkanRenderer")
    override val consumers: MutableList<String> = ArrayList()

    var initialized = false
    var isIndexed = false
    var indexOffset = 0
    var indexCount = 0
    var vertexCount = 0

    var vertexBuffers = ConcurrentHashMap<String, VulkanBuffer>()

    var pipeline = VulkanRenderer.Pipeline()

    var UBOs = ConcurrentHashMap<String, UBO>()

    var textures = ConcurrentHashMap<String, VulkanTexture>()

    var requiredDescriptorSets = ArrayList<String>()

    constructor() {
        consumers.add("VulkanRenderer")
    }

    var textureDescriptorSet: Long = -1L

    fun texturesToDescriptorSet(device: VkDevice, descriptorSetLayout: Long, descriptorPool: Long, targetBinding: Int = 0): Long {
        val pDescriptorSetLayout = memAllocLong(1)
        pDescriptorSetLayout.put(0, descriptorSetLayout)

        val allocInfo = VkDescriptorSetAllocateInfo.calloc()
            .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
            .pNext(NULL)
            .descriptorPool(descriptorPool)
            .pSetLayouts(pDescriptorSetLayout)

        val descriptorSet = VU.run(memAllocLong(1), "vkAllocateDescriptorSets",
            { VK10.vkAllocateDescriptorSets(device, allocInfo, this) },
            { allocInfo.free(); memFree(pDescriptorSetLayout) })

        val d = VkDescriptorImageInfo.calloc(textures.count())
        val wd = VkWriteDescriptorSet.calloc(textures.count())
        var i = 0

        textures.forEach { type, texture ->
            d[i]
                .imageView(texture.image!!.view)
                .sampler(texture.image!!.sampler)
                .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            val dd = VkDescriptorImageInfo.calloc(1)
                dd.put(0, d[i])

//            logger.info("Will put $type into ${toVulkanSlot(type)}, $texture")
            wd[i]
                .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .pNext(NULL)
                .dstSet(descriptorSet)
                .dstBinding(targetBinding)
                .dstArrayElement(toVulkanSlot(type))
                .pImageInfo(dd)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)

            i++
        }

        VK10.vkUpdateDescriptorSets(device, wd, null)
        wd.free()
        (d as NativeResource).free()

        logger.trace("Creating texture descriptor $descriptorSet set with 1 bindings, DSL=$descriptorSetLayout")
        this.textureDescriptorSet = descriptorSet
        return descriptorSet
    }

    fun toVulkanSlot(type: String): Int {
        return when (type) {
            "ambient" -> 0
            "diffuse" -> 1
            "specular" -> 2
            "normal" -> 3
            "displacement" -> 4
            else -> 0
        }
    }
}
