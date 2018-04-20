package graphics.scenery.backends.vulkan

import glfw_.appBuffer
import graphics.scenery.backends.vulkan.VU.createDescriptorSetLayout
import graphics.scenery.utils.LazyLogger
import org.lwjgl.vulkan.*
import vkn.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class VulkanDevice(
    val instance: VkInstance,
    val physicalDevice: VkPhysicalDevice,
    val deviceData: DeviceData,
    extensionsQuery: (VkPhysicalDevice) -> Array<String> = { arrayOf() },
    validationLayers: Collection<String> = listOf()) {

    val logger by LazyLogger()
    val memoryProperties = VkPhysicalDeviceMemoryProperties.calloc()
    val vulkanDevice: VkDevice
    val queueIndices: QueueIndices
    val extensions = ArrayList<String>()

    data class DeviceData(val vendor: String, val name: String, val driverVersion: String, val apiVersion: String, val type: VkPhysicalDeviceType)
    data class QueueIndices(val presentQueue: Int, val graphicsQueue: Int, val computeQueue: Int)

    init {

        val queueProps = physicalDevice.queueFamilyProperties

        var graphicsQueueFamilyIndex = 0
        var computeQueueFamilyIndex = 0
        val presentQueueFamilyIndex = 0
        var index = 0

        while (index < queueProps.size) {
            if (queueProps[index].queueFlags has VkQueueFlag.GRAPHICS_BIT)
                graphicsQueueFamilyIndex = index

            if (queueProps[index].queueFlags has VkQueueFlag.COMPUTE_BIT)
                computeQueueFamilyIndex = index

            index++
        }

        val queueCreateInfo = vk.DeviceQueueCreateInfo(1) {
            queueFamilyIndex = graphicsQueueFamilyIndex
            queuePriorities = appBuffer.floatBufferOf(0f)
        }

        val extensionsRequested = extensionsQuery(physicalDevice)
        logger.debug("Requested extensions: ${extensionsRequested.joinToString()} ${extensionsRequested.size}")

        // allocate enough pointers for required extensions, plus the swapchain extension
        val extensions = arrayListOf(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME)
        extensions += extensionsRequested

        if (validationLayers.isNotEmpty())
            logger.warn("Enabled Vulkan API validations. Expect degraded performance.")

        val enabledFeatures = vk.PhysicalDeviceFeatures {
            samplerAnisotropy = true
            largePoints = true
        }

        val deviceCreateInfo = vk.DeviceCreateInfo {
            this.queueCreateInfos = queueCreateInfo
            enabledExtensionNames = extensions
            enabledLayerNames = validationLayers
            this.enabledFeatures = enabledFeatures
        }

        logger.debug("Creating device...")
        vulkanDevice = physicalDevice createDevice deviceCreateInfo
        logger.debug("Device successfully created.")

        physicalDevice getMemoryProperties memoryProperties

        VulkanRenderer.DeviceAndGraphicsQueueFamily(vulkanDevice,
            graphicsQueueFamilyIndex, computeQueueFamilyIndex, presentQueueFamilyIndex, memoryProperties)

        queueIndices = QueueIndices(
            presentQueue = presentQueueFamilyIndex,
            computeQueue = computeQueueFamilyIndex,
            graphicsQueue = graphicsQueueFamilyIndex)

        logger.debug("Created logical Vulkan device on ${deviceData.vendor} ${deviceData.name}")
    }

    fun getMemoryType(typeBits: Int, flags: VkMemoryPropertyFlags): List<Int> {
        var bits = typeBits
        val types = ArrayList<Int>(5)

        for (i in 0 until memoryProperties.memoryTypeCount) {
            if (bits and 1 == 1) {
                if ((memoryProperties.memoryTypes(i).propertyFlags and flags) == flags)
                    types += i
            }

            bits = bits shr 1
        }

        if (types.isEmpty())
            logger.warn("Memory type $flags not found for device $this (${vulkanDevice.adr.toHexString()}")

        return types
    }

    infix fun createCommandPool(queueNodeIndex: Int): VkCommandPool {

        val cmdPoolInfo = vk.CommandPoolCreateInfo {
            queueFamilyIndex = queueNodeIndex
            flags = VkCommandPoolCreate.RESET_COMMAND_BUFFER_BIT.i
        }

        return vulkanDevice createCommandPool cmdPoolInfo
    }

    // helper vars
    private var MAX_TEXTURES = 2048 * 16
    private var MAX_UBOS = 2048
    private var MAX_INPUT_ATTACHMENTS = 32
    // end helper vars

    fun createDescriptorPool(): VkDescriptorPool {

        // We need to tell the API the number of max. requested descriptors per type
        val typeCounts = vk.DescriptorPoolSize(4)
            .appyAt(0) {
                type = VkDescriptorType.COMBINED_IMAGE_SAMPLER
                descriptorCount = MAX_TEXTURES
            }
            .appyAt(1) {
                type = VkDescriptorType.UNIFORM_BUFFER_DYNAMIC
                descriptorCount = MAX_UBOS
            }.appyAt(2) {
                type = VkDescriptorType.INPUT_ATTACHMENT
                descriptorCount = MAX_INPUT_ATTACHMENTS
            }.appyAt(3) {
                type = VkDescriptorType.UNIFORM_BUFFER
                descriptorCount = MAX_UBOS
            }

        // Create the global descriptor pool
        // All descriptors used in this example are allocated from this pool
        return vulkanDevice createDescriptorPool vk.DescriptorPoolCreateInfo {
            poolSizes = typeCounts
            maxSets = MAX_TEXTURES + MAX_UBOS + MAX_INPUT_ATTACHMENTS + MAX_UBOS // Set the max. number of sets that can be requested
            flags = VkDescriptorPoolCreate.FREE_DESCRIPTOR_SET_BIT.i
        }
    }

    fun prepareDefaultDescriptorSetLayouts(): ConcurrentHashMap<String, VkDescriptorSetLayout> {

        val m = ConcurrentHashMap<String, Long>()

        with(vulkanDevice) {

            m["Matrices"] = createDescriptorSetLayout(
                listOf(VkDescriptorType.UNIFORM_BUFFER_DYNAMIC to 1),
                0, // TODO parametrize
                VkShaderStage.ALL.i)

            m["MaterialProperties"] = createDescriptorSetLayout(
                listOf(VkDescriptorType.UNIFORM_BUFFER_DYNAMIC to 1),
                0,
                VkShaderStage.ALL.i)

            m["LightParameters"] = createDescriptorSetLayout(
                listOf(VkDescriptorType.UNIFORM_BUFFER_DYNAMIC to 1),
                0,
                VkShaderStage.ALL.i)

            m["ObjectTextures"] = createDescriptorSetLayout(
                listOf(
                    VkDescriptorType.COMBINED_IMAGE_SAMPLER to 6,
                    VkDescriptorType.COMBINED_IMAGE_SAMPLER to 1),
                0,
                VkShaderStage.ALL.i)

            m["VRParameters"] = createDescriptorSetLayout(
                listOf(VkDescriptorType.UNIFORM_BUFFER_DYNAMIC to 1),
                0,
                VkShaderStage.ALL.i)
        }
        return m
    }

    infix fun prepareDefaultBuffers(bufferStorage: ConcurrentHashMap<String, VulkanBuffer>) {

        logger.debug("Creating buffers")

        val usage: VkBufferUsageFlags = VkBufferUsage.UNIFORM_BUFFER_BIT.i
        val properties = VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT
        val aligned = true

        bufferStorage["UBOBuffer"] = VulkanBuffer(this, 512 * 1024 * 10, usage, properties, aligned)
        logger.debug("Created UBO buffer")

        bufferStorage["LightParametersBuffer"] = VulkanBuffer(this, 512 * 1024 * 10, usage, properties, aligned)
        logger.debug("Created light buffer")

        bufferStorage["VRParametersBuffer"] = VulkanBuffer(this, 256 * 10, usage, properties, aligned)
        logger.debug("Created VRP buffer")

        bufferStorage["ShaderPropertyBuffer"] = VulkanBuffer(this, 1024 * 1024, usage, properties, aligned)
        logger.debug("Created all buffers")
    }

    infix fun createFramebuffer(info: VkFramebufferCreateInfo): VkFramebuffer = vulkanDevice createFramebuffer info

    infix fun getQueue(queueFamilyIndex: Int): VkQueue = vulkanDevice getQueue queueFamilyIndex

    fun destroyCommandPool(commandPool: VkCommandPool) = vulkanDevice destroyCommandPool commandPool

    override fun toString(): String = "${deviceData.vendor} ${deviceData.name}"

    fun close() {
        logger.debug("Closing device ${deviceData.vendor} ${deviceData.name}...")
        vulkanDevice.waitIdle()
        vulkanDevice.destroy()
        logger.debug("Device closed.")

        memoryProperties.free()
    }

    companion object {
        val logger by LazyLogger()

        @JvmStatic
        fun fromPhysicalDevice(instance: VkInstance, physicalDeviceFilter: (Int, DeviceData) -> Boolean,
                               additionalExtensions: (VkPhysicalDevice) -> Array<String> = { arrayOf() },
                               validationLayers: Collection<String> = listOf()): VulkanDevice {

            val physicalDevices = instance.enumeratePhysicalDevices()

            var devicePreference = 0

            logger.info("Physical devices: ")
            val properties = vk.PhysicalDeviceProperties()
            val deviceList = ArrayList<DeviceData>(10)

            for (i in physicalDevices.indices) {

                val device = physicalDevices[i]

                device getProperties properties

                val deviceData = DeviceData(
                    vendor = properties.vendorName,
                    name = properties.deviceName,
                    driverVersion = properties.driverVersionString,
                    apiVersion = properties.apiVersionString,
                    type = properties.deviceType)

                if (physicalDeviceFilter(i, deviceData))
                    devicePreference = i

                deviceList += deviceData
            }

            deviceList.forEachIndexed { i, device ->
                val selected = if (devicePreference == i) "(selected)" else ""
                logger.info("  $i: ${device.vendor} ${device.name} (${device.type}, driver version ${device.driverVersion}, Vulkan API ${device.apiVersion}) $selected")
            }

            val selectedDevice = physicalDevices[devicePreference]
            val selectedDeviceData = deviceList[devicePreference]

            return VulkanDevice(instance, selectedDevice, selectedDeviceData, additionalExtensions, validationLayers)
        }
    }
}
