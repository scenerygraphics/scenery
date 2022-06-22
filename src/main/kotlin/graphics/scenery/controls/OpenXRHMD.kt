package graphics.scenery.controls

import graphics.scenery.*
import graphics.scenery.backends.Display
import graphics.scenery.backends.vulkan.VulkanDevice
import graphics.scenery.backends.vulkan.toHexString
import graphics.scenery.utils.LazyLogger
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector2i
import org.joml.Vector3f
import org.lwjgl.PointerBuffer
import org.lwjgl.demo.openxr.XRHelper
import org.lwjgl.demo.openxr.XRHelper.prepareApiLayerProperties
import org.lwjgl.openxr.*
import org.lwjgl.openxr.KHRCompositionLayerDepth.XR_KHR_COMPOSITION_LAYER_DEPTH_EXTENSION_NAME
import org.lwjgl.openxr.KHRVulkanEnable.XR_KHR_VULKAN_ENABLE_EXTENSION_NAME
import org.lwjgl.openxr.XR10.*
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK10.VK_SUCCESS
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkQueue
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.util.function.Predicate


class OpenXRHMD(override var hub: Hub?, val requestedExtensions: List<String>) : Display, Hubable, TrackerInput {
    private val logger by LazyLogger()

    init {
//        Configuration.OPENXR_EXPLICIT_INIT.set(true)
//        XR.create()
        createInstance()
    }

    private fun xrCheck(result: Int, functionName: String) {
        if (result < XR_SUCCESS) {
            val constantName = findConstantMeaning(XR10::class.java, { candidateConstant -> candidateConstant.startsWith("XR_ERROR_") }, result)
            throw RuntimeException("OpenXR function $functionName returned $result ($constantName)")
        }
    }

    private fun vkCheck(result: Int, functionName: String) {
        if (result < VK_SUCCESS) {
            val constantName = findConstantMeaning(VK10::class.java, Predicate<String> { candidateConstant -> candidateConstant.startsWith("VK_ERROR_") }, result)
            throw RuntimeException("Vulkan function $functionName returned $result ($constantName)")
        }
    }

    private fun findConstantMeaning(containingClass: Class<*>, constantFilter: Predicate<String>, constant: Any): String? {
        val fields: Array<Field> = containingClass.fields
        for (field in fields) {
            if (Modifier.isStatic(field.getModifiers()) && constantFilter.test(field.getName())) {
                try {
                    val value: Any = field.get(null)
                    if (value is Number && constant is Number) {
                        if (value.toLong() == constant.toLong()) {
                            return field.getName()
                        }
                    }
                    if (constant == value) {
                        return field.getName()
                    }
                } catch (ex: IllegalAccessException) {
                    // Ignore private fields
                }
            }
        }
        return null
    }

    private lateinit var instance: XrInstance
    fun createInstance(): XrInstance {
        stackPush().use { stack ->
            var hasCoreValidationLayer = false
            val pi = stack.callocInt(1)
            xrCheck(xrEnumerateApiLayerProperties(pi, null), "EnumerateApiLayerProperties")
            val numLayers = pi[0]

            val pLayers = prepareApiLayerProperties(stack, numLayers)
            xrCheck(xrEnumerateApiLayerProperties(pi, pLayers), "EnumerateApiLayerProperties")
            println("$numLayers XR layers are available:")
            for (index in 0 until numLayers) {
                val layer = pLayers[index]
                val layerName = layer.layerNameString()
                println(layerName)
                if (layerName == "XR_APILAYER_LUNARG_core_validation") {
                    hasCoreValidationLayer = true
                }
            }
            println("-----------")
            val createInfo = XrInstanceCreateInfo.calloc(stack)
                .type(XR_TYPE_INSTANCE_CREATE_INFO)
            createInfo.applicationInfo()
                .apiVersion(XR_CURRENT_API_VERSION)
                .applicationName(stack.UTF8("scenery OpenXR"))
                .applicationVersion(1)
                .engineName(stack.UTF8("scenery"))

            val extensions = getOpenXRInstanceExtensions()
            createInfo.enabledExtensionNames(extensions)

            val i = stack.callocPointer(1)
            xrCreateInstance(createInfo, i)
            logger.info("Instance address is ${i.get(0).toHexString()}")
            instance = XrInstance(i.get(0), createInfo)

            logger.info("Created instance")
            return instance
        }
    }

    fun close() {
        logger.info("Closing OpenXR instance ...")
        xrDestroyInstance(instance)
    }

    fun getOpenXRInstanceExtensions(): PointerBuffer {
        stackPush().use { stack ->
            logger.info("Querying available extensions")
            val extensionCount = stack.callocInt(1)
            xrEnumerateInstanceExtensionProperties(null as? ByteBuffer, extensionCount, null)
            val extensionProperties = XRHelper.prepareExtensionProperties(stack, extensionCount.get(0))//XrExtensionProperties.calloc(extensionCount.get(0), stack)
            xrEnumerateInstanceExtensionProperties(null as? ByteBuffer, extensionCount, extensionProperties)

            val enabledExtensions = ArrayList<String>()
            extensionProperties.forEach { ext ->
                if(ext.extensionNameString() in requestedExtensions) {
                    logger.info("Enabled OpenXR extension ${ext.extensionNameString()} ${ext.extensionVersion()}")
                    enabledExtensions.add(ext.extensionNameString())
                }
            }

            val buffer = MemoryUtil.memAllocPointer(enabledExtensions.size)
            enabledExtensions.forEach { buffer.put(MemoryUtil.memUTF8(it)) }
            return buffer
        }
    }

    /**
     * Returns the per-eye projection matrix
     *
     * @param[eye] The index of the eye
     * @return Matrix4f containing the per-eye projection matrix
     */
    override fun getEyeProjection(eye: Int, nearPlane: Float, farPlane: Float): Matrix4f {
        TODO("Not yet implemented")
    }

    /**
     * Returns the inter-pupillary distance (IPD)
     *
     * @return IPD as Float
     */
    override fun getIPD(): Float {
        TODO("Not yet implemented")
    }

    /**
     * Query the HMD whether a compositor is used or the renderer should take
     * care of displaying on the HMD on its own.
     *
     * @return True if the HMD has a compositor
     */
    override fun hasCompositor(): Boolean {
        TODO("Not yet implemented")
    }

    /**
     * Submit OpenGL texture IDs to the compositor. The texture is assumed to have the left eye in the
     * left half, right eye in the right half.
     *
     * @param[textureId] OpenGL Texture ID of the left eye texture
     */
    override fun submitToCompositor(textureId: Int) {
        TODO("Not yet implemented")
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
     * @param[queueFamilyIndex] Queue family index
     * @param[image] The Vulkan texture image to be presented to the compositor
     */
    override fun submitToCompositorVulkan(width: Int, height: Int, format: Int, instance: VkInstance, device: VulkanDevice, queue: VkQueue, image: Long) {
        TODO("Not yet implemented")
    }

    /**
     * Returns the optimal render target size for the HMD as 2D vector
     *
     * @return Render target size as 2D vector
     */
    override fun getRenderTargetSize(): Vector2i {
        TODO("Not yet implemented")
    }

    /** Event handler class */
    override var events: TrackerInputEventHandlers
        get() = TODO("Not yet implemented")
        set(value) {}

    /**
     * Returns the orientation of the HMD
     *
     * @returns Matrix4f with orientation
     */
    override fun getOrientation(): Quaternionf {
        TODO("Not yet implemented")
    }

    /**
     * Returns the orientation of the given device, or a unit quaternion if the device is not found.
     *
     * @returns Matrix4f with orientation
     */
    override fun getOrientation(id: String): Quaternionf {
        TODO("Not yet implemented")
    }

    /**
     * Returns the absolute position as Vector3f
     *
     * @return HMD position as Vector3f
     */
    override fun getPosition(): Vector3f {
        TODO("Not yet implemented")
    }

    /**
     * Returns the HMD pose
     *
     * @return HMD pose as Matrix4f
     */
    override fun getPose(): Matrix4f {
        TODO("Not yet implemented")
    }

    /**
     * Returns a list of poses for the devices [type] given.
     *
     * @return Pose as Matrix4f
     */
    override fun getPose(type: TrackedDeviceType): List<TrackedDevice> {
        TODO("Not yet implemented")
    }

    /**
     * Returns the HMD pose for a given eye.
     *
     * @param[eye] The eye to return the pose for.
     * @return HMD pose as Matrix4f
     */
    override fun getPoseForEye(eye: Int): Matrix4f {
        TODO("Not yet implemented")
    }

    /**
     * Check whether the HMD is initialized and working
     *
     * @return True if HMD is initialiased correctly and working properly
     */
    override fun initializedAndWorking(): Boolean {
        TODO("Not yet implemented")
    }

    /**
     * Check whether there is a working TrackerInput for this device.
     *
     * @returns the [TrackerInput] if that is the case, null otherwise.
     */
    override fun getWorkingTracker(): TrackerInput? {
        TODO("Not yet implemented")
    }

    /**
     * Loads a model representing the [TrackedDevice].
     *
     * @param[device] The device to load the model for.
     * @param[mesh] The [Mesh] to attach the model data to.
     */
    override fun loadModelForMesh(device: TrackedDevice, mesh: Mesh): Mesh {
        TODO("Not yet implemented")
    }

    /**
     * Loads a model representing a kind of [TrackedDeviceType].
     *
     * @param[type] The device type to load the model for, by default [TrackedDeviceType.Controller].
     * @param[mesh] The [Mesh] to attach the model data to.
     */
    override fun loadModelForMesh(type: TrackedDeviceType, mesh: Mesh): Mesh {
        TODO("Not yet implemented")
    }

    /**
     * Attaches a given [TrackedDevice] to a scene graph [Node], camera-relative in case [camera] is non-null.
     *
     * @param[device] The [TrackedDevice] to use.
     * @param[node] The node which should take tracking data from [device].
     * @param[camera] A camera, in case the node should also be added as a child to the camera.
     */
    override fun attachToNode(device: TrackedDevice, node: Node, camera: Camera?) {
        TODO("Not yet implemented")
    }

    /**
     * Returns all tracked devices a given type.
     *
     * @param[ofType] The [TrackedDeviceType] of the devices to return.
     * @return A [Map] of device name to [TrackedDevice]
     */
    override fun getTrackedDevices(ofType: TrackedDeviceType): Map<String, TrackedDevice> {
        TODO("Not yet implemented")
    }

    /**
     * update state
     */
    override fun update() {
        TODO("Not yet implemented")
    }

    /**
     * Returns a [List] of Vulkan instance extensions required by the device.
     *
     * @return [List] of strings containing the required instance extensions
     */
    override fun getVulkanInstanceExtensions(): List<String> {
        TODO("Not yet implemented")
    }

    /**
     * Returns a [List] of Vulkan device extensions required by the device.
     *
     * @return [List] of strings containing the required device extensions
     */
    override fun getVulkanDeviceExtensions(physicalDevice: VkPhysicalDevice): List<String> {
        TODO("Not yet implemented")
    }

    /**
     * Returns a [Display] instance, if working currently
     *
     * @return Either a [Display] instance, or null.
     */
    override fun getWorkingDisplay(): Display? {
        TODO("Not yet implemented")
    }

    /**
     * Returns the per-eye transform that moves from head to eye
     *
     * @param[eye] The eye index
     * @return Matrix4f containing the transform
     */
    override fun getHeadToEyeTransform(eye: Int): Matrix4f {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmStatic
        fun hololens(hub: Hub?): OpenXRHMD {
            return OpenXRHMD(hub,
            listOf(
                "XR_MSFT_holographic_remoting",
                "XR_MSFT_holographic_remoting_frame_mirroring",
                "XR_MSFT_holographic_remoting_speech",
                XR_KHR_COMPOSITION_LAYER_DEPTH_EXTENSION_NAME,
                XR_KHR_VULKAN_ENABLE_EXTENSION_NAME,
                "XR_MSFT_unbounded_reference_space",
                "XR_MSFT_spatial_anchor"
            )
                )
        }
    }


}
