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
import kool.free
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import vkk.*
import vkk.`object`.*
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
                            val descriptorPool: VkDescriptorPool,
                            val pipelineCache: VkPipelineCache,
                            val vertexDescriptors: ConcurrentHashMap<VulkanRenderer.VertexDataKinds, VulkanRenderer.VertexDescription>) : AutoCloseable {

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
    var descriptorSets = ConcurrentHashMap<String, VkDescriptorSet>()
        protected set
    /** Descriptor set layouts needed */
    var descriptorSetLayouts = LinkedHashMap<String, VkDescriptorSetLayout>()
        protected set

    /** Semaphores this renderpass is going to wait on when executed */
    var waitSemaphores = memAllocLong(1) // TODO always one?
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
    var semaphore = VkSemaphore(NULL)
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

    private var currentPosition = 0

    /**
     * Vulkan metadata class, keeping information about viewports, scissor areas, etc.
     */
    class VulkanMetadata(var descriptorSets: LongBuffer = memAllocLong(10),
                         var vertexBufferOffsets: LongBuffer = memAllocLong(4),
                         var scissor: VkRect2D = VkRect2D(),
                         var viewport: VkViewport = VkViewport(),
                         var vertexBuffers: LongBuffer = memAllocLong(4),
                         var clearValues: VkClearValue.Buffer? = null,
                         var renderArea: VkRect2D = VkRect2D.calloc(),
                         var renderPassBeginInfo: VkRenderPassBeginInfo = VkRenderPassBeginInfo(),
                         var uboOffsets: IntBuffer = memAllocInt(16),
                         var eye: IntBuffer = memAllocInt(1),
                         var renderLists: HashMap<VulkanCommandBuffer, Array<Node>> = HashMap()) : AutoCloseable {

        /** Close this metadata instance, and frees all members */
        override fun close() {
            descriptorSets.free()
            vertexBufferOffsets.free()
            scissor.free()
            viewport.free()
            vertexBuffers.free()
            clearValues?.free()
            renderArea.free()
            renderPassBeginInfo.free()
            uboOffsets.free()
            eye.free()
        }

    }

    /** [VulkanMetadata] for this renderpass */
    var vulkanMetadata = VulkanMetadata()
        protected set

    val vkDev get() = device.vulkanDevice

    init {
        val semaphoreCreateInfo = vk.SemaphoreCreateInfo()

        semaphore = vkDev createSemaphore semaphoreCreateInfo

        val default = VU.createDescriptorSetLayout(vkDev,
            descriptorNum = 3,
            descriptorCount = 1)

        descriptorSetLayouts["default"] = default

        val lightParameters = VU.createDescriptorSetLayout(vkDev, listOf(VkDescriptorType.UNIFORM_BUFFER to 1))

        descriptorSetLayouts["LightParameters"] = lightParameters

        val dslObjectTextures = VU.createDescriptorSetLayout(vkDev, listOf(
            VkDescriptorType.COMBINED_IMAGE_SAMPLER to 6,
            VkDescriptorType.COMBINED_IMAGE_SAMPLER to 1))

        descriptorSetLayouts["ObjectTextures"] = dslObjectTextures

        val dslVRParameters = VU.createDescriptorSetLayout(vkDev, listOf(VkDescriptorType.UNIFORM_BUFFER to 1))

        descriptorSetLayouts["VRParameters"] = dslVRParameters
    }

    /**
     * Initialises descriptor set layouts coming for the passes' [inputs].
     */
    fun initializeInputAttachmentDescriptorSetLayouts(shaderModules: List<VulkanShaderModule>) {
        var input = 0
        logger.debug("Renderpass $name has inputs ${inputs.keys.joinToString()}")
        inputs.entries.reversed().forEach { inputFramebuffer ->
            // we need to discern here whether the entire framebuffer is the input, or
            // only a part of it (indicated by a dot in the name)
            val descriptorNum = if (inputFramebuffer.key.contains(".")) {
                1
            } else {
                inputFramebuffer.value.attachments.count()
            }

            // create descriptor set layout that matches the render target
            val dsl = VU.createDescriptorSetLayout(vkDev,
                descriptorNum = descriptorNum,
                descriptorCount = 1,
                type = VkDescriptorType.COMBINED_IMAGE_SAMPLER)

            val ds = if (inputFramebuffer.key.contains(".")) {
                val targetName = inputFramebuffer.key.substringBefore(".")
                val attachmentName = inputFramebuffer.key.substringAfter(".")

                val rendertarget = config.rendertargets[targetName]
                    ?: throw IllegalStateException("Rendertargets do not contain required target ${inputFramebuffer.key}")

                VU.createRenderTargetDescriptorSet(device, descriptorPool, dsl,
                    rendertarget.attachments,
                    inputFramebuffer.value, attachmentName)
            } else {
                inputFramebuffer.value.outputDescriptorSet.L
            }

            val searchKeys = if (inputFramebuffer.key.contains(".")) {
                listOf(inputFramebuffer.key.substringAfter("."))
            } else {
                config.rendertargets[inputFramebuffer.key]?.attachments?.keys
                    ?: throw IllegalStateException("Rendertargets do not contain required target ${inputFramebuffer.key}")
            }

            logger.debug("Search keys for input attachments: ${searchKeys.joinToString(",")}")

            val spec = shaderModules.flatMap { it.uboSpecs.entries }.firstOrNull { entry ->
                entry.component2().members.count() == descriptorNum
                    && entry.component1().startsWith("Inputs")
                    && searchKeys.map { entry.component2().members.containsKey("Input$it") }.all { it == true }
            }

            if (spec != null) {
                val inputKey = "input-${this.name}-${spec.value.set}"

                logger.debug("${this.name}: Creating input descriptor set for ${inputFramebuffer.key}, $inputKey")
                descriptorSetLayouts.put(inputKey, dsl)?.let { oldDSL -> vkDestroyDescriptorSetLayout(device.vulkanDevice, oldDSL.L, null) }
                descriptorSets[inputKey] = VkDescriptorSet(ds)
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

                if (!entry.key.startsWith("Global.") && !entry.key.startsWith("Pass.")) {
                    settings.set(settingsKey, value)
                }

                ubo.add(entry.key, { settings.get(settingsKey) })
            }

            if (logger.isDebugEnabled) {
                logger.debug("Members are: {}", ubo.membersAndContent())
                logger.debug("Allocating VulkanUBO memory now, space needed: {}", ubo.getSize())
            }

            ubo.createUniformBuffer()

            // create descriptor set layout
//            val dsl = VU.createDescriptorSetLayout(device,
//                VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, 1)
            val dsl = VU.createDescriptorSetLayout(vkDev, listOf(VkDescriptorType.UNIFORM_BUFFER to 1))

            val ds = VU.createDescriptorSet(device, descriptorPool, dsl,
                1, ubo.descriptor, type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)

            // populate descriptor set
            ubo.populate()

            UBOs["ShaderParameters-$name"] = ubo
            descriptorSets["ShaderParameters-$name"] = VkDescriptorSet(ds)

            logger.debug("Created DSL $dsl for $name, VulkanUBO has ${params.count()} members")
            descriptorSetLayouts.putIfAbsent("ShaderParameters-$name", dsl)
        }
    }

    /**
     * Initialiases descriptor set layouts for [graphics.scenery.ShaderProperty]s.
     */
    fun initializeShaderPropertyDescriptorSetLayout(): VkDescriptorSetLayout {
        // this creates a shader property UBO for items marked @ShaderProperty in node
        val alreadyCreated = descriptorSetLayouts.containsKey("ShaderProperties-$name")

        val dsl = if (!alreadyCreated) {
            // create descriptor set layout
            val dsl = VU.createDescriptorSetLayout(vkDev, listOf(VkDescriptorType.UNIFORM_BUFFER_DYNAMIC to 1))

            logger.debug("Created Shader Property DSL ${dsl.asHexString} for $name")
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

        if (shaderPropertiesSpec.count() == 0) {
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
            if (uboName.startsWith("ShaderParameters-")) {
                ubo.populate()
            }
        }
    }

    /**
     * Updates all shader properties.
     */
    fun updateShaderProperties() {
        UBOs.forEach { uboName, ubo ->
            if (uboName.startsWith("ShaderProperties-")) {
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

        val reqDescriptorLayouts = ArrayList<VkDescriptorSetLayout>()

        val framebuffer = output.values.first()

        p.addShaderStages(shaders)

        logger.debug("${descriptorSetLayouts.count()} DSLs are available: ${descriptorSetLayouts.keys.joinToString()}")

        val blendMasks = VkPipelineColorBlendAttachmentState.calloc(framebuffer.colorAttachmentCount())
        (0 until framebuffer.colorAttachmentCount()).forEach {
            blendMasks[it].apply {
                if (passConfig.renderTransparent) {
                    blendEnable = true
                    colorBlendOp = passConfig.colorBlendOp.toVulkan()
                    srcColorBlendFactor = passConfig.srcColorBlendFactor.toVulkan()
                    dstColorBlendFactor = passConfig.dstColorBlendFactor.toVulkan()
                    alphaBlendOp = passConfig.alphaBlendOp.toVulkan()
                    srcAlphaBlendFactor = passConfig.srcAlphaBlendFactor.toVulkan()
                    dstAlphaBlendFactor = passConfig.dstAlphaBlendFactor.toVulkan()
                    colorWriteMask = VkColorComponent.R_BIT or VkColorComponent.G_BIT or VkColorComponent.B_BIT or VkColorComponent.A_BIT
                } else {
                    blendEnable = false
                    colorWriteMask = 0xF
                }
            }
        }

        p.colorBlendState.pAttachments()?.free()
        p.colorBlendState.apply {
            type = VkStructureType.PIPELINE_COLOR_BLEND_STATE_CREATE_INFO
            next = NULL
            attachments = blendMasks
        }
        p.depthStencilState.apply {
            depthTestEnable = passConfig.depthTestEnabled
            depthWriteEnable = passConfig.depthWriteEnabled
        }
        p.descriptorSpecs.entries
            .sortedBy { it.value.binding }
            .sortedBy { it.value.set }
            .forEach { (name, spec) ->
                logger.debug("${this.name}: Initialising DSL for $name at set=${spec.set} binding=${spec.binding}")

                if (spec.binding == 0L) {
                    reqDescriptorLayouts += initializeDescriptorSetLayoutForSpec(spec)
                }
            }

        settings.invoke(p)

        if (logger.isDebugEnabled) {
            logger.debug("DS are: ${p.descriptorSpecs.entries.sortedBy { it.value.binding }.sortedBy { it.value.set }.joinToString { "${it.key} (set=${it.value.set}, binding=${it.value.binding})" }}")
        }

        logger.debug("Required DSLs: ${reqDescriptorLayouts.joinToString { it.asHexString }}")

        when (passConfig.type) {
            RenderConfigReader.RenderpassType.quad -> {
                p.rasterizationState.cullMode(VK_CULL_MODE_FRONT_BIT)
                p.rasterizationState.frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)

                p.createPipelines(this, framebuffer.renderPass,
                    vertexDescriptors[VulkanRenderer.VertexDataKinds.None]!!.state,
                    descriptorSetLayouts = VkDescriptorSetLayoutBuffer(reqDescriptorLayouts),
                    onlyForTopology = GeometryType.TRIANGLES)
            }

            RenderConfigReader.RenderpassType.geometry,
            RenderConfigReader.RenderpassType.lights -> {
                p.createPipelines(this, framebuffer.renderPass,
                    vertexInputType.state,
                    descriptorSetLayouts = VkDescriptorSetLayoutBuffer(reqDescriptorLayouts))
            }
        }

        logger.debug("Prepared pipeline $pipelineName for $name")

        pipelines.put(pipelineName, p)?.close()
    }

    private fun initializeDescriptorSetLayoutForSpec(spec: VulkanShaderModule.UBOSpec): VkDescriptorSetLayout {
        val contents = when {
            spec.name == "Matrices" || spec.name == "MaterialProperties" -> listOf(VkDescriptorType.UNIFORM_BUFFER_DYNAMIC to 1)

            spec.name == "ObjectTextures" -> listOf(
                VkDescriptorType.COMBINED_IMAGE_SAMPLER to 6,
                VkDescriptorType.COMBINED_IMAGE_SAMPLER to 1)

            spec.name.startsWith("Input") -> (0 until spec.members.size).map { VkDescriptorType.COMBINED_IMAGE_SAMPLER to 1 }.toList()

            spec.name == "ShaderParameters" && (passConfig.type == RenderConfigReader.RenderpassType.geometry || passConfig.type == RenderConfigReader.RenderpassType.lights) -> listOf(VkDescriptorType.UNIFORM_BUFFER to 1)
            spec.name == "ShaderParameters" && passConfig.type == RenderConfigReader.RenderpassType.quad -> listOf(VkDescriptorType.UNIFORM_BUFFER to 1)

            spec.name == "ShaderProperties" && (passConfig.type == RenderConfigReader.RenderpassType.geometry || passConfig.type == RenderConfigReader.RenderpassType.lights) -> listOf(VkDescriptorType.UNIFORM_BUFFER_DYNAMIC to 1)

            else -> listOf(VkDescriptorType.UNIFORM_BUFFER to 1)
        }

        logger.debug("Initialiasing DSL for ${spec.name}, set=${spec.set}, binding=${spec.binding}, type=${contents.first().first}")

        val dsl = VU.createDescriptorSetLayout(vkDev, contents, spec.binding.toInt())
        // destroy descriptor set layout if there was a previously associated one,
        // and add the new one
        descriptorSetLayouts.put(spec.name, dsl)?.let { dslOld -> vkDev destroyDescriptorSetLayout  dslOld }

        return dsl
    }

    /**
     * Returns the default output [VulkanFramebuffer] of this renderpass.
     */
    fun getOutput(): VulkanFramebuffer {
        val fb = if (isViewportRenderpass) {
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
        vkDev destroyDescriptorSetLayouts descriptorSetLayouts.values

        vulkanMetadata.close()

        for (i in 1..commandBufferBacking.size) {
            commandBufferBacking.get().close()
        }

        commandBufferBacking.reset()

        if (semaphore.isValid) {
            vkDev destroySemaphore semaphore
            waitSemaphores.free()
            signalSemaphores.free()
            waitStages.free()
            submitCommandBuffers.free()

            semaphore = VkSemaphore(NULL)
        }
    }
}
