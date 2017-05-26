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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Display/TrackerInput implementation for stereoscopic displays and tracked shutter glasses
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class TrackedStereoGlasses(var address: String = "device@localhost:5500", var screenConfig: String = "CAVEExample.yml", val screenWidth: Int = 1920, val screenHeight: Int = 1080) : Display, TrackerInput, Hubable {
    override var hub: Hub? = null

    var vrpnTracker = VRPNTrackerInput(address)
    var currentOrientation = GLMatrix()
    var ipd = -0.065f

    var logger: Logger = LoggerFactory.getLogger("TrackedStereoGlasses")
    var config: ScreenConfig.Config = ScreenConfig.loadFromFile(screenConfig)
    var screen: ScreenConfig.SingleScreenConfig? = null

    private var rotation: Quaternion

    init {
        logger.info("My screen is ${ScreenConfig.getScreen(config)}")
        screen = ScreenConfig.getScreen(config)
        rotation = Quaternion().setIdentity()

        screen?.let {
            rotation = Quaternion().setFromMatrix(it. getTransform().transposedFloatArray, 0)
        }
    }

    /**
     * Returns the per-eye projection matrix
     *
     * @param[eye] The index of the eye
     * @return GLMatrix containing the per-eye projection matrix
     */
    override fun getEyeProjection(eye: Int, nearPlane: Float, farPlane: Float): GLMatrix {
        screen?.let { screen ->
            val eyeShift = if (eye == 0) {
                -ipd / 2.0f
            } else {
                ipd / 2.0f
            }

            val position = getPosition() + GLVector(eyeShift, 0.0f, 0.0f)
            val position4 = GLVector(position.x(), position.y(), position.z(), 1.0f)

            val result = screen.getTransform().mult(position4)

            val left = -result.x()
            val right = screen.width - result.x()
            val bottom = -result.y()
            val top = screen.height - result.y()
            var near = -result.z()

            if(near < 0.0001f) {
                near = 0.0001f
            }

            val scaledNear = nearPlane / maxOf(near, 0.001f)

            logger.info(eye.toString() + ", " + screen.width + "/" + screen.height + " => " + near + " -> " + left + "/" + right + "/" + bottom + "/" + top + ", s=" + scaledNear)

            val projection = GLMatrix().setFrustumMatrix(left * scaledNear, right * scaledNear, bottom * scaledNear, top * scaledNear, near * scaledNear, farPlane)
            projection.mult(rotation)
            return projection
        }

        logger.warn("No screen configuration found, ")
        return GLMatrix.getIdentity()
    }

    /**
     * Returns the inter-pupillary distance (IPD)
     *
     * @return IPD as Float
     */
    override fun getIPD(): Float {
        return ipd
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
        logger.error("This Display implementation does not have a compositor. Incorrect configuration?")
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
    override fun getPosition(): GLVector {
        if(System.getProperty("scenery.FakeVRPN", "false").toBoolean()) {
//            val pos = GLVector(
//                1.92f * Math.sin(System.nanoTime()/10e9 % (2.0*Math.PI)).toFloat(),
//                1.5f,
//                -1.92f * Math.cos(System.nanoTime()/10e9 % (2.0*Math.PI)).toFloat())

            val pos = GLVector(0.0f, 1.7f, 0.0f)
            logger.info("Using fake position: $pos")
            return pos
        }

        return vrpnTracker.getPosition()
    }

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
    override fun getPose(): GLMatrix {
        val trackerOrientation = vrpnTracker.getOrientation()
        val trackerPos = vrpnTracker.getPosition()

        currentOrientation.setIdentity()
        currentOrientation.translate(-trackerPos.x(), -trackerPos.y(), trackerPos.z())

        return currentOrientation
    }

    /**
     * Check whether the HMD is initialized and working
     *
     * @return True if HMD is initialised correctly and working properly
     */
    override fun initializedAndWorking(): Boolean {
        if(System.getProperty("scenery.FakeVRPN", "false").toBoolean()) {
            return true
        }

        return vrpnTracker.initializedAndWorking()
    }

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
        val shift = GLMatrix.getIdentity()
        if(eye == 0) {
            shift.translate(-0.025f, 0.0f, 0.0f)
        } else {
            shift.translate(0.025f, 0.0f, 0.0f)
        }

        return shift
    }
}
