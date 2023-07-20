package graphics.scenery.backends.vulkan

import graphics.scenery.geometry.GeometryType
import graphics.scenery.Node
import graphics.scenery.backends.vulkan.VulkanPostprocessPass.setRequiredDescriptorSetsPostprocess
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.utils.lazyLogger
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.KHRSwapchain
import org.lwjgl.vulkan.VK10
import kotlin.math.ceil

/**
 * Helper object for compute pass command buffer recording.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
object VulkanComputePass {
    val logger by lazyLogger()

    /**
     * Records a new compute render [pass] into the [commandBuffer] given. Eventual further buffers
     * are allocated from [commandPools]. Global [sceneUBOs] and [descriptorSets] need to be given.
     */
    fun record(
        pass: VulkanRenderpass,
        commandBuffer: VulkanCommandBuffer,
        commandPools: VulkanRenderer.CommandPools,
        sceneUBOs: List<Node>,
        descriptorSets: Map<String, Long>
    ) {
        with(commandBuffer.prepareAndStartRecording(commandPools.Compute)) {
            val metadata = ComputeMetadata(Vector3i(pass.getOutput().width, pass.getOutput().height, 1))

            val pipeline = pass.getDefaultPipeline()
            val vulkanPipeline = pipeline.getPipelineForGeometryType(GeometryType.TRIANGLES)

            if (pass.vulkanMetadata.descriptorSets.capacity() != pipeline.descriptorSpecs.count()) {
                MemoryUtil.memFree(pass.vulkanMetadata.descriptorSets)
                pass.vulkanMetadata.descriptorSets = MemoryUtil.memAllocLong(pipeline.descriptorSpecs.count())
            }

            // allocate more vertexBufferOffsets than needed, set limit lateron
            pass.vulkanMetadata.uboOffsets.position(0)
            pass.vulkanMetadata.uboOffsets.limit(16)
            (0..15).forEach { pass.vulkanMetadata.uboOffsets.put(it, 0) }

            VK10.vkCmdBindPipeline(this, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, vulkanPipeline.pipeline)
            // set the required descriptor sets for this render pass
            pass.vulkanMetadata.setRequiredDescriptorSetsPostprocess(pass, pipeline, sceneUBOs, descriptorSets)

            if(pipeline.pushConstantSpecs.containsKey("currentEye")) {
                VK10.vkCmdPushConstants(this, vulkanPipeline.layout, VK10.VK_SHADER_STAGE_ALL, 0, pass.vulkanMetadata.eye)
            }

            if(pass.vulkanMetadata.descriptorSets.limit() > 0) {
                logger.debug("${pass.name}: Binding ${pass.vulkanMetadata.descriptorSets.limit()} descriptor sets with ${pass.vulkanMetadata.uboOffsets.limit()} required offsets")
                VK10.vkCmdBindDescriptorSets(this, VK10.VK_PIPELINE_BIND_POINT_COMPUTE,
                    vulkanPipeline.layout, 0, pass.vulkanMetadata.descriptorSets, pass.vulkanMetadata.uboOffsets)
            }

            val localSizes = pipeline.shaderStages.first().localSize

            if(localSizes.first == 0 || localSizes.second == 0 || localSizes.third == 0) {
                logger.error("${pass.name}: Compute local sizes $localSizes must not be zero, setting to 1.")
            }

            val loadStoreAttachments = hashMapOf(false to pass.inputs, true to pass.output)


            loadStoreAttachments
                .forEach { (isOutput, fb) ->
                    val originalLayout = if(isOutput && pass.isViewportRenderpass) {
                        KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
                    } else {
                        VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                    }

                    fb.values
                        .flatMap { it.attachments.values }
                        .filter { it.type != VulkanFramebuffer.VulkanFramebufferType.DEPTH_ATTACHMENT}
                        .forEach { att ->
                            VulkanTexture.transitionLayout(att.image,
                                from = originalLayout,
                                to = VK10.VK_IMAGE_LAYOUT_GENERAL,
                                srcStage = VK10.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT,
                                srcAccessMask = VK10.VK_ACCESS_SHADER_READ_BIT,
                                dstStage = VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                                dstAccessMask = VK10.VK_ACCESS_SHADER_WRITE_BIT,
                                commandBuffer = this)
                        }
                }

            VK10.vkCmdDispatch(this,
                ceil(metadata.workSizes.x().toFloat() / maxOf(localSizes.first, 1).toFloat()).toInt(),
                ceil(metadata.workSizes.y().toFloat() / maxOf(localSizes.second, 1).toFloat()).toInt(),
                ceil(metadata.workSizes.z().toFloat() / maxOf(localSizes.third, 1).toFloat()).toInt())

            loadStoreAttachments
                .forEach { (isOutput, fb) ->
                    val originalLayout = if(isOutput && pass.isViewportRenderpass) {
                        KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
                    } else {
                        VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                    }

                    fb.values
                        .flatMap { it.attachments.values }
                        .filter { it.type != VulkanFramebuffer.VulkanFramebufferType.DEPTH_ATTACHMENT}
                        .forEach { att ->

                            VulkanTexture.transitionLayout(att.image,
                                from = VK10.VK_IMAGE_LAYOUT_GENERAL,
                                to = originalLayout,
                                srcStage = VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                                srcAccessMask = VK10.VK_ACCESS_SHADER_WRITE_BIT,
                                dstStage = VK10.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT,
                                dstAccessMask = VK10.VK_ACCESS_SHADER_READ_BIT,
                                commandBuffer = this)
                        }
                }

            commandBuffer.stale = false
            commandBuffer.endCommandBuffer()
        }
    }

}
