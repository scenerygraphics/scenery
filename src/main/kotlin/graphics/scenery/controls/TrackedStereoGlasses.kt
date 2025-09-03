package graphics.scenery.controls

import graphics.scenery.*
import graphics.scenery.backends.Display
import graphics.scenery.backends.vulkan.VulkanDevice
import graphics.scenery.Mesh
import graphics.scenery.utils.lazyLogger
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import graphics.scenery.utils.extensions.xyzw
import org.joml.*
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkQueue

/**
 * Display/TrackerInput implementation for stereoscopic displays and tracked shutter glasses
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class TrackedStereoGlasses(var address: String = "device@localhost:5500", var screenConfig: String = "CAVEExample.yml") : Display, TrackerInput, Hubable {

    private val logger by lazyLogger()
    override var hub: Hub? = null

    var tracker = initializeTracker(address)
    var currentOrientation = Matrix4f()
    var ipd = 0.055f

    var config: ScreenConfig.Config = ScreenConfig.loadFromFile(screenConfig)
    var screen: ScreenConfig.SingleScreenConfig? = null

    private var rotation: Quaternionf

    private var fakeTrackerInput = false

    override var events = TrackerInputEventHandlers()

    init {
        logger.info("My screen is ${ScreenConfig.getScreen(config)}/${config.name}")
        screen = ScreenConfig.getScreen(config)
        rotation = Quaternionf()

        screen?.let {
            rotation = Quaternionf().setFromUnnormalized(Matrix4f(it.getTransform()).transpose()).normalize()
        }
    }

    private fun initializeTracker(address: String): TrackerInput {
        return when {
            address.startsWith("fake:") -> {
                fakeTrackerInput = true
                VRPNTrackerInput()
            }

            address.startsWith("DTrack:") -> {
                val host = address.substringAfter("@").substringBeforeLast(":")
                val device = address.substringAfter("DTrack:").substringBefore("@")
                val port = address.substringAfterLast(":").toIntOrNull() ?: 5000

                DTrackTrackerInput(host, port, device)
            }

            address.startsWith("VRPN:") -> {
                VRPNTrackerInput(address.substringAfter("VRPN:"))
            }

            else -> {
                VRPNTrackerInput(address)
            }
        }
    }

    /**
     * Returns the per-eye projection matrix
     *
     * @param[eye] The index of the eye
     * @return Matrix4f containing the per-eye projection matrix
     */
    override fun getEyeProjection(eye: Int, nearPlane: Float, farPlane: Float): Matrix4f {
        screen?.let { screen ->
            val eyeShift = if (eye == 0) {
                -ipd / 2.0f
            } else {
                ipd / 2.0f
            }

            val position = getPosition() + Vector3f(eyeShift, 0.0f, 0.0f)

            val result = screen.getTransform().transform(position.xyzw())

            val left = -result.x()
            val right = screen.width - result.x()
            val bottom = -result.y()
            val top = screen.height - result.y()
            val near = maxOf(-result.z(), 0.0001f)

            val scaledNear = nearPlane / near

            //logger.info(eye.toString() + ", " + screen.width + "/" + screen.height + " => " + near + " -> " + left + "/" + right + "/" + bottom + "/" + top + ", s=" + scaledNear)

            val projection = Matrix4f().frustum(left * scaledNear, right * scaledNear, bottom * scaledNear, top * scaledNear, near * scaledNear, farPlane)
            projection.mul(Matrix4f().set(rotation))
            return projection
        }

        logger.warn("No screen configuration found, ")
        return Matrix4f().identity()
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
        return true
    }

    /**
     * Submit OpenGL texture IDs to the compositor. The texture is assumed to have the left eye in the
     * left half, right eye in the right half.
     *
     * @param[textureId] OpenGL Texture ID of the left eye texture
     */
    override fun submitToCompositor(textureId: Int) {
    }

    /**
     * Returns the orientation of the HMD
     *
     * @returns Matrix4f with orientation
     */
    override fun getOrientation(): Quaternionf = tracker.getOrientation()

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
    override fun submitToCompositorVulkan(width: Int, height: Int, format: Int, instance: VkInstance, device: VulkanDevice, queue: VulkanDevice.QueueWithMutex, image: Long) {
        //logger.error("This Display implementation does not have a compositor. Incorrect configuration?")
    }

    /**
     * Returns the orientation of the given device, or a unit quaternion if the device is not found.
     *
     * @returns Matrix4f with orientation
     */
    override fun getOrientation(id: String): Quaternionf = tracker.getOrientation()

    /**
     * Returns the absolute position as Vector3f
     *
     * @return HMD position as Vector3f
     */
    override fun getPosition(): Vector3f {
        if(fakeTrackerInput) {
//            val pos = Vector3f(
//                1.92f * Math.sin(System.nanoTime()/10e9 % (2.0*Math.PI)).toFloat(),
//                1.5f,
//                -1.92f * Math.cos(System.nanoTime()/10e9 % (2.0*Math.PI)).toFloat())

            val pos = Vector3f(0.0f, 1.7f, 0.0f)
            return pos
        }

        return tracker.getPosition()
    }

    /**
     * Returns the optimal render target size for the HMD as 2D vector
     *
     * @return Render target size as 2D vector
     */
    override fun getRenderTargetSize(): Vector2i {
        return Vector2i(config.screenWidth * 2, config.screenHeight * 1)
    }

    /**
     * Returns the HMD pose
     *
     * @return HMD pose as Matrix4f
     */
    override fun getPose(): Matrix4f {
        @Suppress("UNUSED_VARIABLE")
        val trackerOrientation = tracker.getOrientation()
        val trackerPos = tracker.getPosition()

        currentOrientation.identity()
        currentOrientation.translation(-trackerPos.x(), -trackerPos.y(), trackerPos.z())//.transpose()
        currentOrientation.rotate(trackerOrientation)

        //logger.info("Returning $currentOrientation")
        return currentOrientation
    }

    /**
     * Returns the HMD pose per eye
     *
     * @return HMD pose as Matrix4f
     */
    override fun getPoseForEye(eye: Int): Matrix4f {
        val p = Matrix4f(this.getPose())
        p.mul(getHeadToEyeTransform(eye))

        return p
    }

    /**
     * Returns a list of poses for the devices [type] given.
     *
     * @return Pose as Matrix4f
     */
    override fun getPose(type: TrackedDeviceType): List<TrackedDevice> {
        return listOf(TrackedDevice(TrackedDeviceType.HMD, "StereoGlasses", getPose(), System.nanoTime()))
    }

    /**
     * Check whether the HMD is initialized and working
     *
     * @return True if HMD is initialised correctly and working properly
     */
    override fun initializedAndWorking(): Boolean {
        if(fakeTrackerInput) {
            return true
        }

        return tracker.initializedAndWorking()
    }

    /**
     * update state
     */
    override fun update() {
        tracker.update()
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
     * @return Matrix4f containing the transform
     */
    override fun getHeadToEyeTransform(eye: Int): Matrix4f {
        val shift = Matrix4f().identity()
        if(eye == 0) {
            shift.translate(ipd/2.0f, 0.0f, 0.0f)
        } else {
            shift.translate(-ipd/2.0f, 0.0f, 0.0f)
        }

        return shift
    }

    override fun fadeToColor(color: Vector4f, seconds: Float) {
        TODO("Not yet implemented")
        // Ulrik: this could be easily implemented with a cam-attached plane that fades, and is removed after the fade 👍
    }

    override fun loadModelForMesh(type: TrackedDeviceType, mesh: Mesh): Mesh {
        TODO("not implemented")
    }

    override fun loadModelForMesh(device: TrackedDevice, mesh: Mesh): Mesh {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun attachToNode(device: TrackedDevice, node: Node, camera: Camera?) {
        TODO("not implemented")
    }

    override fun getTrackedDevices(ofType: TrackedDeviceType): Map<String, TrackedDevice> {
        TODO("Not implemented yet")
    }
}
