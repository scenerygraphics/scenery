package graphics.scenery.backends.vulkan

import cleargl.GLVector
import graphics.scenery.GeometryType
import graphics.scenery.Node
import graphics.scenery.Settings
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.vulkan.VU.createDescriptorSetLayout
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.RingBuffer
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_ALL
import vkn.*
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Class to encapsulate Vulkan Renderpasses
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanRenderpass(val name: String, var config: RenderConfigReader.RenderConfig,
                            val device: VulkanDevice,
                            val descriptorPool: VkDescriptorPool,
                            val pipelineCache: VkPipelineCache,
                            val vertexDescriptors: ConcurrentHashMap<VulkanRenderer.VertexDataKinds, VulkanRenderer.VertexDescription>) : AutoCloseable {

    protected val logger by LazyLogger()

    val inputs = ConcurrentHashMap<String, VulkanFramebuffer>()
    val output = ConcurrentHashMap<String, VulkanFramebuffer>()

    var pipelines = ConcurrentHashMap<String, VulkanPipeline>()
    var UBOs = ConcurrentHashMap<String, VulkanUBO>()
    var descriptorSets = ConcurrentHashMap<String, VkDescriptorSet>()
    var descriptorSetLayouts = LinkedHashMap<String, VkDescriptorSetLayout>()

    var waitSemaphore: VkSemaphore = NULL
    var waitStage: VkPipelineStageFlags = 0
    var signalSemaphore: VkSemaphore = NULL
    lateinit var submitCommandBuffers: VkCommandBuffer

    var commandBuffer: VulkanCommandBuffer
        get() = commandBufferBacking.get()
        set(value) = commandBufferBacking.put(value)

    private var commandBufferBacking = RingBuffer(size = 3,
        default = { VulkanCommandBuffer(device, null, true) })

    var semaphore = -1L

    var passConfig: RenderConfigReader.RenderpassConfig = config.renderpasses.get(name)!!

    var isViewportRenderpass = false
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

    // TODO consider moveing to an empty constructor
    class VulkanMetadata(var descriptorSets: VkDescriptorSetBuffer = memAllocLong(10),
                         var vertexBufferOffsets: LongBuffer = memAllocLong(4),
                         var scissor: VkRect2D.Buffer = VkRect2D.calloc(1),
                         var viewport: VkViewport.Buffer = VkViewport.calloc(1),
                         var vertexBuffers: LongBuffer = memAllocLong(4),
                         var clearValues: VkClearValue.Buffer? = null,
                         var renderArea: VkRect2D = VkRect2D.calloc(),
                         var renderPassBeginInfo: VkRenderPassBeginInfo = VkRenderPassBeginInfo.calloc(),
                         var uboOffsets: IntBuffer = memAllocInt(16),
                         var eye: IntBuffer = memAllocInt(1),
                         var renderLists: HashMap<VulkanCommandBuffer, Array<Node>> = HashMap()) : AutoCloseable {

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

    var vulkanMetadata = VulkanMetadata()

    init {

        val default = VU.createDescriptorSetLayout(device,
            descriptorNum = 3,
            descriptorCount = 1)

        descriptorSetLayouts["default"] = default

        with(device.vulkanDevice) {

            val binding = 0 // TODO parametrize
            val shaderStages = VkShaderStage.ALL.i

            val lightParameters = createDescriptorSetLayout(
                listOf(VkDescriptorType.UNIFORM_BUFFER_DYNAMIC to 1),
                binding, shaderStages)

            descriptorSetLayouts["LightParameters"] = lightParameters

            val dslObjectTextures = createDescriptorSetLayout(
                listOf(
                    VkDescriptorType.COMBINED_IMAGE_SAMPLER to 6,
                    VkDescriptorType.COMBINED_IMAGE_SAMPLER to 1),
                binding, shaderStages)

            descriptorSetLayouts["ObjectTextures"] = dslObjectTextures

            val dslVRParameters = createDescriptorSetLayout(
                listOf(VkDescriptorType.UNIFORM_BUFFER_DYNAMIC to 1),
                binding, shaderStages)

            descriptorSetLayouts["VRParameters"] = dslVRParameters
        }
    }

    fun initializeInputAttachmentDescriptorSetLayouts() {
        var input = 0
        inputs.entries.reversed().forEach { inputFramebuffer ->
            // we need to discern here whether the entire framebuffer is the input, or
            // only a part of it (indicated by a dot in the name)
            val descriptorNum = when {
                inputFramebuffer.key.contains(".") -> 1
                else -> inputFramebuffer.value.attachments.count()
            }

            // create descriptor set layout that matches the render target
            val dsl = VU.createDescriptorSetLayout(device,
                descriptorNum = descriptorNum,
                descriptorCount = 1,
                type = VkDescriptorType.COMBINED_IMAGE_SAMPLER
            )

            val ds: VkDescriptorSet = when {
                inputFramebuffer.key.contains(".") ->
                    VU.createRenderTargetDescriptorSet(device, descriptorPool, dsl,
                        config.rendertargets!![inputFramebuffer.key.substringBefore(".")]!!,
                        inputFramebuffer.value, inputFramebuffer.key.substringAfter("."))
                else -> inputFramebuffer.value.outputDescriptorSet
            }

            logger.debug("$name: Creating input descriptor set for ${inputFramebuffer.key}, input-$name-$input")
            descriptorSetLayouts.put("input-$name-$input", dsl)?.let { oldDSL -> device.vulkanDevice destroyDescriptorSetLayout oldDSL }
            descriptorSets["input-$name-$input"] = ds
            input++
        }
    }

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
                val value = when {
                    entry.value is String || entry.value is java.lang.String -> {
                        val s = entry.value as String
                        GLVector(*(s.split(",").map { it.trim().trimStart().toFloat() }.toFloatArray()))
                    }
                    entry.value is Double -> (entry.value as Double).toFloat()
                    else -> entry.value
                }

                val settingsKey = when {
                    entry.key.startsWith("Global") -> "Renderer.${entry.key.substringAfter("Global.")}"
                    entry.key.startsWith("Pass") -> "Renderer.$name.${entry.key.substringAfter("Pass.")}"
                    else -> "Renderer.$name.${entry.key}"
                }

                if (!entry.key.startsWith("Global"))
                    settings[settingsKey] = value

                ubo.add(entry.key, { settings[settingsKey] })
            }

            logger.debug("Members are: ${ubo.members()}")
            logger.debug("Allocating VulkanUBO memory now, space needed: ${ubo.getSize()}")

            ubo.createUniformBuffer()

            // create descriptor set layout
//            val dsl = VU.createDescriptorSetLayout(device,
//                VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, 1)
            val dsl = device.vulkanDevice.createDescriptorSetLayout(
                listOf(VkDescriptorType.UNIFORM_BUFFER to 1),
                0, VK_SHADER_STAGE_ALL)

            val ds = VU.createDescriptorSet(device, descriptorPool, dsl,
                1, ubo.descriptor!!, type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)

            // populate descriptor set
            ubo.populate()

            UBOs["ShaderParameters-$name"] = ubo
            descriptorSets.put("ShaderParameters-$name", ds)

            logger.debug("Created DSL $dsl for $name, VulkanUBO has ${params.count()} members")
            descriptorSetLayouts.putIfAbsent("ShaderParameters-$name", dsl)
        }
    }

    /** This creates a shader property UBO for items marked @ShaderProperty in node and returns an ordered list of
     *  the members of the ShaderProperties struct */
    fun initializeShaderPropertyDescriptorSetLayout(): VkDescriptorSetLayout {
        return descriptorSetLayouts.getOrPut("ShaderProperties-$name") {
            device.vulkanDevice.createDescriptorSetLayout(
                listOf(VkDescriptorType.UNIFORM_BUFFER_DYNAMIC to 1),
                binding = 0,
                shaderStages = VK_SHADER_STAGE_ALL)
                .apply {
                    logger.debug("Created Shader Property DSL ${toHexString()} for $name")
                }
        }
    }

    fun getShaderPropertyOrder(node: Node): Map<String, Int> {
        // this creates a shader property UBO for items marked @ShaderProperty in node
        logger.debug("specs: ${pipelines["preferred-${node.uuid}"]!!.descriptorSpecs}")
        val shaderPropertiesSpec = pipelines["preferred-${node.uuid}"]!!.descriptorSpecs.filter { it.key == "ShaderProperties" }.map { it.value.members }

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

    fun updateShaderParameters() {
        UBOs.forEach { uboName, ubo ->
            if (uboName.startsWith("ShaderParameters-"))
                ubo.populate()
        }
    }

    fun updateShaderProperties() {
        UBOs.forEach { uboName, ubo ->
            if (uboName.startsWith("ShaderProperties-"))
                ubo.populate()
        }
    }

    fun initializeDefaultPipeline() {
        initializePipeline("default", passConfig.shaders.map { VulkanShaderModule.getFromCacheOrCreate(device, "main", Renderer::class.java, "shaders/$it") })
    }

    fun initializePipeline(pipelineName: String = "default", shaders: List<VulkanShaderModule>,
                           vertexInputType: VulkanRenderer.VertexDescription = vertexDescriptors[VulkanRenderer.VertexDataKinds.PositionNormalTexcoord]!!,
                           settings: (VulkanPipeline) -> Any = {}) {
        val p = VulkanPipeline(device, pipelineCache)

        val reqDescriptorLayouts = ArrayList<VkDescriptorSetLayout>()

        val framebuffer = output.values.first()

        p.addShaderStages(shaders)

        logger.debug("${descriptorSetLayouts.count()} DSLs are available: ${descriptorSetLayouts.keys.joinToString()}")

        val blendMasks = cVkPipelineColorBlendAttachmentState(framebuffer.colorAttachmentCount())
            .applyOnEach {
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

        p.colorBlendState.apply {
            type = VkStructureType.PIPELINE_COLOR_BLEND_STATE_CREATE_INFO
            next = NULL
            attachments?.free()
            attachments = blendMasks
        }
        p.depthStencilState.depthTestEnable = passConfig.depthTestEnabled
        p.depthStencilState.depthWriteEnable = passConfig.depthWriteEnabled

        p.descriptorSpecs.entries
            .sortedBy { it.value.binding }
            .sortedBy { it.value.set }
            .forEach { (name, spec) ->
                logger.debug("${this.name}: Initialising DSL for $name at set=${spec.set} binding=${spec.binding}")

                if (spec.binding == 0L)
                    reqDescriptorLayouts += spec.initializeDescriptorSetLayoutForSpec()
            }

        settings(p)

        if (logger.isDebugEnabled)
            logger.debug("DS are: ${p.descriptorSpecs.entries.sortedBy { it.value.binding }.sortedBy { it.value.set }.joinToString { "${it.key} (set=${it.value.set}, binding=${it.value.binding})" }}")

        logger.debug("Required DSLs: ${reqDescriptorLayouts.joinToString { it.toHexString() }}")

        when (passConfig.type) {
            RenderConfigReader.RenderpassType.quad -> {
                p.rasterizationState.cullMode = VkCullMode.FRONT_BIT.i
                p.rasterizationState.frontFace = VkFrontFace.COUNTER_CLOCKWISE

                p.createPipelines(this, framebuffer.renderPass,
                    vertexDescriptors[VulkanRenderer.VertexDataKinds.None]!!.state,
                    descriptorSetLayouts = reqDescriptorLayouts,
                    onlyForTopology = GeometryType.TRIANGLES)
            }

            RenderConfigReader.RenderpassType.geometry, RenderConfigReader.RenderpassType.lights ->
                p.createPipelines(this, framebuffer.renderPass,
                    vertexInputType.state,
                    descriptorSetLayouts = reqDescriptorLayouts)
        }

        logger.debug("Prepared pipeline $pipelineName for $name")

        pipelines.put(pipelineName, p)?.close()
    }

    private fun VulkanShaderModule.UBOSpec.initializeDescriptorSetLayoutForSpec(): Long {
        val contents = when (name) {

            "LightParameters", "Matrices", "VRParameters", "MaterialProperties" -> listOf(Pair(VkDescriptorType.UNIFORM_BUFFER_DYNAMIC, 1))

            "ObjectTextures" -> listOf(VkDescriptorType.COMBINED_IMAGE_SAMPLER to 6, VkDescriptorType.COMBINED_IMAGE_SAMPLER to 1)

            "ShaderParameters" -> when (passConfig.type) {
                RenderConfigReader.RenderpassType.geometry, RenderConfigReader.RenderpassType.lights -> listOf(VkDescriptorType.UNIFORM_BUFFER to 1)
                RenderConfigReader.RenderpassType.quad -> listOf(VkDescriptorType.UNIFORM_BUFFER to 1)
            // TODO CHECK!!
            //RenderConfigReader.RenderpassType.geometry, RenderConfigReader.RenderpassType.lights -> listOf(VkDescriptorType.UNIFORM_BUFFER_DYNAMIC to 1)
            }
            else -> when {
                name.startsWith("Input") -> (0 until members.size).map { VkDescriptorType.COMBINED_IMAGE_SAMPLER to 1 }.toList()
                else -> listOf(VkDescriptorType.UNIFORM_BUFFER to 1)
            }
        }

        logger.debug("Initialiasing DSL for $name, set=$set, binding=$binding, type=${contents.first().first}")

        return device.vulkanDevice.createDescriptorSetLayout(contents, binding.toInt(), shaderStages = VkShaderStage.ALL.i).also {
            // destroy descriptor set layout if there was a previously associated one and add the new one
            descriptorSetLayouts.put(name, it)?.let { dslOld ->
                device.vulkanDevice destroyDescriptorSetLayout dslOld
            }
        }
    }

    fun getOutput(): VulkanFramebuffer {
        val fb = if (isViewportRenderpass) {
            val pos = currentPosition
            currentPosition = (currentPosition + 1).rem(commandBufferCount)

            output["Viewport-$pos"]!!
        } else
            output.values.first()

        return fb
    }

    fun getReadPosition() = commandBufferBacking.currentReadPosition - 1

    fun getActivePipeline(forNode: Node): VulkanPipeline {
        return pipelines.getOrDefault("preferred-${forNode.uuid}", getDefaultPipeline())
    }

    fun getDefaultPipeline(): VulkanPipeline {
        return pipelines["default"]!!
    }

    override fun close() {
        logger.debug("Closing renderpass $name...")
        output.forEach { it.value.close() }
        pipelines.forEach { it.value.close() }
        UBOs.forEach { it.value.close() }
        descriptorSetLayouts.forEach { device.vulkanDevice destroyDescriptorSetLayout it.value }

        vulkanMetadata.close()

        (1..commandBufferBacking.size).forEach { commandBufferBacking.get().close() }
        commandBufferBacking.reset()

        if (semaphore != NULL) {
            device.vulkanDevice destroySemaphore semaphore
            semaphore = NULL
        }
    }
}
