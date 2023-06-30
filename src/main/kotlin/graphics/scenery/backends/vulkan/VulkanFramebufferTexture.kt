package graphics.scenery.backends.vulkan

import org.lwjgl.vulkan.VkQueue

class VulkanFramebufferTexture(device: VulkanDevice,
                               commandPools: VulkanRenderer.CommandPools,
                               queue: VkQueue,
                               transferQueue: VkQueue,
                               val framebuffer: VulkanFramebuffer,
                               val attachment: VulkanFramebuffer.VulkanFramebufferAttachment,
                               mipLevels: Int = 1) : VulkanTexture(
                                   device,
                                   commandPools,
                                   queue,
                                   transferQueue, framebuffer, attachment, mipLevels
                               )