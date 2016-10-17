package scenery.backends.vulkan

import cleargl.GLMatrix
import cleargl.GLVector
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWKeyCallback
import org.lwjgl.glfw.GLFWVulkan.*
import org.lwjgl.glfw.GLFWWindowSizeCallback
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugReport.*
import org.lwjgl.vulkan.KHRSurface.*
import org.lwjgl.vulkan.KHRSwapchain.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VKUtil.VK_MAKE_VERSION
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scenery.*
import scenery.backends.Renderer
import java.io.IOException
import java.nio.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class VulkanRenderer : Renderer {
    override var hub: Hub? = null
    override var settings: Settings = Settings()
    protected var logger: Logger = LoggerFactory.getLogger("VulkanRenderer")
    override var shouldClose = false

    override fun reshape(width: Int, height: Int) {
    }

    private var swapchain: Swapchain? = null
    private var framebuffers: LongArray? = null
    override var width: Int = 0
    override var height: Int = 0
    private var renderCommandBuffers: Array<VkCommandBuffer>? = null

    private val validation = java.lang.Boolean.parseBoolean(System.getProperty("vulkan.validation", "false"))

    private val layers = arrayOf<ByteBuffer>(memUTF8("VK_LAYER_LUNARG_standard_validation"))

    /**
     * Remove if added to spec.
     */
    private val VK_FLAGS_NONE: Int = 0

    /**
     * This is just -1L, but it is nicer as a symbolic constant.
     */
    private val UINT64_MAX: Long = -1L

    var instance: VkInstance
    var debugCallback = object : VkDebugReportCallbackEXT() {
        override operator fun invoke(flags: Int, objectType: Int, obj: Long, location: Long, messageCode: Int, pLayerPrefix: Long, pMessage: Long, pUserData: Long): Int {
            logger.error("ERROR OCCURED: " + VkDebugReportCallbackEXT.getString(pMessage))
            return 0
        }
    }
    var debugCallbackHandle: Long
    var physicalDevice: VkPhysicalDevice
    var deviceAndGraphicsQueueFamily: DeviceAndGraphicsQueueFamily
    var device: VkDevice
    var queueFamilyIndex: Int
    var memoryProperties: VkPhysicalDeviceMemoryProperties

    var surface: Long

    var semaphoreCreateInfo: VkSemaphoreCreateInfo
    var submitInfo: VkSubmitInfo

    // Info struct to present the current swapchain image to the display
    var presentInfo: VkPresentInfoKHR

    // Create static Vulkan resources
    var colorFormatAndSpace: ColorFormatAndSpace
    var commandPool: Long
    var setupCommandBuffer: VkCommandBuffer
    var postPresentCommandBuffer: VkCommandBuffer
    var queue: VkQueue
    var renderPass: Long
    var renderCommandPool: Long
    var vertices: Vertices
    var uboDescriptor: UboDescriptor
    var descriptorPool: Long
    var descriptorSetLayout: Long
    var descriptorSet: Long
    var pipeline: Pipeline

    var renderPipelines = ConcurrentHashMap<String, Pipeline>()

    var pSwapchains: LongBuffer
    var pImageAcquiredSemaphore: LongBuffer
    var pRenderCompleteSemaphore: LongBuffer
    var pCommandBuffers: PointerBuffer
    var pImageIndex: IntBuffer

    var window: Long

    var lastTime = System.nanoTime()
    var time = 0.0f

    val swapchainRecreator: SwapchainRecreator

    class DeviceAndGraphicsQueueFamily {
        internal var device: VkDevice? = null
        internal var queueFamilyIndex: Int = 0
        internal var memoryProperties: VkPhysicalDeviceMemoryProperties? = null
    }

    class ColorFormatAndSpace {
        internal var colorFormat: Int = 0
        internal var colorSpace: Int = 0
    }

    private class Swapchain {
        internal var swapchainHandle: Long = 0
        internal var images: LongArray? = null
        internal var imageViews: LongArray? = null
    }

    class Vertices {
        internal var verticesBuf: Long = 0
        internal var createInfo: VkPipelineVertexInputStateCreateInfo? = null
    }

    class UboDescriptor {
        internal var memory: Long = 0
        internal var allocationSize: Long = 0
        internal var buffer: Long = 0
        internal var offset: Long = 0
        internal var range: Long = 0
    }

    class Pipeline {
        internal var pipeline: Long = 0
        internal var layout: Long = 0
    }

    class UBO {
        var members = ConcurrentHashMap<String, Any>()
        var descriptor = UboDescriptor()

        fun getSize() =
            members.map {
                when(it.value.javaClass) {
                    GLMatrix::class.java -> (it.value as GLMatrix).floatArray.size * 4
                    GLVector::class.java -> (it.value as GLVector).toFloatArray().size * 4
                    Float::class.java -> 4
                    Int::class.java -> 4
                    Short::class.java -> 2
                    Boolean::class.java -> 4
                    else -> 0
                }
            }.sum()*1L
    }

    inner class SwapchainRecreator {
        var mustRecreate = true
        fun recreate() {
            // Begin the setup command buffer (the one we will use for swapchain/framebuffer creation)
            val cmdBufInfo = VkCommandBufferBeginInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .pNext(NULL)

            var err = vkBeginCommandBuffer(setupCommandBuffer, cmdBufInfo)
            cmdBufInfo.free()

            if (err != VK_SUCCESS) {
                throw AssertionError("Failed to begin setup command buffer: " + VU.translate(err))
            }

            val oldChain = swapchain?.swapchainHandle ?: VK_NULL_HANDLE

            // Create the swapchain (this will also add a memory barrier to initialize the framebuffer images)
            swapchain = createSwapChain(device, physicalDevice, surface, oldChain, setupCommandBuffer,
                width, height, colorFormatAndSpace.colorFormat, colorFormatAndSpace.colorSpace)
            err = vkEndCommandBuffer(setupCommandBuffer)


            if (err != VK_SUCCESS) {
                throw AssertionError("Failed to end setup command buffer: " + VU.translate(err))
            }

            submitCommandBuffer(queue, setupCommandBuffer)
            vkQueueWaitIdle(queue)

            if (framebuffers != null) {
                for (i in framebuffers!!.indices)
                    vkDestroyFramebuffer(device, framebuffers!![i], null)
            }
            framebuffers = createFramebuffers(device, swapchain!!, renderPass, width, height)
            // Create render command buffers
            if (renderCommandBuffers != null) {
                vkResetCommandPool(device, renderCommandPool, VK_FLAGS_NONE)
            }

            renderCommandBuffers = createRenderCommandBuffers(device, renderCommandPool, framebuffers!!, renderPass, width, height, pipeline, descriptorSet,
                vertices.verticesBuf)

            mustRecreate = false
        }
    }

    private var geometryBuffer: VulkanFramebuffer
    private var hdrBuffer: VulkanFramebuffer

    constructor(windowWidth: Int, windowHeight: Int) {
        this.width = windowWidth
        this.height = windowHeight

        if (!glfwInit()) {
            throw RuntimeException("Failed to initialize GLFW")
        }
        if (!glfwVulkanSupported()) {
            throw AssertionError("Failed to find Vulkan loader. Do you have the most recent graphics drivers installed?")
        }

        /* Look for instance extensions */
        val requiredExtensions = glfwGetRequiredInstanceExtensions() ?: throw AssertionError("Failed to find list of required Vulkan extensions")

        // Create the Vulkan instance
        instance = createInstance(requiredExtensions)
        debugCallbackHandle = setupDebugging(instance, VK_DEBUG_REPORT_ERROR_BIT_EXT or VK_DEBUG_REPORT_WARNING_BIT_EXT, debugCallback)
        physicalDevice = getPhysicalDevice(instance)
        deviceAndGraphicsQueueFamily = createDeviceAndGetGraphicsQueueFamily(physicalDevice)
        device = deviceAndGraphicsQueueFamily.device!!
        queueFamilyIndex = deviceAndGraphicsQueueFamily.queueFamilyIndex!!
        memoryProperties = deviceAndGraphicsQueueFamily.memoryProperties!!

        // Create GLFW window
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        window = glfwCreateWindow(windowWidth, windowHeight, "scenery", NULL, NULL)
        val keyCallback: GLFWKeyCallback = object : GLFWKeyCallback() {
            override operator fun invoke(window: Long, key: Int, scancode: Int, action: Int, mods: Int) {
                if (action != GLFW_RELEASE)
                    return
                if (key == GLFW_KEY_ESCAPE)
                    glfwSetWindowShouldClose(window, true)
            }
        }

        glfwSetKeyCallback(window, keyCallback)
        val pSurface = memAllocLong(1)
        var err = glfwCreateWindowSurface(instance, window, null, pSurface)
        surface = pSurface.get(0)

        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to create surface: " + VU.translate(err))
        }

        // Create static Vulkan resources
        colorFormatAndSpace = getColorFormatAndSpace(physicalDevice, surface)
        commandPool = createCommandPool(device, queueFamilyIndex)
        setupCommandBuffer = newCommandBuffer(device, commandPool)
        postPresentCommandBuffer = newCommandBuffer(device, commandPool)
        queue = createDeviceQueue(device, queueFamilyIndex)
        renderPass = createRenderPass(device, colorFormatAndSpace.colorFormat)
        renderCommandPool = createCommandPool(device, queueFamilyIndex)
        vertices = createVertices(memoryProperties, device)
        uboDescriptor = createTriangleUniformBuffer(memoryProperties, device)
        descriptorPool = createDescriptorPool(device)
        descriptorSetLayout = createDescriptorSetLayout(device)
        descriptorSet = createDescriptorSet(device, descriptorPool, descriptorSetLayout, uboDescriptor)

        val p = VulkanPipeline(device)
        p.addShaderStages(
            VulkanShaderModule(device, entryPoint = "main", shaderCodePath = "shaders/coloredRotatingTriangle.vert"),
            VulkanShaderModule(device, entryPoint = "main", shaderCodePath = "shaders/coloredRotatingTriangle.frag"))

        pipeline = p.createPipeline(descriptorSetLayout, renderPass, vertices.createInfo!!)

        swapchainRecreator = SwapchainRecreator()

        // Handle canvas resize
        val windowSizeCallback = object : GLFWWindowSizeCallback() {
            override operator fun invoke(window: Long, w: Int, h: Int) {
                if (width <= 0 || height <= 0)
                    return

                width = w
                height = h
                swapchainRecreator.mustRecreate = true
            }
        }
        glfwSetWindowSizeCallback(window, windowSizeCallback)
        glfwShowWindow(window)

        // Pre-allocate everything needed in the render loop

        pImageIndex = memAllocInt(1)
        pCommandBuffers = memAllocPointer(1)
        pSwapchains = memAllocLong(1)
        pImageAcquiredSemaphore = memAllocLong(1)
        pRenderCompleteSemaphore = memAllocLong(1)

        // Info struct to create a semaphore
        semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
            .pNext(NULL)
            .flags(0)

        // Info struct to submit a command buffer which will wait on the semaphore
        val pWaitDstStageMask = memAllocInt(1)
        pWaitDstStageMask.put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
        submitInfo = VkSubmitInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
            .pNext(NULL)
            .waitSemaphoreCount(pImageAcquiredSemaphore.remaining())
            .pWaitSemaphores(pImageAcquiredSemaphore)
            .pWaitDstStageMask(pWaitDstStageMask)
            .pCommandBuffers(pCommandBuffers)
            .pSignalSemaphores(pRenderCompleteSemaphore)

        // Info struct to present the current swapchain image to the display
        presentInfo = VkPresentInfoKHR.calloc()
            .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
            .pNext(NULL)
            .pWaitSemaphores(pRenderCompleteSemaphore)
            .swapchainCount(pSwapchains.remaining())
            .pSwapchains(pSwapchains)
            .pImageIndices(pImageIndex)
            .pResults(null)

        lastTime = System.nanoTime()
        time = 0.0f

        this.geometryBuffer = prepareGeometryBuffer(this.device, this.physicalDevice, width, height)
        this.hdrBuffer = prepareHDRBuffer(this.device, this.physicalDevice, width, height)

        this.geometryBuffer.createPassAndFramebuffer()
        this.hdrBuffer.createPassAndFramebuffer()
    }


    // source: http://stackoverflow.com/questions/34697828/parallel-operations-on-kotlin-collections
    // Thanks to Holger :-)
    fun <T, R> Iterable<T>.parallelMap(
        numThreads: Int = Runtime.getRuntime().availableProcessors(),
        exec: ExecutorService = Executors.newFixedThreadPool(numThreads),
        transform: (T) -> R): List<R> {

        // default size is just an inlined version of kotlin.collections.collectionSizeOrDefault
        val defaultSize = if (this is Collection<*>) this.size else 10
        val destination = Collections.synchronizedList(ArrayList<R>(defaultSize))

        for (item in this) {
            exec.submit { destination.add(transform(item)) }
        }

        exec.shutdown()
        exec.awaitTermination(1, TimeUnit.DAYS)

        return ArrayList<R>(destination)
    }

    /**
     * This function should initialize the scene contents.
     *
     * @param[scene] The scene to initialize.
     */
    override fun initializeScene(scene: Scene) {

        scene.discover(scene, { it is HasGeometry })
            .parallelMap(numThreads = System.getProperty("scenery.MaxInitThreads", "4").toInt()) { node ->
                node.metadata.put("VulkanRenderer", VulkanObjectState())
                initializeNode(node)
            }
    }

    fun createBuffer(usage: Int, memoryProperties: Int, data: ByteBuffer?, allocationSize: Long = 0): Pair<Long, Long> {
        val buffer = memAllocLong(1)
        val memory = memAllocLong(1)
        val memTypeIndex = memAllocInt(1)

        val reqs = VkMemoryRequirements.calloc()
        val bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .pNext(NULL)
            .usage(usage)
            .size(allocationSize)

        vkCreateBuffer(device, bufferInfo, null, buffer)
        vkGetBufferMemoryRequirements(device, buffer.get(0), reqs)

        val allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .pNext(NULL)

        getMemoryType(this.memoryProperties,
            reqs.memoryTypeBits(),
            memoryProperties,
            memTypeIndex)

        allocInfo.allocationSize(reqs.size())
            .memoryTypeIndex(memTypeIndex.get(0))

        vkAllocateMemory(this.device, allocInfo, null, memory)

        data?.let {
            val dest = memAllocPointer(1)

            vkMapMemory(this.device, memory.get(0), 0, allocInfo.allocationSize(), 0, dest)
            memCopy(memAddress(data), dest.get(0), data.remaining())
            vkUnmapMemory(this.device, memory.get(0))

            memFree(dest)
        }

        vkBindBufferMemory(this.device, buffer.get(0), memory.get(0), 0)

        reqs.free()
        allocInfo.free()
        memFree(memTypeIndex)

        return Pair(buffer.get(0), memory.get(0))
    }

    /**
     *
     */
    fun initializeNode(node: Node): Boolean {
        var s: VulkanObjectState

        s = node.metadata["VulkanRenderer"] as VulkanObjectState

        if(s.initialized) return true

        val stride = if((node as HasGeometry).texcoords.remaining() > 0) {
            s.attributeDescriptions = VkVertexInputAttributeDescription.calloc(3)
            3 + 3
        } else {
            s.attributeDescriptions = VkVertexInputAttributeDescription.calloc(3)
            3 + 3 + 2
        }

        // setup vertex binding descriptions
        s.bindingDescriptions.binding(0)
            .stride(stride * 4)
            .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

        // setup vertex attribute descriptions
        // position
        s.attributeDescriptions.get(0)
            .binding(0)
            .location(0)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(0)

        // normal
        s.attributeDescriptions.get(1)
            .binding(0)
            .location(1)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(3 * 4)

        // texture coordinates, if applicable
        if(stride > 6) {
            s.attributeDescriptions.get(2)
                .binding(0)
                .location(2)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(3 * 4 + 3 * 4)
        }

        s.inputState.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
            .pNext(NULL)
            .pVertexBindingDescriptions(s.bindingDescriptions)
            .pVertexAttributeDescriptions(s.attributeDescriptions)

        logger.info("Initializing ${node.name} (${node.vertices.remaining()/node.vertexSize} vertices/${node.indices.remaining()} indices)")

        if(node.vertices.remaining() > 0) {
            s = createVertexBuffers(node, s, memoryProperties, device)
        }

        return true
    }

    /**
     *
     */
    protected fun prepareGeometryBuffer(device: VkDevice, physicalDevice: VkPhysicalDevice, width: Int, height: Int): VulkanFramebuffer {
        // create uniform buffers
        val defaultDeferredMatrices = UBO()
        defaultDeferredMatrices.members.put("ModelView", GLMatrix.getIdentity())
        createUniformBuffer(defaultDeferredMatrices, memoryProperties, device)

        // create framebuffer
        with(newCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, autostart = true)) {
            val gb = VulkanFramebuffer(device, physicalDevice, width, height, this)

            gb.addFloatRGBABuffer("position", 32)
            gb.addFloatRGBABuffer("normal", 16)
            gb.addUnsignedByteRGBABuffer("albedo+diffuse", 32)
            gb.addDepthBuffer("depth", 32)

            flushCommandBuffer(this, this@VulkanRenderer.queue, true)

            return gb
        }
    }

    /**
     *
     */
    protected fun prepareHDRBuffer(device: VkDevice, physicalDevice: VkPhysicalDevice, width: Int, height: Int): VulkanFramebuffer {
        with(newCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, autostart = true)) {
            val hdr = VulkanFramebuffer(device, physicalDevice, width, height, this)

            hdr.addFloatRGBABuffer("hdr", 32)

            flushCommandBuffer(this, this@VulkanRenderer.queue, true)

            return hdr
        }
    }

    /**
     * This function renders the scene
     *
     * @param[scene] The scene to render.
     */
    override fun render(scene: Scene) {
        if(glfwWindowShouldClose(window)) {
            this.shouldClose = true
            return
        }

        var err = 0
        var currentBuffer = 0

        // Handle window messages. Resize events happen exactly here.
        // So it is safe to use the new swapchain images and framebuffers afterwards.
        glfwPollEvents()
        if (swapchainRecreator.mustRecreate) {
            swapchainRecreator.recreate()
        }

        // Create a semaphore to wait for the swapchain to acquire the next image
        err = vkCreateSemaphore(device, semaphoreCreateInfo, null, pImageAcquiredSemaphore)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to create image acquired semaphore: " + VU.translate(err))
        }

        // Create a semaphore to wait for the render to complete, before presenting
        err = vkCreateSemaphore(device, semaphoreCreateInfo, null, pRenderCompleteSemaphore)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to create render complete semaphore: " + VU.translate(err))
        }

        // Get next image from the swap chain (back/front buffer).
        // This will setup the imageAquiredSemaphore to be signalled when the operation is complete
        err = vkAcquireNextImageKHR(device, swapchain!!.swapchainHandle, UINT64_MAX, pImageAcquiredSemaphore.get(0), VK_NULL_HANDLE, pImageIndex)
        currentBuffer = pImageIndex.get(0)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to acquire next swapchain image: " + VU.translate(err))
        }

        // Select the command buffer for the current framebuffer image/attachment
        pCommandBuffers.put(0, renderCommandBuffers!![currentBuffer])

        // Update UBO
        val thisTime = System.nanoTime()
        time += (thisTime - lastTime) / 1E9f
        lastTime = thisTime
        updateUbo(device, uboDescriptor, time)

        // Submit to the graphics queue
        err = vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to submit render queue: " + VU.translate(err))
        }

        // Present the current buffer to the swap chain
        // This will display the image
        pSwapchains.put(0, swapchain!!.swapchainHandle)
        err = vkQueuePresentKHR(queue, presentInfo)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to present the swapchain image: " + VU.translate(err))
        }
        // Create and submit post present barrier
        vkQueueWaitIdle(queue)

        // Destroy this semaphore (we will create a new one in the next frame)
        vkDestroySemaphore(device, pImageAcquiredSemaphore.get(0), null)
        vkDestroySemaphore(device, pRenderCompleteSemaphore.get(0), null)
        submitPostPresentBarrier(swapchain!!.images!![currentBuffer], postPresentCommandBuffer, queue)
    }


    /**
     * Renders a simple rotating colored quad on a cornflower blue background on a GLFW window with Vulkan.
     *
     *
     * This is like the [ColoredTriangleDemo], but adds an additional rotation.
     * Do a diff between those two classes to see what's new.

     * @author Kai Burjack
     */


    /**
     * Create a Vulkan instance using LWJGL 3.

     * @return the VkInstance handle
     */
    private fun createInstance(requiredExtensions: PointerBuffer): VkInstance {
        val appInfo = VkApplicationInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
            .pApplicationName(memUTF8("scenery"))
            .pEngineName(memUTF8("scenery"))
            .apiVersion(VK_MAKE_VERSION(1, 0, 2))

        val ppEnabledExtensionNames = memAllocPointer(requiredExtensions.remaining() + 1)
        ppEnabledExtensionNames.put(requiredExtensions)
        val VK_EXT_DEBUG_REPORT_EXTENSION = memUTF8(VK_EXT_DEBUG_REPORT_EXTENSION_NAME)
        ppEnabledExtensionNames.put(VK_EXT_DEBUG_REPORT_EXTENSION)
        ppEnabledExtensionNames.flip()
        val ppEnabledLayerNames = memAllocPointer(layers.size)
        var i = 0
        while (validation && i < layers.size) {
            ppEnabledLayerNames.put(layers[i])
            i++
        }
        ppEnabledLayerNames.flip()

        val pCreateInfo = VkInstanceCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
            .pNext(NULL)
            .pApplicationInfo(appInfo)
            .ppEnabledExtensionNames(ppEnabledExtensionNames)
            .ppEnabledLayerNames(ppEnabledLayerNames)

        val pInstance = memAllocPointer(1)
        val err = vkCreateInstance(pCreateInfo, null, pInstance)
        val instance = pInstance.get(0)
        memFree(pInstance)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to create VkInstance: " + VU.translate(err))
        }
        val ret = VkInstance(instance, pCreateInfo)
        pCreateInfo.free()
        memFree(ppEnabledLayerNames)
        memFree(VK_EXT_DEBUG_REPORT_EXTENSION)
        memFree(ppEnabledExtensionNames)
        memFree(appInfo.pApplicationName())
        memFree(appInfo.pEngineName())
        appInfo.free()
        return ret
    }

    private fun setupDebugging(instance: VkInstance, flags: Int, callback: VkDebugReportCallbackEXT): Long {
        val dbgCreateInfo = VkDebugReportCallbackCreateInfoEXT.calloc()
            .sType(VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT)
            .pNext(NULL)
            .pfnCallback(callback)
            .pUserData(NULL)
            .flags(flags)

        val pCallback = memAllocLong(1)
        val err = vkCreateDebugReportCallbackEXT(instance, dbgCreateInfo, null, pCallback)
        val callbackHandle = pCallback.get(0)
        memFree(pCallback)
        dbgCreateInfo.free()
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to create VkInstance: " + VU.translate(err))
        }
        return callbackHandle
    }



    private fun getPhysicalDevice(instance: VkInstance): VkPhysicalDevice {
        val pPhysicalDeviceCount = memAllocInt(1)
        var err = vkEnumeratePhysicalDevices(instance, pPhysicalDeviceCount, null)

        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to get number of physical devices: " + VU.translate(err))
        }

        if(pPhysicalDeviceCount.get(0) < 1) {
            throw AssertionError("No Vulkan-compatible devices found!")
        }

        val pPhysicalDevices = memAllocPointer(pPhysicalDeviceCount.get(0))
        err = vkEnumeratePhysicalDevices(instance, pPhysicalDeviceCount, pPhysicalDevices)

        logger.info("Physical devices: ")
        for(i in 0..pPhysicalDeviceCount.get(0)-1) {
            val device = VkPhysicalDevice(pPhysicalDevices.get(i), instance)
            val properties: VkPhysicalDeviceProperties = VkPhysicalDeviceProperties.calloc()

            vkGetPhysicalDeviceProperties(device, properties)
            logger.info("  $i: ${VU.vendorToString(properties.vendorID())} ${properties.deviceNameString()} (${VU.deviceTypeToString(properties.deviceType())}, driver version ${VU.driverVersionToString(properties.driverVersion())}, Vulkan API ${VU.driverVersionToString(properties.apiVersion())}")
        }

        val physicalDevice = pPhysicalDevices.get(System.getProperty("scenery.VulkanBackend.Device", "0").toInt())

        memFree(pPhysicalDeviceCount)
        memFree(pPhysicalDevices)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to get physical devices: " + VU.translate(err))
        }
        return VkPhysicalDevice(physicalDevice, instance)
    }

    private fun createDeviceAndGetGraphicsQueueFamily(physicalDevice: VkPhysicalDevice): DeviceAndGraphicsQueueFamily {
        val pQueueFamilyPropertyCount = memAllocInt(1)
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyPropertyCount, null)
        val queueCount = pQueueFamilyPropertyCount.get(0)
        val queueProps = VkQueueFamilyProperties.calloc(queueCount)
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyPropertyCount, queueProps)
        memFree(pQueueFamilyPropertyCount)
        var graphicsQueueFamilyIndex: Int
        graphicsQueueFamilyIndex = 0
        while (graphicsQueueFamilyIndex < queueCount) {
            if (queueProps.get(graphicsQueueFamilyIndex).queueFlags() and VK_QUEUE_GRAPHICS_BIT !== 0)
                break
            graphicsQueueFamilyIndex++
        }
        queueProps.free()
        val pQueuePriorities = memAllocFloat(1).put(0.0f)
        pQueuePriorities.flip()
        val queueCreateInfo = VkDeviceQueueCreateInfo.calloc(1)
            .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
            .queueFamilyIndex(graphicsQueueFamilyIndex)
            .pQueuePriorities(pQueuePriorities)

        val extensions = memAllocPointer(1)
        val VK_KHR_SWAPCHAIN_EXTENSION = memUTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME)
        extensions.put(VK_KHR_SWAPCHAIN_EXTENSION)
        extensions.flip()
        val ppEnabledLayerNames = memAllocPointer(layers.size)
        var i = 0
        while (validation && i < layers.size) {
            ppEnabledLayerNames.put(layers[i])
            i++
        }
        ppEnabledLayerNames.flip()

        val deviceCreateInfo = VkDeviceCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
            .pNext(NULL)
            .pQueueCreateInfos(queueCreateInfo)
            .ppEnabledExtensionNames(extensions)
            .ppEnabledLayerNames(ppEnabledLayerNames)

        val pDevice = memAllocPointer(1)
        val err = vkCreateDevice(physicalDevice, deviceCreateInfo, null, pDevice)
        val device = pDevice.get(0)
        memFree(pDevice)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to create device: " + VU.translate(err))
        }

        val memoryProperties = VkPhysicalDeviceMemoryProperties.calloc()
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties)

        val ret = DeviceAndGraphicsQueueFamily()
        ret.device = VkDevice(device, physicalDevice, deviceCreateInfo)
        ret.queueFamilyIndex = graphicsQueueFamilyIndex
        ret.memoryProperties = memoryProperties

        deviceCreateInfo.free()
        memFree(ppEnabledLayerNames)
        memFree(VK_KHR_SWAPCHAIN_EXTENSION)
        memFree(extensions)
        memFree(pQueuePriorities)
        return ret
    }


    private fun getColorFormatAndSpace(physicalDevice: VkPhysicalDevice, surface: Long): ColorFormatAndSpace {
        val pQueueFamilyPropertyCount = memAllocInt(1)
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyPropertyCount, null)
        val queueCount = pQueueFamilyPropertyCount.get(0)
        val queueProps = VkQueueFamilyProperties.calloc(queueCount)
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyPropertyCount, queueProps)
        memFree(pQueueFamilyPropertyCount)

        // Iterate over each queue to learn whether it supports presenting:
        val supportsPresent = memAllocInt(queueCount)
        for (i in 0..queueCount - 1) {
            supportsPresent.position(i)
            val err = vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surface, supportsPresent)
            if (err != VK_SUCCESS) {
                throw AssertionError("Failed to physical device surface support: " + VU.translate(err))
            }
        }

        // Search for a graphics and a present queue in the array of queue families, try to find one that supports both
        var graphicsQueueNodeIndex = Integer.MAX_VALUE
        var presentQueueNodeIndex = Integer.MAX_VALUE
        for (i in 0..queueCount - 1) {
            if (queueProps.get(i).queueFlags() and VK_QUEUE_GRAPHICS_BIT !== 0) {
                if (graphicsQueueNodeIndex == Integer.MAX_VALUE) {
                    graphicsQueueNodeIndex = i
                }
                if (supportsPresent.get(i) === VK_TRUE) {
                    graphicsQueueNodeIndex = i
                    presentQueueNodeIndex = i
                    break
                }
            }
        }
        queueProps.free()
        if (presentQueueNodeIndex == Integer.MAX_VALUE) {
            // If there's no queue that supports both present and graphics try to find a separate present queue
            for (i in 0..queueCount - 1) {
                if (supportsPresent.get(i) === VK_TRUE) {
                    presentQueueNodeIndex = i
                    break
                }
            }
        }
        memFree(supportsPresent)

        // Generate error if could not find both a graphics and a present queue
        if (graphicsQueueNodeIndex == Integer.MAX_VALUE) {
            throw AssertionError("No graphics queue found")
        }
        if (presentQueueNodeIndex == Integer.MAX_VALUE) {
            throw AssertionError("No presentation queue found")
        }
        if (graphicsQueueNodeIndex != presentQueueNodeIndex) {
            throw AssertionError("Presentation queue != graphics queue")
        }

        // Get list of supported formats
        val pFormatCount = memAllocInt(1)
        var err = vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pFormatCount, null)
        val formatCount = pFormatCount.get(0)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to query number of physical device surface formats: " + VU.translate(err))
        }

        val surfFormats = VkSurfaceFormatKHR.calloc(formatCount)
        err = vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pFormatCount, surfFormats)
        memFree(pFormatCount)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to query physical device surface formats: " + VU.translate(err))
        }

        val colorFormat: Int
        if (formatCount == 1 && surfFormats.get(0).format() === VK_FORMAT_UNDEFINED) {
            colorFormat = VK_FORMAT_B8G8R8A8_UNORM
        } else {
            colorFormat = surfFormats.get(0).format()
        }
        val colorSpace = surfFormats.get(0).colorSpace()
        surfFormats.free()

        val ret = ColorFormatAndSpace()
        ret.colorFormat = colorFormat
        ret.colorSpace = colorSpace
        return ret
    }

    private fun createCommandPool(device: VkDevice, queueNodeIndex: Int): Long {
        val cmdPoolInfo = VkCommandPoolCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
            .queueFamilyIndex(queueNodeIndex)
            .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)

        val pCmdPool = memAllocLong(1)
        val err = vkCreateCommandPool(device, cmdPoolInfo, null, pCmdPool)
        val commandPool = pCmdPool.get(0)
        cmdPoolInfo.free()
        memFree(pCmdPool)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to create command pool: " + VU.translate(err))
        }
        return commandPool
    }

    private fun createDeviceQueue(device: VkDevice, queueFamilyIndex: Int): VkQueue {
        val pQueue = memAllocPointer(1)
        vkGetDeviceQueue(device, queueFamilyIndex, 0, pQueue)
        val queue = pQueue.get(0)
        memFree(pQueue)
        return VkQueue(queue, device)
    }

    private fun newCommandBuffer(device: VkDevice, commandPool: Long, level: Int = VK_COMMAND_BUFFER_LEVEL_PRIMARY): VkCommandBuffer {
        val cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            .commandPool(commandPool)
            .level(level)
            .commandBufferCount(1)

        val pCommandBuffer = memAllocPointer(1)
        val err = vkAllocateCommandBuffers(device, cmdBufAllocateInfo, pCommandBuffer)
        cmdBufAllocateInfo.free()
        val commandBuffer = pCommandBuffer.get(0)
        memFree(pCommandBuffer)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to allocate command buffer: " + VU.translate(err))
        }
        return VkCommandBuffer(commandBuffer, device)
    }

    private fun newCommandBuffer(level: Int = VK_COMMAND_BUFFER_LEVEL_PRIMARY, autostart: Boolean = false): VkCommandBuffer {
        val cmdBuf = newCommandBuffer(this.device, this.commandPool, level)

        if(autostart) {
            val cmdBufInfo = VkCommandBufferBeginInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .pNext(NULL)

            vkBeginCommandBuffer(cmdBuf, cmdBufInfo)
        }

        return cmdBuf
    }

    private fun flushCommandBuffer(commandBuffer: VkCommandBuffer, queue: VkQueue, dealloc: Boolean = false) {
        if (commandBuffer.address() === NULL) {
            return
        }

        if(vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) {
            throw AssertionError("Failed to end command buffer $commandBuffer")
        }

        VU.run(memAllocPointer(1).put(commandBuffer).flip(), "flushCommandBuffer") {
            val submitInfo = VkSubmitInfo.calloc(1)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(this)

            vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE)
            vkQueueWaitIdle(queue)
        }

        if(dealloc) {
            vkFreeCommandBuffers(this.device, commandPool, commandBuffer)
        }
    }

    private fun imageBarrier(cmdbuffer: VkCommandBuffer, image: Long, aspectMask: Int, oldImageLayout: Int, srcAccess: Int, newImageLayout: Int, dstAccess: Int) {
        // Create an image barrier object
        val imageMemoryBarrier = VkImageMemoryBarrier.calloc(1)
            .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .pNext(NULL)
            .oldLayout(oldImageLayout)
            .srcAccessMask(srcAccess)
            .newLayout(newImageLayout)
            .dstAccessMask(dstAccess)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .image(image)

        imageMemoryBarrier.subresourceRange().aspectMask(aspectMask).baseMipLevel(0).levelCount(1).layerCount(1)

        // Put barrier on top
        val srcStageFlags = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
        val destStageFlags = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT

        // Put barrier inside setup command buffer
        vkCmdPipelineBarrier(cmdbuffer, srcStageFlags, destStageFlags, VK_FLAGS_NONE,
            null, // no memory barriers
            null, // no buffer memory barriers
            imageMemoryBarrier) // one image memory barrier
        imageMemoryBarrier.free()
    }


    private fun createSwapChain(device: VkDevice, physicalDevice: VkPhysicalDevice, surface: Long, oldSwapChain: Long, commandBuffer: VkCommandBuffer, newWidth: Int,
                                newHeight: Int, colorFormat: Int, colorSpace: Int): Swapchain {
        var err: Int
        // Get physical device surface properties and formats
        val surfCaps = VkSurfaceCapabilitiesKHR.calloc()
        err = vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, surfCaps)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to get physical device surface capabilities: " + VU.translate(err))
        }

        val pPresentModeCount = memAllocInt(1)
        err = vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, null)
        val presentModeCount = pPresentModeCount.get(0)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to get number of physical device surface presentation modes: " + VU.translate(err))
        }

        val pPresentModes = memAllocInt(presentModeCount)
        err = vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, pPresentModes)
        memFree(pPresentModeCount)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to get physical device surface presentation modes: " + VU.translate(err))
        }

        // Try to use mailbox mode. Low latency and non-tearing
        var swapchainPresentMode = VK_PRESENT_MODE_FIFO_KHR
        for (i in 0..presentModeCount - 1) {
            if (pPresentModes.get(i) === VK_PRESENT_MODE_MAILBOX_KHR) {
                swapchainPresentMode = VK_PRESENT_MODE_MAILBOX_KHR
                break
            }
            if (swapchainPresentMode != VK_PRESENT_MODE_MAILBOX_KHR && pPresentModes.get(i) === VK_PRESENT_MODE_IMMEDIATE_KHR) {
                swapchainPresentMode = VK_PRESENT_MODE_IMMEDIATE_KHR
            }
        }
        memFree(pPresentModes)

        // Determine the number of images
        var desiredNumberOfSwapchainImages = surfCaps.minImageCount() + 1
        if (surfCaps.maxImageCount() > 0 && desiredNumberOfSwapchainImages > surfCaps.maxImageCount()) {
            desiredNumberOfSwapchainImages = surfCaps.maxImageCount()
        }

        val currentExtent = surfCaps.currentExtent()
        val currentWidth = currentExtent.width()
        val currentHeight = currentExtent.height()
        if (currentWidth != -1 && currentHeight != -1) {
            width = currentWidth
            height = currentHeight
        } else {
            width = newWidth
            height = newHeight
        }

        val preTransform: Int
        if (surfCaps.supportedTransforms() and VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR !== 0) {
            preTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR
        } else {
            preTransform = surfCaps.currentTransform()
        }
        surfCaps.free()

        val swapchainCI = VkSwapchainCreateInfoKHR.calloc()
            .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
            .pNext(NULL)
            .surface(surface)
            .minImageCount(desiredNumberOfSwapchainImages)
            .imageFormat(colorFormat)
            .imageColorSpace(colorSpace)
            .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
            .preTransform(preTransform)
            .imageArrayLayers(1)
            .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
            .pQueueFamilyIndices(null)
            .presentMode(swapchainPresentMode)
            .oldSwapchain(oldSwapChain)
            .clipped(true)
            .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)

        swapchainCI.imageExtent().width(width).height(height)
        val pSwapChain = memAllocLong(1)
        err = vkCreateSwapchainKHR(device, swapchainCI, null, pSwapChain)
        swapchainCI.free()
        val swapChain = pSwapChain.get(0)
        memFree(pSwapChain)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to create swap chain: " + VU.translate(err))
        }

        // If we just re-created an existing swapchain, we should destroy the old swapchain at this point.
        // Note: destroying the swapchain also cleans up all its associated presentable images once the platform is done with them.
        if (oldSwapChain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device, oldSwapChain, null)
        }

        val pImageCount = memAllocInt(1)
        err = vkGetSwapchainImagesKHR(device, swapChain, pImageCount, null)
        val imageCount = pImageCount.get(0)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to get number of swapchain images: " + VU.translate(err))
        }

        val pSwapchainImages = memAllocLong(imageCount)
        err = vkGetSwapchainImagesKHR(device, swapChain, pImageCount, pSwapchainImages)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to get swapchain images: " + VU.translate(err))
        }
        memFree(pImageCount)

        val images = LongArray(imageCount)
        val imageViews = LongArray(imageCount)
        val pBufferView = memAllocLong(1)
        val colorAttachmentView = VkImageViewCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .pNext(NULL)
            .format(colorFormat)
            .viewType(VK_IMAGE_VIEW_TYPE_2D)
            .flags(VK_FLAGS_NONE)

        colorAttachmentView.components()
            .r(VK_COMPONENT_SWIZZLE_R)
            .g(VK_COMPONENT_SWIZZLE_G)
            .b(VK_COMPONENT_SWIZZLE_B)
            .a(VK_COMPONENT_SWIZZLE_A)

        colorAttachmentView.subresourceRange()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1)

        for (i in 0..imageCount - 1) {
            images[i] = pSwapchainImages.get(i)
            // Bring the image from an UNDEFINED state to the VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT state
            imageBarrier(commandBuffer, images[i], VK_IMAGE_ASPECT_COLOR_BIT,
                VK_IMAGE_LAYOUT_UNDEFINED, 0,
                VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
            colorAttachmentView.image(images[i])
            err = vkCreateImageView(device, colorAttachmentView, null, pBufferView)
            imageViews[i] = pBufferView.get(0)
            if (err != VK_SUCCESS) {
                throw AssertionError("Failed to create image view: " + VU.translate(err))
            }
        }
        colorAttachmentView.free()
        memFree(pBufferView)
        memFree(pSwapchainImages)

        val ret = Swapchain()
        ret.images = images
        ret.imageViews = imageViews
        ret.swapchainHandle = swapChain
        return ret
    }

    private fun createRenderPass(device: VkDevice, colorFormat: Int): Long {
        val attachments = VkAttachmentDescription.calloc(1)
            .format(colorFormat)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
            .finalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

        val colorReference = VkAttachmentReference.calloc(1)
            .attachment(0)
            .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

        val subpass = VkSubpassDescription.calloc(1)
            .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
            .flags(VK_FLAGS_NONE)
            .pInputAttachments(null)
            .colorAttachmentCount(colorReference.remaining())
            .pColorAttachments(colorReference) // <- only color attachment
            .pResolveAttachments(null)
            .pDepthStencilAttachment(null)
            .pPreserveAttachments(null)

        val renderPassInfo = VkRenderPassCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
            .pNext(NULL)
            .pAttachments(attachments)
            .pSubpasses(subpass)
            .pDependencies(null)

        val pRenderPass = memAllocLong(1)
        val err = vkCreateRenderPass(device, renderPassInfo, null, pRenderPass)
        val renderPass = pRenderPass.get(0)
        memFree(pRenderPass)
        renderPassInfo.free()
        colorReference.free()
        subpass.free()
        attachments.free()
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to create clear render pass: " + VU.translate(err))
        }
        return renderPass
    }

    private fun createFramebuffers(device: VkDevice, swapchain: Swapchain, renderPass: Long, width: Int, height: Int): LongArray {
        val attachments = memAllocLong(1)
        val fci = VkFramebufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
            .pAttachments(attachments)
            .flags(VK_FLAGS_NONE)
            .height(height)
            .width(width)
            .layers(1)
            .pNext(NULL)
            .renderPass(renderPass)

        // Create a framebuffer for each swapchain image
        val framebuffers = LongArray(swapchain.images!!.size)
        val pFramebuffer = memAllocLong(1)

        for (i in swapchain.images!!.indices) {
            attachments.put(0, swapchain.imageViews!![i])
            val err = vkCreateFramebuffer(device, fci, null, pFramebuffer)
            val framebuffer = pFramebuffer.get(0)

            if (err != VK_SUCCESS) {
                throw AssertionError("Failed to create framebuffer: " + VU.translate(err))
            }
            framebuffers[i] = framebuffer
        }
        memFree(attachments)
        memFree(pFramebuffer)
        fci.free()
        return framebuffers
    }

    private fun submitCommandBuffer(queue: VkQueue, commandBuffer: VkCommandBuffer?) {
        if (commandBuffer == null || commandBuffer!!.address() === NULL)
            return
        val submitInfo = VkSubmitInfo.calloc().sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
        val pCommandBuffers = memAllocPointer(1).put(commandBuffer).flip()
        submitInfo.pCommandBuffers(pCommandBuffers)
        val err = vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE)
        memFree(pCommandBuffers)
        submitInfo.free()

        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to submit command buffer: " + VU.translate(err))
        }
    }

    private fun getMemoryType(deviceMemoryProperties: VkPhysicalDeviceMemoryProperties, typeBits: Int, properties: Int, typeIndex: IntBuffer): Boolean {
        var bits = typeBits
        for (i in 0..31) {
            if (bits and 1 == 1) {
                if (deviceMemoryProperties.memoryTypes(i).propertyFlags() and properties === properties) {
                    typeIndex.put(0, i)
                    return true
                }
            }
            bits = bits shr 1
        }
        return false
    }

    private fun createVertices(deviceMemoryProperties: VkPhysicalDeviceMemoryProperties, device: VkDevice): Vertices {
        val vertexBuffer = memAlloc(6 * (2 + 3) * 4)
        val fb = vertexBuffer.asFloatBuffer()
        fb.put(-0.5f).put(-0.5f).put(1.0f).put(0.0f).put(0.0f)
        fb.put(0.5f).put(-0.5f).put(0.0f).put(1.0f).put(0.0f)
        fb.put(0.5f).put(0.5f).put(0.0f).put(0.0f).put(1.0f)
        fb.put(0.5f).put(0.5f).put(0.0f).put(0.0f).put(1.0f)
        fb.put(-0.5f).put(0.5f).put(0.0f).put(1.0f).put(1.0f)
        fb.put(-0.5f).put(-0.5f).put(1.0f).put(0.0f).put(0.0f)

        val memAlloc = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .pNext(NULL)
            .allocationSize(0)
            .memoryTypeIndex(0)

        val memReqs = VkMemoryRequirements.calloc()

        var err: Int

        // Generate vertex buffer
        //  Setup
        val bufInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .pNext(NULL)
            .size(vertexBuffer.remaining().toLong())
            .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT).flags(0)

        val pBuffer = memAllocLong(1)
        err = vkCreateBuffer(device, bufInfo, null, pBuffer)
        val verticesBuf = pBuffer.get(0)
        memFree(pBuffer)
        bufInfo.free()
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to create vertex buffer: " + VU.translate(err))
        }

        vkGetBufferMemoryRequirements(device, verticesBuf, memReqs)
        memAlloc.allocationSize(memReqs.size())
        val memoryTypeIndex = memAllocInt(1)
        getMemoryType(deviceMemoryProperties, memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, memoryTypeIndex)
        memAlloc.memoryTypeIndex(memoryTypeIndex.get(0))
        memFree(memoryTypeIndex)
        memReqs.free()

        val pMemory = memAllocLong(1)
        err = vkAllocateMemory(device, memAlloc, null, pMemory)
        val verticesMem = pMemory.get(0)
        memFree(pMemory)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to allocate vertex memory: " + VU.translate(err))
        }

        val pData = memAllocPointer(1)
        err = vkMapMemory(device, verticesMem, 0, memAlloc.allocationSize(), 0, pData)
        memAlloc.free()
        val data = pData.get(0)
        memFree(pData)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to map vertex memory: " + VU.translate(err))
        }

        memCopy(memAddress(vertexBuffer), data, vertexBuffer.remaining())
        memFree(vertexBuffer)
        vkUnmapMemory(device, verticesMem)
        err = vkBindBufferMemory(device, verticesBuf, verticesMem, 0)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to bind memory to vertex buffer: " + VU.translate(err))
        }

        // Binding description
        val bindingDescriptor = VkVertexInputBindingDescription.calloc(1).binding(0) // <- we bind our vertex buffer to point 0
            .stride((2 + 3) * 4).inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

        // Attribute descriptions
        // Describes memory layout and shader attribute locations
        val attributeDescriptions = VkVertexInputAttributeDescription.calloc(2)
        // Location 0 : Position
        attributeDescriptions.get(0).binding(0) // <- binding point used in the VkVertexInputBindingDescription
            .location(0) // <- location in the shader's attribute layout (inside the shader source)
            .format(VK_FORMAT_R32G32_SFLOAT).offset(0)
        // Location 1 : Color
        attributeDescriptions.get(1).binding(0) // <- binding point used in the VkVertexInputBindingDescription
            .location(1) // <- location in the shader's attribute layout (inside the shader source)
            .format(VK_FORMAT_R32G32B32_SFLOAT).offset(2 * 4)

        // Assign to vertex buffer
        val vi = VkPipelineVertexInputStateCreateInfo.calloc()
        vi.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
        vi.pNext(NULL)
        vi.pVertexBindingDescriptions(bindingDescriptor)
        vi.pVertexAttributeDescriptions(attributeDescriptions)

        val ret = Vertices()
        ret.createInfo = vi
        ret.verticesBuf = verticesBuf
        return ret
    }

    private fun createVertexBuffers(node: Node, state: VulkanObjectState, deviceMemoryProperties: VkPhysicalDeviceMemoryProperties, device: VkDevice): VulkanObjectState {
        val n = node as HasGeometry

        val stridedBuffer = memAlloc((n.vertices.remaining() + n.normals.remaining() + n.texcoords.remaining()) * 4 + n.indices.remaining() * 2)

        val fb = stridedBuffer.asFloatBuffer()
        val ib = stridedBuffer.asIntBuffer()

        for(index in 0..n.vertices.remaining() - 1 step 3) {
            fb.put(n.vertices.get())
            fb.put(n.vertices.get())
            fb.put(n.vertices.get())

            fb.put(n.normals.get())
            fb.put(n.normals.get())
            fb.put(n.normals.get())

            if(n.texcoords.remaining() > 0) {
                fb.put(n.texcoords.get())
                fb.put(n.texcoords.get())
            }
        }

        if(n.indices.remaining() > 0) {
            for(index in 0..n.indices.remaining() - 1) {
                ib.put(n.indices.get())
            }
        }

        n.vertices.flip()
        n.normals.flip()
        n.texcoords.flip()
        n.indices.flip()

        val (verticesBufStaging, verticesMemStaging) = createBuffer(
            VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
            stridedBuffer,
            stridedBuffer.remaining().toLong())

        val (verticesBuf, verticesMem) = createBuffer(
            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT or VK_BUFFER_USAGE_TRANSFER_DST_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
            null,
            stridedBuffer.remaining().toLong())

        with(newCommandBuffer(autostart = true)) {
            val copyRegion = VkBufferCopy.calloc(1)
                .size(stridedBuffer.remaining().toLong())

            vkCmdCopyBuffer(this,
                verticesBufStaging,
                verticesBuf,
                copyRegion)

            flushCommandBuffer(this, queue)
        }

        // Assign to vertex buffer
        val vi = VkPipelineVertexInputStateCreateInfo.calloc()
        vi.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
        vi.pNext(NULL)
        vi.pVertexBindingDescriptions(state.bindingDescriptions)
        vi.pVertexAttributeDescriptions(state.attributeDescriptions)

        state.vertexBuffers.put("vertexIndexBuffer", verticesBuf)
        state.createInfo = vi


        vkDestroyBuffer(device, verticesBufStaging, null)
        vkFreeMemory(device, verticesMemStaging, null)

        return state
    }

    private fun createDescriptorPool(device: VkDevice, type: Int = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, size: Int = 1): Long {
        // We need to tell the API the number of max. requested descriptors per type
        val typeCounts = VkDescriptorPoolSize.calloc(1).type(type)
        // This example only uses one descriptor type (uniform buffer) and only
        // requests one descriptor of this type
            .descriptorCount(size)
        // For additional types you need to add new entries in the type count list
        // E.g. for two combined image samplers :
        // typeCounts[1].type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        // typeCounts[1].descriptorCount = 2;

        // Create the global descriptor pool
        // All descriptors used in this example are allocated from this pool
        val descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
            .pNext(NULL)
            .pPoolSizes(typeCounts)
            .maxSets(1)// Set the max. number of sets that can be requested

        // Requesting descriptors beyond maxSets will result in an error

        val pDescriptorPool = memAllocLong(1)
        val err = vkCreateDescriptorPool(device, descriptorPoolInfo, null, pDescriptorPool)
        val descriptorPool = pDescriptorPool.get(0)
        memFree(pDescriptorPool)
        descriptorPoolInfo.free()
        typeCounts.free()
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to create descriptor pool: " + VU.translate(err))
        }
        return descriptorPool
    }

    private fun createTriangleUniformBuffer(deviceMemoryProperties: VkPhysicalDeviceMemoryProperties, device: VkDevice): UboDescriptor {
        val ubo = UBO()
        ubo.members.put("Matrix", GLMatrix.getIdentity())

        return createUniformBuffer(ubo, deviceMemoryProperties, device)
    }

    private fun createUniformBuffer(ubo: UBO, deviceMemoryProperties: VkPhysicalDeviceMemoryProperties, device: VkDevice): UboDescriptor {
        var err: Int
        // Create a new buffer
        val bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(ubo.getSize())
            .usage(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT)

        val uniformDataVSBuffer = VU.run(memAllocLong(1), "Create UBO Buffer") {
            vkCreateBuffer(device, bufferInfo, null, this)
        }

        bufferInfo.free()

        // Get memory requirements including size, alignment and memory type
        val memReqs = VkMemoryRequirements.calloc()
        vkGetBufferMemoryRequirements(device, uniformDataVSBuffer, memReqs)
        val memSize = memReqs.size()
        val memoryTypeBits = memReqs.memoryTypeBits()
        memReqs.free()
        // Gets the appropriate memory type for this type of buffer allocation
        // Only memory types that are visible to the host
        val pMemoryTypeIndex = memAllocInt(1)
        getMemoryType(deviceMemoryProperties, memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, pMemoryTypeIndex)
        val memoryTypeIndex = pMemoryTypeIndex.get(0)
        memFree(pMemoryTypeIndex)
        // Allocate memory for the uniform buffer
        val pUniformDataVSMemory = memAllocLong(1)
        val allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .pNext(NULL)
            .allocationSize(memSize)
            .memoryTypeIndex(memoryTypeIndex)

        err = vkAllocateMemory(device, allocInfo, null, pUniformDataVSMemory)
        val uniformDataVSMemory = pUniformDataVSMemory.get(0)
        memFree(pUniformDataVSMemory)
        allocInfo.free()
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to allocate UBO memory: " + VU.translate(err))
        }
        // Bind memory to buffer
        err = vkBindBufferMemory(device, uniformDataVSBuffer, uniformDataVSMemory, 0)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to bind UBO memory: " + VU.translate(err))
        }

        ubo.descriptor = UboDescriptor()
        ubo.descriptor.memory = uniformDataVSMemory
        ubo.descriptor.allocationSize = memSize
        ubo.descriptor.buffer = uniformDataVSBuffer
        ubo.descriptor.offset = 0L
        ubo.descriptor.range = ubo.getSize()

        return ubo.descriptor
    }

    private fun createDescriptorSet(device: VkDevice, descriptorPool: Long, descriptorSetLayout: Long, uniformDataVSDescriptor: UboDescriptor): Long {
        val pDescriptorSetLayout = memAllocLong(1)
        pDescriptorSetLayout.put(0, descriptorSetLayout)
        val allocInfo = VkDescriptorSetAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
            .descriptorPool(descriptorPool)
            .pSetLayouts(pDescriptorSetLayout)

        val descriptorSet = VU.run(memAllocLong(1), "createDescriptorSet") {
            vkAllocateDescriptorSets(device, allocInfo, this)
        }

        allocInfo.free()
        memFree(pDescriptorSetLayout)

        // Update descriptor sets determining the shader binding points
        // For every binding point used in a shader there needs to be one
        // descriptor set matching that binding point
        val descriptor = VkDescriptorBufferInfo.calloc(1)
            .buffer(uniformDataVSDescriptor.buffer)
            .range(uniformDataVSDescriptor.range)
            .offset(uniformDataVSDescriptor.offset)

        // Binding 0 : Uniform buffer
        val writeDescriptorSet = VkWriteDescriptorSet.calloc(1)
            .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
            .dstSet(descriptorSet)
            .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            .pBufferInfo(descriptor)
            .dstBinding(0) // <- Binds this uniform buffer to binding point 0

        vkUpdateDescriptorSets(device, writeDescriptorSet, null)
        writeDescriptorSet.free()
        descriptor.free()

        return descriptorSet
    }

    private fun createDescriptorSetLayout(device: VkDevice): Long {
        val err: Int
        // One binding for a UBO used in a vertex shader
        val layoutBinding = VkDescriptorSetLayoutBinding.calloc(1)
            .binding(0) // <- Binding 0 : Uniform buffer (Vertex shader)
            .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            .descriptorCount(1)
            .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
            .pImmutableSamplers(null)

        // Build a create-info struct to create the descriptor set layout
        val descriptorLayout = VkDescriptorSetLayoutCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
            .pNext(NULL)
            .pBindings(layoutBinding)

        val pDescriptorSetLayout = memAllocLong(1)
        err = vkCreateDescriptorSetLayout(device, descriptorLayout, null, pDescriptorSetLayout)
        val descriptorSetLayout = pDescriptorSetLayout.get(0)
        memFree(pDescriptorSetLayout)
        descriptorLayout.free()
        layoutBinding.free()
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to create descriptor set layout: " + VU.translate(err))
        }
        return descriptorSetLayout
    }

    private fun createRenderCommandBuffers(device: VkDevice, commandPool: Long, framebuffers: LongArray, renderPass: Long, width: Int, height: Int,
                                           pipeline: Pipeline, descriptorSet: Long, verticesBuf: Long): Array<VkCommandBuffer> {
        // Create the render command buffers (one command buffer per framebuffer image)
        val cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            .commandPool(commandPool)
            .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
            .commandBufferCount(framebuffers.size)

        val pCommandBuffer = memAllocPointer(framebuffers.size)
        var err = vkAllocateCommandBuffers(device, cmdBufAllocateInfo, pCommandBuffer)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to allocate render command buffer: " + VU.translate(err))
        }

        val renderCommandBuffers = framebuffers.indices.map { VkCommandBuffer(pCommandBuffer.get(it), device) }.toTypedArray()
        memFree(pCommandBuffer)
        cmdBufAllocateInfo.free()

        // Create the command buffer begin structure
        val cmdBufInfo = VkCommandBufferBeginInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
            .pNext(NULL)

        // Specify clear color (cornflower blue)
        val clearValues = VkClearValue.calloc(1)
        clearValues.color().float32(0, 100 / 255.0f).float32(1, 149 / 255.0f).float32(2, 237 / 255.0f).float32(3, 1.0f)

        // Specify everything to begin a render pass
        val renderPassBeginInfo = VkRenderPassBeginInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
            .pNext(NULL)
            .renderPass(renderPass)
            .pClearValues(clearValues)

        val renderArea = renderPassBeginInfo.renderArea()
        renderArea.offset().set(0, 0)
        renderArea.extent().set(width, height)

        for (i in renderCommandBuffers.indices) {
            // Set target frame buffer
            renderPassBeginInfo.framebuffer(framebuffers[i])

            err = vkBeginCommandBuffer(renderCommandBuffers[i], cmdBufInfo)
            if (err != VK_SUCCESS) {
                throw AssertionError("Failed to begin render command buffer: " + VU.translate(err))
            }

            vkCmdBeginRenderPass(renderCommandBuffers[i], renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)

            // Update dynamic viewport state
            val viewport = VkViewport.calloc(1)
                .height(height.toFloat())
                .width(width.toFloat())
                .minDepth(0.0f)
                .maxDepth(1.0f)

            vkCmdSetViewport(renderCommandBuffers[i], 0, viewport)
            viewport.free()

            // Update dynamic scissor state
            val scissor = VkRect2D.calloc(1)
            scissor.extent().set(width, height)
            scissor.offset().set(0, 0)
            vkCmdSetScissor(renderCommandBuffers[i], 0, scissor)
            scissor.free()

            // Bind descriptor sets describing shader binding points
            val descriptorSets = memAllocLong(1).put(0, descriptorSet)
            vkCmdBindDescriptorSets(renderCommandBuffers[i], VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.layout, 0, descriptorSets, null)
            memFree(descriptorSets)

            // Bind the rendering pipeline (including the shaders)
            vkCmdBindPipeline(renderCommandBuffers[i], VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.pipeline)

            // Bind triangle vertices
            val offsets = memAllocLong(1)
            offsets.put(0, 0L)
            val pBuffers = memAllocLong(1)
            pBuffers.put(0, verticesBuf)
            vkCmdBindVertexBuffers(renderCommandBuffers[i], 0, pBuffers, offsets)
            memFree(pBuffers)
            memFree(offsets)

            // Draw triangle
            vkCmdDraw(renderCommandBuffers[i], 6, 1, 0, 0)

            vkCmdEndRenderPass(renderCommandBuffers[i])

            // Add a present memory barrier to the end of the command buffer
            // This will transform the frame buffer color attachment to a
            // new layout for presenting it to the windowing system integration
            val prePresentBarrier = createPrePresentBarrier(swapchain!!.images!![i])
            vkCmdPipelineBarrier(renderCommandBuffers[i],
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK_FLAGS_NONE,
                null, // No memory barriers
                null, // No buffer memory barriers
                prePresentBarrier) // One image memory barrier
            prePresentBarrier.free()

            err = vkEndCommandBuffer(renderCommandBuffers[i])
            if (err != VK_SUCCESS) {
                throw AssertionError("Failed to begin render command buffer: " + VU.translate(err))
            }
        }
        renderPassBeginInfo.free()
        clearValues.free()
        cmdBufInfo.free()
        return renderCommandBuffers
    }

    private fun updateUbo(device: VkDevice, ubo: UboDescriptor, angle: Float) {
        val m = GLMatrix.getIdentity().rotEuler(0.0, 0.0, angle * 1.0)
        val pData = memAllocPointer(1)
        val err = vkMapMemory(device, ubo.memory, 0, ubo.allocationSize, 0, pData)
        val data = pData.get(0)
        memFree(pData)

        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to map UBO memory: " + VU.translate(err))
        }

        val matrixBuffer = memByteBuffer(data, 16 * 4)
        m.push(matrixBuffer)

        vkUnmapMemory(device, ubo.memory)
    }

    private fun createPrePresentBarrier(presentImage: Long): VkImageMemoryBarrier.Buffer {
        val imageMemoryBarrier = VkImageMemoryBarrier.calloc(1)
            .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .pNext(NULL)
            .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
            .dstAccessMask(0)
            .oldLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
            .newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)

        imageMemoryBarrier.subresourceRange()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1)

        imageMemoryBarrier.image(presentImage)
        return imageMemoryBarrier
    }

    private fun createPostPresentBarrier(presentImage: Long): VkImageMemoryBarrier.Buffer {
        val imageMemoryBarrier = VkImageMemoryBarrier.calloc(1)
            .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .pNext(NULL)
            .srcAccessMask(0)
            .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
            .oldLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
            .newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)

        imageMemoryBarrier.subresourceRange()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1)

        imageMemoryBarrier.image(presentImage)
        return imageMemoryBarrier
    }

    private fun submitPostPresentBarrier(image: Long, commandBuffer: VkCommandBuffer, queue: VkQueue) {
        val cmdBufInfo = VkCommandBufferBeginInfo.calloc().sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO).pNext(NULL)
        var err = vkBeginCommandBuffer(commandBuffer, cmdBufInfo)
        cmdBufInfo.free()
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to begin command buffer: " + VU.translate(err))
        }

        val postPresentBarrier = createPostPresentBarrier(image)
        vkCmdPipelineBarrier(
            commandBuffer,
            VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
            VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
            VK_FLAGS_NONE,
            null, // No memory barriers,
            null, // No buffer barriers,
            postPresentBarrier) // one image barrier
        postPresentBarrier.free()

        err = vkEndCommandBuffer(commandBuffer)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to wait for idle queue: " + VU.translate(err))
        }

        // Submit the command buffer
        submitCommandBuffer(queue, commandBuffer)
    }

    override fun close() {
        vkDestroyInstance(instance, null)
    }
}
