package graphics.scenery.backends.vulkan

import cleargl.GLMatrix
import cleargl.GLVector
import glm_.L
import glm_.i
import graphics.scenery.*
import graphics.scenery.backends.*
import graphics.scenery.spirvcrossj.Loader
import graphics.scenery.spirvcrossj.libspirvcrossj
import graphics.scenery.utils.*
import kool.ByteBuffer
import kool.cap
import kool.free
import kool.lib.fill
import kool.set
import kotlinx.coroutines.*
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack.stackGet
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugReport.VK_EXT_DEBUG_REPORT_EXTENSION_NAME
import org.lwjgl.vulkan.VK10.VK_FALSE
import org.lwjgl.vulkan.VK10.VK_MAKE_VERSION
import uno.glfw.glfw
import vkk.*
import vkk.entities.*
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import javax.imageio.ImageIO
import kotlin.collections.ArrayList
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties


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

@Suppress("MemberVisibilityCanBePrivate")
open class VulkanRenderer(hub: Hub,
                          applicationName: String,
                          scene: Scene,
                          windowWidth: Int,
                          windowHeight: Int,
                          final override var embedIn: SceneryPanel? = null,
                          renderConfigFile: String) : Renderer(), AutoCloseable {

    protected val logger by LazyLogger()

    // helper classes
    data class PresentHelpers(
        var signalSemaphore: VkSemaphore = VkSemaphore(NULL),
        var waitSemaphore: VkSemaphore = VkSemaphore(NULL),
        var commandBuffers: PointerBuffer = memAllocPointer(1), // TODO check if really single
        var waitStages: IntBuffer = memAllocInt(1),
        var submitInfo: VkSubmitInfo = VkSubmitInfo()
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
        var Standard: VkCommandPool = VkCommandPool(NULL),
        var Render: VkCommandPool = VkCommandPool(NULL),
        var Compute: VkCommandPool = VkCommandPool(NULL),
        var Transfer: VkCommandPool = VkCommandPool(NULL)
    ) {
        constructor(commandPool: VkCommandPool) : this(commandPool, commandPool, commandPool, commandPool)
    }

    data class DeviceAndGraphicsQueueFamily(
        val device: VkDevice? = null,
        val graphicsQueue: Int = 0,
        val computeQueue: Int = 0,
        val presentQueue: Int = 0,
        val transferQueue: Int = 0,
        val memoryProperties: VkPhysicalDeviceMemoryProperties? = null
    )


    class Pipeline(var pipeline: VkPipeline = VkPipeline(NULL), var layout: VkPipelineLayout = VkPipelineLayout(NULL))

    sealed class DescriptorSet(val id: VkDescriptorSet = VkDescriptorSet(NULL), val name: String = "") {
        object None : DescriptorSet()
        data class Set(val setId: VkDescriptorSet, val setName: String = "") : DescriptorSet(setId, setName)
        data class DynamicSet(val setId: VkDescriptorSet, val offset: Int, val setName: String = "") : DescriptorSet(setId, setName)

        companion object {
            fun setOrNull(id: VkDescriptorSet?, setName: String): DescriptorSet? {
                return id?.let { DescriptorSet.Set(id, setName) }
            }
        }
    }

    private val lateResizeInitializers = ConcurrentHashMap<Node, () -> Any>()

    inner class SwapchainRecreator {
        var mustRecreate = true
        var afterRecreateHook: (SwapchainRecreator) -> Unit = {}

        private val lock = ReentrantLock()

        @Synchronized
        fun recreate() {
            if (lock.tryLock()) {
                logger.info("Recreating Swapchain at frame $frames")
                // create new swapchain with changed surface parameters
                queue.waitIdle()

                vkDev.newCommandBuffer(commandPools.Standard)
                    .record { swapchain.create(oldSwapchain = swapchain) }
                    .submit(queue)
                    .deallocate()

                if (pipelineCache.L != NULL)
                    vkDev destroyPipelineCache pipelineCache

                pipelineCache = vkDev createPipelineCache vk.PipelineCacheCreateInfo()

                renderpasses.values.forEach { it.close() }
                renderpasses.clear()

                settings.set("Renderer.displayWidth", (window.width * settings.get<Float>("Renderer.SupersamplingFactor")).toInt())
                settings.set("Renderer.displayHeight", (window.height * settings.get<Float>("Renderer.SupersamplingFactor")).toInt())

                prepareRenderpassesFromConfig(renderConfig, window.width, window.height)

                semaphores.values.forEach { semaphores -> vkDev destroySemaphores semaphores }
                semaphores = prepareStandardSemaphores()

                // Create render command buffers
                vkDev resetCommandPool commandPools.Render

                scene.findObserver()?.let { cam ->
                    cam.perspectiveCamera(cam.fov, window.width.toFloat(), window.height.toFloat(), cam.nearPlaneDistance, cam.farPlaneDistance)
                }

                logger.debug("Calling late resize initializers for ${lateResizeInitializers.keys.joinToString()}")
                lateResizeInitializers.map { it.value.invoke() }

                if (timestampQueryPool.isValid) {
                    vkDev destroyQueryPool timestampQueryPool
                }

                val queryPoolCreateInfo = vk.QueryPoolCreateInfo {
                    queryType = VkQueryType.TIMESTAMP
                    queryCount = renderConfig.renderpasses.size * 2
                }
                timestampQueryPool = vkDev createQueryPool queryPoolCreateInfo

                totalFrames = 0
                mustRecreate = false

                afterRecreateHook.invoke(this)

                lock.unlock()
            }
        }
    }

    /** Debug callback to be used upon encountering validation messages or errors */
    var debugCallback = object : VkDebugReportCallbackEXT() {

        override operator fun invoke(flags: Int, objectType: Int, obj: Long, location: Long, messageCode: Int, pLayerPrefix: Long, pMessage: Long, pUserData: Long): Int {
            val dbg = if (flags and VkDebugReport.DEBUG_BIT_EXT == VkDebugReport.DEBUG_BIT_EXT.i) {
                " (debug)"
            } else {
                ""
            }

            val message = when {
                flags and VkDebugReport.ERROR_BIT_EXT == VkDebugReport.ERROR_BIT_EXT.i ->
                    "$dbg: ${getString(pMessage)}"
                flags and VkDebugReport.WARNING_BIT_EXT == VkDebugReport.WARNING_BIT_EXT.i ->
                    "$dbg: ${getString(pMessage)}"
                flags and VkDebugReport.PERFORMANCE_WARNING_BIT_EXT == VkDebugReport.PERFORMANCE_WARNING_BIT_EXT.i ->
                    " (performance)$dbg: ${getString(pMessage)}"
                flags and VkDebugReport.INFORMATION_BIT_EXT == VkDebugReport.INFORMATION_BIT_EXT.i ->
                    "$dbg: ${getString(pMessage)}"
                else ->
                    " (unknown message type)$dbg: ${getString(pMessage)}"
            }
            logger.info("!! $obj($objectType) Validation$message")

            // trigger exception and delay if strictValidation is activated in general, or only for specific object types
            if (strictValidation.first && strictValidation.second.isEmpty() ||
                strictValidation.first && strictValidation.second.contains(objectType)) {
                // set 15s of delay until the next frame is rendered if a validation error happens
                renderDelay = 1500L

                try {
                    throw Exception("Vulkan validation layer exception, see validation layer error messages above. To disable these exceptions, set scenery.VulkanRenderer.StrictValidation=false. Stack trace:")
                } catch (e: Exception) {
                    logger.error(e.message)
                    e.printStackTrace()
                }
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
        protected set

    private var screenshotRequested = false
    private var screenshotOverwriteExisting = false
    private var screenshotFilename = ""
    var screenshotBuffer: VulkanBuffer? = null
    var imageBuffer: ByteBuffer? = null
    var encoder: H264Encoder? = null
    var recordMovie: Boolean = false
    override var pushMode: Boolean = false

    private var firstWaitSemaphore: LongBuffer = memAllocLong(1)

    var scene: Scene = Scene()
    protected var sceneArray: Array<Node> = emptyArray()

    protected var commandPools = CommandPools()
    protected val renderpasses: MutableMap<String, VulkanRenderpass> = Collections.synchronizedMap(LinkedHashMap<String, VulkanRenderpass>())

    protected var validation = java.lang.Boolean.parseBoolean(System.getProperty("scenery.VulkanRenderer.EnableValidations", "true"))
    protected val strictValidation = getStrictValidation()
    protected val wantsOpenGLSwapchain = java.lang.Boolean.parseBoolean(System.getProperty("scenery.VulkanRenderer.UseOpenGLSwapchain", "false"))
    protected val defaultValidationLayers = arrayListOf("VK_LAYER_LUNARG_standard_validation")

    protected var instance: VkInstance
    protected var device: VulkanDevice

    protected var debugCallbackHandle = VkDebugReportCallback(NULL)
    protected var timestampQueryPool = VkQueryPool(NULL)

    protected var semaphoreCreateInfo: VkSemaphoreCreateInfo

    // Create static Vulkan resources
    protected var queue: VkQueue
    protected var transferQueue: VkQueue
    protected var descriptorPool = VkDescriptorPool(NULL)

    protected var swapchain: Swapchain
    protected var ph = PresentHelpers()

    final override var window: SceneryWindow = SceneryWindow.UninitializedWindow()

    protected val swapchainRecreator: SwapchainRecreator
    protected var pipelineCache = VkPipelineCache(NULL)
    protected var vertexDescriptors = ConcurrentHashMap<VertexDataKinds, VertexDescription>()
    protected var sceneUBOs = ArrayList<Node>()
    protected var geometryPool: VulkanBufferPool
    protected var semaphores = ConcurrentHashMap<StandardSemaphores, VkSemaphoreArray>()
    protected var buffers = ConcurrentHashMap<String, VulkanBuffer>()
    protected var defaultUBOs = ConcurrentHashMap<String, VulkanUBO>()
    protected var textureCache = ConcurrentHashMap<String, VulkanTexture>()
    protected var descriptorSetLayouts = ConcurrentHashMap<String, VkDescriptorSetLayout>()
    protected var descriptorSets = ConcurrentHashMap<String, VkDescriptorSet>()

    protected var lastTime = System.nanoTime()
    protected var time = 0.0f
    var fps = 0
        protected set
    protected var frames = 0
    protected var totalFrames = 0L
    protected var renderDelay = 0L
    protected var heartbeatTimer = Timer()
    protected var gpuStats: GPUStats? = null

    private var renderConfig: RenderConfigReader.RenderConfig
    private var flow: List<String> = listOf()

    private val vulkanProjectionFix =
        GLMatrix(floatArrayOf(
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, -1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.5f, 0.0f,
            0.0f, 0.0f, 0.5f, 1.0f))

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
        private const val MAX_TEXTURES = 2048 * 16
        private const val MAX_UBOS = 2048
        private const val MAX_INPUT_ATTACHMENTS = 32
        private const val UINT64_MAX: Long = -1L

        private const val MATERIAL_HAS_DIFFUSE = 0x0001
        private const val MATERIAL_HAS_AMBIENT = 0x0002
        private const val MATERIAL_HAS_SPECULAR = 0x0004
        private const val MATERIAL_HAS_NORMAL = 0x0008
        private const val MATERIAL_HAS_ALPHAMASK = 0x0010

        init {
            Loader.loadNatives()
            libspirvcrossj.initializeProcess()

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    logger.debug("Finalizing libspirvcrossj")
                    libspirvcrossj.finalizeProcess()
                }
            })
        }

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
    }

    val vkDev get() = device.vulkanDevice

    init {
        this.hub = hub

        val hmd = hub.getWorkingHMDDisplay()
        if (hmd != null) {
            logger.debug("Setting window dimensions to bounds from HMD")
            val bounds = hmd.getRenderTargetSize()
            window.width = bounds.x().toInt() * 2
            window.height = bounds.y().toInt()
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

        if ((System.getenv("ENABLE_VULKAN_RENDERDOC_CAPTURE")?.toInt() == 1 || Renderdoc.renderdocAttached) && validation) {
            logger.warn("Validation Layers requested, but Renderdoc capture and Validation Layers are mutually incompatible. Disabling validations layers.")
            validation = false
        }

        // explicitly create VK, to make GLFW pick up MoltenVK on OS X
        if (ExtractsNatives.getPlatform() == ExtractsNatives.Platform.MACOS) {
            try {
                Configuration.VULKAN_EXPLICIT_INIT.set(true)
                VK.create()
            } catch (e: IllegalStateException) {
                logger.warn("IllegalStateException during Vulkan initialisation")
            }
        }


        // Create the Vulkan instance
        instance = if (embedIn != null) {
            createInstance()
        } else {
            if (!glfwInit()) {
                throw RuntimeException("Failed to initialize GLFW")
            }
            if (!glfwVulkanSupported()) {
                throw UnsupportedOperationException("Failed to find Vulkan loader. Is Vulkan supported by your GPU and do you have the most recent graphics drivers installed?")
            }

            /* Look for instance extensions */
            createInstance(glfw.requiredInstanceExtensions)
        }

        if (validation)
            debugCallbackHandle = setupDebugging(instance, VkDebugReport.ERROR_BIT_EXT or VkDebugReport.WARNING_BIT_EXT, debugCallback)

        val requestedValidationLayers = arrayListOf<String>()
        if (validation)
            if (wantsOpenGLSwapchain)
                logger.warn("Requested OpenGL swapchain, validation layers disabled.")
            else
                requestedValidationLayers += defaultValidationLayers

        device = VulkanDevice.fromPhysicalDevice(instance,
            physicalDeviceFilter = { _, device -> device.name.contains(System.getProperty("scenery.Renderer.Device", "DOES_NOT_EXIST")) },
            additionalExtensions = { physicalDevice ->
                hub.getWorkingHMDDisplay()?.getVulkanDeviceExtensions(physicalDevice) ?: arrayListOf()
            },
            validationLayers = requestedValidationLayers,
            headless = embedIn != null)

        logger.debug("Device creation done")

        if (device.deviceData.vendor.toLowerCase().contains("nvidia") && ExtractsNatives.getPlatform() == ExtractsNatives.Platform.WINDOWS) {
            gpuStats = NvidiaGPUStats()
        }

        queue = vkDev getQueue device.queueIndices.graphicsQueue
        logger.debug("Creating transfer queue with ${device.queueIndices.transferQueue} (vs ${device.queueIndices.graphicsQueue})")
        transferQueue = vkDev getQueue device.queueIndices.transferQueue

        with(commandPools) {
            Render = device.createCommandPool(device.queueIndices.graphicsQueue)
            Standard = device.createCommandPool(device.queueIndices.graphicsQueue)
            Compute = device.createCommandPool(device.queueIndices.computeQueue)
            Transfer = device.createCommandPool(device.queueIndices.transferQueue)
        }
        logger.debug("Creating command pools done")

        swapchainRecreator = SwapchainRecreator()

        swapchain = when {
            wantsOpenGLSwapchain -> {
                logger.info("Using OpenGL-based swapchain")
                OpenGLSwapchain(
                    device, queue, commandPools,
                    renderConfig = renderConfig, useSRGB = renderConfig.sRGB,
                    useFramelock = System.getProperty("scenery.Renderer.Framelock", "false")?.toBoolean() ?: false)
            }

            (System.getProperty("scenery.Headless", "false")?.toBoolean() ?: false) -> {
                logger.info("Vulkan running in headless mode.")
                HeadlessSwapchain(
                    device, queue, commandPools,
                    renderConfig = renderConfig, useSRGB = renderConfig.sRGB)
            }

            (System.getProperty("scenery.Renderer.UseJavaFX", "false")?.toBoolean() ?: false || embedIn != null) -> {
                logger.info("Using JavaFX-based swapchain")
                FXSwapchain(
                    device, queue, commandPools,
                    renderConfig = renderConfig, useSRGB = renderConfig.sRGB)
            }

            else -> {
                VulkanSwapchain(
                    device, queue, commandPools,
                    renderConfig = renderConfig, useSRGB = renderConfig.sRGB,
                    vsync = settings.get("Renderer.ForceVsync"),
                    undecorated = settings.get("Renderer.ForceUndecoratedWindow"))
            }
        }.apply {
            embedIn(embedIn)
            window = createWindow(window, swapchainRecreator)
        }

        logger.debug("Created swapchain")
        vertexDescriptors = prepareStandardVertexDescriptors()
        logger.debug("Created vertex descriptors")
        descriptorPool = createDescriptorPool()
        logger.debug("Created descriptor pool")

        descriptorSetLayouts = prepareDefaultDescriptorSetLayouts()
        logger.debug("Prepared default DSLs")
        prepareDefaultBuffers(device, buffers)
        logger.debug("Prepared default buffers")

        prepareDescriptorSets(descriptorPool)
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
                    " - VALIDATIONS ENABLED"
                } else {
                    ""
                }

                window.setTitle("$applicationName [${this@VulkanRenderer.javaClass.simpleName}, ${this@VulkanRenderer.renderConfig.name}] $validationsEnabled - $fps fps")
            }
        }, 0, 1000)

        // Info struct to create a semaphore
        semaphoreCreateInfo = VkSemaphoreCreateInfo()

        lastTime = System.nanoTime()
        time = 0f

        if (System.getProperty("scenery.RunFullscreen", "false")?.toBoolean() == true) {
            toggleFullscreen = true
        }

        geometryPool = VulkanBufferPool(device, usage = VkBufferUsage.VERTEX_BUFFER_BIT or VkBufferUsage.INDEX_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT)

        initialized = true
        logger.info("Renderer initialisation complete.")
    }

    // source: http://stackoverflow.com/questions/34697828/parallel-operations-on-kotlin-collections
    // Thanks to Holger :-)
    @Suppress("UNUSED")
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

    @Suppress("UNUSED")
    fun setCurrentScene(scene: Scene) {
        this.scene = scene
    }

    /**
     * This function should initialize the current scene contents.
     */
    override fun initializeScene() {
        logger.info("Scene initialization started.")

        this.scene.discover(this.scene, { it is HasGeometry && it !is Light })
//            .parallelMap(numThreads = System.getProperty("scenery.MaxInitThreads", "1").toInt()) { node ->
            .map { node ->
                // skip initialization for nodes that are only instance slaves
                if (node.instanceOf != null) {
                    node.initialized = true
                    return@map
                }

                logger.debug("Initializing object '${node.name}'")
                node.metadata["VulkanRenderer"] = VulkanObjectState()

                initializeNode(node)
            }

        scene.initialized = true
        logger.info("Scene initialization complete.")
    }

    fun updateNodeGeometry(node: Node) {
        if (node is HasGeometry && node.vertices.remaining() > 0) {
            node.rendererMetadata()?.let { s ->
                createVertexBuffers(device, node, s)
            }
        }
    }

    /**
     * Returns the material type flag for a Node, considering it's [Material]'s textures.
     */
    protected fun Node.materialTypeFromTextures(s: VulkanObjectState): Int {
        var materialType = 0
        if (material.textures.containsKey("ambient") && !s.defaultTexturesFor.contains("ambient")) {
            materialType = materialType or MATERIAL_HAS_AMBIENT
        }

        if (material.textures.containsKey("diffuse") && !s.defaultTexturesFor.contains("diffuse")) {
            materialType = materialType or MATERIAL_HAS_DIFFUSE
        }

        if (material.textures.containsKey("specular") && !s.defaultTexturesFor.contains("specular")) {
            materialType = materialType or MATERIAL_HAS_SPECULAR
        }

        if (material.textures.containsKey("normal") && !s.defaultTexturesFor.contains("normal")) {
            materialType = materialType or MATERIAL_HAS_NORMAL
        }

        if (material.textures.containsKey("alphamask") && !s.defaultTexturesFor.contains("alphamask")) {
            materialType = materialType or MATERIAL_HAS_ALPHAMASK
        }

        return materialType
    }

    /**
     * Initialises a given [node] with the metadata required by the [VulkanRenderer].
     */
    fun initializeNode(node: Node): Boolean {
        var s: VulkanObjectState = node.rendererMetadata()
            ?: throw IllegalStateException("Node ${node.name} does not contain metadata object")

        if (s.initialized) return true

        logger.debug("Initializing ${node.name} (${(node as HasGeometry).vertices.remaining() / node.vertexSize} vertices/${node.indices.remaining()} indices)")

        // determine vertex input type
        s.vertexInputType = when {
            node.vertices.remaining() > 0 && node.normals.remaining() > 0 && node.texcoords.remaining() > 0 -> VertexDataKinds.PositionNormalTexcoord
            node.vertices.remaining() > 0 && node.normals.remaining() > 0 && node.texcoords.remaining() == 0 -> VertexDataKinds.PositionNormal
            node.vertices.remaining() > 0 && node.normals.remaining() == 0 && node.texcoords.remaining() > 0 -> VertexDataKinds.PositionTexcoords
            else -> VertexDataKinds.PositionNormalTexcoord
        }

        // create custom vertex description if necessary, else use one of the defaults
        s.vertexDescription = if (node.instanceMaster) {
            updateInstanceBuffer(device, node, s)
            // TODO: Rewrite shader in case it does not conform to coord/normal/texcoord vertex description
            s.vertexInputType = VertexDataKinds.PositionNormalTexcoord
            vertexDescriptionFromInstancedNode(node, vertexDescriptors[VertexDataKinds.PositionNormalTexcoord]!!)
        } else {
            vertexDescriptors[s.vertexInputType]!!
        }

        val instanceMaster = node.instanceOf
        if (instanceMaster != null) {
            val parentMetadata = instanceMaster.rendererMetadata()
                ?: throw IllegalStateException("Instance master lacks metadata")

            if (!parentMetadata.initialized) {
                logger.debug("Instance parent $instanceMaster is not initialized yet, initializing now...")
                initializeNode(instanceMaster)
            }

            return true
        }

        s = createVertexBuffers(device, node, s)

        val matricesDescriptorSet = getDescriptorCache().getOrPut("Matrices") {
            VU.createDescriptorSetDynamic(vkDev, descriptorPool,
                descriptorSetLayouts["Matrices"]!!, 1,
                buffers["UBOBuffer"]!!)
        }!!

        val materialPropertiesDescriptorSet = getDescriptorCache().getOrPut("MaterialProperties") {
            VU.createDescriptorSetDynamic(vkDev, descriptorPool,
                descriptorSetLayouts["MaterialProperties"]!!, 1,
                buffers["UBOBuffer"]!!)
        }!!

        val matricesUbo = VulkanUBO(device, backingBuffer = buffers["UBOBuffer"])
        with(matricesUbo) {
            name = "Matrices"
            add("ModelMatrix", { node.world })
            add("NormalMatrix", { node.world.inverse.transpose() })
            add("isBillboard", { node.isBillboard.i })

            createUniformBuffer()
            sceneUBOs.add(node)

            s.UBOs[name] = matricesDescriptorSet to this
        }

        loadTexturesForNode(node, s)

        s.blendingHashCode = node.material.blending.hashCode()

        val materialUbo = VulkanUBO(device, backingBuffer = buffers["UBOBuffer"])
        with(materialUbo) {
            name = "MaterialProperties"
            add("materialType", { node.materialTypeFromTextures(s) })
            add("Ka", { node.material.ambient })
            add("Kd", { node.material.diffuse })
            add("Ks", { node.material.specular })
            add("Roughness", { node.material.roughness })
            add("Metallic", { node.material.metallic })
            add("Opacity", { node.material.blending.opacity })

            createUniformBuffer()
            s.UBOs["MaterialProperties"] = materialPropertiesDescriptorSet to this
        }

        s.initialized = true
        node.initialized = true
        node.metadata["VulkanRenderer"] = s

        try {
            initializeCustomShadersForNode(node)
        } catch (e: ShaderCompilationException) {
            logger.error("Compilation of custom shader failed: ${e.message}")
            logger.error("Node ${node.name} will use default shader for render pass.")

            if (logger.isDebugEnabled) {
                e.printStackTrace()
            }
        }

        return true
    }

    private fun initializeCustomShadersForNode(node: Node, addInitializer: Boolean = true): Boolean {

        if (!(node.material.blending.transparent || node.material is ShaderMaterial || node.material.cullingMode != Material.CullingMode.Back)) {
            logger.debug("Using default renderpass material for ${node.name}")
            return false
        }

        if (node.instanceOf != null) {
            logger.debug("${node.name} is instance slave, not initializing custom shaders")
            return false
        }

        if (addInitializer) {
            lateResizeInitializers.remove(node)
        }

        node.rendererMetadata()?.let { s ->

            //            node.javaClass.kotlin.memberProperties.filter { it.findAnnotation<ShaderProperty>() != null }.forEach { logger.info("${node.name}.${it.name} is ShaderProperty!") }
            val needsShaderPropertyUBO = if (node.javaClass.kotlin.memberProperties.any { it.findAnnotation<ShaderProperty>() != null }) {
                var dsl = VkDescriptorSetLayout(NULL)

                renderpasses.filter {
                    (it.value.passConfig.type == RenderConfigReader.RenderpassType.geometry || it.value.passConfig.type == RenderConfigReader.RenderpassType.lights)
                        && it.value.passConfig.renderTransparent == node.material.blending.transparent
                }
                    .map { pass ->
                        logger.debug("Initializing shader properties for ${node.name}")
                        dsl = pass.value.initializeShaderPropertyDescriptorSetLayout()
                    }

                val descriptorSet = VU.createDescriptorSetDynamic(vkDev, descriptorPool, dsl,
                    1, buffers["ShaderPropertyBuffer"]!!)

                s.requiredDescriptorSets["ShaderProperties"] = descriptorSet
                true
            } else {
                false
            }


            renderpasses.filter { it.value.passConfig.type == RenderConfigReader.RenderpassType.geometry || it.value.passConfig.type == RenderConfigReader.RenderpassType.lights }
                .map { pass ->
                    val shaders = when {
                        node.material is ShaderMaterial -> {
                            logger.debug("Initializing preferred pipeline for ${node.name} from ShaderMaterial")
                            (node.material as ShaderMaterial).shaders
                        }

//                        pass.value.passConfig.renderTransparent == node.material.blending.transparent -> {
//                            logger.debug("Initializing classname-derived preferred pipeline for ${node.name}")
//                            val shaders = node.findExistingShaders()
//
//                            if(shaders.isEmpty()) {
//                                throw ShaderCompilationException("No shaders found for ${node.name}")
//                            }
//
//                            shaders
//                        }

                        else -> {
                            logger.debug("Initializing pass-default shader preferred pipeline for ${node.name}")
                            Shaders.ShadersFromFiles(pass.value.passConfig.shaders.map { "shaders/$it" }.toTypedArray())
                        }
                    }

                    logger.debug("Shaders are: $shaders")

                    val shaderModules = ShaderType.values().mapNotNull { type ->
                        try {
                            VulkanShaderModule.getFromCacheOrCreate(device, "main", shaders.get(Shaders.ShaderTarget.Vulkan, type))
                        } catch (e: ShaderNotFoundException) {
                            null
                        }
                    }

                    pass.value.initializeInputAttachmentDescriptorSetLayouts(shaderModules)
                    pass.value.initializePipeline("preferred-${node.uuid}",
                        shaderModules, settings = { pipeline ->
                        pipeline.rasterizationState.cullMode = when (node.material.cullingMode) {
                            Material.CullingMode.None -> VkCullMode.NONE
                            Material.CullingMode.Front -> VkCullMode.FRONT_BIT
                            Material.CullingMode.Back -> VkCullMode.BACK_BIT
                            Material.CullingMode.FrontAndBack -> VkCullMode.FRONT_AND_BACK
                        }.i

                        pipeline.depthStencilState.depthCompareOp = when (node.material.depthTest) {
                            Material.DepthTest.Equal -> VkCompareOp.EQUAL
                            Material.DepthTest.Less -> VkCompareOp.LESS
                            Material.DepthTest.Greater -> VkCompareOp.GREATER
                            Material.DepthTest.LessEqual -> VkCompareOp.LESS_OR_EQUAL
                            Material.DepthTest.GreaterEqual -> VkCompareOp.GREATER_OR_EQUAL
                            Material.DepthTest.Always -> VkCompareOp.ALWAYS
                            Material.DepthTest.Never -> VkCompareOp.NEVER
                        }

                        if (node.material.blending.transparent) {
                            with(node.material.blending) {
                                pipeline.colorBlendState.attachments?.let { blendStates ->
                                    for (state in blendStates) {
                                        @Suppress("SENSELESS_COMPARISON", "IfThenToSafeAccess")
                                        state.apply {
                                            blendEnable = true
                                            colorBlendOp = colorBlending.toVulkan()
                                            srcColorBlendFactor = sourceColorBlendFactor.toVulkan()
                                            dstColorBlendFactor = destinationColorBlendFactor.toVulkan()
                                            alphaBlendOp = alphaBlending.toVulkan()
                                            srcAlphaBlendFactor = sourceAlphaBlendFactor.toVulkan()
                                            dstAlphaBlendFactor = destinationAlphaBlendFactor.toVulkan()
                                            colorWriteMask = VkColorComponent.R_BIT or VkColorComponent.G_BIT or VkColorComponent.B_BIT or VkColorComponent.A_BIT
                                        }
                                    }
                                }
                            }
                        }
                    },
                        vertexInputType = s.vertexDescription!!)
                }


            if (needsShaderPropertyUBO) {
                renderpasses.filter {
                    (it.value.passConfig.type == RenderConfigReader.RenderpassType.geometry || it.value.passConfig.type == RenderConfigReader.RenderpassType.lights) &&
                        it.value.passConfig.renderTransparent == node.material.blending.transparent
                }.forEach { pass ->
                    logger.debug("Initializing shader properties for ${node.name} in pass ${pass.key}")
                    val order = pass.value.getShaderPropertyOrder(node)

                    val shaderPropertyUbo = VulkanUBO(device, backingBuffer = buffers["ShaderPropertyBuffer"])
                    with(shaderPropertyUbo) {
                        name = "ShaderProperties"

                        order.forEach { name, offset ->
                            add(name, { node.getShaderProperty(name)!! }, offset)
                        }

                        this.createUniformBuffer()
                        s.UBOs["${pass.key}-ShaderProperties"] = s.requiredDescriptorSets["ShaderProperties"]!! to this
                    }
                }

            }

            if (addInitializer) {
                lateResizeInitializers[node] = { initializeCustomShadersForNode(node, addInitializer = false) }
            }

            return true
        }

        return false
    }

    fun destroyNode(node: Node) {
        logger.trace("Destroying node ${node.name}...")
        if (!node.metadata.containsKey("VulkanRenderer")) {
            return
        }

        lateResizeInitializers.remove(node)
        node.initialized = false

        node.rendererMetadata()?.UBOs?.forEach { it.value.second.close() }

        if (node is HasGeometry) {
            node.rendererMetadata()?.vertexBuffers?.forEach {
                it.value.close()
            }
        }

        node.metadata.remove("VulkanRenderer")
    }

    /**
     * Returns true if the current VulkanTexture can be reused to store the information in the [GenericTexture]
     * [other]. Returns false otherwise.
     */
    protected fun VulkanTexture.canBeReused(other: GenericTexture, miplevels: Int, device: VulkanDevice): Boolean {
        return this.device == device &&
            this.width == other.dimensions.x().toInt() &&
            this.height == other.dimensions.y().toInt() &&
            this.depth == other.dimensions.z().toInt() &&
            this.mipLevels == miplevels

    }

    /**
     * Loads a texture given as string in [texture] from the classpath of this Node. Emits an error and falls back
     * to [fallback] in case the texture cannot be located.
     */
    protected fun Node.loadTextureFromJar(texture: String, generateMipmaps: Boolean, fallback: VulkanTexture): VulkanTexture {
        val f = texture.substringAfterLast("/")
        val stream = this@loadTextureFromJar.javaClass.getResourceAsStream(f)

        return if (stream == null) {
            logger.error("Not found: $f for ${this@loadTextureFromJar}")
            fallback
        } else {
            VulkanTexture.loadFromFile(device,
                commandPools, queue, queue, stream,
                texture.substringAfterLast("."), true, generateMipmaps)
        }
    }

    /**
     * Loads or reloads the textures for [node], updating it's internal renderer state stored in [s].
     */
    protected fun loadTexturesForNode(node: Node, s: VulkanObjectState): VulkanObjectState {
        val stats = hub?.get(SceneryElement.Statistics) as Statistics?
        val defaultTexture = textureCache["DefaultTexture"]
            ?: throw IllegalStateException("Default fallback texture does not exist.")

        if (!node.lock.tryLock()) {
            logger.warn("Failed to lock node ${node.name} for texture update")
            return s
        }

        node.material.textures.forEach { type, texture ->
            val slot = VulkanObjectState.textureTypeToSlot(type)
            val generateMipmaps = (type == "ambient" || type == "diffuse" || type == "specular")

            logger.debug("${node.name} will have $type texture from $texture in slot $slot")

            if (!textureCache.containsKey(texture) || node.material.needsTextureReload) {
                logger.trace("Loading texture $texture for ${node.name}")

                val gt = node.material.transferTextures[texture.substringAfter("fromBuffer:")]

                val vkTexture: VulkanTexture = if (texture.startsWith("fromBuffer:") && gt != null) {
                    val miplevels = if (generateMipmaps && gt.mipmap) {
                        1 + Math.floor(Math.log(Math.max(gt.dimensions.x() * 1.0, gt.dimensions.y() * 1.0)) / Math.log(2.0)).toInt()
                    } else {
                        1
                    }

                    val existingTexture = s.textures[type]
                    val t: VulkanTexture = if (existingTexture != null && existingTexture.canBeReused(gt, miplevels, device)) {
                        existingTexture
                    } else {
                        VulkanTexture(device, commandPools, queue, queue, gt, miplevels)
                    }

                    t.copyFrom(gt.contents)
                } else {
                    val start = System.nanoTime()

                    val t = if (texture.contains("jar!")) {
                        node.loadTextureFromJar(texture, generateMipmaps, defaultTexture)
                    } else {
                        VulkanTexture.loadFromFile(device,
                            commandPools, queue, queue, texture, true, generateMipmaps)
                    }

                    val duration = System.nanoTime() - start * 1.0f
                    stats?.add("loadTexture", duration)

                    t
                }

                // add new texture to texture list and cache, and close old texture
                s.textures[type] = vkTexture
                textureCache[texture] = vkTexture
            } else {
                s.textures[type] = textureCache[texture]!!
            }
        }

        arrayOf("ambient", "diffuse", "specular", "normal", "alphamask", "displacement").forEach {
            if (!s.textures.containsKey(it)) {
                s.textures.putIfAbsent(it, defaultTexture)
                s.defaultTexturesFor.add(it)
            }
        }

        s.texturesToDescriptorSet(device, descriptorSetLayouts["ObjectTextures"]!!, descriptorPool, targetBinding = 0)

        node.lock.unlock()

        return s
    }

    protected fun prepareDefaultDescriptorSetLayouts(): ConcurrentHashMap<String, VkDescriptorSetLayout> {
        val m = ConcurrentHashMap<String, VkDescriptorSetLayout>()

        m["Matrices"] = vkDev.createDescriptorSetLayout(VkDescriptorType.UNIFORM_BUFFER_DYNAMIC to 1)

        m["MaterialProperties"] = vkDev.createDescriptorSetLayout(VkDescriptorType.UNIFORM_BUFFER_DYNAMIC to 1)

        m["LightParameters"] = vkDev.createDescriptorSetLayout(VkDescriptorType.UNIFORM_BUFFER to 1)

        m["ObjectTextures"] = vkDev.createDescriptorSetLayout(listOf(
            VkDescriptorType.COMBINED_IMAGE_SAMPLER to 6,
            VkDescriptorType.COMBINED_IMAGE_SAMPLER to 1))

        m["VRParameters"] = vkDev.createDescriptorSetLayout(VkDescriptorType.UNIFORM_BUFFER to 1)

        return m
    }

    protected fun prepareDescriptorSets(descriptorPool: VkDescriptorPool) {
        descriptorSets["Matrices"] = vkDev.createDescriptorSetDynamic(descriptorPool,
            descriptorSetLayouts["Matrices"]!!, 1,
            buffers["UBOBuffer"]!!)

        descriptorSets["MaterialProperties"] = vkDev.createDescriptorSetDynamic(descriptorPool,
            descriptorSetLayouts["MaterialProperties"]!!, 1,
            buffers["UBOBuffer"]!!)

        val lightUbo = VulkanUBO(device)
        lightUbo.add("ViewMatrix0", { GLMatrix.getIdentity() })
        lightUbo.add("ViewMatrix1", { GLMatrix.getIdentity() })
        lightUbo.add("InverseViewMatrix0", { GLMatrix.getIdentity() })
        lightUbo.add("InverseViewMatrix1", { GLMatrix.getIdentity() })
        lightUbo.add("ProjectionMatrix", { GLMatrix.getIdentity() })
        lightUbo.add("InverseProjectionMatrix", { GLMatrix.getIdentity() })
        lightUbo.add("CamPosition", { GLVector.getNullVector(3) })
        lightUbo.createUniformBuffer()
        lightUbo.populate()

        defaultUBOs["LightParameters"] = lightUbo

        this.descriptorSets["LightParameters"] = VU.createDescriptorSet(vkDev, descriptorPool,
            descriptorSetLayouts["LightParameters"]!!, 1,
            lightUbo.descriptor)

        val vrUbo = VulkanUBO(device)

        vrUbo.add("projection0", { GLMatrix.getIdentity() })
        vrUbo.add("projection1", { GLMatrix.getIdentity() })
        vrUbo.add("inverseProjection0", { GLMatrix.getIdentity() })
        vrUbo.add("inverseProjection1", { GLMatrix.getIdentity() })
        vrUbo.add("headShift", { GLMatrix.getIdentity() })
        vrUbo.add("IPD", { 0.0f })
        vrUbo.add("stereoEnabled", { 0 })
        vrUbo.createUniformBuffer()
        vrUbo.populate()

        defaultUBOs["VRParameters"] = vrUbo

        this.descriptorSets["VRParameters"] = VU.createDescriptorSet(vkDev, descriptorPool,
            descriptorSetLayouts["VRParameters"]!!, 1,
            vrUbo.descriptor)
    }

    protected fun prepareStandardVertexDescriptors(): ConcurrentHashMap<VertexDataKinds, VertexDescription> {
        val map = ConcurrentHashMap<VertexDataKinds, VertexDescription>()

        VertexDataKinds.values().forEach { kind ->
            val attributeDesc: VkVertexInputAttributeDescription.Buffer?
            val stride: Int

            when (kind) {
                VertexDataKinds.None -> {
                    stride = 0
                    attributeDesc = null
                }

                VertexDataKinds.PositionNormal -> {
                    stride = 3 + 3
                    attributeDesc = VkVertexInputAttributeDescription.calloc(2).also {
                        it[1].apply {
                            binding = 0
                            location = 1
                            format = VkFormat.R32G32B32_SFLOAT
                            offset = 3 * 4
                        }
                    }
                }

                VertexDataKinds.PositionNormalTexcoord -> {
                    stride = 3 + 3 + 2
                    attributeDesc = VkVertexInputAttributeDescription.calloc(3).also {
                        it[1].apply {
                            binding = 0
                            location = 1
                            format = VkFormat.R32G32B32_SFLOAT
                            offset = 3 * 4
                        }
                        it[2].apply {
                            binding = 0
                            location = 2
                            format = VkFormat.R32G32_SFLOAT
                            offset = 3 * 4 + 3 * 4
                        }
                    }
                }

                VertexDataKinds.PositionTexcoords -> {
                    stride = 3 + 2
                    attributeDesc = VkVertexInputAttributeDescription.calloc(2).also {
                        it[1].apply {
                            binding = 0
                            location = 1
                            format = VkFormat.R32G32_SFLOAT
                            offset = 3 * 4
                        }
                    }
                }
            }

            attributeDesc?.get(0)?.apply {
                binding = 0
                location = 0
                format = VkFormat.R32G32B32_SFLOAT
                offset = 0
            }

            val bindingDesc: VkVertexInputBindingDescription.Buffer? = VkVertexInputBindingDescription.calloc(1).also {
                it[0].apply {
                    binding = 0
                    this.stride = stride * 4
                    inputRate = VkVertexInputRate.VERTEX
                }
            }.takeIf { attributeDesc != null }

            val inputState = VkPipelineVertexInputStateCreateInfo().apply {
                vertexAttributeDescriptions = attributeDesc
                vertexBindingDescriptions = bindingDesc
            }
            map[kind] = VertexDescription(inputState, attributeDesc, bindingDesc)
        }

        return map
    }

    data class AttributeInfo(val format: VkFormat, val elementByteSize: Int, val elementCount: Int)

    fun HashMap<String, () -> Any>.getFormatsAndRequiredAttributeSize(): List<AttributeInfo> {
        return this.map {
            val value = it.value.invoke()

            when (value.javaClass) {
                GLVector::class.java -> {
                    val v = value as GLVector
                    when {
                        v.toFloatArray().size == 2 -> AttributeInfo(VkFormat.R32G32_SFLOAT, 4 * 2, 1)
                        v.toFloatArray().size == 4 -> AttributeInfo(VkFormat.R32G32B32A32_SFLOAT, 4 * 4, 1)
                        else -> AttributeInfo(VkFormat.UNDEFINED, -1, -1).also { logger.error("Unsupported vector length for instancing: ${v.toFloatArray().size}") }
                    }
                }

                GLMatrix::class.java -> {
                    val m = value as GLMatrix
                    AttributeInfo(VkFormat.R32G32B32A32_SFLOAT, 4 * 4, m.floatArray.size / 4)
                }

                else -> AttributeInfo(VkFormat.UNDEFINED, -1, -1).also { logger.error("Unsupported type for instancing: ${value.javaClass.simpleName}") }
            }
        }
    }

    protected fun vertexDescriptionFromInstancedNode(node: Node, template: VertexDescription): VertexDescription {
        logger.debug("Creating instanced vertex description for ${node.name}")

        if (template.attributeDescription == null || template.bindingDescription == null) {
            return template
        }

        val attributeDescs = template.attributeDescription
        val bindingDescs = template.bindingDescription

        val formatsAndAttributeSizes = node.instancedProperties.getFormatsAndRequiredAttributeSize()
        val newAttributesNeeded = formatsAndAttributeSizes.map { it.elementCount }.sum()

        val newAttributeDesc = VkVertexInputAttributeDescription(attributeDescs.capacity() + newAttributesNeeded)

        var position: Int
        var offset = 0

        for (i in 0 until attributeDescs.capacity()) {
            newAttributeDesc[i] = attributeDescs[i]
            offset += newAttributeDesc[i].offset()
            logger.debug("location(${newAttributeDesc[i].location()})")
            logger.debug("    .offset(${newAttributeDesc[i].offset()})")
            position = i // TODO useless
        }

        position = 3
        offset = 0

        formatsAndAttributeSizes.zip(node.instancedProperties.toList().reversed()).forEach {
            val attribInfo = it.first
            val property = it.second

            for (i in (0 until attribInfo.elementCount)) {
                newAttributeDesc[position].apply {
                    binding = 1
                    location = position
                    format = attribInfo.format
                    this.offset = offset
                }
                logger.debug("location($position, $i/${attribInfo.elementCount}) for ${property.first}, type: ${property.second.invoke().javaClass.simpleName}")
                logger.debug("\t.format(${attribInfo.format})")
                logger.debug("\t.offset($offset)")

                offset += attribInfo.elementByteSize
                position++
            }
        }

        logger.debug("stride($offset), ${bindingDescs.capacity()}")

        val newBindingDesc = VkVertexInputBindingDescription(bindingDescs.capacity() + 1).also {
            it[0] = bindingDescs[0]
            it[1].apply {
                binding = 1
                stride = offset
                inputRate = VkVertexInputRate.INSTANCE
            }
        }
        val inputState = VkPipelineVertexInputStateCreateInfo().apply {
            vertexAttributeDescriptions = newAttributeDesc
            vertexBindingDescriptions = newBindingDesc
        }
        return VertexDescription(inputState, newAttributeDesc, newBindingDesc)
    }

    protected fun prepareDefaultTextures(device: VulkanDevice) {
        val t = VulkanTexture.loadFromFile(device, commandPools, queue, queue,
            Renderer::class.java.getResourceAsStream("DefaultTexture.png"), "png", true, true)

        textureCache["DefaultTexture"] = t
    }

    protected fun prepareRenderpassesFromConfig(config: RenderConfigReader.RenderConfig, windowWidth: Int, windowHeight: Int) {
        // create all renderpasses first
        val framebuffers = ConcurrentHashMap<String, VulkanFramebuffer>()

        flow = renderConfig.createRenderpassFlow()
        logger.debug("Renderpasses to be run: ${flow.joinToString()}")

        descriptorSetLayouts
            .filter { it.key.startsWith("outputs-") }
            .map {
                logger.debug("Marking RT DSL ${it.value.asHexString} for deletion")
                vkDev destroyDescriptorSetLayout it.value
                it.key
            }
            .map {
                descriptorSetLayouts.remove(it)
            }

        renderConfig.renderpasses.filter { it.value.inputs != null }
            .flatMap { rp ->
                rp.value.inputs!!
            }
            .map {
                renderConfig.rendertargets.let { rts ->
                    val name = it.substringBefore(".")
                    name to rts[name]!!
                }
            }
            .map { rt ->
                if (!descriptorSetLayouts.containsKey("outputs-${rt.first}")) {
                    logger.debug("Creating output descriptor set for ${rt.first}")
                    // create descriptor set layout that matches the render target
                    descriptorSetLayouts["outputs-${rt.first}"] = VU.createDescriptorSetLayout(vkDev,
                        descriptorNum = rt.second.attachments.count(),
                        descriptorCount = 1,
                        type = VkDescriptorType.COMBINED_IMAGE_SAMPLER
                    )
                }
            }

        config.createRenderpassFlow().map { passName ->
            val passConfig = config.renderpasses[passName]!!
            val pass = VulkanRenderpass(passName, config, device, descriptorPool, pipelineCache, vertexDescriptors)

            var width = windowWidth
            var height = windowHeight

            // create framebuffer
            vkDev.newCommandBuffer(commandPools.Standard)
                .record {
                    config.rendertargets.filter { it.key == passConfig.output }.map { rt ->
                        logger.info("Creating render framebuffer ${rt.key} for pass $passName")

                        width = (settings.get<Float>("Renderer.SupersamplingFactor") * windowWidth * rt.value.size.first).toInt()
                        height = (settings.get<Float>("Renderer.SupersamplingFactor") * windowHeight * rt.value.size.second).toInt()

                        settings.set("Renderer.$passName.displayWidth", width)
                        settings.set("Renderer.$passName.displayHeight", height)

                        if (framebuffers.containsKey(rt.key)) {
                            logger.info("Reusing already created framebuffer")
                            pass.output[rt.key] = framebuffers[rt.key]!!
                        } else {

                            // create framebuffer -- don't clear it, if blitting is needed
                            val framebuffer = VulkanFramebuffer(this@VulkanRenderer.device, commandPools.Standard,
                                width, height, this,
                                shouldClear = !passConfig.blitInputs,
                                sRGB = renderConfig.sRGB)

                            rt.value.attachments.forEach { att ->
                                logger.info(" + attachment ${att.key}, ${att.value.name}")

                                when (att.value) {
                                    RenderConfigReader.TargetFormat.RGBA_Float32 -> framebuffer.addFloatRGBABuffer(att.key, 32)
                                    RenderConfigReader.TargetFormat.RGBA_Float16 -> framebuffer.addFloatRGBABuffer(att.key, 16)

                                    RenderConfigReader.TargetFormat.RGB_Float32 -> framebuffer.addFloatRGBBuffer(att.key, 32)
                                    RenderConfigReader.TargetFormat.RGB_Float16 -> framebuffer.addFloatRGBBuffer(att.key, 16)

                                    RenderConfigReader.TargetFormat.RG_Float32 -> framebuffer.addFloatRGBuffer(att.key, 32)
                                    RenderConfigReader.TargetFormat.RG_Float16 -> framebuffer.addFloatRGBuffer(att.key, 16)

                                    RenderConfigReader.TargetFormat.RGBA_UInt16 -> framebuffer.addUnsignedByteRGBABuffer(att.key, 16)
                                    RenderConfigReader.TargetFormat.RGBA_UInt8 -> framebuffer.addUnsignedByteRGBABuffer(att.key, 8)
                                    RenderConfigReader.TargetFormat.R_UInt16 -> framebuffer.addUnsignedByteRBuffer(att.key, 16)
                                    RenderConfigReader.TargetFormat.R_UInt8 -> framebuffer.addUnsignedByteRBuffer(att.key, 8)

                                    RenderConfigReader.TargetFormat.Depth32 -> framebuffer.addDepthBuffer(att.key, 32)
                                    RenderConfigReader.TargetFormat.Depth24 -> framebuffer.addDepthBuffer(att.key, 24)
                                    RenderConfigReader.TargetFormat.R_Float16 -> framebuffer.addFloatBuffer(att.key, 16)
                                }

                            }

                            framebuffer.createRenderpassAndFramebuffer()
                            framebuffer.outputDescriptorSet = VkDescriptorSet(VU.createRenderTargetDescriptorSet(this@VulkanRenderer.device,
                                descriptorPool, descriptorSetLayouts["outputs-${rt.key}"]!!, rt.value.attachments, framebuffer))

                            pass.output[rt.key] = framebuffer
                            framebuffers[rt.key] = framebuffer
                        }
                    }

                    pass.commandBufferCount = swapchain.images.size

                    if (passConfig.output == "Viewport") {
                        // create viewport renderpass with swapchain image-derived framebuffer
                        pass.isViewportRenderpass = true
                        width = windowWidth
                        height = windowHeight

                        for (i in swapchain.images.indices) {
                            val fb = VulkanFramebuffer(this@VulkanRenderer.device, commandPools.Standard,
                                width, height, this, sRGB = renderConfig.sRGB).apply {

                                addSwapchainAttachment("swapchain-$i", swapchain, i)
                                addDepthBuffer("swapchain-$i-depth", 32)
                                createRenderpassAndFramebuffer()
                            }
                            pass.output["Viewport-$i"] = fb
                        }
                    }

                    pass.vulkanMetadata.clearValues?.free()
                    pass.vulkanMetadata.clearValues = when {
                        passConfig.blitInputs -> null
                        else -> VkClearValue(pass.output.values.first().attachments.count()).also { clearValues ->

                            pass.output.values.first().attachments.values.forEachIndexed { i, att ->
                                when (att.type) {
                                    VulkanFramebuffer.VulkanFramebufferType.COLOR_ATTACHMENT -> {
                                        clearValues[i].color(pass.passConfig.clearColor.toFloatArray())
                                    }
                                    VulkanFramebuffer.VulkanFramebufferType.DEPTH_ATTACHMENT -> {
                                        clearValues[i].depthStencil(pass.passConfig.depthClearValue, 0)
                                    }
                                }
                            }
                        }
                    }

                    pass.vulkanMetadata.apply {

                        val cfg = pass.passConfig

                        renderArea.apply {
                            extent(
                                cfg.viewportSize.first * width,
                                cfg.viewportSize.second * height)
                            offset(
                                cfg.viewportOffset.first * width,
                                cfg.viewportOffset.second * height)
                            logger.debug("Render area for $passName: ${extent.width}x${extent.height}")
                        }
                        viewport.set(
                            cfg.viewportOffset.first * width,
                            cfg.viewportOffset.second * height,
                            cfg.viewportSize.first * width,
                            cfg.viewportSize.second * height,
                            0f, 1f)

                        scissor.extent(
                            cfg.viewportSize.first * width,
                            cfg.viewportSize.second * height)

                        scissor.offset(
                            cfg.viewportOffset.first * width,
                            cfg.viewportOffset.second * height)

                        eye[0] = pass.passConfig.eye
                    }
                }
                .submit(queue)

            renderpasses[passName] = pass
        }

        // connect inputs with each othe
        renderpasses.forEach { pass ->
            val passConfig = config.renderpasses[pass.key]!!

            passConfig.inputs?.forEach { inputTarget ->
                val targetName = inputTarget.substringBefore(".")
                renderpasses.filter { targetName in it.value.output.keys }
                    .forEach { pass.value.inputs[inputTarget] = it.value.output[targetName]!! }
            }

            with(pass.value) {
                initializeShaderParameterDescriptorSetLayouts(settings)

                initializeDefaultPipeline()
            }
        }
    }

    protected fun prepareStandardSemaphores(): ConcurrentHashMap<StandardSemaphores, VkSemaphoreArray> {
        val map = ConcurrentHashMap<StandardSemaphores, VkSemaphoreArray>()

        StandardSemaphores.values().forEach {
            map[it] = VkSemaphoreArray(swapchain.images.map { vkDev createSemaphore semaphoreCreateInfo })
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

    private fun beginFrame() {
        swapchainRecreator.mustRecreate = swapchain.next(timeout = UINT64_MAX,
            signalSemaphore = semaphores[StandardSemaphores.PresentComplete]!![0])
    }

    @Suppress("unused")
    fun recordMovie() {
        if (recordMovie) {
            encoder?.finish()
            encoder = null

            recordMovie = false
        } else {
            recordMovie = true
        }
    }

    private fun submitFrame(queue: VkQueue, pass: VulkanRenderpass, commandBuffer: VulkanCommandBuffer, present: PresentHelpers) {
        val stats = hub?.get(SceneryElement.Statistics) as? Statistics
        present.submitInfo.apply {
            waitSemaphoreCount = 1
            waitSemaphore = present.waitSemaphore
            waitDstStageMask = present.waitStages
            commandBuffers = present.commandBuffers
            signalSemaphore = present.signalSemaphore
        }
        // Submit to the graphics queue
        queue.submit(present.submitInfo, commandBuffer.fence)

        val startPresent = System.nanoTime()
        commandBuffer.submitted = true
        swapchain.present(vkSemaphoreBufferOf(ph.signalSemaphore))
        // TODO: Figure out whether this waitForFence call is strictly necessary -- actually, the next renderloop iteration should wait for it.
        commandBuffer.waitForFence()

        swapchain.postPresent(pass.getReadPosition())

        // submit to OpenVR if attached
        if (hub?.getWorkingHMDDisplay()?.hasCompositor() == true) {
            hub?.getWorkingHMDDisplay()?.wantsVR()?.submitToCompositorVulkan(
                window.width, window.height,
                swapchain.format.i,
                instance, device, queue,
                swapchain.images[pass.getReadPosition()])
        }

        if (recordMovie || screenshotRequested) {
            // default image format is 32bit BGRA
            val imageByteSize = VkDeviceSize(window.width * window.height * 4L)
            if (screenshotBuffer == null || screenshotBuffer?.size?.L != imageByteSize.L) {
                logger.debug("Reallocating screenshot buffer")
                screenshotBuffer = VulkanBuffer(device, imageByteSize,
                    VkBufferUsage.TRANSFER_DST_BIT.i,
                    VkMemoryProperty.HOST_VISIBLE_BIT.i,
                    wantAligned = true)
            }

            if (imageBuffer == null || imageBuffer?.cap != imageByteSize.i) {
                logger.debug("Reallocating image buffer")
                imageBuffer = memAlloc(imageByteSize.i)
            }

            // finish encoding if a resize was performed
            if (recordMovie) {
                if (encoder != null && (encoder?.frameWidth != window.width || encoder?.frameHeight != window.height)) {
                    encoder?.finish()
                }

                if (encoder == null || encoder?.frameWidth != window.width || encoder?.frameHeight != window.height) {
                    encoder = H264Encoder(window.width, window.height, System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "$applicationName - ${SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(Date())}.mp4")
                }
            }

            screenshotBuffer?.let { sb ->
                vkDev.newCommandBuffer(commandPools.Render)
                    .record {
                        val subresource = vk.ImageSubresourceLayers {
                            aspectMask = VkImageAspect.COLOR_BIT.i
                            mipLevel = 0
                            baseArrayLayer = 0
                            layerCount = 1
                        }
                        val region = vk.BufferImageCopy {
                            bufferRowLength = 0
                            bufferImageHeight = 0
                            imageOffset(0)
                            imageExtent(window.width, window.height, 1)
                            imageSubresource = subresource
                        }
                        val image = swapchain.images[pass.getReadPosition()]

                        VulkanTexture.transitionLayout(image,
                            VkImageLayout.PRESENT_SRC_KHR,
                            VkImageLayout.TRANSFER_SRC_OPTIMAL,
                            commandBuffer = this)

                        copyImageToBuffer(image, VkImageLayout.TRANSFER_SRC_OPTIMAL, sb.vulkanBuffer, region)

                        VulkanTexture.transitionLayout(image, VkImageLayout.TRANSFER_SRC_OPTIMAL, VkImageLayout.PRESENT_SRC_KHR, commandBuffer = this)
                    }
                    .submit(queue).deallocate()

                if (screenshotRequested) {
                    sb.copyTo(imageBuffer!!)
                }

                if (recordMovie) {
                    encoder?.encodeFrame(memByteBuffer(sb.mapIfUnmapped(), imageByteSize.i))
                }

                if (screenshotRequested && !recordMovie) {
                    sb.close()
                    screenshotBuffer = null
                }
            }

            if (screenshotRequested) {
                // reorder bytes for screenshot in a separate thread
                thread {
                    imageBuffer?.let { ib ->
                        try {
                            val file = SystemHelpers.addFileCounter(if (screenshotFilename == "") {
                                File(System.getProperty("user.home"), "Desktop" + File.separator + "$applicationName - ${SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(Date())}.png")
                            } else {
                                File(screenshotFilename)
                            }, screenshotOverwriteExisting)
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

                            ImageIO.write(image, "png", file)
                            logger.info("Screenshot saved to ${file.absolutePath}")
                        } catch (e: Exception) {
                            logger.error("Unable to take screenshot: ")
                            e.printStackTrace()
                        } finally {
//                            ib.free()
                        }
                    }
                }

                screenshotOverwriteExisting = false
                screenshotRequested = false
            }
        }

        val presentDuration = System.nanoTime() - startPresent
        stats?.add("Renderer.viewportSubmitAndPresent", presentDuration)

        firstImageReady = true
    }

    /**
     * This function renders the scene
     */
    override fun render() = runBlocking {
        val swapchainChanged = pollEvents()

        if (shouldClose) {
            closeInternal()
            return@runBlocking
        }

        val stats = hub?.get(SceneryElement.Statistics) as? Statistics
        val sceneObjects = GlobalScope.async {
            scene.discover(scene, { n ->
                n is HasGeometry
                    && n.visible
                    && n.instanceOf == null
            }, useDiscoveryBarriers = true)
        }

        // check whether scene is already initialized
        if (scene.children.count() == 0 || !scene.initialized) {
            initializeScene()

            Thread.sleep(200)
            return@runBlocking
        }

        if (toggleFullscreen) {
            vkDev.waitIdle()

            switchFullscreen()
            toggleFullscreen = false
            return@runBlocking
        }

        if (window.shouldClose) {
            shouldClose = true
            // stop all
            vkDev.waitIdle()
            return@runBlocking
        }

        if (renderDelay > 0) {
            logger.warn("Delaying next frame for $renderDelay ms, as one or more validation error have occured in the previous frame.")
            Thread.sleep(renderDelay)
        }


        val startUboUpdate = System.nanoTime()
        val ubosUpdated = updateDefaultUBOs(device)
        stats?.add("Renderer.updateUBOs", System.nanoTime() - startUboUpdate)

        val startInstanceUpdate = System.nanoTime()
        updateInstanceBuffers(sceneObjects)
        stats?.add("Renderer.updateInstanceBuffers", System.nanoTime() - startInstanceUpdate)

        // flag set to true if command buffer re-recording is necessary,
        // e.g. because of scene or pipeline changes
        var forceRerecording = false
        val rerecordingCauses = ArrayList<String>(20)

        // here we discover the objects in the scene that could be relevant for the scene
        if (renderpasses.filter { it.value.passConfig.type != RenderConfigReader.RenderpassType.quad }.any()) {
            sceneObjects.await().forEach {
                // if a node is not initialized yet, it'll be initialized here and it's UBO updated
                // in the next round
                if (it.rendererMetadata() == null) {
                    logger.debug("${it.name} is not initialized, doing that now")
                    it.metadata["VulkanRenderer"] = VulkanObjectState()
                    initializeNode(it)

                    return@forEach
                }

                it.preDraw()

                // the current command buffer will be forced to be re-recorded if either geometry, blending or
                // texturing of a given node have changed, as these might change pipelines or descriptor sets, leading
                // to the original command buffer becoming obsolete.
                it.rendererMetadata()?.let { metadata ->
                    if (it.dirty) {
                        logger.debug("Force command buffer re-recording, as geometry for {} has been updated", it.name)

                        it.preUpdate(this@VulkanRenderer, hub!!)
                        updateNodeGeometry(it)
                        it.dirty = false

                        rerecordingCauses.add(it.name)
                        forceRerecording = true
                    }

                    if (it.material.needsTextureReload) {
                        logger.trace("Force command buffer re-recording, as reloading textures for ${it.name}")
                        loadTexturesForNode(it, metadata)

                        it.material.needsTextureReload = false

                        rerecordingCauses.add(it.name)
                        forceRerecording = true
                    }

                    if (it.material.blending.hashCode() != metadata.blendingHashCode) {
                        logger.trace("Force command buffer re-recording, as blending options for ${it.name} have changed")
                        initializeCustomShadersForNode(it)
                        metadata.blendingHashCode = it.material.blending.hashCode()

                        rerecordingCauses.add(it.name)
                        forceRerecording = true
                    }
                }
            }

            val newSceneArray = sceneObjects.getCompleted().toTypedArray()
            if (!newSceneArray.contentDeepEquals(sceneArray)) {
                forceRerecording = true
            }

            sceneArray = newSceneArray
        }

        val presentedFrames = swapchain.presentedFrames()
        // return if neither UBOs were updated, nor the scene was modified
        if (pushMode && !swapchainChanged && !ubosUpdated && !forceRerecording && !screenshotRequested && totalFrames > 3 && presentedFrames > 3) {
            logger.trace("UBOs have not been updated, returning (pushMode={}, swapchainChanged={}, ubosUpdated={}, forceRerecording={}, screenshotRequested={})", pushMode, swapchainChanged, ubosUpdated, forceRerecording, totalFrames)
            Thread.sleep(2)

            return@runBlocking
        }

        beginFrame()

        // firstWaitSemaphore is now the RenderComplete semaphore of the previous pass
        firstWaitSemaphore[0] = semaphores[StandardSemaphores.PresentComplete]!![0].L

        val si = vk.SubmitInfo()

        var waitSemaphore = semaphores[StandardSemaphores.PresentComplete]!![0]

        val timing = stackGet().callocInt(2)

        flow.take(flow.size - 1).forEachIndexed { i, t ->
            logger.trace("Running pass {}", t)
            val target = renderpasses[t]!!
            val commandBuffer = target.commandBuffer.apply {
                if (submitted) {
                    waitForFence()
                    submitted = false
                    resetFence()

                    vkDev.getQueryPoolResults(timestampQueryPool, 2 * i, 2, timing)

                    stats?.add("Renderer.$t.gpuTiming", timing[1] - timing[0])
                }
            }
            val start = System.nanoTime()

            when (target.passConfig.type) {
                RenderConfigReader.RenderpassType.geometry -> recordSceneRenderCommands(target, commandBuffer, sceneObjects, { it !is Light }, forceRerecording)
                RenderConfigReader.RenderpassType.lights -> recordSceneRenderCommands(target, commandBuffer, sceneObjects, { it is Light }, forceRerecording)
                RenderConfigReader.RenderpassType.quad -> recordPostprocessRenderCommands(target, commandBuffer)
            }

            stats?.add("VulkanRenderer.$t.recordCmdBuffer", System.nanoTime() - start)

            target.apply {
                updateShaderParameters()

                submitCommandBuffers[0] = commandBuffer.commandBuffer!!
                signalSemaphores[0] = target.semaphore.L
                waitSemaphores[0] = waitSemaphore.L
                waitStages[0] = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i

                si.also {
                    it.waitSemaphoreCount = 1
                    it.waitDstStageMask = waitStages
                    it.commandBuffers = submitCommandBuffers
                    it.signalSemaphores = VkSemaphoreBuffer(signalSemaphores)
                    it.waitSemaphores = VkSemaphoreBuffer(waitSemaphores)
                }
            }
            queue.submit(si, commandBuffer.fence)

            commandBuffer.submitted = true
            firstWaitSemaphore[0] = target.semaphore.L
            waitSemaphore = target.semaphore
        }

        val viewportPass = renderpasses.values.last()
        val viewportCommandBuffer = viewportPass.commandBuffer
        logger.trace("Running viewport pass {}", renderpasses.keys.last())

        val start = System.nanoTime()

        when (viewportPass.passConfig.type) {
            RenderConfigReader.RenderpassType.geometry -> recordSceneRenderCommands(viewportPass, viewportCommandBuffer, sceneObjects, { it !is Light }, forceRerecording)
            RenderConfigReader.RenderpassType.lights -> recordSceneRenderCommands(viewportPass, viewportCommandBuffer, sceneObjects, { it is Light })
            RenderConfigReader.RenderpassType.quad -> recordPostprocessRenderCommands(viewportPass, viewportCommandBuffer)
        }

        stats?.add("VulkanRenderer.${viewportPass.name}.recordCmdBuffer", System.nanoTime() - start)

        viewportCommandBuffer.apply {
            if (submitted) {
                waitForFence()
                submitted = false
                resetFence()

                vkDev.getQueryPoolResults(timestampQueryPool, 2 * (flow.size - 1), 2, timing)

                stats?.add("Renderer.${viewportPass.name}.gpuTiming", timing[1] - timing[0])
            }
        }

        viewportPass.updateShaderParameters()

        ph.apply {
            commandBuffers[0] = viewportCommandBuffer.commandBuffer!!
            waitStages[0] = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
            signalSemaphore = semaphores[StandardSemaphores.RenderComplete]!![0]
            this.waitSemaphore = VkSemaphore(firstWaitSemaphore.get(0))
        }
        submitFrame(queue, viewportPass, viewportCommandBuffer, ph)

        updateTimings()
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

    private fun createInstance(requiredExtensions: ArrayList<String> = arrayListOf()): VkInstance {

        val appInfo = vk.ApplicationInfo {
            this.applicationName = applicationName
            engineName = "scenery"
            apiVersion = VK_MAKE_VERSION(1, 0, 73)
        }
        val additionalExts: List<String> = hub?.getWorkingHMDDisplay()?.getVulkanInstanceExtensions() ?: listOf()

        logger.debug("HMD required instance exts: ${additionalExts.joinToString()} ${additionalExts.size}")

        // allocate enough pointers for already pre-required extensions, plus HMD-required extensions, plus the debug extension
        val enabledExtensionNames = requiredExtensions + VK_EXT_DEBUG_REPORT_EXTENSION_NAME

        val enabledLayerNames = when {
            !wantsOpenGLSwapchain && validation -> defaultValidationLayers
            else -> arrayListOf()
        }

        val createInfo = vk.InstanceCreateInfo {
            applicationInfo = appInfo
            this.enabledExtensionNames = enabledExtensionNames
            this.enabledLayerNames = enabledLayerNames
        }
        return vk.createInstance(createInfo)
    }

    private fun setupDebugging(instance: VkInstance, flags: VkDebugReportFlagsEXT, callback: VkDebugReportCallbackEXT): VkDebugReportCallback {

        val dbgCreateInfo = vk.DebugReportCallbackCreateInfoEXT().also {
            it.callback = callback
            it.flags = flags
        }

        return instance createDebugReportCallbackEXT dbgCreateInfo
    }

    private fun createVertexBuffers(device: VulkanDevice, node: Node, state: VulkanObjectState): VulkanObjectState {
        val n = node as HasGeometry
        if (n.vertices.remaining() == 0) {
            return state
        }

        if (n.texcoords.remaining() == 0 && node.instanceMaster) {
            val buffer = ByteBuffer(4 * n.vertices.remaining() / n.vertexSize * n.texcoordSize)
            n.texcoords = buffer.asFloatBuffer()
        }

        val vertexAllocationBytes: Long = 4L * (n.vertices.remaining() + n.normals.remaining() + n.texcoords.remaining())
        val indexAllocationBytes: Long = 4L * n.indices.remaining()
        val fullAllocationBytes = VkDeviceSize(vertexAllocationBytes + indexAllocationBytes)

        val stridedBuffer = ByteBuffer(fullAllocationBytes.L.i)

        val fb = stridedBuffer.asFloatBuffer()
        val ib = stridedBuffer.asIntBuffer()

        state.vertexCount = n.vertices.remaining() / n.vertexSize
        logger.trace("${node.name} has ${n.vertices.remaining()} floats and ${n.texcoords.remaining() / n.texcoordSize} remaining")

        for (index in 0 until n.vertices.remaining() step 3) {
            fb.put(n.vertices.get())
            fb.put(n.vertices.get())
            fb.put(n.vertices.get())

            fb.put(n.normals.get())
            fb.put(n.normals.get())
            fb.put(n.normals.get())

            if (n.texcoords.remaining() > 0) {
                fb.put(n.texcoords.get())
                fb.put(n.texcoords.get())
            }
        }

        logger.trace("Adding {} bytes to strided buffer", n.indices.remaining() * 4)
        if (n.indices.remaining() > 0) {
            state.isIndexed = true
            ib.position(vertexAllocationBytes.toInt() / 4)

            for (index in 0 until n.indices.remaining()) {
                ib.put(n.indices.get())
            }
        }

        logger.trace("Strided buffer is now at {} bytes", stridedBuffer.remaining())

        n.vertices.flip()
        n.normals.flip()
        n.texcoords.flip()
        n.indices.flip()

        val stagingBuffer = VulkanBuffer(device,
            fullAllocationBytes,
            VkBufferUsage.TRANSFER_SRC_BIT.i,
            VkMemoryProperty.HOST_VISIBLE_BIT.i,
            wantAligned = false)

        stagingBuffer.copyFrom(stridedBuffer)

        val vertexIndexBuffer = state.vertexBuffers["vertex+index"]
        val vertexBuffer = if (vertexIndexBuffer != null && vertexIndexBuffer.size.L >= fullAllocationBytes.L) {
            logger.debug("Reusing existing vertex+index buffer for {} update", node.name)
            vertexIndexBuffer
        } else {
            logger.debug("Creating new vertex+index buffer for {} with {} bytes", node.name, fullAllocationBytes)
            geometryPool.createBuffer(fullAllocationBytes)
        }

        logger.debug("Using VulkanBuffer {} for vertex+index storage, offset={}", vertexBuffer.vulkanBuffer.asHexString, vertexBuffer.bufferOffset)

        logger.debug("Initiating copy with 0->${vertexBuffer.bufferOffset}, size=${fullAllocationBytes.L}")
        val copyRegion = vk.BufferCopy {
            dstOffset = vertexBuffer.bufferOffset
            size = fullAllocationBytes
        }
        vkDev.newCommandBuffer(commandPools.Standard)
            .record {
                copyBuffer(stagingBuffer.vulkanBuffer, vertexBuffer.vulkanBuffer, copyRegion)
            }
            .submit(queue).deallocate()

        state.vertexBuffers.put("vertex+index", vertexBuffer)?.run {
            // check if vertex buffer has been replaced, if yes, close the old one
            if (this != vertexBuffer) {
                close()
            }
        }
        state.indexOffset = VkDeviceSize(vertexBuffer.bufferOffset.L + vertexAllocationBytes)
        state.indexCount = n.indices.remaining()

        stridedBuffer.free()
        stagingBuffer.close()

        return state
    }

    private fun updateInstanceBuffer(device: VulkanDevice, parentNode: Node, state: VulkanObjectState): VulkanObjectState {
        logger.trace("Updating instance buffer for ${parentNode.name}")

        if (parentNode.instances.isEmpty()) {
            logger.debug("$parentNode has no child instances attached, returning.")
            return state
        }

        // first we create a fake UBO to gauge the size of the needed properties
        val ubo = VulkanUBO(device)
        ubo.fromInstance(parentNode.instances.first())

        val instanceBufferSize = VkDeviceSize(ubo.getSize() * parentNode.instances.size.L)

        val instanceStagingBuffer = state.vertexBuffers["instanceStaging"]
        val stagingBuffer = if (instanceStagingBuffer != null && instanceStagingBuffer.size.L >= instanceBufferSize.L) {
            instanceStagingBuffer
        } else {
            logger.debug("Creating new staging buffer")
            val buffer = VulkanBuffer(device,
                instanceBufferSize,
                VkBufferUsage.TRANSFER_SRC_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT.i,
                wantAligned = true)

            state.vertexBuffers["instanceStaging"] = buffer
            buffer
        }

        ubo.updateBackingBuffer(stagingBuffer)
        ubo.createUniformBuffer()

        val index = AtomicInteger(0)
        parentNode.instances.parallelStream().forEach { node ->
            node.needsUpdate = true
            node.needsUpdateWorld = true
            node.updateWorld(true, false)

            node.metadata.getOrPut("instanceBufferView") {
                stagingBuffer.stagingBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            }.run {
                val buffer = this as? ByteBuffer ?: return@run

                ubo.populateParallel(buffer, offset = index.getAndIncrement() * ubo.getSize() * 1L, elements = node.instancedProperties)
            }
        }

        stagingBuffer.stagingBuffer.position(parentNode.instances.size * ubo.getSize())
        stagingBuffer.copyFromStagingBuffer()

        val existingInstanceBuffer = state.vertexBuffers["instance"]
        val instanceBuffer = if (existingInstanceBuffer != null && existingInstanceBuffer.size.L >= instanceBufferSize.L) {
            existingInstanceBuffer
        } else {
            logger.debug("Instance buffer for ${parentNode.name} needs to be reallocated due to insufficient size ($instanceBufferSize vs ${state.vertexBuffers["instance"]?.size
                ?: "<not allocated yet>"})")
            state.vertexBuffers["instance"]?.close()

            val buffer = VulkanBuffer(device,
                instanceBufferSize,
                VkBufferUsage.VERTEX_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT,
                VkMemoryProperty.DEVICE_LOCAL_BIT.i,
                wantAligned = true)

            state.vertexBuffers["instance"] = buffer
            buffer
        }

        vkDev.newCommandBuffer(commandPools.Standard)
            .record {
                val copyRegion = vk.BufferCopy { size = instanceBufferSize }
                copyBuffer(stagingBuffer.vulkanBuffer, instanceBuffer.vulkanBuffer, copyRegion)
            }
            .submit(queue).deallocate()

        state.instanceCount = parentNode.instances.size

        return state
    }

    private fun createDescriptorPool(): VkDescriptorPool {

        // We need to tell the API the number of max. requested descriptors per type
        val typeCounts = vk.DescriptorPoolSize(
            VkDescriptorType.COMBINED_IMAGE_SAMPLER, MAX_TEXTURES,
            VkDescriptorType.UNIFORM_BUFFER_DYNAMIC, MAX_UBOS,
            VkDescriptorType.INPUT_ATTACHMENT, MAX_INPUT_ATTACHMENTS,
            VkDescriptorType.UNIFORM_BUFFER, MAX_UBOS)

        // Create the global descriptor pool
        // All descriptors used in this example are allocated from this pool
        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo {
            poolSizes = typeCounts
            maxSets = MAX_TEXTURES + MAX_UBOS + MAX_INPUT_ATTACHMENTS + MAX_UBOS // Set the max. number of sets that can be requested
            flags = VkDescriptorPoolCreate.FREE_DESCRIPTOR_SET_BIT.i
        }
        return vkDev createDescriptorPool descriptorPoolInfo
    }

    private fun prepareDefaultBuffers(device: VulkanDevice, bufferStorage: ConcurrentHashMap<String, VulkanBuffer>) {
        logger.debug("Creating buffers")

        val usage = VkBufferUsage.UNIFORM_BUFFER_BIT.i
        val memProp = VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT
        val aligned = true
        bufferStorage["UBOBuffer"] = VulkanBuffer(device, VkDeviceSize(512 * 1024 * 10), usage, memProp, aligned)
        logger.debug("Created UBO buffer")

        bufferStorage["LightParametersBuffer"] = VulkanBuffer(device, VkDeviceSize(512 * 1024 * 10), usage, memProp, aligned)
        logger.debug("Created light buffer")

        bufferStorage["VRParametersBuffer"] = VulkanBuffer(device, VkDeviceSize(256 * 10), usage, memProp, aligned)
        logger.debug("Created VRP buffer")

        bufferStorage["ShaderPropertyBuffer"] = VulkanBuffer(device, VkDeviceSize(1024 * 1024), usage, memProp, aligned)
        logger.debug("Created all buffers")
    }

    private fun Node.rendererMetadata(): VulkanObjectState? {
        return metadata["VulkanRenderer"] as? VulkanObjectState
    }

    private fun recordSceneRenderCommands(pass: VulkanRenderpass,
                                          commandBuffer: VulkanCommandBuffer, sceneObjects: Deferred<List<Node>>,
                                          customNodeFilter: ((Node) -> Boolean)? = null, forceRerecording: Boolean = false) = runBlocking {
        val target = pass.getOutput()

        logger.trace("Initialising recording of scene command buffer for {}/{} ({} attachments)", pass.name, target, target.attachments.count())

        pass.vulkanMetadata.renderPassBeginInfo.apply {
            renderPass = target.renderPass
            framebuffer = target.framebuffer
            renderArea = pass.vulkanMetadata.renderArea
            clearValues = pass.vulkanMetadata.clearValues
        }
        val renderOrderList = ArrayList<Node>(pass.vulkanMetadata.renderLists[commandBuffer]?.size ?: 512)

        // here we discover all the nodes which are relevant for this pass,
        // e.g. which have the same transparency settings as the pass,
        // and filter according to any custom filters applicable to this pass
        // (e.g. to discern geometry from lighting passes)
        sceneObjects.await().filter { customNodeFilter?.invoke(it) ?: true }.forEach { n ->
            n.rendererMetadata()?.let {
                if (!((pass.passConfig.renderOpaque && n.material.blending.transparent && pass.passConfig.renderOpaque != pass.passConfig.renderTransparent) ||
                        (pass.passConfig.renderTransparent && !n.material.blending.transparent && pass.passConfig.renderOpaque != pass.passConfig.renderTransparent)))
                    renderOrderList += n
            }
        }

        // if the pass' metadata does not contain a command buffer,
        // OR the cached command buffer does not contain the same nodes in the same order,
        // OR re-recording is forced due to node changes, the buffer will be re-recorded.
        // Furthermore, all sibling command buffers for this pass will be marked stale, thus
        // also forcing their re-recording.
        if (!pass.vulkanMetadata.renderLists.containsKey(commandBuffer)
            || !renderOrderList.toTypedArray().contentDeepEquals(pass.vulkanMetadata.renderLists[commandBuffer]!!)
            || forceRerecording) {

            pass.vulkanMetadata.renderLists[commandBuffer] = renderOrderList.toTypedArray()
            pass.vulkanMetadata.renderLists.keys.forEach { it.stale = true }

            // if we are in a VR pass, invalidate passes for both eyes to prevent one of them housing stale data
            if (renderConfig.stereoEnabled && (pass.name.contains("Left") || pass.name.contains("Right"))) {
                val passLeft = if (pass.name.contains("Left")) {
                    pass.name
                } else {
                    pass.name.substringBefore("Right") + "Left"
                }

                val passRight = if (pass.name.contains("Right")) {
                    pass.name
                } else {
                    pass.name.substringBefore("Left") + "Right"
                }

                renderpasses[passLeft]?.vulkanMetadata?.renderLists?.keys?.forEach { it.stale = true }
                renderpasses[passRight]?.vulkanMetadata?.renderLists?.keys?.forEach { it.stale = true }
            }
        }

        // If the command buffer is not stale, though, we keep the cached one and return. This
        // can buy quite a bit of performance.
        if (!commandBuffer.stale && commandBuffer.commandBuffer != null) {
            return@runBlocking
        }

        logger.debug("Recording scene command buffer $commandBuffer for pass ${pass.name}...")

        // command buffer cannot be null here anymore, otherwise this is clearly in error
        commandBuffer.prepare(commandPools.Render).record {

            writeTimestamp(VkPipelineStage.BOTTOM_OF_PIPE_BIT.i, timestampQueryPool, 2 * renderpasses.values.indexOf(pass))

            if (pass.passConfig.blitInputs) {

                val imageBlit = vk.ImageBlit()

                for ((name, input) in pass.inputs) {
                    val attachmentList = if (name.contains(".")) {
                        input.attachments.filter { it.key == name.substringAfter(".") }
                    } else {
                        input.attachments
                    }

                    for ((_, inputAttachment) in attachmentList) {

                        val type = when (inputAttachment.type) {
                            VulkanFramebuffer.VulkanFramebufferType.COLOR_ATTACHMENT -> VkImageAspect.COLOR_BIT
                            VulkanFramebuffer.VulkanFramebufferType.DEPTH_ATTACHMENT -> VkImageAspect.DEPTH_BIT
                        }.i

                        // return to use() if no output with the correct attachment type is found
                        val outputAttachment = pass.getOutput().attachments.values.find { it.type == inputAttachment.type }
                        if (outputAttachment == null) {
                            logger.warn("Didn't find matching attachment for $name of type ${inputAttachment.type}")
                        } else {

                            val outputAspectSrcType = when (outputAttachment.type) {
                                VulkanFramebuffer.VulkanFramebufferType.COLOR_ATTACHMENT -> VkImageLayout.SHADER_READ_ONLY_OPTIMAL
                                VulkanFramebuffer.VulkanFramebufferType.DEPTH_ATTACHMENT -> VkImageLayout.SHADER_READ_ONLY_OPTIMAL
                            }

                            val outputAspectDstType = when (outputAttachment.type) {
                                VulkanFramebuffer.VulkanFramebufferType.COLOR_ATTACHMENT -> VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
                                VulkanFramebuffer.VulkanFramebufferType.DEPTH_ATTACHMENT -> VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL
                            }

                            val inputAspectType = when (inputAttachment.type) {
                                VulkanFramebuffer.VulkanFramebufferType.COLOR_ATTACHMENT -> VkImageLayout.SHADER_READ_ONLY_OPTIMAL
                                VulkanFramebuffer.VulkanFramebufferType.DEPTH_ATTACHMENT -> VkImageLayout.SHADER_READ_ONLY_OPTIMAL
                            }

                            val outputDstStage = when (outputAttachment.type) {
                                VulkanFramebuffer.VulkanFramebufferType.COLOR_ATTACHMENT -> VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT
                                VulkanFramebuffer.VulkanFramebufferType.DEPTH_ATTACHMENT -> VkPipelineStage.EARLY_FRAGMENT_TESTS_BIT
                            }.i

                            val offsetX = (input.width * pass.passConfig.viewportOffset.first).toInt()
                            val offsetY = (input.height * pass.passConfig.viewportOffset.second).toInt()

                            val sizeX = offsetX + (input.width * pass.passConfig.viewportSize.first).toInt()
                            val sizeY = offsetY + (input.height * pass.passConfig.viewportSize.second).toInt()

                            imageBlit.apply {
                                srcSubresource().set(type.i, 0, 0, 1)
                                srcOffsets(0).set(offsetX, offsetY, 0)
                                srcOffsets(1).set(sizeX, sizeY, 1)

                                dstSubresource().set(type.i, 0, 0, 1)
                                dstOffsets(0).set(offsetX, offsetY, 0)
                                dstOffsets(1).set(sizeX, sizeY, 1)
                            }
                            val transitionBuffer = this

                            val subresourceRange = vk.ImageSubresourceRange {
                                aspectMask = type
                                baseMipLevel = 0
                                levelCount = 1
                                baseArrayLayer = 0
                                layerCount = 1
                            }
                            // transition source attachment
                            VulkanTexture.transitionLayout(inputAttachment.image,
                                VkImageLayout.SHADER_READ_ONLY_OPTIMAL,
                                VkImageLayout.TRANSFER_SRC_OPTIMAL,
                                subresourceRange = subresourceRange,
                                commandBuffer = transitionBuffer,
                                srcStage = VkPipelineStage.FRAGMENT_SHADER_BIT.i,
                                dstStage = VkPipelineStage.TRANSFER_BIT.i)

                            // transition destination attachment
                            VulkanTexture.transitionLayout(outputAttachment.image,
                                inputAspectType,
                                VkImageLayout.TRANSFER_DST_OPTIMAL,
                                subresourceRange = subresourceRange,
                                commandBuffer = transitionBuffer,
                                srcStage = VkPipelineStage.FRAGMENT_SHADER_BIT.i,
                                dstStage = VkPipelineStage.TRANSFER_BIT.i)

                            blitImage(
                                inputAttachment.image, VkImageLayout.TRANSFER_SRC_OPTIMAL,
                                outputAttachment.image, VkImageLayout.TRANSFER_DST_OPTIMAL,
                                imageBlit, VkFilter.NEAREST)

                            // transition destination attachment back to attachment
                            VulkanTexture.transitionLayout(outputAttachment.image,
                                VkImageLayout.TRANSFER_DST_OPTIMAL,
                                outputAspectDstType,
                                subresourceRange = subresourceRange,
                                commandBuffer = transitionBuffer,
                                srcStage = VkPipelineStage.TRANSFER_BIT.i,
                                dstStage = outputDstStage)

                            // transition source attachment back to shader read-only
                            VulkanTexture.transitionLayout(inputAttachment.image,
                                VkImageLayout.TRANSFER_SRC_OPTIMAL,
                                outputAspectSrcType,
                                subresourceRange = subresourceRange,
                                commandBuffer = transitionBuffer,
                                srcStage = VkPipelineStage.TRANSFER_BIT.i,
                                dstStage = VkPipelineStage.VERTEX_SHADER_BIT.i)
                        }
                    }
                }
            }

            beginRenderPass(pass.vulkanMetadata.renderPassBeginInfo, VkSubpassContents.INLINE)

            setViewport(pass.vulkanMetadata.viewport)
            setScissor(pass.vulkanMetadata.scissor)

            // allocate more vertexBufferOffsets than needed, set limit lateron
            pass.vulkanMetadata.uboOffsets.limit(16)
            (0 until pass.vulkanMetadata.uboOffsets.limit()).forEach { pass.vulkanMetadata.uboOffsets.put(it, 0) }

            renderOrderList.forEach drawLoop@{ node ->
                val s = node.rendererMetadata() ?: return@drawLoop

                // instanced nodes will not be drawn directly, but only the master node.
                // nodes with no vertices will also not be drawn.
                if (node.instanceOf != null || s.vertexCount == 0) {
                    return@drawLoop
                }

                // return if we are on a opaque pass, but the node requires transparency.
                if (pass.passConfig.renderOpaque && node.material.blending.transparent && pass.passConfig.renderOpaque != pass.passConfig.renderTransparent) {
                    return@drawLoop
                }

                // return if we are on a transparency pass, but the node is only opaque.
                if (pass.passConfig.renderTransparent && !node.material.blending.transparent && pass.passConfig.renderOpaque != pass.passConfig.renderTransparent) {
                    return@drawLoop
                }

                val vertexIndexBuffer = s.vertexBuffers["vertex+index"]
                val instanceBuffer = s.vertexBuffers["instance"]

                if (vertexIndexBuffer == null) {
                    logger.error("Vertex+Index buffer not initialiazed")
                    return@drawLoop
                }

                logger.trace("{} - Rendering {}, vertex+index buffer={}...", pass.name, node.name, vertexIndexBuffer.vulkanBuffer.asHexString)
//                if(rerecordingCauses.contains(node.name)) {
//                    logger.debug("Using pipeline ${pass.getActivePipeline(node)} for re-recording")
//                }
                val p = pass.getActivePipeline(node)
                val pipeline = p.getPipelineForGeometryType((node as HasGeometry).geometryType)
                val specs = p.orderedDescriptorSpecs()

                logger.trace("node {} has: {} / pipeline needs: {}", node.name, s.UBOs.keys.joinToString(), specs.joinToString { it.key })

                pass.vulkanMetadata.apply {
                    descriptorSets.rewind()
                    uboOffsets.rewind()

                    vertexBufferOffsets[0] = vertexIndexBuffer.bufferOffset.L
                    vertexBuffers[0] = vertexIndexBuffer.vulkanBuffer.L

                    vertexBufferOffsets.limit(1)
                    vertexBuffers.limit(1)

                    if (node.instanceMaster && instanceBuffer != null) {
                        vertexBuffers.limit(2)
                        vertexBufferOffsets.limit(2)

                        vertexBufferOffsets[1] = 0
                        vertexBuffers[1] = instanceBuffer.vulkanBuffer.L
                    }
                }

                val sets = specs.mapNotNull { (name, _) ->
                    val ds = when {
                        name == "VRParameters" -> {
                            DescriptorSet.setOrNull(descriptorSets["VRParameters"], setName = "VRParameters")
                        }

                        name == "LightParameters" -> {
                            DescriptorSet.setOrNull(descriptorSets["LightParameters"], setName = "LightParameters")
                        }

                        name == "ObjectTextures" -> {
                            DescriptorSet.setOrNull(s.textureDescriptorSet, setName = "ObjectTextures")
                        }

                        name.startsWith("Inputs") -> {
                            DescriptorSet.setOrNull(pass.descriptorSets["input-${pass.name}-${name.substringAfter("-")}"], setName = "Inputs")
                        }

                        name == "ShaderParameters" -> {
                            DescriptorSet.setOrNull(pass.descriptorSets["ShaderParameters-${pass.name}"], setName = "ShaderParameters")
                        }

                        else -> when {
                            s.UBOs.containsKey(name) -> DescriptorSet.DynamicSet(s.UBOs[name]!!.first, offset = s.UBOs[name]!!.second.offsets[0], setName = name)
                            s.UBOs.containsKey("${pass.name}-$name") -> DescriptorSet.DynamicSet(s.UBOs["${pass.name}-$name"]!!.first, offset = s.UBOs["${pass.name}-$name"]!!.second.offsets[0], setName = name)
                            else -> DescriptorSet.None
                        }
                    }

                    if (ds == null) {
                        logger.error("Internal consistency error for node ${node.name}: Descriptor set $name not found in renderpass, skipping node for rendering.")
                        return@drawLoop
                    }

                    ds
                }
                logger.debug("${node.name} requires DS ${specs.joinToString { "${it.key}, " }}")

                val requiredSets = sets.filter { it !is DescriptorSet.None }.map { it.id.L }.toLongArray()
                if (pass.vulkanMetadata.descriptorSets.capacity() < requiredSets.size) {
                    logger.debug("Reallocating descriptor set storage")
                    pass.vulkanMetadata.descriptorSets.free()
                    pass.vulkanMetadata.descriptorSets = memAllocLong(requiredSets.size)
                }

                pass.vulkanMetadata.descriptorSets.position(0)
                pass.vulkanMetadata.descriptorSets.limit(pass.vulkanMetadata.descriptorSets.capacity())
                pass.vulkanMetadata.descriptorSets.put(requiredSets)
                pass.vulkanMetadata.descriptorSets.flip()

                pass.vulkanMetadata.uboOffsets.position(0)
                pass.vulkanMetadata.uboOffsets.limit(pass.vulkanMetadata.uboOffsets.capacity())
                pass.vulkanMetadata.uboOffsets.put(sets.filter { it is DescriptorSet.DynamicSet }.map { (it as DescriptorSet.DynamicSet).offset }.toIntArray())
                pass.vulkanMetadata.uboOffsets.flip()

                if (p.pushConstantSpecs.containsKey("currentEye")) {
                    pushConstants(pipeline.layout, VkShaderStage.ALL.i, 0, pass.vulkanMetadata.eye)
                }

                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipeline.pipeline)
                if (pass.vulkanMetadata.descriptorSets.limit() > 0) {
                    bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipeline.layout, pass.vulkanMetadata.descriptorSets, pass.vulkanMetadata.uboOffsets)
                }
                bindVertexBuffers(0, pass.vulkanMetadata.vertexBuffers, pass.vulkanMetadata.vertexBufferOffsets)

                logger.debug("${pass.name}: now drawing {}, {} DS bound, {} textures, {} vertices, {} indices, {} instances", node.name, pass.vulkanMetadata.descriptorSets.limit(), s.textures.count(), s.vertexCount, s.indexCount, s.instanceCount)

                if (s.isIndexed) {
                    bindIndexBuffer(VkBuffer(pass.vulkanMetadata.vertexBuffers[0]), s.indexOffset, VkIndexType.UINT32)
                    drawIndexed(s.indexCount, s.instanceCount, 0, 0, 0)
                } else {
                    draw(s.vertexCount, s.instanceCount, 0, 0)
                }
            }

            endRenderPass()

            writeTimestamp(VkPipelineStage.BOTTOM_OF_PIPE_BIT.i, timestampQueryPool, 2 * renderpasses.values.indexOf(pass) + 1)

            // finish command buffer recording by marking this buffer non-stale
            commandBuffer.stale = false
        }
    }

    private fun recordPostprocessRenderCommands(pass: VulkanRenderpass, commandBuffer: VulkanCommandBuffer) {
        val target = pass.getOutput()

        logger.trace("Creating postprocessing command buffer for {}/{} ({} attachments)", pass.name, target, target.attachments.count())

        pass.vulkanMetadata.renderPassBeginInfo.apply {
            renderPass = target.renderPass
            framebuffer = target.framebuffer
            renderArea = pass.vulkanMetadata.renderArea
            clearValues = pass.vulkanMetadata.clearValues
        }
        if (!commandBuffer.stale) {
            return
        }

        // prepare command buffer and start recording
        commandBuffer.prepare(commandPools.Render).record {

            val meta = pass.vulkanMetadata
            writeTimestamp(VkPipelineStage.BOTTOM_OF_PIPE_BIT.i, timestampQueryPool, 2 * renderpasses.values.indexOf(pass))
            beginRenderPass(meta.renderPassBeginInfo, VkSubpassContents.INLINE)

            setViewport(meta.viewport)
            setScissor(meta.scissor)

            val pipeline = pass.getDefaultPipeline()
            val vulkanPipeline = pipeline.getPipelineForGeometryType(GeometryType.TRIANGLES)

            if (meta.descriptorSets.capacity() != pipeline.descriptorSpecs.count()) {
                meta.descriptorSets.free()
                meta.descriptorSets = memAllocLong(pipeline.descriptorSpecs.count())
            }

            // allocate more vertexBufferOffsets than needed, set limit lateron
            meta.uboOffsets.position(0) // TODO
            meta.uboOffsets.limit(16)
            meta.uboOffsets.fill(0)

            if (logger.isDebugEnabled) {
                logger.debug("${pass.name}: descriptor sets are {}", pass.descriptorSets.keys.joinToString())
                logger.debug("pipeline provides {}", pipeline.descriptorSpecs.keys.joinToString())
            }

            // set the required descriptor sets for this render pass
            meta.setRequiredDescriptorSetsPostprocess(pass, pipeline)

            if (pipeline.pushConstantSpecs.containsKey("currentEye")) {
                pushConstants(vulkanPipeline.layout, VkShaderStage.ALL.i, 0, meta.eye)
            }

            bindPipeline(VkPipelineBindPoint.GRAPHICS, vulkanPipeline.pipeline)
            if (meta.descriptorSets.limit() > 0) {
                logger.debug("Binding ${meta.descriptorSets.limit()} descriptor sets with ${meta.uboOffsets.limit()} required offsets")
                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, vulkanPipeline.layout, pass.vulkanMetadata.descriptorSets, pass.vulkanMetadata.uboOffsets)
            }

            draw(3, 1, 0, 0)

            endRenderPass()
            writeTimestamp(VkPipelineStage.BOTTOM_OF_PIPE_BIT.i, timestampQueryPool, 2 * renderpasses.values.indexOf(pass) + 1)

            commandBuffer.stale = false
        }
    }

    private fun VulkanRenderpass.VulkanMetadata.setRequiredDescriptorSetsPostprocess(pass: VulkanRenderpass, pipeline: VulkanPipeline): Int {
        var requiredDynamicOffsets = 0
        logger.trace("Ubo position: {}", this.uboOffsets.position())

        pipeline.descriptorSpecs.entries.sortedBy { it.value.set }.forEachIndexed { i, (name, spec) ->
            logger.trace("Looking at {}, set={}, binding={}...", name, spec.set, spec.binding)
            val dsName = when {
                name.startsWith("ShaderParameters") -> "ShaderParameters-${pass.name}"
                name.startsWith("Inputs") -> "input-${pass.name}-${spec.set}"
                name.startsWith("Matrices") -> {
                    val offsets = sceneUBOs.first().rendererMetadata()!!.UBOs["Matrices"]!!.second.offsets
                    this.uboOffsets.put(offsets)
                    requiredDynamicOffsets += 3

                    "Matrices"
                }
                else -> name
            }

            val set = if (dsName == "Matrices" || dsName == "LightParameters" || dsName == "VRParameters") {
                this@VulkanRenderer.descriptorSets[dsName]
            } else {
                pass.descriptorSets[dsName]
            }

            if (set != null) {
                logger.trace("Adding DS#{} for {} to required pipeline DSs", i, dsName)
                this.descriptorSets.put(i, set.L)
            } else {
                logger.error("DS for {} not found!", dsName)
            }
        }

        logger.trace("{}: Requires {} dynamic offsets", pass.name, requiredDynamicOffsets)
        this.uboOffsets.flip()

        return requiredDynamicOffsets
    }

    private fun updateInstanceBuffers(sceneObjects: Deferred<List<Node>>) = runBlocking {
        val instanceMasters = sceneObjects.await().filter { it.instanceMaster }

        instanceMasters.forEach { parent ->
            updateInstanceBuffer(device, parent, parent.rendererMetadata()!!)
        }
    }

    fun GLMatrix.applyVulkanCoordinateSystem(): GLMatrix {
        val m = vulkanProjectionFix.clone()
        m.mult(this)

        return m
    }

    private fun Display.wantsVR(): Display? {
        return takeIf { settings.get("vr.Active") }
    }

    private fun getDescriptorCache(): ConcurrentHashMap<String, VkDescriptorSet> {
        @Suppress("UNCHECKED_CAST")
        return scene.metadata.getOrPut("DescriptorCache") {
            ConcurrentHashMap<String, VkDescriptorSet>()
        } as? ConcurrentHashMap<String, VkDescriptorSet>
            ?: throw IllegalStateException("Could not retrieve descriptor cache from scene")
    }

    private fun updateDefaultUBOs(device: VulkanDevice): Boolean = runBlocking {
        if (shouldClose) {
            return@runBlocking false
        }

        logger.trace("Updating default UBOs for {}", device)
        // find observer, if none, return
        val cam = scene.findObserver() ?: return@runBlocking false
        // sticky boolean
        var updated: Boolean by StickyBoolean(initial = false)

        if (!cam.lock.tryLock()) {
            return@runBlocking false
        }

        val hmd = hub?.getWorkingHMDDisplay()?.wantsVR()

        cam.view = cam.getTransformation()
        cam.updateWorld(true, false)

        buffers["VRParametersBuffer"]!!.reset()
        val vrUbo = defaultUBOs["VRParameters"]!!
        vrUbo.add("projection0", {
            (hmd?.getEyeProjection(0, cam.nearPlaneDistance, cam.farPlaneDistance)
                ?: cam.projection).applyVulkanCoordinateSystem()
        })
        vrUbo.add("projection1", {
            (hmd?.getEyeProjection(1, cam.nearPlaneDistance, cam.farPlaneDistance)
                ?: cam.projection).applyVulkanCoordinateSystem()
        })
        vrUbo.add("inverseProjection0", {
            (hmd?.getEyeProjection(0, cam.nearPlaneDistance, cam.farPlaneDistance)
                ?: cam.projection).applyVulkanCoordinateSystem().inverse
        })
        vrUbo.add("inverseProjection1", {
            (hmd?.getEyeProjection(1, cam.nearPlaneDistance, cam.farPlaneDistance)
                ?: cam.projection).applyVulkanCoordinateSystem().inverse
        })
        vrUbo.add("headShift", { hmd?.getHeadToEyeTransform(0) ?: GLMatrix.getIdentity() })
        vrUbo.add("IPD", { hmd?.getIPD() ?: 0.05f })
        vrUbo.add("stereoEnabled", { renderConfig.stereoEnabled.i })

        updated = vrUbo.populate()

        buffers["UBOBuffer"]!!.reset()
        buffers["ShaderPropertyBuffer"]!!.reset()

        sceneUBOs.forEach { node ->
            node.lock.withLock {
                var nodeUpdated: Boolean by StickyBoolean(initial = false)

                if (!node.metadata.containsKey("VulkanRenderer") || node.instanceOf != null) {
                    return@withLock
                }

                val s = node.rendererMetadata() ?: return@forEach

                val ubo = s.UBOs["Matrices"]!!.second

                node.updateWorld(true, false)

                ubo.offsets.limit(1)

                var bufferOffset = ubo.backingBuffer!!.advance()
                ubo.offsets.put(0, bufferOffset.i)
                ubo.offsets.limit(1)

//                node.projection.copyFrom(cam.projection.applyVulkanCoordinateSystem())

                node.view.copyFrom(cam.view)

                nodeUpdated = ubo.populate(offset = bufferOffset)

                val materialUbo = s.UBOs["MaterialProperties"]!!.second
                bufferOffset = ubo.backingBuffer!!.advance()
                materialUbo.offsets.put(0, bufferOffset.i)
                materialUbo.offsets.limit(1)

                nodeUpdated = materialUbo.populate(offset = bufferOffset)

                s.UBOs.filter { it.key.contains("ShaderProperties") && it.value.second.memberCount() > 0 }.forEach {
                    //                if(s.requiredDescriptorSets.keys.any { it.contains("ShaderProperties") }) {
                    val propertyUbo = it.value.second
                    val offset = propertyUbo.backingBuffer!!.advance()
                    nodeUpdated = propertyUbo.populate(offset = offset)
                    propertyUbo.offsets.put(0, offset.i)
                    propertyUbo.offsets.limit(1)
                }

                if (nodeUpdated) {
                    GlobalScope.launch { node.getScene()?.onNodePropertiesChanged?.forEach { it.value.invoke(node) } }
                }

                updated = nodeUpdated
            }
        }

        buffers["UBOBuffer"]!!.copyFromStagingBuffer()

        val lightUbo = defaultUBOs["LightParameters"]!!
        lightUbo.add("ViewMatrix0", { cam.getTransformationForEye(0) })
        lightUbo.add("ViewMatrix1", { cam.getTransformationForEye(1) })
        lightUbo.add("InverseViewMatrix0", { cam.getTransformationForEye(0).inverse })
        lightUbo.add("InverseViewMatrix1", { cam.getTransformationForEye(1).inverse })
        lightUbo.add("ProjectionMatrix", { cam.projection.applyVulkanCoordinateSystem() })
        lightUbo.add("InverseProjectionMatrix", { cam.projection.applyVulkanCoordinateSystem().inverse })
        lightUbo.add("CamPosition", { cam.position })

        updated = lightUbo.populate()

        buffers["ShaderPropertyBuffer"]!!.copyFromStagingBuffer()

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
            if (it.toLowerCase().contains("debug")) {
                try {
                    val property = settings.get<Int>(it).toggle()
                    settings.set(it, property)

                } catch (e: Exception) {
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
        if (!initialized) {
            return
        }

        initialized = false

        logger.info("Renderer teardown started.")
        queue.waitIdle()

        logger.debug("Cleaning texture cache...")
        textureCache.forEach {
            logger.debug("Cleaning ${it.key}...")
            it.value.close()
        }

        logger.debug("Closing nodes...")
        scene.discover(scene, { true }).forEach {
            destroyNode(it)
        }
        scene.metadata.remove("DescriptorCache")
        scene.initialized = false

        logger.debug("Closing buffers...")
        buffers.forEach { _, vulkanBuffer -> vulkanBuffer.close() }

        logger.debug("Closing vertex descriptors ...")
        vertexDescriptors.forEach {
            logger.debug("Closing vertex descriptor ${it.key}...")

            it.value.attributeDescription?.free()
            it.value.bindingDescription?.free()

            it.value.state.free()
        }

        logger.debug("Closing descriptor sets and pools...")
        vkDev destroyDescriptorSetLayouts descriptorSetLayouts.values
        vkDev destroyDescriptorPool descriptorPool

        logger.debug("Closing command buffers...")
        ph.commandBuffers.free()
        ph.waitStages.free()

        if (timestampQueryPool.isValid) {
            logger.debug("Closing query pools...")
            vkDev destroyQueryPool timestampQueryPool
        }

        semaphores.values.forEach { semaphores -> vkDev destroySemaphores semaphores }

        firstWaitSemaphore.free()
        semaphoreCreateInfo.free()

        logger.debug("Closing swapchain...")

        swapchain.close()

        logger.debug("Closing renderpasses...")
        renderpasses.forEach { _, vulkanRenderpass -> vulkanRenderpass.close() }

        logger.debug("Clearing shader module cache...")
        VulkanShaderModule.clearCache()

        logger.debug("Closing command pools...")
        vkDev.destroyCommandPools(commandPools.Render, commandPools.Compute, commandPools.Standard)

        vkDev destroyPipelineCache pipelineCache

        if (validation) {
            instance destroyDebugReportCallbackEXT debugCallbackHandle
        }

        debugCallback.free()

        logger.debug("Closing device $device...")
        device.close()

        logger.debug("Closing instance...")
        instance.destroy()

        logger.info("Renderer teardown complete.")
    }

    override fun reshape(newWidth: Int, newHeight: Int) {}

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

            logger.debug("Setting $setting: ${settings.get<Any>(setting)} -> $value")
            settings.set(setting, value)
        }

        if (renderConfig.qualitySettings.isNotEmpty()) {
            logger.info("Setting rendering quality to $quality")

            renderConfig.qualitySettings[quality]?.forEach { setting ->
                if (setting.key.endsWith(".shaders") && setting.value is List<*>) {
                    val pass = setting.key.substringBeforeLast(".shaders")
                    @Suppress("UNCHECKED_CAST") val shaders = setting.value as? List<String> ?: return@forEach

                    renderConfig.renderpasses[pass]?.shaders = shaders

                    @Suppress("SENSELESS_COMPARISON")
                    if (swapchainRecreator != null) {
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
