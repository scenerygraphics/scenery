package graphics.scenery.backends.vulkan

import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.NativeResource
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import graphics.scenery.GeometryType
import java.nio.IntBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by ulrik on 9/28/2016.
 */
class VulkanPipeline(val device: VkDevice, val pipelineCache: Long? = null): AutoCloseable {
    protected var logger: Logger = LoggerFactory.getLogger("VulkanPipeline")

    var pipeline = HashMap<GeometryType, VulkanRenderer.Pipeline>()
    var descriptorSpecs = ArrayList<VulkanShaderModule.UBOSpec>()

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
        .lineWidth(1.0f)

    val colorWriteMask = VkPipelineColorBlendAttachmentState.calloc(1)
        .blendEnable(false)
        .colorWriteMask(0xF) // this means RGBA writes

    val colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
        .pNext(NULL)
        .pAttachments(colorWriteMask)

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

    var shaderStages: VkPipelineShaderStageCreateInfo.Buffer = VkPipelineShaderStageCreateInfo.calloc(2)

    fun addShaderStages(shaderModules: List<VulkanShaderModule>) {
        this.shaderStages.free()
        this.shaderStages = VkPipelineShaderStageCreateInfo.calloc(shaderModules.size)

        shaderModules.forEachIndexed {
            i, it ->
            this.shaderStages.put(i, it.shader)

            descriptorSpecs.addAll(it.uboSpecs.values)
            descriptorSpecs.sortBy { spec -> spec.set }
        }
    }

    fun createPipelines(renderPass: Long, vi: VkPipelineVertexInputStateCreateInfo,
                        descriptorSetLayouts: List<Long>, onlyForTopology: GeometryType? = null) {
        val setLayouts = memAllocLong(descriptorSetLayouts.size)

        descriptorSetLayouts.forEachIndexed { i, layout ->
            logger.debug("Adding DSL $layout for renderpass $renderPass")
            setLayouts.put(i, layout)
        }

        val pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
            .pNext(NULL)
            .pSetLayouts(setLayouts)

        val layout = VU.run(memAllocLong(1), "vkCreatePipelineLayout",
            { vkCreatePipelineLayout(device, pPipelineLayoutCreateInfo, null, this) },
            { pPipelineLayoutCreateInfo.free(); memFree(setLayouts); })

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

        if(onlyForTopology != null) {
            inputAssemblyState.topology(onlyForTopology.asVulkanTopology())
        }

        val p = VU.run(memAllocLong(1), "vkCreateGraphicsPipelines",
            { vkCreateGraphicsPipelines(device, pipelineCache ?: VK_NULL_HANDLE, pipelineCreateInfo, null, this) })

        val vkp = VulkanRenderer.Pipeline()
        vkp.layout = layout
        vkp.pipeline = p

        this.pipeline.put(GeometryType.TRIANGLES, vkp)
        descriptorSpecs.sortBy { spec -> spec.set }

        logger.debug("Pipeline needs descriptor sets ${descriptorSpecs.joinToString(", ")}")

        if(onlyForTopology == null) {
            // create pipelines for other topologies as well
            GeometryType.values().forEach { topology ->
                if (topology == GeometryType.TRIANGLES) {
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
        }

        logger.debug("Created $this for renderpass $renderPass with pipeline layout $layout (${if(onlyForTopology == null) { "Derivatives:" + this.pipeline.keys.joinToString(", ")} else { "no derivatives, only ${this.pipeline.keys.first()}" }})")

        pipelineCreateInfo.free()
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

    override fun toString(): String {
        return "VulkanPipeline (${pipeline.map { "${it.key.name} -> ${String.format("0x%X", it.value.pipeline)}"}.joinToString(", ")})"
    }

    override fun close() {
        val removedLayouts = ArrayList<Long>()

        pipeline.forEach { geometryType, pipeline ->
            vkDestroyPipeline(device, pipeline.pipeline, null)

            if(!removedLayouts.contains(pipeline.layout)) {
                vkDestroyPipelineLayout(device, pipeline.layout, null)
                removedLayouts.add(pipeline.layout)
            }
        }

        (0..shaderStages.capacity()-1).forEach { i ->
            vkDestroyShaderModule(device, shaderStages.get(i).module(), null)
        }

        inputAssemblyState.free()
        rasterizationState.free()
        depthStencilState.free()
        colorBlendState.free()
        colorWriteMask.free()
        viewportState.free()
        dynamicState.free()
        memFree(pDynamicStates)
        multisampleState.free()

        shaderStages.free()
    }
}
