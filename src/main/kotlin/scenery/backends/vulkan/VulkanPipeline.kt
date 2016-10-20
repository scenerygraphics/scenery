package scenery.backends.vulkan

import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Created by ulrik on 9/28/2016.
 */
class VulkanPipeline(val device: VkDevice, val descriptorPool: Long) {
    protected var logger: Logger = LoggerFactory.getLogger("VulkanPipeline")

    var pipeline = VulkanRenderer.Pipeline()

    var UBOs = ArrayList<VulkanRenderer.UboDescriptor>()
    var descriptorSets = HashMap<String, Long>()

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

    fun createDescriptorSetLayout(device: VkDevice, descriptorCount: Int = 1): Long {
        // One binding for a UBO used in a vertex shader
        val layoutBinding = VkDescriptorSetLayoutBinding.calloc(1)
            .binding(0) // <- Binding 0 : Uniform buffer (Vertex shader)
            .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            .descriptorCount(descriptorCount)
            .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
            .pImmutableSamplers(null)

        // Build a create-info struct to create the descriptor set layout
        val descriptorLayout = VkDescriptorSetLayoutCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
            .pNext(NULL)
            .pBindings(layoutBinding)

        val descriptorSetLayout = VU.run(memAllocLong(1), "vkCreateDescriptorSetLayout",
            function = { vkCreateDescriptorSetLayout(device, descriptorLayout, null, this) },
            cleanup = { descriptorLayout.free(); layoutBinding.free() }
        )

        return descriptorSetLayout
    }

    fun createDescriptorSet(device: VkDevice, descriptorPool: Long, descriptorSetLayout: Long, uniformDataVSDescriptor: VulkanRenderer.UboDescriptor): Long {
        val pDescriptorSetLayout = memAllocLong(1)
        pDescriptorSetLayout.put(0, descriptorSetLayout)
        val allocInfo = VkDescriptorSetAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
            .descriptorPool(descriptorPool)
            .pSetLayouts(pDescriptorSetLayout)

        val descriptorSet = VU.run(memAllocLong(1), "createDescriptorSet") {
            vkAllocateDescriptorSets(device, allocInfo, this)
        }

        allocInfo.free()
        memFree(pDescriptorSetLayout)

        // Update descriptor sets determining the shader binding points
        // For every binding point used in a shader there needs to be one
        // descriptor set matching that binding point
        val descriptor = VkDescriptorBufferInfo.calloc(1)
            .buffer(uniformDataVSDescriptor.buffer)
            .range(uniformDataVSDescriptor.range)
            .offset(uniformDataVSDescriptor.offset)

        // Binding 0 : Uniform buffer
        val writeDescriptorSet = VkWriteDescriptorSet.calloc(1)
            .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
            .dstSet(descriptorSet)
            .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            .pBufferInfo(descriptor)
            .dstBinding(0) // <- Binds this uniform buffer to binding point 0

        vkUpdateDescriptorSets(device, writeDescriptorSet, null)
        writeDescriptorSet.free()
        descriptor.free()

        return descriptorSet
    }

    fun createPipeline(renderPass: Long, vi: VkPipelineVertexInputStateCreateInfo, descriptorSetLayout: Long = createDescriptorSetLayout(device, 1)): VulkanRenderer.Pipeline {
        var err = 0

        this.UBOs.forEach { ubo ->
            this.descriptorSets.put("default", createDescriptorSet(device, descriptorPool, descriptorSetLayout, ubo))
        }

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
