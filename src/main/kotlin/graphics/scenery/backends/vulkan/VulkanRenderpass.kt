package graphics.scenery.backends.vulkan

import cleargl.GLVector
import graphics.scenery.GeometryType
import graphics.scenery.Node
import graphics.scenery.Settings
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.ShaderNotFoundException
import graphics.scenery.backends.ShaderType
import graphics.scenery.backends.Shaders
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.RingBuffer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Class to encapsulate a Vulkan renderpass with [name] and associated [RenderConfigReader.RenderConfig] [config].
 * The renderpass will be created on [device], with descriptors being allocated from [descriptorPool].
 * A [pipelineCache] can be used for performance gains. The available vertex descriptors need to be handed
 * over in [vertexDescriptors].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanRenderpass(val name: String, var config: RenderConfigReader.RenderConfig,
                       val device: VulkanDevice,
                       val descriptorPool: Long,
                       val pipelineCache: Long,
                       val vertexDescriptors: ConcurrentHashMap<VulkanRenderer.VertexDataKinds, VulkanRenderer.VertexDescription>): AutoCloseable {

    protected val logger by LazyLogger()

    /** [VulkanFramebuffer] inputs of this render pass */
    val inputs = ConcurrentHashMap<String, VulkanFramebuffer>()
    /** [VulkanFramebuffer] outputs of this render pass */
    val output = ConcurrentHashMap<String, VulkanFramebuffer>()

    /** The pipelines this renderpass contains */
    var pipelines = ConcurrentHashMap<String, VulkanPipeline>()
        protected set
    /** The UBOs this renderpass contains, e.g. for storage of shader parameters */
    var UBOs = ConcurrentHashMap<String, VulkanUBO>()
        protected set
    /** Descriptor sets needed */
    var descriptorSets = ConcurrentHashMap<String, Long>()
        protected set
    /** Descriptor set layouts needed */
    var descriptorSetLayouts = LinkedHashMap<String, Long>()
        protected set
    protected var oldDescriptorSetLayouts = LinkedHashMap<String, Long>()

    /** Semaphores this renderpass is going to wait on when executed */
    var waitSemaphores = memAllocLong(1)
        protected set
    /** Stages this renderpass will wait for when executed */
    var waitStages = memAllocInt(1)
        protected set
    /** Semaphores this renderpass is going to signal after finishing execution */
    var signalSemaphores = memAllocLong(1)
        protected set
    /** Pointers to command buffers associated with running this renderpass */
    var submitCommandBuffers = memAllocPointer(1)
        protected set

    /**
     * The command buffer associated with this render pass. Command buffers
     * are usually multi-buffered, their backing store is contained in the [RingBuffer]
     * [commandBufferBacking]. When requesting this command buffer, the ring buffer
     * will hop forward one, such that the next request will return the next command buffer
     * in the ring buffer, and so on. Be sure to only request a command buffer once, then
     * store the result and use this.
     * */
    var commandBuffer: VulkanCommandBuffer
        get() {
            return commandBufferBacking.get()
        }

        set(b) {
            commandBufferBacking.put(b)
        }

    private var commandBufferBacking = RingBuffer(size = 3,
        default = { VulkanCommandBuffer(device, null, true) })

    /** This renderpasses' semaphore */
    var semaphore = -1L
        protected set

    /** This renderpasses' [RenderConfigReader.RenderpassConfig]. */
    var passConfig: RenderConfigReader.RenderpassConfig = config.renderpasses.get(name)!!
        protected set

    /** Whether this renderpass will render to the viewport or to a [VulkanFramebuffer] */
    var isViewportRenderpass = false

    /** The number of command buffers to keep in the [RingBuffer] [commandBufferBacking]. */
    var commandBufferCount = 3
        set(count) {
            // clean up old backing
            (1..commandBufferBacking.size).forEach { commandBufferBacking.get().close() }
            commandBufferBacking.reset()

            field = count

            commandBufferBacking = RingBuffer(size = count,
                default = { VulkanCommandBuffer(device, null, true) })
        }

    /** Timestamp of the renderpass recreation */
    var recreated: Long = 0
        protected set

    private var currentPosition = 0

    /**
     * Vulkan metadata class, keeping information about viewports, scissor areas, etc.
     */
    class VulkanMetadata(var descriptorSets: LongBuffer = memAllocLong(10),
                              var vertexBufferOffsets: LongBuffer = memAllocLong(4),
                              var scissor: VkRect2D.Buffer = VkRect2D.calloc(1),
                              var viewport: VkViewport.Buffer = VkViewport.calloc(1),
                              var vertexBuffers: LongBuffer = memAllocLong(4),
                              var clearValues: VkClearValue.Buffer? = null,
                              var renderArea: VkRect2D = VkRect2D.calloc(),
                              var renderPassBeginInfo: VkRenderPassBeginInfo = VkRenderPassBeginInfo.calloc(),
                              var uboOffsets: IntBuffer = memAllocInt(16),
                              var eye: IntBuffer = memAllocInt(1),
                              var renderLists: HashMap<VulkanCommandBuffer, Array<Node>> = HashMap()): AutoCloseable {

        /** Close this metadata instance, and frees all members */
        override fun close() {
            memFree(descriptorSets)
            memFree(vertexBufferOffsets)
            scissor.free()
            viewport.free()
            memFree(vertexBuffers)
            clearValues?.free()
            renderArea.free()
            renderPassBeginInfo.free()
            memFree(uboOffsets)
            memFree(eye)
        }

    }

    /** [VulkanMetadata] for this renderpass */
    var vulkanMetadata = VulkanMetadata()
        protected set

    init {
        val semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
            .pNext(NULL)
            .flags(0)

        semaphore = VU.getLong("vkCreateSemaphore",
            { vkCreateSemaphore(device.vulkanDevice, semaphoreCreateInfo, null, this) }, {})

        val default = VU.createDescriptorSetLayout(device,
            descriptorNum = 3,
            descriptorCount = 1)

        descriptorSetLayouts.put("default", default)

        val lightParameters = VU.createDescriptorSetLayout(
            device,
            listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1)),
            binding = 0, shaderStages = VK_SHADER_STAGE_ALL)

        descriptorSetLayouts.put("LightParameters", lightParameters)

        val dslObjectTextures = VU.createDescriptorSetLayout(
            device,
            listOf(Pair(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 6),
                Pair(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1)),
            binding = 0, shaderStages = VK_SHADER_STAGE_ALL)

        descriptorSetLayouts.put("ObjectTextures", dslObjectTextures)

        val dslVRParameters = VU.createDescriptorSetLayout(
            device,
            listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1)),
            binding = 0, shaderStages = VK_SHADER_STAGE_ALL)

        descriptorSetLayouts.put("VRParameters", dslVRParameters)

        recreated = System.nanoTime()
    }

    /**
     * Initialises descriptor set layouts coming for the passes' [inputs].
     */
    fun initializeInputAttachmentDescriptorSetLayouts(shaderModules: List<VulkanShaderModule>) {
        var input = 0
        logger.debug("Renderpass $name has inputs ${inputs.keys.joinToString(", ")}")
        inputs.entries.reversed().forEach { inputFramebuffer ->
            // we need to discern here whether the entire framebuffer is the input, or
            // only a part of it (indicated by a dot in the name)
            val descriptorNum = if(inputFramebuffer.key.contains(".")) {
                1
            } else {
                inputFramebuffer.value.attachments.count()
            }

            // create descriptor set layout that matches the render target
            val dsl = VU.createDescriptorSetLayout(device,
                descriptorNum = descriptorNum,
                descriptorCount = 1,
                type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
            )

            val ds = if(inputFramebuffer.key.contains(".")) {
                val targetName = inputFramebuffer.key.substringBefore(".")
                val attachmentName = inputFramebuffer.key.substringAfter(".")

                val rendertarget = config.rendertargets[targetName] ?: throw IllegalStateException("Rendertargets do not contain required target ${inputFramebuffer.key}")

                VU.createRenderTargetDescriptorSet(device, descriptorPool, dsl,
                    rendertarget.attachments,
                    inputFramebuffer.value, attachmentName)
            } else {
                inputFramebuffer.value.outputDescriptorSet
            }

            val searchKeys = if(inputFramebuffer.key.contains(".")) {
                listOf(inputFramebuffer.key.substringAfter("."))
            } else {
                config.rendertargets[inputFramebuffer.key]?.attachments?.keys ?: throw IllegalStateException("Rendertargets do not contain required target ${inputFramebuffer.key}")
            }

            logger.debug("Search keys for input attachments: ${searchKeys.joinToString(",")}")

            val spec = shaderModules.flatMap { it.uboSpecs.entries }.firstOrNull { entry ->
                entry.component2().members.count() == descriptorNum
                && entry.component1().startsWith("Inputs")
                && searchKeys.map { entry.component2().members.containsKey("Input$it") }.all { it == true }
            }

            if(spec != null) {
                val inputKey = "input-${this.name}-${spec.value.set}"

                logger.debug("${this.name}: Creating input descriptor set for ${inputFramebuffer.key}, $inputKey")
                descriptorSetLayouts.put(inputKey, dsl)?.let { oldDSL -> vkDestroyDescriptorSetLayout(device.vulkanDevice, oldDSL, null) }
                descriptorSets.put(inputKey, ds)
                input++
            } else {
                logger.warn("$name: Shader does not use input of ${inputFramebuffer.key}")
            }
        }
    }

    /**
     * Initialiases descriptor set layours associated with this passes' shader parameters.
     */
    fun initializeShaderParameterDescriptorSetLayouts(settings: Settings) {
        // renderpasses might have parameters set up in their YAML config. These get translated to
        // descriptor layouts, UBOs and descriptor sets
        passConfig.parameters?.let { params ->
            logger.debug("Creating VulkanUBO for $name")
            // create UBO
            val ubo = VulkanUBO(device)

            ubo.name = "ShaderParameters-$name"
            params.forEach { entry ->
                // Entry could be created in Java, so we check for both Java and Kotlin strings
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                val value = if (entry.value is String || entry.value is java.lang.String) {
                    val s = entry.value as String
                    GLVector(*(s.split(",").map { it.trim().trimStart().toFloat() }.toFloatArray()))
                } else if (entry.value is Double) {
                    (entry.value as Double).toFloat()
                } else {
                    entry.value
                }

                val settingsKey = when {
                    entry.key.startsWith("Global") -> "Renderer.${entry.key.substringAfter("Global.")}"
                    entry.key.startsWith("Pass") -> "Renderer.$name.${entry.key.substringAfter("Pass.")}"
                    else -> "Renderer.$name.${entry.key}"
                }

                if(!entry.key.startsWith("Global.") && !entry.key.startsWith("Pass.")) {
                    settings.setIfUnset(settingsKey, value)
                }

                ubo.add(entry.key, { settings.get(settingsKey) })
            }

            if(logger.isDebugEnabled) {
                logger.debug("Members are: {}", ubo.membersAndContent())
                logger.debug("Allocating VulkanUBO memory now, space needed: {}", ubo.getSize())
            }

            ubo.createUniformBuffer()

            // create descriptor set layout
//            val dsl = VU.createDescriptorSetLayout(device,
//                VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, 1)
            val dsl = VU.createDescriptorSetLayout(device,
                listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1)),
                0, VK_SHADER_STAGE_ALL)

            val ds = VU.createDescriptorSet(device, descriptorPool, dsl,
            1, ubo.descriptor, type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)

            // populate descriptor set
            ubo.populate()

            UBOs.put("ShaderParameters-$name", ubo)
            descriptorSets.put("ShaderParameters-$name", ds)

            logger.debug("Created DSL $dsl for $name, VulkanUBO has ${params.count()} members")
            descriptorSetLayouts.putIfAbsent("ShaderParameters-$name", dsl)
        }
    }

    /**
     * Initialiases descriptor set layouts for [graphics.scenery.ShaderProperty]s.
     */
    fun initializeShaderPropertyDescriptorSetLayout(): Long {
        // this creates a shader property UBO for items marked @ShaderProperty in node
        val alreadyCreated = descriptorSetLayouts.containsKey("ShaderProperties-$name")

        val dsl = if(!alreadyCreated) {
            // create descriptor set layout
            val dsl = VU.createDescriptorSetLayout(
                device,
                listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1)),
                binding = 0, shaderStages = VK_SHADER_STAGE_ALL)

            logger.debug("Created Shader Property DSL ${dsl.toHexString()} for $name")
            descriptorSetLayouts.putIfAbsent("ShaderProperties-$name", dsl)
            dsl
        } else {
            descriptorSetLayouts.getOrElse("ShaderProperties-$name") {
                throw IllegalStateException("ShaderProperties-$name does not exist in descriptor set layouts for $this.")
            }
        }

        // returns a ordered list of the members of the ShaderProperties struct
        return dsl
    }

    /**
     * Returns the order of shader properties as a map for a given [node] as required by the shader file.
     */
    fun getShaderPropertyOrder(node: Node): Map<String, Int> {
        // this creates a shader property UBO for items marked @ShaderProperty in node
        logger.debug("specs: ${this.pipelines["preferred-${node.uuid}"]!!.descriptorSpecs}")
        val shaderPropertiesSpec = this.pipelines["preferred-${node.uuid}"]!!.descriptorSpecs.filter { it.key == "ShaderProperties" }.map { it.value.members }

        if(shaderPropertiesSpec.count() == 0) {
            logger.debug("Warning: Shader file uses no declared shader properties, despite the class declaring them.")
            return emptyMap()
        }

        // returns a ordered list of the members of the ShaderProperties struct
        return shaderPropertiesSpec
            .flatMap { it.values }
            .map { it.name to it.offset.toInt() }
            .toMap()
    }

    /**
     * Updates all shader parameters.
     */
    fun updateShaderParameters() {
        UBOs.forEach { uboName, ubo ->
            if(uboName.startsWith("ShaderParameters-")) {
                ubo.populate()
            }
        }
    }

    /**
     * Initialiases the default [VulkanPipeline] for this renderpass.
     */
    fun initializeDefaultPipeline() {
        val shaders = Shaders.ShadersFromFiles(passConfig.shaders.map { "shaders/$it" }.toTypedArray())
        val shaderModules = ShaderType.values().mapNotNull { type ->
            try {
                VulkanShaderModule.getFromCacheOrCreate(device, "main", shaders.get(Shaders.ShaderTarget.Vulkan, type))
            } catch (e: ShaderNotFoundException) {
                logger.debug("Shader not found: $type - this is normal if there are no errors reported")
                null
            }
        }

        initializeInputAttachmentDescriptorSetLayouts(shaderModules)
        initializePipeline("default", shaderModules)
    }

    /**
     * Initialiases a custom [VulkanPipeline] with [pipelineName], built out of the [shaders] for a specific [vertexInputType].
     * The pipeline settings are customizable using the lambda [settings].
     */
    fun initializePipeline(pipelineName: String = "default", shaders: List<VulkanShaderModule>,
                           vertexInputType: VulkanRenderer.VertexDescription = vertexDescriptors.get(VulkanRenderer.VertexDataKinds.PositionNormalTexcoord)!!,
                           settings: (VulkanPipeline) -> Any = {}) {
        val p = VulkanPipeline(device, pipelineCache)

        val reqDescriptorLayouts = ArrayList<Long>()

        val framebuffer = output.values.first()

        p.addShaderStages(shaders)

        logger.debug("${descriptorSetLayouts.count()} DSLs are available: ${descriptorSetLayouts.keys.joinToString(", ")}")

        val blendMasks = VkPipelineColorBlendAttachmentState.calloc(framebuffer.colorAttachmentCount())
        (0 until framebuffer.colorAttachmentCount()).forEach {
            if(passConfig.renderTransparent) {
                blendMasks[it]
                    .blendEnable(true)
                    .colorBlendOp(passConfig.colorBlendOp.toVulkan())
                    .srcColorBlendFactor(passConfig.srcColorBlendFactor.toVulkan())
                    .dstColorBlendFactor(passConfig.dstColorBlendFactor.toVulkan())
                    .alphaBlendOp(passConfig.alphaBlendOp.toVulkan())
                    .srcAlphaBlendFactor(passConfig.srcAlphaBlendFactor.toVulkan())
                    .dstAlphaBlendFactor(passConfig.dstAlphaBlendFactor.toVulkan())
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT or VK_COLOR_COMPONENT_G_BIT or VK_COLOR_COMPONENT_B_BIT or VK_COLOR_COMPONENT_A_BIT)
            } else {
                blendMasks[it]
                    .blendEnable(false)
                    .colorWriteMask(0xF)
            }
        }

        p.colorBlendState.pAttachments()?.free()
        p.colorBlendState
            .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
            .pNext(MemoryUtil.NULL)
            .pAttachments(blendMasks)

        p.depthStencilState
            .depthTestEnable(passConfig.depthTestEnabled)
            .depthWriteEnable(passConfig.depthWriteEnabled)

        p.descriptorSpecs.entries
            .groupBy { it.value.set }
            .toSortedMap()
            .forEach { setId, group ->
                logger.debug("${this.name}: Initialising DSL for set $setId with ${group.sortedBy { it.value.binding }.joinToString(", ") { "${it.value.name} (${it.value.set}/${it.value.binding})" }}")
                reqDescriptorLayouts.add(initializeDescriptorSetLayoutForSpecs(setId, group.sortedBy { it.value.binding }))
            }

        settings.invoke(p)

        if(logger.isDebugEnabled) {
            logger.debug("DS are: ${p.descriptorSpecs.entries.sortedBy { it.value.binding }.sortedBy { it.value.set }.joinToString { "${it.key} (set=${it.value.set}, binding=${it.value.binding})" } }")
        }

        logger.debug("Required DSLs: ${reqDescriptorLayouts.joinToString { it.toHexString() } }")

        when(passConfig.type) {
            RenderConfigReader.RenderpassType.quad -> {
                p.rasterizationState.cullMode(VK_CULL_MODE_FRONT_BIT)
                p.rasterizationState.frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)

                p.createPipelines(this, framebuffer.renderPass.get(0),
                    vertexDescriptors[VulkanRenderer.VertexDataKinds.None]!!.state,
                    descriptorSetLayouts = reqDescriptorLayouts,
                    onlyForTopology = GeometryType.TRIANGLES)
            }

            RenderConfigReader.RenderpassType.geometry,
            RenderConfigReader.RenderpassType.lights -> {
                p.createPipelines(this, framebuffer.renderPass.get(0),
                    vertexInputType.state,
                    descriptorSetLayouts = reqDescriptorLayouts)
            }
        }

        logger.debug("Prepared pipeline $pipelineName for $name")

        pipelines.put(pipelineName, p)?.close()
    }

    private fun initializeDescriptorSetLayoutForSpecs(setId: Long, specs: List<MutableMap.MutableEntry<String, VulkanShaderModule.UBOSpec>>): Long {
        val contents = specs.map { s ->
            val spec = s.value
            when {
                spec.name == "Matrices" ->
                    listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1))
                spec.name == "MaterialProperties" ->
                    listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1))
                spec.name.startsWith("Input") ->
                    spec.members.map { Pair(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1) }.toList()
                spec.name == "ShaderParameters" ->
                    listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1))
                spec.name == "ShaderProperties" ->
                    listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1))
                spec.type != VulkanShaderModule.UBOSpecType.UniformBuffer ->
                    listOf(Pair(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, spec.size))
                else ->
                    listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1))
            }
        }.flatten()

        logger.debug("Initialiasing DSL for ${specs.first().value.name}, set=$setId, type=${contents.first().first}")

        val dsl = VU.createDescriptorSetLayout(device, contents, 0, shaderStages = VK_SHADER_STAGE_ALL)
        // destroy descriptor set layout if there was a previously associated one,
        // and add the new one
        descriptorSetLayouts.put(specs.first().value.name, dsl)?.let { dslOld ->
            // TODO: Figure out whether they should actually be deleted, or just marked for garbage collection
            oldDescriptorSetLayouts.put(specs.first().value.name, dslOld)
        }

        return dsl
    }

    /**
     * Returns the default output [VulkanFramebuffer] of this renderpass.
     */
    fun getOutput(): VulkanFramebuffer {
        val fb = if(isViewportRenderpass) {
            val pos = currentPosition
            currentPosition = (currentPosition + 1).rem(commandBufferCount)

            output["Viewport-$pos"]!!
        } else {
            output.values.first()
        }

        return fb
    }

    /**
     * Returns the [commandBufferBacking]'s current read position. Used e.g.
     * to determine the most currently rendered swapchain image for a viewport pass.
     */
    fun getReadPosition() = commandBufferBacking.currentReadPosition - 1

    /**
     * Returns the active [VulkanPipeline] for [forNode], if it has a preferred pipeline,
     * or the default one if not.
     */
    fun getActivePipeline(forNode: Node): VulkanPipeline {
        return pipelines.getOrDefault("preferred-${forNode.uuid}", getDefaultPipeline())
    }

    /**
     * Returns this renderpasses' default pipeline.
     */
    fun getDefaultPipeline(): VulkanPipeline {
        return pipelines["default"]!!
    }

    /**
     * Closes this renderpass and deallocates its resources.
     */
    override fun close() {
        logger.debug("Closing renderpass $name...")
        output.forEach { it.value.close() }
        pipelines.forEach { it.value.close() }
        UBOs.forEach { it.value.close() }
        descriptorSetLayouts.forEach { vkDestroyDescriptorSetLayout(device.vulkanDevice, it.value, null) }
        descriptorSetLayouts.clear()
        oldDescriptorSetLayouts.forEach { vkDestroyDescriptorSetLayout(device.vulkanDevice, it.value, null) }
        oldDescriptorSetLayouts.clear()

        vulkanMetadata.close()

        for(i in 1..commandBufferBacking.size) {
            commandBufferBacking.get().close()
        }

        commandBufferBacking.reset()

        if(semaphore != -1L) {
            vkDestroySemaphore(device.vulkanDevice, semaphore, null)
            memFree(waitSemaphores)
            memFree(signalSemaphores)
            memFree(waitStages)
            memFree(submitCommandBuffers)

            semaphore = -1L
        }
    }
}
