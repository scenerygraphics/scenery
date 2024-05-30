package graphics.scenery.backends

import graphics.scenery.Settings
import org.joml.Matrix4f
import graphics.scenery.backends.vulkan.VulkanDevice
import org.joml.Vector2i

import org.joml.Vector4f
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkQueue

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
     * @return Matrix4f containing the per-eye projection matrix
     */
    fun getEyeProjection(eye: Int, nearPlane: Float = 1.0f, farPlane: Float = 1000.0f): Matrix4f

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
    fun submitToCompositorVulkan(width: Int, height: Int, format: Int,
                                 instance: VkInstance, device: VulkanDevice,
                                 queueWithMutex: VulkanDevice.QueueWithMutex,
                                 image: Long)

    /**
     * Returns the optimal render target size for the HMD as 2D vector
     *
     * @return Render target size as 2D vector
     */
    fun getRenderTargetSize(): Vector2i

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

    /**
     * Returns a [List] of Vulkan instance extensions required by the device.
     *
     * @return [List] of strings containing the required instance extensions
     */
    fun getVulkanInstanceExtensions(): List<String>

    /**
     * Returns a [List] of Vulkan device extensions required by the device.
     *
     * @return [List] of strings containing the required device extensions
     */
    fun getVulkanDeviceExtensions(physicalDevice: VkPhysicalDevice): List<String>

    /**
     * Returns a [Display] instance, if working currently
     *
     * @return Either a [Display] instance, or null.
     */
    fun getWorkingDisplay(): Display?

    /**
     * Returns the per-eye transform that moves from head to eye
     *
     * @param[eye] The eye index
     * @return Matrix4f containing the transform
     */
    fun getHeadToEyeTransform(eye: Int): Matrix4f

    fun wantsVR(settings: Settings): Display? {
        return if (settings.get("vr.Active")) {
            this
        } else {
            null
        }
    }

    /**
     * Fades the view on the HMD to the specified color.
     *
     * The fade will take {@code seconds}, and the color values are between 0.0 and 1.0. This color is faded on top of the scene based on the alpha
     * parameter. Removing the fade color instantly would be {@code FadeToColor( 0.0, 0.0, 0.0, 0.0, 0.0 )}. Values are in un-premultiplied alpha space.
     */
    fun fadeToColor(color: Vector4f, seconds: Float)
}
