package scenery.backends.vulkan

import cleargl.GLMatrix
import cleargl.GLVector
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan.*
import org.lwjgl.glfw.GLFWWindowSizeCallback
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugReport.*
import org.lwjgl.vulkan.KHRSurface.*
import org.lwjgl.vulkan.KHRSwapchain.*
import org.lwjgl.vulkan.VK10.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scenery.*
import scenery.backends.*
import scenery.fonts.SDFFontAtlas
import java.awt.color.ColorSpace
import java.awt.image.*
import java.io.File
import java.nio.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import javax.imageio.ImageIO


/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanRenderer : Renderer {

    // helper classes

    data class PresentHelpers(
        var signalSemaphore: LongBuffer = memAllocLong(1),
        var waitSemaphore: LongBuffer = memAllocLong(1),
        var commandBuffers: PointerBuffer = memAllocPointer(1),
        var waitStages: IntBuffer = memAllocInt(1)
    )

    enum class VertexDataKinds {
        coords_none,
        coords_normals_texcoords,
        coords_texcoords,
        coords_normals
    }

    enum class StandardSemaphores {
        render_complete,
        image_available,
        present_complete
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

    class DeviceAndGraphicsQueueFamily {
        internal var device: VkDevice? = null
        internal var queueFamilyIndex: Int = 0
        internal var memoryProperties: VkPhysicalDeviceMemoryProperties? = null
    }

    class ColorFormatAndSpace {
        internal var colorFormat: Int = 0
        internal var colorSpace: Int = 0
    }

    class Swapchain {
        internal var handle: Long = 0
        internal var images: LongArray? = null
        internal var imageViews: LongArray? = null
    }

    class Pipeline {
        internal var pipeline: Long = 0
        internal var layout: Long = 0
    }

    class GlobalResources {
        var pipelineCache = -1L
        var descriptorPool = -1L
        var memoryProperties: VkPhysicalDeviceProperties? = null
        var physicalDevice: VkPhysicalDevice? = null
        var logicalDevice: VkDevice? = null
        var queue = -1L
    }

    inner class SwapchainRecreator {
        var mustRecreate = true

        fun recreate() {
            logger.info("Recreating Swapchain at frame $frames")
            // create new swapchain with changed surface parameters
            setupCommandBuffer = with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
                val oldChain = swapchain?.handle ?: VK_NULL_HANDLE

                // Create the swapchain (this will also add a memory barrier to initialize the framebuffer images)
                swapchain = createSwapChain(
                    device, physicalDevice,
                    surface, oldChain,
                    window.width, window.height,
                    colorFormatAndSpace.colorFormat,
                    colorFormatAndSpace.colorSpace)

                this.endCommandBuffer(device, commandPools.Standard, queue, flush = true, dealloc = false)

                this
            }

            val pipelineCacheInfo = VkPipelineCacheCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO)
                .pNext(NULL)
                .flags(VK_FLAGS_NONE)

            val refreshResolutionDependentResources = {
                pipelineCache = VU.run(memAllocLong(1), "create pipeline cache",
                    { vkCreatePipelineCache(device, pipelineCacheInfo, null, this) },
                    { pipelineCacheInfo.free() })

                // destroy and recreate all framebuffers
//                rendertargets.values.forEach { rt -> rt.destroy() }
//                rendertargets.clear()


//                rendertargets = prepareFramebuffers(device, physicalDevice, window.width, window.height)
//                renderPipelines = prepareDefaultPipelines(device)
                renderpasses = prepareRenderpassesFromConfig(renderConfig, window.width, window.height)
                semaphores = prepareStandardSemaphores(device)

                // Create render command buffers
                if (renderCommandBuffers != null) {
                    vkResetCommandPool(device, commandPools.Render, VK_FLAGS_NONE)
                }
            }

            refreshResolutionDependentResources.invoke()

            totalFrames = 0
            mustRecreate = false
        }
    }

    var debugCallback = object : VkDebugReportCallbackEXT() {
        override operator fun invoke(flags: Int, objectType: Int, obj: Long, location: Long, messageCode: Int, pLayerPrefix: Long, pMessage: Long, pUserData: Long): Int {
            var type = if (flags and VK_DEBUG_REPORT_ERROR_BIT_EXT == 0) {
                "error"
            } else if (flags and VK_DEBUG_REPORT_WARNING_BIT_EXT == 0) {
                "warning"
            } else if (flags and VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT == 0) {
                "performance warning"
            } else if (flags and VK_DEBUG_REPORT_INFORMATION_BIT_EXT == 0) {
                "information"
            } else {
                "(unknown message type)"
            }

            if (flags and VK_DEBUG_REPORT_DEBUG_BIT_EXT == 1) {
                type + " (debug)"
            }

            logger.info("!! Validation $type: " + VkDebugReportCallbackEXT.getString(pMessage))

            // returning VK_TRUE would lead to the abortion of the offending Vulkan call
            return if (System.getProperty("scenery.VulkanRenderer.StrictValidation", "false").toBoolean()) {
                VK_TRUE
            } else {
                VK_FALSE
            }
        }
    }

    // helper classes end


    // helper vars
    private val VK_FLAGS_NONE: Int = 0
    private var MAX_TEXTURES = 2048*16
    private var MAX_UBOS = 2048
    private var MAX_INPUT_ATTACHMENTS = 32
    private val UINT64_MAX: Long = -1L
    // end helper vars

    override var hub: Hub? = null
    protected var applicationName = ""
    override var settings: Settings = Settings()
    protected var logger: Logger = LoggerFactory.getLogger("VulkanRenderer")
    override var shouldClose = false
    override var managesRenderLoop = false
    var screenshotRequested = false

    var firstWaitSemaphore = memAllocLong(1)

    var scene: Scene = Scene()

    protected var commandPools = CommandPools()
    protected var renderpasses = LinkedHashMap<String, VulkanRenderpass>()
    /** Cache for [SDFFontAtlas]es used for font rendering */
    protected var fontAtlas = HashMap<String, SDFFontAtlas>()

    protected var renderCommandBuffers: Array<VkCommandBuffer>? = null

    protected val validation = java.lang.Boolean.parseBoolean(System.getProperty("scenery.VulkanRenderer.EnableValidation", "false"))
    protected val layers = arrayOf<ByteBuffer>(memUTF8("VK_LAYER_LUNARG_standard_validation"))

    protected var instance: VkInstance

    protected var debugCallbackHandle: Long
    protected var physicalDevice: VkPhysicalDevice
    protected var deviceAndGraphicsQueueFamily: DeviceAndGraphicsQueueFamily
    protected var device: VkDevice
    protected var queueFamilyIndex: Int
    protected var memoryProperties: VkPhysicalDeviceMemoryProperties

    protected var surface: Long

    protected var semaphoreCreateInfo: VkSemaphoreCreateInfo

    // Create static Vulkan resources
    protected var colorFormatAndSpace: ColorFormatAndSpace
    protected var setupCommandBuffer: VkCommandBuffer
    protected var postPresentCommandBuffer: VkCommandBuffer
    protected var queue: VkQueue
    protected var descriptorPool: Long

    protected var standardUBOs = ConcurrentHashMap<String, UBO>()

    protected var swapchain: Swapchain? = null
    protected var pSwapchains: LongBuffer
    protected var swapchainImage: IntBuffer = memAllocInt(1)
    protected var ph = PresentHelpers()

    override var window = SceneryWindow()

    protected val swapchainRecreator: SwapchainRecreator
    protected var pipelineCache: Long = -1L
    protected var vertexDescriptors = ConcurrentHashMap<VertexDataKinds, VertexDescription>()
    protected var sceneUBOs = ConcurrentHashMap<Node, UBO>()
    protected var semaphores = ConcurrentHashMap<StandardSemaphores, Array<Long>>()
    protected var buffers = HashMap<String, VulkanBuffer>()
    protected var textureCache = ConcurrentHashMap<String, VulkanTexture>()
    protected var descriptorSetLayouts = ConcurrentHashMap<String, Long>()
    protected var descriptorSets = ConcurrentHashMap<String, Long>()

    protected var lastTime = System.nanoTime()
    protected var time = 0.0f
    protected var fps = 0
    protected var frames = 0
    protected var totalFrames = 0L
    protected var heartbeatTimer = Timer()

    private var renderConfig: RenderConfigReader.RenderConfig

    constructor(applicationName: String, scene: Scene, windowWidth: Int, windowHeight: Int, renderConfigFile: String = "DeferredShading.yml") {
        window.width = windowWidth
        window.height = windowHeight

        this.applicationName = applicationName
        this.scene = scene

        this.settings = getDefaultRendererSettings()

        logger.debug("Loading rendering config from $renderConfigFile")
        this.renderConfig = RenderConfigReader().loadFromFile(renderConfigFile)

        logger.info("Loaded ${renderConfig.name} (${renderConfig.description ?: "no description"})")

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
        debugCallbackHandle = setupDebugging(instance,
            VK_DEBUG_REPORT_ERROR_BIT_EXT or VK_DEBUG_REPORT_WARNING_BIT_EXT,
            debugCallback)

        physicalDevice = getPhysicalDevice(instance)
        deviceAndGraphicsQueueFamily = createDeviceAndGetGraphicsQueueFamily(physicalDevice)
        device = deviceAndGraphicsQueueFamily.device!!
        queueFamilyIndex = deviceAndGraphicsQueueFamily.queueFamilyIndex
        memoryProperties = deviceAndGraphicsQueueFamily.memoryProperties!!

        // Create GLFW window
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        window.glfwWindow = glfwCreateWindow(windowWidth, windowHeight, "scenery", NULL, NULL)

        surface = VU.run(memAllocLong(1), "glfwCreateWindowSurface") {
            glfwCreateWindowSurface(instance, window.glfwWindow!!, null, this)
        }

        swapchainRecreator = SwapchainRecreator()


        // create resolution-independent resources
        colorFormatAndSpace = getColorFormatAndSpace(physicalDevice, surface)

        with(commandPools) {
            Render = createCommandPool(device, queueFamilyIndex)
            Standard = createCommandPool(device, queueFamilyIndex)
            Compute = createCommandPool(device, queueFamilyIndex)
        }

        setupCommandBuffer = VU.newCommandBuffer(device, commandPools.Standard)
        postPresentCommandBuffer = VU.newCommandBuffer(device, commandPools.Standard)

        queue = VU.createDeviceQueue(device, queueFamilyIndex)

        descriptorPool = createDescriptorPool(device)
        vertexDescriptors = prepareStandardVertexDescriptors()

        buffers = prepareDefaultBuffers(device)
        descriptorSetLayouts = prepareDefaultDescriptorSetLayouts(device)
        standardUBOs = prepareDefaultUniformBuffers(device)

        prepareDescriptorSets(device, descriptorPool)
        prepareDefaultTextures(device)


        heartbeatTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                fps = frames.toInt()
                frames = 0
            }
        }, 0, 1000)

        // Handle canvas resize
        val windowSizeCallback = object : GLFWWindowSizeCallback() {
            override operator fun invoke(glfwWindow: Long, w: Int, h: Int) {
                if (window.width <= 0 || window.height <= 0)
                    return

                window.width = w
                window.height = h
                swapchainRecreator.mustRecreate = true
            }
        }

        glfwSetWindowSizeCallback(window.glfwWindow!!, windowSizeCallback)
        glfwShowWindow(window.glfwWindow!!)

        // Pre-allocate everything needed in the render loop

        pSwapchains = memAllocLong(1)

        // Info struct to create a semaphore
        semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
            .pNext(NULL)
            .flags(0)


        lastTime = System.nanoTime()
        time = 0.0f
    }

    /**
     * Returns the default [Settings] for [VulkanRenderer]
     *
     * Providing some sane defaults that may of course be overridden after
     * construction of the renderer.
     *
     * @return Default [Settings] values
     */
    protected fun getDefaultRendererSettings(): Settings {
        val ds = Settings()

        ds.set("wantsFullscreen", false)
        ds.set("isFullscreen", false)

        ds.set("ssao.Active", true)
        ds.set("ssao.FilterRadius", GLVector(0.0f, 0.0f))
        ds.set("ssao.DistanceThreshold", 50.0f)
        ds.set("ssao.Algorithm", 1)

        ds.set("vr.Active", false)
        ds.set("vr.DoAnaglyph", false)
        ds.set("vr.IPD", 0.0f)
        ds.set("vr.EyeDivisor", 1)

        ds.set("hdr.Active", true)
        ds.set("hdr.Exposure", 1.0f)
        ds.set("hdr.Gamma", 2.2f)

        ds.set("sdf.MaxDistance", 10)

        ds.set("debug.DebugDeferredBuffers", false)

        return ds
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

    fun setCurrentScene(scene: Scene) {
        this.scene = scene
    }

    /**
     * This function should initialize the scene contents.
     *
     * @param[scene] The scene to initialize.
     */
    override fun initializeScene() {
        logger.info("Starting scene initialization")

        this.scene.discover(this.scene, { it is HasGeometry })
//            .parallelMap(numThreads = System.getProperty("scenery.MaxInitThreads", "1").toInt()) { node ->
            .map { node ->
                logger.debug("Initializing object '${node.name}'")
                node.metadata.put("VulkanRenderer", VulkanObjectState())

                if (node is FontBoard) {
                    updateFontBoard(node)
                }

                initializeNode(node)
            }

        scene.initialized = true
    }

    protected fun updateFontBoard(board: FontBoard) {
        val atlas = fontAtlas.getOrPut(board.fontFamily, { SDFFontAtlas(this.hub!!, board.fontFamily, maxDistance = settings.get<Int>("sdf.MaxDistance")) })
        val m = atlas.createMeshForString(board.text)

        board.vertices = m.vertices
        board.normals = m.normals
        board.indices = m.indices
        board.texcoords = m.texcoords

        board.metadata.remove("VulkanRenderer")
        board.metadata.put("VulkanRenderer", VulkanObjectState())
    }

    fun Boolean.toInt(): Int {
        return if(this) {
            1
        } else {
            0
        }
    }
    /**
     *
     */
    fun initializeNode(node: Node): Boolean {
        var s: VulkanObjectState

        s = node.metadata["VulkanRenderer"] as VulkanObjectState

        if (s.initialized) return true

        logger.debug("Initializing ${node.name} (${(node as HasGeometry).vertices.remaining() / node.vertexSize} vertices/${node.indices.remaining()} indices)")

        if(node.instanceOf != null) {
            val parentMetadata = node.instanceOf!!.metadata["VulkanRenderer"] as VulkanObjectState

            if(!parentMetadata.initialized) {
                logger.info("Instance parent ${node.instanceOf!!} is not initialized yet, initializing now...")
                initializeNode(node.instanceOf!!)

            }

            if(!parentMetadata.vertexBuffers.containsKey("instance")) {
                createInstanceBuffer(device, node.instanceOf!!, parentMetadata)
            }

//            node.instanceOf!!.metadata["VulkanRenderer"] = s

            return true
        }

        if (node.vertices.remaining() > 0) {
            s = createVertexBuffers(device, node, s)
        }

        val matricesUbo = UBO(device, backingBuffer = buffers["UBOBuffer"])
        with(matricesUbo) {
            name = "Default"
            members.put("ModelViewMatrix", { node.modelView })
            members.put("ModelMatrix", { node.model })
            members.put("ProjectionMatrix", { node.projection })
            members.put("MVP", { node.mvp } )
            members.put("CamPosition", { scene.findObserver().position })
            members.put("isBillboard", { node.isBillboard.toInt() })

            createUniformBuffer(memoryProperties)
            sceneUBOs.put(node, this)
            s.UBOs.put("Default", this)
        }

        s = loadTexturesForNode(node, s)

        if(node.material != null) {
            val materialUbo = UBO(device, backingBuffer = buffers["UBOBuffer"])
            val materialType = if (node.material!!.textures.containsKey("diffuse")) {
                1
            } else if (node.material!!.textures.containsKey("normal")) {
                logger.info("${node.name} has type texture+normalmap")
                3
            } else {
                0
            }

            with(materialUbo) {
                name = "BlinnPhongMaterial"
                members.put("Ka", { node.material!!.ambient })
                members.put("Kd", { node.material!!.diffuse })
                members.put("Ks", { node.material!!.specular })
                members.put("Shininess", { node.material!!.specularExponent })
                members.put("materialType", { materialType })

                createUniformBuffer(memoryProperties)
                s.UBOs.put("BlinnPhongMaterial", this)
            }
        } else {
            val materialUbo = UBO(device, backingBuffer = buffers["UBOBuffer"])
            val m = Material.DefaultMaterial()

            with(materialUbo) {
                name = "BlinnPhongMaterial"
                members.put("Ka", { m.ambient })
                members.put("Kd", { m.diffuse })
                members.put("Ks", { m.specular })
                members.put("Shininess", { m.specularExponent })
                members.put("materialType", { 0 })

                createUniformBuffer(memoryProperties)
                s.UBOs.put("BlinnPhongMaterial", this)
            }
        }

        s.initialized = true
        node.metadata["VulkanRenderer"] = s

        return true
    }

    protected fun loadTexturesForNode(node: Node, s: VulkanObjectState): VulkanObjectState {
        if (node.lock.tryLock()) {
            node.material?.textures?.forEach {
                type, texture ->

                val slot = when (type) {
                    "ambient" -> 0
                    "diffuse" -> 1
                    "specular" -> 2
                    "normal" -> 3
                    "displacement" -> 4
                    else -> 0
                }

                logger.debug("${node.name} will have $type texture from $texture in slot $slot")

                if (!textureCache.containsKey(texture) || node.material?.needsTextureReload!!) {
                    logger.trace("Loading texture $texture for ${node.name}")

                    val vkTexture = if (texture.startsWith("fromBuffer:")) {
                        val gt = node.material!!.transferTextures[texture.substringAfter("fromBuffer:")]

                        val t = VulkanTexture(device, physicalDevice,
                            commandPools.Standard, queue, gt!!.dimensions.x().toInt(), gt.dimensions.y().toInt(), 1)
                        t.copyFrom(gt.contents)

                        t
                    } else {
                        VulkanTexture.loadFromFile(device, physicalDevice,
                            commandPools.Standard, queue, texture, true, 1)
                    }

                    s.textures.put(type, vkTexture!!)
                    textureCache.put(texture, vkTexture)
                } else {
                    s.textures.put(type, textureCache[texture]!!)
                }
            }

            /*arrayOf("ambient", "diffuse", "specular", "normal", "displacement").forEach {
                s.textures.putIfAbsent(it, textureCache["DefaultTexture"])
            }*/

            s.texturesToDescriptorSet(device, descriptorSetLayouts["ObjectTextures"]!!,
                descriptorPool,
                targetBinding = 0)

            node.lock.unlock()
        }

        return s
    }

    protected fun prepareDefaultDescriptorSetLayouts(device: VkDevice): ConcurrentHashMap<String, Long> {
        val m = ConcurrentHashMap<String, Long>()

        m.put("default", VU.createDescriptorSetLayout(
            device,
            listOf(
                Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1),
                Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1),
                Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1)),
            VK_SHADER_STAGE_ALL_GRAPHICS))

        m.put("LightParameters", VU.createDescriptorSetLayout(
            device,
            listOf(Pair(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1)),
            VK_SHADER_STAGE_ALL_GRAPHICS))

        m.put("ObjectTextures", VU.createDescriptorSetLayout(
            device,
            listOf(Pair(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 5)),
            VK_SHADER_STAGE_ALL_GRAPHICS))

        renderConfig.renderpasses.forEach { rp ->
            rp.value.inputs?.let {
                renderConfig.rendertargets?.let { rts ->
                    val rt = rts.get(it.first())!!

                    // create descriptor set layout that matches the render target
                    m.put("outputs-${it.first()}",
                        VU.createDescriptorSetLayout(device,
                            descriptorNum = rt.count(),
                            descriptorCount = 1,
                            type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
                        ))
                }
            }
        }

        return m
    }

    protected fun prepareDescriptorSets(device: VkDevice, descriptorPool: Long) {
        this.descriptorSets.put("default",
            VU.createDescriptorSetDynamic(device, descriptorPool,
                descriptorSetLayouts["default"]!!, standardUBOs.count(),
                buffers["UBOBuffer"]!!))

        this.descriptorSets.put("LightParameters",
            VU.createDescriptorSetDynamic(device, descriptorPool,
                descriptorSetLayouts["LightParameters"]!!, 1,
                buffers["LightParametersBuffer"]!!))
    }

    protected fun prepareStandardVertexDescriptors(): ConcurrentHashMap<VertexDataKinds, VertexDescription> {
        val map = ConcurrentHashMap<VertexDataKinds, VertexDescription>()

        VertexDataKinds.values().forEach { kind ->
            var attributeDesc: VkVertexInputAttributeDescription.Buffer?
            var stride = 0

            when (kind) {
                VertexDataKinds.coords_none -> {
                    stride = 0
                    attributeDesc = null
                }

                VertexDataKinds.coords_normals -> {
                    stride = 3 + 3
                    attributeDesc = VkVertexInputAttributeDescription.calloc(2)

                    attributeDesc.get(1)
                        .binding(0)
                        .location(1)
                        .format(VK_FORMAT_R32G32B32_SFLOAT)
                        .offset(3 * 4)
                }

                VertexDataKinds.coords_normals_texcoords -> {
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

                VertexDataKinds.coords_texcoords -> {
                    stride = 3 + 2
                    attributeDesc = VkVertexInputAttributeDescription.calloc(2)

                    attributeDesc.get(1)
                        .binding(0)
                        .location(1)
                        .format(VK_FORMAT_R32G32_SFLOAT)
                        .offset(3 * 4)
                }
            }

            if (attributeDesc != null) {
                attributeDesc.get(0)
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(0)
            }

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

    protected fun prepareDefaultTextures(device: VkDevice) {
        val t = VulkanTexture.loadFromFile(device, physicalDevice, commandPools.Standard, queue,
            Renderer::class.java.getResource("DefaultTexture.png").path.toString(), true, 1)

        textureCache.put("DefaultTexture", t!!)
    }

    protected fun prepareRenderpassesFromConfig(config: RenderConfigReader.RenderConfig, width: Int, height: Int): LinkedHashMap<String, VulkanRenderpass> {
        // create all renderpasses first
        val passes = LinkedHashMap<String, VulkanRenderpass>()

        config.createRenderpassFlow().map { passName ->
            val passConfig = config.renderpasses.get(passName)!!
            val pass = VulkanRenderpass(passName, config, device, descriptorPool, pipelineCache,
                memoryProperties, vertexDescriptors)

            // create framebuffer
            with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
                config.rendertargets?.filter { it.key == passConfig.output }?.map { rt ->
                    logger.info("Creating render framebuffer ${rt.key} for pass ${passName}")

                    val framebuffer = VulkanFramebuffer(device, physicalDevice, commandPools.Standard, width, height, this)

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
                }

                if (passConfig.output == "Viewport") {
                    // let's also create the default framebuffers
                    swapchain!!.images!!.forEachIndexed { i, image ->
                        val fb = VulkanFramebuffer(device, physicalDevice, commandPools.Standard, width, height, this)
                        fb.addSwapchainAttachment("swapchain-$i", swapchain!!, i)
                        fb.addDepthBuffer("swapchain-$i-depth", 32)
                        fb.createRenderpassAndFramebuffer()

                        pass.output.put("Viewport-$i", fb)
                        pass.setViewportPass(swapchain!!.images!!.size)
                    }
                }

                this.endCommandBuffer(device, commandPools.Standard, this@VulkanRenderer.queue, flush = true)

            }

            passes.put(passName, pass)
        }

        // connect inputs with each other
        passes.forEach { pass ->
            val passConfig = config.renderpasses.get(pass.key)!!

            passConfig.inputs?.forEach { inputTarget ->
                passes.filter {
                    it.value.output.keys.contains(inputTarget)
                }.forEach { pass.value.inputs.put(inputTarget, it.value.output.get(inputTarget)!!) }
            }

            with(pass.value) {
                initializeInputAttachmentDescriptorSetLayouts()
                initializeShaderParameterDescriptorSetLayouts(settings)

                initializeDefaultPipeline()
            }
        }

        return passes
    }

    protected fun prepareStandardSemaphores(device: VkDevice): ConcurrentHashMap<StandardSemaphores, Array<Long>> {
        val map = ConcurrentHashMap<StandardSemaphores, Array<Long>>()

        StandardSemaphores.values().forEach {
            map.put(it, swapchain!!.images!!.map {
                VU.run(memAllocLong(1), "Semaphore for $it") {
                    vkCreateSemaphore(device, semaphoreCreateInfo, null, this)
                }
            }.toTypedArray())
        }

        return map
    }

    fun Long.hex(): String {
        return String.format("0x%X", this)
    }

    private fun pollEvents() {
        if (glfwWindowShouldClose(window.glfwWindow!!)) {
            this.shouldClose = true
        }

        glfwPollEvents()

        if (swapchainRecreator.mustRecreate) {
            swapchainRecreator.recreate()
            frames = 0
        }
    }

    fun beginFrame() {
        // this will wait infinite time or until an error occurs, then signal
        // that an image is available
        var err = vkAcquireNextImageKHR(device, swapchain!!.handle, UINT64_MAX,
            semaphores[StandardSemaphores.present_complete]!!.get(0),
            VK_NULL_HANDLE, swapchainImage)

        if (err == VK_ERROR_OUT_OF_DATE_KHR || err == VK_SUBOPTIMAL_KHR) {
            swapchainRecreator.mustRecreate = true
        } else if (err != VK_SUCCESS) {
            throw AssertionError("Failed to acquire next swapchain image: " + VU.translate(err))
        }
    }


    fun submitFrame(queue: VkQueue, pass: VulkanRenderpass, present: PresentHelpers) {
        val submitInfo = VkSubmitInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
            .pNext(NULL)
            .waitSemaphoreCount(1)
            .pWaitSemaphores(present.waitSemaphore)
            .pWaitDstStageMask(present.waitStages)
            .pCommandBuffers(present.commandBuffers)
            .pSignalSemaphores(present.signalSemaphore)

        // Submit to the graphics queue
        var err = vkQueueSubmit(queue, submitInfo, pass.commandBuffer!!.fence.get(0))
        if (err != VK_SUCCESS) {
            throw AssertionError("Frame $frames: Failed to submit render queue: " + VU.translate(err))
        }

        pass.commandBuffer!!.submitted = true

        // Present the current buffer to the swap chain
        // This will display the image
        pSwapchains.put(0, swapchain!!.handle)

        // Info struct to present the current swapchain image to the display
        val presentInfo = VkPresentInfoKHR.calloc()
            .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
            .pNext(NULL)
            .pWaitSemaphores(present.signalSemaphore)
            .swapchainCount(pSwapchains.remaining())
            .pSwapchains(pSwapchains)
            .pImageIndices(swapchainImage)
            .pResults(null)

        err = vkQueuePresentKHR(queue, presentInfo)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to present the swapchain image: " + VU.translate(err))
        }

        if(screenshotRequested) {
            // default image format is 32bit BGRA
            val imageByteSize = window.width*window.height*4L
            val screenshotBuffer = VU.createBuffer(device,
                memoryProperties, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                wantAligned = true,
                allocationSize = imageByteSize)

            with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
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

                vkCmdCopyImageToBuffer(this, swapchain!!.images!![pass.readPos],
                    VK_IMAGE_LAYOUT_GENERAL,
                    screenshotBuffer.buffer,
                    regions)

                this.endCommandBuffer(device, commandPools.Render, queue,
                    flush = true, dealloc = true)
            }

            vkDeviceWaitIdle(device)

            val imageBuffer = memAlloc(imageByteSize.toInt())
            screenshotBuffer.copyTo(imageBuffer)

            try {
                val file = File(System.getProperty("user.home"), "Desktop" + File.separator + "$applicationName - ${SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(Date())}.png")
                imageBuffer.rewind()

                val imageArray = ByteArray(imageBuffer.remaining())
                imageBuffer.get(imageArray)
                val shifted = ByteArray(imageArray.size)

                // swizzle BGRA -> ABGR
                for(i in 0..shifted.size-1 step 4) {
                    shifted[i] = imageArray[i+3]
                    shifted[i+1] = imageArray[i]
                    shifted[i+2] = imageArray[i+1]
                    shifted[i+3] = imageArray[i+2]
                }

                val image = BufferedImage(window.width, window.height, BufferedImage.TYPE_4BYTE_ABGR)
                val imgData = (image.raster.dataBuffer as DataBufferByte).data
                System.arraycopy(shifted, 0, imgData, 0, shifted.size)

                ImageIO.write(image, "png", file)
                logger.info("Screenshot saved to ${file.absolutePath}")
            } catch (e: Exception) {
                System.err.println("Unable to take screenshot: ")
                e.printStackTrace()
            }

            memFree(imageBuffer)
            screenshotRequested = false
        }

        pass.nextSwapchainImage()

        submitInfo.free()
        presentInfo.free()
    }

    /**
     * This function renders the scene
     *
     * @param[scene] The scene to render.
     */
    override fun render() {
        pollEvents()

        // check whether scene is already initialized
        if (scene.children.count() == 0 || scene.initialized == false) {
            initializeScene()

            Thread.sleep(200)
            return
        }

        updateDefaultUBOs(device)

        // TODO: better logic for command buffer recreation
        // record command buffers for the renderpasses
        if (totalFrames == 0L) {
            renderpasses.forEach { s, vulkanRenderpass ->
                when (vulkanRenderpass.passConfig.type) {
                    RenderConfigReader.RenderpassType.geometry -> createSceneRenderCommandBuffer(device, vulkanRenderpass)
                    RenderConfigReader.RenderpassType.quad -> createPostprocessRenderCommandBuffer(device, vulkanRenderpass)
                }
            }
        }

        beginFrame()

        val flow = renderConfig.createRenderpassFlow()

        // firstWaitSemaphore is now the render_complete semaphore of the previous pass
        firstWaitSemaphore.put(0, semaphores[StandardSemaphores.present_complete]!!.get(0))

        flow.take(flow.size - 1).forEachIndexed { i, t ->
            logger.trace("Running pass $t")
            val target = renderpasses[t]!!
            target.updateShaderParameters()

            if (target.commandBuffer!!.submitted) {
                target.commandBuffer!!.waitForFence()
            }

            target.commandBuffer!!.resetFence()

            ph.commandBuffers.put(0, target.commandBuffer!!.commandBuffer)
            ph.signalSemaphore.put(0, target.semaphore)
            ph.waitStages.put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)

            val si = VkSubmitInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pNext(NULL)
                .waitSemaphoreCount(1)
                .pWaitDstStageMask(ph.waitStages)
                .pCommandBuffers(ph.commandBuffers)
                .pSignalSemaphores(ph.signalSemaphore)
                .pWaitSemaphores(firstWaitSemaphore)

            logger.trace("Target $t will wait on semaphore ${firstWaitSemaphore.get(0).hex()} and signal it's own semaphore ${target.semaphore.hex()})")

            vkQueueSubmit(queue, si, target.commandBuffer!!.fence.get(0))

            target.commandBuffer!!.submitted = true
            firstWaitSemaphore.put(0, target.semaphore)
        }

        val viewportPass = renderpasses.values.last()
        viewportPass.updateShaderParameters()

        if (viewportPass.commandBuffer!!.submitted == true) {
            viewportPass.commandBuffer!!.waitForFence()
        }

        viewportPass.commandBuffer!!.resetFence()

        ph.commandBuffers.put(0, viewportPass.commandBuffer!!.commandBuffer)
        ph.waitStages.put(0, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
        ph.signalSemaphore.put(0, semaphores[StandardSemaphores.render_complete]!!.get(0))
        ph.waitSemaphore.put(0, firstWaitSemaphore.get(0))

        submitFrame(queue, viewportPass, ph)

        updateTimings()
        glfwSetWindowTitle(window.glfwWindow!!,
            "$applicationName [${this.javaClass.simpleName}, ${this.renderConfig.name}${if (validation) {
                " - VALIDATIONS ENABLED"
            } else {
                ""
            }}] - $fps fps")
    }

    private fun updateTimings() {
        val thisTime = System.nanoTime()
        time += (thisTime - lastTime) / 1E9f
        lastTime = thisTime

        frames++
        totalFrames++
    }

    private fun createInstance(requiredExtensions: PointerBuffer): VkInstance {
        val appInfo = VkApplicationInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
            .pApplicationName(memUTF8(applicationName))
            .pEngineName(memUTF8("scenery"))
            .apiVersion(VK_MAKE_VERSION(1, 0, 24))

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

        if (pPhysicalDeviceCount.get(0) < 1) {
            throw AssertionError("No Vulkan-compatible devices found!")
        }

        val pPhysicalDevices = memAllocPointer(pPhysicalDeviceCount.get(0))
        err = vkEnumeratePhysicalDevices(instance, pPhysicalDeviceCount, pPhysicalDevices)

        val devicePreference = System.getProperty("scenery.VulkanRenderer.Device", "0").toInt()

        logger.info("Physical devices: ")
        for (i in 0..pPhysicalDeviceCount.get(0) - 1) {
            val device = VkPhysicalDevice(pPhysicalDevices.get(i), instance)
            val properties: VkPhysicalDeviceProperties = VkPhysicalDeviceProperties.calloc()

            vkGetPhysicalDeviceProperties(device, properties)
            logger.info("  $i: ${VU.vendorToString(properties.vendorID())} ${properties.deviceNameString()} (${VU.deviceTypeToString(properties.deviceType())}, driver version ${VU.driverVersionToString(properties.driverVersion())}, Vulkan API ${VU.driverVersionToString(properties.apiVersion())}) ${if (devicePreference == i) {
                "(selected)"
            } else {
                ""
            }}")
        }

        val physicalDevice = pPhysicalDevices.get(devicePreference)

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

        if (validation) {
            logger.info("Enabled Vulkan API validations. Expect degraded performance.")
        }

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
//            colorFormat = VK_FORMAT_B8G8R8A8_UNORM
            colorFormat = VK_FORMAT_B8G8R8A8_SRGB
        } else {
//            colorFormat = surfFormats.get(0).format()
            colorFormat = VK_FORMAT_B8G8R8A8_SRGB
        }
        val colorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR//surfFormats.get(0).colorSpace()
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

    private fun createSwapChain(device: VkDevice, physicalDevice: VkPhysicalDevice, surface: Long, oldSwapChain: Long, newWidth: Int,
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
            window.width = currentWidth
            window.height = currentHeight
        } else {
            window.width = newWidth
            window.height = newHeight
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
            .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_TRANSFER_SRC_BIT )
            .preTransform(preTransform)
            .imageArrayLayers(1)
            .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
            .pQueueFamilyIndices(null)
            .presentMode(swapchainPresentMode)
            .oldSwapchain(oldSwapChain)
            .clipped(true)
            .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)

        swapchainCI.imageExtent().width(window.width).height(window.height)
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

        with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
            for (i in 0..imageCount - 1) {
                images[i] = pSwapchainImages.get(i)
                // Bring the image from an UNDEFINED state to the VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT state
//            imageBarrier(commandBuffer, images[i], VK_IMAGE_ASPECT_COLOR_BIT,
//                VK_IMAGE_LAYOUT_UNDEFINED, 0,
//                VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                VU.setImageLayout(this, images[i],
                    aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                    oldImageLayout = VK_IMAGE_LAYOUT_GENERAL,
                    newImageLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                colorAttachmentView.image(images[i])

                imageViews[i] = VU.run(memAllocLong(1), "create image view",
                    { vkCreateImageView(device, colorAttachmentView, null, this) })
            }

            this.endCommandBuffer(device, commandPools.Standard, queue,
                flush = true, dealloc = true)
        }

        colorAttachmentView.free()
        memFree(pSwapchainImages)

        val ret = Swapchain()
        ret.images = images
        ret.imageViews = imageViews
        ret.handle = swapChain
        return ret
    }

    private fun createVertexBuffers(device: VkDevice, node: Node, state: VulkanObjectState): VulkanObjectState {
        val n = node as HasGeometry

        val vertexAllocationBytes = 4 * (n.vertices.remaining() + n.normals.remaining() + n.texcoords.remaining())
        val indexAllocationBytes = 4 * n.indices.remaining()
        val fullAllocationBytes = vertexAllocationBytes + indexAllocationBytes

        val stridedBuffer = memAlloc(fullAllocationBytes)

        val fb = stridedBuffer.asFloatBuffer()
        val ib = stridedBuffer.asIntBuffer()

        state.vertexCount = n.vertices.remaining() / n.vertexSize
        logger.trace("${node.name} has ${n.vertices.remaining()} floats and ${n.texcoords.remaining() / n.texcoordSize} remaining")

        for (index in 0..n.vertices.remaining() - 1 step 3) {
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

        logger.trace("Adding ${n.indices.remaining() * 4} bytes to strided buffer")
        if (n.indices.remaining() > 0) {
            state.isIndexed = true
            ib.position(vertexAllocationBytes / 4)

            for (index in 0..n.indices.remaining() - 1) {
                ib.put(n.indices.get())
            }
        }

        logger.trace("Strided buffer is now at ${stridedBuffer.remaining()} bytes")

        n.vertices.flip()
        n.normals.flip()
        n.texcoords.flip()
        n.indices.flip()

        val stagingBuffer = VU.createBuffer(device,
            this.memoryProperties,
            VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
            wantAligned = false,
            allocationSize = fullAllocationBytes * 1L)

        stagingBuffer.copyFrom(stridedBuffer)

        val vertexBuffer = VU.createBuffer(device,
            this.memoryProperties,
            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT or VK_BUFFER_USAGE_TRANSFER_DST_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
            wantAligned = false,
            allocationSize = fullAllocationBytes * 1L)

        with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
            val copyRegion = VkBufferCopy.calloc(1)
                .size(fullAllocationBytes * 1L)

            vkCmdCopyBuffer(this,
                stagingBuffer.buffer,
                vertexBuffer.buffer,
                copyRegion)

            this.endCommandBuffer(device, commandPools.Standard, queue, flush = true, dealloc = true)
        }

        state.vertexBuffers.put("vertex+index", vertexBuffer)
        state.indexOffset = vertexAllocationBytes
        state.indexCount = n.indices.remaining()

        vkDestroyBuffer(device, stagingBuffer.buffer, null)
        vkFreeMemory(device, stagingBuffer.memory, null)

        return state
    }

    private fun createInstanceBuffer(device: VkDevice, parentNode: Node, state: VulkanObjectState): VulkanObjectState {
        val instances = ArrayList<Node>()

        scene.discover(scene, { n -> n.instanceOf == parentNode }).forEach {
            instances.add(it)
        }

        if(instances.size < 1) {
            logger.info("$parentNode has no child instances attached, returning.")
            return state
        }

        // first we create a fake UBO to gauge the size of the needed properties
        val ubo = UBO(device)
        ubo.fromInstance(instances.first())

        val instanceBufferSize = ubo.getSize() * instances.size
        val instanceStagingBuffer = memAlloc(instanceBufferSize)

        logger.info("$parentNode has ${instances.size} child instances with ${ubo.getSize()} bytes each.")
        logger.info("Creating staging buffer...")

        val stagingBuffer = VU.createBuffer(device,
            this.memoryProperties,
            VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
            wantAligned = false,
            allocationSize = instanceBufferSize * 1L)

        instances.forEach { instance ->
            val instanceUbo = UBO(device, backingBuffer = stagingBuffer)
            instanceUbo.createUniformBuffer(memoryProperties)

            instanceUbo.populate()
        }

        stagingBuffer.copyFrom(instanceStagingBuffer)

        logger.info("Creating instance buffer...")
        // the actual instance buffer is kept device-local for performance reasons
        val instanceBuffer = VU.createBuffer(device,
            this.memoryProperties,
            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK_BUFFER_USAGE_TRANSFER_DST_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
            wantAligned = false,
            allocationSize = instanceBufferSize * 1L)

        logger.info("Copying staging -> instance buffer")
        with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
            val copyRegion = VkBufferCopy.calloc(1)
                .size(instanceBufferSize * 1L)

            vkCmdCopyBuffer(this,
                stagingBuffer.buffer,
                instanceBuffer.buffer,
                copyRegion)

            this.endCommandBuffer(device, commandPools.Standard, queue, flush = true, dealloc = true)
        }

        state.vertexBuffers.put("instance", instanceBuffer)

        vkDestroyBuffer(device, stagingBuffer.buffer, null)
        vkFreeMemory(device, stagingBuffer.memory, null)

        return state
    }

    private fun createDescriptorPool(device: VkDevice): Long {
        // We need to tell the API the number of max. requested descriptors per type
        val typeCounts = VkDescriptorPoolSize.calloc(4)
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
        val descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
            .pNext(NULL)
            .pPoolSizes(typeCounts)
            .maxSets(this.MAX_TEXTURES + this.MAX_UBOS + this.MAX_INPUT_ATTACHMENTS + this.MAX_UBOS)// Set the max. number of sets that can be requested

        val descriptorPool = VU.run(memAllocLong(1), "vkCreateDescriptorPool",
            function = { vkCreateDescriptorPool(device, descriptorPoolInfo, null, this) },
            cleanup = { descriptorPoolInfo.free(); typeCounts.free() })

        return descriptorPool
    }

    private fun prepareDefaultBuffers(device: VkDevice): HashMap<String, VulkanBuffer> {
        val map = HashMap<String, VulkanBuffer>()

        map.put("UBOBuffer", VU.createBuffer(device, this.memoryProperties,
            VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            wantAligned = true,
            allocationSize = 512 * 1024))

        map.put("LightParametersBuffer", VU.createBuffer(device, this.memoryProperties,
            VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            wantAligned = true,
            allocationSize = 512 * 1024))

        return map
    }

    private fun prepareDefaultUniformBuffers(device: VkDevice): ConcurrentHashMap<String, UBO> {
        val ubos = ConcurrentHashMap<String, UBO>()
        val defaultUbo = UBO(device)

        defaultUbo.name = "default"
        defaultUbo.members.put("ModelViewMatrix", { GLMatrix.getIdentity() })
        defaultUbo.members.put("ModelMatrix", { GLMatrix.getIdentity() })
        defaultUbo.members.put("ProjectionMatrix", { GLMatrix.getIdentity() })
        defaultUbo.members.put("MVP", { GLMatrix.getIdentity() })
        defaultUbo.members.put("CamPosition", { GLVector(0.0f, 0.0f, 0.0f)})
        defaultUbo.members.put("isBillboard", { 0 })

        defaultUbo.createUniformBuffer(memoryProperties)
        ubos.put("default", defaultUbo)

        val lightUbo = UBO(device)

        lightUbo.name = "BlinnPhongLighting"
        lightUbo.members.put("Position", { GLVector(0.0f, 0.0f, 0.0f) })
        lightUbo.members.put("La", { GLVector(0.0f, 0.0f, 0.0f) })
        lightUbo.members.put("Ld", { GLVector(0.0f, 0.0f, 0.0f) })
        lightUbo.members.put("Ls", { GLVector(0.0f, 0.0f, 0.0f) })

        lightUbo.createUniformBuffer(memoryProperties)
        ubos.put("BlinnPhongLighting", lightUbo)

        val materialUbo = UBO(device)

        materialUbo.name = "BlinnPhongMaterial"
        materialUbo.members.put("Ka", { GLVector(0.0f, 0.0f, 0.0f) })
        materialUbo.members.put("Kd", { GLVector(0.0f, 0.0f, 0.0f) })
        materialUbo.members.put("Ks", { GLVector(0.0f, 0.0f, 0.0f) })
        materialUbo.members.put("Shininess", { 1.0f })
        materialUbo.members.put("materialType", { 0 })

        materialUbo.createUniformBuffer(memoryProperties)
        ubos.put("BlinnPhongMaterial", materialUbo)

        return ubos
    }

    private fun createSceneRenderCommandBuffer(device: VkDevice, pass: VulkanRenderpass) {
        (0..pass.swapchainSize - 1).forEach {
            val target = pass.getOutput()

            pass.semaphore = VU.run(memAllocLong(1), "vkCreateSemaphore") {
                vkCreateSemaphore(device, semaphoreCreateInfo, null, this)
            }

            logger.info("Creating scene command buffer for ${pass.name}/$target (${target.attachments.count()} attachments)")

            val clearValues = VkClearValue.calloc(target.colorAttachmentCount() + target.depthAttachmentCount())
            val clearColor = BufferUtils.allocateFloatAndPut(floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f))

            target.attachments.values.forEachIndexed { i, att ->
                clearValues.put(i, when (att.type) {
                    VulkanFramebuffer.VulkanFramebufferType.COLOR_ATTACHMENT -> {
                        VkClearValue.calloc().color(VkClearColorValue.calloc().float32(clearColor))
                    }
                    VulkanFramebuffer.VulkanFramebufferType.DEPTH_ATTACHMENT -> {
                        VkClearValue.calloc().depthStencil(VkClearDepthStencilValue.calloc().depth(1.0f).stencil(0))
                    }
                })
            }

            val renderArea = VkRect2D.calloc()
            renderArea.extent(VkExtent2D.calloc().set(window.width, window.height)).offset(VkOffset2D.calloc().set(0, 0))

            val renderPassBegin = VkRenderPassBeginInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .pNext(NULL)
                .renderPass(target.renderPass.get(0))
                .framebuffer(target.framebuffer.get(0))
                .renderArea(renderArea)
                .pClearValues(clearValues)

            val renderOrderList = ArrayList<Node>()

            scene.discover(scene, { n -> n is Renderable && n is HasGeometry && n.visible }).forEach {
                renderOrderList.add(it)
            }

            val instanceGroups = renderOrderList.groupBy(Node::instanceOf)

            pass.commandBuffer = with(VU.newCommandBuffer(device, commandPools.Render, autostart = true)) {

                vkCmdBeginRenderPass(this, renderPassBegin, VK_SUBPASS_CONTENTS_INLINE)

                val viewport = VkViewport.calloc(1)
                viewport[0].set(0.0f, 0.0f, window.width.toFloat(), window.height.toFloat(), 0.0f, 1.0f)
                val scissor = VkRect2D.calloc(1).extent(VkExtent2D.calloc().set(window.width, window.height))

                vkCmdSetViewport(this, 0, viewport)
                vkCmdSetScissor(this, 0, scissor)

                instanceGroups[null]?.forEach nonInstancedDrawing@ { node ->
                    val s = node.metadata["VulkanRenderer"]!! as VulkanObjectState

                    if (node in instanceGroups.keys || s.vertexCount == 0) {
                        return@nonInstancedDrawing
                    }

                    val vb = memAllocLong(1)
                    vb.put(0, s.vertexBuffers["vertex+index"]!!.buffer)

                    val ds = memAllocLong(1 + if (s.textures.size > 0) {
                        1
                    } else {
                        0
                    })
                    ds.put(0, descriptorSets["default"]!!)

                    if (s.textures.size > 0) {
                        ds.put(1, s.textureDescriptorSet)
                    }
//                var dspos = 1
//                logger.info("${n.name} has ${s.textures.count()} textures")
//                s.textures.forEach { type, texture ->
//                    logger.info("Adding ds for $type $texture of ${n.name}, ${texture.image!!.descriptorSet}")
//                    ds.put(dspos, texture.image!!.descriptorSet)
//                    dspos ++
//                }

                    val offsets = memAllocLong(1)
                    offsets.put(0, 0)

                    val pipeline = pass.pipelines["default"]!!.getPipelineForGeometryType((node as HasGeometry).geometryType)

                    vkCmdBindPipeline(this, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.pipeline)
                    vkCmdBindDescriptorSets(this, VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipeline.layout, 0, ds, sceneUBOs[node]!!.offsets)
                    vkCmdBindVertexBuffers(this, 0, vb, offsets)

                    logger.trace("now drawing ${node.name}, ${ds.capacity()} DS bound, ${s.textures.count()} textures")
                    if (s.isIndexed) {
                        vkCmdBindIndexBuffer(this, vb.get(0), s.indexOffset * 1L, VK_INDEX_TYPE_UINT32)
                        vkCmdDrawIndexed(this, s.indexCount, 1, 0, 0, 0)
                    } else {
                        vkCmdDraw(this, s.vertexCount, 1, 0, 0)
                    }
                }

                // TODO: implement instanced rendering
                instanceGroups.keys.filterNotNull().forEach instancedDrawing@ { node ->
                    val s = node.metadata["VulkanRenderer"]!! as VulkanObjectState

                    // this only lets non-instanced, parent nodes through
                    if (node in instanceGroups.keys || s.vertexCount == 0) {
                        return@instancedDrawing
                    }

                    val vb = memAllocLong(1)
                    vb.put(0, s.vertexBuffers["vertex+index"]!!.buffer)

                    val instanceBuffer = memAllocLong(1)
                    instanceBuffer.put(0, s.vertexBuffers["instance"]!!.buffer)

                    val ds = memAllocLong(1 + if (s.textures.size > 0) {
                        1
                    } else {
                        0
                    })
                    ds.put(0, descriptorSets["default"]!!)

                    if (s.textures.size > 0) {
                        ds.put(1, s.textureDescriptorSet)
                    }
//                var dspos = 1
//                logger.info("${n.name} has ${s.textures.count()} textures")
//                s.textures.forEach { type, texture ->
//                    logger.info("Adding ds for $type $texture of ${n.name}, ${texture.image!!.descriptorSet}")
//                    ds.put(dspos, texture.image!!.descriptorSet)
//                    dspos ++
//                }

                    val bufferOffsets = memAllocLong(1)
                    bufferOffsets.put(0, 0)

                    val pipeline = pass.pipelines["default"]!!.getPipelineForGeometryType((node as HasGeometry).geometryType)

                    vkCmdBindPipeline(this, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.pipeline)
                    vkCmdBindDescriptorSets(this, VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipeline.layout, 0, ds, sceneUBOs[node]!!.offsets)
                    vkCmdBindVertexBuffers(this, 0, vb, bufferOffsets)
                    vkCmdBindVertexBuffers(this, 1, instanceBuffer, bufferOffsets)

                    logger.trace("now drawing ${node.name}, ${ds.capacity()} DS bound, ${s.textures.count()} textures")
                    if (s.isIndexed) {
                        vkCmdBindIndexBuffer(this, vb.get(0), s.indexOffset * 1L, VK_INDEX_TYPE_UINT32)
                        vkCmdDrawIndexed(this, s.indexCount, 1, 0, 0, 0)
                    } else {
                        vkCmdDraw(this, s.vertexCount, 1, 0, 0)
                    }
                }

                vkCmdEndRenderPass(this)
                this.endCommandBuffer()

                VulkanCommandBuffer(device, this)
            }
        }
    }

    private fun createPostprocessRenderCommandBuffer(device: VkDevice, pass: VulkanRenderpass) {
        (0..pass.swapchainSize - 1).forEach {
            val target = pass.getOutput()

            pass.semaphore = VU.run(memAllocLong(1), "vkCreateSemaphore") {
                vkCreateSemaphore(device, semaphoreCreateInfo, null, this)
            }

            logger.info("Creating postprocessing command buffer for ${pass.name}/$target (${target.attachments.count()} attachments)")

            val clearValues = VkClearValue.calloc(target.colorAttachmentCount() + target.depthAttachmentCount())
            val clearColor = BufferUtils.allocateFloatAndPut(floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f))

            target.attachments.values.forEachIndexed { i, att ->
                clearValues.put(i, when (att.type) {
                    VulkanFramebuffer.VulkanFramebufferType.COLOR_ATTACHMENT -> {
                        VkClearValue.calloc().color(VkClearColorValue.calloc().float32(clearColor))
                    }
                    VulkanFramebuffer.VulkanFramebufferType.DEPTH_ATTACHMENT -> {
                        VkClearValue.calloc().depthStencil(VkClearDepthStencilValue.calloc().depth(1.0f))
                    }
                })
            }

            val renderArea = VkRect2D.calloc()
            renderArea.extent(VkExtent2D.calloc().set(window.width, window.height)).offset(VkOffset2D.calloc().set(0, 0))

            val renderPassBegin = VkRenderPassBeginInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .pNext(NULL)
                .renderPass(target.renderPass.get(0))
                .framebuffer(target.framebuffer.get(0))
                .renderArea(renderArea)
                .pClearValues(clearValues)

            pass.commandBuffer = with(VU.newCommandBuffer(device, commandPools.Render, autostart = true)) {

                vkCmdBeginRenderPass(this, renderPassBegin, VK_SUBPASS_CONTENTS_INLINE)

                val viewport = VkViewport.calloc(1)
                viewport[0].set(0.0f, 0.0f, window.width.toFloat(), window.height.toFloat(), 0.0f, 1.0f)
                val scissor = VkRect2D.calloc(1).extent(VkExtent2D.calloc().set(window.width, window.height))

                vkCmdSetViewport(this, 0, viewport)
                vkCmdSetScissor(this, 0, scissor)

                val pipeline = pass.pipelines["default"]!!
                val vulkanPipeline = pipeline.getPipelineForGeometryType(GeometryType.TRIANGLES)

                val ds = memAllocLong(pipeline.descriptorSpecs.count())
                logger.info("descriptor sets are ${pass.descriptorSets.keys.joinToString(", ")}")
                logger.info("pipeline provides ${pipeline.descriptorSpecs.map { it.name }.joinToString(", ")}")

                pipeline.descriptorSpecs.forEachIndexed { i, spec ->
                    val dsName = if (spec.name.startsWith("ShaderParameters")) {
                        "ShaderParameters-${pass.name}"
                    } else if (spec.name.startsWith("inputs")) {
                        "inputs-${pass.name}"
                    } else if (spec.name.startsWith("Matrices")) {
                        "default"
                    } else {
                        spec.name
                    }

                    val set = if (dsName == "default" || dsName == "LightParameters") {
                        descriptorSets.get(dsName)
                    } else {
                        pass.descriptorSets.get(dsName)
                    }

                    if (set != null) {
                        logger.info("Adding DS#$i for $dsName to required pipeline DSs")
                        ds.put(i, set)
                    } else {
                        logger.error("DS for $dsName not found!")
                    }
                }

                val offsets = memAllocInt(sceneUBOs.values.first().offsets!!.capacity() + 1)
                offsets.put(sceneUBOs.values.first().offsets)
                offsets.put(0)
                offsets.flip()

                vkCmdBindPipeline(this, VK_PIPELINE_BIND_POINT_GRAPHICS, vulkanPipeline.pipeline)
//                if(pass.name == "DeferredLighting") {
                vkCmdBindDescriptorSets(this, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    vulkanPipeline.layout, 0, ds, offsets)
//                } else {
//                    vkCmdBindDescriptorSets(this, VK_PIPELINE_BIND_POINT_GRAPHICS,
//                        vulkanPipeline.layout, 0, ds, null)
//                }

                vkCmdDraw(this, 3, 1, 0, 0)

                vkCmdEndRenderPass(this)
                this.endCommandBuffer()

                VulkanCommandBuffer(device, this)
            }
        }
    }

    private fun updateDefaultUBOs(device: VkDevice) {
        val cam = scene.findObserver()
        cam.view = cam.getTransformation()

        buffers["UBOBuffer"]!!.reset()

        sceneUBOs.forEach { node, ubo ->
            node.updateWorld(true, false)

            ubo.offsets = memAllocInt(2)

            var bufferOffset = buffers["UBOBuffer"]!!.advance(ubo.getSize())
            ubo.offsets!!.put(0, bufferOffset)

            node.projection.copyFrom(cam.projection)
            node.projection.set(1, 1, -1.0f * cam.projection.get(1, 1))

            node.modelView.copyFrom(cam.view)
            node.modelView.mult(node.world)

            node.mvp.copyFrom(node.projection)
            node.mvp.mult(node.modelView)

            ubo.populate(offset = bufferOffset.toLong())

            val materialUbo = (node.metadata["VulkanRenderer"]!! as VulkanObjectState).UBOs["BlinnPhongMaterial"]!!
            bufferOffset = buffers["UBOBuffer"]!!.advance(materialUbo.getSize())
            ubo.offsets!!.put(1, bufferOffset)

            materialUbo.populate(offset = bufferOffset.toLong())
        }

        buffers["UBOBuffer"]!!.copyFromStagingBuffer()

        buffers["LightParametersBuffer"]!!.reset()

        val lights = scene.discover(scene, { n -> n is PointLight })
        var bufferOffset = buffers["LightParametersBuffer"]!!.advance(0)

        buffers["LightParametersBuffer"]!!.stagingBuffer.asIntBuffer().put(0, lights.size)
        buffers["LightParametersBuffer"]!!.stagingBuffer.position(4)

        lights.forEach { light->
            val l = light as PointLight
            val lightUbo = UBO(device, backingBuffer = buffers["LightParametersBuffer"]!!)

            lightUbo.members.put("Linear", { l.linear})
            lightUbo.members.put("Quadratic", { l.quadratic })
            lightUbo.members.put("Intensity", { l.intensity })
            lightUbo.members.put("Position", { l.position })
            lightUbo.members.put("Color", { l.emissionColor })

            lightUbo.createUniformBuffer(memoryProperties)

            lightUbo.populate(offset = bufferOffset.toLong())
            bufferOffset = buffers["LightParametersBuffer"]!!.advance(lightUbo.getSize())
        }

        buffers["LightParametersBuffer"]!!.copyFromStagingBuffer()
    }

    override fun screenshot() {
        screenshotRequested = true
    }

    override fun close() {
        vkDestroyInstance(instance, null)
    }
}
