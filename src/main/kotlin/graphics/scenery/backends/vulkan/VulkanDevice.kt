package graphics.scenery.backends.vulkan

import glm_.set
import glm_.toHexString
import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryStack.stackGet
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import vkk.*

/**
 * Describes a Vulkan device attached to an [instance] and a [physicalDevice].
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
open class VulkanDevice(val instance: VkInstance,
                        val physicalDevice: VkPhysicalDevice,
                        val deviceData: DeviceData,
                        extensionsQuery: (VkPhysicalDevice) -> ArrayList<String> = { arrayListOf() },
                        validationLayers: ArrayList<String> = arrayListOf(),
                        headless: Boolean = false) {

    protected val logger by LazyLogger()
    /** Stores available memory types on the device. */
    val memoryProperties = VkPhysicalDeviceMemoryProperties.calloc()
    /** Stores the Vulkan-internal device. */
    val vulkanDevice: VkDevice
    /** Stores available queue indices. */
    val queueIndices: QueueIndices
    /** Stores available extensions */
    val extensions = ArrayList<String>()

    /**
     * Class to store device-specific metadata.
     *
     * @property[vendor] The vendor name of the device.
     * @property[name] The name of the device.
     * @property[driverVersion] The driver version as represented as string.
     * @property[apiVersion] The Vulkan API version supported by the device, represented as string.
     * @property[type] The [DeviceType] of the GPU.
     */
    data class DeviceData(val vendor: String, val name: String, val driverVersion: String, val apiVersion: String, val type: VkPhysicalDeviceType)

    /**
     * Data class to store device-specific queue indices.
     *
     * @property[presentQueue] The index of the present queue
     * @property[graphicsQueue] The index of the graphics queue
     * @property[computeQueue] The index of the compute queue
     */
    data class QueueIndices(val presentQueue: Int, val transferQueue: Int, val graphicsQueue: Int, val computeQueue: Int)

    init {

        val queueProps = physicalDevice.queueFamilyProperties

        var graphicsQueueFamilyIndex = 0
        var transferQueueFamilyIndex = 0
        var computeQueueFamilyIndex = 0
        val presentQueueFamilyIndex = 0

        for (index in queueProps.indices)
            queueProps[index].apply {
                if (queueFlags has VkQueueFlag.GRAPHICS_BIT)
                    graphicsQueueFamilyIndex = index

                if (queueFlags has VkQueueFlag.TRANSFER_BIT)
                    transferQueueFamilyIndex = index

                if (queueFlags has VkQueueFlag.COMPUTE_BIT)
                    computeQueueFamilyIndex = index
            }

        val requiredFamilies = listOf(
            graphicsQueueFamilyIndex,
            transferQueueFamilyIndex,
            computeQueueFamilyIndex)
            .groupBy { it }

        logger.info("Creating ${requiredFamilies.size} distinct queue groups")

        /**
         * Adjusts the queue count of a [VkDeviceQueueCreateInfo] struct to [num].
         */
        fun VkDeviceQueueCreateInfo.queueCount(num: Int): VkDeviceQueueCreateInfo {
            VkDeviceQueueCreateInfo.nqueueCount(this.address(), num)
            return this
        }

        val queueInfos = vk.DeviceQueueCreateInfo(requiredFamilies.size)

        requiredFamilies.entries.forEachIndexed { i, (familyIndex, group) ->
            logger.debug("Adding queue with familyIndex=$familyIndex, size=${group.size}")

            val pQueuePriorities = stackGet().callocFloat(group.size)
            for (pr in 0 until group.size) {
                pQueuePriorities[pr] = 1f
            }

            queueInfos[i].apply {
                queueFamilyIndex = familyIndex
                queuePriorities = pQueuePriorities
            }
        }

        val extensionsRequested = extensionsQuery(physicalDevice)
        logger.debug("Requested extensions: ${extensionsRequested.joinToString()} ${extensionsRequested.size}")

        // if we are not running in headless mode, add swapchain extension
        if (!headless)
            extensionsRequested += KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME

        if (validationLayers.isNotEmpty()) {
            logger.warn("Enabled Vulkan API validations. Expect degraded performance.")
        }

        val features = vk.PhysicalDeviceFeatures {
            samplerAnisotropy = true
            largePoints = true
        }
        val deviceCreateInfo = vk.DeviceCreateInfo {
            queueCreateInfos = queueInfos
            enabledExtensionNames = extensionsRequested
            enabledLayerNames = validationLayers
            enabledFeatures = features
        }
        logger.debug("Creating device...")
        val (device, err) = physicalDevice createDevice deviceCreateInfo
        vulkanDevice = device

        if (err != SUCCESS) {
            throw IllegalStateException("Failed to create device: ${err.description}")
        }
        logger.debug("Device successfully created.")

        vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties)

        queueIndices = QueueIndices(
            presentQueue = presentQueueFamilyIndex,
            transferQueue = transferQueueFamilyIndex,
            computeQueue = computeQueueFamilyIndex,
            graphicsQueue = graphicsQueueFamilyIndex)

        extensions += extensionsQuery(physicalDevice)
        extensions += KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME

        logger.debug("Created logical Vulkan device on ${deviceData.vendor} ${deviceData.name}")
    }

    /**
     * Returns the available memory types on this devices that
     * bear [typeBits] and [flags]. May return an empty list in case
     * the device does not support the given types and flags.
     */
    fun getMemoryType(typeBits: Int, flags: Int): List<Int> {
        var bits = typeBits
        val types = ArrayList<Int>(5)

        for (i in 0 until memoryProperties.memoryTypeCount) {
            if (bits and 1 == 1) {
                if (memoryProperties.memoryTypes[i].propertyFlags and flags == flags) {
                    types += i
                }
            }

            bits = bits shr 1
        }

        if (types.isEmpty()) {
            logger.warn("Memory type $flags not found for device $this (${vulkanDevice.adr.toHexString}")
        }

        return types
    }

    /**
     * Creates a command pool with a given [queueNodeIndex] for this device.
     */
    fun createCommandPool(queueNodeIndex: Int): VkCommandPool {

        val cmdPoolInfo = vk.CommandPoolCreateInfo {
            queueFamilyIndex = queueNodeIndex
            flags = VkCommandPoolCreate.RESET_COMMAND_BUFFER_BIT.i
        }

        return vulkanDevice createCommandPool cmdPoolInfo
    }

    /**
     * Returns a string representation of this device.
     */
    override fun toString(): String {
        return "${deviceData.vendor} ${deviceData.name}"
    }

    /**
     * Destroys this device, waiting for all operations to finish before.
     */
    fun close() {
        logger.debug("Closing device ${deviceData.vendor} ${deviceData.name}...")
        vulkanDevice.waitIdle()
        vulkanDevice.destroy()
        logger.debug("Device closed.")

        memoryProperties.free()
    }

    /**
     * Utility functions for [VulkanDevice].
     */
    companion object {
        val logger by LazyLogger()

        /**
         * Data class for defining device/driver-specific workarounds.
         *
         * @property[filter] A lambda to define the condition to trigger this workaround, must return a boolean.
         * @property[description] A string description of the cause and effects of the workaround
         * @property[workaround] A lambda that will be executed if this [DeviceWorkaround] is triggered.
         */
        data class DeviceWorkaround(val filter: (DeviceData) -> Boolean, val description: String, val workaround: (DeviceData) -> Any)


        val deviceWorkarounds: List<DeviceWorkaround> = listOf(
//            DeviceWorkaround(
//                { it.vendor == "Nvidia" && it.driverVersion.substringBefore(".").toInt() >= 396 },
//                "Nvidia 396.xx series drivers are unsupported due to crashing bugs in the driver") {
//                if(System.getenv("__GL_NextGenCompiler") == null) {
//                    logger.warn("The graphics driver version you are using (${it.driverVersion}) contains a bug that prevents scenery's Vulkan renderer from functioning correctly.")
//                    logger.warn("Please set the environment variable __GL_NextGenCompiler to 0 and restart the application to work around this issue.")
//                    logger.warn("For this session, scenery will fall back to the OpenGL renderer in 20 seconds.")
//                    Thread.sleep(20000)
//
//                    throw RuntimeException("Bug in graphics driver, falling back to OpenGL")
//                }
//            }
        )

        /**
         * Creates a [VulkanDevice] in a given [instance] from a physical device, requesting extensions
         * given by [additionalExtensions]. The device selection is done in a fuzzy way by [physicalDeviceFilter],
         * such that one can filter for certain vendors, e.g.
         */
        @JvmStatic
        fun fromPhysicalDevice(instance: VkInstance, physicalDeviceFilter: (Int, DeviceData) -> Boolean,
                               additionalExtensions: (VkPhysicalDevice) -> ArrayList<String> = { arrayListOf() },
                               validationLayers: ArrayList<String> = arrayListOf(),
                               headless: Boolean = false): VulkanDevice {
            return stackPush().use { stack ->

                val physicalDeviceCount = VU.getInt("Enumerate physical devices") {
                    vkEnumeratePhysicalDevices(instance, this, null)
                }

                if (physicalDeviceCount < 1) {
                    throw IllegalStateException("No Vulkan-compatible devices found!")
                }

                val physicalDevices = VU.getPointers("Getting Vulkan physical devices", physicalDeviceCount) {
                    vkEnumeratePhysicalDevices(instance, intArrayOf(physicalDeviceCount), this)
                }

                var devicePreference = 0

                logger.info("Physical devices: ")
                val properties: VkPhysicalDeviceProperties = VkPhysicalDeviceProperties.callocStack(stack)
                val deviceList = ArrayList<DeviceData>(10)

                for (i in 0 until physicalDeviceCount) {
                    val device = VkPhysicalDevice(physicalDevices.get(i), instance)

                    vkGetPhysicalDeviceProperties(device, properties)

                    val deviceData = DeviceData(
                        vendor = properties.vendor.toString(),
                        name = properties.deviceNameString(),
                        driverVersion = properties.driverVersionString,
                        apiVersion = properties.apiVersionString,
                        type = properties.deviceType)

                    if (physicalDeviceFilter.invoke(i, deviceData)) {
                        devicePreference = i
                    }

                    deviceList.add(deviceData)
                }

                deviceList.forEachIndexed { i, device ->
                    val selected = if (devicePreference == i) {
                        "(selected)"
                    } else {
                        ""
                    }

                    logger.info("  $i: ${device.vendor} ${device.name} (${device.type}, driver version ${device.driverVersion}, Vulkan API ${device.apiVersion}) $selected")
                }

                val selectedDevice = physicalDevices.get(devicePreference)
                val selectedDeviceData = deviceList[devicePreference]

                if (System.getProperty("scenery.DisableDeviceWorkarounds", "false")?.toBoolean() != true) {
                    deviceWorkarounds.forEach {
                        if (it.filter.invoke(selectedDeviceData)) {
                            logger.warn("Workaround activated: ${it.description}")
                            it.workaround.invoke(selectedDeviceData)
                        }
                    }
                } else {
                    logger.warn("Device-specific workarounds disabled upon request, expect weird things to happen.")
                }

                val physicalDevice = VkPhysicalDevice(selectedDevice, instance)

                physicalDevices.free()

                VulkanDevice(instance, physicalDevice, selectedDeviceData, additionalExtensions, validationLayers, headless)
            }
        }
    }
}
