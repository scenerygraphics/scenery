package graphics.scenery.backends.vulkan

import graphics.scenery.ShaderMaterial
import graphics.scenery.textures.Texture
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.RendererFlags
import graphics.scenery.attribute.renderable.Renderable
import graphics.scenery.utils.lazyLogger
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.LinkedHashMap
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

/**
 * Vulkan Object State class. Saves texture, UBO, pipeline and vertex buffer state.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanRendererMetadata {
    protected val logger by lazyLogger()

    /**
     * Whether this metadata object has been fully initialised, now redirects to the respective
     * flag contained in [flags].
     */
    val initialized: Boolean
        get() = flags.contains(RendererFlags.Initialised)
    /** Indicates whether the mesh is using indexed vertex storage. */
    var isIndexed = false
    /** The number of indices stored. */
    var indexCount = 0
    /** The number of vertices stored. */
    var vertexCount = 0
    /** The number of instances the [graphics.scenery.Node] this metadata object is attached to has. */
    var instanceCount = 1

    /** Hash map storing necessary vertex buffers. */
    var geometryBuffers = ConcurrentHashMap<String, VulkanBuffer>()

    /** UBOs required by the [graphics.scenery.Node] this metadata object is attached to. */
    var UBOs = LinkedHashMap<String, Pair<Long, VulkanUBO>>()

    /** SSBOs required by the [graphics.scenery.Node] this metadata object is attached to. */
    var SSBOs = LinkedHashMap<String, Long>()
    var SSBOBuffers = LinkedHashMap<String, VulkanBuffer>()
    //TODO: Temp
    var inheritedDescriptors = HashMap<String, Long>()

    /** [VulkanTexture]s used by the [graphics.scenery.Node] this metadata object is attached to. */
    var textures = ConcurrentHashMap<String, VulkanTexture>()

    /** Material hash code, does not include textures. */
    var materialHashCode = 0

    /** Whether this [graphics.scenery.Node] will use any default textures for any of its texture slots. */
    var defaultTexturesFor = HashSet<String>()

    /** Descriptor sets required */
    var requiredDescriptorSets = HashMap<String, Long>()

    /** The vertex input type defining what are going to be inputs to the vertex shader. */
    var vertexInputType = VulkanRenderer.VertexDataKinds.PositionNormalTexcoord
    /** The vertex description, if necessary (can be null, e.g. for generative geometry). */
    var vertexDescription: VulkanRenderer.VertexDescription? = null

    /** Descriptor set for the textures this [graphics.scenery.Node] will be rendered with. */
    protected var textureDescriptorSets =  ConcurrentHashMap<Pair<String, String>, Long>()

    /** Time stamp of the last recreation of the texture descriptor sets */
    protected var descriptorSetsRecreated: Long = 0

    /** Set of [RendererFlags] to indicate state and status. */
    var flags: EnumSet<RendererFlags> = EnumSet.noneOf(RendererFlags::class.java)

    /** Last reload time for textures */
    var texturesLastSeen = 0L

    /**
     * Creates or updates the [textureDescriptorSets] describing the textures used. Will cover all the renderpasses
     * given in [passes]. The set will reside on [device] and the descriptor set layout(s) determined from the renderpass.
     * The set will be allocated from [descriptorPool].
     */
    fun texturesToDescriptorSets(device: VulkanDevice, passes: Map<String, VulkanRenderpass>, renderable: Renderable) {
        val updateDuration = measureTime {
            // this groups textures by the ones being members of the ObjectTextures array
            // and the ones being not.
            val textures = textures.entries.groupBy { Texture.objectTextures.contains(it.key) }
            val objectTextures = textures[true]
            val others = textures[false]

            passes.forEach { (passName, pass) ->
                if (pass.recreated > descriptorSetsRecreated) {
                    textureDescriptorSets.clear()
                }

                val descriptorSetLayoutObjectTextures = pass.descriptorSetLayouts["ObjectTextures"]
                if (descriptorSetLayoutObjectTextures != null && !objectTextures.isNullOrEmpty()) {
                    val specs = Texture.objectTextures.map { ot ->
                        val entry = objectTextures.first { it.key == ot }
                        Triple(entry.key, entry.value, -1L)
                    }

                    textureDescriptorSets[pass.passConfig.type.name to "ObjectTextures"] = createOrUpdateTextureDescriptorSet(
                        "ObjectTextures",
                        renderable,
                        pass,
                        specs,
                        descriptorSetLayoutObjectTextures,
                        device)
                } else {
                    // we warn only if the associated renderable is used in graphics mode, not in compute mode.
                    // compute mode nodes are not automatically assumed to have ObjectTextures available.
                    val renderableForCompute = (renderable.parent.materialOrNull() as? ShaderMaterial)?.isCompute() ?: false
                    if (pass.passConfig.type == RenderConfigReader.RenderpassType.geometry && !renderableForCompute) {
                        logger.warn("$this: DSL for ObjectTextures not found for pass $passName")
                    } else {
                        logger.debug("{}: DSL for ObjectTextures not found for pass {}", this, passName)
                    }
                }

                if (logger.isTraceEnabled) {
                    logger.trace("Pass descriptor sets are {}", pass.descriptorSetLayouts.keys.joinToString(","))
                }

                // non-ObjectTextures textures are handled separately,
                // a new descriptor set is created for each set declared in the shader.
                others?.mapNotNull { texture ->
                    pass.getDescriptorSetLayoutForTexture(texture.key, renderable)
                }?.forEach {(dsl, specList) ->
                    val sets = specList.groupBy { it.set }
                    sets.forEach { (set, specs) ->
                        val textureNames = specs.map { it.name }
                        val firstTextureName = textureNames.first()
                        val texturesForSet = specs
                            .sortedBy { it.binding }
                            .mapNotNull { textureSpec ->
                                val entry = others.firstOrNull { it.key == textureSpec.name }
                                if(entry != null) {
                                    Triple(entry.key, entry.value, textureSpec.binding)
                                } else {
                                    null
                                }
                            }

                        val ds = createOrUpdateTextureDescriptorSet(
                            firstTextureName,
                            renderable,
                            pass,
                            texturesForSet,
                            dsl,
                            device
                        )

                        texturesForSet.forEach { (textureName, _) ->
                            textureDescriptorSets[pass.passConfig.type.name to textureName] = ds
                            logger.debug("DS for $textureName (set=$set) is ${ds.toHexString()}, associated with $firstTextureName")
                        }
                    }
                }
            }
        }

        logger.trace("DS update took {} ms", updateDuration.toDouble(DurationUnit.MILLISECONDS))
    }

    fun clearTextureDescriptorSets() {
        textureDescriptorSets.clear()
    }

    private fun createOrUpdateTextureDescriptorSet(
        name: String,
        renderable: Renderable,
        pass: VulkanRenderpass,
        textures: List<Triple<String, VulkanTexture, Long>>,
        descriptorSetLayout: Long,
        device: VulkanDevice
    ): Long {
        val cacheKey = TextureKey(device.vulkanDevice, descriptorSetLayout, textures)
        val passName = pass.passConfig.type.name
        val pipeline = pass.getActivePipeline(renderable)

        val existing = cache[cacheKey]

        if(existing != null) {
            return existing
        }

        val descriptorSet: Long = textureDescriptorSets.getOrPut(passName to name) {
            val pDescriptorSetLayout = memAllocLong(1)
            pDescriptorSetLayout.put(0, descriptorSetLayout)

            val pool = device.findAvailableDescriptorPool(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            pool.decreaseAvailable(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)

            val allocInfo = VkDescriptorSetAllocateInfo.calloc()
                .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .pNext(NULL)
                .descriptorPool(pool.handle)
                .pSetLayouts(pDescriptorSetLayout)

            descriptorSetsRecreated = System.nanoTime()

            VU.getLong("vkAllocateDescriptorSets",
                { VK10.vkAllocateDescriptorSets(device.vulkanDevice, allocInfo, this) },
                { allocInfo.free(); memFree(pDescriptorSetLayout) })

        }

        val d = (1..textures.count()).map { VkDescriptorImageInfo.calloc(1) }.toTypedArray()
        val wd = VkWriteDescriptorSet.calloc(textures.count())
        var i = 0

        textures.forEach { (textureName, vulkanTexture, binding) ->
            val isStorageImage = pipeline.type == VulkanPipeline.PipelineType.Compute
                    && vulkanTexture.usage.contains(Texture.UsageType.LoadStoreImage)
            val (type, layout) = if(isStorageImage) {
                VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE to VK10.VK_IMAGE_LAYOUT_GENERAL
            } else {
                VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER to VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
            }

            d[i]
                .imageView(vulkanTexture.image.view)
                .sampler(vulkanTexture.image.sampler)
                .imageLayout(layout)

            wd[i]
                .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .pNext(NULL)
                .dstSet(descriptorSet)
                .pImageInfo(d[i])
                .descriptorType(type)
                .descriptorCount(1)

            // ObjectTextures is handled as an array texture, we set the array element here accordingly,
            // if the [name] given matches.
            if(name == "ObjectTextures") {
                wd[i].dstArrayElement(i)
            }

            // ObjectTextures will have an invalid (-1L) binding given, to indicate they're an array texture.
            // For other custom textures, the binding will be >= 0.
            if(binding >= 0) {
                wd[i].dstBinding(binding.toInt())
            }

            i++
        }

        VK10.vkUpdateDescriptorSets(device.vulkanDevice, wd, null)
        wd.free()
        d.forEach { it.free() }

        logger.debug("Creating texture descriptor for $name in pass $passName {} set with 1 bindings, DSL={}", descriptorSet.toHexString(), descriptorSetLayout.toHexString())
        cache[cacheKey] = descriptorSet
        return descriptorSet
    }

    /**
     * Returns the descriptor set named [textureSet] containing referring to the textures needed in a given [passname].
     * If [textureSet] is not found for [passname], null is returne
     */
    fun getTextureDescriptorSet(passname: String, textureSet: String = ""): Long? {
        val texture = if(textureSet == "") {
            "ObjectTextures"
        } else {
            textureSet
        }

        val set = textureDescriptorSets[passname to texture]
        if(set == null) {
            logger.warn("$this: Could not find descriptor set for pass $passname and texture set $texture")
//            logger.warn("DS are: ${textureDescriptorSets.keys().asSequence().joinToString { "${it.first} in ${it.second}" }}")
            logger.warn("DS are: ${textureDescriptorSets.keys().asSequence().groupBy { it.first }.entries.joinToString { "${it.key}: ${it.value.joinToString(", ") { ds -> ds.second }}" }}")
        }

        return set
    }
    private data class TextureKey(
        val device: VkDevice,
        val dsl: Long,
        val textures: List<Triple<String, VulkanTexture, Long>>
    )

    /**
     * Utility class for [VulkanRendererMetadata].
     */
    companion object {
        protected val logger by lazyLogger()

        private val cache = HashMap<TextureKey, Long>()

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
                else -> { logger.trace("Don't know how to determine slot for: {}", type); 0 }
            }
        }
    }
}
