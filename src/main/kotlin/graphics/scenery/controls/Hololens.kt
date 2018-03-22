package graphics.scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.*
import graphics.scenery.backends.Display
import graphics.scenery.backends.vulkan.*
import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.memAllocInt
import org.lwjgl.system.MemoryUtil.memAllocLong
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.NVDedicatedAllocation.*
import org.lwjgl.vulkan.NVExternalMemory.*
import org.lwjgl.vulkan.NVExternalMemoryCapabilities.*
import org.lwjgl.vulkan.NVExternalMemoryWin32.*
import org.lwjgl.vulkan.NVWin32KeyedMutex.VK_STRUCTURE_TYPE_WIN32_KEYED_MUTEX_ACQUIRE_RELEASE_INFO_NV
import org.lwjgl.vulkan.VK10.*
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

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
    // BGR is native surface format and saves unnecessary conversions
    private val textureFormat = VK_FORMAT_B8G8R8A8_SRGB

    private val zmqContext = ZContext()
    private val zmqSocket = zmqContext.createSocket(ZMQ.REQ)

    private var commandBuffer: VulkanCommandBuffer? = null
    private var hololensCommandPool = -1L
    private var d3dSharedHandle = -1L
    private var memoryHandle = -1L
    private var d3dImage: VulkanTexture.VulkanImage? = null

    private val acqKeys = memAllocLong(1).put(0, 0)
    private val releaseKeys = memAllocLong(1).put(0, 0)
    private val memoryHandleBuffer = memAllocLong(1)
    private val acquireTimeout = memAllocInt(1).put(0, 1)

    private var leftProjection: GLMatrix? = null
    private var rightProjection: GLMatrix? = null

    private var poseLeft: GLMatrix = GLMatrix.getIdentity()
    private var poseRight: GLMatrix = GLMatrix.getIdentity()

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
        return poseLeft
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
        zmqSocket.send("ViewTransform")
        val matrixData = zmqSocket.recv()
        assert(matrixData.size == 128)

        val b0 = ByteBuffer.wrap(matrixData).order(ByteOrder.LITTLE_ENDIAN).limit(16 * 4) as ByteBuffer
        b0.asFloatBuffer().get(poseLeft.floatArray)

        (b0.position(16 * 4).limit(16 * 8) as ByteBuffer).asFloatBuffer().get(poseRight.floatArray)
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
        return when(eye) {
            0 -> leftProjection ?: GLMatrix().setPerspectiveProjectionMatrix(50.0f, 1.0f, nearPlane, farPlane)
            1 -> rightProjection ?: GLMatrix().setPerspectiveProjectionMatrix(50.0f, 1.0f, nearPlane, farPlane)
            else -> { logger.error("3rd eye, wtf?"); GLMatrix.getIdentity() }
        }
    }

    override fun getPoseForEye(eye: Int): GLMatrix {
        return when(eye) {
            0 -> poseLeft
            else -> poseRight
        }
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

    override fun loadModelForMesh(type: TrackedDeviceType, mesh: Mesh): Mesh {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun attachToNode(type: TrackedDeviceType, index: Int, node: Node, camera: Camera?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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

        if(leftProjection == null) {
            zmqSocket.send("LeftPR")
            val matrixData = zmqSocket.recv()
            assert(matrixData.size == 64)

            leftProjection = GLMatrix.getIdentity()
            ByteBuffer.wrap(matrixData).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(leftProjection!!.floatArray)
            logger.info("Hololens left projection: $leftProjection")
        }

        if(rightProjection == null) {
            zmqSocket.send("RightPR")
            val matrixData = zmqSocket.recv()
            assert(matrixData.size == 64)

            rightProjection = GLMatrix.getIdentity()
            ByteBuffer.wrap(matrixData).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(rightProjection!!.floatArray)
            logger.info("Hololens right projection: $rightProjection")
        }

        if(d3dSharedHandle == -1L || d3dImage == null) {
            zmqSocket.send("RegPid${SceneryBase.getProcessID()}")
            val handle = zmqSocket.recvStr()

            val address = BigInteger(handle, 16)
            d3dSharedHandle = address.toLong()
            logger.info("Registered D3D shared texture handle as ${d3dSharedHandle.toHexString()}/${address.toString(16)}")

            // VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D11_IMAGE_BIT_NV does not seem to work here
            val handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D11_IMAGE_BIT_NV
            val extProperties = VkExternalImageFormatPropertiesNV.calloc()

            val formatSupported = vkGetPhysicalDeviceExternalImageFormatPropertiesNV(
                device.physicalDevice,
                textureFormat,
                VK_IMAGE_TYPE_2D,
                VK_IMAGE_TILING_OPTIMAL,
                VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
                0,
                handleType,
                extProperties)

            if (formatSupported == VK_ERROR_FORMAT_NOT_SUPPORTED) {
                logger.error("Shared handles not supported, omfg!")
                return
            }

            if(formatSupported < 0) {
                logger.error("Something else went wrong: $formatSupported")
            }

            logger.debug("Can import these types: ")
            logger.debug(" VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D11_IMAGE_BIT_NV: ${extProperties.compatibleHandleTypes() and VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D11_IMAGE_BIT_NV != 0}")
            logger.debug(" VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D11_IMAGE_KMT_BIT_NV: ${extProperties.compatibleHandleTypes() and VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D11_IMAGE_KMT_BIT_NV != 0}")
            logger.debug(" VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT_NV : ${extProperties.compatibleHandleTypes() and VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT_NV != 0}")
            logger.debug(" VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_KMT_BIT_NV : ${extProperties.compatibleHandleTypes() and VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_KMT_BIT_NV != 0}")

            if (extProperties.externalMemoryFeatures() and VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT_NV == 0) {
                logger.error("Importable handles are not support, omfg! ${extProperties.externalMemoryFeatures()}")
                return
            }

            if(handleType and extProperties.compatibleHandleTypes() == 0) {
                logger.error("Requested import type not available! ${extProperties.compatibleHandleTypes()}")
                return
            }

            val extMemoryImageInfo = VkExternalMemoryImageCreateInfoNV.calloc()
                .sType(VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO_NV)
                .pNext(0)
                .handleTypes(handleType)

            val dedicatedAllocationCreateInfo = VkDedicatedAllocationImageCreateInfoNV.calloc()
                .sType(VK_STRUCTURE_TYPE_DEDICATED_ALLOCATION_IMAGE_CREATE_INFO_NV)
                .pNext(0)
                .dedicatedAllocation(false)

            if (extProperties.externalMemoryFeatures() and VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT_NV != 0) {
                logger.info("Platform requires dedicated allocation")

                extMemoryImageInfo.pNext(dedicatedAllocationCreateInfo.address())
                dedicatedAllocationCreateInfo.dedicatedAllocation(true)
            }

            val t = VulkanTexture(device, hololensCommandPool, queue,
                hololensDisplaySize.x().toInt(), hololensDisplaySize.y().toInt(), 1,
                textureFormat, 1, true, true)

            val imageCreateInfo = VkImageCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .pNext(extMemoryImageInfo.address())
                .imageType(VK_IMAGE_TYPE_2D)
                .format(textureFormat)
                .mipLevels(1)
                .arrayLayers(1)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .flags(0)

            imageCreateInfo.extent().set(hololensDisplaySize.x().toInt(), hololensDisplaySize.y().toInt(), 1)

            d3dImage = t.createImage(hololensDisplaySize.x().toInt(), hololensDisplaySize.y().toInt(), 1,
                VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_USAGE_SAMPLED_BIT,
                VK_IMAGE_TILING_OPTIMAL, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 1,
                imageCreateInfo = imageCreateInfo,
                customAllocator = { memoryRequirements, allocatedImage ->
                    logger.debug("Using custom image allocation for external handle ...")
                    val memoryTypeIndex = device.getMemoryType(memoryRequirements.memoryTypeBits(),
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)

                    if(memoryTypeIndex.isEmpty()) {
                        logger.error("Could not find suitable memory type")
                    } else {
                        logger.debug("Got memory types ${memoryTypeIndex.joinToString(", ")}")
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
                        .memoryTypeIndex(memoryTypeIndex.first())

                    val dedicatedAllocationInfo = VkDedicatedAllocationMemoryAllocateInfoNV.calloc()
                        .sType(VK_STRUCTURE_TYPE_DEDICATED_ALLOCATION_MEMORY_ALLOCATE_INFO_NV)
                        .pNext(0)

                    if (extProperties.externalMemoryFeatures() and VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT_NV != 0) {
                        logger.debug("Using VK_NV_dedicated_allocation")
                        dedicatedAllocationInfo.image(allocatedImage)
                        importMemoryInfo.pNext(dedicatedAllocationInfo.address())
                    }

                    logger.debug("Trying to allocate ${memoryRequirements.size()} bytes for shared texture")

                    memoryHandle = VU.getLong("Allocate memory for D3D shared image",
                        { vkAllocateMemory(device.vulkanDevice, memoryInfo, null, this) },
                        { dedicatedAllocationInfo.free(); memoryInfo.free(); importMemoryInfo.free(); })
                    memoryHandle
                })

            with(VU.newCommandBuffer(device, hololensCommandPool, autostart = true)) {
                VulkanTexture.transitionLayout(d3dImage!!.image,
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, 1,
                    srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                    dstStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                    commandBuffer = this)

                this
            }.endCommandBuffer(device, hololensCommandPool,
                queue, flush = true, dealloc = true)
        }

        if(commandBuffer == null) {
            commandBuffer = VulkanCommandBuffer(device, null, false)
        }

        // blit into D3D image
        if(commandBuffer!!.commandBuffer == null) {
            commandBuffer!!.commandBuffer = with(VU.newCommandBuffer(device, hololensCommandPool, autostart = true)) {
                MemoryStack.stackPush().use { stack ->
                    logger.info("Blitting image of size ${width}x$height")
                    val imageBlit = VkImageBlit.callocStack(1, stack)
                    val type = VK_IMAGE_ASPECT_COLOR_BIT

                    imageBlit.srcSubresource().set(type, 0, 0, 1)
                    imageBlit.srcOffsets(0).set(0, 0, 0)
                    imageBlit.srcOffsets(1).set(width, height, 1)

                    imageBlit.dstSubresource().set(type, 0, 0, 1)
                    imageBlit.dstOffsets(0).set(0, 0, 0)
                    imageBlit.dstOffsets(1).set(hololensDisplaySize.x().toInt(), hololensDisplaySize.y().toInt(), 1)

                    val subresourceRange = VkImageSubresourceRange.callocStack(stack)
                        .aspectMask(type)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1)

                    // transition source attachment
                    VulkanTexture.transitionLayout(image,
                        KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        subresourceRange = subresourceRange,
                        commandBuffer = this,
                        srcStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                        dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT
                    )

                    // transition destination attachment
                    VulkanTexture.transitionLayout(d3dImage!!.image,
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        subresourceRange = subresourceRange,
                        commandBuffer = this,
                        srcStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                        dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT
                    )

                    vkCmdBlitImage(this@with,
                        image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        d3dImage!!.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        imageBlit, VK_FILTER_NEAREST
                    )

                    // transition destination attachment back to attachment
                    VulkanTexture.transitionLayout(d3dImage!!.image,
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        subresourceRange = subresourceRange,
                        commandBuffer = this,
                        srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                        dstStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                    )

                    // transition source attachment back to shader read-only
                    VulkanTexture.transitionLayout(image,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                        subresourceRange = subresourceRange,
                        commandBuffer = this,
                        srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                        dstStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                    )
                }

                this
            }

            commandBuffer?.commandBuffer?.endCommandBuffer(device, hololensCommandPool, queue,
                flush = false, dealloc = false, submitInfoPNext = null)
        }

        memoryHandleBuffer.put(0, memoryHandle)

        val keyedMutex = VkWin32KeyedMutexAcquireReleaseInfoNV.calloc()
            .sType(VK_STRUCTURE_TYPE_WIN32_KEYED_MUTEX_ACQUIRE_RELEASE_INFO_NV)
            .pNext(0)
            .acquireCount(1)
            .pAcquireSyncs(memoryHandleBuffer)
            .pAcquireKeys(acqKeys)
            .pAcquireTimeoutMilliseconds(acquireTimeout)
            .releaseCount(1)
            .pReleaseKeys(releaseKeys)
            .pReleaseSyncs(memoryHandleBuffer)

        commandBuffer?.commandBuffer?.submit(queue, submitInfoPNext = keyedMutex)
    }

    private fun checkSocketResponse(response: ByteArray) {
        if(String(response, Charset.defaultCharset()) != "kthxbye") {
            logger.error("Did not receive expected response!")
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
