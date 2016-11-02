package scenery.backends.vulkan

import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.NativeResource
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scenery.GeometryType
import java.nio.IntBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by ulrik on 9/28/2016.
 */
class VulkanPipeline(val device: VkDevice, val descriptorPool: Long, val pipelineCache: Long? = null, val buffers: HashMap<String, VulkanBuffer>) {
    protected var logger: Logger = LoggerFactory.getLogger("VulkanRenderer")

    var pipeline = HashMap<GeometryType, VulkanRenderer.Pipeline>()

    var UBOs = ArrayList<VulkanRenderer.UBO>()
    var descriptorSets = ConcurrentHashMap<String, Long>()
    var descriptorSetLayouts = ConcurrentHashMap<String, Long>()

    val inputAssemblyState = VkPipelineInputAssemblyStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
        .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
        .pNext(NULL)

    val rasterizationState = VkPipelineRasterizationStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
        .pNext(NULL)
        .polygonMode(VK_POLYGON_MODE_FILL)
        .cullMode(VK_CULL_MODE_BACK_BIT)
        .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
        .depthClampEnable(false)
        .rasterizerDiscardEnable(false)
        .depthBiasEnable(false)

    val colorWriteMask = VkPipelineColorBlendAttachmentState.calloc(1)
        .blendEnable(false)
        .colorWriteMask(0xF) // this means RGBA writes

    val colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
        .pAttachments(colorWriteMask)
        .pNext(NULL)

    val viewportState = VkPipelineViewportStateCreateInfo.calloc()
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

    var depthStencilState = VkPipelineDepthStencilStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
        .pNext(NULL)
        .depthTestEnable(true)
        .depthWriteEnable(true)
        .depthCompareOp(VK_COMPARE_OP_LESS)
        .depthBoundsTestEnable(false)
        .minDepthBounds(0.0f)
        .maxDepthBounds(1.0f)
        .stencilTestEnable(false)

    var depthStencilStateBack = depthStencilState.back()
        .failOp(VK_STENCIL_OP_KEEP)
        .passOp(VK_STENCIL_OP_KEEP)
        .compareOp(VK_COMPARE_OP_ALWAYS)

    var depthStencilStateFront = depthStencilState.front(depthStencilState.back())

    val multisampleState = VkPipelineMultisampleStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
        .pNext(NULL)
        .pSampleMask(null)
        .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)

    var shaderStages = VkPipelineShaderStageCreateInfo.calloc(2)

    fun addShaderStages(vararg shaderModules: VulkanShaderModule) {
        val stages = VkPipelineShaderStageCreateInfo.calloc(shaderModules.size)
        shaderModules.forEachIndexed { i, it -> stages.get(i).set(it.shader) }

        this.shaderStages = stages
    }

    fun addShaderStages(shaderModules: List<VulkanShaderModule>) {
        val stages = VkPipelineShaderStageCreateInfo.calloc(shaderModules.size)
        shaderModules.forEachIndexed { i, it -> stages.get(i).set(it.shader) }

        this.shaderStages = stages
    }

    fun createDescriptorSetLayout(device: VkDevice, type: Int = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, descriptorNum: Int = 1, descriptorCount: Int = 1): Long {
        logger.info("Creating DSL for $descriptorNum descriptors")

        val layoutBinding = VkDescriptorSetLayoutBinding.calloc(descriptorNum)
        (0..descriptorNum - 1).forEach { i ->
            layoutBinding[i]
                .binding(i) // <- Binding 0 : Uniform buffer (Vertex shader)
                .descriptorType(type)
                .descriptorCount(descriptorCount)
                .stageFlags(VK_SHADER_STAGE_ALL)
                .pImmutableSamplers(null)
        }

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

    fun createDescriptorSet(device: VkDevice, descriptorPool: Long, descriptorSetLayout: Long, bindingCount: Int,
                            type: Int = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC): Long {
        logger.info("Creating descriptor set with ${bindingCount} bindings, DSL=$descriptorSetLayout")
        val pDescriptorSetLayout = memAllocLong(1)
        pDescriptorSetLayout.put(0, descriptorSetLayout)

        val allocInfo = VkDescriptorSetAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
            .pNext(NULL)
            .descriptorPool(descriptorPool)
            .pSetLayouts(pDescriptorSetLayout)

        val descriptorSet = VU.run(memAllocLong(1), "createDescriptorSet",
            { vkAllocateDescriptorSets(device, allocInfo, this) },
            { allocInfo.free(); memFree(pDescriptorSetLayout) })

        val d = if(type == VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC) {
            VkDescriptorBufferInfo.calloc(1)
                .buffer(buffers["UBOBuffer"]!!.buffer)
                .range(2048)
                .offset(0L)
        } else {
            VkDescriptorImageInfo.calloc(1)
        }

        val writeDescriptorSet = VkWriteDescriptorSet.calloc(bindingCount)

        (0..bindingCount-1).forEach { i ->
            writeDescriptorSet[i]
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .pNext(NULL)
                .dstSet(descriptorSet)
                .dstBinding(i)

            if(type == VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC) {
                writeDescriptorSet[i]
                    .pBufferInfo(d as VkDescriptorBufferInfo.Buffer)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
            }
            if(type == VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER) {
                writeDescriptorSet[i]
                    .pImageInfo(d as VkDescriptorImageInfo.Buffer)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            }
        }

        vkUpdateDescriptorSets(device, writeDescriptorSet, null)
        writeDescriptorSet.free()
        (d as NativeResource).free()

        return descriptorSet
    }

    fun createPipelines(renderPass: Long, vi: VkPipelineVertexInputStateCreateInfo) {
        val pDescriptorSetLayout = memAllocLong(2)
        val descriptorSetLayout = createDescriptorSetLayout(device,
            descriptorNum = this.UBOs.count(),
            descriptorCount = 1)

        val dslObjectTextures = createDescriptorSetLayout(device,
            type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
            descriptorNum = 1,
            descriptorCount = 8)

        this.descriptorSets.put("default",
            createDescriptorSet(device, descriptorPool,
                descriptorSetLayout, this.UBOs.count(),
                type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC))

        /*this.descriptorSets.put("ObjectTextures",
            createDescriptorSet(device, descriptorPool,
                dslObjectTextures, 1,
                type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER))
                */

        pDescriptorSetLayout.put(0, descriptorSetLayout)
        pDescriptorSetLayout.put(1, dslObjectTextures)

        descriptorSetLayouts.put("default", descriptorSetLayout)
        descriptorSetLayouts.put("ObjectTextures", dslObjectTextures)

        val pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
            .pNext(NULL)
            .pSetLayouts(pDescriptorSetLayout)

        val layout = VU.run(memAllocLong(1), "vkCreatePipelineLayout",
            { vkCreatePipelineLayout(device, pPipelineLayoutCreateInfo, null, this) },
            { pPipelineLayoutCreateInfo.free(); memFree(pDescriptorSetLayout); })

        val pipelineCreateInfo = VkGraphicsPipelineCreateInfo.calloc(1)
            .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
            .pNext(NULL)
            .layout(layout)
            .renderPass(renderPass)
            .pVertexInputState(vi)
            .pInputAssemblyState(inputAssemblyState)
            .pRasterizationState(rasterizationState)
            .pColorBlendState(colorBlendState)
            .pMultisampleState(multisampleState)
            .pViewportState(viewportState)
            .pDepthStencilState(depthStencilState)
            .pStages(shaderStages)
            .pDynamicState(dynamicState)
            .flags(VK_PIPELINE_CREATE_ALLOW_DERIVATIVES_BIT)
            .subpass(0)

        logger.info("Creating pipeline for $renderPass with $layout")

        val p = VU.run(memAllocLong(1), "vkCreateGraphicsPipelines",
            { vkCreateGraphicsPipelines(device, pipelineCache ?: VK_NULL_HANDLE, pipelineCreateInfo, null, this) })

        val vkp = VulkanRenderer.Pipeline()
        vkp.layout = layout
        vkp.pipeline = p

        this.pipeline.put(GeometryType.TRIANGLES, vkp)

        // create pipelines for other topologies as well
        GeometryType.values().forEach { topology ->
            if(topology == GeometryType.TRIANGLES) {
                return@forEach
            }

            inputAssemblyState.topology(topology.asVulkanTopology()).pNext(NULL)

            pipelineCreateInfo
                .pInputAssemblyState(inputAssemblyState)
                .basePipelineHandle(vkp.pipeline)
                .basePipelineIndex(-1)
                .flags(VK_PIPELINE_CREATE_DERIVATIVE_BIT)

            val derivativeP = VU.run(memAllocLong(1), "vkCreateGraphicsPipelines(derivative)",
                { vkCreateGraphicsPipelines(device, pipelineCache ?: VK_NULL_HANDLE, pipelineCreateInfo, null, this) })

            val derivativePipeline = VulkanRenderer.Pipeline()
            derivativePipeline.layout = layout
            derivativePipeline.pipeline = derivativeP

            this.pipeline.put(topology, derivativePipeline)
        }

        pipelineCreateInfo.free()
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
}
