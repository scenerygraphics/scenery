package graphics.scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.backends.Display
import graphics.scenery.backends.vulkan.VulkanDevice
import graphics.scenery.utils.LazyLogger
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkQueue

/**
 * Hololens support class
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates a new Hololens HMD instance
 */
class Hololens: TrackerInput, Display, Hubable {
    override var hub: Hub? = null

    private val logger by LazyLogger()

    private val identityQuat = Quaternion().setIdentity()
    private val nullVector = GLVector.getNullVector(3)

    private val hololensDisplaySize = GLVector(1280.0f, 720.0f)
    private val headToEyeTransforms = arrayOf(
        GLMatrix.getIdentity().translate(-0.033f, 0.0f, 0.0f),
        GLMatrix.getIdentity().translate(0.033f, 0.0f, 0.0f))

    /**
     * Returns the orientation of the HMD
     *
     * @returns GLMatrix with orientation
     */
    override fun getOrientation(): Quaternion {
        // TODO: Return actual Hololens orientation
        return identityQuat
    }

    /**
     * Returns the orientation of the given device, or a unit quaternion if the device is not found.
     *
     * @returns GLMatrix with orientation
     */
    override fun getOrientation(id: String): Quaternion {
        // TODO: Return actual Hololens orientation
        return identityQuat
    }

    /**
     * Returns the absolute position as GLVector
     *
     * @return HMD position as GLVector
     */
    override fun getPosition(): GLVector {
        // TODO: Return actual Hololens position
        return nullVector
    }

    /**
     * Returns the HMD pose
     *
     * @return HMD pose as GLMatrix
     */
    override fun getPose(): GLMatrix {
        // TODO: Return actual Hololens pose
        return GLMatrix.getIdentity()
    }

    /**
     * Check whether the HMD is initialized and working
     *
     * @return True if HMD is initialiased correctly and working properly
     */
    override fun initializedAndWorking(): Boolean {
        // TODO: Return whether Hololens is actually connected and working
        return true
    }

    /**
     * update state
     */
    override fun update() {
        // TODO: Update Hololens state
    }

    override fun getWorkingTracker(): TrackerInput? {
        return this
    }

    /**
     * Returns the per-eye projection matrix
     *
     * @param[eye] The index of the eye
     * @return GLMatrix containing the per-eye projection matrix
     */
    override fun getEyeProjection(eye: Int, nearPlane: Float, farPlane: Float): GLMatrix {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * Returns the inter-pupillary distance (IPD)
     *
     * @return IPD as Float
     */
    override fun getIPD(): Float {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * Query the HMD whether a compositor is used or the renderer should take
     * care of displaying on the HMD on its own. For the Hololens, this is always true as it is not addressed
     * as regular display.
     *
     * @return Always true, Hololens requires compositor use.
     */
    override fun hasCompositor(): Boolean {
        return true
    }

    /**
     * Submit OpenGL texture IDs to the compositor. The texture is assumed to have the left eye in the
     * left half, right eye in the right half.
     *
     * @param[textureId] OpenGL Texture ID of the left eye texture
     */
    override fun submitToCompositor(textureId: Int) {
        logger.error("Hololens is not supported with OpenGL rendering") //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * Submit a Vulkan texture handle to the compositor
     *
     * @param[width] Texture width
     * @param[height] Texture height
     * @param[format] Vulkan texture format
     * @param[instance] Vulkan Instance
     * @param[device] Vulkan device
     * @param[queue] Vulkan queue
     * @param[image] The Vulkan texture image to be presented to the compositor
     */
    override fun submitToCompositorVulkan(width: Int, height: Int, format: Int, instance: VkInstance, device: VulkanDevice, queue: VkQueue, image: Long) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * Returns the optimal render target size for the HMD as 2D vector
     *
     * @return Render target size as 2D vector
     */
    override fun getRenderTargetSize(): GLVector {
        return hololensDisplaySize
    }

    override fun getVulkanInstanceExtensions(): List<String> {
        return listOf("VK_NV_external_memory_capabilities",
            "VK_KHR_external_memory_capabilities",
            "VK_KHR_get_physical_device_properties2")
    }

    override fun getVulkanDeviceExtensions(physicalDevice: VkPhysicalDevice): List<String> {
        return listOf("VK_NV_dedicated_allocation",
            "VK_NV_external_memory",
            "VK_NV_external_memory_win32",
            "VK_NV_win32_keyed_mutex")
    }

    override fun getWorkingDisplay(): Display? {
        return this
    }

    /**
     * Returns the per-eye transform that moves from head to eye
     *
     * @param[eye] The eye index
     * @return GLMatrix containing the transform
     */
    override fun getHeadToEyeTransform(eye: Int): GLMatrix {
        return headToEyeTransforms[eye]
    }
}
