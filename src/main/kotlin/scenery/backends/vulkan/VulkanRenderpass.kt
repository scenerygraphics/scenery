package scenery.backends.vulkan

import cleargl.GLVector
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scenery.GeometryType
import scenery.backends.RenderConfigReader
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by ulrik on 11/9/2016.
 */
class VulkanRenderpass(val name: String, val config: RenderConfigReader.RenderConfig,
                       val device: VkDevice,
                       val descriptorPool: Long,
                       val pipelineCache: Long,
                       val memoryProperties: VkPhysicalDeviceMemoryProperties,
                       val vertexDescriptors: ConcurrentHashMap<VulkanRenderer.VertexDataKinds, VulkanRenderer.VertexDescription>) {

    protected var logger: Logger = LoggerFactory.getLogger("VulkanRenderer")

    val inputs = ConcurrentHashMap<String, VulkanFramebuffer>()
    val output = ConcurrentHashMap<String, VulkanFramebuffer>()

    var pipelines = ConcurrentHashMap<String, VulkanPipeline>()
    var UBOs = ConcurrentHashMap<String, UBO>()
    var descriptorSets = ConcurrentHashMap<String, Long>()
    var descriptorSetLayouts = LinkedHashMap<String, Long>()

    var commandBuffer: VulkanCommandBuffer?
        get() {
            if(isViewportRenderpass) {
                logger.trace("Returning command buffer at $readPos")
                return commandBufferBacking[readPos]
            } else {
                return commandBufferBacking[0]
            }
        }

        set(b) {
           if(isViewportRenderpass) {
               b?.let {
                   logger.trace("Added command buffer for $currentPosition")
                   commandBufferBacking.add(it)
               }
           } else {
               b?.let {
                   logger.trace("Added single commandBuffer")
                   commandBufferBacking.add(it)
               }
           }
        }

    private var commandBufferBacking = ArrayList<VulkanCommandBuffer>(1)
    val secondaryCommandBuffers = ArrayList<Long>()

    var semaphore = -1L

    var passConfig: RenderConfigReader.RenderpassConfig

    var isViewportRenderpass = false
    var swapchainSize = 1
        private set
    private var currentPosition = 0
    private var readPos = 0

    fun cmdPrepareDraw(forPipeline: String) {

    }

    fun queueCommandBuffers() {

    }

    init {
        passConfig = config.renderpasses.get(name)!!

        val default = VU.createDescriptorSetLayout(device,
            descriptorNum = 3,
            descriptorCount = 1)

        descriptorSetLayouts.put("default", default)

        val dslObjectTextures = VU.createDescriptorSetLayout(
            device,
            listOf(Pair(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 5)),
            VK_SHADER_STAGE_ALL_GRAPHICS)

        descriptorSetLayouts.put("ObjectTextures", dslObjectTextures)
    }

    fun initializePipeline(device: VkDevice, descriptorPool: Long, pipelineCache: Long) {

    }

    fun initializeInputAttachmentDescriptorSetLayouts() {
        inputs.forEach { inputFramebuffer ->
            // create descriptor set layout that matches the render target
            val dsl = VU.createDescriptorSetLayout(device,
                descriptorNum = inputFramebuffer.value.attachments.count(),
                descriptorCount = 1,
                type = VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
            )

            val ds = inputFramebuffer.value.outputDescriptorSet

            descriptorSetLayouts.put("inputs-${this.name}", dsl)
            descriptorSets.put("inputs-${this.name}", ds)
        }
    }

    fun initializeShaderParameterDescriptorSetLayouts() {
        // renderpasses might have parameters set up in their YAML config. These get translated to
        // descriptor layouts, UBOs and descriptor sets
        passConfig.parameters?.let { params ->
            logger.info("Creating UBO for $name")
            // create UBO
            val ubo = UBO(device)

            ubo.name = "ShaderParameters-${name}"
            params.forEach { entry ->
                val value = if (entry.value is String || entry.value is java.lang.String) {
                    val s = entry.value as String
                    GLVector(*(s.split(",").map { it.trim().trimStart().toFloat() }.toFloatArray()))
                } else if (entry.value is Double) {
                    (entry.value as Double).toFloat()
                } else {
                    entry.value
                }

                ubo.members.put(entry.key, value)
            }

            logger.info("Members are: ${ubo.members.values.joinToString(", ")}")
            logger.info("Allocating UBO memory now, space needed: ${ubo.getSize()}")

            ubo.createUniformBuffer(memoryProperties)

            // create descriptor set layout
            val dsl = VU.createDescriptorSetLayout(device,
                VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, 1)

            val ds = VU.createDescriptorSet(device, descriptorPool, dsl,
                1, ubo.descriptor!!, type = VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)

            // populate descriptor set
            ubo.populate()

            UBOs.put("ShaderParameters-$name", ubo)
            descriptorSets.put("ShaderParameters-$name", ds)

            logger.info("Created DSL $dsl for $name, UBO has ${params.count()} members")
            descriptorSetLayouts.putIfAbsent("ShaderParameters-${name}", dsl)
        }
    }

    fun initializeDefaultPipeline() {
        val map = ConcurrentHashMap<String, VulkanPipeline>()

        val p = VulkanPipeline(device, pipelineCache)
        val reqDescriptorLayouts = ArrayList<Long>()

        val framebuffer = output.values.first()

        p.addShaderStages(passConfig.shaders.map { VulkanShaderModule(device, "main", "shaders/" + it) })

        logger.info("${descriptorSetLayouts.count()} DSLs are available: ${descriptorSetLayouts.keys.joinToString(", ")}")

        val blendMasks = VkPipelineColorBlendAttachmentState.calloc(framebuffer.colorAttachmentCount())
        (0..framebuffer.colorAttachmentCount() - 1).forEach {
            blendMasks[it]
                .blendEnable(false)
                .colorWriteMask(0xF)
        }

        val blendState = VkPipelineColorBlendStateCreateInfo.calloc()
            .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
            .pNext(MemoryUtil.NULL)
            .pAttachments(blendMasks)

        p.colorBlendState.set(blendState)

        if (passConfig.type == RenderConfigReader.RenderpassType.quad) {
            p.rasterizationState.cullMode(VK_CULL_MODE_FRONT_BIT)
            p.rasterizationState.frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)

            reqDescriptorLayouts.add(descriptorSetLayouts.get("default")!!)

            descriptorSetLayouts.get("inputs-$name")?.let { dsl ->
                logger.info("Adding DSL for $name to required pipeline DSLs")
                reqDescriptorLayouts.add(dsl)
            }
            descriptorSetLayouts.get("ShaderParameters-${name}")?.let { dsl ->
                logger.info("Adding DSL for ShaderParameters-${name} to required pipeline DSLs")
                reqDescriptorLayouts.add(dsl)
            }

            p.createPipelines(framebuffer.renderPass.get(0),
                vertexDescriptors.get(VulkanRenderer.VertexDataKinds.coords_none)!!.state,
                descriptorSetLayouts = reqDescriptorLayouts,
                onlyForTopology = GeometryType.TRIANGLES)
        } else {
            reqDescriptorLayouts.add(descriptorSetLayouts.get("default")!!)
            reqDescriptorLayouts.add(descriptorSetLayouts.get("ObjectTextures")!!)

            p.createPipelines(framebuffer.renderPass.get(0),
                vertexDescriptors.get(VulkanRenderer.VertexDataKinds.coords_normals_texcoords)!!.state,
                descriptorSetLayouts = reqDescriptorLayouts)
        }

        logger.info("Prepared pipeline for ${name}")

        pipelines.put("default", p)
    }

    fun setViewportPass(swapchainSize: Int) {
        this.isViewportRenderpass = true
        this.swapchainSize = swapchainSize

        commandBufferBacking.ensureCapacity(swapchainSize)
    }

    fun getOutput(): VulkanFramebuffer {
        val fb = if(isViewportRenderpass) {
            val pos = currentPosition
            currentPosition = (currentPosition + 1) % swapchainSize

            output["Viewport-$pos"]!!
        } else {
            output.values.first()
        }

        return fb
    }

    fun nextSwapchainImage() {
        if(!isViewportRenderpass) {
            logger.error("Renderpass $name is not a viewport renderpass!")
        } else {
            readPos = (readPos + 1) % swapchainSize
        }
    }
}
