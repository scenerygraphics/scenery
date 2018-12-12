package graphics.scenery.backends.vulkan

import graphics.scenery.GenericTexture
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import graphics.scenery.NodeMetadata
import graphics.scenery.utils.LazyLogger
import vkk.*
import vkk.entities.*
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
    var indexOffset = VkDeviceSize(0)
    /** The number of indices stored. */
    var indexCount = 0
    /** The number of vertices stored. */
    var vertexCount = 0
    /** The number of instances the [graphics.scenery.Node] this metadata object is attached to has. */
    var instanceCount = 1

    /** Hash map storing necessary vertex buffers. */
    var vertexBuffers = ConcurrentHashMap<String, VulkanBuffer>()

    /** UBOs required by the [graphics.scenery.Node] this metadata object is attached to. */
    var UBOs = LinkedHashMap<String, Pair<VkDescriptorSet, VulkanUBO>>()

    /** [VulkanTexture]s used by the [graphics.scenery.Node] this metadata object is attached to. */
    var textures = ConcurrentHashMap<String, VulkanTexture>()

    /** Hash code for the blending options used in the last command buffer recording. */
    var blendingHashCode = 0

    /** Whether this [graphics.scenery.Node] will use any default textures for any of its texture slots. */
    var defaultTexturesFor = HashSet<String>()

    /** Descriptor sets required */
    var requiredDescriptorSets = HashMap<String, VkDescriptorSet>()

    /** The vertex input type defining what are going to be inputs to the vertex shader. */
    var vertexInputType = VulkanRenderer.VertexDataKinds.PositionNormalTexcoord
    /** The vertex description, if necessary (can be null, e.g. for generative geometry). */
    var vertexDescription: VulkanRenderer.VertexDescription? = null

    /** Descriptor set for the textures this [graphics.scenery.Node] will be rendered with. */
    protected var textureDescriptorSets = ConcurrentHashMap<Pair<String, String>, VkDescriptorSet>()

    /** Time stamp of the last recreation of the texture descriptor sets */
    protected var descriptorSetsRecreated: Long = 0

    /**
     * Creates or updates the [textureDescriptorSets] describing the textures used. Will cover all the renderpasses
     * given in [passes]. The set will reside on [device] and the descriptor set layout(s) determined from the renderpass.
     * The set will be allocated from [descriptorPool].
     */
    fun texturesToDescriptorSets(device: VulkanDevice, passes: Map<String, VulkanRenderpass>, descriptorPool: VkDescriptorPool) {
        passes.forEach { passName, pass ->
            if (pass.recreated > descriptorSetsRecreated) {
                textureDescriptorSets.clear()
            }
            val textures = textures.entries.groupBy { GenericTexture.objectTextures.contains(it.key) }
            val objectTextures = textures[true]
            val others = textures[false]

            val descriptorSetLayoutObjectTextures = pass.descriptorSetLayouts["ObjectTextures"]
            if (descriptorSetLayoutObjectTextures != null && objectTextures != null) {
                textureDescriptorSets[passName to "ObjectTextures"] = createOrUpdateTextureDescriptorSet(
                    "ObjectTextures",
                    passName,
                    GenericTexture.objectTextures.map { ot -> objectTextures.first { it.key == ot } },
                    descriptorSetLayoutObjectTextures,
                    device,
                    descriptorPool)
            } else {
                logger.warn("$this: DSL for ObjectTextures not found for pass $passName")
            }

            others?.forEach { texture ->
                logger.trace("Pass descriptor sets are {}", pass.descriptorSetLayouts.keys.joinToString(","))
                val dsl = pass.descriptorSetLayouts[texture.key]
                if (dsl != null) {
                    textureDescriptorSets[passName to texture.key] = createOrUpdateTextureDescriptorSet(
                        texture.key,
                        passName,
                        listOf(texture),
                        dsl,
                        device,
                        descriptorPool)
                } else {
                    logger.warn("$this: DSL for ${texture.key} not found for pass $passName")
                }
            }
        }
    }

    private fun createOrUpdateTextureDescriptorSet(name: String, passName: String, textures: List<MutableMap.MutableEntry<String, VulkanTexture>>,
                                                   descriptorSetLayout: VkDescriptorSetLayout, device: VulkanDevice, descriptorPool: VkDescriptorPool): VkDescriptorSet {
        val descriptorSet = textureDescriptorSets[passName to name] ?: run {
            descriptorSetsRecreated = System.nanoTime()
            val result = device.vulkanDevice allocateDescriptorSets vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)
            textureDescriptorSets[passName to name] = result
            result
        }

        val d = (1..textures.count()).map { vk.DescriptorImageInfo() }.toTypedArray()
        val wd = vk.WriteDescriptorSet(textures.count())
        var i = 0

        textures.forEach { texture ->
            d[i].apply {
                imageView = texture.value.image.view
                sampler = texture.value.image.sampler
                imageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
            }
            wd[i].apply {
                dstSet = descriptorSet
                dstBinding = 0
                dstArrayElement = i
                imageInfo_ = d[i]
                descriptorType = VkDescriptorType.COMBINED_IMAGE_SAMPLER
            }
            i++
        }

        device.vulkanDevice updateDescriptorSets wd

        logger.debug("Creating texture descriptor for $name in pass $passName {} set with 1 bindings, DSL={}", descriptorSet.asHexString, descriptorSetLayout.asHexString)
        return descriptorSet
    }

    /**
     * Returns the descriptor set named [textureSet] containing referring to the textures needed in a given [passname].
     * If [textureSet] is not found for [passname], null is returned.
     */
    fun getTextureDescriptorSet(passname: String, textureSet: String = ""): VkDescriptorSet? {
        val texture = if (textureSet == "") {
            "ObjectTextures"
        } else {
            textureSet
        }

        val set = textureDescriptorSets[passname to texture]
        if (set == null) {
            logger.warn("$this: Could not find descriptor set for $passname and texture set $texture")
            logger.warn("DS are: ${textureDescriptorSets.keys().asSequence().joinToString { "${it.first} in ${it.second}" }}")
        }

        return set
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
                else -> 0.also {
                    logger.trace("Don't know how to determine slot for: {}", type)
                }
            }
        }
    }
}
