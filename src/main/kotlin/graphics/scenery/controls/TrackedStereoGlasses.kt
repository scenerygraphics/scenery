package graphics.scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.backends.Display
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkQueue

/**
 * Display/TrackerInput implementation for stereoscopic displays and tracked shutter glasses
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class TrackedStereoGlasses(var address: String = "device@localhost:5500", val screenWidth: Int = 1920, val screenHeight: Int = 1080) : Display, TrackerInput, Hubable {
    override var hub: Hub? = null

    var vrpnTracker = VRPNTrackerInput(address)

    /**
     * Returns the per-eye projection matrix
     *
     * @param[eye] The index of the eye
     * @return GLMatrix containing the per-eye projection matrix
     */
    override fun getEyeProjection(eye: Int, nearPlane: Float, farPlane: Float, flipY: Boolean): GLMatrix {
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
     * care of displaying on the HMD on its own.
     *
     * @return True if the HMD has a compositor
     */
    override fun hasCompositor(): Boolean {
        return false
    }

    /**
     * Submit OpenGL texture IDs to the compositor
     *
     * @param[leftId] Texture ID of the left eye texture
     * @param[rightId] Texture ID of the right eye texture
     */
    override fun submitToCompositor(leftId: Int, rightId: Int) {
    }

    /**
     * Returns the orientation of the HMD
     *
     * @returns GLMatrix with orientation
     */
    override fun getOrientation(): Quaternion = vrpnTracker.getOrientation()

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
    override fun submitToCompositorVulkan(width: Int, height: Int, format: Int, instance: VkInstance, device: VkDevice, physicalDevice: VkPhysicalDevice, queue: VkQueue, queueFamilyIndex: Int, image: Long) {
    }

    /**
     * Returns the orientation of the given device, or a unit quaternion if the device is not found.
     *
     * @returns GLMatrix with orientation
     */
    override fun getOrientation(id: String): Quaternion = vrpnTracker.getOrientation()

    /**
     * Returns the absolute position as GLVector
     *
     * @return HMD position as GLVector
     */
    override fun getPosition(): GLVector = vrpnTracker.getPosition()

    /**
     * Returns the optimal render target size for the HMD as 2D vector
     *
     * @return Render target size as 2D vector
     */
    override fun getRenderTargetSize(): GLVector {
        return GLVector(screenWidth * 2.0f, screenHeight * 1.0f)
    }

    /**
     * Returns the HMD pose
     *
     * @return HMD pose as GLMatrix
     */
    override fun getPose(): GLMatrix = vrpnTracker.getPose()

    /**
     * Check whether the HMD is initialized and working
     *
     * @return True if HMD is initialised correctly and working properly
     */
    override fun initializedAndWorking(): Boolean = vrpnTracker.initializedAndWorking()

    /**
     * update state
     */
    override fun update() {
        vrpnTracker.update()
    }

    override fun getVulkanInstanceExtensions(): List<String> = emptyList()

    override fun getWorkingTracker(): TrackerInput? {
        if(initializedAndWorking()) {
            return this
        } else {
            return null
        }
    }

    override fun getVulkanDeviceExtensions(physicalDevice: VkPhysicalDevice): List<String> = emptyList()

    override fun getWorkingDisplay(): Display? {
        if(initializedAndWorking()) {
            return this
        } else {
            return null
        }
    }

    /**
     * Returns the per-eye transform that moves from head to eye
     *
     * @param[eye] The eye index
     * @return GLMatrix containing the transform
     */
    override fun getHeadToEyeTransform(eye: Int): GLMatrix {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
