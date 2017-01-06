package graphics.scenery.backends.vulkan

import cleargl.GLVector
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.memAllocInt
import org.lwjgl.system.MemoryUtil.memAllocLong
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import graphics.scenery.GeometryType
import graphics.scenery.Settings
import graphics.scenery.backends.RenderConfigReader
import java.nio.IntBuffer
import java.nio.LongBuffer
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

    protected var logger: Logger = LoggerFactory.getLogger("VulkanRenderpass")

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
    var currentPosition = 0
        private set
    var readPos = 0
        private set

    data class VulkanMetadata(var descriptorSets: LongBuffer = memAllocLong(2),
                              var offsets: LongBuffer = memAllocLong(1),
                              var scissor: VkRect2D.Buffer = VkRect2D.calloc(1),
                              var viewport: VkViewport.Buffer = VkViewport.calloc(1),
                              var vertexBuffers: LongBuffer = memAllocLong(1),
                              var instanceBuffers: LongBuffer = memAllocLong(1))

    var vulkanMetadata = VulkanMetadata()

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

        val lightParameters = VU.createDescriptorSetLayout(
            device,
            listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1)),
            VK_SHADER_STAGE_ALL_GRAPHICS)

        descriptorSetLayouts.put("LightParameters", lightParameters)

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
                type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
            )

            val ds = inputFramebuffer.value.outputDescriptorSet

            descriptorSetLayouts.put("inputs-${this.name}", dsl)
            descriptorSets.put("inputs-${this.name}", ds)
        }
    }

    fun initializeShaderParameterDescriptorSetLayouts(settings: Settings) {
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

                val settingsKey = "VulkanRenderer.$name.${entry.key}"
                settings.set(settingsKey, value)
                ubo.members.put(entry.key, { settings.get(settingsKey) })
            }

            logger.debug("Members are: ${ubo.members.values.joinToString(", ")}")
            logger.debug("Allocating UBO memory now, space needed: ${ubo.getSize()}")

            ubo.createUniformBuffer(memoryProperties)

            // create descriptor set layout
            val dsl = VU.createDescriptorSetLayout(device,
                VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, 1)

            val ds = VU.createDescriptorSet(device, descriptorPool, dsl,
                1, ubo.descriptor!!, type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)

            // populate descriptor set
            ubo.populate()

            UBOs.put("ShaderParameters-$name", ubo)
            descriptorSets.put("ShaderParameters-$name", ds)

            logger.info("Created DSL $dsl for $name, UBO has ${params.count()} members")
            descriptorSetLayouts.putIfAbsent("ShaderParameters-${name}", dsl)
        }
    }

    fun updateShaderParameters() {
        UBOs.forEach { uboName, ubo ->
            if(uboName.startsWith("ShaderParameters-")) {
                ubo.populate()
            }
        }
    }

    fun initializeDefaultPipeline() {
        initializePipeline("default", passConfig.shaders.map { VulkanShaderModule(device, "main", "shaders/" + it) })
    }

    fun initializePipeline(pipelineName: String = "default", shaders: List<VulkanShaderModule>,
                           vertexInputType: VulkanRenderer.VertexDescription = vertexDescriptors.get(VulkanRenderer.VertexDataKinds.coords_normals_texcoords)!!,
                           settings: (VulkanPipeline) -> Any = {}) {
        val p = VulkanPipeline(device, pipelineCache)
        settings.invoke(p)

        val reqDescriptorLayouts = ArrayList<Long>()

        val framebuffer = output.values.first()

        p.addShaderStages(shaders)

        logger.info("${descriptorSetLayouts.count()} DSLs are available: ${descriptorSetLayouts.keys.joinToString(", ")}")

        val blendMasks = VkPipelineColorBlendAttachmentState.calloc(framebuffer.colorAttachmentCount())
        (0..framebuffer.colorAttachmentCount() - 1).forEach {
            blendMasks[it]
                .blendEnable(false)
                .colorWriteMask(0xF)
        }

        val blendState = VkPipelineColorBlendStateCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
            .pNext(MemoryUtil.NULL)
            .pAttachments(blendMasks)

        p.colorBlendState.set(blendState)

        if (passConfig.type == RenderConfigReader.RenderpassType.quad) {
            p.rasterizationState.cullMode(VK_CULL_MODE_FRONT_BIT)
            p.rasterizationState.frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)

            logger.info("DS are: ${p.descriptorSpecs.map { it.name }.joinToString(", ")}")
            // add descriptor specs. at this time, they are expected to be already
            // ordered (which happens at pipeline creation time).
            p.descriptorSpecs.forEach { spec ->
                val dslName = if(spec.name.startsWith("ShaderParameters")) {
                    "ShaderParameters-$name"
                } else if(spec.name.startsWith("inputs")) {
                    "inputs-$name"
                } else if(spec.name.startsWith("Matrices")) {
                    "default"
                } else {
                    spec.name
                }

                val dsl = descriptorSetLayouts.get(dslName)
                if(dsl != null) {
                    logger.info("Adding DSL for $dslName to required pipeline DSLs")
                    reqDescriptorLayouts.add(dsl)
                } else {
                    logger.error("DSL for $dslName not found!")
                }
            }

            p.createPipelines(framebuffer.renderPass.get(0),
                vertexDescriptors.get(VulkanRenderer.VertexDataKinds.coords_none)!!.state,
                descriptorSetLayouts = reqDescriptorLayouts,
                onlyForTopology = GeometryType.TRIANGLES)
        } else {
            reqDescriptorLayouts.add(descriptorSetLayouts.get("default")!!)
            reqDescriptorLayouts.add(descriptorSetLayouts.get("ObjectTextures")!!)

            p.createPipelines(framebuffer.renderPass.get(0),
                vertexInputType.state,
                descriptorSetLayouts = reqDescriptorLayouts)
        }

        logger.info("Prepared pipeline $pipelineName for ${name}")

        pipelines.put(pipelineName, p)
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
