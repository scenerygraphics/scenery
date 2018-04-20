package graphics.scenery.backends.vulkan

import glfw_.appBuffer
import graphics.scenery.GeometryType
import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memFree
import org.lwjgl.vulkan.*
import uno.buffer.intBufferOf
import vkn.*
import java.nio.IntBuffer
import java.util.*

/**
 * Vulkan Pipeline class.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class VulkanPipeline(val device: VulkanDevice, val pipelineCache: VkPipelineCache = NULL) : AutoCloseable {
    private val logger by LazyLogger()

    var pipeline = HashMap<GeometryType, VulkanRenderer.Pipeline>()
    var descriptorSpecs = LinkedHashMap<String, VulkanShaderModule.UBOSpec>()

    val inputAssemblyState: VkPipelineInputAssemblyStateCreateInfo = cVkPipelineInputAssemblyStateCreateInfo { topology = VkPrimitiveTopology.TRIANGLE_LIST }

    val rasterizationState: VkPipelineRasterizationStateCreateInfo = cVkPipelineRasterizationStateCreateInfo {
        polygonMode = VkPolygonMode.FILL
        cullMode = VkCullMode.BACK_BIT.i
        frontFace = VkFrontFace.COUNTER_CLOCKWISE
        depthClampEnable = false
        rasterizerDiscardEnable = false
        depthBiasEnable = false
        lineWidth = 1f
    }

    val colorWriteMask: VkPipelineColorBlendAttachmentState.Buffer = cVkPipelineColorBlendAttachmentState(1) {
        blendEnable = false
        colorWriteMask = 0xF // this means RGBA writes
    }

    val colorBlendState: VkPipelineColorBlendStateCreateInfo = cVkPipelineColorBlendStateCreateInfo { attachments = colorWriteMask }

    val viewportState: VkPipelineViewportStateCreateInfo = cVkPipelineViewportStateCreateInfo {
        viewportCount = 1
        scissorCount = 1
    }

    val dynamicStates: IntBuffer = intBufferOf(VkDynamicState.VIEWPORT.i, VkDynamicState.SCISSOR.i)

    val dynamicState: VkPipelineDynamicStateCreateInfo = cVkPipelineDynamicStateCreateInfo { dynamicStates = this@VulkanPipeline.dynamicStates }

    var depthStencilState: VkPipelineDepthStencilStateCreateInfo = cVkPipelineDepthStencilStateCreateInfo {
        depthTestEnable = true
        depthWriteEnable = true
        depthCompareOp = VkCompareOp.LESS
        depthBoundsTestEnable = false
        minDepthBounds = 0f
        maxDepthBounds = 1f
        stencilTestEnable = false
    }

    val multisampleState: VkPipelineMultisampleStateCreateInfo = cVkPipelineMultisampleStateCreateInfo {
        sampleMask = null
        rasterizationSamples = VkSampleCount.`1_BIT`
    }

    val shaderStages = ArrayList<VulkanShaderModule>(2)

    fun addShaderStages(shaderModules: List<VulkanShaderModule>) {
        shaderStages.clear()

        shaderModules.forEach {
            shaderStages += it

            it.uboSpecs.forEach { uboName, ubo ->
                if (descriptorSpecs.containsKey(uboName))
                    descriptorSpecs[uboName]!!.members += ubo.members
                else
                    descriptorSpecs[uboName] = ubo
            }
        }
    }

    fun createPipelines(renderpass: VulkanRenderpass, vulkanRenderpass: VkRenderPass, vi: VkPipelineVertexInputStateCreateInfo,
                        descriptorSetLayouts: List<VkDescriptorSetLayout>, onlyForTopology: GeometryType? = null) {

        val pushConstantRanges = vk.PushConstantRange(1) {
            offset = 0
            size = 4
            stageFlags = VkShaderStage.ALL.i
        }

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo {
            setLayouts = appBuffer.longBufferOf(descriptorSetLayouts)
            this.pushConstantRanges = pushConstantRanges
        }

        val layout = device.vulkanDevice createPipelineLayout pipelineLayoutCreateInfo

        val stages = vk.PipelineShaderStageCreateInfo(shaderStages.size)
        for (i in shaderStages.indices)
            stages[i] = shaderStages[i].shader

        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo {
            this.layout = layout
            renderPass = vulkanRenderpass
            vertexInputState = vi
            inputAssemblyState = this@VulkanPipeline.inputAssemblyState
            rasterizationState = this@VulkanPipeline.rasterizationState
            colorBlendState = this@VulkanPipeline.colorBlendState
            multisampleState = this@VulkanPipeline.multisampleState
            viewportState = this@VulkanPipeline.viewportState
            depthStencilState = this@VulkanPipeline.depthStencilState
            this.stages = stages
            dynamicState = this@VulkanPipeline.dynamicState
            flags = VkPipelineCreate.ALLOW_DERIVATIVES_BIT.i
            subpass = 0
        }

        onlyForTopology?.let { inputAssemblyState.topology = it.asVulkanTopology() }

        val vkp = VulkanRenderer.Pipeline(
            device.vulkanDevice.createGraphicsPipelines(pipelineCache, pipelineCreateInfo),
            layout)

        pipeline[GeometryType.TRIANGLES] = vkp
//        descriptorSpecs.sortBy { spec -> spec.set }

        logger.debug("Pipeline needs descriptor sets ${descriptorSpecs.keys.joinToString()}")

        if (onlyForTopology == null) {
            // create pipelines for other topologies as well
            GeometryType.values().forEach { topology ->
                if (topology == GeometryType.TRIANGLES)
                    return@forEach

                inputAssemblyState.apply {
                    this.topology = topology.asVulkanTopology()
                    next = NULL
                }

                pipelineCreateInfo.apply {
                    inputAssemblyState = this@VulkanPipeline.inputAssemblyState
                    basePipelineHandle = vkp.pipeline
                    basePipelineIndex = -1
                    flags = VkPipelineCreate.DERIVATIVE_BIT.i
                }

                val derivativePipeline = VulkanRenderer.Pipeline(
                    device.vulkanDevice.createGraphicsPipelines(pipelineCache, pipelineCreateInfo),
                    layout)

                pipeline[topology] = derivativePipeline
            }
        }

        val derivatives = when {
            onlyForTopology == null -> "Derivatives:" + pipeline.keys.joinToString()
            else -> "no derivatives, only ${this.pipeline.keys.first()}"
        }
        logger.debug("Created $this for renderpass ${renderpass.name} ($vulkanRenderpass) with pipeline layout $layout ($derivatives)")
    }

    fun getPipelineForGeometryType(type: GeometryType): VulkanRenderer.Pipeline {
        return pipeline.getOrElse(type) {
            logger.error("Pipeline $this does not contain a fitting pipeline for $type, return triangle pipeline")
            pipeline[GeometryType.TRIANGLES]!!
        }
    }

    fun orderedDescriptorSpecs(): List<MutableMap.MutableEntry<String, VulkanShaderModule.UBOSpec>> {
        return descriptorSpecs.entries.sortedBy { it.value.binding }.sortedBy { it.value.set }
    }

    override fun toString(): String {
        return "VulkanPipeline (${pipeline.map { "${it.key.name} -> ${String.format("0x%X", it.value.pipeline)}" }.joinToString()})"
    }

    override fun close() {
        val removedLayouts = ArrayList<VkPipelineLayout>()

        pipeline.forEach { _, pipeline ->
            device.vulkanDevice destroyPipeline pipeline.pipeline

            if (!removedLayouts.contains(pipeline.layout)) {
                device.vulkanDevice destroyPipelineLayout pipeline.layout
                removedLayouts += pipeline.layout
            }
        }

        inputAssemblyState.free()
        rasterizationState.free()
        depthStencilState.free()
        colorBlendState.pAttachments()?.free()
        colorBlendState.free()
        viewportState.free()
        dynamicState.free()
        memFree(dynamicStates)
        multisampleState.free()
    }
}
