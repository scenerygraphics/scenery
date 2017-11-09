package graphics.scenery.backends.vulkan

import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.memUTF8
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.util.ArrayList

class VulkanDevice(val instance: VkInstance, val physicalDevice: VkPhysicalDevice, val deviceData: DeviceData, extensionsQuery: (VkPhysicalDevice) -> Array<String> = { arrayOf() }, validationLayers: Array<String> = arrayOf()) {

    val logger by LazyLogger()
    val memoryProperties: VkPhysicalDeviceMemoryProperties
    val vulkanDevice: VkDevice
    val queueIndices: QueueIndices
    val extensions = ArrayList<String>()

    enum class DeviceType { Unknown, Other, IntegratedGPU, DiscreteGPU, VirtualGPU, CPU }
    data class DeviceData(val vendor: String, val name: String, val driverVersion: String, val apiVersion: String, val type: DeviceType)
    data class QueueIndices(val presentQueue: Int, val graphicsQueue: Int, val computeQueue: Int)

    init {
        val result = MemoryStack.stackPush().use { stack ->
            val pQueueFamilyPropertyCount = stack.callocInt(1)
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyPropertyCount, null)
            val queueCount = pQueueFamilyPropertyCount.get(0)
            val queueProps = VkQueueFamilyProperties.callocStack(queueCount, stack)
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyPropertyCount, queueProps)

            var graphicsQueueFamilyIndex = 0
            var computeQueueFamilyIndex = 0
            val presentQueueFamilyIndex = 0
            var index = 0

            while (index < queueCount) {
                if (queueProps.get(index).queueFlags() and VK_QUEUE_GRAPHICS_BIT != 0) {
                    graphicsQueueFamilyIndex = index
                }

                if (queueProps.get(index).queueFlags() and VK_QUEUE_COMPUTE_BIT != 0) {
                    computeQueueFamilyIndex = index
                }

                index++
            }

            val pQueuePriorities = stack.callocFloat(1).put(0, 0.0f)
            val queueCreateInfo = VkDeviceQueueCreateInfo.callocStack(1, stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(graphicsQueueFamilyIndex)
                .pQueuePriorities(pQueuePriorities)

            val extensionsRequested = extensionsQuery.invoke(physicalDevice)
            logger.debug("Requested extensions: ${extensionsRequested.joinToString(", ")} ${extensionsRequested.size}")
            val utf8Exts = extensionsRequested.map { stack.UTF8(it) }

            // allocate enough pointers for required extensions, plus the swapchain extension
            val extensions = stack.callocPointer(1 + extensionsRequested.size)
            extensions.put(stack.UTF8(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME))
            utf8Exts.forEach { extensions.put(it) }
            extensions.flip()

            if(validationLayers.isNotEmpty()) {
                logger.warn("Enabled Vulkan API validations. Expect degraded performance.")
            }

            val ppEnabledLayerNames = stack.callocPointer(validationLayers.size)
            var i = 0
            while (i < validationLayers.size) {
                ppEnabledLayerNames.put(memUTF8(validationLayers[i]))
                i++
            }
            ppEnabledLayerNames.flip()

            val enabledFeatures = VkPhysicalDeviceFeatures.calloc()
                .samplerAnisotropy(true)
                .largePoints(true)

            val deviceCreateInfo = VkDeviceCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pNext(MemoryUtil.NULL)
                .pQueueCreateInfos(queueCreateInfo)
                .ppEnabledExtensionNames(extensions)
                .ppEnabledLayerNames(ppEnabledLayerNames)
                .pEnabledFeatures(enabledFeatures)

            logger.debug("Creating device...")
            val pDevice = stack.callocPointer(1)
            val err = vkCreateDevice(physicalDevice, deviceCreateInfo, null, pDevice)
            val device = pDevice.get(0)

            if (err != VK_SUCCESS) {
                throw AssertionError("Failed to create device: " + VU.translate(err))
            }
            logger.debug("Device successfully created.")

            val memoryProperties = VkPhysicalDeviceMemoryProperties.calloc()
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties)

            VulkanRenderer.DeviceAndGraphicsQueueFamily(VkDevice(device, physicalDevice, deviceCreateInfo),
                graphicsQueueFamilyIndex, computeQueueFamilyIndex, presentQueueFamilyIndex, memoryProperties)

            Triple(VkDevice(device, physicalDevice, deviceCreateInfo),
                QueueIndices(presentQueueFamilyIndex, computeQueueFamilyIndex, graphicsQueueFamilyIndex),
                memoryProperties
            )
        }

        vulkanDevice = result.first
        queueIndices = result.second
        memoryProperties = result.third

        extensions.addAll(extensionsQuery.invoke(physicalDevice))
        extensions.add(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME)

        logger.debug("Created logical Vulkan device on ${deviceData.vendor} ${deviceData.name}")
    }

    fun close() {
        logger.debug("Closing device ${deviceData.vendor} ${deviceData.name}...")
        vkDeviceWaitIdle(vulkanDevice)
        vkDestroyDevice(vulkanDevice, null)
        logger.debug("Device closed.")

        memoryProperties.free()
    }

    companion object {
        val logger by LazyLogger()

        private fun toDeviceType(vkDeviceType: Int): DeviceType {
            return when(vkDeviceType) {
                0 -> DeviceType.Other
                1 -> DeviceType.IntegratedGPU
                2 -> DeviceType.DiscreteGPU
                3 -> DeviceType.VirtualGPU
                4 -> DeviceType.CPU
                else -> DeviceType.Unknown
            }
        }

        private fun vendorToString(vendor: Int): String =
            when(vendor) {
                0x1002 -> "AMD"
                0x10DE -> "Nvidia"
                0x8086 -> "Intel"
                else -> "(Unknown vendor)"
            }

        private fun decodeDriverVersion(version: Int) =
            Triple(
                version and 0xFFC00000.toInt() shr 22,
                version and 0x003FF000 shr 12,
                version and 0x00000FFF
            )

        private fun driverVersionToString(version: Int) =
            decodeDriverVersion(version).toList().joinToString(".")

        @JvmStatic fun fromPhysicalDevice(instance: VkInstance, physicalDeviceFilter: (Int, DeviceData) -> Boolean,
                                          additionalExtensions: (VkPhysicalDevice) -> Array<String> = { arrayOf() },
                                          validationLayers: Array<String> = arrayOf()): VulkanDevice {
            return MemoryStack.stackPush().use { stack ->

                val physicalDeviceCount = VU.getInt("Enumerate physical devices", 1,
                    { vkEnumeratePhysicalDevices(instance, this, null)} )

                if (physicalDeviceCount < 1) {
                    throw AssertionError("No Vulkan-compatible devices found!")
                }

                val physicalDevices = VU.getPointers("Getting Vulkan physical devices", physicalDeviceCount,
                    { vkEnumeratePhysicalDevices(instance, intArrayOf(physicalDeviceCount), this) })

                var devicePreference = 0

                logger.info("Physical devices: ")
                val properties: VkPhysicalDeviceProperties = VkPhysicalDeviceProperties.callocStack(stack)
                val deviceList = ArrayList<DeviceData>(10)

                for (i in 0 until physicalDeviceCount) {
                    val device = VkPhysicalDevice(physicalDevices.get(i), instance)

                    vkGetPhysicalDeviceProperties(device, properties)

                    val deviceData = DeviceData(
                        vendor = vendorToString(properties.vendorID()),
                        name = properties.deviceNameString(),
                        driverVersion = driverVersionToString(properties.driverVersion()),
                        apiVersion = driverVersionToString(properties.apiVersion()),
                        type = toDeviceType(properties.deviceType()))

                    if(physicalDeviceFilter.invoke(i, deviceData)) {
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

                val physicalDevice = VkPhysicalDevice(selectedDevice, instance)

                VulkanDevice(instance, physicalDevice, selectedDeviceData, additionalExtensions, validationLayers)
            }
        }
    }
}
