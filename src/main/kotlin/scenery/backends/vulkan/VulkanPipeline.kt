package scenery.backends.vulkan

import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Created by ulrik on 9/28/2016.
 */
class VulkanPipeline(val device: VkDevice) {
    protected var logger: Logger = LoggerFactory.getLogger("VulkanPipeline")

    var pipeline = VulkanRenderer.Pipeline()
    val inputAssemblyState = VkPipelineInputAssemblyStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
        .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)

    val rasterizationState = VkPipelineRasterizationStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
        .polygonMode(VK_POLYGON_MODE_FILL)
        .cullMode(VK_CULL_MODE_NONE)
        .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
        .depthClampEnable(false)
        .rasterizerDiscardEnable(false)
        .depthBiasEnable(false)

    val colorWriteMask = VkPipelineColorBlendAttachmentState.calloc(1)
        .blendEnable(false)
        .colorWriteMask(0xF) // <- RGBA

    val colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
        .pAttachments(colorWriteMask)

    val viewportState = VkPipelineViewportStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
        .viewportCount(1) // <- one viewport
        .scissorCount(1) // <- one scissor rectangle

    val pDynamicStates = memAllocInt(2)
    private val pDynamicStatesBuffer = pDynamicStates.put(VK_DYNAMIC_STATE_VIEWPORT).put(VK_DYNAMIC_STATE_SCISSOR).flip()

    val dynamicState = VkPipelineDynamicStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)// The dynamic state properties themselves are stored in the command buffer
        .pDynamicStates(pDynamicStates)

    var depthStencilState = VkPipelineDepthStencilStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)// No depth test/write and no stencil used
        .depthTestEnable(false)
        .depthWriteEnable(false)
        .depthCompareOp(VK_COMPARE_OP_ALWAYS)
        .depthBoundsTestEnable(false)
        .stencilTestEnable(false)

    var depthStencilStateBack = depthStencilState.back()
        .failOp(VK_STENCIL_OP_KEEP)
        .passOp(VK_STENCIL_OP_KEEP)
        .compareOp(VK_COMPARE_OP_ALWAYS)

    var depthStencilStateFront = depthStencilState.front(depthStencilState.back())

    val multisampleState = VkPipelineMultisampleStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
        .pSampleMask(null)
        .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)

    var shaderStages = VkPipelineShaderStageCreateInfo.calloc(2)

    fun addShaderStages(vararg shaderModules: VulkanShaderModule) {
        val stages = VkPipelineShaderStageCreateInfo.calloc(shaderModules.size)
        shaderModules.forEachIndexed { i, it -> stages.get(i).set(it.shader)}

        this.shaderStages = stages
    }

    fun createPipeline(descriptorSetLayout: Long, renderPass: Long, vi: VkPipelineVertexInputStateCreateInfo): VulkanRenderer.Pipeline {
        var err = 0

        val pDescriptorSetLayout = memAllocLong(1).put(0, descriptorSetLayout)
        val pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
            .pNext(NULL)
            .pSetLayouts(pDescriptorSetLayout)

        val layout = VU.run(memAllocLong(1), "vkCreatePipelineLayout",
            { vkCreatePipelineLayout(device, pPipelineLayoutCreateInfo, null, this) })

        pPipelineLayoutCreateInfo.free()
        memFree(pDescriptorSetLayout)

        if (err != VK_SUCCESS) {
            logger.error("Failed to create pipeline layout: " + VU.translate(err))
        }

        // Assign states
        val pipelineCreateInfo = VkGraphicsPipelineCreateInfo.calloc(1)
            .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
            .layout(layout) // <- the layout used for this pipeline (NEEDS TO BE SET! even though it is basically empty)
            .renderPass(renderPass) // <- renderpass this pipeline is attached to
            .pVertexInputState(vi)
            .pInputAssemblyState(inputAssemblyState)
            .pRasterizationState(rasterizationState)
            .pColorBlendState(colorBlendState)
            .pMultisampleState(multisampleState)
            .pViewportState(viewportState)
            .pDepthStencilState(depthStencilState)
            .pStages(shaderStages)
            .pDynamicState(dynamicState)

        // Create rendering pipeline
        val p = VU.run(memAllocLong(1), "vkCreateGraphicsPipelines")
            { vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineCreateInfo, null, this) }

        this.pipeline = VulkanRenderer.Pipeline()
        this.pipeline.layout = layout
        this.pipeline.pipeline = p

        return this.pipeline
    }
}
