package graphics.scenery.backends

import cleargl.GLMatrix
import cleargl.GLVector
import graphics.scenery.backends.vulkan.VulkanDevice
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkQueue
import vkn.VkFormat

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface Display {
    /**
     * Returns the per-eye projection matrix
     *
     * @param[eye] The index of the eye
     * @return GLMatrix containing the per-eye projection matrix
     */
    fun getEyeProjection(eye: Int, nearPlane: Float = 1.0f, farPlane: Float = 1000.0f): GLMatrix

    /**
     * Returns the inter-pupillary distance (IPD)
     *
     * @return IPD as Float
     */
    fun getIPD(): Float
    /**
     * Query the HMD whether a compositor is used or the renderer should take
     * care of displaying on the HMD on its own.
     *
     * @return True if the HMD has a compositor
     */
    fun hasCompositor(): Boolean

    /**
     * Submit OpenGL texture IDs to the compositor. The texture is assumed to have the left eye in the
     * left half, right eye in the right half.
     *
     * @param[textureId] OpenGL Texture ID of the left eye texture
     */
    fun submitToCompositor(textureId: Int)

    /**
     * Submit a Vulkan texture handle to the compositor
     *
     * @param[width] Texture width
     * @param[height] Texture height
     * @param[format] Vulkan texture format
     * @param[instance] Vulkan Instance
     * @param[device] Vulkan device
     * @param[queue] Vulkan queue
     * @param[queueFamilyIndex] Queue family index
     * @param[image] The Vulkan texture image to be presented to the compositor
     */
    fun submitToCompositorVulkan(width: Int, height: Int, format: VkFormat,
                                 instance: VkInstance, device: VulkanDevice,
                                 queue: VkQueue,
                                 image: Long)

    /**
     * Returns the optimal render target size for the HMD as 2D vector
     *
     * @return Render target size as 2D vector
     */
    fun getRenderTargetSize(): GLVector

    /**
     * Check whether the HMD is initialized and working
     *
     * @return True if HMD is initialiased correctly and working properly
     */
    fun initializedAndWorking(): Boolean

    /**
     * update state
     */
    fun update()

    fun getVulkanInstanceExtensions(): List<String>

    fun getVulkanDeviceExtensions(physicalDevice: VkPhysicalDevice): List<String>

    fun getWorkingDisplay(): Display?

    /**
     * Returns the per-eye transform that moves from head to eye
     *
     * @param[eye] The eye index
     * @return GLMatrix containing the transform
     */
    fun getHeadToEyeTransform(eye: Int): GLMatrix
}
