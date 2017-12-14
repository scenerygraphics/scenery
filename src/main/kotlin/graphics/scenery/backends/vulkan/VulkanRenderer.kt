package graphics.scenery.backends.vulkan

import cleargl.GLMatrix
import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.*
import graphics.scenery.fonts.SDFFontAtlas
import graphics.scenery.spirvcrossj.Loader
import graphics.scenery.spirvcrossj.libspirvcrossj
import graphics.scenery.utils.*
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions
import org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.jemalloc.JEmalloc.je_calloc
import org.lwjgl.system.jemalloc.JEmalloc.je_free
import org.lwjgl.system.jemalloc.JEmalloc.je_malloc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugReport.*
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.VK10.*
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.imageio.ImageIO
import kotlin.concurrent.thread
import kotlin.concurrent.withLock


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

open class VulkanRenderer(hub: Hub,
                          applicationName: String,
                          scene: Scene,
                          windowWidth: Int,
                          windowHeight: Int,
                          override final var embedIn: SceneryPanel? = null,
                          renderConfigFile: String = System.getProperty("scenery.Renderer.Config", "DeferredShading.yml")) : Renderer(), AutoCloseable {

    protected val logger by LazyLogger()

    // helper classes
    data class PresentHelpers(
        var signalSemaphore: LongBuffer = memAllocLong(1),
        var waitSemaphore: LongBuffer = memAllocLong(1),
        var commandBuffers: PointerBuffer = memAllocPointer(1),
        var waitStages: IntBuffer = memAllocInt(1)
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
        var state: VkPipelineVertexInputStateCreateInfo,
        var attributeDescription: VkVertexInputAttributeDescription.Buffer?,
        var bindingDescription: VkVertexInputBindingDescription.Buffer?
    )

    data class CommandPools(
        var Standard: Long = -1L,
        var Render: Long = -1L,
        var Compute: Long = -1L
    )

    data class DeviceAndGraphicsQueueFamily(
        val device: VkDevice? = null,
        val graphicsQueue: Int = 0,
        val computeQueue: Int = 0,
        val presentQueue: Int = 0,
        val memoryProperties: VkPhysicalDeviceMemoryProperties? = null
    )

    class Pipeline {
        internal var pipeline: Long = 0
        internal var layout: Long = 0
    }

    sealed class DescriptorSet(val id: Long = 0L, val name: String = "") {
        object None: DescriptorSet(0L)
        data class Set(val setId: Long, val setName: String = "") : DescriptorSet(setId, setName)
        data class DynamicSet(val setId: Long, val offset: Int, val setName: String = "") : DescriptorSet(setId, setName)
    }

    private val lateResizeInitializers = ConcurrentHashMap<Node, () -> Any>()

    inner class SwapchainRecreator {
        var mustRecreate = true
        private val lock = ReentrantLock()

        @Synchronized fun recreate() {
            if(lock.tryLock()) {
                logger.info("Recreating Swapchain at frame $frames")
                // create new swapchain with changed surface parameters
                vkQueueWaitIdle(queue)

                with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
                    // Create the swapchain (this will also add a memory barrier to initialize the framebuffer images)

                    swapchain?.create(oldSwapchain = swapchain)

                    this.endCommandBuffer(device, commandPools.Standard, queue, flush = true, dealloc = true)

                    this
                }

                val pipelineCacheInfo = VkPipelineCacheCreateInfo.calloc()
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO)
                    .pNext(NULL)
                    .flags(VK_FLAGS_NONE)

                val refreshResolutionDependentResources = {
                    if (pipelineCache != -1L) {
                        vkDestroyPipelineCache(device.vulkanDevice, pipelineCache, null)
                    }

                    pipelineCache = VU.getLong("create pipeline cache",
                        { vkCreatePipelineCache(device.vulkanDevice, pipelineCacheInfo, null, this) },
                        { pipelineCacheInfo.free() })

                    renderpasses.values.forEach { it.close() }
                    renderpasses.clear()

                    settings.set("Renderer.displayWidth", window.width)
                    settings.set("Renderer.displayHeight", window.height)

                    prepareRenderpassesFromConfig(renderConfig, window.width, window.height)

                    semaphores.forEach { it.value.forEach { semaphore -> vkDestroySemaphore(device.vulkanDevice, semaphore, null) } }
                    semaphores = prepareStandardSemaphores(device)

                    // Create render command buffers
                    vkResetCommandPool(device.vulkanDevice, commandPools.Render, VK_FLAGS_NONE)

                    scene.findObserver()?.let { cam ->
                        cam.perspectiveCamera(cam.fov, window.width.toFloat(), window.height.toFloat(), cam.nearPlaneDistance, cam.farPlaneDistance)
                    }

                    logger.info("Calling late resize initializers for ${lateResizeInitializers.keys.joinToString(", ")}")
                    lateResizeInitializers.map { it.value.invoke() }

                    if (timestampQueryPool != -1L) {
                        vkDestroyQueryPool(device.vulkanDevice, timestampQueryPool, null)
                    }

                    val queryPoolCreateInfo = VkQueryPoolCreateInfo.calloc()
                        .sType(VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO)
                        .pNext(NULL)
                        .queryType(VK_QUERY_TYPE_TIMESTAMP)
                        .queryCount(renderConfig.renderpasses.size * 2)

                    timestampQueryPool = VU.getLong("Create timestamp query pool",
                        { vkCreateQueryPool(device.vulkanDevice, queryPoolCreateInfo, null, this) },
                        { queryPoolCreateInfo.free() })
                }

                refreshResolutionDependentResources.invoke()

                totalFrames = 0
                mustRecreate = false

                lock.unlock()
            }
        }
    }

    var debugCallback = object : VkDebugReportCallbackEXT() {
        override operator fun invoke(flags: Int, objectType: Int, obj: Long, location: Long, messageCode: Int, pLayerPrefix: Long, pMessage: Long, pUserData: Long): Int {
            val dbg = if (flags and VK_DEBUG_REPORT_DEBUG_BIT_EXT == 1) {
                " (debug)"
            } else {
                ""
            }

            if (flags and VK_DEBUG_REPORT_ERROR_BIT_EXT == VK_DEBUG_REPORT_ERROR_BIT_EXT) {
                logger.error("!! $obj Validation$dbg: " + getString(pMessage))
            } else if (flags and VK_DEBUG_REPORT_WARNING_BIT_EXT == VK_DEBUG_REPORT_WARNING_BIT_EXT) {
                logger.warn("!! $obj Validation$dbg: " + getString(pMessage))
            } else if (flags and VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT == VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT) {
                logger.error("!! $obj Validation (performance)$dbg: " + getString(pMessage))
            } else if (flags and VK_DEBUG_REPORT_INFORMATION_BIT_EXT == VK_DEBUG_REPORT_INFORMATION_BIT_EXT) {
                logger.info("!! $obj Validation$dbg: " + getString(pMessage))
            } else {
                logger.info("!! $obj Validation (unknown message type)$dbg: " + getString(pMessage))
            }

            if(strictValidation) {
                // set 15s of delay until the next frame is rendered if a validation error happens
                renderDelay = 1500L

                try {
                    throw Exception("Vulkan validation layer exception, see validation layer error messages above. To disable these exceptions, set scenery.VulkanRenderer.StrictValidation=false. Stack trace:")
                } catch (e: Exception) {
                    logger.error(e.message)
                    e.printStackTrace()
                }
            }

            // if strict validation is enabled, the application will quit after a
            // validation error has been encountered
            return if(strictValidation) {
                VK_FALSE
            } else {
                VK_FALSE
            }
        }
    }

    // helper classes end


    // helper vars
    private val VK_FLAGS_NONE: Int = 0
    private var MAX_TEXTURES = 2048 * 16
    private var MAX_UBOS = 2048
    private var MAX_INPUT_ATTACHMENTS = 32
    private val UINT64_MAX: Long = -1L


    private val MATERIAL_HAS_DIFFUSE = 0x0001
    private val MATERIAL_HAS_AMBIENT = 0x0002
    private val MATERIAL_HAS_SPECULAR = 0x0004
    private val MATERIAL_HAS_NORMAL = 0x0008
    private val MATERIAL_HAS_ALPHAMASK = 0x0010

    // end helper vars

    final override var hub: Hub? = null
    protected var applicationName = ""
    final override var settings: Settings = Settings()
    override var shouldClose = false
    private var toggleFullscreen = false
    override var managesRenderLoop = false
    private var screenshotRequested = false

    private var firstWaitSemaphore: LongBuffer = memAllocLong(1)

    var scene: Scene = Scene()

    protected var commandPools = CommandPools()
    protected val renderpasses = Collections.synchronizedMap(LinkedHashMap<String, VulkanRenderpass>())
    /** Cache for [SDFFontAtlas]es used for font rendering */
    protected var fontAtlas = HashMap<String, SDFFontAtlas>()

    protected val validation = java.lang.Boolean.parseBoolean(System.getProperty("scenery.VulkanRenderer.EnableValidations", "false"))
    protected val strictValidation = java.lang.Boolean.parseBoolean(System.getProperty("scenery.VulkanRenderer.StrictValidation", "false"))
    protected val wantsOpenGLSwapchain = java.lang.Boolean.parseBoolean(System.getProperty("scenery.VulkanRenderer.UseOpenGLSwapchain", "false"))
    protected val defaultValidationLayers = arrayOf("VK_LAYER_LUNARG_standard_validation")

    protected var instance: VkInstance
    protected var device: VulkanDevice

    protected var debugCallbackHandle: Long
    protected var timestampQueryPool: Long = -1L

    protected var semaphoreCreateInfo: VkSemaphoreCreateInfo

    // Create static Vulkan resources
    protected var queue: VkQueue
    protected var descriptorPool: Long

    protected var swapchain: Swapchain? = null
    protected var ph = PresentHelpers()

    final override var window: SceneryWindow = SceneryWindow.UninitializedWindow()

    protected val swapchainRecreator: SwapchainRecreator
    protected var pipelineCache: Long = -1L
    protected var vertexDescriptors = ConcurrentHashMap<VertexDataKinds, VertexDescription>()
    protected var sceneUBOs = ArrayList<Node>()
    protected var semaphores = ConcurrentHashMap<StandardSemaphores, Array<Long>>()
    protected var buffers = ConcurrentHashMap<String, VulkanBuffer>()
    protected var textureCache = ConcurrentHashMap<String, VulkanTexture>()
    protected var descriptorSetLayouts = ConcurrentHashMap<String, Long>()
    protected var descriptorSets = ConcurrentHashMap<String, Long>()

    protected var lastTime = System.nanoTime()
    protected var time = 0.0f
    protected var fps = 0
    protected var frames = 0
    protected var totalFrames = 0L
    protected var renderDelay = 0L
    protected var heartbeatTimer = Timer()
    protected var gpuStats: GPUStats? = null

    private var renderConfig: RenderConfigReader.RenderConfig
    private var flow: List<String> = listOf()

    private val vulkanProjectionFix =
        GLMatrix(floatArrayOf(
            1.0f,  0.0f, 0.0f, 0.0f,
            0.0f, -1.0f, 0.0f, 0.0f,
            0.0f,  0.0f, 0.5f, 0.0f,
            0.0f,  0.0f, 0.5f, 1.0f))

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

    init {
        this.hub = hub

        Loader.loadNatives()
        libspirvcrossj.initializeProcess()

        val hmd = hub.getWorkingHMDDisplay()
        if (hmd != null) {
            logger.info("Setting window dimensions to bounds from HMD")
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


        // explicitly create VK, to make GLFW pick up MoltenVK on OS X
        try {
            Configuration.VULKAN_EXPLICIT_INIT.set(true)
            VK.create()
        } catch(e: IllegalStateException) {
            logger.warn("IllegalStateException during Vulkan initialisation")
        }

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
        if(validation) {
            debugCallbackHandle = setupDebugging(instance,
                VK_DEBUG_REPORT_ERROR_BIT_EXT or VK_DEBUG_REPORT_WARNING_BIT_EXT,
                debugCallback)
        } else {
            debugCallbackHandle = -1L
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

        device = VulkanDevice.fromPhysicalDevice(instance,
            physicalDeviceFilter = { id, device -> device.name.contains(System.getProperty("scenery.Renderer.Device", "DOES_NOT_EXIST"))},
            additionalExtensions = { physicalDevice -> hub.getWorkingHMDDisplay()?.getVulkanDeviceExtensions(physicalDevice)?.toTypedArray() ?: arrayOf() },
            validationLayers = requestedValidationLayers)

        logger.debug("Device creation done")

        queue = VU.createDeviceQueue(device, device.queueIndices.graphicsQueue)
        with(commandPools) {
            Render = createCommandPool(device, device.queueIndices.graphicsQueue)
            Standard = createCommandPool(device, device.queueIndices.graphicsQueue)
            Compute = createCommandPool(device, device.queueIndices.graphicsQueue)
        }
        logger.debug("Creating command pools done")

        swapchainRecreator = SwapchainRecreator()

        swapchain = if (wantsOpenGLSwapchain) {
            logger.info("Using OpenGL-based swapchain")
            OpenGLSwapchain(
                device, queue, commandPools.Standard,
                renderConfig = renderConfig, useSRGB = true,
                useFramelock = System.getProperty("scenery.Renderer.Framelock", "false").toBoolean())
        } else {
            if(System.getProperty("scenery.Renderer.UseJavaFX", "false").toBoolean() || embedIn != null) {
                logger.info("Using JavaFX-based swapchain")
                FXSwapchain(
                    device, queue, commandPools.Standard,
                    renderConfig = renderConfig, useSRGB = true)
            } else {
                VulkanSwapchain(
                    device, queue, commandPools.Standard,
                    renderConfig = renderConfig, useSRGB = true)
            }
        }.apply {
            embedIn(embedIn)
            window = createWindow(window, swapchainRecreator)
        }

        logger.debug("Created swapchain")
        vertexDescriptors = prepareStandardVertexDescriptors()
        logger.debug("Created vertex descriptors")
        descriptorPool = createDescriptorPool(device)
        logger.debug("Created descriptor pool")

        descriptorSetLayouts = prepareDefaultDescriptorSetLayouts(device)
        logger.debug("Prepared default DSLs")
        prepareDefaultBuffers(device, buffers)
        logger.debug("Prepared default buffers")

        prepareDescriptorSets(device, descriptorPool)
        prepareDefaultTextures(device)

        heartbeatTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (window.shouldClose) {
                    shouldClose = true
                    return
                }

                fps = frames
                frames = 0

                gpuStats?.let {
                    it.update(0)

                    hub.get(SceneryElement.Statistics).let { s ->
                        val stats = s as Statistics

                        stats.add("GPU", it.get("GPU"), isTime = false)
                        stats.add("GPU bus", it.get("Bus"), isTime = false)
                        stats.add("GPU mem", it.get("AvailableDedicatedVideoMemory"), isTime = false)
                    }

                    if (settings.get<Boolean>("Renderer.PrintGPUStats")) {
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
        semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
            .pNext(NULL)
            .flags(0)

        lastTime = System.nanoTime()
        time = 0.0f

        if(System.getProperty("scenery.RunFullscreen","false").toBoolean()) {
            toggleFullscreen = true
        }
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

        this.scene.discover(this.scene, { it is HasGeometry })
//            .parallelMap(numThreads = System.getProperty("scenery.MaxInitThreads", "1").toInt()) { node ->
            .map { node ->
                logger.debug("Initializing object '${node.name}'")
                node.metadata.put("VulkanRenderer", VulkanObjectState())

                if (node is TextBoard) {
                    updateTextBoard(node)
                } else {
                    initializeNode(node)
                }
            }

        scene.initialized = true
        logger.info("Scene initialization complete.")
    }

    protected fun updateTextBoard(board: TextBoard) {
        logger.debug("Updating font board ... ${board.name}")
        val atlas = fontAtlas.getOrPut(board.fontFamily,
            { SDFFontAtlas(this.hub!!, board.fontFamily, maxDistance = settings.get("sdf.MaxDistance")) })
        val m = atlas.createMeshForString(board.text)

        board.vertices = m.vertices
        board.normals = m.normals
        board.indices = m.indices
        board.texcoords = m.texcoords
        board.atlasSize = GLVector(atlas.atlasWidth.toFloat(), atlas.atlasHeight.toFloat(), 0.0f, 0.0f)

        if (!board.initialized) {
            board.metadata.put("VulkanRenderer", VulkanObjectState())
            initializeNode(board)
        } else {
            updateNodeGeometry(board)
        }

        board.rendererMetadata()?.let { s ->
            val texture = textureCache.getOrPut("sdf-${board.fontFamily}", {
                val t = VulkanTexture(device,
                    commandPools.Standard, queue,
                    atlas.atlasWidth, atlas.atlasHeight, 1,
                    format = VK_FORMAT_R8_UNORM,
                    mipLevels = 1)

                t.copyFrom(atlas.getAtlas())
                t
            })

            s.textures.put("ambient", texture)
            s.textures.put("diffuse", texture)

            arrayOf("ambient", "diffuse", "specular", "normal", "alphamask", "displacement").forEach {
                if (!s.textures.containsKey(it)) {
                    s.textures.putIfAbsent(it, textureCache["DefaultTexture"]!!)
                    s.defaultTexturesFor.add(it)
                }
            }

            s.texturesToDescriptorSet(device, descriptorSetLayouts["ObjectTextures"]!!,
                descriptorPool,
                targetBinding = 0)

            board.dirty = false
            board.initialized = true
            logger.debug("Update complete")
        }
    }

    fun Boolean.toInt(): Int {
        return if (this) {
            1
        } else {
            0
        }
    }

    fun updateNodeGeometry(node: Node) {
        if (node is HasGeometry && node.vertices.remaining() > 0) {
            node.rendererMetadata()?.let { s ->
                createVertexBuffers(device, node, s)
            }
        }
    }

    /**
     *
     */
    fun initializeNode(node: Node): Boolean {
        var s: VulkanObjectState

        s = node.rendererMetadata()!!

        if (s.initialized) return true

        logger.debug("Initializing ${node.name} (${(node as HasGeometry).vertices.remaining() / node.vertexSize} vertices/${node.indices.remaining()} indices)")

        // determine vertex input type
        if (node.vertices.remaining() > 0 && node.normals.remaining() > 0 && node.texcoords.remaining() > 0) {
            s.vertexInputType = VertexDataKinds.PositionNormalTexcoord
        }

        if (node.vertices.remaining() > 0 && node.normals.remaining() > 0 && node.texcoords.remaining() == 0) {
            s.vertexInputType = VertexDataKinds.PositionNormal
        }

        if (node.vertices.remaining() > 0 && node.normals.remaining() == 0 && node.texcoords.remaining() > 0) {
            s.vertexInputType = VertexDataKinds.PositionTexcoords
        }

        // create custom vertex description if necessary, else use one of the defaults
        s.vertexDescription = if (node.instanceMaster) {
            // TODO: Rewrite shader in case it does not conform to coord/normal/texcoord vertex description
            s.vertexInputType = VertexDataKinds.PositionNormalTexcoord
            vertexDescriptionFromInstancedNode(node, vertexDescriptors[VertexDataKinds.PositionNormalTexcoord]!!)
        } else {
            vertexDescriptors[s.vertexInputType]!!
        }

        if (node.instanceOf != null) {
            val parentMetadata = node.instanceOf!!.rendererMetadata()!!

            if (!parentMetadata.initialized) {
                logger.debug("Instance parent ${node.instanceOf!!} is not initialized yet, initializing now...")
                initializeNode(node.instanceOf!!)
            }

            if (!parentMetadata.vertexBuffers.containsKey("instance")) {
                createInstanceBuffer(device, node.instanceOf!!, parentMetadata)
            }

            return true
        }

        if (node.vertices.remaining() > 0) {
            s = createVertexBuffers(device, node, s)
        }

        val matricesDescriptorSet =
            VU.createDescriptorSetDynamic(device, descriptorPool,
                descriptorSetLayouts["Matrices"]!!, 1,
                buffers["UBOBuffer"]!!)

        val materialPropertiesDescriptorSet =
            VU.createDescriptorSetDynamic(device, descriptorPool,
                descriptorSetLayouts["MaterialProperties"]!!, 1,
                buffers["UBOBuffer"]!!)

        val matricesUbo = VulkanUBO(device, backingBuffer = buffers["UBOBuffer"])
        with(matricesUbo) {
            name = "Matrices"
            add("ModelMatrix", { node.world })
            add("NormalMatrix", { node.world.inverse.transpose() })
            add("ProjectionMatrix", { node.projection })
            add("isBillboard", { node.isBillboard.toInt() })

            requiredOffsetCount = 2
            createUniformBuffer()
            sceneUBOs.add(node)

            s.UBOs.put(name, matricesDescriptorSet.to(this))
        }

        loadTexturesForNode(node, s)

        val materialUbo = VulkanUBO(device, backingBuffer = buffers["UBOBuffer"])
        var materialType = 0

        if (node.material.textures.containsKey("ambient") && !s.defaultTexturesFor.contains("ambient")) {
            materialType = materialType or MATERIAL_HAS_AMBIENT
        }

        if (node.material.textures.containsKey("diffuse") && !s.defaultTexturesFor.contains("diffuse")) {
            materialType = materialType or MATERIAL_HAS_DIFFUSE
        }

        if (node.material.textures.containsKey("specular") && !s.defaultTexturesFor.contains("specular")) {
            materialType = materialType or MATERIAL_HAS_SPECULAR
        }

        if (node.material.textures.containsKey("normal") && !s.defaultTexturesFor.contains("normal")) {
            materialType = materialType or MATERIAL_HAS_NORMAL
        }

        if (node.material.textures.containsKey("alphamask") && !s.defaultTexturesFor.contains("alphamask")) {
            materialType = materialType or MATERIAL_HAS_ALPHAMASK
        }

        s.blendingHashCode = node.material.blending.hashCode()

        with(materialUbo) {
            name = "MaterialProperties"
            add("materialType", { materialType })
            add("Ka", { node.material.ambient })
            add("Kd", { node.material.diffuse })
            add("Ks", { node.material.specular })
            add("Shininess", { node.material.specularExponent })
            add("Opacity", { node.material.blending.opacity })

            requiredOffsetCount = 1
            createUniformBuffer()
            s.UBOs.put("MaterialProperties", materialPropertiesDescriptorSet.to(this))
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

    private fun Node.findExistingShaders(): List<String> {
        val baseName = this.javaClass.simpleName
        val base = if(this.javaClass.`package`.name.startsWith("graphics.scenery")) {
            Renderer::class.java.to("shaders/")
        } else {
            this.javaClass.to("")
        }

        return listOf("$baseName.vert", "$baseName.geom", "$baseName.tesc", "$baseName.tese", "$baseName.frag")
            .filter { base.first.getResource(base.second + it) != null }
    }

    private fun initializeCustomShadersForNode(node: Node, addInitializer: Boolean = true): Boolean {

        if(!(node.material.blending.transparent || node.useClassDerivedShader || node.material.doubleSided || node.material is ShaderMaterial)) {
            logger.debug("Using default renderpass material for ${node.name}")
            return false
        }

        if(addInitializer) {
            lateResizeInitializers.remove(node)
        }

        // TODO: Add check whether the node actually needs a custom shader
        node.rendererMetadata()?.let { s ->

            val needsShaderPropertyUBO = if (node.javaClass.declaredFields.filter { it.isAnnotationPresent(ShaderProperty::class.java) }.count() > 0) {
                var dsl = 0L

                renderpasses.filter {
                    it.value.passConfig.type == RenderConfigReader.RenderpassType.geometry
                        && it.value.passConfig.renderTransparent == node.material.blending.transparent
                }
                    .map { pass ->
                        logger.debug("Initializing shader properties for ${node.name}")
                        dsl = pass.value.initializeShaderPropertyDescriptorSetLayout()
                    }

                val descriptorSet = VU.createDescriptorSetDynamic(device, descriptorPool, dsl,
                    1, buffers["ShaderPropertyBuffer"]!!)

                s.requiredDescriptorSets.put("ShaderProperties", descriptorSet)
                true
            } else {
                false
            }


            renderpasses.filter { it.value.passConfig.type == RenderConfigReader.RenderpassType.geometry }
                .map { pass ->
                    val shaders = when {
                        node.material is ShaderMaterial -> {
                            logger.debug("Initializing preferred pipeline for ${node.name} from ShaderMaterial")
                            (node.material as ShaderMaterial).shaders
                        }

                        node.useClassDerivedShader && pass.value.passConfig.renderTransparent == node.material.blending.transparent -> {
                            logger.debug("Initializing classname-derived preferred pipeline for ${node.name}")
                            val shaders = node.findExistingShaders()

                            if(shaders.isEmpty()) {
                                throw ShaderCompilationException("No shaders found for ${node.name}")
                            }

                            shaders
                        }

                        else -> {
                            logger.debug("Initializing pass-default shader preferred pipeline for ${node.name}")
                            pass.value.passConfig.shaders
                        }
                    }

                    logger.debug("Shaders are: ${shaders.joinToString(", ")}")

                    pass.value.initializePipeline("preferred-${node.name}",
                        shaders.map { VulkanShaderModule(device, "main", node.javaClass, "shaders/" + it) },

                        settings = { pipeline ->
                            if (node.material.doubleSided) {
                                logger.debug("Pipeline for ${node.name} will be double-sided, backface culling disabled.")
                                pipeline.rasterizationState.cullMode(VK_CULL_MODE_NONE)
                            }

                            if(node.material.blending.transparent) {
                                with(node.material.blending) {
                                    val blendStates = pipeline.colorBlendState.pAttachments()
                                    for (attachment in 0 until blendStates.capacity()) {
                                        blendStates[attachment]
                                            .blendEnable(true)
                                            .colorBlendOp(colorBlending.toVulkan())
                                            .srcColorBlendFactor(sourceColorBlendFactor.toVulkan())
                                            .dstColorBlendFactor(destinationColorBlendFactor.toVulkan())
                                            .alphaBlendOp(alphaBlending.toVulkan())
                                            .srcAlphaBlendFactor(sourceAlphaBlendFactor.toVulkan())
                                            .dstAlphaBlendFactor(destinationAlphaBlendFactor.toVulkan())
                                            .colorWriteMask(VK_COLOR_COMPONENT_R_BIT or VK_COLOR_COMPONENT_G_BIT or VK_COLOR_COMPONENT_B_BIT or VK_COLOR_COMPONENT_A_BIT)
                                    }
                                }
                            }
                        },
                        vertexInputType = s.vertexDescription!!)
                }


            if (needsShaderPropertyUBO) {
                var order: Map<String, Int> = emptyMap()
                renderpasses.filter {
                    it.value.passConfig.type == RenderConfigReader.RenderpassType.geometry &&
                        it.value.passConfig.renderTransparent == node.material.blending.transparent
                }
                    .map { pass ->
                        logger.debug("Initializing shader properties for ${node.name}")
                        order = pass.value.getShaderPropertyOrder(node)
                    }

                val shaderPropertyUbo = VulkanUBO(device, backingBuffer = buffers["ShaderPropertyBuffer"])
                with(shaderPropertyUbo) {
                    name = "ShaderProperties"

                    order.forEach { name, offset ->
                        add(name, { node.getShaderProperty(name)!! }, offset)
                    }

                    requiredOffsetCount = 1
                    this.createUniformBuffer()
                    s.UBOs.put("ShaderProperties", s.requiredDescriptorSets["ShaderProperties"]!!.to(this))
                }
            }

            if(addInitializer) {
                lateResizeInitializers.put(node, { initializeCustomShadersForNode(node, addInitializer = false) })
            }

            return true
        }

        return false
    }

    fun destroyNode(node: Node) {
        logger.debug("Destroying node ${node.name}...")
        if (!node.metadata.containsKey("VulkanRenderer")) {
            return
        }

        lateResizeInitializers.remove(node)

        node.rendererMetadata()?.UBOs?.forEach { it.value.second.close() }

        if (node is HasGeometry) {
            node.rendererMetadata()?.vertexBuffers?.forEach {
                it.value.close()
            }
        }
    }

    protected fun loadTexturesForNode(node: Node, s: VulkanObjectState): VulkanObjectState {
        val stats = hub?.get(SceneryElement.Statistics) as Statistics?

        if (node.lock.tryLock()) {
            node.material.textures.forEach {
                type, texture ->

                val slot = VulkanObjectState.textureTypeToSlot(type)

                val generateMipmaps = (type == "ambient" || type == "diffuse" || type == "specular")

                logger.debug("${node.name} will have $type texture from $texture in slot $slot")

                if (!textureCache.containsKey(texture) || node.material.needsTextureReload) {
                    logger.trace("Loading texture $texture for ${node.name}")

                    val gt = node.material.transferTextures[texture.substringAfter("fromBuffer:")]

                    val vkTexture = if (texture.startsWith("fromBuffer:") && gt != null) {
                        val miplevels = if (generateMipmaps) {
                            1 + Math.floor(Math.log(Math.max(gt.dimensions.x() * 1.0, gt.dimensions.y() * 1.0)) / Math.log(2.0)).toInt()
                        } else {
                            1
                        }

                        val existingTexture = s.textures[type]
                        val t = if (existingTexture != null && existingTexture.device == device
                            && existingTexture.width == gt.dimensions.x().toInt()
                            && existingTexture.height == gt.dimensions.y().toInt()
                            && existingTexture.depth == gt.dimensions.z().toInt()
                            && existingTexture.mipLevels == miplevels) {
                            existingTexture
                        } else {
                            VulkanTexture(device,
                                commandPools.Standard, queue, gt, miplevels)
                        }

                        t.copyFrom(gt.contents)

                        t
                    } else {
                        val start = System.nanoTime()

                        val t = if(texture.contains("jar!")) {
                            val f = texture.substringAfterLast(File.separatorChar)
                            val stream = node.javaClass.getResourceAsStream(f)

                            if(stream == null) {
                                logger.error("Not found: $f for $node")
                                textureCache["DefaultTexture"]
                            } else {
                                VulkanTexture.loadFromFile(device,
                                    commandPools.Standard, queue, stream, texture.substringAfterLast("."), true, generateMipmaps)
                            }
                        } else {
                            VulkanTexture.loadFromFile(device,
                                commandPools.Standard, queue, texture, true, generateMipmaps)
                        }

                        val duration = System.nanoTime() - start * 1.0f
                        stats?.add("loadTexture", duration)

                        t
                    }

                    // add new texture to texture list and cache, and close old texture
                    s.textures.put(type, vkTexture!!)
                    textureCache.put(texture, vkTexture)
                } else {
                    s.textures.put(type, textureCache[texture]!!)
                }
            }

            arrayOf("ambient", "diffuse", "specular", "normal", "alphamask", "displacement").forEach {
                if (!s.textures.containsKey(it)) {
                    s.textures.putIfAbsent(it, textureCache["DefaultTexture"]!!)
                    s.defaultTexturesFor.add(it)
                }
            }

            s.texturesToDescriptorSet(device, descriptorSetLayouts["ObjectTextures"]!!,
                descriptorPool,
                targetBinding = 0)

            node.lock.unlock()
        }

        return s
    }

    protected fun prepareDefaultDescriptorSetLayouts(device: VulkanDevice): ConcurrentHashMap<String, Long> {
        val m = ConcurrentHashMap<String, Long>()

        m.put("Matrices", VU.createDescriptorSetLayout(
            device,
            listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1)),
            0,
            VK_SHADER_STAGE_ALL))

        m.put("MaterialProperties", VU.createDescriptorSetLayout(
            device,
            listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1)),
            0,
            VK_SHADER_STAGE_ALL))

        m.put("LightParameters", VU.createDescriptorSetLayout(
            device,
            listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1)),
            0,
            VK_SHADER_STAGE_ALL))

        m.put("ObjectTextures", VU.createDescriptorSetLayout(
            device,
            listOf(
                Pair(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 6),
                Pair(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1)),
            0,
            VK_SHADER_STAGE_ALL))

        m.put("VRParameters", VU.createDescriptorSetLayout(
            device,
            listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1)),
            0,
            VK_SHADER_STAGE_ALL))

        return m
    }

    protected fun prepareDescriptorSets(device: VulkanDevice, descriptorPool: Long) {
        this.descriptorSets.put("Matrices",
            VU.createDescriptorSetDynamic(device, descriptorPool,
                descriptorSetLayouts["Matrices"]!!, 1,
                buffers["UBOBuffer"]!!))

        this.descriptorSets.put("MaterialProperties",
            VU.createDescriptorSetDynamic(device, descriptorPool,
                descriptorSetLayouts["MaterialProperties"]!!, 1,
                buffers["UBOBuffer"]!!))

        this.descriptorSets.put("LightParameters",
            VU.createDescriptorSetDynamic(device, descriptorPool,
                descriptorSetLayouts["LightParameters"]!!, 1,
                buffers["LightParametersBuffer"]!!))

        this.descriptorSets.put("VRParameters",
            VU.createDescriptorSetDynamic(device, descriptorPool,
                descriptorSetLayouts["VRParameters"]!!, 1,
                buffers["VRParametersBuffer"]!!))
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

            attributeDesc?.get(0)?.binding(0)?.location(0)?.format(VK_FORMAT_R32G32B32_SFLOAT)?.offset(0)

            val bindingDesc = if (attributeDesc != null) {
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

            map.put(kind, VertexDescription(inputState, attributeDesc, bindingDesc))
        }

        return map
    }

    data class AttributeInfo(val format: Int, val elementByteSize: Int, val elementCount: Int)

    fun HashMap<String, () -> Any>.getFormatsAndRequiredAttributeSize(): List<AttributeInfo> {
        return this.map {
            val value = it.value.invoke()

            when (value.javaClass) {
                GLVector::class.java -> {
                    val v = value as GLVector
                    if (v.toFloatArray().size == 2) {
                        graphics.scenery.backends.vulkan.VulkanRenderer.AttributeInfo(VK_FORMAT_R32G32_SFLOAT, 4 * 2, 1)
                    } else if (v.toFloatArray().size == 4) {
                        graphics.scenery.backends.vulkan.VulkanRenderer.AttributeInfo(VK_FORMAT_R32G32B32A32_SFLOAT, 4 * 4, 1)
                    } else {
                        logger.error("Unsupported vector length for instancing: ${v.toFloatArray().size}")
                        graphics.scenery.backends.vulkan.VulkanRenderer.AttributeInfo(-1, -1, -1)
                    }
                }

                GLMatrix::class.java -> {
                    val m = value as GLMatrix
                    graphics.scenery.backends.vulkan.VulkanRenderer.AttributeInfo(VK_FORMAT_R32G32B32A32_SFLOAT, 4 * 4, m.floatArray.size / 4)
                }

                else -> {
                    logger.error("Unsupported type for instancing: ${value.javaClass.simpleName}")
                    graphics.scenery.backends.vulkan.VulkanRenderer.AttributeInfo(-1, -1, -1)
                }
            }
        }
    }

    protected fun vertexDescriptionFromInstancedNode(node: Node, template: VertexDescription): VertexDescription {
        logger.debug("Creating instanced vertex description for ${node.name}")

        if(template.attributeDescription == null || template.bindingDescription == null) {
            return template
        }

        val attributeDescs = template.attributeDescription!!
        val bindingDescs = template.bindingDescription!!

        val formatsAndAttributeSizes = node.instancedProperties.getFormatsAndRequiredAttributeSize()
        val newAttributesNeeded = formatsAndAttributeSizes.map { it.elementCount }.sum()

        val newAttributeDesc = VkVertexInputAttributeDescription
            .calloc(attributeDescs.capacity() + newAttributesNeeded)

        var position: Int
        var offset = 0

        (0..attributeDescs.capacity() - 1).forEach { i ->
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

            (0..attribInfo.elementCount - 1).forEach {
                newAttributeDesc[position]
                    .binding(1)
                    .location(position)
                    .format(attribInfo.format)
                    .offset(offset)

                logger.debug("location($position, $it/${attribInfo.elementCount}) for ${property.first}, type: ${property.second.invoke().javaClass.simpleName}")
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
        val t = VulkanTexture.loadFromFile(device, commandPools.Standard, queue,
            Renderer::class.java.getResourceAsStream("DefaultTexture.png"), "png", true, true)

        textureCache.put("DefaultTexture", t!!)
    }

    protected fun prepareRenderpassesFromConfig(config: RenderConfigReader.RenderConfig, windowWidth: Int, windowHeight: Int) {
        // create all renderpasses first
        val framebuffers = ConcurrentHashMap<String, VulkanFramebuffer>()

        flow = renderConfig.createRenderpassFlow()

        descriptorSetLayouts
            .filter { it.key.startsWith("outputs-") }
            .values.map { vkDestroyDescriptorSetLayout(device.vulkanDevice, it, null) }

        renderConfig.renderpasses.forEach { rp ->
            rp.value.inputs?.let {
                renderConfig.rendertargets?.let { rts ->
                    val rt = rts[it.first()]!!

                    // create descriptor set layout that matches the render target
                    descriptorSetLayouts.put("outputs-${it.first()}",
                        VU.createDescriptorSetLayout(device,
                            descriptorNum = rt.count(),
                            descriptorCount = 1,
                            type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
                        ))
                }
            }
        }

        config.createRenderpassFlow().map { passName ->
            val passConfig = config.renderpasses[passName]!!
            val pass = VulkanRenderpass(passName, config, device, descriptorPool, pipelineCache, vertexDescriptors)

            var width = windowWidth
            var height = windowHeight

            // create framebuffer
            with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
                config.rendertargets?.filter { it.key == passConfig.output }?.map { rt ->
                    logger.info("Creating render framebuffer ${rt.key} for pass $passName")

                    width = (settings.get<Float>("Renderer.SupersamplingFactor") * windowWidth).toInt()
                    height = (settings.get<Float>("Renderer.SupersamplingFactor") * windowHeight).toInt()

                    if (framebuffers.containsKey(rt.key)) {
                        logger.info("Reusing already created framebuffer")
                        pass.output.put(rt.key, framebuffers[rt.key]!!)
                    } else {

                        // create framebuffer -- don't clear it, if blitting is needed
                        val framebuffer = VulkanFramebuffer(device, commandPools.Standard,
                            width, height, this,
                            shouldClear = !passConfig.blitInputs)

                        rt.value.forEach { att ->
                            logger.info(" + attachment ${att.key}, ${att.value.format.name}")

                            when (att.value.format) {
                                RenderConfigReader.TargetFormat.RGBA_Float32 -> framebuffer.addFloatRGBABuffer(att.key, 32)
                                RenderConfigReader.TargetFormat.RGBA_Float16 -> framebuffer.addFloatRGBABuffer(att.key, 16)

                                RenderConfigReader.TargetFormat.RGB_Float32 -> framebuffer.addFloatRGBBuffer(att.key, 32)
                                RenderConfigReader.TargetFormat.RGB_Float16 -> framebuffer.addFloatRGBBuffer(att.key, 16)

                                RenderConfigReader.TargetFormat.RG_Float32 -> framebuffer.addFloatRGBuffer(att.key, 32)
                                RenderConfigReader.TargetFormat.RG_Float16 -> framebuffer.addFloatRGBuffer(att.key, 16)

                                RenderConfigReader.TargetFormat.RGBA_UInt16 -> framebuffer.addUnsignedByteRGBABuffer(att.key, 16)
                                RenderConfigReader.TargetFormat.RGBA_UInt8 -> framebuffer.addUnsignedByteRGBABuffer(att.key, 8)

                                RenderConfigReader.TargetFormat.Depth32 -> framebuffer.addDepthBuffer(att.key, 32)
                                RenderConfigReader.TargetFormat.Depth24 -> framebuffer.addDepthBuffer(att.key, 24)
                            }

                        }

                        framebuffer.createRenderpassAndFramebuffer()
                        framebuffer.outputDescriptorSet = VU.createRenderTargetDescriptorSet(device,
                            descriptorPool, descriptorSetLayouts["outputs-${rt.key}"]!!, rt.value, framebuffer)

                        pass.output.put(rt.key, framebuffer)
                        framebuffers.put(rt.key, framebuffer)
                    }
                }

                pass.commandBufferCount = swapchain!!.images!!.size

                if (passConfig.output == "Viewport") {
                    // create viewport renderpass with swapchain image-derived framebuffer
                    pass.isViewportRenderpass = true
                    width = windowWidth
                    height = windowHeight

                    swapchain!!.images!!.forEachIndexed { i, _ ->
                        val fb = VulkanFramebuffer(device, commandPools.Standard,
                            width, height, this@with)

                        fb.addSwapchainAttachment("swapchain-$i", swapchain!!, i)
                        fb.addDepthBuffer("swapchain-$i-depth", 32)
                        fb.createRenderpassAndFramebuffer()

                        pass.output.put("Viewport-$i", fb)
                    }
                }

                pass.vulkanMetadata.clearValues?.free()
                if(!passConfig.blitInputs) {
                    pass.vulkanMetadata.clearValues = VkClearValue.calloc(pass.output.values.first().attachments.count())
                    pass.vulkanMetadata.clearValues?.let { clearValues ->

                        pass.output.values.first().attachments.values.forEachIndexed { i, att ->
                            when (att.type) {
                                VulkanFramebuffer.VulkanFramebufferType.COLOR_ATTACHMENT -> {
                                    clearValues[i].color().float32().put(pass.passConfig.clearColor.toFloatArray())
                                }
                                VulkanFramebuffer.VulkanFramebufferType.DEPTH_ATTACHMENT -> {
                                    clearValues[i].depthStencil().set(pass.passConfig.depthClearValue, 0)
                                }
                            }
                        }
                    }
                } else {
                    pass.vulkanMetadata.clearValues = null
                }

                pass.vulkanMetadata.renderArea.extent().set(
                    (pass.passConfig.viewportSize.first * width).toInt(),
                    (pass.passConfig.viewportSize.second * height).toInt())
                pass.vulkanMetadata.renderArea.offset().set(
                    (pass.passConfig.viewportOffset.first * width).toInt(),
                    (pass.passConfig.viewportOffset.second * height).toInt())
                logger.debug("Render area for $passName: ${pass.vulkanMetadata.renderArea.extent().width()}x${pass.vulkanMetadata.renderArea.extent().height()}")

                pass.vulkanMetadata.viewport[0].set(
                    (pass.passConfig.viewportOffset.first * width),
                    (pass.passConfig.viewportOffset.second * height),
                    (pass.passConfig.viewportSize.first * width),
                    (pass.passConfig.viewportSize.second * height),
                    0.0f, 1.0f)

                pass.vulkanMetadata.scissor[0].extent().set(
                    (pass.passConfig.viewportSize.first * width).toInt(),
                    (pass.passConfig.viewportSize.second * height).toInt())

                pass.vulkanMetadata.scissor[0].offset().set(
                    (pass.passConfig.viewportOffset.first * width).toInt(),
                    (pass.passConfig.viewportOffset.second * height).toInt())

                pass.vulkanMetadata.eye.put(0, pass.passConfig.eye)

                pass.semaphore = VU.getLong("vkCreateSemaphore",
                    { vkCreateSemaphore(device.vulkanDevice, semaphoreCreateInfo, null, this) }, {})

                this.endCommandBuffer(device, commandPools.Standard, this@VulkanRenderer.queue, flush = true)
            }

            renderpasses.put(passName, pass)
        }

        // connect inputs with each other
        renderpasses.forEach { pass ->
            val passConfig = config.renderpasses[pass.key]!!

            passConfig.inputs?.forEach { inputTarget ->
                renderpasses.filter {
                    it.value.output.keys.contains(inputTarget)
                }.forEach { pass.value.inputs.put(inputTarget, it.value.output[inputTarget]!!) }
            }

            with(pass.value) {
                initializeInputAttachmentDescriptorSetLayouts()
                initializeShaderParameterDescriptorSetLayouts(settings)

                initializeDefaultPipeline()
            }
        }
    }

    protected fun prepareStandardSemaphores(device: VulkanDevice): ConcurrentHashMap<StandardSemaphores, Array<Long>> {
        val map = ConcurrentHashMap<StandardSemaphores, Array<Long>>()

        StandardSemaphores.values().forEach {
            map.put(it, swapchain!!.images!!.map {
                VU.getLong("Semaphore for $it",
                { vkCreateSemaphore(device.vulkanDevice, semaphoreCreateInfo, null, this) }, {})
            }.toTypedArray())
        }

        return map
    }

    private fun pollEvents() {
        window.pollEvents()

        if (swapchainRecreator.mustRecreate) {
            swapchainRecreator.recreate()
            frames = 0
        }
    }

    private fun beginFrame() {
        swapchainRecreator.mustRecreate = swapchain!!.next(timeout = UINT64_MAX,
            waitForSemaphore = semaphores[StandardSemaphores.PresentComplete]!![0])
    }

    var screenshotBuffer: VulkanBuffer? = null
    var imageBuffer: ByteBuffer? = null
    var encoder: H264Encoder? = null
    var recordMovie: Boolean = false


    fun recordMovie() {
        if(recordMovie) {
            encoder?.finish()
            encoder = null

            recordMovie = false
        } else {
            recordMovie = true
        }
    }

    private fun submitFrame(queue: VkQueue, pass: VulkanRenderpass, commandBuffer: VulkanCommandBuffer, present: PresentHelpers) {
        val stats = hub?.get(SceneryElement.Statistics) as? Statistics
        val submitInfo = VkSubmitInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
            .pNext(NULL)
            .waitSemaphoreCount(1)
            .pWaitSemaphores(present.waitSemaphore)
            .pWaitDstStageMask(present.waitStages)
            .pCommandBuffers(present.commandBuffers)
            .pSignalSemaphores(present.signalSemaphore)

        // Submit to the graphics queue
        VU.run("Submit viewport render queue", { vkQueueSubmit(queue, submitInfo, commandBuffer.getFence()) })

        val startPresent = System.nanoTime()
        commandBuffer.submitted = true
        swapchain!!.present(ph.signalSemaphore)
        commandBuffer.waitForFence()

        swapchain!!.postPresent(pass.getReadPosition())

        // submit to OpenVR if attached
        if(hub?.getWorkingHMDDisplay()?.hasCompositor() == true) {
            hub?.getWorkingHMDDisplay()?.wantsVR()?.submitToCompositorVulkan(
                window.width, window.height,
                swapchain!!.format,
                instance, device, queue,
                swapchain!!.images!![pass.getReadPosition()])
        }

        if (recordMovie) {
                // default image format is 32bit BGRA
                val imageByteSize = window.width * window.height * 4L
            if(screenshotBuffer == null || screenshotBuffer?.size != imageByteSize) {
                logger.info("Reallocating screenshot buffer")
                screenshotBuffer = VulkanBuffer(device, imageByteSize,
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                    wantAligned = true)
            }

            if(imageBuffer == null || imageBuffer?.capacity() != imageByteSize.toInt()) {
                logger.info("Reallocating image buffer")
                imageBuffer = memAlloc(imageByteSize.toInt())
            }

            if(encoder == null || encoder?.frameWidth != window.width || encoder?.frameHeight != window.height) {
                encoder = H264Encoder(window.width, window.height, "${SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(Date())}.mp4")
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

                    val image = swapchain!!.images!![pass.getReadPosition()]

                    VulkanTexture.transitionLayout(image,
                        VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        commandBuffer = this)

                    vkCmdCopyImageToBuffer(this, image,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        sb.vulkanBuffer,
                        regions)

                    VulkanTexture.transitionLayout(image,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                        commandBuffer = this)

                    this.endCommandBuffer(device, commandPools.Render, queue,
                        flush = true, dealloc = true)
                }

//            vkQueueWaitIdle(queue)

//                sb.copyTo(imageBuffer!!)
                encoder?.encodeFrame(sb.mapIfUnmapped().getByteBuffer(imageByteSize.toInt()))
//                sb.close()
            }

            /*
            thread {
                try {
                    val file = File(System.getProperty("user.home"), "Desktop" + File.separator + "$applicationName - ${SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(Date())}.png")
                    imageBuffer.rewind()

                    val imageArray = ByteArray(imageBuffer.remaining())
                    imageBuffer.get(imageArray)
                    val shifted = ByteArray(imageArray.size)

                    // swizzle BGRA -> ABGR
                    for (i in 0..shifted.size - 1 step 4) {
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
                    System.err.println("Unable to take screenshot: ")
                    e.printStackTrace()
                } finally {
                    memFree(imageBuffer)
                }
            }
            */

            screenshotRequested = false
        }

        val presentDuration = System.nanoTime() - startPresent
        stats?.add("Renderer.present", presentDuration)

        submitInfo.free()
    }

    /**
     * This function renders the scene
     */
    @Synchronized override fun render() {
        pollEvents()

        val stats = hub?.get(SceneryElement.Statistics) as? Statistics

        // check whether scene is already initialized
        if (scene.children.count() == 0 || !scene.initialized) {
            initializeScene()

            Thread.sleep(200)
            return
        }

        if (toggleFullscreen) {
            vkDeviceWaitIdle(device.vulkanDevice)

            switchFullscreen()
            toggleFullscreen = false
            return
        }

        if (window.shouldClose) {
            shouldClose = true
            // stop all
            vkDeviceWaitIdle(device.vulkanDevice)
            return
        }

        if(renderDelay > 0) {
            logger.warn("Delaying next frame for $renderDelay ms, as one or more validation error have occured in the previous frame.")
            Thread.sleep(renderDelay)
        }

        val startUboUpdate = System.nanoTime()
        updateDefaultUBOs(device)
        updateInstanceBuffers()
        stats?.add("Renderer.updateUBOs", System.nanoTime() - startUboUpdate)

        beginFrame()

        // firstWaitSemaphore is now the RenderComplete semaphore of the previous pass
        firstWaitSemaphore.put(0, semaphores[StandardSemaphores.PresentComplete]!![0])

        val si = VkSubmitInfo.calloc()

        var waitSemaphore = semaphores[StandardSemaphores.PresentComplete]!![0]

        flow.take(flow.size - 1).forEachIndexed { i, t ->
            val start = System.nanoTime()
            logger.trace("Running pass {}", t)
            val target = renderpasses[t]!!
            val commandBuffer = target.commandBuffer

            if (commandBuffer.submitted) {
                commandBuffer.waitForFence()
                commandBuffer.submitted = false
                commandBuffer.resetFence()

                val timing = intArrayOf(0,0)
                VU.run("getting query pool results", { vkGetQueryPoolResults(device.vulkanDevice, timestampQueryPool, 2*i, 2, timing, 0, VK_FLAGS_NONE)})

                stats?.add("Renderer.$t.gpuTiming", timing[1] - timing[0])
            }

            when (target.passConfig.type) {
                RenderConfigReader.RenderpassType.geometry -> recordSceneRenderCommands(device, target, commandBuffer)
                RenderConfigReader.RenderpassType.quad -> recordPostprocessRenderCommands(device, target, commandBuffer)
            }

            target.updateShaderParameters()

            target.submitCommandBuffers.put(0, commandBuffer.commandBuffer)
            target.signalSemaphores.put(0, target.semaphore)
            target.waitSemaphores.put(0, waitSemaphore)
            target.waitStages.put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)

            si.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pNext(NULL)
                .waitSemaphoreCount(1)
                .pWaitDstStageMask(target.waitStages)
                .pCommandBuffers(target.submitCommandBuffers)
                .pSignalSemaphores(target.signalSemaphores)
                .pWaitSemaphores(target.waitSemaphores)

            VU.run("Submit pass $t render queue", { vkQueueSubmit(queue, si, commandBuffer.getFence() )})

            commandBuffer.submitted = true
            firstWaitSemaphore.put(0, target.semaphore)
            waitSemaphore = target.semaphore

            stats?.add("VulkanRenderer.$t.recordCmdBuffer", System.nanoTime() - start)
        }

        si.free()

        val viewportPass = renderpasses.values.last()
        val viewportCommandBuffer = viewportPass.commandBuffer
        logger.trace("Running viewport pass {}", renderpasses.keys.last())

        when (viewportPass.passConfig.type) {
            RenderConfigReader.RenderpassType.geometry -> recordSceneRenderCommands(device, viewportPass, viewportCommandBuffer)
            RenderConfigReader.RenderpassType.quad -> recordPostprocessRenderCommands(device, viewportPass, viewportCommandBuffer)
        }

        if(viewportCommandBuffer.submitted) {
            viewportCommandBuffer.waitForFence()
            viewportCommandBuffer.submitted = false
            viewportCommandBuffer.resetFence()
        }

        viewportPass.updateShaderParameters()

        ph.commandBuffers.put(0, viewportCommandBuffer.commandBuffer)
        ph.waitStages.put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
        ph.signalSemaphore.put(0, semaphores[StandardSemaphores.RenderComplete]!![0])
        ph.waitSemaphore.put(0, firstWaitSemaphore.get(0))

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

    private fun createInstance(requiredExtensions: PointerBuffer): VkInstance {
        return stackPush().use { stack ->
            val appInfo = VkApplicationInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8(applicationName))
                .pEngineName(stack.UTF8("scenery"))
                .apiVersion(VK_MAKE_VERSION(1, 0, 54))

            val additionalExts: List<String> = hub?.getWorkingHMDDisplay()?.getVulkanInstanceExtensions() ?: listOf()
            val utf8Exts = additionalExts.map { stack.UTF8(it) }

            logger.debug("HMD required instance exts: ${additionalExts.joinToString(", ")} ${additionalExts.size}")

            // allocate enough pointers for already pre-required extensions, plus HMD-required extensions, plus the debug extension
            val enabledExtensionNames = stack.callocPointer(requiredExtensions.remaining() + additionalExts.size + 1)
            enabledExtensionNames.put(requiredExtensions)
            enabledExtensionNames.put(stack.UTF8(VK_EXT_DEBUG_REPORT_EXTENSION_NAME))
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

            val createInfo = VkInstanceCreateInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pNext(NULL)
                .pApplicationInfo(appInfo)
                .ppEnabledExtensionNames(enabledExtensionNames)
                .ppEnabledLayerNames(enabledLayerNames)

            val instance = VU.getPointer("Creating Vulkan instance",
                { vkCreateInstance(createInfo, null, this) }, {})

            VkInstance(instance, createInfo)
        }
    }

    private fun setupDebugging(instance: VkInstance, flags: Int, callback: VkDebugReportCallbackEXT): Long {
        val dbgCreateInfo = VkDebugReportCallbackCreateInfoEXT.calloc()
            .sType(VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT)
            .pNext(NULL)
            .pfnCallback(callback)
            .pUserData(NULL)
            .flags(flags)

        val pCallback = memAllocLong(1)
        try {
            val err = vkCreateDebugReportCallbackEXT(instance, dbgCreateInfo, null, pCallback)
            val callbackHandle = pCallback.get(0)
            memFree(pCallback)
            dbgCreateInfo.free()
            if (err != VK_SUCCESS) {
                throw AssertionError("Failed to create VkInstance: " + VU.translate(err))
            }
            return callbackHandle
        } catch(e: NullPointerException) {
            return -1
        }
    }

    private fun createCommandPool(device: VulkanDevice, queueNodeIndex: Int): Long {
        return stackPush().use { stack ->
            val cmdPoolInfo = VkCommandPoolCreateInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .queueFamilyIndex(queueNodeIndex)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)

            val pCmdPool = stack.callocLong(1)
            val err = vkCreateCommandPool(device.vulkanDevice, cmdPoolInfo, null, pCmdPool)
            val commandPool = pCmdPool.get(0)

            if (err != VK_SUCCESS) {
                throw AssertionError("Failed to create command pool: " + VU.translate(err))
            }

            commandPool
        }
    }

    private fun destroyCommandPool(device: VulkanDevice, commandPool: Long) {
        vkDestroyCommandPool(device.vulkanDevice, commandPool, null)
    }

    private fun createVertexBuffers(device: VulkanDevice, node: Node, state: VulkanObjectState): VulkanObjectState {
        val n = node as HasGeometry

        if (n.texcoords.remaining() == 0 && node.instanceMaster) {
            n.texcoords = je_calloc(1, 4L * n.vertices.remaining() / n.vertexSize * n.texcoordSize).asFloatBuffer()
        }

        val vertexAllocationBytes: Long = 4L * (n.vertices.remaining() + n.normals.remaining() + n.texcoords.remaining())
        val indexAllocationBytes: Long = 4L * n.indices.remaining()
        val fullAllocationBytes: Long = vertexAllocationBytes + indexAllocationBytes

        val stridedBuffer = je_malloc(fullAllocationBytes)

        if(stridedBuffer == null) {
            logger.error("Allocation failed.")
        }

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
            fullAllocationBytes * 1L,
            VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
            wantAligned = false)

        stagingBuffer.copyFrom(stridedBuffer)

        val vertexBuffer = if(state.vertexBuffers.containsKey("vertex+index") && state.vertexBuffers["vertex+index"]?.size == fullAllocationBytes) {
            logger.debug("Reusing existing vertex+index buffer for {} update", node.name)
            state.vertexBuffers["vertex+index"]!!
        } else {
            logger.debug("Creating new vertex+index buffer for {} with {} bytes", node.name, fullAllocationBytes)
            state.vertexBuffers["vertex+index"]?.close()
            VulkanBuffer(device,
                fullAllocationBytes,
                VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT or VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                wantAligned = false)
        }

        with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
            val copyRegion = VkBufferCopy.calloc(1)
                .size(fullAllocationBytes * 1L)

            vkCmdCopyBuffer(this,
                stagingBuffer.vulkanBuffer,
                vertexBuffer.vulkanBuffer,
                copyRegion)

            copyRegion.free()
            this.endCommandBuffer(device, commandPools.Standard, queue, flush = true, dealloc = true)
        }

        state.vertexBuffers.put("vertex+index", vertexBuffer)
        state.indexOffset = vertexAllocationBytes
        state.indexCount = n.indices.remaining()

        je_free(stridedBuffer)
        stagingBuffer.close()

        return state
    }

    private fun createInstanceBuffer(device: VulkanDevice, parentNode: Node, state: VulkanObjectState): VulkanObjectState {
        val instances = ArrayList<Node>()

        // return if no observer found
        val cam = scene.activeObserver ?: scene.findObserver() ?: return state

        cam.view = cam.getTransformation()

        scene.discover(scene, { n -> n.instanceOf == parentNode }).forEach {
            instances.add(it)
        }

        if (instances.size < 1) {
            logger.info("$parentNode has no child instances attached, returning.")
            return state
        }

        // first we create a fake UBO to gauge the size of the needed properties
        val ubo = VulkanUBO(device)
        ubo.fromInstance(instances.first())

        val instanceBufferSize = ubo.getSize() * instances.size

        logger.debug("$parentNode has ${instances.size} child instances with ${ubo.getSize()} bytes each.")
        logger.debug("Creating staging buffer...")

        val stagingBuffer = VulkanBuffer(device,
            instanceBufferSize * 1L,
            VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
            wantAligned = false)

        instances.forEach { node ->
            node.updateWorld(true, false)

            node.projection.copyFrom(cam.projection)
            node.projection.set(1, 1, -1.0f * cam.projection.get(1, 1))

            node.modelView.copyFrom(cam.view)
            node.modelView.mult(node.world)

            node.mvp.copyFrom(node.projection)
            node.mvp.mult(node.modelView)

            val instanceUbo = VulkanUBO(device, backingBuffer = stagingBuffer)
            instanceUbo.fromInstance(node)
            instanceUbo.createUniformBuffer()
            instanceUbo.populate()
        }

        logger.debug("Copying from staging buffer")
        stagingBuffer.copyFromStagingBuffer()

        // the actual instance buffer is kept device-local for performance reasons
        val instanceBuffer = VulkanBuffer(device,
            instanceBufferSize * 1L,
            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK_BUFFER_USAGE_TRANSFER_DST_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
            wantAligned = false)

        with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
            val copyRegion = VkBufferCopy.calloc(1)
                .size(instanceBufferSize * 1L)

            vkCmdCopyBuffer(this,
                stagingBuffer.vulkanBuffer,
                instanceBuffer.vulkanBuffer,
                copyRegion)

            this.endCommandBuffer(device, commandPools.Standard, queue, flush = true, dealloc = true)
        }

        state.vertexBuffers.put("instance", instanceBuffer)
        state.instanceCount = instances.size

        stagingBuffer.close()

        logger.debug("Instance buffer creation done")

        return state
    }

    private fun updateInstanceBuffer(device: VulkanDevice, parentNode: Node, state: VulkanObjectState): VulkanObjectState {
        val instances = ArrayList<Node>()
        // return if no observer found
        val cam = scene.findObserver() ?: return state

        scene.discover(scene, { n -> n.instanceOf == parentNode }).forEach {
            instances.add(it)
        }

        if (instances.size < 1) {
            logger.debug("$parentNode has no child instances attached, returning.")
            return state
        }

        // first we create a fake UBO to gauge the size of the needed properties
        val ubo = VulkanUBO(device)
        ubo.fromInstance(instances.first())

        val instanceBufferSize = ubo.getSize() * instances.size

        val stagingBuffer = VulkanBuffer(device,
            instanceBufferSize * 1L,
            VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
            wantAligned = true)

        instances.forEach { node ->
            node.updateWorld(true, false)

            node.projection.copyFrom(cam.projection)
            node.projection.set(1, 1, -1.0f * cam.projection.get(1, 1))

            node.modelView.copyFrom(cam.view)
            node.modelView.mult(node.world)

            node.mvp.copyFrom(node.projection)
            node.mvp.mult(node.modelView)

            val instanceUbo = VulkanUBO(device, backingBuffer = stagingBuffer)
            instanceUbo.fromInstance(node)
            instanceUbo.createUniformBuffer()
            instanceUbo.populate()
        }

        stagingBuffer.copyFromStagingBuffer()

        val instanceBuffer = if (state.vertexBuffers.containsKey("instance") && state.vertexBuffers["instance"]!!.size >= instanceBufferSize) {
            state.vertexBuffers["instance"]!!
        } else {
            logger.debug("Instance buffer for ${parentNode.name} needs to be required, insufficient size ($instanceBufferSize vs ${state.vertexBuffers["instance"]!!.size})")
            state.vertexBuffers["instance"]?.close()

            val buffer = VulkanBuffer(device,
                instanceBufferSize * 1L,
                VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                wantAligned = true)

            state.vertexBuffers.put("instance", buffer)
            buffer
        }

        with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
            val copyRegion = VkBufferCopy.calloc(1)
                .size(instanceBufferSize * 1L)

            vkCmdCopyBuffer(this,
                stagingBuffer.vulkanBuffer,
                instanceBuffer.vulkanBuffer,
                copyRegion)

            copyRegion.free()
            this.endCommandBuffer(device, commandPools.Standard, queue, flush = true, dealloc = true)
        }

        state.instanceCount = instances.size

        stagingBuffer.close()
        return state
    }

    private fun createDescriptorPool(device: VulkanDevice): Long {
        return stackPush().use { stack ->
            // We need to tell the API the number of max. requested descriptors per type
            val typeCounts = VkDescriptorPoolSize.callocStack(4, stack)
            typeCounts[0]
                .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(this.MAX_TEXTURES)

            typeCounts[1]
                .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                .descriptorCount(this.MAX_UBOS)

            typeCounts[2]
                .type(VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT)
                .descriptorCount(this.MAX_INPUT_ATTACHMENTS)

            typeCounts[3]
                .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(this.MAX_UBOS)

            // Create the global descriptor pool
            // All descriptors used in this example are allocated from this pool
            val descriptorPoolInfo = VkDescriptorPoolCreateInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .pNext(NULL)
                .pPoolSizes(typeCounts)
                .maxSets(this.MAX_TEXTURES + this.MAX_UBOS + this.MAX_INPUT_ATTACHMENTS + this.MAX_UBOS)// Set the max. number of sets that can be requested
                .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)

            val descriptorPool = VU.getLong("vkCreateDescriptorPool",
                { vkCreateDescriptorPool(device.vulkanDevice, descriptorPoolInfo, null, this) }, {})

            descriptorPool
        }
    }

    private fun prepareDefaultBuffers(device: VulkanDevice, bufferStorage: ConcurrentHashMap<String, VulkanBuffer>) {
        logger.debug("Creating buffers")

        bufferStorage.put("UBOBuffer", VulkanBuffer(device,
            512 * 1024 * 10,
            VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            wantAligned = true))
        logger.debug("Created UBO buffer")

        bufferStorage.put("LightParametersBuffer", VulkanBuffer(device,
            512 * 1024 * 10,
            VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            wantAligned = true))
        logger.debug("Created light buffer")

        bufferStorage.put("VRParametersBuffer", VulkanBuffer(device,
            256 * 10,
            VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            wantAligned = true))
        logger.debug("Created VRP buffer")

        bufferStorage.put("ShaderPropertyBuffer", VulkanBuffer(device,
            1024 * 10,
            VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            wantAligned = true))
        logger.debug("Created all buffers")
    }

    private fun Node.rendererMetadata(): VulkanObjectState? {
        return this.metadata["VulkanRenderer"] as? VulkanObjectState
    }

    private fun recordSceneRenderCommands(device: VulkanDevice, pass: VulkanRenderpass, commandBuffer: VulkanCommandBuffer) {
        val target = pass.getOutput()

        logger.trace("Creating scene command buffer for {}/{} ({} attachments)", pass.name, target, target.attachments.count())

        pass.vulkanMetadata.renderPassBeginInfo
            .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
            .pNext(NULL)
            .renderPass(target.renderPass.get(0))
            .framebuffer(target.framebuffer.get(0))
            .renderArea(pass.vulkanMetadata.renderArea)
            .pClearValues(pass.vulkanMetadata.clearValues)

        val renderOrderList = ArrayList<Node>(pass.vulkanMetadata.renderLists[commandBuffer]?.size ?: 512)
        var forceRerecording = false
        val rerecordingCauses = ArrayList<String>(20)

        // here we discover all the nodes which are relevant for this pass,
        // e.g. which have the same transparency settings as the pass
        scene.discover(scene, { n -> n is HasGeometry
            && n.visible
            && n.material.blending.transparent == pass.passConfig.renderTransparent}).forEach {
            // if a node is not initialized yet, it'll be initialized here and it's UBO updated
            // in the next round
            if(it.rendererMetadata() == null) {
                logger.debug("${it.name} is not initialized, doing that now")
                it.metadata.put("VulkanRenderer", VulkanObjectState())
                initializeNode(it)

                return@forEach
            }

            // the current command buffer will be forced to be re-recorded if either geometry, blending or
            // texturing of a given node have changed, as these might change pipelines or descriptor sets, leading
            // to the original command buffer becoming obsolete.
            it.rendererMetadata()?.let { metadata ->
                if (it.dirty) {
                    logger.debug("Force command buffer re-recording, as geometry for ${it.name} has been updated")
                    if (it is TextBoard) {
                        updateTextBoard(it)
                    } else {
                        updateNodeGeometry(it)
                        it.dirty = false
                    }

                    rerecordingCauses.add(it.name)
                    forceRerecording = true
                }

                if (it.material.needsTextureReload) {
                    logger.debug("Force command buffer re-recording, as reloading textures for ${it.name}")
                    loadTexturesForNode(it, metadata)

                    it.material.needsTextureReload = false

                    rerecordingCauses.add(it.name)
                    forceRerecording = true
                }

                if(it.material.blending.hashCode() != metadata.blendingHashCode) {
                    logger.debug("Force command buffer re-recording, as blending options for ${it.name} have changed")
                    initializeCustomShadersForNode(it)
                    metadata.blendingHashCode = it.material.blending.hashCode()

                    rerecordingCauses.add(it.name)
                    forceRerecording = true
                }

                if (!((pass.passConfig.renderOpaque && it.material.blending.transparent && pass.passConfig.renderOpaque != pass.passConfig.renderTransparent) ||
                    (pass.passConfig.renderTransparent && !it.material.blending.transparent && pass.passConfig.renderOpaque != pass.passConfig.renderTransparent))) {
                    renderOrderList.add(it)
                }
            }
        }

        // if the pass' metadata does not contain a commond buffer,
        // OR the cached command buffer does not contain the same nodes in the same order,
        // OR re-recording is forced due to node changes, the buffer will be re-recorded.
        // Furthermore, all sibling command buffers for this pass will be marked stale, thus
        // also forcing their re-recording.
        if(!pass.vulkanMetadata.renderLists.containsKey(commandBuffer)
            || !renderOrderList.toTypedArray().contentDeepEquals(pass.vulkanMetadata.renderLists[commandBuffer]!!)
            || forceRerecording) {

            pass.vulkanMetadata.renderLists.put(commandBuffer, renderOrderList.toTypedArray())
            pass.vulkanMetadata.renderLists.keys.forEach { it.stale = true }
        }

        // If the command buffer is not stale, though, we keep the cached one and return. This
        // can buy quite a bit of performance.
        if(!commandBuffer.stale && commandBuffer.commandBuffer != null) {
            return
        }

        logger.debug("Recording command buffer $commandBuffer...")

        // start command buffer recording
        val instanceGroups = renderOrderList.groupBy(Node::instanceOf)

        if (commandBuffer.commandBuffer == null) {
            commandBuffer.commandBuffer = VU.newCommandBuffer(device, commandPools.Render, autostart = true)
        } else {
            vkResetCommandBuffer(commandBuffer.commandBuffer!!, VK_FLAGS_NONE)
            VU.beginCommandBuffer(commandBuffer.commandBuffer!!)
        }

        with(commandBuffer.commandBuffer) {

            vkCmdWriteTimestamp(this, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                timestampQueryPool, 2*renderpasses.values.indexOf(pass))

            if(pass.passConfig.blitInputs) {
                stackPush().use { stack ->
                    val imageBlit = VkImageBlit.callocStack(1, stack)

                    for((name, input) in pass.inputs) {
                        for((_, inputAttachment) in input.attachments) {

                            val type = when(inputAttachment.type) {
                                VulkanFramebuffer.VulkanFramebufferType.COLOR_ATTACHMENT -> VK_IMAGE_ASPECT_COLOR_BIT
                                VulkanFramebuffer.VulkanFramebufferType.DEPTH_ATTACHMENT -> VK_IMAGE_ASPECT_DEPTH_BIT
                            }

                            // return to use() if no output with the correct attachment type is found
                            val outputAttachment = pass.getOutput().attachments.values.find { it.type == inputAttachment.type }
                            if(outputAttachment == null) {
                                logger.warn("Didn't find matching attachment for $name of type ${inputAttachment.type}")
                                return@use
                            }

                            val outputAspectType = when(outputAttachment.type) {
                                VulkanFramebuffer.VulkanFramebufferType.DEPTH_ATTACHMENT -> VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                                VulkanFramebuffer.VulkanFramebufferType.COLOR_ATTACHMENT -> VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                            }

                            val inputAspectType = when(inputAttachment.type) {
                                VulkanFramebuffer.VulkanFramebufferType.COLOR_ATTACHMENT -> VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                                VulkanFramebuffer.VulkanFramebufferType.DEPTH_ATTACHMENT -> VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                            }

                            val offsetX = (input.width * pass.passConfig.viewportOffset.first).toInt()
                            val offsetY = (input.height * pass.passConfig.viewportOffset.second).toInt()

                            val sizeX = offsetX + (input.width * pass.passConfig.viewportSize.first).toInt()
                            val sizeY = offsetY + (input.height * pass.passConfig.viewportSize.second).toInt()

                            imageBlit.srcSubresource().set(type, 0, 0, 1)
                            imageBlit.srcOffsets(0).set(offsetX, offsetY, 0)
                            imageBlit.srcOffsets(1).set(sizeX, sizeY, 1)

                            imageBlit.dstSubresource().set(type, 0, 0, 1)
                            imageBlit.dstOffsets(0).set(offsetX, offsetY, 0)
                            imageBlit.dstOffsets(1).set(sizeX, sizeY, 1)

                            val transitionBuffer = this@with!!

                            val subresourceRange = VkImageSubresourceRange.callocStack(stack)
                                .aspectMask(type)
                                .baseMipLevel(0)
                                .levelCount(1)
                                .baseArrayLayer(0)
                                .layerCount(1)

                            // transition source attachment
                            VulkanTexture.transitionLayout(inputAttachment.image,
                                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                                subresourceRange = subresourceRange,
                                commandBuffer = transitionBuffer,
                                srcStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                                dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT
                            )

                            // transition destination attachment
                            VulkanTexture.transitionLayout(outputAttachment.image,
                                inputAspectType,
                                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                                subresourceRange = subresourceRange,
                                commandBuffer = transitionBuffer,
                                srcStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                                dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT
                            )

                            vkCmdBlitImage(this@with,
                                inputAttachment.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                                outputAttachment.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                                imageBlit, VK_FILTER_NEAREST
                            )

                            // transition destination attachment back to attachment
                            VulkanTexture.transitionLayout(outputAttachment.image,
                                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                                outputAspectType,
                                subresourceRange = subresourceRange,
                                commandBuffer = transitionBuffer,
                                srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                                dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
                            )

                            // transition source attachment back to shader read-only
                            VulkanTexture.transitionLayout(inputAttachment.image,
                                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                                outputAspectType,
                                subresourceRange = subresourceRange,
                                commandBuffer = transitionBuffer,
                                srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                                dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
                            )

                        }
                    }
                }
            }

            vkCmdBeginRenderPass(this, pass.vulkanMetadata.renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)

            vkCmdSetViewport(this, 0, pass.vulkanMetadata.viewport)
            vkCmdSetScissor(this, 0, pass.vulkanMetadata.scissor)

            // allocate more vertexBufferOffsets than needed, set limit lateron
            pass.vulkanMetadata.uboOffsets.limit(16)
            (0..15).forEach { pass.vulkanMetadata.uboOffsets.put(it, 0) }

            instanceGroups[null]?.forEach nonInstancedDrawing@ { node ->
                val s = node.rendererMetadata()!!

                if(pass.passConfig.renderOpaque && node.material.blending.transparent && pass.passConfig.renderOpaque != pass.passConfig.renderTransparent) {
                    return@nonInstancedDrawing
                }

                if(pass.passConfig.renderTransparent && !node.material.blending.transparent && pass.passConfig.renderOpaque != pass.passConfig.renderTransparent) {
                    return@nonInstancedDrawing
                }

                if (node in instanceGroups.keys || s.vertexCount == 0) {
                    return@nonInstancedDrawing
                }

                logger.debug("Rendering ${node.name}...")
                if(rerecordingCauses.contains(node.name)) {
                    logger.debug("Using pipeline ${pass.getActivePipeline(node)} for re-recording")
                }
                val pipeline = pass.getActivePipeline(node).getPipelineForGeometryType((node as HasGeometry).geometryType)
                val specs = pass.getActivePipeline(node).orderedDescriptorSpecs()

                logger.trace("node {} has: {} / pipeline needs: {}", node.name, s.UBOs.keys.joinToString(", "), specs.joinToString { it.key })

                pass.vulkanMetadata.descriptorSets.rewind()
                pass.vulkanMetadata.uboOffsets.rewind()

                pass.vulkanMetadata.vertexBufferOffsets.put(0, 0)
                pass.vulkanMetadata.vertexBuffers.put(0, s.vertexBuffers["vertex+index"]!!.vulkanBuffer)

                val sets = specs.map { (name, _) ->
                    when(name) {
                        "VRParameters" -> {
                            DescriptorSet.DynamicSet(descriptorSets["VRParameters"]!!, offset = 0, setName = "VRParameters")
                        }

                        "LightParameters" -> {
                            DescriptorSet.DynamicSet(descriptorSets["LightParameters"]!!, offset = 0, setName = "LightParameters")
                        }

                        "ObjectTextures" -> {
                            DescriptorSet.Set(s.textureDescriptorSet, setName = "ObjectTextures")
                        }

                        "Inputs" -> {
                            DescriptorSet.Set(pass.descriptorSets["inputs-${pass.name}"]!!, setName = "Inputs")
                        }

                        else -> {
                            if (s.UBOs.containsKey(name)) {
                                DescriptorSet.DynamicSet(s.UBOs[name]!!.first, offset = s.UBOs[name]!!.second.offsets.get(0), setName = name)
                            } else {
                                DescriptorSet.None
                            }
                        }
                    }
                }

                val requiredSets = sets.filter { it !is DescriptorSet.None }.map { it.id }.toLongArray()
                if(pass.vulkanMetadata.descriptorSets.capacity() < requiredSets.size) {
                    logger.info("Reallocating descriptor set storage")
                    memFree(pass.vulkanMetadata.descriptorSets)
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

                vkCmdBindPipeline(this, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.pipeline)
                vkCmdBindDescriptorSets(this, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipeline.layout, 0, pass.vulkanMetadata.descriptorSets, pass.vulkanMetadata.uboOffsets)
                vkCmdBindVertexBuffers(this, 0, pass.vulkanMetadata.vertexBuffers, pass.vulkanMetadata.vertexBufferOffsets)

                vkCmdPushConstants(this, pipeline.layout, VK_SHADER_STAGE_ALL, 0, pass.vulkanMetadata.eye)

                logger.trace("now drawing {}, {} DS bound, {} textures", node.name, pass.vulkanMetadata.descriptorSets.capacity(), s.textures.count())

                if (s.isIndexed) {
                    vkCmdBindIndexBuffer(this, pass.vulkanMetadata.vertexBuffers.get(0), s.indexOffset, VK_INDEX_TYPE_UINT32)
                    vkCmdDrawIndexed(this, s.indexCount, 1, 0, 0, 0)
                } else {
                    vkCmdDraw(this, s.vertexCount, 1, 0, 0)
                }
            }

            instanceGroups.keys.filterNotNull().forEach instancedDrawing@ { node ->
                val s = node.rendererMetadata()!!

                // this only lets non-instanced, parent nodes through
                if (s.vertexCount == 0) {
                    return@instancedDrawing
                }

                pass.vulkanMetadata.vertexBufferOffsets.put(0, 0)
                pass.vulkanMetadata.vertexBuffers.put(0, s.vertexBuffers["vertex+index"]!!.vulkanBuffer)
                pass.vulkanMetadata.descriptorSets.put(0, s.UBOs["Matrices"]!!.first)

                if (s.textures.size > 0) {
                    pass.vulkanMetadata.descriptorSets.put(1, s.textureDescriptorSet)
                    pass.vulkanMetadata.descriptorSets.put(2, descriptorSets["VRParameters"]!!)
                    pass.vulkanMetadata.descriptorSets.limit(3)
                } else {
                    pass.vulkanMetadata.descriptorSets.put(1, descriptorSets["VRParameters"]!!)
                    pass.vulkanMetadata.descriptorSets.limit(2)
                }

                pass.vulkanMetadata.instanceBuffers.put(0, s.vertexBuffers["instance"]!!.vulkanBuffer)

                val pipeline = pass.getActivePipeline(node).getPipelineForGeometryType((node as HasGeometry).geometryType)

                pass.vulkanMetadata.uboOffsets.position(0)
                s.UBOs["Matrices"]!!.second.offsets.position(0)

                pass.vulkanMetadata.uboOffsets.put(s.UBOs["Matrices"]!!.second.offsets)
                pass.vulkanMetadata.uboOffsets.put(0)
                pass.vulkanMetadata.uboOffsets.flip()

                vkCmdBindPipeline(this, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.pipeline)
                vkCmdBindDescriptorSets(this, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipeline.layout, 0, pass.vulkanMetadata.descriptorSets, pass.vulkanMetadata.uboOffsets)

                vkCmdBindVertexBuffers(this, 0, pass.vulkanMetadata.vertexBuffers, pass.vulkanMetadata.vertexBufferOffsets)
                vkCmdBindVertexBuffers(this, 1, pass.vulkanMetadata.instanceBuffers, pass.vulkanMetadata.vertexBufferOffsets)

                if (s.isIndexed) {
                    vkCmdBindIndexBuffer(this, pass.vulkanMetadata.vertexBuffers.get(0), s.indexOffset, VK_INDEX_TYPE_UINT32)
                    vkCmdDrawIndexed(this, s.indexCount, s.instanceCount, 0, 0, 0)
                } else {
                    vkCmdDraw(this, s.vertexCount, s.instanceCount, 0, 0)
                }
            }

            vkCmdEndRenderPass(this)

            vkCmdWriteTimestamp(this, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                timestampQueryPool, 2*renderpasses.values.indexOf(pass)+1)

            // finish command buffer recording by marking this buffer non-stale
            commandBuffer.stale = false
            this!!.endCommandBuffer()
        }
    }

    private fun recordPostprocessRenderCommands(device: VulkanDevice, pass: VulkanRenderpass, commandBuffer: VulkanCommandBuffer) {
        val target = pass.getOutput()

        logger.trace("Creating postprocessing command buffer for {}/{} ({} attachments)", pass.name, target, target.attachments.count())

        pass.vulkanMetadata.renderPassBeginInfo
            .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
            .pNext(NULL)
            .renderPass(target.renderPass.get(0))
            .framebuffer(target.framebuffer.get(0))
            .renderArea(pass.vulkanMetadata.renderArea)
            .pClearValues(pass.vulkanMetadata.clearValues)

        if(!commandBuffer.stale) {
            return
        }

        // start command buffer recording
        if (commandBuffer.commandBuffer == null) {
            commandBuffer.commandBuffer = VU.newCommandBuffer(device, commandPools.Render, autostart = true)
        } else {
            vkResetCommandBuffer(commandBuffer.commandBuffer!!, VK_FLAGS_NONE)
            VU.beginCommandBuffer(commandBuffer.commandBuffer!!)
        }

        with(commandBuffer.commandBuffer) {

            vkCmdWriteTimestamp(this, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                timestampQueryPool, 2*renderpasses.values.indexOf(pass))
            vkCmdBeginRenderPass(this, pass.vulkanMetadata.renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)

            vkCmdSetViewport(this, 0, pass.vulkanMetadata.viewport)
            vkCmdSetScissor(this, 0, pass.vulkanMetadata.scissor)

            val pipeline = pass.getDefaultPipeline()
            val vulkanPipeline = pipeline.getPipelineForGeometryType(GeometryType.TRIANGLES)

            if (pass.vulkanMetadata.descriptorSets.capacity() != pipeline.descriptorSpecs.count()) {
                memFree(pass.vulkanMetadata.descriptorSets)
                pass.vulkanMetadata.descriptorSets = memAllocLong(pipeline.descriptorSpecs.count())
            }

            // allocate more vertexBufferOffsets than needed, set limit lateron
            pass.vulkanMetadata.uboOffsets.limit(16)
            (0..15).forEach { pass.vulkanMetadata.uboOffsets.put(it, 0) }

            if (logger.isDebugEnabled) {
                logger.debug("descriptor sets are {}", pass.descriptorSets.keys.joinToString(", "))
                logger.debug("pipeline provides {}", pipeline.descriptorSpecs.keys.joinToString(", "))
            }

            // set the required descriptor sets for this render pass
            pass.vulkanMetadata.setRequiredDescriptorSetsPostprocess(pass, pipeline)

            vkCmdBindPipeline(this, VK_PIPELINE_BIND_POINT_GRAPHICS, vulkanPipeline.pipeline)
            vkCmdBindDescriptorSets(this, VK_PIPELINE_BIND_POINT_GRAPHICS,
                vulkanPipeline.layout, 0, pass.vulkanMetadata.descriptorSets, pass.vulkanMetadata.uboOffsets)

            vkCmdDraw(this, 3, 1, 0, 0)

            vkCmdEndRenderPass(this)
            vkCmdWriteTimestamp(this, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                timestampQueryPool, 2*renderpasses.values.indexOf(pass)+1)

            commandBuffer.stale = false
            this!!.endCommandBuffer()
        }
    }

    private fun VulkanRenderpass.VulkanMetadata.setRequiredDescriptorSetsPostprocess(pass: VulkanRenderpass, pipeline: VulkanPipeline): Int {
        var requiredDynamicOffsets = 0

        pipeline.descriptorSpecs.entries.sortedBy { it.value.set }.forEachIndexed { i, (name, _) ->
            val dsName = if (name.startsWith("ShaderParameters")) {
                "ShaderParameters-${pass.name}"
            } else if (name.startsWith("Inputs")) {
                "inputs-${pass.name}"
            } else if (name.startsWith("Matrices")) {
                val offsets = sceneUBOs.first().rendererMetadata()!!.UBOs["Matrices"]!!.second.offsets
                this.uboOffsets.put(offsets)
                requiredDynamicOffsets += 3

                "Matrices"
            } else {
                if (name.startsWith("LightParameters")) {
                    this.uboOffsets.put(0)
                    requiredDynamicOffsets++
                }

                name
            }

            val set = if (dsName == "Matrices" || dsName == "LightParameters") {
                this@VulkanRenderer.descriptorSets[dsName]
            } else {
                pass.descriptorSets[dsName]
            }

            if (set != null) {
                logger.debug("Adding DS#{} for {} to required pipeline DSs", i, dsName)
                this.descriptorSets.put(i, set)
            } else {
                logger.error("DS for {} not found!", dsName)
            }
        }

        this.uboOffsets.limit(requiredDynamicOffsets)
        this.uboOffsets.position(0)

        return requiredDynamicOffsets
    }

    private fun updateInstanceBuffers() {
        val renderOrderList = ArrayList<Node>()

        scene.discover(scene, { n -> n is HasGeometry && n.visible }).forEach {
            renderOrderList.add(it)
        }

        val instanceGroups = renderOrderList.groupBy(Node::instanceOf)

        instanceGroups.keys.filterNotNull().forEach { node ->
            updateInstanceBuffer(device, node, node.rendererMetadata()!!)
        }
    }

    fun GLMatrix.applyVulkanCoordinateSystem(): GLMatrix {
        val m = vulkanProjectionFix.clone()
        m.mult(this)

        return m
    }

    private fun Display.wantsVR(): Display? {
        if (settings.get<Boolean>("vr.Active")) {
            return this@wantsVR
        } else {
            return null
        }
    }

    @Synchronized private fun updateDefaultUBOs(device: VulkanDevice) {
        // find observer, if none, return
        val cam = scene.findObserver() ?: return

        if (!cam.lock.tryLock()) {
            return
        }

        val hmd = hub?.getWorkingHMDDisplay()?.wantsVR()

        cam.view = cam.getTransformation()

        buffers["VRParametersBuffer"]!!.reset()
        val vrUbo = VulkanUBO(device, backingBuffer = buffers["VRParametersBuffer"]!!)

        vrUbo.createUniformBuffer()
        vrUbo.add("projection0", { (hmd?.getEyeProjection(0, cam.nearPlaneDistance, cam.farPlaneDistance)
            ?: cam.projection).applyVulkanCoordinateSystem() } )
        vrUbo.add("projection1", { (hmd?.getEyeProjection(1, cam.nearPlaneDistance, cam.farPlaneDistance)
            ?: cam.projection).applyVulkanCoordinateSystem() } )
        vrUbo.add("headShift", { hmd?.getHeadToEyeTransform(0) ?: GLMatrix.getIdentity() })
        vrUbo.add("IPD", { hmd?.getIPD() ?: 0.05f })
        vrUbo.add("stereoEnabled", { renderConfig.stereoEnabled.toInt() })

        vrUbo.populate()
        vrUbo.close()
        buffers["VRParametersBuffer"]!!.copyFromStagingBuffer()

        buffers["UBOBuffer"]!!.reset()
        buffers["ShaderPropertyBuffer"]!!.reset()

        sceneUBOs.forEach { node ->
            node.lock.withLock {
                if (!node.metadata.containsKey("VulkanRenderer")) {
                    return@withLock
                }

                val s = node.rendererMetadata() ?: return@forEach

                val ubo = s.UBOs["Matrices"]!!.second

                node.updateWorld(true, false)

//                if (ubo.offsets.capacity() < 3) {
//                    memFree(ubo.offsets)
//                    ubo.offsets = memAllocInt(3)
//                }
//
//                (0..2).forEach { ubo.offsets.put(it, 0) }
                ubo.offsets.limit(1)

                var bufferOffset = ubo.backingBuffer!!.advance()
                ubo.offsets.put(0, bufferOffset)
                ubo.offsets.limit(1)

                node.projection.copyFrom(cam.projection.applyVulkanCoordinateSystem())

                node.view.copyFrom(cam.view)

                ubo.populate(offset = bufferOffset.toLong())

                val materialUbo = s.UBOs["MaterialProperties"]!!.second
                bufferOffset = ubo.backingBuffer!!.advance()
                materialUbo.offsets.put(0, bufferOffset)
                materialUbo.offsets.limit(1)

                materialUbo.populate(offset = bufferOffset.toLong())

                if(s.requiredDescriptorSets.containsKey("ShaderProperties")) {
                    val propertyUbo = s.UBOs["ShaderProperties"]!!.second
                    // TODO: Correct buffer advancement
                    val offset = propertyUbo.backingBuffer!!.advance()
                    propertyUbo.populate(offset = offset.toLong())
                    propertyUbo.offsets.put(0, offset)
                    propertyUbo.offsets.limit(1)
                }
            }
        }

        buffers["UBOBuffer"]!!.copyFromStagingBuffer()

        buffers["LightParametersBuffer"]!!.reset()

        val lights = scene.discover(scene, { n -> n is PointLight })

        val lightUbo = VulkanUBO(device, backingBuffer = buffers["LightParametersBuffer"]!!)
        lightUbo.add("ViewMatrix", { cam.view })
        lightUbo.add("CamPosition", { cam.position })
        lightUbo.add("numLights", { lights.size })

        lights.forEachIndexed { i, light ->
            val l = light as PointLight
            l.updateWorld(true, false)

            lightUbo.add("Linear-$i", { l.linear })
            lightUbo.add("Quadratic-$i", { l.quadratic })
            lightUbo.add("Intensity-$i", { l.intensity })
            lightUbo.add("Radius-$i",
                { ((-l.linear + Math.sqrt(l.linear * l.linear - 4 * l.quadratic * (1.0 - (256.0f / 5.0) * l.intensity)))/(2 * l.quadratic)).toFloat() })
            lightUbo.add("Position-$i", { l.position })
            lightUbo.add("Color-$i", { l.emissionColor })
            lightUbo.add("filler-$i", { 0.0f })
        }

        lightUbo.createUniformBuffer()
        lightUbo.populate()
        lightUbo.close()

        buffers["LightParametersBuffer"]!!.copyFromStagingBuffer()
        buffers["ShaderPropertyBuffer"]!!.copyFromStagingBuffer()

        cam.lock.unlock()
    }

    @Suppress("UNUSED")
    override fun screenshot() {
        screenshotRequested = true
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

                } catch(e: Exception) {
                    logger.warn("$it is a property that is not togglable.")
                }
            }
        }
    }

    override fun close() {
        logger.info("Renderer teardown started.")
        vkQueueWaitIdle(queue)

        logger.debug("Cleaning texture cache...")
        textureCache.forEach {
            logger.debug("Cleaning ${it.key}...")
            it.value.close()
        }

        logger.debug("Closing nodes...")
        scene.discover(scene, { _ -> true }).forEach {
            destroyNode(it)
        }

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
        descriptorSetLayouts.forEach { vkDestroyDescriptorSetLayout(device.vulkanDevice, it.value, null) }
        vkDestroyDescriptorPool(device.vulkanDevice, descriptorPool, null)

        logger.debug("Closing command buffers...")
        ph.commandBuffers.free()
        memFree(ph.signalSemaphore)
        memFree(ph.waitSemaphore)
        memFree(ph.waitStages)

        if(timestampQueryPool != -1L) {
            logger.debug("Closing query pools...")
            vkDestroyQueryPool(device.vulkanDevice, timestampQueryPool, null)
        }

        semaphores.forEach { it.value.forEach { semaphore -> vkDestroySemaphore(device.vulkanDevice, semaphore, null) } }

        memFree(firstWaitSemaphore)
        semaphoreCreateInfo.free()

        logger.debug("Closing swapchain...")

        swapchain?.close()

        renderpasses.forEach { _, vulkanRenderpass -> vulkanRenderpass.close() }

        with(commandPools) {
            destroyCommandPool(device, Render)
            destroyCommandPool(device, Compute)
            destroyCommandPool(device, Standard)
        }

        vkDestroyPipelineCache(device.vulkanDevice, pipelineCache, null)

        if (validation) {
            vkDestroyDebugReportCallbackEXT(instance, debugCallbackHandle, null)
        }

        debugCallback.free()

        device.close()

        logger.debug("Closing instance...")
        vkDestroyInstance(instance, null)

        logger.debug("Finalizing spirvcrossj process...")
        libspirvcrossj.finalizeProcess()

        logger.info("Renderer teardown complete.")
    }

    override fun reshape(newWidth: Int, newHeight: Int) {

    }

    @Suppress("UNUSED")
    fun toggleFullscreen() {
        toggleFullscreen = !toggleFullscreen
    }

    fun switchFullscreen() {
        hub?.let { hub -> swapchain?.toggleFullscreen(hub, swapchainRecreator) }
    }
}
