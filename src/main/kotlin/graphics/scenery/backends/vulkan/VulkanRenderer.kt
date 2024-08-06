package graphics.scenery.backends.vulkan

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.attribute.renderable.DelegatesRenderable
import graphics.scenery.attribute.renderable.HasCustomRenderable
import graphics.scenery.attribute.renderable.Renderable
import graphics.scenery.backends.*
import graphics.scenery.textures.Texture
import graphics.scenery.utils.*
import graphics.scenery.utils.extensions.applyVulkanCoordinateSystem
import kotlinx.coroutines.*
import org.joml.*
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.glfwGetError
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions
import org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.Platform
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugReport.*
import org.lwjgl.vulkan.EXTDebugUtils.*
import org.lwjgl.vulkan.KHRSurface.VK_KHR_SURFACE_EXTENSION_NAME
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.KHRWin32Surface.VK_KHR_WIN32_SURFACE_EXTENSION_NAME
import org.lwjgl.vulkan.KHRXlibSurface.VK_KHR_XLIB_SURFACE_EXTENSION_NAME
import org.lwjgl.vulkan.MVKMacosSurface.VK_MVK_MACOS_SURFACE_EXTENSION_NAME
import org.lwjgl.vulkan.VK10.*
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import javax.imageio.ImageIO
import kotlin.collections.ArrayList
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.reflect.full.*
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime


/**
 * Vulkan Renderer
 *
 * @param[hub] Hub instance to use and attach to.
 * @param[applicationName] The name of this application.
 * @param[scene] The [Scene] instance to initialize first.
 * @param[windowWidth] Horizontal window size.
 * @param[windowHeight] Vertical window size.
 * @param[embedIn] An optional [SceneryPanel] in which to embed the renderer instance.
 * @param[renderConfigFile] The file to create a [RenderConfigReader.RenderConfig] from.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

@OptIn(ExperimentalTime::class)
@Suppress("MemberVisibilityCanBePrivate")
open class VulkanRenderer(hub: Hub,
                          applicationName: String,
                          scene: Scene,
                          windowWidth: Int,
                          windowHeight: Int,
                          final override var embedIn: SceneryPanel? = null,
                          renderConfigFile: String) : Renderer(), AutoCloseable {

    protected val logger by lazyLogger()

    // helper classes
    data class PresentHelpers(
        var signalSemaphore: LongBuffer = memAllocLong(1),
        var waitSemaphore: LongBuffer = memAllocLong(2),
        var commandBuffers: PointerBuffer = memAllocPointer(1),
        var waitStages: IntBuffer = memAllocInt(2),
        var submitInfo: VkSubmitInfo = VkSubmitInfo.calloc(),
        var imageUsageFence: Long = -1L
    )

    enum class VertexDataKinds {
        None,
        PositionNormalTexcoord,
        PositionTexcoords,
        PositionNormal
    }

    enum class StandardSemaphores {
        RenderComplete,
        ImageAvailable,
        PresentComplete
    }

    data class VertexDescription(
        val state: VkPipelineVertexInputStateCreateInfo,
        val attributeDescription: VkVertexInputAttributeDescription.Buffer?,
        val bindingDescription: VkVertexInputBindingDescription.Buffer?
    )

    data class CommandPools(
        var Standard: Long = -1L,
        var Render: Long = -1L,
        var Compute: Long = -1L,
        var Transfer: Long = -1L
    )

    data class DeviceAndGraphicsQueueFamily(
        val device: VkDevice? = null,
        val graphicsQueue: Int = 0,
        val computeQueue: Int = 0,
        val presentQueue: Int = 0,
        val transferQueue: Int = 0,
        val memoryProperties: VkPhysicalDeviceMemoryProperties? = null
    )

    class Pipeline {
        internal var pipeline: Long = 0
        internal var layout: Long = 0
        internal var type: VulkanPipeline.PipelineType = VulkanPipeline.PipelineType.Graphics
    }

    sealed class DescriptorSet(val id: Long = 0L, val name: String = "") {
        object None: DescriptorSet(0L)
        data class Set(val setId: Long, val setName: String = "") : DescriptorSet(setId, setName)
        data class DynamicSet(val setId: Long, val offset: Int, val setName: String = "") : DescriptorSet(setId, setName)

        companion object {
            fun setOrNull(id: Long?, setName: String): DescriptorSet? {
                return if(id == null) {
                    null
                } else {
                    Set(id, setName)
                }
            }
        }
    }

    private val lateResizeInitializers = ConcurrentHashMap<Renderable, () -> Any>()

    inner class SwapchainRecreator {
        var mustRecreate = true
        var afterRecreateHook: (SwapchainRecreator) -> Unit = {}

        private val lock = ReentrantLock()

        @Synchronized fun recreate() {
            if(lock.tryLock() && !shouldClose) {
                logger.info("Recreating Swapchain at frame $frames (${swapchain.javaClass.simpleName})")
                // create new swapchain with changed surface parameters
                vkQueueWaitIdle(queue.queue)

                with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
                    // Create the swapchain (this will also add a memory barrier to initialize the framebuffer images)

                    swapchain.create(oldSwapchain = swapchain)

                    endCommandBuffer(this@VulkanRenderer.device, commandPools.Standard, queue, flush = true, dealloc = true)

                    this
                }

                VulkanRenderpass.createPipelineCache(device)

                val refreshResolutionDependentResources = {
                    renderpasses.values.forEach { it.close() }
                    renderpasses.clear()

                    settings.set("Renderer.displayWidth", (window.width * settings.get<Float>("Renderer.SupersamplingFactor")).toInt())
                    settings.set("Renderer.displayHeight", (window.height * settings.get<Float>("Renderer.SupersamplingFactor")).toInt())

                    val flowAndPasses = VulkanRenderpass.prepareRenderpassesFromConfig(renderConfig, device, commandPools, queue, vertexDescriptors, swapchain, window.width, window.height, settings)

                    flow = flowAndPasses.first
                    flowAndPasses.second.forEach { (k, v) -> renderpasses.put(k, v) }

                    semaphores.forEach { it.value.forEach { semaphore -> device.removeSemaphore(semaphore) }}
                    semaphores = prepareStandardSemaphores(device)

                    // Create render command buffers
                    vkResetCommandPool(device.vulkanDevice, commandPools.Render, VK_FLAGS_NONE)

                    scene.findObserver()?.let { cam ->
                        when(cam.projectionType) {
                            Camera.ProjectionType.Orthographic ->
                                cam.orthographicCamera(cam.fov, window.width, window.height, cam.nearPlaneDistance, cam.farPlaneDistance)

                            Camera.ProjectionType.Perspective ->
                                cam.perspectiveCamera(cam.fov, window.width, window.height, cam.nearPlaneDistance, cam.farPlaneDistance)

                            Camera.ProjectionType.Undefined -> {
                                logger.warn("Camera ${cam.name} has undefined projection type, using default perspective projection")
                                cam.perspectiveCamera(cam.fov, window.width, window.height, cam.nearPlaneDistance, cam.farPlaneDistance)
                            }
                        }
                    }

                    logger.debug("Calling late resize initializers for ${lateResizeInitializers.keys.joinToString(", ")}")
                    lateResizeInitializers.map { it.value.invoke() }
                }

                refreshResolutionDependentResources.invoke()

                totalFrames = 0
                mustRecreate = false

                afterRecreateHook.invoke(this)

                settings.set("VulkanRenderer.swapchainImageCount", swapchain.images.size)
                lock.unlock()
            }
        }
    }

    private fun Throwable.filteredStackTrace(): List<String> {
        val firstIndex = this.stackTrace.indexOfFirst { it.toString().contains("VkDebugUtilsMessengerCallbackEXTI") } + 1
        val lastIndex = this.stackTrace.indexOfLast { it.toString().contains("SceneryBase") } - 1

        return this.stackTrace.copyOfRange(firstIndex, lastIndex)
            .filter { !it.toString().contains(".coroutines.") }
            .map { " at $it" }
    }

    var debugCallbackUtils = callback@ { severity: Int, type: Int, callbackDataPointer: Long, _: Long ->
        val dbg = if (type and VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT == VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT) {
            " (performance)"
        } else if(type and VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT == VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT) {
            ""
        } else {
            ""
        }

        val callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(callbackDataPointer)
        val message = callbackData.pMessageString()
        val objectType = 0

        when (severity) {
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT ->
                logger.error("🌋⛔️ Validation$dbg: $message")
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT ->
                logger.warn("🌋⚠️ Validation$dbg: $message")
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT ->
                logger.info("🌋ℹ️ Validation$dbg: $message")
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT ->
                logger.info("(verbose) $message")
            else -> logger.info("🌋🤷 Validation (unknown message type)$dbg: $message")
        }

        // trigger exception and delay if strictValidation is activated in general, or only for specific object types
        if(strictValidation.first && strictValidation.second.isEmpty() ||
            strictValidation.first && strictValidation.second.contains(objectType)) {
            if(severity < VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) {
                return@callback VK_FALSE
            }
            // set 15s of delay until the next frame is rendered if a validation error happens
            renderDelay = System.getProperty("scenery.VulkanRenderer.DefaultRenderDelay", "1500").toLong()

            val e = VulkanValidationLayerException("Vulkan validation layer exception, see validation layer error messages above. To disable these exceptions, set scenery.VulkanRenderer.StrictValidation=false.")
            e.filteredStackTrace().forEach { logger.error(it) }
        }

        // return false here, otherwise the application would quit upon encountering a validation error.
        return@callback VK_FALSE
    }

    /** Debug callback to be used upon encountering validation messages or errors */
    var debugCallback = object : VkDebugReportCallbackEXT() {
        override operator fun invoke(flags: Int, objectType: Int, obj: Long, location: Long, messageCode: Int, pLayerPrefix: Long, pMessage: Long, pUserData: Long): Int {
            val dbg = if (flags and VK_DEBUG_REPORT_DEBUG_BIT_EXT == VK_DEBUG_REPORT_DEBUG_BIT_EXT) {
                " (debug)"
            } else {
                ""
            }

            when {
                flags and VK_DEBUG_REPORT_ERROR_BIT_EXT == VK_DEBUG_REPORT_ERROR_BIT_EXT ->
                    logger.error("!! $obj($objectType) Validation$dbg: " + getString(pMessage))
                flags and VK_DEBUG_REPORT_WARNING_BIT_EXT == VK_DEBUG_REPORT_WARNING_BIT_EXT ->
                    logger.warn("!! $obj($objectType) Validation$dbg: " + getString(pMessage))
                flags and VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT == VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT ->
                    logger.error("!! $obj($objectType) Validation (performance)$dbg: " + getString(pMessage))
                flags and VK_DEBUG_REPORT_INFORMATION_BIT_EXT == VK_DEBUG_REPORT_INFORMATION_BIT_EXT ->
                    logger.info("!! $obj($objectType) Validation$dbg: " + getString(pMessage))
                else ->
                    logger.info("!! $obj($objectType) Validation (unknown message type)$dbg: " + getString(pMessage))
            }

            // trigger exception and delay if strictValidation is activated in general, or only for specific object types
            if(strictValidation.first && strictValidation.second.isEmpty() ||
                strictValidation.first && strictValidation.second.contains(objectType)) {
                if(!(flags and VK_DEBUG_REPORT_ERROR_BIT_EXT == VK_DEBUG_REPORT_ERROR_BIT_EXT)) {
                    return VK_FALSE
                }
                // set 15s of delay until the next frame is rendered if a validation error happens
                renderDelay = System.getProperty("scenery.VulkanRenderer.DefaultRenderDelay", "1500").toLong()

                val e = VulkanValidationLayerException("Vulkan validation layer exception, see validation layer error messages above. To disable these exceptions, set scenery.VulkanRenderer.StrictValidation=false. Stack trace:")
                e.filteredStackTrace().forEach { logger.error(it) }
            }

            // return false here, otherwise the application would quit upon encountering a validation error.
            return VK_FALSE
        }
    }

    // helper classes end



    final override var hub: Hub? = null
    protected var applicationName = ""
    final override var settings: Settings = Settings()
    override var shouldClose = false
    private var toggleFullscreen = false
    override var managesRenderLoop = false
    override var lastFrameTime = System.nanoTime() * 1.0f
    final override var initialized = false
    override var firstImageReady: Boolean = false

    private var screenshotRequested = false
    private var screenshotOverwriteExisting = false
    private var screenshotFilename = ""
    var screenshotBuffer: VulkanBuffer? = null
    var imageBuffer: ByteBuffer? = null
    var encoder: VideoEncoder? = null
    private var movieFilename = ""
    private var recordMovie: Boolean = false
    private var recordMovieOverwrite: Boolean = false
    override var pushMode: Boolean = false

    val persistentTextureRequests = ArrayList<Pair<Texture, AtomicInteger>>()

    var scene: Scene = Scene()
    protected var sceneArray: HashSet<Node> = HashSet(256)

    protected var commandPools = CommandPools()
    protected val renderpasses: MutableMap<String, VulkanRenderpass> = Collections.synchronizedMap(LinkedHashMap<String, VulkanRenderpass>())

    protected var validation = java.lang.Boolean.parseBoolean(System.getProperty("scenery.VulkanRenderer.EnableValidations", "false"))
    protected val strictValidation = getStrictValidation()
    protected val wantsOpenGLSwapchain = java.lang.Boolean.parseBoolean(System.getProperty("scenery.VulkanRenderer.UseOpenGLSwapchain", "false"))
    protected val defaultValidationLayers = arrayOf("VK_LAYER_KHRONOS_validation")

    protected var instance: VkInstance
    protected var device: VulkanDevice

    protected var debugCallbackHandle: Long = -1L

    // Create static Vulkan resources
    protected var queue: VulkanDevice.QueueWithMutex
    protected var transferQueue: VulkanDevice.QueueWithMutex

    protected var swapchain: Swapchain
    protected var ph = PresentHelpers()

    final override var window: SceneryWindow = SceneryWindow.UninitializedWindow()

    protected val swapchainRecreator: SwapchainRecreator
    protected var pipelineCache: Long = -1L
    protected var vertexDescriptors = ConcurrentHashMap<VertexDataKinds, VertexDescription>()
    protected var sceneUBOs = ArrayList<Node>()
    protected var geometryPool: VulkanBufferPool
    protected var ssboUploadPool: VulkanBufferPool
    protected var ssboDownloadPool: VulkanBufferPool
    // not using a separate staging pool: SSBO content overflows into VertexAttribute upload TODO: investigate why
    protected var ssboStagingPool: VulkanBufferPool
    protected var stagingPool: VulkanBufferPool
    protected var semaphores = ConcurrentHashMap<StandardSemaphores, Array<Long>>()

    data class DefaultBuffers(var UBOs: VulkanBuffer,
                              var LightParameters: VulkanBuffer,
                              var VRParameters: VulkanBuffer,
                              var ShaderProperties: VulkanBuffer)
    protected var defaultBuffers: DefaultBuffers
    protected var defaultUBOs = ConcurrentHashMap<String, VulkanUBO>()
    protected var textureCache = ConcurrentHashMap<Texture, VulkanTexture>()
    protected var defaultTextures = ConcurrentHashMap<String, VulkanTexture>()
    protected var descriptorSetLayouts = ConcurrentHashMap<String, Long>()
    protected var descriptorSets = ConcurrentHashMap<String, Long>()

    protected var lastTime = System.nanoTime()
    protected var time = 0.0f
    var fps = 0
        protected set
    protected var frames = 0
    protected var renderDelay = 0L
    protected var heartbeatTimer = Timer()
    protected var gpuStats: GPUStats? = null

    private var renderConfig: RenderConfigReader.RenderConfig
    private var flow: List<String> = listOf()

    final override var renderConfigFile: String = ""
        set(config) {
            field = config

            this.renderConfig = RenderConfigReader().loadFromFile(renderConfigFile)

            // check for null as this is used in the constructor as well where
            // the swapchain recreator is not yet initialized
            @Suppress("SENSELESS_COMPARISON")
            if (swapchainRecreator != null) {
                swapchainRecreator.mustRecreate = true
                logger.info("Loaded ${renderConfig.name} (${renderConfig.description ?: "no description"})")
            }
        }

    companion object {
        private const val VK_FLAGS_NONE: Int = 0
        private const val UINT64_MAX: Long = -1L

        private const val MATERIAL_HAS_DIFFUSE = 0x0001
        private const val MATERIAL_HAS_AMBIENT = 0x0002
        private const val MATERIAL_HAS_SPECULAR = 0x0004
        private const val MATERIAL_HAS_NORMAL = 0x0008
        private const val MATERIAL_HAS_ALPHAMASK = 0x0010

        private val availableSwapchains = mutableListOf(
            SwingSwapchain::class.java,
            HeadlessSwapchain::class.java,
            OpenGLSwapchain::class.java
        )

        fun getStrictValidation(): Pair<Boolean, List<Int>> {
            val strict = System.getProperty("scenery.VulkanRenderer.StrictValidation")
            val separated = strict?.split(",")?.asSequence()?.mapNotNull { it.toIntOrNull() }?.toList()

            return when {
                strict == null -> false to emptyList()
                strict == "true" -> true to emptyList()
                strict == "false" -> false to emptyList()
                separated != null && separated.count() > 0 -> true to separated
                else -> false to emptyList()
            }
        }

        /**
         * Registers a new special-purpose swapchain with the Vulkan renderer.
         */
        fun registerSwapchain(swapchain: Class<VulkanSwapchain>) {
            availableSwapchains.add(swapchain)
        }

    }

    fun getCurrentScene(): Scene {
        return scene
    }

    init {
        stackPush().use { stack ->
            this.hub = hub

            val hmd = hub.getWorkingHMDDisplay()
            if (hmd != null) {
                logger.debug("Setting window dimensions to bounds from HMD")
                val bounds = hmd.getRenderTargetSize()
                window.width = bounds.x() * 2
                window.height = bounds.y()
            } else {
                window.width = windowWidth
                window.height = windowHeight
            }

            this.applicationName = applicationName
            this.scene = scene

            this.settings = loadDefaultRendererSettings((hub.get(SceneryElement.Settings) as Settings))

            logger.debug("Loading rendering config from $renderConfigFile")
            this.renderConfigFile = renderConfigFile
            this.renderConfig = RenderConfigReader().loadFromFile(renderConfigFile)

            logger.info("Loaded ${renderConfig.name} (${renderConfig.description ?: "no description"})")

            /*if((System.getenv("ENABLE_VULKAN_RENDERDOC_CAPTURE")?.toInt() == 1  || Renderdoc.renderdocAttached) && validation) {
                logger.warn("Validation Layers requested, but Renderdoc capture and Validation Layers are mutually incompatible. Disabling validations layers.")
                validation = false
            }*/

            // explicitly create VK, to make GLFW pick up MoltenVK on OS X
            if(ExtractsNatives.getPlatform() == ExtractsNatives.Platform.MACOS) {
                try {
                    Configuration.VULKAN_EXPLICIT_INIT.set(true)
                    VK.create()
                } catch (e: IllegalStateException) {
                    logger.warn("IllegalStateException during Vulkan initialisation")
                }
            }

            val headlessRequested = System.getProperty(Renderer.HEADLESS_PROPERTY_NAME)?.toBoolean() ?: false
            // GLFW works kinda shaky on macOS, we create a JFrame here for a nicer experience then.
            // That is of course unless [embedIn] is already set.
            if(Platform.get() == Platform.MACOSX && embedIn == null && !headlessRequested) {
                embedIn = SwingSwapchain.createApplicationFrame(
                    applicationName,
                    windowWidth,
                    windowHeight
                )
            }

            // Create the Vulkan instance
            instance = if(embedIn != null || headlessRequested) {
                logger.debug("Running embedded or headless, skipping GLFW initialisation.")

                // macOS needs a Metal surface for embedding
                val requiredExtensions = if(ExtractsNatives.getPlatform() == ExtractsNatives.Platform.MACOS && !headlessRequested) {
                    stack.pointers(
                        stack.UTF8(VK_KHR_SURFACE_EXTENSION_NAME),
                        stack.UTF8(EXTMetalSurface.VK_EXT_METAL_SURFACE_EXTENSION_NAME)
                    )
                } else {
                    stack.pointers(
                        stack.UTF8(VK_KHR_SURFACE_EXTENSION_NAME)
                    )
                }

                createInstance(
                    requiredExtensions,
                    validation,
                    headless = headlessRequested
                )
            } else {
                if (!glfwInit()) {
                    val buffer = PointerBuffer.allocateDirect(255)
                    val error = glfwGetError(buffer)

                    val description = if(error != 0) {
                        buffer.stringUTF8
                    } else {
                        "no error"
                    }

                    throw RendererUnavailableException("Failed to initialize GLFW: $description ($error)")
                }
                if (!glfwVulkanSupported()) {
                    throw RendererUnavailableException("Failed to find Vulkan loader. Is Vulkan supported by your GPU and do you have the most recent graphics drivers installed?")
                }

                /* Look for instance extensions */
                val requiredExtensions = glfwGetRequiredInstanceExtensions() ?: throw RendererUnavailableException("Failed to find list of required Vulkan extensions")
                createInstance(requiredExtensions, validation)
            }

            debugCallbackHandle = if(validation) {
                setupDebuggingDebugUtils(instance,
                    VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT
                        or VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
                        or VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
                        or VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT,
                    debugCallbackUtils)
            } else {
                -1L
            }

            val requestedValidationLayers = if(validation) {
                if(wantsOpenGLSwapchain) {
                    logger.warn("Requested OpenGL swapchain, validation layers disabled.")
                    emptyArray()
                } else {
                    defaultValidationLayers
                }
            } else {
                emptyArray()
            }

            // get available swapchains, but remove default swapchain, will always be there as fallback
            logger.info("Available special-purpose swapchains are: ${availableSwapchains.joinToString { it.simpleName }}")
            val selectedSwapchain = availableSwapchains.firstOrNull() {
                val result = (it.kotlin.companionObjectInstance as SwapchainParameters).usageCondition.invoke(embedIn)
                logger.debug("Swapchain usage condition result for ${it.simpleName} is $result")
                result
            }
            val headless = (selectedSwapchain?.kotlin?.companionObjectInstance as? SwapchainParameters)?.headless ?: false

            device = VulkanDevice.fromPhysicalDevice(instance,
                physicalDeviceFilter = { index, device ->
                    val namePreference = System.getProperty("scenery.Renderer.Device", "DOES_NOT_EXIST")
                    val idPreference = System.getProperty("scenery.Renderer.DeviceId", "DOES_NOT_EXIST").toIntOrNull()
                    when {
                        idPreference != null -> index == idPreference
                        else -> "${device.vendor} ${device.name}".contains(namePreference)
                    }
                },
                additionalExtensions = { physicalDevice -> hub.getWorkingHMDDisplay()?.getVulkanDeviceExtensions(physicalDevice)?.toMutableList() ?: mutableListOf(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME) },
                validationLayers = requestedValidationLayers,
                headless = headless,
                debugEnabled = validation
            )

            logger.debug("Device creation done")

            if(device.deviceData.vendor.lowercase().contains("nvidia") && ExtractsNatives.getPlatform() == ExtractsNatives.Platform.WINDOWS) {
                try {
                    gpuStats = NvidiaGPUStats()
                } catch(e: NullPointerException) {
                    logger.warn("Could not initialize Nvidia GPU stats")
                    if(logger.isDebugEnabled) {
                        logger.warn("Reason: ${e.message}, traceback follows:")
                        e.printStackTrace()
                    }
                }
            }

            queue = device.getQueue(device.queueIndices.graphicsQueue.first)
            logger.debug("Creating transfer queue with ${device.queueIndices.transferQueue.first} (vs ${device.queueIndices.graphicsQueue.first})")
            transferQueue = device.getQueue(device.queueIndices.transferQueue.first)
            logger.debug("Have {} and {}", queue, transferQueue)

            with(commandPools) {
                Render = device.createCommandPool(device.queueIndices.graphicsQueue.first)
                Standard = device.createCommandPool(device.queueIndices.graphicsQueue.first)
                Compute = device.createCommandPool(device.queueIndices.computeQueue.first)
                Transfer = device.createCommandPool(device.queueIndices.transferQueue.first)
            }
            logger.debug("Creating command pools done")

            swapchainRecreator = SwapchainRecreator()
            swapchain = when {
                selectedSwapchain != null -> {
                    logger.info("Using swapchain ${selectedSwapchain.simpleName}")
                    val params = selectedSwapchain.kotlin.primaryConstructor!!.parameters.associateWith { param ->
                        when(param.name) {
                            "device" -> device
                            "queue" -> queue
                            "commandPools" -> commandPools
                            "renderConfig" -> renderConfig
                            "useSRGB" -> renderConfig.sRGB
                            "vsync" -> !settings.get<Boolean>("Renderer.DisableVsync")
                            else -> null
                        }
                    }.filter { it.value != null }

                    // The cast is actually necessary, despite the IDE saying otherwise
                    @Suppress("USELESS_CAST")
                    selectedSwapchain
                        .kotlin
                        .primaryConstructor!!
                        .callBy(params) as Swapchain
                }
                else -> {
                    logger.info("Using default swapchain")
                    VulkanSwapchain(
                        device, queue, commandPools,
                        renderConfig = renderConfig, useSRGB = renderConfig.sRGB,
                        vsync = !settings.get<Boolean>("Renderer.DisableVsync"),
                        undecorated = settings.get("Renderer.ForceUndecoratedWindow"))
                }
            }.apply {
                embedIn(embedIn)
                window = createWindow(window, swapchainRecreator)
            }

            logger.debug("Created swapchain with image size ${window.width}x${window.height}")
            vertexDescriptors = prepareStandardVertexDescriptors()
            logger.debug("Created vertex descriptors")

            descriptorSetLayouts = prepareDefaultDescriptorSetLayouts(device)
            logger.debug("Prepared default DSLs")
            defaultBuffers = prepareDefaultBuffers(device)
            logger.debug("Prepared default buffers")

            prepareDescriptorSets(device)
            logger.debug("Prepared default descriptor sets")
            prepareDefaultTextures(device)
            logger.debug("Prepared default textures")

            heartbeatTimer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (window.shouldClose) {
                        shouldClose = true
                        return
                    }

                    fps = frames
                    frames = 0

                    if(!pushMode) {
                        (hub.get(SceneryElement.Statistics) as? Statistics)?.add("Renderer.fps", fps, false)
                    }

                    gpuStats?.let {
                        it.update(0)

                        hub.get(SceneryElement.Statistics).let { s ->
                            val stats = s as Statistics

                            stats.add("GPU", it.get("GPU"), isTime = false)
                            stats.add("GPU bus", it.get("Bus"), isTime = false)
                            stats.add("GPU mem", it.get("AvailableDedicatedVideoMemory"), isTime = false)
                        }

                        if (settings.get("Renderer.PrintGPUStats")) {
                            logger.info(it.utilisationToString())
                            logger.info(it.memoryUtilisationToString())
                        }
                    }

                    val validationsEnabled = if (validation) {
                        ", validation layers enabled"
                    } else {
                        ""
                    }

                    if(embedIn == null || (embedIn as? SceneryJPanel)?.owned == true) {
                        window.title = "$applicationName [${this@VulkanRenderer.renderConfig.name}$validationsEnabled] $fps fps"
                    }
                }
            }, 0, 1000)

            lastTime = System.nanoTime()
            time = 0.0f

            if(System.getProperty("scenery.RunFullscreen","false")?.toBoolean() == true) {
                toggleFullscreen = true
            }

            geometryPool = VulkanBufferPool(
                device,
                usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT or VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                poolName = "GeometryPool"
            )

            ssboUploadPool = VulkanBufferPool(
                device,
                usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT or VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                bufferSize = 1024*1024,
                poolName = "ssboUploadPool"
            )

            ssboDownloadPool = VulkanBufferPool(
                device,
                usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT or VK_BUFFER_USAGE_TRANSFER_SRC_BIT or VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                bufferSize = 1024*1024,
                poolName = "ssboDownloadPool"
            )

            ssboStagingPool = VulkanBufferPool(
                device,
                usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                properties = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                bufferSize = 64*1024*1024,
                poolName = "ssboStagingPool"
            )

            stagingPool = VulkanBufferPool(
                device,
                usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                properties = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                bufferSize = 64*1024*1024,
                poolName = "StagingPool"
            )

            initialized = true
            logger.info("Renderer initialisation complete.")
        }
    }

    // source: http://stackoverflow.com/questions/34697828/parallel-operations-on-kotlin-collections
    // Thanks to Holger :-)
    @Suppress("UNUSED")
    fun <T, R> Iterable<T>.parallelMap(
        numThreads: Int = java.lang.Runtime.getRuntime().availableProcessors(),
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

    @Suppress("UNUSED")
    fun setCurrentScene(scene: Scene) {
        this.scene = scene
    }

    /**
     * This function should initialize the current scene contents.
     */
    override fun initializeScene() {
        logger.info("Scene initialization started.")
        val start = System.nanoTime()

        this.scene.discover(this.scene, { it !is Light })
//            .parallelMap(numThreads = System.getProperty("scenery.MaxInitThreads", "1").toInt()) { node ->
            .map { node ->
                // skip initialization for nodes that are only instance slaves
                logger.debug("Initializing object '${node.name}'")

                initializeNode(node)
            }

        scene.initialized = true
        logger.info("Scene initialization complete, took ${(System.nanoTime() - start)/10e6} ms.")
    }

    fun Boolean.toInt(): Int {
        return if (this) {
            1
        } else {
            0
        }
    }

    fun updateNodeGeometry(node: Node) {
        val renderable = node.renderableOrNull() ?: return
        node.ifGeometry {
            if (vertices.remaining() > 0) {
                renderable.rendererMetadata()?.let { s ->
                    VulkanNodeHelpers.createVertexBuffers(
                        device,
                        node,
                        s,
                        stagingPool,
                        geometryPool,
                        commandPools,
                        queue
                    )
                }
            }
        }
    }

    /**
     * Uploads changed SSBO buffer contents of [node] from Local to Device memory
     */
    fun updateNodeSSBOs(node: Node) {
        val renderable = node.renderableOrNull() ?: return
        val s: VulkanRendererMetadata = renderable.rendererMetadata() ?: throw IllegalStateException("Node ${node.name} does not contain metadata object")
        node.ifBuffers {
            VulkanNodeHelpers.updateShaderStorageBuffers(
                device,
                node,
                s,
                ssboStagingPool,
                ssboUploadPool,
                ssboDownloadPool,
                commandPools,
                queue
            )
        }
    }

    /**
     * Downloads requested SSBO buffer contents of [node] from Local to Device memory
     */
    fun downloadNodeSSBOs(node: Node) {
        val renderable = node.renderableOrNull() ?: return
        val s: VulkanRendererMetadata = renderable.rendererMetadata() ?: throw IllegalStateException("Node ${node.name} does not contain metadata object")
        node.ifBuffers {
            VulkanNodeHelpers.downloadShaderStorageBuffers(
                device,
                node,
                s,
                commandPools,
                queue
            )
        }
    }

    /**
     * Returns the material type flag for a Node, considering it's [Material]'s textures.
     */
    protected fun Material.materialTypeFromTextures(s: VulkanRendererMetadata): Int {
        var materialType = 0
        if (this.textures.containsKey("ambient") && !s.defaultTexturesFor.contains("ambient")) {
            materialType = materialType or MATERIAL_HAS_AMBIENT
        }

        if (this.textures.containsKey("diffuse") && !s.defaultTexturesFor.contains("diffuse")) {
            materialType = materialType or MATERIAL_HAS_DIFFUSE
        }

        if (this.textures.containsKey("specular") && !s.defaultTexturesFor.contains("specular")) {
            materialType = materialType or MATERIAL_HAS_SPECULAR
        }

        if (this.textures.containsKey("normal") && !s.defaultTexturesFor.contains("normal")) {
            materialType = materialType or MATERIAL_HAS_NORMAL
        }

        if (this.textures.containsKey("alphamask") && !s.defaultTexturesFor.contains("alphamask")) {
            materialType = materialType or MATERIAL_HAS_ALPHAMASK
        }

        return materialType
    }

    /**
     * Initialises a given [Node] with the metadata required by the [VulkanRenderer].
     */
    fun initializeNode(node: Node): Boolean {
        val renderable = node.renderableOrNull() ?: return false
        val material = node.materialOrNull() ?: return false
        if(node is DelegatesRenderable) {
            logger.debug("Initialising node {} with delegate {} (state={})", node, renderable, node.state)
        } else {
            logger.debug("Initialising node {}", node)
        }

        if(renderable.rendererMetadata() == null) {
            renderable.metadata["VulkanRenderer"] = VulkanRendererMetadata()
        }

        var s: VulkanRendererMetadata = renderable.rendererMetadata() ?: throw IllegalStateException("Node ${node.name} does not contain metadata object")

        if(node.state != State.Ready) {
            logger.info("Not initialising Renderable $renderable because state=${node.state}")
            return false
        }

        if (s.initialized) {
            node.initialized = true
            return true
        }

        s.flags.add(RendererFlags.Seen)

        node.ifGeometry {
            // When the vertex buffer comes from elsewhere, the vertex description always differs from default, because of the padding inside a ssbo-layout (always 4 byte blocks)
            // Example:
            //  Position    = vec3 -> vec3 + 1B padding = vec4
            //  Normal      = vec3 -> vec3 + 1B padding = vec4
            //  UV          = vec2 -> vec2 + 2B padding = vec4
            //  Total       = vec3+vec3+vec2 = 32Bytes -> vec4+vec4+vec4 = 48Bytes per Vertex

            logger.debug("Initializing geometry for ${node.name} (${vertices.remaining() / vertexSize} vertices/${indices.remaining()} indices)")
            // determine vertex input type
            s.vertexInputType = when {
                vertices.remaining() > 0 && normals.remaining() > 0 && texcoords.remaining() > 0 -> VertexDataKinds.PositionNormalTexcoord
                vertices.remaining() > 0 && normals.remaining() > 0 && texcoords.remaining() == 0 -> VertexDataKinds.PositionNormal
                vertices.remaining() > 0 && normals.remaining() == 0 && texcoords.remaining() > 0 -> VertexDataKinds.PositionTexcoords
                else -> VertexDataKinds.PositionNormalTexcoord
            }

            // create custom vertex description if necessary, else use one of the defaults
            s.vertexDescription = if (node is InstancedNode) {
                VulkanNodeHelpers.updateInstanceBuffer(device, node, s, commandPools, queue)
                // TODO: Rewrite shader in case it does not conform to coord/normal/texcoord vertex description
                s.vertexInputType = VertexDataKinds.PositionNormalTexcoord
                s.flags += RendererFlags.Instanced
                vertexDescriptionFromInstancedNode(
                    node,
                    vertexDescriptors.getValue(VertexDataKinds.PositionNormalTexcoord)
                )
            } else if(shaderSourced && customVertexLayout != null) {
                customVertexLayout?.let {
                    vertexDescriptionFromCustomVertexLayout(it.membersSizesAndOffsets())
                }
            } else {
                s.flags -= RendererFlags.Instanced
                s.geometryBuffers["instance"]?.close()
                s.geometryBuffers.remove("instance")
                s.geometryBuffers["instanceStaging"]?.close()
                s.geometryBuffers.remove("instanceStaging")
                vertexDescriptors.getValue(s.vertexInputType)
            }

            s = VulkanNodeHelpers.createVertexBuffers(
                device,
                node,
                s,
                stagingPool,
                geometryPool,
                commandPools,
                queue
            )
        }

        val matricesDescriptorSet = getDescriptorCache().getOrPut("Matrices") {
            SimpleTimestamped(device.createDescriptorSetDynamic(
                descriptorSetLayouts["Matrices"]!!, 1,
                defaultBuffers.UBOs))
        }

        val materialPropertiesDescriptorSet = getDescriptorCache().getOrPut("MaterialProperties") {
            SimpleTimestamped(device.createDescriptorSetDynamic(
                descriptorSetLayouts["MaterialProperties"]!!, 1,
                defaultBuffers.UBOs))
        }

        val matricesUbo = VulkanUBO(device, backingBuffer = defaultBuffers.UBOs)
        with(matricesUbo) {
            name = "Matrices"
            node.ifSpatial {
                add("ModelMatrix", { world })
                add("NormalMatrix", { Matrix4f(world).invert().transpose() })
            }
            add("isBillboard", { renderable.isBillboard.toInt() })

            createUniformBuffer()
            sceneUBOs.add(node)

            s.UBOs.put(name, matricesDescriptorSet.contents to this)
        }

        try {
            VulkanNodeHelpers.initializeCustomShadersForNode(device, node, true, renderpasses, lateResizeInitializers, defaultBuffers)
        } catch (e: ShaderCompilationException) {
            logger.error("Compilation of custom shader failed: ${e.message}")
            logger.error("Node ${node.name} will use default shader for render pass.")

            if (logger.isDebugEnabled) {
                e.printStackTrace()
            }
        }

        node.ifBuffers {
            logger.debug("Initializing ssbos for ${node.name}")
            s = VulkanNodeHelpers.updateShaderStorageBuffers(
                device,
                node,
                s,
                ssboStagingPool,
                ssboUploadPool,
                ssboDownloadPool,
                commandPools,
                queue
            )
        }

        val (_, descriptorUpdated) = VulkanNodeHelpers.loadTexturesForNode(
            device,
            node,
            s,
            defaultTextures,
            textureCache,
            commandPools,
            queue,
            transferQueue
        )

        if(descriptorUpdated) {
            s.texturesToDescriptorSets(device,
                renderpasses.filter { it.value.passConfig.type != RenderConfigReader.RenderpassType.quad },
                renderable)
        }

        s.materialHashCode = material.materialHashCode()

        val materialUbo = VulkanUBO(device, backingBuffer = defaultBuffers.UBOs)
        with(materialUbo) {
            name = "MaterialProperties"
            add("materialType", { node.materialOrNull()!!.materialTypeFromTextures(renderable.rendererMetadata()!!) })
            add("Ka", { node.materialOrNull()!!.ambient })
            add("Kd", { node.materialOrNull()!!.diffuse })
            add("Ks", { node.materialOrNull()!!.specular })
            add("Roughness", { node.materialOrNull()!!.roughness})
            add("Metallic", { node.materialOrNull()!!.metallic})
            add("Opacity", { node.materialOrNull()!!.blending.opacity })
            add("Emissive", { node.materialOrNull()!!.emissive })

            createUniformBuffer()
            s.UBOs.put("MaterialProperties", materialPropertiesDescriptorSet.contents to this)
        }

        s.flags.add(RendererFlags.Initialised)
        node.initialized = true
        renderable.metadata["VulkanRenderer"] = s

        return true
    }

    protected fun destroyNode(node: Node, onShutdown: Boolean = false) {
        logger.trace("Destroying node ${node.name}...")
        val renderable = node.renderableOrNull()
        if (!(renderable?.metadata?.containsKey("VulkanRenderer") ?: false)) {
            return
        }

        lateResizeInitializers.remove(renderable)
        node.initialized = false

        renderable?.rendererMetadata()?.UBOs?.forEach { it.value.second.close() }

        node.ifGeometry {
            renderable?.rendererMetadata()?.geometryBuffers?.forEach {
                it.value.close()
            }
        }

        if(onShutdown) {
            renderable?.rendererMetadata()?.textures?.forEach { it.value.close() }
        }
        renderable?.metadata?.remove("VulkanRenderer")
    }

    protected fun prepareDefaultDescriptorSetLayouts(device: VulkanDevice): ConcurrentHashMap<String, Long> {
        val m = ConcurrentHashMap<String, Long>()

        m["Matrices"] = device.createDescriptorSetLayout(
            listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1)),
            0,
            VK_SHADER_STAGE_ALL)

        m["MaterialProperties"] = device.createDescriptorSetLayout(
            listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1)),
            0,
            VK_SHADER_STAGE_ALL)

        m["LightParameters"] = device.createDescriptorSetLayout(
            listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1)),
            0,
            VK_SHADER_STAGE_ALL)

        m["VRParameters"] = device.createDescriptorSetLayout(
            listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1)),
            0,
            VK_SHADER_STAGE_ALL)

        return m
    }

    protected fun prepareDescriptorSets(device: VulkanDevice) {
        this.descriptorSets["Matrices"] = device.createDescriptorSetDynamic(
                descriptorSetLayouts["Matrices"]!!, 1,
                defaultBuffers.UBOs)

        this.descriptorSets["MaterialProperties"] = device.createDescriptorSetDynamic(
                descriptorSetLayouts["MaterialProperties"]!!, 1,
                defaultBuffers.UBOs)

        val lightUbo = VulkanUBO(device)
        lightUbo.add("ViewMatrix0", { Matrix4f().identity() })
        lightUbo.add("ViewMatrix1", { Matrix4f().identity() })
        lightUbo.add("InverseViewMatrix0", { Matrix4f().identity() })
        lightUbo.add("InverseViewMatrix1", { Matrix4f().identity() })
        lightUbo.add("ProjectionMatrix", { Matrix4f().identity() })
        lightUbo.add("InverseProjectionMatrix", { Matrix4f().identity() })
        lightUbo.add("CamPosition", { Vector3f(0.0f) })
        lightUbo.createUniformBuffer()
        lightUbo.populate()

        defaultUBOs["LightParameters"] = lightUbo

        this.descriptorSets["LightParameters"] = device.createDescriptorSet(
                descriptorSetLayouts["LightParameters"]!!, 1,
                lightUbo.descriptor)

        val vrUbo = VulkanUBO(device)

        vrUbo.add("projection0", { Matrix4f().identity() } )
        vrUbo.add("projection1", { Matrix4f().identity() } )
        vrUbo.add("inverseProjection0", { Matrix4f().identity() } )
        vrUbo.add("inverseProjection1", { Matrix4f().identity() } )
        vrUbo.add("headShift", { Matrix4f().identity() })
        vrUbo.add("IPD", { 0.0f })
        vrUbo.add("stereoEnabled", { 0 })
        vrUbo.createUniformBuffer()
        vrUbo.populate()

        defaultUBOs["VRParameters"] = vrUbo

        this.descriptorSets["VRParameters"] = device.createDescriptorSet(
                descriptorSetLayouts["VRParameters"]!!, 1,
                vrUbo.descriptor)
    }

    protected fun prepareStandardVertexDescriptors(): ConcurrentHashMap<VertexDataKinds, VertexDescription> {
        val map = ConcurrentHashMap<VertexDataKinds, VertexDescription>()

        VertexDataKinds.values().forEach { kind ->
            val attributeDesc: VkVertexInputAttributeDescription.Buffer?
            var stride = 0

            when (kind) {
                VertexDataKinds.None -> {
                    stride = 0
                    attributeDesc = null
                }

                VertexDataKinds.PositionNormal -> {
                    stride = 3 + 3
                    attributeDesc = VkVertexInputAttributeDescription.calloc(2)

                    attributeDesc.get(1)
                        .binding(0)
                        .location(1)
                        .format(VK_FORMAT_R32G32B32_SFLOAT)
                        .offset(3 * 4)
                }

                VertexDataKinds.PositionNormalTexcoord -> {
                    stride = 3 + 3 + 2
                    attributeDesc = VkVertexInputAttributeDescription.calloc(3)

                    attributeDesc.get(1)
                        .binding(0)
                        .location(1)
                        .format(VK_FORMAT_R32G32B32_SFLOAT)
                        .offset(3 * 4)

                    attributeDesc.get(2)
                        .binding(0)
                        .location(2)
                        .format(VK_FORMAT_R32G32_SFLOAT)
                        .offset(3 * 4 + 3 * 4)
                }

                VertexDataKinds.PositionTexcoords -> {
                    stride = 3 + 2
                    attributeDesc = VkVertexInputAttributeDescription.calloc(2)

                    attributeDesc.get(1)
                        .binding(0)
                        .location(1)
                        .format(VK_FORMAT_R32G32_SFLOAT)
                        .offset(3 * 4)
                }
            }

            attributeDesc?.let {
                if(it.capacity() > 0) {
                    it.get(0).binding(0).location(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0)
                }
            }

            val bindingDesc: VkVertexInputBindingDescription.Buffer? = if (attributeDesc != null) {
                VkVertexInputBindingDescription.calloc(1)
                    .binding(0)
                    .stride(stride * 4)
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)
            } else {
                null
            }

            val inputState = VkPipelineVertexInputStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pNext(NULL)
                .pVertexAttributeDescriptions(attributeDesc)
                .pVertexBindingDescriptions(bindingDesc)

            map[kind] = VertexDescription(inputState, attributeDesc, bindingDesc)
        }

        return map
    }

    /** Data class for encapsulation of shader vertex attributes */
    data class AttributeInfo(val format: Int, val elementByteSize: Int, val elementCount: Int)

    /**
     * Calculates the formats and required sizes for the elements contained in this hash map when
     * used as definition for vertex attributes in a shader.
     */
    protected fun HashMap<String, () -> Any>.getFormatsAndRequiredAttributeSize(): List<AttributeInfo> {
        return this.map {
            val value = it.value.invoke()

            when (value.javaClass) {
                Vector2f::class.java -> AttributeInfo(VK_FORMAT_R32G32_SFLOAT, 4 * 2, 1)
                Vector3f::class.java -> AttributeInfo(VK_FORMAT_R32G32B32_SFLOAT, 3 * 4, 1)
                Vector4f::class.java -> AttributeInfo(VK_FORMAT_R32G32B32A32_SFLOAT, 4 * 4, 1)
                Matrix4f::class.java -> AttributeInfo(VK_FORMAT_R32G32B32A32_SFLOAT, 4 * 4, 4 * 4 / 4)

                else -> {
                    logger.error("Unsupported type for instancing: ${value.javaClass.simpleName}")
                    AttributeInfo(-1, -1, -1)
                }
            }
        }
    }


    protected fun vertexDescriptionFromCustomVertexLayout(sizesAndOffsets : LinkedList<Pair<Int, Int>>) : VertexDescription {
        val attributeDesc: VkVertexInputAttributeDescription.Buffer?
        var stride = 0

        attributeDesc = VkVertexInputAttributeDescription.calloc(sizesAndOffsets.size)
        sizesAndOffsets.forEachIndexed() { i, it ->
            val format = when(it.first) {
                1 -> VK_FORMAT_R32_SFLOAT
                2 -> VK_FORMAT_R32G32_SFLOAT
                3 -> VK_FORMAT_R32G32B32_SFLOAT
                4 -> VK_FORMAT_R32G32B32A32_SFLOAT
                else -> {
                    logger.error("This size is not supported! Falling back to R32G32B32_SFloat")
                    VK_FORMAT_R32G32B32_SFLOAT
                }
            }
            attributeDesc.get(i)
                .binding(0)
                .location(i)
                .format(format)
                .offset(it.second)

            //stride of one vertex is last entry's offset plus last entries size * sizeOf(float) (for now)
            stride += 4 * 4
        }

        val bindingDesc: VkVertexInputBindingDescription.Buffer? = VkVertexInputBindingDescription.calloc(1)
            .binding(0)
            .stride(stride)
            .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

        val inputState = VkPipelineVertexInputStateCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
            .pNext(NULL)
            .pVertexAttributeDescriptions(attributeDesc)
            .pVertexBindingDescriptions(bindingDesc)

        return VertexDescription(inputState, attributeDesc, bindingDesc)
    }
    protected fun vertexDescriptionFromInstancedNode(node: InstancedNode, template: VertexDescription): VertexDescription {
        logger.debug("Creating instanced vertex description for ${node.name}")

        if(template.attributeDescription == null || template.bindingDescription == null) {
            return template
        }

        val attributeDescs = template.attributeDescription
        val bindingDescs = template.bindingDescription

        val formatsAndAttributeSizes = node.instancedProperties.getFormatsAndRequiredAttributeSize()
        val newAttributesNeeded = formatsAndAttributeSizes.map { it.elementCount }.sum()

        val newAttributeDesc = VkVertexInputAttributeDescription
            .calloc(attributeDescs.capacity() + newAttributesNeeded)

        var position: Int
        var offset = 0

        for(i in 0 until attributeDescs.capacity()) {
            newAttributeDesc[i].set(attributeDescs[i])
            offset += newAttributeDesc[i].offset()
            logger.debug("location(${newAttributeDesc[i].location()})")
            logger.debug("    .offset(${newAttributeDesc[i].offset()})")
            position = i
        }

        position = 3
        offset = 0

        formatsAndAttributeSizes.zip(node.instancedProperties.toList().reversed()).forEach {
            val attribInfo = it.first
            val property = it.second

            for(i in (0 until attribInfo.elementCount)) {
                newAttributeDesc[position]
                    .binding(1)
                    .location(position)
                    .format(attribInfo.format)
                    .offset(offset)

                logger.debug("location($position, $i/${attribInfo.elementCount}) for ${property.first}, type: ${property.second.invoke().javaClass.simpleName}")
                logger.debug("   .format(${attribInfo.format})")
                logger.debug("   .offset($offset)")

                offset += attribInfo.elementByteSize
                position++
            }
        }

        logger.debug("stride($offset), ${bindingDescs.capacity()}")

        val newBindingDesc = VkVertexInputBindingDescription.calloc(bindingDescs.capacity() + 1)
        newBindingDesc[0].set(bindingDescs[0])
        newBindingDesc[1]
            .binding(1)
            .stride(offset)
            .inputRate(VK_VERTEX_INPUT_RATE_INSTANCE)

        val inputState = VkPipelineVertexInputStateCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
            .pNext(NULL)
            .pVertexAttributeDescriptions(newAttributeDesc)
            .pVertexBindingDescriptions(newBindingDesc)

        return VertexDescription(inputState, newAttributeDesc, newBindingDesc)
    }

    protected fun prepareDefaultTextures(device: VulkanDevice) {
        val t = VulkanTexture.loadFromFile(device, commandPools, queue, transferQueue,
            Renderer::class.java.getResourceAsStream("DefaultTexture.png"), "png", true, true)

        // TODO: Do an asset manager or sth here?
        defaultTextures["DefaultTexture"] = t
    }

    protected fun prepareStandardSemaphores(device: VulkanDevice): ConcurrentHashMap<StandardSemaphores, Array<Long>> {
        val map = ConcurrentHashMap<StandardSemaphores, Array<Long>>()

        StandardSemaphores.values().forEach {
            map[it] = swapchain.images.map {
                device.createSemaphore()
            }.toTypedArray()
        }

        return map
    }

    /**
     * Polls for window events and triggers swapchain recreation if necessary.
     * Returns true if the swapchain has been recreated, or false if not.
     */
    private fun pollEvents(): Boolean {
        window.pollEvents()

        (swapchain as? HeadlessSwapchain)?.queryResize()

        if (swapchainRecreator.mustRecreate) {
            swapchainRecreator.recreate()
            frames = 0

            return true
        }

        return false
    }

    private fun beginFrame(): Pair<Long, Long>? {
        previousFrame = currentFrame
        val semaphoreAndFence= swapchain.next(timeout = UINT64_MAX)
        if(semaphoreAndFence == null) {
            swapchainRecreator.mustRecreate = true
            return null
        }
        //logger.info("Prev: $previousFrame, Current: $currentFrame, will signal ${semaphores.getValue(StandardSemaphores.ImageAvailable)[previousFrame].toHexString()}")
        return semaphoreAndFence
    }

    var presentationFence = -1L

    @Suppress("unused")
    override fun recordMovie(filename: String, overwrite: Boolean) {
        if(recordMovie) {
            encoder?.finish()
            encoder = null

            recordMovie = false
            recordMovieOverwrite = overwrite
        } else {
            movieFilename = filename
            recordMovie = true
        }
    }

    private suspend fun submitFrame(
        queue: VulkanDevice.QueueWithMutex,
        pass: VulkanRenderpass,
        commandBuffer: VulkanCommandBuffer,
        present: PresentHelpers
    ) {
        if(swapchainRecreator.mustRecreate) {
            return
        }

        val stats = hub?.get(SceneryElement.Statistics) as? Statistics
        present.submitInfo
            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
            .pNext(NULL)
            .waitSemaphoreCount(present.waitSemaphore.limit())
            .pWaitSemaphores(present.waitSemaphore)
            .pWaitDstStageMask(present.waitStages)
            .pCommandBuffers(present.commandBuffers)
            .pSignalSemaphores(present.signalSemaphore)

        val q = (swapchain as? VulkanSwapchain)?.presentQueue ?: queue
        // Submit to the graphics queue
//        vkResetFences(device.vulkanDevice, swapchain.currentFence)
        VU.run("Submit viewport render queue", { vkQueueSubmit(q.queue, present.submitInfo, swapchain.currentFence) })

        // submit to OpenVR if attached
        if(hub?.getWorkingHMDDisplay()?.hasCompositor() == true) {
            hub?.getWorkingHMDDisplay()?.wantsVR(settings)?.submitToCompositorVulkan(
                window.width, window.height,
                swapchain.format,
                instance, device, queue,
                swapchain.images[pass.getReadPosition()])
        }

        val startPresent = System.nanoTime()
        commandBuffer.submitted = true
        swapchain.present(waitForSemaphores = present.signalSemaphore)

        vkWaitForFences(device.vulkanDevice, swapchain.currentFence, true, -1L)
        vkResetFences(device.vulkanDevice, swapchain.currentFence)
        presentationFence = swapchain.currentFence
        swapchain.postPresent(pass.getReadPosition())

        if(textureRequests.isNotEmpty()) {
            val request = try {
                logger.info("Polling requests")
                textureRequests.poll()
            } catch(e: NoSuchElementException) {
                null
            }

            request?.let { req ->
                logger.info("Working on texture request for texture ${req.first}")
                val buffer = req.first.contents ?: return@let
                val ref = VulkanTexture.getReference(req.first)

                if(ref != null) {
                    ref.copyTo(buffer, false)
                    req.second.send(req.first)
                    req.second.close()
                    logger.info("Sent updated texture")
                } else {
                    logger.info("Texture not accessible")
                }
            }
        }

        persistentTextureRequests.forEach { (texture, indicator) ->
            val ref = VulkanTexture.getReference(texture)
            val buffer = texture.contents ?: return@forEach

            if(ref != null) {
                val start = System.nanoTime()
                ref.copyTo(buffer)
                val end = System.nanoTime()
                logger.debug("The request textures of size ${texture.contents?.remaining()?.toFloat()?.div((1024f*1024f))} took: ${(end.toDouble()-start.toDouble())/1000000.0}")
                indicator.incrementAndGet()
            } else {
                logger.error("In persistent texture requests: Texture not accessible")
            }
        }

        if (recordMovie || screenshotRequested || imageRequests.isNotEmpty()) {
            val request = try {
                imageRequests.poll()
            } catch(e: NoSuchElementException) {
                null
            }

            // default image format is 32bit BGRA
            val imageByteSize = window.width * window.height * 4L
            if(screenshotBuffer == null || screenshotBuffer?.size != imageByteSize) {
                logger.debug("Reallocating screenshot buffer")
                screenshotBuffer = VulkanBuffer(device, imageByteSize,
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                    wantAligned = true,
                    name = "ScreenshotBuffer")
            }

            if(imageBuffer == null || imageBuffer?.capacity() != imageByteSize.toInt()) {
                logger.debug("Reallocating image buffer")
                imageBuffer = memAlloc(imageByteSize.toInt())
            }

            // finish encoding if a resize was performed
            if(recordMovie) {
                if (encoder != null && (encoder?.frameWidth != window.width || encoder?.frameHeight != window.height)) {
                    encoder?.finish()
                }

                if (encoder == null || encoder?.frameWidth != window.width || encoder?.frameHeight != window.height) {
                    val file = SystemHelpers.addFileCounter(if(movieFilename == "") {
                        File(System.getProperty("user.home"), "Desktop" + File.separator + "$applicationName - ${SystemHelpers.formatDateTime()}.mp4")
                    } else {
                        File(movieFilename)
                    }, recordMovieOverwrite)

                    encoder = VideoEncoder(
                        (window.width * settings.get<Float>("Renderer.SupersamplingFactor")).toInt(),
                        (window.height* settings.get<Float>("Renderer.SupersamplingFactor")).toInt(),
                        file.absolutePath,
                        hub = hub)
                }
            }

            screenshotBuffer?.let { sb ->
                with(VU.newCommandBuffer(device, commandPools.Render, autostart = true)) {
                    val subresource = VkImageSubresourceLayers.calloc()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(0)
                        .baseArrayLayer(0)
                        .layerCount(1)

                    val regions = VkBufferImageCopy.calloc(1)
                        .bufferRowLength(0)
                        .bufferImageHeight(0)
                        .imageOffset(VkOffset3D.calloc().set(0, 0, 0))
                        .imageExtent(VkExtent3D.calloc().set(window.width, window.height, 1))
                        .imageSubresource(subresource)

                    val image = swapchain.images[pass.getReadPosition()]

                    VulkanTexture.transitionLayout(image,
                        from = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                        to = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        srcStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                        srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                        dstStage = VK_PIPELINE_STAGE_HOST_BIT,
                        dstAccessMask = VK_ACCESS_HOST_READ_BIT,
                        commandBuffer = this)

                    vkCmdCopyImageToBuffer(this, image,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        sb.vulkanBuffer,
                        regions)

                    VulkanTexture.transitionLayout(image,
                        from = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        to = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                        srcStage = VK_PIPELINE_STAGE_HOST_BIT,
                        srcAccessMask = VK_ACCESS_HOST_READ_BIT,
                        dstStage = VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                        dstAccessMask = 0,
                        commandBuffer = this)

                    endCommandBuffer(this@VulkanRenderer.device, commandPools.Render, queue,
                        flush = true, dealloc = true)
                }

                if(screenshotRequested || request != null) {
                    sb.copyTo(imageBuffer!!)
                }

                if(recordMovie) {
                    encoder?.encodeFrame(sb.mapIfUnmapped().getByteBuffer(imageByteSize.toInt()))
                }

                if((screenshotRequested || request != null) && !recordMovie) {
                    sb.close()
                    screenshotBuffer = null
                }
            }

            if(screenshotRequested || request != null) {
                val writeToFile = screenshotRequested
                val overwrite = screenshotOverwriteExisting
                // reorder bytes for screenshot in a separate thread
                thread {
                    imageBuffer?.let { ib ->
                        try {
                            val file = SystemHelpers.addFileCounter(if(screenshotFilename == "") {
                                File(System.getProperty("user.home"), "Desktop" + File.separator + "$applicationName - ${SystemHelpers.formatDateTime()}.png")
                            } else {
                                File(screenshotFilename)
                            }, overwrite)
                            file.createNewFile()
                            ib.rewind()

                            val imageArray = ByteArray(ib.remaining())
                            ib.get(imageArray)
                            val shifted = ByteArray(imageArray.size)
                            ib.flip()

                            // swizzle BGRA -> ABGR
                            for (i in 0 until shifted.size step 4) {
                                shifted[i] = imageArray[i + 3]
                                shifted[i + 1] = imageArray[i]
                                shifted[i + 2] = imageArray[i + 1]
                                shifted[i + 3] = imageArray[i + 2]
                            }

                            val image = BufferedImage(window.width, window.height, BufferedImage.TYPE_4BYTE_ABGR)
                            val imgData = (image.raster.dataBuffer as DataBufferByte).data
                            System.arraycopy(shifted, 0, imgData, 0, shifted.size)

                            if(request != null && request is RenderedImage.RenderedRGBAImage) {
                                request.width = window.width
                                request.height = window.height
                                request.data = imgData
                            }

                            if(writeToFile) {
                                ImageIO.write(image, "png", file)
                                logger.info("Screenshot saved to ${file.absolutePath}")
                            }
                        } catch (e: Exception) {
                            logger.error("Unable to take screenshot: ")
                            e.printStackTrace()
                        } finally {
//                            memFree(ib)
                        }
                    }
                }

                screenshotOverwriteExisting = false
                screenshotRequested = false
            }
        }

        if(hub?.getWorkingHMDDisplay()?.hasCompositor() == true) {
            hub?.getWorkingHMDDisplay()?.wantsVR(settings)?.update()
        }

        runAfterRendering.forEach {
            it.invoke()
        }

        val presentDuration = System.nanoTime() - startPresent
        stats?.add("Renderer.viewportSubmitAndPresent", presentDuration)

        firstImageReady = true
    }

    private var currentFrame = 0
    private var previousFrame = 0
    private var currentNow = 0L

    /**
     * Enum class for reasons to re-record command buffers.
     */
    enum class RerecordingCause {
        GeometryUpdated,
        TexturesUpdated,
        MaterialUpdated,
        InstancesUpdated,
        SceneUpdated,
        SwapchainUpdated
    }

    /** Keeps track of the reasons why push mode has been defeated, and in which frame. */
    val pushModeDefeats: Queue<Pair<Int, List<Pair<String, RerecordingCause>>>> = LinkedList()

    /**
     * This function renders the scene
     */
    override fun render(activeCamera: Camera, sceneNodes: List<Node>) = runBlocking {
        val profiler = hub?.get<Profiler>()
//        profiler?.begin("Renderer.Housekeeping")
        val swapchainChanged = pollEvents()

        if(shouldClose) {
            closeInternal()
            return@runBlocking
        }

        val stats = hub?.get(SceneryElement.Statistics) as? Statistics

        // check whether scene is already initialized
        if (scene.children.count() == 0 || !scene.initialized) {
            initializeScene()

            delay(200)
            return@runBlocking
        }

        if (toggleFullscreen) {
            vkDeviceWaitIdle(device.vulkanDevice)

            switchFullscreen()
            toggleFullscreen = false
            return@runBlocking
        }

        if (window.shouldClose) {
            shouldClose = true
            // stop all
            vkDeviceWaitIdle(device.vulkanDevice)
            return@runBlocking
        }

        if (renderDelay > 0) {
            logger.warn("Delaying next frame for $renderDelay ms, as one or more validation error have occured in the previous frame.")
            delay(renderDelay)
        }
//        profiler?.end()

        profiler?.begin("Renderer.updateUBOs")
        val startUboUpdate = System.nanoTime()
        val ubosUpdated = updateDefaultUBOs(device, activeCamera)
        stats?.add("Renderer.updateUBOs", System.nanoTime() - startUboUpdate)
        profiler?.end()

        profiler?.begin("Renderer.updateInstanceBuffers")
        val startInstanceUpdate = System.nanoTime()
        val instancesUpdated = updateInstanceBuffers(sceneNodes)
        stats?.add("Renderer.updateInstanceBuffers", System.nanoTime() - startInstanceUpdate)
        profiler?.end()

        // flag set to true if command buffer re-recording is necessary,
        // e.g. because of scene or pipeline changes
        var forceRerecording = false
        val rerecordingCauses = ArrayList<Pair<String, RerecordingCause>>(20)

        if(instancesUpdated) {
            forceRerecording = true
            rerecordingCauses.add("General" to RerecordingCause.InstancesUpdated)
        }

        profiler?.begin("Renderer.PreDraw")
        // here we discover the objects in the scene that could be relevant for the scene
        var texturesUpdated: Boolean by StickyBoolean(false)

        if (renderpasses.filter { it.value.passConfig.type != RenderConfigReader.RenderpassType.quad }.any()) {
            sceneNodes.forEach { node ->
                val renderable = node.renderableOrNull() ?: return@forEach
                val material = node.materialOrNull() ?: return@forEach

                // if a node is not initialized yet, it'll be initialized here and it's UBO updated
                // in the next round
                if (renderable.rendererMetadata() == null || node.state == State.Created || renderable.rendererMetadata()?.initialized == false) {
                    logger.debug("${node.name} is not initialized, doing that now")
                    renderable.metadata["VulkanRenderer"] = VulkanRendererMetadata()
                    initializeNode(node)

                    return@forEach
                }

                if(!renderable.preDraw()) {
                    renderable.rendererMetadata()?.flags?.add(RendererFlags.PreDrawSkip)
                    return@forEach
                } else {
                    renderable.rendererMetadata()?.flags?.remove(RendererFlags.PreDrawSkip)
                }

                // the current command buffer will be forced to be re-recorded if either geometry, blending or
                // texturing of a given node have changed, as these might change pipelines or descriptor sets, leading
                // to the original command buffer becoming obsolete.
                renderable.rendererMetadata()?.let { metadata ->

                    node.ifBuffers {
                        // dirtySSBOs is set to true if the update function inside the buffers-attribute is used to update the buffers content, and set to false
                        // after the new content has been consumed to update the GPU sided buffer here
                        if(dirtySSBOs) {
                            logger.debug("Force command buffer re-recording, as SSBOs for {} has been updated", node.name)

                            renderable.preUpdate(this@VulkanRenderer, hub)
                            updateNodeSSBOs(node)
                            dirtySSBOs = false
                        }

                        if(downloadRequests.isNotEmpty()) {
                            logger.debug("Query buffer contents from marked downloadable buffers' device memory to host memory")
                            logger.info("Requests download")

                            downloadNodeSSBOs(node)
                            downloadRequests.clear()
                        }
                    }

                    node.ifGeometry {
                        if (dirty && !shaderSourced) {
                            logger.debug("Force command buffer re-recording, as geometry for {} has been updated", node.name)

                            renderable.preUpdate(this@VulkanRenderer, hub)
                            updateNodeGeometry(node)
                            dirty = false

                            rerecordingCauses.add(node.name to RerecordingCause.GeometryUpdated)
                            forceRerecording = true
                        }
                    }

                    // this covers cases where a master node is not given any instanced properties in the beginning
                    // but only later, or when instancing is removed at some point.
                    if((!metadata.flags.contains(RendererFlags.Instanced) && node is InstancedNode) ||
                        metadata.flags.contains(RendererFlags.Instanced) && node !is InstancedNode) {
                        metadata.flags.remove(RendererFlags.Initialised)
                        initializeNode(node)
                        return@forEach
                    }

                    val reloadTime = measureTimeMillis {
                        val (texturesUpdatedForNode, descriptorUpdated) = VulkanNodeHelpers.loadTexturesForNode(
                            device,
                            node,
                            metadata,
                            defaultTextures,
                            textureCache,
                            commandPools,
                            queue,
                            transferQueue
                        )
                        if(descriptorUpdated) {
                            metadata.texturesToDescriptorSets(device,
                                renderpasses.filter { it.value.passConfig.type != RenderConfigReader.RenderpassType.quad },
                                renderable)

                            logger.trace("Force command buffer re-recording, as reloading textures for ${node.name}")
                            rerecordingCauses.add(node.name to RerecordingCause.TexturesUpdated)
                            forceRerecording = true
                        }

                        texturesUpdated = texturesUpdatedForNode
                    }

                    if(texturesUpdated) {
                        logger.debug("Updating textures for {} took {}ms", node.name, reloadTime)
                    }

                    if (material.materialHashCode() != metadata.materialHashCode || (material is ShaderMaterial && material.shaders.stale)) {
                        val reloaded = VulkanNodeHelpers.initializeCustomShadersForNode(device, node, true, renderpasses, lateResizeInitializers, defaultBuffers)
                        logger.debug("{}: Material is stale, re-recording, reloaded={}", node.name, reloaded)
                        metadata.materialHashCode = material.materialHashCode()

                        // if we reloaded the node's shaders, we might need to recreate its texture descriptor sets
                        if(reloaded) {
                            renderable.rendererMetadata()?.texturesToDescriptorSets(device,
                                renderpasses.filter { pass -> pass.value.passConfig.type != RenderConfigReader.RenderpassType.quad },
                                renderable)
                        }

                        rerecordingCauses.add(node.name to RerecordingCause.MaterialUpdated)
                        forceRerecording = true

                        (material as? ShaderMaterial)?.shaders?.stale = false
                    }
                }
            }

            if(pushMode) {
                val newSceneArray = sceneNodes.toHashSet()
                if (!newSceneArray.equals(sceneArray)) {
                    forceRerecording = true
                    rerecordingCauses.add("General" to RerecordingCause.SceneUpdated)
                }

                sceneArray = newSceneArray
            }
        }
        profiler?.end()

        getDescriptorCache().forEachChanged(defaultBuffers.UBOs.updated) {
            if(it.value.updated < defaultBuffers.UBOs.updated) {
                logger.debug("Canceling current frame, UBO backing buffers updated.")

                renderpasses.forEach { (_, pass) ->
                    pass.invalidateCommandBuffers()
                }

                return@runBlocking
            }
        }

        if(renderpasses.any { it.value.shaders.stale }) {
            logger.info("Rebuilding swapchain due to stale shaders")
            swapchainRecreator.mustRecreate = true
        }

        if(swapchainChanged && pushMode) {
            rerecordingCauses.add("General" to RerecordingCause.SwapchainUpdated)
        }

        if(pushMode && rerecordingCauses.size > 0) {
            if(pushModeDefeats.size > 5) {
                pushModeDefeats.poll()
            }
            pushModeDefeats.add(frames to rerecordingCauses)
        }

        profiler?.begin("Renderer.BeginFrame")
        val presentedFrames = swapchain.presentedFrames()
        // return if neither UBOs were updated, nor the scene was modified
        if (pushMode && !swapchainChanged && !ubosUpdated && !forceRerecording && !screenshotRequested && !recordMovie && !texturesUpdated && totalFrames > 3 && presentedFrames > 3) {
            logger.trace("UBOs have not been updated, returning (pushMode={}, swapchainChanged={}, ubosUpdated={}, texturesUpdated={}, forceRerecording={}, screenshotRequested={})", pushMode, swapchainChanged, ubosUpdated, texturesUpdated, forceRerecording, totalFrames)
            delay(2)

            return@runBlocking
        }

        val submitInfo = VkSubmitInfo.calloc(flow.size-1)

        val (_, fence) = beginFrame() ?: return@runBlocking
        var waitSemaphore = -1L

        profiler?.end()

        flow.take(flow.size - 1).forEachIndexed { i, t ->
            val si = submitInfo[i]
            profiler?.begin("Renderer.$t")
            logger.trace("Running pass {}", t)
            val target = renderpasses[t]!!
            val commandBuffer = target.commandBuffer

            if (commandBuffer.submitted) {
                commandBuffer.waitForFence()
                commandBuffer.submitted = false
                commandBuffer.resetFence()

                stats?.add("Renderer.$t.gpuTiming", commandBuffer.runtime)
            }

            val start = System.nanoTime()

            when (target.passConfig.type) {
                RenderConfigReader.RenderpassType.geometry -> VulkanScenePass.record(hub!!, target, commandBuffer, commandPools, descriptorSets, renderConfig, renderpasses, sceneNodes, { it !is Light }, forceRerecording)
                RenderConfigReader.RenderpassType.lights -> VulkanScenePass.record(hub!!, target, commandBuffer, commandPools, descriptorSets, renderConfig, renderpasses, sceneNodes, { it is Light }, forceRerecording)
                RenderConfigReader.RenderpassType.quad -> VulkanPostprocessPass.record(target, commandBuffer, commandPools, sceneUBOs, descriptorSets)
                RenderConfigReader.RenderpassType.compute -> VulkanComputePass.record(target, commandBuffer, commandPools, sceneUBOs, descriptorSets)
            }


            stats?.add("VulkanRenderer.$t.recordCmdBuffer", System.nanoTime() - start)

            target.updateShaderParameters()

            val targetSemaphore = target.semaphore
            target.submitCommandBuffers.put(0, commandBuffer.commandBuffer!!)
            target.signalSemaphores.put(0, targetSemaphore)
            target.waitSemaphores.put(0, waitSemaphore)
            target.waitStages.put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT or VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)

            si.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pNext(NULL)
                .waitSemaphoreCount(0)
                .pWaitDstStageMask(target.waitStages)
                .pCommandBuffers(target.submitCommandBuffers)
                .pSignalSemaphores(target.signalSemaphores)

            if(waitSemaphore != -1L) {
                si
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(target.waitSemaphores)
            }

            if(swapchainRecreator.mustRecreate) {
                return@runBlocking
            }

//            logger.info("Submitting pass $t waiting on semaphore ${target.waitSemaphores.get(0).toHexString()}")
            VU.run("Submit pass $t render queue", { vkQueueSubmit(queue.queue, si, commandBuffer.getFence() )})

            commandBuffer.submitted = true
            waitSemaphore = targetSemaphore

            profiler?.end()
        }

        submitInfo.free()

        profiler?.begin("Renderer.${renderpasses.keys.last()}")
        val viewportPass = renderpasses.values.last()
        val viewportCommandBuffer = viewportPass.commandBuffer

        logger.trace("Running viewport pass {}", renderpasses.keys.last())

        val start = System.nanoTime()

        /*if(viewportCommandBuffer.submitted) {
            viewportCommandBuffer.waitForFence()
            viewportCommandBuffer.submitted = false
            viewportCommandBuffer.resetFence()

            stats?.add("Renderer.${viewportPass.name}.gpuTiming", viewportCommandBuffer.runtime)
        }*/

        when (viewportPass.passConfig.type) {
            RenderConfigReader.RenderpassType.geometry -> VulkanScenePass.record(hub!!, viewportPass, viewportCommandBuffer, commandPools, descriptorSets, renderConfig, renderpasses, sceneNodes, { it !is Light }, forceRerecording)
            RenderConfigReader.RenderpassType.lights -> VulkanScenePass.record(hub!!, viewportPass, viewportCommandBuffer, commandPools, descriptorSets, renderConfig, renderpasses, sceneNodes, { it is Light }, forceRerecording)
            RenderConfigReader.RenderpassType.quad -> VulkanPostprocessPass.record(viewportPass, viewportCommandBuffer, commandPools, sceneUBOs, descriptorSets)
            RenderConfigReader.RenderpassType.compute -> VulkanComputePass.record(viewportPass, viewportCommandBuffer, commandPools, sceneUBOs, descriptorSets)
        }

        stats?.add("VulkanRenderer.${viewportPass.name}.recordCmdBuffer", System.nanoTime() - start)

        viewportPass.updateShaderParameters()

        ph.commandBuffers.put(0, viewportCommandBuffer.commandBuffer!!)
        ph.signalSemaphore.put(0, semaphores.getValue(StandardSemaphores.RenderComplete)[currentFrame])

        // for single-pass render pipelines, waitSemaphore might not be valid, as
        // there was no previous pass.
        if(waitSemaphore == -1L) {
            ph.waitStages.limit(1)
            ph.waitSemaphore.limit(1)
            ph.waitStages.put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT or VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
            ph.waitSemaphore.put(0, swapchain.imageAvailableSemaphore)
        } else {
            ph.waitStages.limit(2)
            ph.waitSemaphore.limit(2)
            ph.waitStages.put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT or VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
            ph.waitSemaphore.put(0, waitSemaphore)
            ph.waitStages.put(1, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT or VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
            ph.waitSemaphore.put(1, swapchain.imageAvailableSemaphore)
        }
        profiler?.end()

        profiler?.begin("Renderer.SubmitFrame")
        vkWaitForFences(device.vulkanDevice, fence, true, -1L)
        vkResetFences(device.vulkanDevice, fence)

        submitFrame(queue, viewportPass, viewportCommandBuffer, ph)

        updateTimings()
        profiler?.end()

        currentNow = System.nanoTime()
    }

    private fun updateTimings() {
        val thisTime = System.nanoTime()
        val duration = thisTime - lastTime
        time += duration / 1E9f
        lastTime = thisTime

//        scene.activeObserver?.deltaT = duration / 10E6f

        frames++
        totalFrames++
    }

    private fun getSupportedExtensions(): List<String> {
        stackPush().use { stack ->
            val count = stack.ints(1)
            vkEnumerateInstanceExtensionProperties(null as? ByteBuffer, count, null)
            val exts = VkExtensionProperties.calloc(count.get(0), stack)
            vkEnumerateInstanceExtensionProperties(null as? ByteBuffer, count, exts)

            return exts.map { it.extensionNameString() }
        }
    }

    private fun createInstance(requiredExtensions: PointerBuffer? = null, enableValidations: Boolean = false, headless: Boolean = false): VkInstance {
        return stackPush().use { stack ->
            val supportedExtensions = getSupportedExtensions()

            val appInfo = VkApplicationInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8(applicationName))
                .pEngineName(stack.UTF8("scenery"))
                .apiVersion(VK_MAKE_VERSION(1, 1, 0))

            val additionalExts = ArrayList<String>()
            hub?.getWorkingHMDDisplay()?.getVulkanInstanceExtensions()?.forEach { additionalExts.add(it) }

            if(enableValidations) {
                additionalExts.add(VK_EXT_DEBUG_UTILS_EXTENSION_NAME)
            }

            if(supportedExtensions.contains("VK_KHR_portability_enumeration")
                && !settings.get("Renderer.DisablePortabilityEnumeration", false)
                && Platform.get() == Platform.MACOSX) {
                additionalExts.add("VK_KHR_portability_enumeration")
            }

            val utf8Exts = additionalExts.map { stack.UTF8(it) }

            logger.debug("HMD required instance exts: ${additionalExts.joinToString(", ")} ${additionalExts.size}")

            // allocate enough pointers for already pre-required extensions, plus HMD-required extensions, plus the debug extension
            val size = requiredExtensions?.remaining() ?: 0

            val enabledExtensionNames = if(!headless) {
                val buffer = stack.callocPointer(size + additionalExts.size + 2)
                val platformSurfaceExtension = when {
                    Platform.get() === Platform.WINDOWS -> stack.UTF8(VK_KHR_WIN32_SURFACE_EXTENSION_NAME)
                    Platform.get() === Platform.LINUX -> stack.UTF8(VK_KHR_XLIB_SURFACE_EXTENSION_NAME)
                    Platform.get() === Platform.MACOSX -> stack.UTF8(VK_MVK_MACOS_SURFACE_EXTENSION_NAME)
                    else -> throw RendererUnavailableException("Vulkan is not supported on ${Platform.get()}")
                }

                buffer.put(platformSurfaceExtension)
                buffer.put(stack.UTF8(VK_KHR_SURFACE_EXTENSION_NAME))
                buffer
            } else {
                stack.callocPointer(size + additionalExts.size)
            }

            requiredExtensions?.let { exts -> enabledExtensionNames.put(exts) }

            utf8Exts.forEach { enabledExtensionNames.put(it) }
            enabledExtensionNames.flip()

            val enabledLayerNames = if(!wantsOpenGLSwapchain && validation) {
                val pointers = stack.callocPointer(defaultValidationLayers.size)
                defaultValidationLayers.forEach { pointers.put(stack.UTF8(it)) }
                pointers
            } else {
                stack.callocPointer(0)
            }

            enabledLayerNames.flip()

            val createInfo = VkInstanceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pNext(NULL)
                .pApplicationInfo(appInfo)
                .ppEnabledExtensionNames(enabledExtensionNames)
                .ppEnabledLayerNames(enabledLayerNames)

            if(supportedExtensions.contains("VK_KHR_portability_enumeration")
                && !settings.get("Renderer.DisablePortabilityEnumeration", false)
                && Platform.get() == Platform.MACOSX) {
                createInfo
                    .flags(0x00000001) // VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
            }

            val extensions = (0 until enabledExtensionNames.remaining()).map {
                memUTF8(enabledExtensionNames.get(it))
            }
            val layers = (0 until enabledLayerNames.remaining()).map {
                memUTF8(enabledLayerNames.get(it))
            }

            logger.info("Creating Vulkan instance with extensions ${extensions.joinToString(",")} and layers ${layers.joinToString(",")}")

            val instance = VU.getPointer("Creating Vulkan instance",
                { vkCreateInstance(createInfo, null, this) }, {})

            VkInstance(instance, createInfo)
        }
    }

    @Suppress("SameParameterValue")
    private fun setupDebuggingDebugUtils(instance: VkInstance, severity: Int, callback: (Int, Int, Long, Long) -> Int): Long {
        val messengerCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc()
            .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
            .pfnUserCallback(callback)
            .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
                or VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                or VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
            .messageSeverity(severity)
            .flags(0)

        return try {
            val messenger = VU.getLong("Create debug messenger", { vkCreateDebugUtilsMessengerEXT(instance,
                messengerCreateInfo,
                null,
                this
            )}, {})

           messenger
        } catch(e: NullPointerException) {
            logger.warn("Caught NPE on creating debug callback, is extension $VK_EXT_DEBUG_UTILS_EXTENSION_NAME available?")
            -1
        }
    }


    @Suppress("SameParameterValue", "unused")
    private fun setupDebuggingDebugReport(instance: VkInstance, flags: Int, callback: VkDebugReportCallbackEXT): Long {
        val dbgCreateInfo = VkDebugReportCallbackCreateInfoEXT.calloc()
            .sType(VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT)
            .pNext(NULL)
            .pfnCallback(callback)
            .pUserData(NULL)
            .flags(flags)

        val pCallback = memAllocLong(1)

        return try {
            val err = vkCreateDebugReportCallbackEXT(instance, dbgCreateInfo, null, pCallback)
            val callbackHandle = pCallback.get(0)
            memFree(pCallback)
            dbgCreateInfo.free()
            if (err != VK_SUCCESS) {
                throw RuntimeException("Failed to create VkInstance with debugging enabled: " + VU.translate(err))
            }

            callbackHandle
        } catch(e: NullPointerException) {
            logger.warn("Caught NPE on creating debug callback, is extension ${VK_EXT_DEBUG_REPORT_EXTENSION_NAME} available?")
            -1
        }
    }

    private fun prepareDefaultBuffers(device: VulkanDevice): DefaultBuffers {
        logger.debug("Creating buffers")
        return DefaultBuffers(
            UBOs = VulkanBuffer(device,
                5 * 1024 * 1024,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                wantAligned = true,
                name = "DefaultBuffer_UBO"),

            LightParameters = VulkanBuffer(device,
                5 * 1024 * 1024,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                wantAligned = true,
                name = "DefaultBuffer_Light"),

            VRParameters = VulkanBuffer(device,
                256 * 10,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                wantAligned = true,
                name = "DefaultBuffer_VR"),

            ShaderProperties = VulkanBuffer(device,
                4 * 1024 * 1024,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                wantAligned = true,
                name = "DefaultBuffer_ShaderProps"))
    }

    private fun Renderable.rendererMetadata(): VulkanRendererMetadata? {
        return this.metadata["VulkanRenderer"] as? VulkanRendererMetadata
    }

    private fun updateInstanceBuffers(sceneObjects: List<Node>) = runBlocking {
        val instanceMasters = sceneObjects.filterIsInstance<InstancedNode>()

        instanceMasters.forEach { parent ->
            val metadata = parent.renderableOrNull()?.rendererMetadata()

            if(metadata != null && metadata.initialized) {
                VulkanNodeHelpers.updateInstanceBuffer(device, parent, metadata, commandPools, queue)
            }
        }

        instanceMasters.isNotEmpty()
    }


    private fun getDescriptorCache(): TimestampedConcurrentHashMap<String, SimpleTimestamped<Long>> {
        @Suppress("UNCHECKED_CAST")
        return scene.metadata.getOrPut("DescriptorCache") {
            TimestampedConcurrentHashMap<String, SimpleTimestamped<Long>>()
        } as? TimestampedConcurrentHashMap<String, SimpleTimestamped<Long>> ?: throw IllegalStateException("Could not retrieve descriptor cache from scene")
    }

    private fun updateDefaultUBOs(device: VulkanDevice, cam: Camera): Boolean = runBlocking {
        if(shouldClose) {
            return@runBlocking false
        }

        logger.trace("Updating default UBOs for {}", device)
        // sticky boolean
        var updated: Boolean by StickyBoolean(initial = false)

        if (!cam.lock.tryLock()) {
            return@runBlocking false
        }

        val hmd = hub?.getWorkingHMDDisplay()?.wantsVR(settings)

        val now = System.nanoTime()
        getDescriptorCache().forEachChanged(now = defaultBuffers.UBOs.updated) {
            if(it.value.updated < defaultBuffers.UBOs.updated) {
                logger.debug("Updating descriptor set for ${it.key} as the backing buffer has changed")
                VU.updateDynamicDescriptorSetBuffer(device, it.value.contents, 1, defaultBuffers.UBOs)
                it.value.updated = now
            }
        }

        val camSpatial = cam.spatial()
        camSpatial.view = camSpatial.getTransformation()
//        cam.updateWorld(true, false)

        defaultBuffers.VRParameters.reset()
        val vrUbo = defaultUBOs["VRParameters"]!!
        vrUbo.add("projection0", {
            (hmd?.getEyeProjection(0, cam.nearPlaneDistance, cam.farPlaneDistance)
                ?: camSpatial.projection).applyVulkanCoordinateSystem()
        })
        vrUbo.add("projection1", {
            (hmd?.getEyeProjection(1, cam.nearPlaneDistance, cam.farPlaneDistance)
                ?: camSpatial.projection).applyVulkanCoordinateSystem()
        })
        vrUbo.add("inverseProjection0", {
            (hmd?.getEyeProjection(0, cam.nearPlaneDistance, cam.farPlaneDistance)
                ?: camSpatial.projection).applyVulkanCoordinateSystem().invert()
        })
        vrUbo.add("inverseProjection1", {
            (hmd?.getEyeProjection(1, cam.nearPlaneDistance, cam.farPlaneDistance)
                ?: camSpatial.projection).applyVulkanCoordinateSystem().invert()
        })
        vrUbo.add("headShift", { hmd?.getHeadToEyeTransform(0) ?: Matrix4f().identity() })
        vrUbo.add("IPD", { hmd?.getIPD() ?: 0.05f })
        vrUbo.add("stereoEnabled", { renderConfig.stereoEnabled.toInt() })

        updated = vrUbo.populate()

        defaultBuffers.UBOs.reset()
        defaultBuffers.ShaderProperties.reset()

        sceneUBOs.forEach { node ->
            val renderable = node.renderableOrNull() ?: return@forEach
            val spatial = node.spatialOrNull()
            node.lock.withLock {
                var nodeUpdated: Boolean by StickyBoolean(initial = false)

                if (!renderable.metadata.containsKey("VulkanRenderer")) {
                    return@forEach
                }

                val s = renderable.rendererMetadata() ?: return@forEach

                val ubo = s.UBOs["Matrices"]!!.second

                ubo.offsets.limit(1)

                var bufferOffset = ubo.backingBuffer!!.advance()
                ubo.offsets.put(0, bufferOffset)
                ubo.offsets.limit(1)

//                spatial.projection.copyFrom(cam.projection.applyVulkanCoordinateSystem())

                spatial?.view?.set(camSpatial.view)

                nodeUpdated = ubo.populate(offset = bufferOffset.toLong())

                val materialUbo = s.UBOs["MaterialProperties"]!!.second
                bufferOffset = ubo.backingBuffer!!.advance()
                materialUbo.offsets.put(0, bufferOffset)
                materialUbo.offsets.limit(1)

                nodeUpdated = materialUbo.populate(offset = bufferOffset.toLong())

                s.UBOs.filter { it.key.contains("ShaderProperties") && it.value.second.memberCount() > 0 }.forEach {
//                if(s.requiredDescriptorSets.keys.any { it.contains("ShaderProperties") }) {
                    val propertyUbo = it.value.second
                    val offset = propertyUbo.backingBuffer!!.advance()
                    nodeUpdated = propertyUbo.populate(offset = offset.toLong())
                    propertyUbo.offsets.put(0, offset)
                    propertyUbo.offsets.limit(1)
                }

                if(nodeUpdated && node.getScene()?.onNodePropertiesChanged?.isNotEmpty() == true) {
                    GlobalScope.launch { node.getScene()?.onNodePropertiesChanged?.forEach { it.value.invoke(node) } }
                }

                updated = nodeUpdated
                s.flags.add(RendererFlags.Updated)
            }
        }

        defaultBuffers.UBOs.copyFromStagingBuffer()

        val lightUbo = defaultUBOs["LightParameters"]!!
        lightUbo.add("ViewMatrix0", { camSpatial.getTransformationForEye(0) })
        lightUbo.add("ViewMatrix1", { camSpatial.getTransformationForEye(1) })
        lightUbo.add("InverseViewMatrix0", { camSpatial.getTransformationForEye(0).invert() })
        lightUbo.add("InverseViewMatrix1", { camSpatial.getTransformationForEye(1).invert() })
        lightUbo.add("ProjectionMatrix", { camSpatial.projection.applyVulkanCoordinateSystem() })
        lightUbo.add("InverseProjectionMatrix", { camSpatial.projection.applyVulkanCoordinateSystem().invert() })
        lightUbo.add("CamPosition", { camSpatial.position })

        updated = lightUbo.populate()

        defaultBuffers.ShaderProperties.copyFromStagingBuffer()

//        updateDescriptorSets()

        cam.lock.unlock()

        return@runBlocking updated
    }

    @Suppress("UNUSED")
    override fun screenshot(filename: String, overwrite: Boolean) {
        screenshotRequested = true
        screenshotOverwriteExisting = overwrite
        screenshotFilename = filename
    }

    fun Int.toggle(): Int {
        if (this == 0) {
            return 1
        } else if (this == 1) {
            return 0
        }

        logger.warn("Property is not togglable.")
        return this
    }

    @Suppress("UNUSED")
    fun toggleDebug() {
        settings.getAllSettings().forEach {
            if (it.lowercase().contains("debug")) {
                try {
                    val property = settings.get<Int>(it)
                    val range = settings.get<Int>(it + "Range", 9)
                    val new = (property+1) % range
                    settings.set(it, new)
                    logger.info("$it: $property -> $new")

                } catch(e: Exception) {
                    logger.warn("$it is a property that is not togglable.")
                }
            }
        }
    }

    /**
     * Closes the current instance of [VulkanRenderer].
     */
    override fun close() {
        shouldClose = true
    }

    fun closeInternal() {
        if(!initialized) {
            return
        }

        initialized = false

        logger.info("Renderer teardown started.")
        vkQueueWaitIdle(queue.queue)

        logger.debug("Closing nodes...")
        scene.discover(scene, { true }).forEach {
            destroyNode(it, onShutdown = true)
        }

        hub?.elements?.values?.forEach { elem ->
            (elem as? HasCustomRenderable<*>)?.close()
            (elem as? Node)?.let { destroyNode(it, onShutdown = true) }
        }

        scene.metadata.remove("DescriptorCache")
        scene.initialized = false

        logger.debug("Cleaning texture cache...")
        textureCache.forEach {
            logger.debug("Cleaning {}...", it.key)
            it.value.close()
        }

        logger.debug("Closing buffers...")
        defaultBuffers.LightParameters.close()
        defaultBuffers.ShaderProperties.close()
        defaultBuffers.UBOs.close()
        defaultBuffers.VRParameters.close()

        logger.debug("Closing default UBOs...")
        defaultUBOs.forEach { ubo ->
            ubo.value.close()
        }

        logger.debug("Closing memory pools ...")
        geometryPool.close()
        stagingPool.close()

        logger.debug("Closing vertex descriptors ...")
        vertexDescriptors.forEach {
            logger.debug("Closing vertex descriptor {}...", it.key)

            it.value.attributeDescription?.free()
            it.value.bindingDescription?.free()

            it.value.state.free()
        }

        logger.debug("Closing descriptor sets and pools...")
//        descriptorSetLayouts.forEach { vkDestroyDescriptorSetLayout(device.vulkanDevice, it.value, null) }

        logger.debug("Closing command buffers...")
        ph.commandBuffers.free()
        memFree(ph.signalSemaphore)
        memFree(ph.waitSemaphore)
        memFree(ph.waitStages)

        semaphores.forEach { it.value.forEach { semaphore -> device.removeSemaphore(semaphore) }}

        logger.debug("Closing swapchain...")

        swapchain.close()

        logger.debug("Closing renderpasses...")
        renderpasses.forEach { _, vulkanRenderpass -> vulkanRenderpass.close() }

        logger.debug("Clearing shader module cache...")
        VulkanShaderModule.clearCache()

        logger.debug("Closing command pools...")
        with(commandPools) {
            device.destroyCommandPool(Render)
            device.destroyCommandPool(Compute)
            device.destroyCommandPool(Standard)
            device.destroyCommandPool(Transfer)
        }

        VulkanRenderpass.destroyPipelineCache(device)

        if (validation && debugCallbackHandle != -1L) {
            vkDestroyDebugUtilsMessengerEXT(instance, debugCallbackHandle, null)
        }

        debugCallback.free()

        logger.debug("Closing device {}...", device)
        device.close()

        if(System.getProperty("scenery.Workarounds.DontCloseVulkanInstances")?.toBoolean() == true) {
            logger.warn("Not closing Vulkan instances explicitly requested as workaround for Nvidia driver issue.")
        } else {
            logger.debug("Closing instance...")
            vkDestroyInstance(instance, null)
        }

        heartbeatTimer.cancel()
        heartbeatTimer.purge()
        logger.info("Renderer teardown complete.")
    }

    override fun reshape(newWidth: Int, newHeight: Int) {

    }

    @Suppress("UNUSED")
    fun toggleFullscreen() {
        toggleFullscreen = !toggleFullscreen
    }

    fun switchFullscreen() {
        hub?.let { hub -> swapchain.toggleFullscreen(hub, swapchainRecreator) }
    }

    /**
     * Sets the rendering quality, if the loaded renderer config file supports it.
     *
     * @param[quality] The [RenderConfigReader.RenderingQuality] to be set.
     */
    override fun setRenderingQuality(quality: RenderConfigReader.RenderingQuality) {
        fun setConfigSetting(key: String, value: Any) {
            val setting = "Renderer.$key"

            logger.debug("Setting {}: {} -> {}", setting, settings.getOrNull<Any>(setting), value)
            settings.set(setting, value)
        }

        if(renderConfig.qualitySettings.isNotEmpty()) {
            logger.info("Setting rendering quality to $quality")

            renderConfig.qualitySettings[quality]?.forEach { setting ->
                if(setting.key.endsWith(".shaders") && setting.value is List<*>) {
                    val pass = setting.key.substringBeforeLast(".shaders")
                    @Suppress("UNCHECKED_CAST") val shaders = setting.value as? List<String> ?: return@forEach

                    renderConfig.renderpasses[pass]?.shaders = shaders

                    @Suppress("SENSELESS_COMPARISON")
                    if(swapchainRecreator != null) {
                        swapchainRecreator.mustRecreate = true
                        swapchainRecreator.afterRecreateHook = { swapchainRecreator ->
                            renderConfig.qualitySettings[quality]?.filter { !it.key.endsWith(".shaders") }?.forEach {
                                setConfigSetting(it.key, it.value)
                            }

                            swapchainRecreator.afterRecreateHook = {}
                        }
                    }
                } else {
                    setConfigSetting(setting.key, setting.value)
                }
            }
        } else {
            logger.warn("The current renderer config, $renderConfigFile, does not support setting quality options.")
        }
    }
}
