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
import scenery.backends.RenderConfigReader
import scenery.backends.Renderer
import scenery.backends.SceneryWindow
import scenery.fonts.SDFFontAtlas
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
open class VulkanRenderer : Renderer {

    // helper classes

    data class PresentHelpers(
        var signalSemaphore: LongBuffer = memAllocLong(1),
        var waitSemaphore: LongBuffer = memAllocLong(1),
        var commandBuffers: PointerBuffer = memAllocPointer(1),
        var waitStages: IntBuffer = memAllocInt(1)
    )

    enum class VertexDataKinds {
        coords_normals_texcoords,
        coords_texcoords,
        coords_normals
    }

    enum class StandardSemaphores {
        render_complete,
        image_available
    }


    data class VertexDescription(
        var state: VkPipelineVertexInputStateCreateInfo,
        var attributeDescription: VkVertexInputAttributeDescription.Buffer,
        var bindingDescription: VkVertexInputBindingDescription.Buffer
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

    class UBODescriptor {
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
        var name = ""
        var members = LinkedHashMap<String, Any>()
        var descriptor: UBODescriptor? = null
        var offsets: IntBuffer? = null

        fun getSize(): Int {
            val sizes = members.map {
                when (it.value.javaClass) {
                    GLMatrix::class.java -> (it.value as GLMatrix).floatArray.size * 4
                    GLVector::class.java -> (it.value as GLVector).toFloatArray().size * 4
                    Float::class.java -> 4
                    Double::class.java -> 8
                    Int::class.java -> 4
                    Integer::class.java -> 4
                    Short::class.java -> 2
                    Boolean::class.java -> 4
                    else -> 0
                }
            }

            return sizes.sum()
        }
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
                    surface, oldChain, setupCommandBuffer,
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

            pipelineCache = VU.run(memAllocLong(1), "create pipeline cache",
                { vkCreatePipelineCache(device, pipelineCacheInfo, null, this) },
                { pipelineCacheInfo.free() })

            // destroy and recreate all framebuffers
            rendertargets.values.forEach { rt -> rt.destroy() }
            rendertargets.clear()

            standardUBOs = prepareDefaultUniformBuffers(device)
            rendertargets = prepareFramebuffers(device, physicalDevice, window.width, window.height)
            renderPipelines = prepareDefaultPipelines(device)

            semaphores = prepareStandardSemaphores(device)

            // Create render command buffers
            if (renderCommandBuffers != null) {
                vkResetCommandPool(device, commandPools.Render, VK_FLAGS_NONE)
            }

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

            logger.info("Validation $type: " + VkDebugReportCallbackEXT.getString(pMessage))

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
    private var MAX_TEXTURES = 2048
    private var MAX_UBOS = 128
    private var MAX_INPUT_ATTACHMENTS = 2
    private val UINT64_MAX: Long = -1L
    // end helper vars

    override var hub: Hub? = null
    protected var applicationName = ""
    override var settings: Settings = Settings()
    protected var logger: Logger = LoggerFactory.getLogger("VulkanRenderer")
    override var shouldClose = false
    override var managesRenderLoop = false

    var currentBufferNum = 0

    var scene: Scene = Scene()

    protected var rendertargets = ConcurrentHashMap<String, VulkanFramebuffer>()
    protected var commandPools = CommandPools()
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

    protected var renderPipelines = ConcurrentHashMap<String, VulkanPipeline>()
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

    protected var lastTime = System.nanoTime()
    protected var time = 0.0f
    protected var fps = 0
    protected var frames = 0
    protected var heartbeatTimer = Timer()

    private var renderConfig: RenderConfigReader.RenderConfig

    constructor(applicationName: String, scene: Scene, windowWidth: Int, windowHeight: Int, renderConfigFile: String = "ForwardShading.yml") {
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
        queueFamilyIndex = deviceAndGraphicsQueueFamily.queueFamilyIndex!!
        memoryProperties = deviceAndGraphicsQueueFamily.memoryProperties!!

        // Create GLFW window
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        window.glfwWindow = glfwCreateWindow(windowWidth, windowHeight, "scenery", NULL, NULL)

        surface = VU.run(memAllocLong(1), "glfwCreateWindowSurface") {
            glfwCreateWindowSurface(instance, window.glfwWindow!!, null, this)
        }

        // Create static Vulkan resources
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

        swapchainRecreator = SwapchainRecreator()

        vertexDescriptors = prepareStandardVertexDescriptors(device)

        buffers = prepareDefaultBuffers(device)

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

    fun createBuffer(usage: Int, memoryProperties: Int, wantAligned: Boolean = false, allocationSize: Long = 0): VulkanBuffer {
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

        bufferInfo.free()

        val allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .pNext(NULL)

        getMemoryType(this.memoryProperties,
            reqs.memoryTypeBits(),
            memoryProperties,
            memTypeIndex)

        val size = if (wantAligned) {
            if (reqs.size() % reqs.alignment() == 0L) {
                reqs.size()
            } else {
                reqs.size() + reqs.alignment() - (reqs.size() % reqs.alignment())
            }
        } else {
            reqs.size()
        }

        allocInfo.allocationSize(size)
            .memoryTypeIndex(memTypeIndex.get(0))

        vkAllocateMemory(this.device, allocInfo, null, memory)
        vkBindBufferMemory(this.device, buffer.get(0), memory.get(0), 0)

        val vb = VulkanBuffer(device, memory = memory.get(0), buffer = buffer.get(0))
        vb.maxSize = size
        vb.alignment = reqs.alignment()

        reqs.free()
        allocInfo.free()
        memFree(memTypeIndex)

        return vb
    }

    /**
     *
     */
    fun initializeNode(node: Node): Boolean {
        var s: VulkanObjectState

        s = node.metadata["VulkanRenderer"] as VulkanObjectState

        if (s.initialized) return true

        logger.debug("Initializing ${node.name} (${(node as HasGeometry).vertices.remaining() / node.vertexSize} vertices/${node.indices.remaining()} indices)")

        if (node.vertices.remaining() > 0) {
            s = createVertexBuffers(node, s, memoryProperties, device)
        }

        s.UBO = UBO()
        s.UBO?.let {
            it.members.put("ModelViewMatrix", GLMatrix.getIdentity())
            it.members.put("ModelMatrix", GLMatrix.getIdentity())
            it.members.put("ProjectionMatrix", GLMatrix.getIdentity())
            it.members.put("MVP", GLMatrix.getIdentity())
            it.members.put("CamPosition", GLVector(0.0f, 0.0f, 0.0f))
            it.members.put("isBillboard", 0)

            sceneUBOs.put(node, it)
        }

        s = loadTexturesForNode(node, s)

        node.metadata["VulkanRenderer"] = s

        return true
    }

    protected fun loadTexturesForNode(node: Node, s: VulkanObjectState): VulkanObjectState {
        if(node.lock.tryLock()) {
            node.material?.textures?.forEach {
                type, texture ->

                val slot = when(type) {
                    "ambient" ->        0
                    "diffuse" ->        1
                    "specular" ->       2
                    "normal" ->         3
                    "displacement" ->   4
                    else ->             0
                }

                logger.info("${node.name} will have $type texture from $texture in slot $slot")

                if(!textureCache.containsKey(texture) || node.material?.needsTextureReload!!) {
                    logger.trace("Loading texture $texture for ${node.name}")

                    val vkTexture = if(texture.startsWith("fromBuffer:")) {
                        val gt = node.material!!.transferTextures[texture.substringAfter("fromBuffer:")]

                        val t = VulkanTexture(device, physicalDevice,
                            commandPools.Standard, queue, gt!!.dimensions.x().toInt(), gt!!.dimensions.y().toInt(), 1)
                        t.copyFrom(gt.contents)

                        t
                    } else {
                        VulkanTexture.loadFromFile(device, physicalDevice,
                            commandPools.Standard, queue, texture, true, 1)
                    }

                    s.textures.put(type, vkTexture!!)
                    textureCache.put(texture, vkTexture!!)
                } else {
                    s.textures.put(type, textureCache[texture]!!)
                }
            }

            arrayOf("ambient", "diffuse", "specular", "normal", "displacement").forEach {
                s.textures.putIfAbsent(it, textureCache["DefaultTexture"])
            }

            s.texturesToDescriptorSet(device, descriptorSetLayouts["ObjectTextures"]!!, descriptorPool,
                targetBinding = 0)

            node.lock.unlock()
        }

        return s
    }

    protected fun prepareStandardVertexDescriptors(device: VkDevice): ConcurrentHashMap<VertexDataKinds, VertexDescription> {
        val map = ConcurrentHashMap<VertexDataKinds, VertexDescription>()

        VertexDataKinds.values().forEach { kind ->
            var attributeDesc: VkVertexInputAttributeDescription.Buffer
            var stride = 0

            when (kind) {
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

            attributeDesc.get(0)
                .binding(0)
                .location(0)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(0)

            val bindingDesc = VkVertexInputBindingDescription.calloc(1)
                .binding(0)
                .stride(stride * 4)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

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
           Renderer.javaClass.getResource("DefaultTexture.png").path.toString(), true, 1)

        textureCache.put("DefaultTexture", t!!)
    }

    protected fun prepareDefaultPipelines(device: VkDevice): ConcurrentHashMap<String, VulkanPipeline> {
        val map = ConcurrentHashMap<String, VulkanPipeline>()

        rendertargets.forEach { target ->
            val p = VulkanPipeline(device, descriptorPool, pipelineCache, buffers)
            val name = if (target.key.startsWith("Viewport")) {
                "Viewport"
            } else {
                target.key
            }

            val pass = renderConfig.renderpasses.values.filter { it.output == name }.first()

            p.UBOs.add(standardUBOs["default"]!!)
            p.UBOs.add(standardUBOs["BlinnPhongMaterial"]!!)
            p.UBOs.add(standardUBOs["BlinnPhongLighting"]!!)

            p.addShaderStages(pass.shaders.map { VulkanShaderModule(device, "main", "shaders/" + it) })
            p.createPipelines(target.value.renderPass.get(0),
                vertexDescriptors.get(VertexDataKinds.coords_normals_texcoords)!!.state)

            p.descriptorSetLayouts.forEach {
                descriptorSetLayouts.put(it.key, it.value)
            }

            logger.info("Prepared pipeline for ${target.key}")
            map.put(target.key, p)
        }

        return map
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

    /**
     *
     */
    protected fun prepareFramebuffers(device: VkDevice, physicalDevice: VkPhysicalDevice, width: Int, height: Int): ConcurrentHashMap<String, VulkanFramebuffer> {
        // create framebuffer
        with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
            val fbs = ConcurrentHashMap<String, VulkanFramebuffer>()

            renderConfig.rendertargets?.forEach { rt ->
                logger.info("Creating render target ${rt.key}")

                val target = VulkanFramebuffer(device, physicalDevice, commandPools.Standard, width, height, this)

                rt.value.forEach { att ->
                    logger.info(" + attachment ${att.key}, ${att.value.format.name}")

                    when (att.value.format) {
                        RenderConfigReader.TargetFormat.RGBA_Float32 -> target.addFloatRGBABuffer(att.key, 32)
                        RenderConfigReader.TargetFormat.RGBA_Float16 -> target.addFloatRGBABuffer(att.key, 16)

                        RenderConfigReader.TargetFormat.RGBA_UInt32 -> target.addUnsignedByteRGBABuffer(att.key, 32)
                        RenderConfigReader.TargetFormat.RGBA_UInt16 -> target.addUnsignedByteRGBABuffer(att.key, 16)
                        RenderConfigReader.TargetFormat.RGBA_UInt8 -> target.addUnsignedByteRGBABuffer(att.key, 8)

                        RenderConfigReader.TargetFormat.Depth32 -> target.addDepthBuffer(att.key, 32)
                        RenderConfigReader.TargetFormat.Depth24 -> target.addDepthBuffer(att.key, 24)
                    }
                }

                target.createRenderpassAndFramebuffer()

                fbs.put(rt.key, target)
            }

            // let's also create the default framebuffers
            swapchain!!.images!!.forEachIndexed { i, image ->
                val fb = VulkanFramebuffer(device, physicalDevice, commandPools.Standard, width, height, this)
                fb.addSwapchainAttachment("swapchain-$i", swapchain!!, i)
                fb.addDepthBuffer("swapchain-$i-depth", 32)
                fb.createRenderpassAndFramebuffer()

                fbs.put("Viewport-$i", fb)
            }

            this.endCommandBuffer(device, commandPools.Standard, this@VulkanRenderer.queue, flush = true)

            return fbs
        }
    }

    /**
     * This function renders the scene
     *
     * @param[scene] The scene to render.
     */
    override fun render() {
        if (glfwWindowShouldClose(window.glfwWindow!!)) {
            this.shouldClose = true
            return
        }

        glfwPollEvents()

        if (swapchainRecreator.mustRecreate) {
            swapchainRecreator.recreate()
        }

        if (scene.children.count() == 0 || scene.initialized == false) {
            initializeScene()

            Thread.sleep(200)
            return
        }

        val currentBuffer = "Viewport-$currentBufferNum".to(currentBufferNum)

        updateDefaultUBOs(device)

        if(frames == 0) {
            this.rendertargets.forEach { rt ->
                val name = if (rt.key.startsWith("Viewport")) {
                    "Viewport"
                } else {
                    rt.key
                }

                val pass = renderConfig.renderpasses.filter { it.value.output == name }.values.first()

                rt.value.renderCommandBuffer =
                    if (pass.output == "Viewport" && renderConfig.rendertargets != null) {
                        createPresentCommandBuffer(device, rt.key)
                    } else {
                        when (pass.type) {
                            RenderConfigReader.RenderpassType.geometry -> createSceneRenderCommandBuffer(device, rt.key)
                            RenderConfigReader.RenderpassType.quad -> createPostprocessRenderCommandBuffer(device, rt.key)
                        }
                    }
            }
        }

        val currentTarget = rendertargets[currentBuffer.first]!!

        if(currentTarget.renderCommandBuffer!!.submitted == true) {
            currentTarget.renderCommandBuffer!!.waitForFence()
        }

        currentTarget.renderCommandBuffer!!.resetFence()

        // Get next image from the swap chain (back/front buffer).
        // This will setup the imageAquiredSemaphore to be signalled when the operation is complete
        var err = vkAcquireNextImageKHR(device, swapchain!!.handle, UINT64_MAX,
            semaphores[StandardSemaphores.image_available]!!.get(currentBufferNum),
            VK_NULL_HANDLE, swapchainImage)

        if (err == VK_ERROR_OUT_OF_DATE_KHR || err == VK_SUBOPTIMAL_KHR) {
            swapchainRecreator.mustRecreate = true
        } else if (err != VK_SUCCESS) {
            throw AssertionError("Failed to acquire next swapchain image: " + VU.translate(err))
        }

        ph.commandBuffers.put(0, currentTarget.renderCommandBuffer!!.commandBuffer)
        ph.waitStages.put(0, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
        ph.signalSemaphore.put(0, semaphores[StandardSemaphores.render_complete]!!.get(currentBufferNum))
        ph.waitSemaphore.put(0, semaphores[StandardSemaphores.image_available]!!.get(currentBufferNum))

        val submitInfo = VkSubmitInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
            .pNext(NULL)
            .waitSemaphoreCount(1)
            .pWaitSemaphores(ph.waitSemaphore)
            .pWaitDstStageMask(ph.waitStages)
            .pCommandBuffers(ph.commandBuffers)
            .pSignalSemaphores(ph.signalSemaphore)

        // Submit to the graphics queue
        err = vkQueueSubmit(queue, submitInfo, currentTarget.renderCommandBuffer!!.fence.get(0))
        if (err != VK_SUCCESS) {
            throw AssertionError("Frame $frames: Failed to submit render queue: " + VU.translate(err))
        }

        currentTarget.renderCommandBuffer!!.submitted = true

        // Present the current buffer to the swap chain
        // This will display the image
        pSwapchains.put(0, swapchain!!.handle)

        // Info struct to present the current swapchain image to the display
        val presentInfo = VkPresentInfoKHR.calloc()
            .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
            .pNext(NULL)
            .pWaitSemaphores(ph.signalSemaphore)
            .swapchainCount(pSwapchains.remaining())
            .pSwapchains(pSwapchains)
            .pImageIndices(swapchainImage)
            .pResults(null)

        err = vkQueuePresentKHR(queue, presentInfo)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to present the swapchain image: " + VU.translate(err))
        }

        // Update UBO
        val thisTime = System.nanoTime()
        time += (thisTime - lastTime) / 1E9f
        lastTime = thisTime

        frames++

        currentBufferNum = (currentBufferNum + 1) % swapchain!!.images!!.size
        glfwSetWindowTitle(window.glfwWindow!!, "$applicationName [${this.javaClass.simpleName}] - $fps fps")

        presentInfo.free()
        submitInfo.free()
    }

    private fun createInstance(requiredExtensions: PointerBuffer): VkInstance {
        val appInfo = VkApplicationInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
            .pApplicationName(memUTF8("scenery"))
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

        logger.info("Physical devices: ")
        for (i in 0..pPhysicalDeviceCount.get(0) - 1) {
            val device = VkPhysicalDevice(pPhysicalDevices.get(i), instance)
            val properties: VkPhysicalDeviceProperties = VkPhysicalDeviceProperties.calloc()

            vkGetPhysicalDeviceProperties(device, properties)
            logger.info("  $i: ${VU.vendorToString(properties.vendorID())} ${properties.deviceNameString()} (${VU.deviceTypeToString(properties.deviceType())}, driver version ${VU.driverVersionToString(properties.driverVersion())}, Vulkan API ${VU.driverVersionToString(properties.apiVersion())})")
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
            .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
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
//            imageBarrier(commandBuffer, images[i], VK_IMAGE_ASPECT_COLOR_BIT,
//                VK_IMAGE_LAYOUT_UNDEFINED, 0,
//                VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
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
        ret.handle = swapChain
        return ret
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

    private fun createVertexBuffers(node: Node, state: VulkanObjectState, deviceMemoryProperties: VkPhysicalDeviceMemoryProperties, device: VkDevice): VulkanObjectState {
        val n = node as HasGeometry

        val vertexAllocationBytes = 4 * (n.vertices.remaining() + n.normals.remaining() + n.texcoords.remaining())
        val indexAllocationBytes = 4 * n.indices.remaining()
        val fullAllocationBytes = vertexAllocationBytes + indexAllocationBytes

        val stridedBuffer = memAlloc(fullAllocationBytes)

        val fb = stridedBuffer.asFloatBuffer()
        val ib = stridedBuffer.asIntBuffer()

        state.vertexCount = n.vertices.remaining() / n.vertexSize
        logger.trace("${node.name} has ${n.vertices.remaining()} floats and ${n.texcoords.remaining()/n.texcoordSize} remaining")

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

        val stagingBuffer = createBuffer(
            VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
            wantAligned = false,
            allocationSize = fullAllocationBytes*1L)

        stagingBuffer.copy(stridedBuffer)

        val vertexBuffer = createBuffer(
            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT or VK_BUFFER_USAGE_TRANSFER_DST_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
            wantAligned = false,
            allocationSize = fullAllocationBytes*1L)

        with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
            val copyRegion = VkBufferCopy.calloc(1)
                .size(fullAllocationBytes*1L)

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

    private fun createDescriptorPool(device: VkDevice): Long {
        // We need to tell the API the number of max. requested descriptors per type
        val typeCounts = VkDescriptorPoolSize.calloc(3)
        typeCounts[0]
            .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(this.MAX_TEXTURES)

        typeCounts[1]
            .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
            .descriptorCount(this.MAX_UBOS)

        typeCounts[2]
            .type(VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT)
            .descriptorCount(this.MAX_INPUT_ATTACHMENTS)

        // Create the global descriptor pool
        // All descriptors used in this example are allocated from this pool
        val descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
            .pNext(NULL)
            .pPoolSizes(typeCounts)
            .maxSets(this.MAX_TEXTURES + this.MAX_UBOS + this.MAX_INPUT_ATTACHMENTS)// Set the max. number of sets that can be requested

        val descriptorPool = VU.run(memAllocLong(1), "vkCreateDescriptorPool",
            function = { vkCreateDescriptorPool(device, descriptorPoolInfo, null, this) },
            cleanup = { descriptorPoolInfo.free(); typeCounts.free() })

        return descriptorPool
    }

    private fun prepareDefaultBuffers(device: VkDevice): HashMap<String, VulkanBuffer> {
        val map = HashMap<String, VulkanBuffer>()

        map.put("UBOBuffer", createBuffer(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            wantAligned = true,
            allocationSize = 512 * 1024))

        return map
    }

    private fun prepareDefaultUniformBuffers(device: VkDevice): ConcurrentHashMap<String, UBO> {
        val ubos = ConcurrentHashMap<String, UBO>()
        val defaultUbo = UBO()

        defaultUbo.name = "default"
        defaultUbo.members.put("ModelViewMatrix", GLMatrix.getIdentity())
        defaultUbo.members.put("ModelMatrix", GLMatrix.getIdentity())
        defaultUbo.members.put("ProjectionMatrix", GLMatrix.getIdentity())
        defaultUbo.members.put("MVP", GLMatrix.getIdentity())
        defaultUbo.members.put("CamPosition", GLVector(0.0f, 0.0f, 0.0f))
        defaultUbo.members.put("isBillboard", 0)

        defaultUbo.descriptor = createUniformBuffer(defaultUbo, memoryProperties, device)
        ubos.put("default", defaultUbo)

        val lightUbo = UBO()

        lightUbo.name = "BlinnPhongLighting"
        lightUbo.members.put("Position", GLVector(0.0f, 0.0f, 0.0f))
        lightUbo.members.put("La", GLVector(0.0f, 0.0f, 0.0f))
        lightUbo.members.put("Ld", GLVector(0.0f, 0.0f, 0.0f))
        lightUbo.members.put("Ls", GLVector(0.0f, 0.0f, 0.0f))

        lightUbo.descriptor = createUniformBuffer(lightUbo, memoryProperties, device)
        ubos.put("BlinnPhongLighting", lightUbo)

        val materialUbo = UBO()

        materialUbo.name = "BlinnPhongMaterial"
        materialUbo.members.put("Ka", GLVector(0.0f, 0.0f, 0.0f))
        materialUbo.members.put("Kd", GLVector(0.0f, 0.0f, 0.0f))
        materialUbo.members.put("Ks", GLVector(0.0f, 0.0f, 0.0f))
        materialUbo.members.put("Shinyness", 1.0f)

        materialUbo.descriptor = createUniformBuffer(materialUbo, memoryProperties, device)
        ubos.put("BlinnPhongMaterial", materialUbo)

        return ubos
    }

    private fun createUniformBuffer(ubo: UBO, deviceMemoryProperties: VkPhysicalDeviceMemoryProperties, device: VkDevice): UBODescriptor {
        var err: Int
        // Create a new buffer
        val bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(ubo.getSize() * 1L)
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

        ubo.descriptor = UBODescriptor()
        ubo.descriptor!!.memory = uniformDataVSMemory
        ubo.descriptor!!.allocationSize = memSize
        ubo.descriptor!!.buffer = uniformDataVSBuffer
        ubo.descriptor!!.offset = 0L
        ubo.descriptor!!.range = ubo.getSize() * 1L

        return ubo.descriptor!!
    }


    private fun createSceneRenderCommandBuffer(device: VkDevice, targetName: String): VulkanCommandBuffer {
        val target = this.rendertargets[targetName]!!

        target.semaphore = VU.run(memAllocLong(1), "vkCreateSemaphore") {
            vkCreateSemaphore(device, semaphoreCreateInfo, null, this)
        }

        logger.debug("Creating scene command buffer for $targetName/$target (${target.attachments.count()} attachments, pipeline=${renderPipelines[targetName]!!.pipeline})")

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

        return with(VU.newCommandBuffer(device, commandPools.Render, autostart = true)) {

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

                val ds = memAllocLong(1+if(s.textures.size > 0) { 1 } else { 0 })
                ds.put(0, renderPipelines[targetName]!!.descriptorSets["default"]!!)

                if(s.textures.size > 0) {
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

                val pipeline = renderPipelines[targetName]!!.pipeline[(node as HasGeometry).geometryType]!!

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

            vkCmdEndRenderPass(this)
            this.endCommandBuffer()

            VulkanCommandBuffer(device, this)
        }

    }


    private fun createPresentCommandBuffer(device: VkDevice, targetName: String): VulkanCommandBuffer {
        val target = rendertargets[targetName]!!

        val clearValues = VkClearValue.calloc(target.colorAttachmentCount() + target.depthAttachmentCount())

        target.attachments.values.forEachIndexed { i, att ->
            clearValues.put(i, when (att.type) {
                VulkanFramebuffer.VulkanFramebufferType.COLOR_ATTACHMENT -> {
                    VkClearValue.calloc().color(VkClearColorValue.calloc().float32(BufferUtils.allocateFloatAndPut(floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f))))
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

        return with(VU.newCommandBuffer(device, commandPools.Render, autostart = true)) {

            vkCmdBeginRenderPass(this, renderPassBegin, VK_SUBPASS_CONTENTS_INLINE)

            val viewport = VkViewport.calloc(1)
            viewport[0].set(0.0f, 0.0f, window.width.toFloat(), window.height.toFloat(), 0.0f, 1.0f)
            val scissor = VkRect2D.calloc(1).extent(VkExtent2D.calloc().set(window.width, window.height))

            vkCmdSetViewport(this, 0, viewport)
            vkCmdSetScissor(this, 0, scissor)

//            vkCmdBindDescriptorSets(this, VK_PIPELINE_BIND_POINT_GRAPHICS, layout, 0, descriptorSets, null)
//            vkCmdBindPipeline(this, VK_PIPELINE_BIND_POINT_GRAPHICS, layout)
//
//            vkCmdBindVertexBuffers(this, vertexBindId, buffers, null)
//            vkCmdBindIndexBuffer(this, indexBuffer, offset, VK_INDEX_TYPE_UINT32)
//            vkCmdDrawIndexed(this, indexCount, instanceCount, 0, 0, 1)

            /*val prePresentBarrier = createPrePresentBarrier(target.attachments["std"]!!.image)
            vkCmdPipelineBarrier(this,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK_FLAGS_NONE,
                null, // No memory barriers
                null, // No buffer memory barriers
                prePresentBarrier) // One image memory barrier
            prePresentBarrier.free()
            */

            vkCmdEndRenderPass(this)
            this.endCommandBuffer()

            VulkanCommandBuffer(device, this)
        }
    }

    private fun createPostprocessRenderCommandBuffer(device: VkDevice, targetName: String): VulkanCommandBuffer {
        return VulkanCommandBuffer(device, VU.newCommandBuffer(device, commandPools.Render))
    }

    private fun updateDefaultUBOs(device: VkDevice) {
        val cam = scene.findObserver()
        cam.view = cam.getTransformation()

        buffers["UBOBuffer"]!!.reset()

        sceneUBOs.forEach { node, ubo ->
            logger.trace("Updating UBO for ${node.name} of size ${ubo.getSize()}, members ${ubo.members.keys.joinToString(", ")}")
            node.updateWorld(true, false)

            ubo.offsets = memAllocInt(3)

            ubo.offsets!!.put(0, buffers["UBOBuffer"]!!.getCurrentOffset())
            var memoryTarget = buffers["UBOBuffer"]!!.getPointerBuffer(ubo.getSize())
            logger.debug("Current buffer offset is ${buffers["UBOBuffer"]!!.getCurrentOffset()}")

            // layout:
            // ModelView Matrix
            // ModelMatrix
            // Projection Matrix
            // MVP
            // CamPosition
            // isBillboard

            val projection = GLMatrix().setPerspectiveProjectionMatrix(cam.fov / 180.0f * Math.PI.toFloat(),
                (1.0f * window.width) / (1.0f * window.height), cam.nearPlaneDistance, cam.farPlaneDistance)
            projection.set(1, 1, -1.0f * projection.get(1, 1))

            val mv = cam.view!!.clone()
            mv.mult(node.world)

            val mvp = projection.clone()
            mvp.mult(mv)

            mv.put(memoryTarget)
            node.model.put(memoryTarget)
            projection.put(memoryTarget)
            mvp.put(memoryTarget)
            cam.position.put(memoryTarget)
            memoryTarget.asIntBuffer().put(if (node.isBillboard) {
                1
            } else {
                0
            })

            ubo.offsets!!.put(1, buffers["UBOBuffer"]!!.getCurrentOffset())
            memoryTarget = buffers["UBOBuffer"]!!.getPointerBuffer(4 * 3 * 4)

            logger.debug("UBO buffer now at ${memoryTarget.position()}")

            // Light Info
            GLVector(5.0f, 5.0f, 5.0f).put(memoryTarget)
            GLVector(1.0f, .0f, .0f).put(memoryTarget)
            GLVector(1.0f, .0f, .0f).put(memoryTarget)
            GLVector(1.0f, .0f, .0f).put(memoryTarget)

            ubo.offsets!!.put(2, buffers["UBOBuffer"]!!.getCurrentOffset())
            memoryTarget = buffers["UBOBuffer"]!!.getPointerBuffer(3 * 3 * 4 + 4)

            // MaterialInfo
            GLVector(1.0f, .0f, .0f).put(memoryTarget)
            GLVector(.0f, 1.0f, .0f).put(memoryTarget)
            GLVector(.0f, .0f, 1.0f).put(memoryTarget)
            memoryTarget.asFloatBuffer().put(1.0f)

            logger.debug("UBO buffer now at ${memoryTarget.position()}")
        }
    }

    override fun close() {
        vkDestroyInstance(instance, null)
    }
}
