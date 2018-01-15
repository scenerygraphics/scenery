package graphics.scenery.backends.vulkan

import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import graphics.scenery.GeometryType
import graphics.scenery.utils.LazyLogger
import java.nio.IntBuffer
import java.util.*

/**
 * Vulkan Pipeline class.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class VulkanPipeline(val device: VulkanDevice, val pipelineCache: Long? = null): AutoCloseable {
    private val logger by LazyLogger()

    var pipeline = HashMap<GeometryType, VulkanRenderer.Pipeline>()
    var descriptorSpecs = LinkedHashMap<String, VulkanShaderModule.UBOSpec>()

    val inputAssemblyState: VkPipelineInputAssemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
        .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
        .pNext(NULL)

    val rasterizationState: VkPipelineRasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
        .pNext(NULL)
        .polygonMode(VK_POLYGON_MODE_FILL)
        .cullMode(VK_CULL_MODE_BACK_BIT)
        .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
        .depthClampEnable(false)
        .rasterizerDiscardEnable(false)
        .depthBiasEnable(false)
        .lineWidth(1.0f)

    val colorWriteMask: VkPipelineColorBlendAttachmentState.Buffer = VkPipelineColorBlendAttachmentState.calloc(1)
        .blendEnable(false)
        .colorWriteMask(0xF) // this means RGBA writes

    val colorBlendState: VkPipelineColorBlendStateCreateInfo = VkPipelineColorBlendStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
        .pNext(NULL)
        .pAttachments(colorWriteMask)

    val viewportState: VkPipelineViewportStateCreateInfo = VkPipelineViewportStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
        .pNext(NULL)
        .viewportCount(1)
        .scissorCount(1)

    val pDynamicStates: IntBuffer = memAllocInt(2).apply {
        put(0, VK_DYNAMIC_STATE_VIEWPORT)
        put(1, VK_DYNAMIC_STATE_SCISSOR)
    }

    val dynamicState: VkPipelineDynamicStateCreateInfo = VkPipelineDynamicStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
        .pNext(NULL)
        .pDynamicStates(pDynamicStates)

    var depthStencilState: VkPipelineDepthStencilStateCreateInfo = VkPipelineDepthStencilStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
        .pNext(NULL)
        .depthTestEnable(true)
        .depthWriteEnable(true)
        .depthCompareOp(VK_COMPARE_OP_LESS)
        .depthBoundsTestEnable(false)
        .minDepthBounds(0.0f)
        .maxDepthBounds(1.0f)
        .stencilTestEnable(false)

    var depthStencilStateBack: VkStencilOpState = depthStencilState.back()
        .failOp(VK_STENCIL_OP_KEEP)
        .passOp(VK_STENCIL_OP_KEEP)
        .compareOp(VK_COMPARE_OP_ALWAYS)

    var depthStencilStateFront: VkStencilOpState = depthStencilState.front(depthStencilState.back()).front()

    val multisampleState: VkPipelineMultisampleStateCreateInfo = VkPipelineMultisampleStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
        .pNext(NULL)
        .pSampleMask(null)
        .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)

    val shaderStages = ArrayList<VulkanShaderModule>(2)

    fun addShaderStages(shaderModules: List<VulkanShaderModule>) {
        shaderStages.forEach { it.close() }
        shaderStages.clear()

        shaderModules.forEach {
            this.shaderStages.add(it)

            it.uboSpecs.forEach { uboName, ubo ->
                if(descriptorSpecs.containsKey(uboName)) {
                    descriptorSpecs[uboName]!!.members.putAll(ubo.members)
                } else {
                    descriptorSpecs.put(uboName, ubo)
                }
            }
        }
    }

    fun createPipelines(renderpass: VulkanRenderpass, vulkanRenderpass: Long, vi: VkPipelineVertexInputStateCreateInfo,
                        descriptorSetLayouts: List<Long>, onlyForTopology: GeometryType? = null) {
        val setLayouts = memAllocLong(descriptorSetLayouts.size).put(descriptorSetLayouts.toLongArray())
        setLayouts.flip()

//        descriptorSetLayouts.forEachIndexed { i, layout -> setLayouts.put(i, layout) }

        val pushConstantRanges = VkPushConstantRange.calloc(1)
            .offset(0)
            .size(4)
            .stageFlags(VK_SHADER_STAGE_ALL)

        val pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
            .pNext(NULL)
            .pSetLayouts(setLayouts)
            .pPushConstantRanges(pushConstantRanges)

        val layout = VU.getLong("vkCreatePipelineLayout",
            { vkCreatePipelineLayout(device.vulkanDevice, pPipelineLayoutCreateInfo, null, this) },
            { pushConstantRanges.free(); pPipelineLayoutCreateInfo.free(); memFree(setLayouts); })

        val stages = VkPipelineShaderStageCreateInfo.calloc(shaderStages.size)
        shaderStages.forEachIndexed { i, shaderStage ->
            stages.put(i, shaderStage.shader)
        }

        val pipelineCreateInfo = VkGraphicsPipelineCreateInfo.calloc(1)
            .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
            .pNext(NULL)
            .layout(layout)
            .renderPass(vulkanRenderpass)
            .pVertexInputState(vi)
            .pInputAssemblyState(inputAssemblyState)
            .pRasterizationState(rasterizationState)
            .pColorBlendState(colorBlendState)
            .pMultisampleState(multisampleState)
            .pViewportState(viewportState)
            .pDepthStencilState(depthStencilState)
            .pStages(stages)
            .pDynamicState(dynamicState)
            .flags(VK_PIPELINE_CREATE_ALLOW_DERIVATIVES_BIT)
            .subpass(0)

        if(onlyForTopology != null) {
            inputAssemblyState.topology(onlyForTopology.asVulkanTopology())
        }

        val p = VU.getLong("vkCreateGraphicsPipelines for ${renderpass.name} ($vulkanRenderpass)",
            { vkCreateGraphicsPipelines(device.vulkanDevice, pipelineCache ?: VK_NULL_HANDLE, pipelineCreateInfo, null, this) }, {})

        val vkp = VulkanRenderer.Pipeline()
        vkp.layout = layout
        vkp.pipeline = p

        this.pipeline.put(GeometryType.TRIANGLES, vkp)
//        descriptorSpecs.sortBy { spec -> spec.set }

        logger.debug("Pipeline needs descriptor sets ${descriptorSpecs.keys.joinToString(", ")}")

        if(onlyForTopology == null) {
            // create pipelines for other topologies as well
            GeometryType.values().forEach { topology ->
                if (topology == GeometryType.TRIANGLES) { return@forEach
                }

                inputAssemblyState.topology(topology.asVulkanTopology()).pNext(NULL)

                pipelineCreateInfo
                    .pInputAssemblyState(inputAssemblyState)
                    .basePipelineHandle(vkp.pipeline)
                    .basePipelineIndex(-1)
                    .flags(VK_PIPELINE_CREATE_DERIVATIVE_BIT)

                val derivativeP = VU.getLong("vkCreateGraphicsPipelines(derivative) for ${renderpass.name} ($vulkanRenderpass)",
                    { vkCreateGraphicsPipelines(device.vulkanDevice, pipelineCache ?: VK_NULL_HANDLE, pipelineCreateInfo, null, this) }, {})

                val derivativePipeline = VulkanRenderer.Pipeline()
                derivativePipeline.layout = layout
                derivativePipeline.pipeline = derivativeP

                this.pipeline.put(topology, derivativePipeline)
            }
        }

        logger.debug("Created $this for renderpass ${renderpass.name} ($vulkanRenderpass) with pipeline layout $layout (${if(onlyForTopology == null) { "Derivatives:" + this.pipeline.keys.joinToString(", ")} else { "no derivatives, only ${this.pipeline.keys.first()}" }})")

        pipelineCreateInfo.free()
        stages.free()
    }

    fun getPipelineForGeometryType(type: GeometryType): VulkanRenderer.Pipeline {
        if(this.pipeline.containsKey(type)) {
            return this.pipeline.get(type)!!
        } else {
            logger.error("Pipeline $this does not contain a fitting pipeline for $type, return triangle pipeline")
            return this.pipeline[GeometryType.TRIANGLES]!!
        }
    }

    fun GeometryType.asVulkanTopology(): Int {
        return when(this) {
            GeometryType.TRIANGLE_FAN -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN
            GeometryType.TRIANGLES -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST
            GeometryType.LINE -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST
            GeometryType.POINTS -> VK_PRIMITIVE_TOPOLOGY_POINT_LIST
            GeometryType.LINES_ADJACENCY -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST_WITH_ADJACENCY
            GeometryType.LINE_STRIP_ADJACENCY -> VK_PRIMITIVE_TOPOLOGY_LINE_STRIP_WITH_ADJACENCY
            GeometryType.POLYGON -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST
            GeometryType.TRIANGLE_STRIP -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP
        }
    }

    fun orderedDescriptorSpecs(): List<MutableMap.MutableEntry<String, VulkanShaderModule.UBOSpec>> {
        return descriptorSpecs.entries.sortedBy { it.value.binding }.sortedBy { it.value.set }
    }

    override fun toString(): String {
        return "VulkanPipeline (${pipeline.map { "${it.key.name} -> ${String.format("0x%X", it.value.pipeline)}"}.joinToString(", ")})"
    }

    override fun close() {
        val removedLayouts = ArrayList<Long>()

        pipeline.forEach { _, pipeline ->
            vkDestroyPipeline(device.vulkanDevice, pipeline.pipeline, null)

            if(!removedLayouts.contains(pipeline.layout)) {
                vkDestroyPipelineLayout(device.vulkanDevice, pipeline.layout, null)
                removedLayouts.add(pipeline.layout)
            }
        }

        inputAssemblyState.free()
        rasterizationState.free()
        depthStencilState.free()
        colorBlendState.pAttachments()?.free()
        colorBlendState.free()
        viewportState.free()
        dynamicState.free()
        memFree(pDynamicStates)
        multisampleState.free()

        shaderStages.forEach { it.close() }
    }
}
