package graphics.scenery.backends.vulkan

import cleargl.GLVector
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import graphics.scenery.GeometryType
import graphics.scenery.Node
import graphics.scenery.Settings
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.RingBuffer
import org.lwjgl.system.MemoryUtil.*
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
                       val descriptorPool: Long,
                       val pipelineCache: Long,
                       val vertexDescriptors: ConcurrentHashMap<VulkanRenderer.VertexDataKinds, VulkanRenderer.VertexDescription>): AutoCloseable {

    protected val logger by LazyLogger()

    val inputs = ConcurrentHashMap<String, VulkanFramebuffer>()
    val output = ConcurrentHashMap<String, VulkanFramebuffer>()

    var pipelines = ConcurrentHashMap<String, VulkanPipeline>()
    var UBOs = ConcurrentHashMap<String, VulkanUBO>()
    var descriptorSets = ConcurrentHashMap<String, Long>()
    var descriptorSetLayouts = LinkedHashMap<String, Long>()

    var waitSemaphores = memAllocLong(1)
    var waitStages = memAllocInt(1)
    var signalSemaphores = memAllocLong(1)
    var submitCommandBuffers = memAllocPointer(1)

    var commandBuffer: VulkanCommandBuffer
        get() {
            return commandBufferBacking.get()
        }

        set(b) {
            commandBufferBacking.put(b)
        }

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
    }

    fun initializeInputAttachmentDescriptorSetLayouts() {
        var input = 0
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
                VU.createRenderTargetDescriptorSet(device, descriptorPool, dsl,
                    config.rendertargets[inputFramebuffer.key.substringBefore(".")]!!.attachments,
                    inputFramebuffer.value, inputFramebuffer.key.substringAfter("."))
            } else {
                inputFramebuffer.value.outputDescriptorSet
            }

            logger.debug("${this.name}: Creating input descriptor set for ${inputFramebuffer.key}, input-${this.name}-$input")
            descriptorSetLayouts.put("input-${this.name}-$input", dsl)?.let { oldDSL -> vkDestroyDescriptorSetLayout(device.vulkanDevice, oldDSL, null) }
            descriptorSets.put("input-${this.name}-$input", ds)
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
                    settings.set(settingsKey, value)
                }

                ubo.add(entry.key, { settings.get(settingsKey) })
            }

            logger.debug("Members are: ${ubo.members()}")
            logger.debug("Allocating VulkanUBO memory now, space needed: ${ubo.getSize()}")

            ubo.createUniformBuffer()

            // create descriptor set layout
//            val dsl = VU.createDescriptorSetLayout(device,
//                VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, 1)
            val dsl = VU.createDescriptorSetLayout(device,
                listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1)),
                0, VK_SHADER_STAGE_ALL)

            val ds = VU.createDescriptorSet(device, descriptorPool, dsl,
            1, ubo.descriptor!!, type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)

            // populate descriptor set
            ubo.populate()

            UBOs.put("ShaderParameters-$name", ubo)
            descriptorSets.put("ShaderParameters-$name", ds)

            logger.debug("Created DSL $dsl for $name, VulkanUBO has ${params.count()} members")
            descriptorSetLayouts.putIfAbsent("ShaderParameters-$name", dsl)
        }
    }

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
            descriptorSetLayouts.get("ShaderProperties-$name")!!
        }

        // returns a ordered list of the members of the ShaderProperties struct
        return dsl
    }

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

    fun updateShaderParameters() {
        UBOs.forEach { uboName, ubo ->
            if(uboName.startsWith("ShaderParameters-")) {
                ubo.populate()
            }
        }
    }

    fun updateShaderProperties() {
        UBOs.forEach { uboName, ubo ->
            if(uboName.startsWith("ShaderProperties-")) {
                ubo.populate()
            }
        }
    }

    fun initializeDefaultPipeline() {
        initializePipeline("default", passConfig.shaders.map { VulkanShaderModule.getFromCacheOrCreate(device, "main", Renderer::class.java, "shaders/" + it) })
    }

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
            .sortedBy { it.value.binding }
            .sortedBy { it.value.set }
            .forEach { (name, spec) ->
            logger.debug("${this.name}: Initialising DSL for $name at set=${spec.set} binding=${spec.binding}")

            if(spec.binding == 0L) {
                reqDescriptorLayouts.add(initializeDescriptorSetLayoutForSpec(spec))
            }
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

    private fun initializeDescriptorSetLayoutForSpec(spec: VulkanShaderModule.UBOSpec): Long {
        val contents = when {
            spec.name == "Matrices" ||
            spec.name == "MaterialProperties" -> listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1))

            spec.name == "ObjectTextures" -> listOf(Pair(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 6),
                Pair(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1))

            spec.name.startsWith("Input") -> (0..spec.members.size-1).map { Pair(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1) }.toList()

            spec.name == "ShaderParameters" && (passConfig.type == RenderConfigReader.RenderpassType.geometry || passConfig.type == RenderConfigReader.RenderpassType.lights) -> listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1))
            spec.name == "ShaderParameters" && passConfig.type == RenderConfigReader.RenderpassType.quad -> listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1))

            spec.name == "ShaderProperties" && (passConfig.type == RenderConfigReader.RenderpassType.geometry || passConfig.type == RenderConfigReader.RenderpassType.lights) -> listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1))

            else -> listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1))
        }

        logger.debug("Initialiasing DSL for ${spec.name}, set=${spec.set}, binding=${spec.binding}, type=${contents.first().first}")

        val dsl = VU.createDescriptorSetLayout(device, contents, spec.binding.toInt(), shaderStages = VK_SHADER_STAGE_ALL)
        // destroy descriptor set layout if there was a previously associated one,
        // and add the new one
        descriptorSetLayouts.put(spec.name, dsl)?.let { dslOld ->
            vkDestroyDescriptorSetLayout(device.vulkanDevice, dslOld, null)
        }

        return dsl
    }

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
        descriptorSetLayouts.forEach { vkDestroyDescriptorSetLayout(device.vulkanDevice, it.value, null) }

        vulkanMetadata.close()

        (1..commandBufferBacking.size).forEach { commandBufferBacking.get().close() }
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
