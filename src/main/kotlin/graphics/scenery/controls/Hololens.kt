package graphics.scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.SceneryBase
import graphics.scenery.backends.Display
import graphics.scenery.backends.vulkan.VU
import graphics.scenery.backends.vulkan.VulkanDevice
import graphics.scenery.backends.vulkan.VulkanTexture
import graphics.scenery.backends.vulkan.toHexString
import graphics.scenery.utils.LazyLogger
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.NVDedicatedAllocation.VK_STRUCTURE_TYPE_DEDICATED_ALLOCATION_IMAGE_CREATE_INFO_NV
import org.lwjgl.vulkan.NVDedicatedAllocation.VK_STRUCTURE_TYPE_DEDICATED_ALLOCATION_MEMORY_ALLOCATE_INFO_NV
import org.lwjgl.vulkan.NVExternalMemory.VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO_NV
import org.lwjgl.vulkan.NVExternalMemoryCapabilities.*
import org.lwjgl.vulkan.NVExternalMemoryWin32.VK_STRUCTURE_TYPE_IMPORT_MEMORY_WIN32_HANDLE_INFO_NV
import org.lwjgl.vulkan.VK10.*
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.math.BigInteger

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

    private val zmqContext = ZContext()
    private val zmqSocket = zmqContext.createSocket(ZMQ.REQ)

    private var hololensCommandPool = -1L
    private var d3dSharedHandle = -1L
    private var d3dImage: VulkanTexture.VulkanImage? = null

    init {
        zmqSocket.connect("tcp://localhost:1339")
    }

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
        return GLMatrix().setPerspectiveProjectionMatrix(50.0f, 1.0f,  nearPlane, farPlane )
    }

    /**
     * Returns the inter-pupillary distance (IPD)
     *
     * @return IPD as Float
     */
    override fun getIPD(): Float {
        return 0.05f
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
        if(hololensCommandPool == -1L) {
            hololensCommandPool = device.createCommandPool(device.queueIndices.graphicsQueue)
        }

        if(d3dSharedHandle == -1L || d3dImage == null) {
            zmqSocket.send("RegPid${SceneryBase.getProcessID()}")
            val handle = zmqSocket.recvStr()

            val address = BigInteger(handle, 16)
            d3dSharedHandle = address.toLong()
            logger.info("Registered D3D shared texture handle as ${d3dSharedHandle.toHexString()}/${address.toString(16)}")

            val handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D11_IMAGE_BIT_NV
            val extProperties = VkExternalImageFormatPropertiesNV.calloc()

            val formatSupported = vkGetPhysicalDeviceExternalImageFormatPropertiesNV(
                device.physicalDevice,
                VK_FORMAT_R8G8B8A8_UNORM,
                VK_IMAGE_TYPE_2D,
                VK_IMAGE_TILING_OPTIMAL,
                VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
                0,
                handleType,
                extProperties)

            if (formatSupported == VK_ERROR_FORMAT_NOT_SUPPORTED) {
                logger.error("Shared handles not supported, omfg!")

                return
            }

            if (extProperties.externalMemoryFeatures() and VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT_NV != VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT_NV) {
                logger.error("Importable handles are not support, omfg! ${extProperties.externalMemoryFeatures()}")
            }

            val extMemoryImageInfo = VkExternalMemoryImageCreateInfoNV.calloc()
                .sType(VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO_NV)
                .pNext(0)
                .handleTypes(handleType)

            val dedicatedAllocationCreateInfo = VkDedicatedAllocationImageCreateInfoNV.calloc()
                .sType(VK_STRUCTURE_TYPE_DEDICATED_ALLOCATION_IMAGE_CREATE_INFO_NV)
                .pNext(0)
                .dedicatedAllocation(false)

            if (extProperties.externalMemoryFeatures() and VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT_NV == VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT_NV) {
                logger.info("Platform requires dedicated allocation")

                extMemoryImageInfo.pNext(dedicatedAllocationCreateInfo.address())
                dedicatedAllocationCreateInfo.dedicatedAllocation(true)
            }

            val t = VulkanTexture(device, hololensCommandPool, queue,
                hololensDisplaySize.x().toInt(), hololensDisplaySize.y().toInt(), 1,
                VK_FORMAT_R8G8B8A8_UNORM, 1, true, true)

            d3dImage = t.createImage(hololensDisplaySize.x().toInt(), hololensDisplaySize.y().toInt(), 1,
                VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
                VK_IMAGE_TILING_OPTIMAL, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 1,
                imageCreateInfoPNext = extMemoryImageInfo.address(),
                customAllocator = { memoryRequirements ->
                    logger.debug("Using custom image allocation for external handle ...")
                    val memoryTypeIndex = device.getMemoryType(memoryRequirements.memoryTypeBits(),
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)

                    if(memoryTypeIndex.first == false) {
                        logger.error("Could not find suitable memory type")
                    } else {
                        logger.debug("Got memory type ${memoryTypeIndex.second}")
                    }

                    val importMemoryInfo = VkImportMemoryWin32HandleInfoNV.calloc()
                        .sType(VK_STRUCTURE_TYPE_IMPORT_MEMORY_WIN32_HANDLE_INFO_NV)
                        .pNext(0)
                        .handleType(handleType)
                        .handle(d3dSharedHandle)

                    val memoryInfo = VkMemoryAllocateInfo.calloc()
                        .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                        .pNext(importMemoryInfo.address())
                        .allocationSize(memoryRequirements.size())
                        .memoryTypeIndex(memoryTypeIndex.second)

                    val dedicatedAllocationInfo = VkDedicatedAllocationMemoryAllocateInfoNV.calloc()
                        .sType(VK_STRUCTURE_TYPE_DEDICATED_ALLOCATION_MEMORY_ALLOCATE_INFO_NV)

                    if (extProperties.externalMemoryFeatures() and VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT_NV == VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT_NV) {
                        logger.debug("Using VK_NV_dedicated_allocation")
                        dedicatedAllocationInfo.image(d3dImage!!.image)
                        importMemoryInfo.pNext(dedicatedAllocationInfo.address())
                    }

                    VU.getLong("Allocate memory for D3D shared image",
                        { vkAllocateMemory(device.vulkanDevice, memoryInfo, null, this) },
                        { dedicatedAllocationInfo.free(); memoryInfo.free(); importMemoryInfo.free(); })
                })
        }
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
