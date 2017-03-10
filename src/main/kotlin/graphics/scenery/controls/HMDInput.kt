package graphics.scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkQueue

/**
 * Generic interface for head-mounted displays (HMDs)
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

enum class TrackedDeviceType {
    Invalid,
    HMD,
    Controller,
    BaseStation,
    Generic
}

class TrackedDevice(val type: TrackedDeviceType, var name: String, var pose: GLMatrix, var timestamp: Long) {
    var orientation = Quaternion()
        get(): Quaternion {
//            val pose = pose.floatArray
//
//            field.w = Math.sqrt(1.0 * Math.max(0.0f, 1.0f + pose[0] + pose[5] + pose[10])).toFloat() / 2.0f
//            field.x = Math.sqrt(1.0 * Math.max(0.0f, 1.0f + pose[0] - pose[5] - pose[10])).toFloat() / 2.0f
//            field.y = Math.sqrt(1.0 * Math.max(0.0f, 1.0f - pose[0] + pose[5] - pose[10])).toFloat() / 2.0f
//            field.z = Math.sqrt(1.0 * Math.max(0.0f, 1.0f - pose[0] - pose[5] + pose[10])).toFloat() / 2.0f
//
//            field.x *= Math.signum(field.x * (pose[9] - pose[6]))
//            field.y *= Math.signum(field.y * (pose[2] - pose[8]))
//            field.z *= Math.signum(field.z * (pose[4] - pose[1]))
            field = Quaternion().setFromMatrix(pose.floatArray, 0)

            return field
        }

    var position = GLVector(0.0f, 0.0f, 0.0f)
        get(): GLVector {
            val m = pose.floatArray
            field = GLVector(m[12], m[13], m[14])

            return field
        }
}

interface HMDInput {
    /**
     * Returns the per-eye projection matrix
     *
     * @param[eye] The index of the eye
     * @return GLMatrix containing the per-eye projection matrix
     */
    fun getEyeProjection(eye: Int, nearPlane: Float = 1.0f, farPlane: Float = 1000.0f, flipY: Boolean = false): GLMatrix

    /**
     * Returns the inter-pupillary distance (IPD)
     *
     * @return IPD as Float
     */
    fun getIPD(): Float

    /**
     * Returns the orientation of the HMD
     *
     * @returns GLMatrix with orientation
     */
    fun getOrientation(): Quaternion

    /**
     * Returns the orientation of the given device, or a unit quaternion if the device is not found.
     *
     * @returns GLMatrix with orientation
     */
    fun getOrientation(id: String): Quaternion

    /**
     * Returns the absolute position as GLVector
     *
     * @return HMD position as GLVector
     */
    fun getPosition(): GLVector

    /**
     * Returns the per-eye transform that moves from head to eye
     *
     * @param[eye] The eye index
     * @return GLMatrix containing the transform
     */
    fun getHeadToEyeTransform(eye: Int): GLMatrix

    /**
     * Returns the HMD pose
     *
     * @return HMD pose as GLMatrix
     */
    fun getPose(): GLMatrix

    /**
     * Query the HMD whether a compositor is used or the renderer should take
     * care of displaying on the HMD on its own.
     *
     * @return True if the HMD has a compositor
     */
    fun hasCompositor(): Boolean

    /**
     * Submit OpenGL texture IDs to the compositor
     *
     * @param[leftId] Texture ID of the left eye texture
     * @param[rightId] Texture ID of the right eye texture
     */
    fun submitToCompositor(leftId: Int, rightId: Int)

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
                                 instance: VkInstance, device: VkDevice, physicalDevice: VkPhysicalDevice,
                                 queue: VkQueue, queueFamilyIndex: Int,
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
}
