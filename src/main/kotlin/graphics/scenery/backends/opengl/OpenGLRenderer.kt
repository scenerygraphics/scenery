package graphics.scenery.backends.opengl

import cleargl.*
import com.jogamp.nativewindow.WindowClosingProtocol
import com.jogamp.newt.event.WindowAdapter
import com.jogamp.newt.event.WindowEvent
import com.jogamp.opengl.*
import com.jogamp.opengl.util.FPSAnimator
import com.jogamp.opengl.util.awt.AWTGLReadBufferUtil
import graphics.scenery.*
import graphics.scenery.backends.*
import graphics.scenery.spirvcrossj.Loader
import graphics.scenery.spirvcrossj.libspirvcrossj
import graphics.scenery.utils.*
import javafx.application.Platform
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.io.FileNotFoundException
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.collections.LinkedHashMap
import kotlin.concurrent.withLock
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Deferred Lighting Renderer for scenery
 *
 * This is the main class of scenery's Deferred Lighting Renderer. Currently,
 * a rendering strategy using a 32bit position, 16bit normal, 32bit RGBA diffuse/albedo,
 * and 24bit depth buffer is employed. The renderer supports HDR rendering and does that
 * by default. By deactivating the `hdr.Active` [Settings], HDR can be programmatically
 * deactivated. The renderer also supports drawing to HMDs via OpenVR. If this is intended,
 * make sure the `vr.Active` [Settings] is set to `true`, and that the `Hub` has a HMD
 * instance attached.
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

class OpenGLRenderer(hub: Hub,
                     applicationName: String,
                     scene: Scene,
                     width: Int,
                     height: Int,
                     override var embedIn: SceneryPanel? = null,
                     renderConfigFile: String) : Renderer(), Hubable, ClearGLEventListener {
    /** slf4j logger */
    private val logger by LazyLogger()
    /** [GL4] instance handed over, coming from [ClearGLDefaultEventListener]*/
    lateinit private var gl: GL4
    /** should the window close on next looping? */
    override var shouldClose = false
    /** the scenery window */
    override var window: SceneryWindow = SceneryWindow.UninitializedWindow()
    /** separately stored ClearGLWindow */
    var cglWindow: ClearGLWindow? = null
    /** drawble for offscreen rendering */
    var drawable: GLOffscreenAutoDrawable? = null
    /** Whether the renderer manages its own event loop, which is the case for this one. */
    override var managesRenderLoop = true

    /** The currently active scene */
    var scene: Scene = Scene()

    /** Cache of [Node]s, needed e.g. for fullscreen quad rendering */
    private var nodeStore = ConcurrentHashMap<String, Node>()

    /** [Settings] for the renderer */
    override var settings: Settings = Settings()

    /** The hub used for communication between the components */
    override var hub: Hub? = null

    /** Texture cache */
    private var textureCache = HashMap<String, GLTexture>()

    /** Shader Property cache */
    private var shaderPropertyCache = HashMap<Class<*>, List<Field>>()

    /** JOGL Drawable */
    private var joglDrawable: GLAutoDrawable? = null

    /** Flag set when a screenshot is requested */
    private var screenshotRequested = false

    /** Path of the file where to store the screenshot */
    private var screenshotFilename = ""

    /** H264 movie encoder */
    private var encoder: H264Encoder? = null
    /** Flag set when a movie recording is requested */
    private var recordMovie = false

    /** Eyes of the stereo render targets */
    var eyes = (0..0)

    /** time since last resizing */
    private var lastResizeTimer = Timer()

    /** Window resizing timeout */
    private var WINDOW_RESIZE_TIMEOUT = 200L

    /** Flag to indicate whether framebuffers have to be recreated */
    @Volatile private var mustRecreateFramebuffers = false

    /** GPU stats object */
    private var gpuStats: GPUStats? = null

    /** heartbeat timer */
    private var heartbeatTimer = Timer()
    override var lastFrameTime = System.nanoTime() * 1.0f
    private var currentTime = System.nanoTime()

    final override var initialized = false

    private var pboBuffers: Array<ByteBuffer?> = arrayOf(null, null)
    private var pboBufferAvailable = arrayOf(true, true)
    @Volatile private var pbos: IntArray = intArrayOf(0, 0)
    private var readIndex = 0
    private var updateIndex = 1

    private var renderConfig: RenderConfigReader.RenderConfig
    override var renderConfigFile = ""
        set(config) {
            field = config
            this.renderConfig = RenderConfigReader().loadFromFile(renderConfigFile)

            mustRecreateFramebuffers = true
        }

    private var renderpasses = LinkedHashMap<String, OpenGLRenderpass>()
    private var flow: List<String>

    /**
     * Extension function of Boolean to use Booleans in GLSL
     *
     * This function converts a Boolean to Int 0, if false, and to 1, if true
     */
    fun Boolean.toInt(): Int {
        if (this) {
            return 1
        } else {
            return 0
        }
    }

    var applicationName = ""

    private val MATERIAL_HAS_DIFFUSE = 0x0001
    private val MATERIAL_HAS_AMBIENT = 0x0002
    private val MATERIAL_HAS_SPECULAR = 0x0004
    private val MATERIAL_HAS_NORMAL = 0x0008
    private val MATERIAL_HAS_ALPHAMASK = 0x0010

    inner class ResizeHandler {
        @Volatile var lastResize = -1L
        var lastWidth = 0
        var lastHeight = 0

        @Synchronized fun queryResize() {
            if (lastWidth <= 0 || lastHeight <= 0) {
                lastWidth = Math.max(1, lastWidth)
                lastHeight = Math.max(1, lastHeight)
                return
            }

            if (lastResize > 0L && lastResize + WINDOW_RESIZE_TIMEOUT < System.nanoTime()) {
                lastResize = System.nanoTime()
                return
            }

            if (lastWidth == window.width && lastHeight == window.height) {
                return
            }

            mustRecreateFramebuffers = true
            gl.glDeleteBuffers(2, pbos, 0)
            pbos[0] = 0
            pbos[1] = 0

            window.width = lastWidth
            window.height = lastHeight

            drawable?.setSurfaceSize(window.width, window.height)

            lastResize = -1L
        }
    }

    class OpenGLBuffer(var gl: GL4, var size: Int) {
        var buffer: ByteBuffer
            private set
        var id = intArrayOf(-1)
            private set
        var alignment = 256L
            private set

        init {
            val tmp = intArrayOf(0, 0)
            gl.glGetIntegerv(GL4.GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT, tmp, 0)
            alignment = tmp[0].toLong()

            gl.glGenBuffers(1, id, 0)
            buffer = MemoryUtil.memAlloc(maxOf(tmp[0], size))

            val valBuf = MemoryUtil.memAlloc(maxOf(tmp[0], size))
            while(valBuf.hasRemaining()) { valBuf.put(0xAF.toByte()) }
            valBuf.flip()

            gl.glBindBuffer(GL4.GL_UNIFORM_BUFFER, id[0])
            gl.glBufferData(GL4.GL_UNIFORM_BUFFER, size * 1L, valBuf, GL4.GL_DYNAMIC_DRAW)
            gl.glBindBuffer(GL4.GL_UNIFORM_BUFFER, 0)

            MemoryUtil.memFree(valBuf)
        }

        fun copyFromStagingBuffer() {
            buffer.flip()

            gl.glBindBuffer(GL4.GL_UNIFORM_BUFFER, id[0])
            gl.glBufferSubData(GL4.GL_UNIFORM_BUFFER, 0, buffer.remaining() * 1L, buffer)
            gl.glBindBuffer(GL4.GL_UNIFORM_BUFFER, 0)
        }

        fun reset() {
            buffer.position(0)
            buffer.limit(size)
        }

        fun advance(align: Long = this.alignment): Int {
            val pos = buffer.position()
            val rem = pos.rem(align)

            if (rem != 0L) {
                val newpos = pos + align.toInt() - rem.toInt()
                buffer.position(newpos)
            }

            return buffer.position()
        }
    }

    internal val buffers = HashMap<String, OpenGLBuffer>()
    internal val sceneUBOs = CopyOnWriteArrayList<Node>()

    internal val resizeHandler = ResizeHandler()

    /**
     * Constructor for OpenGLRenderer, initialises geometry buffers
     * according to eye configuration. Also initialises different rendering passes.
     *
     */
    init {

        Loader.loadNatives()
        libspirvcrossj.initializeProcess()

        logger.info("Initializing OpenGL Renderer...")
        this.hub = hub
        this.settings = loadDefaultRendererSettings(hub.get(SceneryElement.Settings) as Settings)
        this.window.width = width
        this.window.height = height

        this.renderConfigFile = renderConfigFile
        this.renderConfig = RenderConfigReader().loadFromFile(renderConfigFile)

        this.flow = this.renderConfig.createRenderpassFlow()

        logger.info("Loaded ${renderConfig.name} (${renderConfig.description ?: "no description"}")

        this.scene = scene
        this.applicationName = applicationName

        val hmd = hub.getWorkingHMDDisplay()
        if (settings.get("vr.Active") && hmd != null) {
            this.window.width = hmd.getRenderTargetSize().x().toInt() * 2
            this.window.height = hmd.getRenderTargetSize().y().toInt()
        }

        if (embedIn != null) {
            val profile = GLProfile.getMaxProgrammableCore(true)

            if(!profile.isGL4) {
                throw UnsupportedOperationException("Could not create OpenGL 4 context, perhaps you need a graphics driver update?")
            }

            val caps = GLCapabilities(profile)
            caps.hardwareAccelerated = true
            caps.doubleBuffered = true
            caps.isOnscreen = false
            caps.isPBuffer = true
            caps.redBits = 8
            caps.greenBits = 8
            caps.blueBits = 8
            caps.alphaBits = 8

            val factory = GLDrawableFactory.getFactory(profile)
            drawable = factory.createOffscreenAutoDrawable(factory.defaultDevice, caps,
                DefaultGLCapabilitiesChooser(), window.width, window.height)
                .apply {

                    addGLEventListener(this@OpenGLRenderer)

                    animator = FPSAnimator(this, 600)
                    animator.setUpdateFPSFrames(600, null)
                    animator.start()

                    embedIn?.let { panel ->
                        panel.imageView.scaleY = -1.0

                        panel.widthProperty()?.addListener { _, _, newWidth ->
                            resizeHandler.lastWidth = newWidth.toInt()
                        }

                        panel.heightProperty()?.addListener { _, _, newHeight ->
                            resizeHandler.lastHeight = newHeight.toInt()
                        }
                    }

                    window = SceneryWindow.JavaFXStage(embedIn!!)
                    window.width = width
                    window.height = height

                    resizeHandler.lastWidth = window.width
                    resizeHandler.lastHeight = window.height

                }
        } else {
            val w = this.window.width
            val h = this.window.height

            cglWindow = ClearGLWindow("",
                w,
                h, this).apply {

                if (embedIn != null) {

                } else {
                    window = SceneryWindow.ClearGLWindow(this)
                    window.width = w
                    window.height = h

                    val windowAdapter = object: WindowAdapter() {
                        override fun windowDestroyNotify(e: WindowEvent?) {
                            shouldClose = true
                            cglWindow?.close()
                        }
                    }

                    this.addWindowListener(windowAdapter)

                    this.setFPS(300)
                    this.start()
                    this.setDefaultCloseOperation(WindowClosingProtocol.WindowClosingMode.DO_NOTHING_ON_CLOSE)

                    this.isVisible = true
                }
            }
        }
    }

    override fun init(pDrawable: GLAutoDrawable) {
        this.gl = pDrawable.gl.gL4

        val width = this.window.width
        val height = this.window.height

        gl.swapInterval = 0

        val driverString = gl.glGetString(GL4.GL_RENDERER)
        val driverVersion = gl.glGetString(GL4.GL_VERSION)
        logger.info("OpenGLRenderer: $width x $height on $driverString, $driverVersion")

        if (driverVersion.toLowerCase().indexOf("nvidia") != -1 && System.getProperty("os.name").toLowerCase().indexOf("windows") != -1) {
            gpuStats = NvidiaGPUStats()
        }

        val numExtensionsBuffer = IntBuffer.allocate(1)
        gl.glGetIntegerv(GL4.GL_NUM_EXTENSIONS, numExtensionsBuffer)
        val extensions = (0 until numExtensionsBuffer[0]).map { gl.glGetStringi(GL4.GL_EXTENSIONS, it) }
        logger.debug("Available OpenGL extensions: ${extensions.joinToString(", ")}")

        settings.set("ssao.FilterRadius", GLVector(5.0f / width, 5.0f / height))

        buffers.put("UBOBuffer", OpenGLBuffer(gl, 10 * 1024 * 1024))
        buffers.put("LightParameters", OpenGLBuffer(gl, 10 * 1024 * 1024))
        buffers.put("VRParameters", OpenGLBuffer(gl, 2 * 1024))
        buffers.put("ShaderPropertyBuffer", OpenGLBuffer(gl, 10 * 1024 * 1024))
        buffers.put("ShaderParametersBuffer", OpenGLBuffer(gl, 128 * 1024))

        prepareDefaultTextures()

        renderpasses = prepareRenderpasses(renderConfig, window.width, window.height)

        // enable required features
//        gl.glEnable(GL4.GL_TEXTURE_GATHER)
        gl.glEnable(GL4.GL_PROGRAM_POINT_SIZE)

        initialized = true

        heartbeatTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                gpuStats?.let {
                    it.update(0)

                    hub?.get(SceneryElement.Statistics).let { s ->
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
            }
        }, 0, 1000)

        initializeScene()
    }

    private fun Node.rendererMetadata(): OpenGLObjectState? {
        return this.metadata["OpenGLRenderer"] as? OpenGLObjectState
    }

    fun prepareRenderpasses(config: RenderConfigReader.RenderConfig, windowWidth: Int, windowHeight: Int): LinkedHashMap<String, OpenGLRenderpass> {
        buffers["ShaderParametersBuffer"]!!.reset()

        val framebuffers = ConcurrentHashMap<String, GLFramebuffer>()
        val passes = LinkedHashMap<String, OpenGLRenderpass>()

        val flow = renderConfig.createRenderpassFlow()

        val supersamplingFactor = if(settings.get<Float>("Renderer.SupersamplingFactor").toInt() == 1) {
            if(cglWindow != null && ClearGLWindow.isRetina(cglWindow!!.gl)) {
                logger.debug("Setting Renderer.SupersamplingFactor to 0.5, as we are rendering on a retina display.")
                settings.set("Renderer.SupersamplingFactor", 0.5f)
                0.5f
            } else {
                settings.get<Float>("Renderer.SupersamplingFactor")
            }
        } else {
            settings.get<Float>("Renderer.SupersamplingFactor")
        }

        scene.findObserver()?.let { cam ->
            cam.perspectiveCamera(cam.fov, windowWidth * supersamplingFactor, windowHeight * supersamplingFactor, cam.nearPlaneDistance, cam.farPlaneDistance)
        }

        settings.set("Renderer.displayWidth", (windowWidth * supersamplingFactor).toInt())
        settings.set("Renderer.displayHeight", (windowHeight * supersamplingFactor).toInt())

        flow.map { passName ->
            val passConfig = config.renderpasses[passName]!!
            val pass = OpenGLRenderpass(passName, passConfig)

            var width = windowWidth
            var height = windowHeight

            config.rendertargets.filter { it.key == passConfig.output }.map { rt ->
                width = (supersamplingFactor * windowWidth * rt.value.size.first).toInt()
                height = (supersamplingFactor * windowHeight * rt.value.size.second).toInt()
                logger.info("Creating render framebuffer ${rt.key} for pass $passName (${width}x$height)")

                settings.set("Renderer.$passName.displayWidth", width)
                settings.set("Renderer.$passName.displayHeight", height)

                if (framebuffers.containsKey(rt.key)) {
                    logger.info("Reusing already created framebuffer")
                    pass.output.put(rt.key, framebuffers[rt.key]!!)
                } else {
                    val framebuffer = GLFramebuffer(gl, width, height)

                    rt.value.attachments.forEach { att ->
                        logger.info(" + attachment ${att.key}, ${att.value.name}")

                        when (att.value) {
                            RenderConfigReader.TargetFormat.RGBA_Float32 -> framebuffer.addFloatRGBABuffer(gl, att.key, 32)
                            RenderConfigReader.TargetFormat.RGBA_Float16 -> framebuffer.addFloatRGBABuffer(gl, att.key, 16)

                            RenderConfigReader.TargetFormat.RGB_Float32 -> framebuffer.addFloatRGBBuffer(gl, att.key, 32)
                            RenderConfigReader.TargetFormat.RGB_Float16 -> framebuffer.addFloatRGBBuffer(gl, att.key, 16)

                            RenderConfigReader.TargetFormat.RG_Float32 -> framebuffer.addFloatRGBuffer(gl, att.key, 32)
                            RenderConfigReader.TargetFormat.RG_Float16 -> framebuffer.addFloatRGBuffer(gl, att.key, 16)
                            RenderConfigReader.TargetFormat.R_Float16 -> framebuffer.addFloatRBuffer(gl, att.key, 16)

                            RenderConfigReader.TargetFormat.RGBA_UInt16 -> framebuffer.addUnsignedByteRGBABuffer(gl, att.key, 16)
                            RenderConfigReader.TargetFormat.RGBA_UInt8 -> framebuffer.addUnsignedByteRGBABuffer(gl, att.key, 8)
                            RenderConfigReader.TargetFormat.R_UInt16 -> framebuffer.addUnsignedByteRBuffer(gl, att.key, 16)
                            RenderConfigReader.TargetFormat.R_UInt8 -> framebuffer.addUnsignedByteRBuffer(gl, att.key, 8)

                            RenderConfigReader.TargetFormat.Depth32 -> framebuffer.addDepthBuffer(gl, att.key, 32)
                            RenderConfigReader.TargetFormat.Depth24 -> framebuffer.addDepthBuffer(gl, att.key, 24)
                        }
                    }

                    pass.output.put(rt.key, framebuffer)
                    framebuffers.put(rt.key, framebuffer)
                }
            }

            if(passConfig.output == "Viewport") {
                width = (supersamplingFactor * windowWidth).toInt()
                height = (supersamplingFactor * windowHeight).toInt()
                logger.info("Creating render framebuffer Viewport for pass $passName (${width}x$height)")

                settings.set("Renderer.$passName.displayWidth", width)
                settings.set("Renderer.$passName.displayHeight", height)

                val framebuffer = GLFramebuffer(gl, width, height)
                framebuffer.addUnsignedByteRGBABuffer(gl, "Viewport", 8)

                pass.output.put("Viewport", framebuffer)
                framebuffers.put("Viewport", framebuffer)
            }

            pass.openglMetadata.renderArea = OpenGLRenderpass.Rect2D(
                (pass.passConfig.viewportSize.first * width).toInt(),
                (pass.passConfig.viewportSize.second * height).toInt(),
                (pass.passConfig.viewportOffset.first * width).toInt(),
                (pass.passConfig.viewportOffset.second * height).toInt())
            logger.debug("Render area for $passName: ${pass.openglMetadata.renderArea.width}x${pass.openglMetadata.renderArea.height}")

            pass.openglMetadata.viewport = OpenGLRenderpass.Viewport(OpenGLRenderpass.Rect2D(
                (pass.passConfig.viewportSize.first * width).toInt(),
                (pass.passConfig.viewportSize.second * height).toInt(),
                (pass.passConfig.viewportOffset.first * width).toInt(),
                (pass.passConfig.viewportOffset.second * height).toInt()),
                0.0f, 1.0f)

            pass.openglMetadata.scissor = OpenGLRenderpass.Rect2D(
                (pass.passConfig.viewportSize.first * width).toInt(),
                (pass.passConfig.viewportSize.second * height).toInt(),
                (pass.passConfig.viewportOffset.first * width).toInt(),
                (pass.passConfig.viewportOffset.second * height).toInt())

            pass.openglMetadata.eye = pass.passConfig.eye
            pass.defaultShader = prepareShaderProgram(Renderer::class.java, pass.passConfig.shaders.toTypedArray())

            pass.initializeShaderParameters(settings, buffers["ShaderParametersBuffer"]!!)

            passes.put(passName, pass)
        }

        // connect inputs
        passes.forEach { pass ->
            val passConfig = config.renderpasses[pass.key]!!

            passConfig.inputs?.forEach { inputTarget ->
                val targetName = if(inputTarget.contains(".")) {
                    inputTarget.substringBefore(".")
                } else {
                    inputTarget
                }

                passes.filter {
                    it.value.output.keys.contains(targetName)
                }.forEach { pass.value.inputs.put(inputTarget, it.value.output[targetName]!!) }
            }

            with(pass.value) {
                // initialize pass if needed
            }
        }

        return passes
    }

    protected fun prepareShaderProgram(baseClass: Class<*>, shaders: Array<String>): OpenGLShaderProgram? {

        val modules = HashMap<GLShaderType, OpenGLShaderModule>()

        shaders.forEach {
            if (baseClass.getResource("shaders/" + it) != null) {
                val m = OpenGLShaderModule.getFromCacheOrCreate(gl, "main", baseClass, "shaders/" + it)
                modules.put(m.shaderType, m)
            } else {
                if(Renderer::class.java.getResource("shaders/" + it) != null && baseClass !is Renderer) {
                    val m = OpenGLShaderModule.getFromCacheOrCreate(gl, "main", Renderer::class.java, "shaders/" + it)
                    modules.put(m.shaderType, m)
                } else {
                    if(Renderer::class.java.getResource("shaders/" + it.substringBeforeLast(".spv")) != null && baseClass !is Renderer) {
                        val m = OpenGLShaderModule.getFromCacheOrCreate(gl, "main", Renderer::class.java, "shaders/" + it)
                        modules.put(m.shaderType, m)
                    } else {
                        logger.warn("Shader not found: shaders/$it")
                        return null
                    }
                }
            }
        }

        val program = OpenGLShaderProgram(gl, modules)
        return if(program.isValid()) {
            program
        } else {
            null
        }
    }

    override fun display(pDrawable: GLAutoDrawable) {
        val fps = pDrawable.animator?.lastFPS ?: 0.0f

        window.setTitle("$applicationName [${this@OpenGLRenderer.javaClass.simpleName}] - ${fps.toInt()} fps")

        this.joglDrawable = pDrawable

        if (mustRecreateFramebuffers) {
            renderpasses = prepareRenderpasses(renderConfig, window.width, window.height)
            flow = renderConfig.createRenderpassFlow()


            mustRecreateFramebuffers = false
        }

        this@OpenGLRenderer.render()
    }

    override fun setClearGLWindow(pClearGLWindow: ClearGLWindow) {
        cglWindow = pClearGLWindow
    }

    override fun getClearGLWindow(): ClearGLDisplayable {
        return cglWindow!!
    }

    override fun reshape(pDrawable: GLAutoDrawable,
                         pX: Int,
                         pY: Int,
                         pWidth: Int,
                         pHeight: Int) {
        var height = pHeight

        if (height == 0)
            height = 1

        this@OpenGLRenderer.reshape(pWidth, height)
    }

    override fun dispose(pDrawable: GLAutoDrawable) {
        cglWindow?.stop()
    }

    /**
     * Based on the [GLFramebuffer], devises a texture unit that can be used
     * for object textures.
     *
     * @param[type] texture type
     * @return Int of the texture unit to be used
     */
    fun textureTypeToUnit(target: OpenGLRenderpass, type: String): Int {
        val offset = if (target.inputs.values.isNotEmpty()) {
            target.inputs.values.sumBy { it.boundBufferNum }
        } else {
            0
        }

        return offset + when (type) {
            "ambient" -> 0
            "diffuse" -> 1
            "specular" -> 2
            "normal" -> 3
            "alphamask" -> 4
            "displacement" -> 5
            "3D-volume" -> 6
            else -> {
                logger.warn("Unknown texture type $type"); 10
            }
        }
    }

    private fun textureTypeToArrayName(type: String): String {
        return when (type) {
            "ambient" -> "ObjectTextures[0]"
            "diffuse" -> "ObjectTextures[1]"
            "specular" -> "ObjectTextures[2]"
            "normal" -> "ObjectTextures[3]"
            "alphamask" -> "ObjectTextures[4]"
            "displacement" -> "ObjectTextures[5]"
            "3D-volume" -> "VolumeTextures"
            else -> {
                logger.warn("Unknown texture type $type")
                "ObjectTextures[0]"
            }
        }
    }

    /**
     * Converts a [GeometryType] to an OpenGL geometry type
     *
     * @return Int of the OpenGL geometry type.
     */
    private fun GeometryType.toOpenGLType(): Int {
        return when (this) {
            GeometryType.TRIANGLE_STRIP -> GL4.GL_TRIANGLE_STRIP
            GeometryType.POLYGON -> GL4.GL_TRIANGLES
            GeometryType.TRIANGLES -> GL4.GL_TRIANGLES
            GeometryType.TRIANGLE_FAN -> GL4.GL_TRIANGLE_FAN
            GeometryType.POINTS -> GL4.GL_POINTS
            GeometryType.LINE -> GL4.GL_LINE_STRIP
            GeometryType.LINES_ADJACENCY -> GL4.GL_LINES_ADJACENCY
            GeometryType.LINE_STRIP_ADJACENCY -> GL4.GL_LINE_STRIP_ADJACENCY
        }
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

    /**
     * Toggles deferred shading buffer debug view. Used for e.g.
     * [graphics.scenery.controls.behaviours.ToggleCommand]
     */
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

    /**
     * Toggles Screen-space ambient occlusion. Used for e.g.
     * [graphics.scenery.controls.behaviours.ToggleCommand].
     */
    @Suppress("UNUSED")
    fun toggleSSAO() {
        if (!settings.get<Boolean>("ssao.Active")) {
            settings.set("ssao.Active", true)
        } else {
            settings.set("ssao.Active", false)
        }
    }

    /**
     * Toggles HDR rendering. Used for e.g.
     * [graphics.scenery.controls.behaviours.ToggleCommand].
     */
    @Suppress("UNUSED")
    fun toggleHDR() {
        if (!settings.get<Boolean>("hdr.Active")) {
            settings.set("hdr.Active", true)
        } else {
            settings.set("hdr.Active", false)
        }
    }

    /**
     * Increases the HDR exposure value. Used for e.g.
     * [graphics.scenery.controls.behaviours.ToggleCommand].
     */
    @Suppress("UNUSED")
    fun increaseExposure() {
        val exp: Float = settings.get<Float>("hdr.Exposure")
        settings.set("hdr.Exposure", exp + 0.05f)
    }

    /**
     * Decreases the HDR exposure value.Used for e.g.
     * [graphics.scenery.controls.behaviours.ToggleCommand].
     */
    @Suppress("UNUSED")
    fun decreaseExposure() {
        val exp: Float = settings.get<Float>("hdr.Exposure")
        settings.set("hdr.Exposure", exp - 0.05f)
    }

    /**
     * Increases the HDR gamma value. Used for e.g.
     * [graphics.scenery.controls.behaviours.ToggleCommand].
     */
    @Suppress("unused")
    fun increaseGamma() {
        val gamma: Float = settings.get<Float>("hdr.Gamma")
        settings.set("hdr.Gamma", gamma + 0.05f)
    }

    /**
     * Decreases the HDR gamma value. Used for e.g.
     * [graphics.scenery.controls.behaviours.ToggleCommand].
     */
    @Suppress("unused")
    fun decreaseGamma() {
        val gamma: Float = settings.get<Float>("hdr.Gamma")
        if (gamma - 0.05f >= 0) settings.set("hdr.Gamma", gamma - 0.05f)
    }

    /**
     * Toggles fullscreen. Used for e.g.
     * [graphics.scenery.controls.behaviours.ToggleCommand].
     */
    @Suppress("unused")
    fun toggleFullscreen() {
        if (!settings.get<Boolean>("wantsFullscreen")) {
            settings.set("wantsFullscreen", true)
        } else {
            settings.set("wantsFullscreen", false)
        }
    }

    /**
     * Convenience function that extracts the [OpenGLObjectState] from a [Node]'s
     * metadata.
     *
     * @param[node] The node of interest
     * @return The [OpenGLObjectState] of the [Node]
     */
    fun getOpenGLObjectStateFromNode(node: Node): OpenGLObjectState {
        return node.metadata["OpenGLRenderer"] as OpenGLObjectState
    }

    /**
     * Initializes the [Scene] with the [OpenGLRenderer], to be called
     * before [render].
     */
    @Synchronized override fun initializeScene() {
        scene.discover(scene, { it is HasGeometry })
            .forEach { it ->
                it.metadata.put("OpenGLRenderer", OpenGLObjectState())
                initializeNode(it)
            }

        scene.initialized = true
        logger.info("Initialized ${textureCache.size} textures")
    }

    private fun Display.wantsVR(): Display? {
        if (settings.get<Boolean>("vr.Active")) {
            return this@wantsVR
        } else {
            return null
        }
    }

    @Synchronized fun updateDefaultUBOs() {
        // find observer, if none, return
        val cam = scene.findObserver() ?: return

        val hmd = hub?.getWorkingHMDDisplay()?.wantsVR()

        cam.view = cam.getTransformation()
        cam.updateWorld(true, false)

        buffers["VRParameters"]!!.reset()
        val vrUbo = OpenGLUBO(backingBuffer = buffers["VRParameters"]!!)

        vrUbo.add("projection0", {
            (hmd?.getEyeProjection(0, cam.nearPlaneDistance, cam.farPlaneDistance)
                ?: cam.projection)
        })
        vrUbo.add("projection1", {
            (hmd?.getEyeProjection(1, cam.nearPlaneDistance, cam.farPlaneDistance)
                ?: cam.projection)
        })
        vrUbo.add("inverseProjection0", {
            (hmd?.getEyeProjection(0, cam.nearPlaneDistance, cam.farPlaneDistance)
                ?: cam.projection).inverse
        })
        vrUbo.add("inverseProjection1", {
            (hmd?.getEyeProjection(1, cam.nearPlaneDistance, cam.farPlaneDistance)
                ?: cam.projection).inverse
        })
        vrUbo.add("headShift", { hmd?.getHeadToEyeTransform(0) ?: GLMatrix.getIdentity() })
        vrUbo.add("IPD", { hmd?.getIPD() ?: 0.05f })
        vrUbo.add("stereoEnabled", { renderConfig.stereoEnabled.toInt() })

        vrUbo.populate()
        buffers["VRParameters"]!!.copyFromStagingBuffer()

        buffers["UBOBuffer"]!!.reset()
        buffers["ShaderPropertyBuffer"]!!.reset()

        sceneUBOs.forEach { node ->
            node.lock.withLock {
                if (!node.metadata.containsKey(this.javaClass.simpleName)) {
                    return@withLock
                }

                val s = node.metadata[this.javaClass.simpleName] as OpenGLObjectState

                val ubo = s.UBOs["Matrices"]!!

                node.updateWorld(true, false)

                var bufferOffset = ubo.backingBuffer!!.advance()
                ubo.offset = bufferOffset
                node.view.copyFrom(cam.view)
                ubo.populate(offset = bufferOffset.toLong())

                val materialUbo = (node.metadata["OpenGLRenderer"]!! as OpenGLObjectState).UBOs["MaterialProperties"]!!
                bufferOffset = ubo.backingBuffer.advance()
                materialUbo.offset = bufferOffset

                materialUbo.populate(offset = bufferOffset.toLong())

                if (s.UBOs.containsKey("ShaderProperties")) {
                    val propertyUbo = s.UBOs["ShaderProperties"]!!
                    // TODO: Correct buffer advancement
                    val offset = propertyUbo.backingBuffer!!.advance()
                    propertyUbo.populate(offset = offset.toLong())
                    propertyUbo.offset = offset
                }
            }
        }

        buffers["UBOBuffer"]!!.copyFromStagingBuffer()
        buffers["LightParameters"]!!.reset()

//        val lights = sceneObjects.filter { it is PointLight }

        val lightUbo = OpenGLUBO(backingBuffer = buffers["LightParameters"]!!)
        lightUbo.add("ViewMatrix0", { cam.getTransformationForEye(0) })
        lightUbo.add("ViewMatrix1", { cam.getTransformationForEye(1) })
        lightUbo.add("InverseViewMatrix0", { cam.getTransformationForEye(0).inverse })
        lightUbo.add("InverseViewMatrix1", { cam.getTransformationForEye(1).inverse })
        lightUbo.add("ProjectionMatrix", { cam.projection })
        lightUbo.add("InverseProjectionMatrix", { cam.projection.inverse })
        lightUbo.add("CamPosition", { cam.position })
//        lightUbo.add("numLights", { lights.size })

//        lights.forEachIndexed { i, light ->
//            val l = light as PointLight
//            l.updateWorld(true, false)
//
//            lightUbo.add("Linear-$i", { l.linear })
//            lightUbo.add("Quadratic-$i", { l.quadratic })
//            lightUbo.add("Intensity-$i", { l.intensity })
//            lightUbo.add("Radius-$i", { -l.linear + Math.sqrt(l.linear * l.linear - 4 * l.quadratic * (1.0 - (256.0f / 5.0) * 100)).toFloat() })
//            lightUbo.add("Position-$i", { l.position })
//            lightUbo.add("Color-$i", { l.emissionColor })
//            lightUbo.add("filler-$i", { 0.0f })
//        }

        lightUbo.populate()

        buffers["LightParameters"]!!.copyFromStagingBuffer()
        buffers["ShaderPropertyBuffer"]!!.copyFromStagingBuffer()
    }


    /**
     * Update a [Node]'s geometry, if needed and run it's preDraw() routine.
     *
     * @param[n] The Node to update and preDraw()
     */
    private fun preDrawAndUpdateGeometryForNode(n: Node) {
        if (n is HasGeometry) {
            if (n.dirty) {
                if (n.lock.tryLock()) {
                    if (n.vertices.remaining() > 0 && n.normals.remaining() > 0) {
                        updateVertices(n)
                        updateNormals(n)
                    }

                    if (n.texcoords.remaining() > 0) {
                        updateTextureCoords(n)
                    }

                    if (n.indices.remaining() > 0) {
                        updateIndices(n)
                    }

                    n.dirty = false

                    n.lock.unlock()
                }
            }

            n.preDraw()
        }
    }

    /**
     * Set a [GLProgram]'s uniforms according to a [Node]'s [ShaderProperty]s.
     *
     * This functions uses reflection to query for a Node's declared fields and checks
     * whether they are marked up with the [ShaderProperty] annotation. If this is the case,
     * the [GLProgram]'s uniform with the same name as the field is set to its value.
     *
     * Currently limited to GLVector, GLMatrix, Int and Float properties.
     *
     * @param[n] The Node to search for [ShaderProperty]s
     * @param[program] The [GLProgram] used to render the Node
     */
    @Suppress("unused")
    private fun setShaderPropertiesForNode(n: Node, program: GLProgram) {
        shaderPropertyCache
            .getOrPut(n.javaClass, { n.javaClass.declaredFields.filter { it.isAnnotationPresent(ShaderProperty::class.java) } })
            .forEach { property ->
                property.isAccessible = true
                val field = property.get(n)

                when (property.type) {
                    GLVector::class.java -> {
                        program.getUniform(property.name).setFloatVector(field as GLVector)
                    }

                    Int::class.java -> {
                        program.getUniform(property.name).setInt(field as Int)
                    }

                    Float::class.java -> {
                        program.getUniform(property.name).setFloat(field as Float)
                    }

                    GLMatrix::class.java -> {
                        program.getUniform(property.name).setFloatMatrix((field as GLMatrix).floatArray, false)
                    }

                    else -> {
                        logger.warn("Could not derive shader data type for @ShaderProperty ${n.javaClass.canonicalName}.${property.name} of type ${property.type}!")
                    }
                }
            }
    }

    private fun blitFramebuffers(source: GLFramebuffer?, target: GLFramebuffer?,
                                 sourceOffset: OpenGLRenderpass.Rect2D,
                                 targetOffset: OpenGLRenderpass.Rect2D,
                                 colorOnly: Boolean = false, depthOnly: Boolean = false,
                                 sourceName: String? = null) {
        if (target != null) {
            target.setDrawBuffers(gl)
        } else {
            gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, 0)
        }

        if (source != null) {
            if(sourceName != null) {
                source.setReadBuffers(gl, sourceName)
            } else {
                source.setReadBuffers(gl)
            }
        } else {
            gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, 0)
        }

        val (blitColor, blitDepth) = when {
            colorOnly && !depthOnly -> true to false
            !colorOnly && depthOnly -> false to true
            else -> true to true
        }

        if(blitColor) {
            if (source?.hasColorAttachment() != false) {
                gl.glBlitFramebuffer(
                    sourceOffset.offsetX, sourceOffset.offsetY,
                    sourceOffset.offsetX + sourceOffset.width, sourceOffset.offsetY + sourceOffset.height,
                    targetOffset.offsetX, targetOffset.offsetY,
                    targetOffset.offsetX + targetOffset.width, targetOffset.offsetY + targetOffset.height,
                    GL4.GL_COLOR_BUFFER_BIT, GL4.GL_LINEAR)
            }
        }

        if(blitDepth) {
            if ((source?.hasDepthAttachment() != false && target?.hasDepthAttachment() != false) || (depthOnly && !colorOnly)) {
                gl.glBlitFramebuffer(
                    sourceOffset.offsetX, sourceOffset.offsetY,
                    sourceOffset.offsetX + sourceOffset.width, sourceOffset.offsetY + sourceOffset.height,
                    targetOffset.offsetX, targetOffset.offsetY,
                    targetOffset.offsetX + targetOffset.width, targetOffset.offsetY + targetOffset.height,
                    GL4.GL_DEPTH_BUFFER_BIT, GL4.GL_NEAREST)
            } else {
                logger.trace("Either source or target don't have a depth buffer. If blitting to window surface, this is not a problem.")
            }
        }

        gl.glBindFramebuffer(GL4.GL_FRAMEBUFFER, 0)
    }

    private fun updateInstanceBuffers(sceneObjects:List<Node>) {
        val instanceMasters = sceneObjects.filter { it.instanceMaster }

        instanceMasters.forEach { parent ->
            updateInstanceBuffer(parent, parent.rendererMetadata()!!)
        }
    }

    private fun updateInstanceBuffer(parentNode: Node, state: OpenGLObjectState): OpenGLObjectState {
        logger.trace("Updating instance buffer for ${parentNode.name}")

        if (parentNode.instances.isEmpty()) {
            logger.debug("$parentNode has no child instances attached, returning.")
            return state
        }

        // first we create a fake UBO to gauge the size of the needed properties
        val ubo = OpenGLUBO()
        ubo.fromInstance(parentNode.instances.first())

        val instanceBufferSize = ubo.getSize() * parentNode.instances.size

        val stagingBuffer = if(state.vertexBuffers.containsKey("instanceStaging") && state.vertexBuffers["instanceStaging"]!!.capacity() >= instanceBufferSize) {
            state.vertexBuffers["instanceStaging"]!!
        } else {
            logger.debug("${parentNode.name}: Creating new staging buffer with capacity=$instanceBufferSize (${ubo.getSize()} x ${parentNode.instances.size})")
            val buffer = BufferUtils.allocateByte(instanceBufferSize)

            state.vertexBuffers.put("instanceStaging", buffer)
            buffer
        }

        logger.trace("{}: Staging buffer position, {}, cap={}", parentNode.name, stagingBuffer.position(), stagingBuffer.capacity())

        val index = AtomicInteger(0)
        parentNode.instances.parallelStream().forEach { node ->
            node.needsUpdate = true
            node.needsUpdateWorld = true
            node.updateWorld(true, false)

            node.metadata.getOrPut("instanceBufferView", {
                stagingBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            }).run {
                val buffer = this as? ByteBuffer?: return@run

                ubo.populateParallel(buffer, offset = index.getAndIncrement() * ubo.getSize()*1L, elements = node.instancedProperties)
            }
        }

        stagingBuffer.position(parentNode.instances.size * ubo.getSize())
        stagingBuffer.flip()

        val instanceBuffer = if (state.additionalBufferIds.containsKey("instance")) {
            state.additionalBufferIds["instance"]!!
        } else {
            logger.debug("Instance buffer for ${parentNode.name} needs to be reallocated due to insufficient size ($instanceBufferSize vs ${state.vertexBuffers["instance"]?.capacity() ?: "<not allocated yet>"})")

            val bufferArray = intArrayOf(0)
            gl.glGenBuffers(1, bufferArray, 0)

            gl.glBindVertexArray(state.mVertexArrayObject[0])
            gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, bufferArray[0])

            // instance data starts after vertex, normal, texcoord
            val locationBase = 3
            var location = locationBase
            parentNode.instances.first().instancedProperties.entries.forEachIndexed { locationOffset, element ->
                val result = element.value.invoke()
                val sizeAlignment = ubo.getSizeAndAlignment(result)
                location += locationOffset

                val glType = when(result.javaClass) {
                    java.lang.Integer::class.java,
                    Int::class.java -> GL4.GL_INT

                    java.lang.Float::class.java,
                    Float::class.java -> GL4.GL_FLOAT

                    java.lang.Boolean::class.java,
                    Boolean::class.java -> GL4.GL_INT

                    GLMatrix::class.java -> GL4.GL_FLOAT
                    GLVector::class.java -> GL4.GL_FLOAT

                    else -> { logger.error("Don't know how to serialise ${result.javaClass} for instancing."); GL4.GL_FLOAT }
                }

                val count = when (result) {
                    is GLMatrix -> 4
                    is GLVector -> result.toFloatArray().size
                    else -> { logger.error("Don't know element size of ${result.javaClass} for instancing."); 1 }
                }

                val necessaryAttributes = if(result is GLMatrix) {
                    result.floatArray.size / count
                } else {
                    1
                }

                logger.trace("{} needs {} locations with {} elements:", result.javaClass, necessaryAttributes, count)
                (0 until necessaryAttributes).forEach { attributeLocation ->
                    val stride = sizeAlignment.first
                    val offset = 1L * attributeLocation * (sizeAlignment.first/necessaryAttributes)

                    logger.trace("{}, stride={}, offset={}",
                        location + attributeLocation,
                        stride,
                        offset)

                    gl.glEnableVertexAttribArray(location + attributeLocation)

                    // glVertexAttribPoint takes parameters:
                    // * index: attribute location
                    // * size: element count per location
                    // * type: the OpenGL type
                    // * normalized: whether the OpenGL type should be taken as normalized
                    // * stride: the stride between different occupants in the instance array
                    // * pointer_buffer_offset: the offset of the current element (e.g. matrix row) with respect
                    //   to the start of the element in the instance array
                    gl.glVertexAttribPointer(location + attributeLocation, count, glType, false,
                        stride, offset)
                    gl.glVertexAttribDivisor(location + attributeLocation, 1)
                }

                location += necessaryAttributes
            }

            gl.glBindVertexArray(0)

            state.additionalBufferIds["instance"] = bufferArray[0]
            bufferArray[0]
        }

        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, instanceBuffer)
        gl.glBufferData(GL4.GL_ARRAY_BUFFER, instanceBufferSize.toLong(), stagingBuffer, GL4.GL_DYNAMIC_DRAW)
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0)

        state.instanceCount = parentNode.instances.size
        logger.trace("Updated instance buffer, {parentNode.name} has {} instances.", parentNode.name, state.instanceCount)

        return state
    }

    /**
     * Renders the [Scene].
     *
     * The general rendering workflow works like this:
     *
     * 1) All visible elements of the Scene are gathered into the renderOrderList, based on their position
     * 2) Nodes that are an instance of another Node, as indicated by their instanceOf property, are gathered
     *    into the instanceGroups map.
     * 3) The eye-dependent geometry buffers are cleared, both color and depth buffers.
     * 4) First for the non-instanced Nodes, then for the instanced Nodes the following steps are executed
     *    for each eye:
     *
     *      i) The world state of the given Node is updated
     *     ii) Model, view, and model-view-projection matrices are calculated for each. If a HMD is present,
     *         the transformation coming from that is taken into account.
     *    iii) The Node's geometry is updated, if necessary.
     *     iv) The eye's geometry buffer is activated and the Node drawn into it.
     *
     * 5) The deferred shading pass is executed, together with eventual post-processing steps, such as SSAO.
     * 6) If HDR is active, Exposure/Gamma tone mapping is performed. Else, this part is skipped.
     * 7) The resulting image is drawn to the screen, or -- if a HMD is present -- submitted to the OpenVR
     *    compositor.
     */
    @Synchronized override fun render() {
        if(!initialized) {
            return
        }

        val newTime = System.nanoTime()
        lastFrameTime = (System.nanoTime() - currentTime)/1e6f
        currentTime = newTime

        val stats = hub?.get(SceneryElement.Statistics) as? Statistics
        hub?.getWorkingHMD()?.update()

        if (shouldClose) {
            try {
                logger.info("Closing window")
                joglDrawable?.animator?.stop()
                cglWindow?.close()
            } catch(e: ThreadDeath) {
                logger.debug("Caught JOGL ThreadDeath, ignoring.")
            }
            return
        }

        val running = hub?.getApplication()?.running ?: true

        if (scene.children.count() == 0 || renderpasses.isEmpty() || mustRecreateFramebuffers || !running) {
            Thread.sleep(200)
            return
        }

        val sceneObjects = scene.discover(scene, { n ->
                n is HasGeometry
                    && n.visible
                    && n.instanceOf == null
            }, useDiscoveryBarriers = true)

        val startUboUpdate = System.nanoTime()
        updateDefaultUBOs()
        stats?.add("OpenGLRenderer.updateUBOs", System.nanoTime() - startUboUpdate)

        val startInstanceUpdate = System.nanoTime()
        updateInstanceBuffers(sceneObjects)
        stats?.add("OpenGLRenderer.updateInstanceBuffers", System.nanoTime() - startInstanceUpdate)

        buffers["ShaderParametersBuffer"]?.let { shaderParametersBuffer ->
            shaderParametersBuffer.reset()
            renderpasses.forEach { name, pass ->
                logger.trace("Updating shader parameters for {}", name)
                pass.updateShaderParameters()
            }
            shaderParametersBuffer.copyFromStagingBuffer()
        }

        flow.forEach { t ->
            if(logger.isDebugEnabled || logger.isTraceEnabled) {
                val error = gl.glGetError()

                if (error != 0) {
                    throw Exception("OpenGL error: $error")
                }
            }

            val pass = renderpasses[t]!!
            logger.trace("Running pass {}", pass.passName)
            val startPass = System.nanoTime()

            if (pass.passConfig.blitInputs) {
                pass.inputs.forEach { name, input ->
                    val targetName = name.substringAfter(".")
                    if(name.contains(".") && input.getTextureType(targetName) == 0) {
                        logger.trace("Blitting {} into {} (color only)", targetName, pass.output.values.first().id)
                        blitFramebuffers(input, pass.output.values.firstOrNull(),
                            pass.openglMetadata.viewport.area, pass.openglMetadata.viewport.area, colorOnly = true, sourceName = name.substringAfter("."))
                    } else if(name.contains(".") && input.getTextureType(targetName) == 1) {
                        logger.trace("Blitting {} into {} (depth only)", targetName, pass.output.values.first().id)
                        blitFramebuffers(input, pass.output.values.firstOrNull(),
                            pass.openglMetadata.viewport.area, pass.openglMetadata.viewport.area, depthOnly = true)
                    } else {
                        logger.trace("Blitting {} into {}", targetName, pass.output.values.first().id)
                        blitFramebuffers(input, pass.output.values.firstOrNull(),
                            pass.openglMetadata.viewport.area, pass.openglMetadata.viewport.area)
                    }
                }
            }

            if (pass.output.isNotEmpty()) {
                pass.output.values.first().setDrawBuffers(gl)
            } else {
                gl.glBindFramebuffer(GL4.GL_FRAMEBUFFER, 0)
            }

            // bind framebuffers to texture units and determine total number
            pass.inputs.values.reversed().fold(0, { acc, fb -> acc + fb.bindTexturesToUnitsWithOffset(gl, acc) })

            gl.glViewport(
                pass.openglMetadata.viewport.area.offsetX,
                pass.openglMetadata.viewport.area.offsetY,
                pass.openglMetadata.viewport.area.width,
                pass.openglMetadata.viewport.area.height)

            gl.glScissor(
                pass.openglMetadata.scissor.offsetX,
                pass.openglMetadata.scissor.offsetY,
                pass.openglMetadata.scissor.width,
                pass.openglMetadata.scissor.height
            )

            gl.glEnable(GL4.GL_SCISSOR_TEST)

            gl.glClearColor(
                pass.openglMetadata.clearValues.clearColor.x(),
                pass.openglMetadata.clearValues.clearColor.y(),
                pass.openglMetadata.clearValues.clearColor.z(),
                pass.openglMetadata.clearValues.clearColor.w())

            if (!pass.passConfig.blitInputs) {
                pass.output.values.forEach {
                    if (it.hasDepthAttachment()) {
                        gl.glClear(GL4.GL_DEPTH_BUFFER_BIT)
                    }
                }
                gl.glClear(GL4.GL_COLOR_BUFFER_BIT)
            }

            gl.glDisable(GL4.GL_SCISSOR_TEST)

            gl.glDepthRange(
                pass.openglMetadata.viewport.minDepth.toDouble(),
                pass.openglMetadata.viewport.maxDepth.toDouble())

            if (pass.passConfig.type == RenderConfigReader.RenderpassType.geometry ||
                pass.passConfig.type == RenderConfigReader.RenderpassType.lights) {

                gl.glEnable(GL4.GL_DEPTH_TEST)
                gl.glEnable(GL4.GL_CULL_FACE)

                if (pass.passConfig.renderTransparent) {
                    gl.glEnable(GL4.GL_BLEND)
                    gl.glBlendFuncSeparate(
                        pass.passConfig.srcColorBlendFactor.toOpenGL(),
                        pass.passConfig.dstColorBlendFactor.toOpenGL(),
                        pass.passConfig.srcAlphaBlendFactor.toOpenGL(),
                        pass.passConfig.dstAlphaBlendFactor.toOpenGL())

                    gl.glBlendEquationSeparate(
                        pass.passConfig.colorBlendOp.toOpenGL(),
                        pass.passConfig.alphaBlendOp.toOpenGL()
                    )
                } else {
                    gl.glDisable(GL4.GL_BLEND)
                }

                val actualObjects = if(pass.passConfig.type == RenderConfigReader.RenderpassType.geometry) {
                    sceneObjects.filter { it !is PointLight }
                } else {
                    sceneObjects.filter { it is PointLight }
                }

                actualObjects.forEach renderLoop@ { n ->
                    if (n.instanceOf != null) {
                        return@renderLoop
                    }

                    if (pass.passConfig.renderOpaque && n.material.blending.transparent && pass.passConfig.renderOpaque != pass.passConfig.renderTransparent) {
                        return@renderLoop
                    }

                    if (pass.passConfig.renderTransparent && !n.material.blending.transparent && pass.passConfig.renderOpaque != pass.passConfig.renderTransparent) {
                        return@renderLoop
                    }

                    if (n.material.doubleSided) {
                        gl.glDisable(GL4.GL_CULL_FACE)
                    }

                    when(n.material.cullingMode) {
                        Material.CullingMode.None -> gl.glDisable(GL4.GL_CULL_FACE)
                        Material.CullingMode.Front -> gl.glCullFace(GL4.GL_FRONT)
                        Material.CullingMode.Back -> gl.glCullFace(GL4.GL_BACK)
                        Material.CullingMode.FrontAndBack -> gl.glCullFace(GL4.GL_FRONT_AND_BACK)
                    }

                    if (n.material.blending.transparent) {
                        with(n.material.blending) {
                            gl.glBlendFuncSeparate(
                                sourceColorBlendFactor.toOpenGL(),
                                destinationColorBlendFactor.toOpenGL(),
                                sourceAlphaBlendFactor.toOpenGL(),
                                destinationAlphaBlendFactor.toOpenGL()
                            )

                            gl.glBlendEquationSeparate(
                                colorBlending.toOpenGL(),
                                alphaBlending.toOpenGL()
                            )
                        }
                    }

                    if (!n.metadata.containsKey("OpenGLRenderer") || !n.initialized) {
                        n.metadata.put("OpenGLRenderer", OpenGLObjectState())
                        initializeNode(n)
                        return@renderLoop
                    }

                    var s = getOpenGLObjectStateFromNode(n)

                    if (n.material.needsTextureReload) {
                        s = loadTexturesForNode(n, s)
                    }

                    if (n is Skybox) {
                        gl.glCullFace(GL4.GL_FRONT)
                        gl.glDepthFunc(GL4.GL_LEQUAL)
                    }

                    preDrawAndUpdateGeometryForNode(n)

                    val shader = if (s.shader != null) {
                        s.shader!!
                    } else {
                        pass.defaultShader!!
                    }

                    shader.use(gl)

                    if (renderConfig.stereoEnabled) {
                        shader.getUniform("currentEye.eye").setInt(pass.openglMetadata.eye)
                    }

                    var unit = 0
                    pass.inputs.keys.reversed().forEach { name ->
                        renderConfig.rendertargets.get(name.substringBefore("."))?.attachments?.forEach {
                            shader.getUniform("Input" + it.key).setInt(unit)
                            unit++
                        }
                    }

                    s.textures.forEach { type, glTexture ->
                        val samplerIndex = textureTypeToUnit(pass, type)

                        @Suppress("SENSELESS_COMPARISON")
                        if (glTexture != null) {
                            gl.glActiveTexture(GL4.GL_TEXTURE0 + samplerIndex)

                            val target = if (glTexture.depth > 1) {
                                GL4.GL_TEXTURE_3D
                            } else {
                                GL4.GL_TEXTURE_2D
                            }

                            gl.glBindTexture(target, glTexture.id)
                            shader.getUniform(textureTypeToArrayName(type)).setInt(samplerIndex)
                        }
                    }

                    var binding = 0

                    (s.UBOs + pass.UBOs).forEach { name, ubo ->
                        val actualName = if (name.contains("ShaderParameters")) {
                            "ShaderParameters"
                        } else {
                            name
                        }

                        if(shader.uboSpecs.containsKey(actualName) && shader.isValid()) {
                            val index = shader.getUniformBlockIndex(actualName)
                            logger.trace("Binding {} for {}, index={}, binding={}, size={}", actualName, n.name, index, binding, ubo.getSize())

                            if (index == -1) {
                                logger.error("Failed to bind UBO $actualName for ${n.name} to $binding")
                            } else {
                                gl.glUniformBlockBinding(shader.id, index, binding)
                                gl.glBindBufferRange(GL4.GL_UNIFORM_BUFFER, binding,
                                    ubo.backingBuffer!!.id[0], 1L * ubo.offset, 1L * ubo.getSize())
                                binding++
                            }
                        }
                    }

                    arrayOf("VRParameters", "LightParameters").forEach { name ->
                        if (shader.uboSpecs.containsKey(name) && shader.isValid()) {
                            val index = shader.getUniformBlockIndex(name)

                            if (index == -1) {
                                logger.error("Failed to bind shader parameter UBO $name for ${pass.passName} to $binding, though it is required by the shader")
                            } else {
                                gl.glUniformBlockBinding(shader.id, index, binding)
                                gl.glBindBufferRange(GL4.GL_UNIFORM_BUFFER, binding,
                                    buffers[name]!!.id[0],
                                    0L, buffers[name]!!.buffer.remaining().toLong())


                                binding++
                            }
                        }
                    }

                    if(n.instanceMaster) {
                        drawNodeInstanced(n)
                    } else {
                        drawNode(n)
                    }
                }
            } else {
                gl.glDisable(GL4.GL_CULL_FACE)

                if (pass.output.any { it.value.hasDepthAttachment() }) {
                    gl.glEnable(GL4.GL_DEPTH_TEST)
                } else {
                    gl.glDisable(GL4.GL_DEPTH_TEST)
                }

                if (pass.passConfig.renderTransparent) {
                    gl.glEnable(GL4.GL_BLEND)

                    gl.glBlendFuncSeparate(
                        pass.passConfig.srcColorBlendFactor.toOpenGL(),
                        pass.passConfig.dstColorBlendFactor.toOpenGL(),
                        pass.passConfig.srcAlphaBlendFactor.toOpenGL(),
                        pass.passConfig.dstAlphaBlendFactor.toOpenGL())

                    gl.glBlendEquationSeparate(
                        pass.passConfig.colorBlendOp.toOpenGL(),
                        pass.passConfig.alphaBlendOp.toOpenGL()
                    )
                } else {
                    gl.glDisable(GL4.GL_BLEND)
                }


                pass.defaultShader?.let { shader ->
                    shader.use(gl)

                    var unit = 0
                    pass.inputs.keys.reversed().forEach { name ->
                        renderConfig.rendertargets.get(name.substringBefore("."))?.attachments?.forEach {
                            shader.getUniform("Input" + it.key).setInt(unit)
                            unit++
                        }
                    }

                    var binding = 0
                    pass.UBOs.forEach { name, ubo ->
                        val actualName = if (name.contains("ShaderParameters")) {
                            "ShaderParameters"
                        } else {
                            name
                        }

                        val index = shader.getUniformBlockIndex(actualName)
                        gl.glUniformBlockBinding(shader.id, index, binding)
                        gl.glBindBufferRange(GL4.GL_UNIFORM_BUFFER, binding,
                            ubo.backingBuffer!!.id[0],
                            1L * ubo.offset, 1L * ubo.getSize())

                        if (index == -1) {
                            logger.error("Failed to bind shader parameter UBO $actualName for ${pass.passName} to $binding")
                        }
                        binding++
                    }

                    arrayOf("LightParameters", "VRParameters").forEach { name ->
                        if (shader.uboSpecs.containsKey(name)) {
                            val index = shader.getUniformBlockIndex(name)
                            gl.glUniformBlockBinding(shader.id, index, binding)
                            gl.glBindBufferRange(GL4.GL_UNIFORM_BUFFER, binding,
                                buffers[name]!!.id[0],
                                0L, buffers[name]!!.buffer.remaining().toLong())

                            if (index == -1) {
                                logger.error("Failed to bind shader parameter UBO $name for ${pass.passName} to $binding, though it is required by the shader")
                            }

                            binding++
                        }
                    }

                    renderFullscreenQuad(shader)
                }
            }

            stats?.add("Renderer.$t.renderTiming", System.nanoTime() - startPass)
        }

        if(logger.isDebugEnabled || logger.isTraceEnabled) {
            val error = gl.glGetError()

            if (error != 0) {
                throw Exception("OpenGL error: $error")
            }
        }

        logger.trace("Running viewport pass")
        val viewportPass = renderpasses.get(flow.last())!!
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, 0)

        blitFramebuffers(viewportPass.output.values.first(), null,
            OpenGLRenderpass.Rect2D(
                settings.get<Int>("Renderer.${viewportPass.passName}.displayWidth"),
                settings.get<Int>("Renderer.${viewportPass.passName}.displayHeight"), 0, 0),
            OpenGLRenderpass.Rect2D(window.width, window.height, 0, 0))

        // submit to OpenVR if attached
        if(hub?.getWorkingHMDDisplay()?.hasCompositor() == true && !mustRecreateFramebuffers) {
            hub?.getWorkingHMDDisplay()?.wantsVR()?.submitToCompositor(
                viewportPass.output.values.first().getTextureId("Viewport"))
        }

        if(embedIn != null || recordMovie) {
            if (shouldClose || mustRecreateFramebuffers) {
                encoder?.finish()

                encoder = null
                return
            }

            if (recordMovie && (encoder == null || encoder?.frameWidth != window.width || encoder?.frameHeight != window.height)) {
                encoder = H264Encoder(window.width, window.height, "$applicationName - ${SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(Date())}.mp4")
            }

            readIndex = (readIndex + 1) % 2
            updateIndex = (updateIndex + 1) % 2

            if (pbos[0] == 0 || pbos[1] == 0 || mustRecreateFramebuffers) {
                gl.glGenBuffers(2, pbos, 0)

                gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, pbos[0])
                gl.glBufferData(GL4.GL_PIXEL_PACK_BUFFER, window.width * window.height * 4L, null, GL4.GL_STREAM_READ)

                gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, pbos[1])
                gl.glBufferData(GL4.GL_PIXEL_PACK_BUFFER, window.width * window.height * 4L, null, GL4.GL_STREAM_READ)

                gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, 0)

                if(pboBuffers[0] != null) {
                    MemoryUtil.memFree(pboBuffers[0])
                }

                if(pboBuffers[1] != null) {
                    MemoryUtil.memFree(pboBuffers[1])
                }

                pboBuffers[0] = null
                pboBuffers[1] = null
            }

            if(pboBuffers[0] == null) {
                pboBuffers[0] = MemoryUtil.memAlloc(4*window.width*window.height)
            }

            if(pboBuffers[1] == null) {
                pboBuffers[1] = MemoryUtil.memAlloc(4*window.width*window.height)
            }

            gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, pbos[updateIndex])

            gl.glReadBuffer(GL4.GL_FRONT)
            gl.glReadPixels(0, 0, window.width, window.height, GL4.GL_BGRA, GL4.GL_UNSIGNED_BYTE, 0)

            gl.glGetBufferSubData(GL4.GL_PIXEL_PACK_BUFFER, 0,
                4L * window.width * window.height, pboBuffers[updateIndex])

            if (!mustRecreateFramebuffers) {
                embedIn?.let { embedPanel ->
                    Platform.runLater {
                        pboBuffers[readIndex]?.let {
                            val id = viewportPass.output.values.first().getTextureId("Viewport")
                            embedPanel.update(it, id = id)
                        }
                    }
                }

                encoder?.let { e ->
                    pboBuffers[readIndex]?.let {
                        e.encodeFrame(it)
                    }
                }
            }

            gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, 0)

            embedIn?.let {
                resizeHandler.queryResize()
            }
        }


        if (screenshotRequested && joglDrawable != null) {
            try {
                val readBufferUtil = AWTGLReadBufferUtil(joglDrawable!!.glProfile, false)
                val image = readBufferUtil.readPixelsToBufferedImage(gl, true)
                val file = if(screenshotFilename == "") {
                    File(System.getProperty("user.home"), "Desktop" + File.separator + "$applicationName - ${SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(Date())}.png")
                } else {
                    File(screenshotFilename)
                }

                ImageIO.write(image, "png", file)
                logger.info("Screenshot saved to ${file.absolutePath}")
            } catch (e: Exception) {
                logger.error("Unable to take screenshot: ")
                e.printStackTrace()
            }

            screenshotRequested = false
        }
    }

    /**
     * Renders a fullscreen quad, using from an on-the-fly generated
     * Node that is saved in [nodeStore], with the [GLProgram]'s ID.
     *
     * @param[program] The [GLProgram] to draw into the fullscreen quad.
     */
    fun renderFullscreenQuad(program: OpenGLShaderProgram) {
        val quad: Node
        val quadName = "fullscreenQuad-${program.id}"

        if (!nodeStore.containsKey(quadName)) {
            quad = Plane(GLVector(1.0f, 1.0f, 0.0f))

            quad.metadata.put("OpenGLRenderer", OpenGLObjectState())
            initializeNode(quad)

            nodeStore.put(quadName, quad)
        } else {
            quad = nodeStore[quadName]!!
        }

        drawNode(quad, count = 3)
        program.gl.glBindTexture(GL4.GL_TEXTURE_2D, 0)
    }

    /**
     * Initializes a given [Node].
     *
     * This function initializes a Node, equipping its metadata with an [OpenGLObjectState],
     * generating VAOs and VBOs. If the Node has a [Material] assigned, a [GLProgram] fitting
     * this Material will be used. Else, a default GLProgram will be used.
     *
     * For the assigned Material case, the GLProgram is derived either from the class name of the
     * Node (if useClassDerivedShader is set), or from a set [ShaderMaterial] which may define
     * the whole shader pipeline for the Node.
     *
     * If the [Node] implements [HasGeometry], it's geometry is also initialized by this function.
     *
     * @param[node]: The [Node] to initialise.
     * @return True if the initialisation went alright, False if it failed.
     */
    @Synchronized fun initializeNode(node: Node): Boolean {
        if(!node.lock.tryLock(2, TimeUnit.MILLISECONDS)) {
            return false
        }

        val s: OpenGLObjectState

        if (node.instanceOf == null) {
            s = node.metadata["OpenGLRenderer"] as OpenGLObjectState
        } else {
            s = node.instanceOf!!.metadata["OpenGLRenderer"] as OpenGLObjectState
            node.metadata["OpenGLRenderer"] = s

            if (!s.initialized) {
                logger.trace("Instance not yet initialized, doing now...")
                initializeNode(node.instanceOf!!)
            }

//            if (!s.additionalBufferIds.containsKey("Model") || !s.additionalBufferIds.containsKey("ModelView") || !s.additionalBufferIds.containsKey("MVP")) {
//                logger.trace("${node.name} triggered instance buffer creation")
//                createInstanceBuffer(node.instanceOf!!)
//                logger.trace("---")
//            }
            return true
        }

        if (s.initialized) {
            return true
        }

        // generate VAO for attachment of VBO and indices
        gl.glGenVertexArrays(1, s.mVertexArrayObject, 0)

        // generate three VBOs for coords, normals, texcoords
        gl.glGenBuffers(3, s.mVertexBuffers, 0)
        gl.glGenBuffers(1, s.mIndexBuffer, 0)

        when {
            node.useClassDerivedShader -> {
                val javaClass = node.javaClass.simpleName
                val className = javaClass.substring(javaClass.indexOf(".") + 1)

                val shaders = arrayOf(".vert", ".geom", ".tese", ".tesc", ".frag", ".comp")
                    .map { "$className$it" }
                    .filter {
                        Renderer::class.java.getResource("shaders/$it") != null
                    }

                try {
                    s.shader = prepareShaderProgram(Renderer::class.java, shaders.toTypedArray())
                } catch (e: ShaderCompilationException) {
                    logger.warn("Shader compilation for node ${node.name} with shaders $shaders failed, falling back to default shaders.")
                    logger.warn("Shader compiler error was: ${e.message}")
                    s.shader = null
                }
            }

            node.material is ShaderMaterial -> {
                val shaders = (node.material as ShaderMaterial).shaders.toTypedArray()

                try {
                    s.shader = prepareShaderProgram(node.javaClass, shaders)
                } catch (e: ShaderCompilationException) {
                    logger.warn("Shader compilation for node ${node.name} with shaders $shaders failed, falling back to default shaders.")
                    logger.warn("Shader compiler error was: ${e.message}")
                }
            }
            else -> s.shader = null
        }

        if (node is HasGeometry) {
            setVerticesAndCreateBufferForNode(node)
            setNormalsAndCreateBufferForNode(node)

            if (node.texcoords.limit() > 0) {
                setTextureCoordsAndCreateBufferForNode(node)
            }

            if (node.indices.limit() > 0) {
                setIndicesAndCreateBufferForNode(node)
            }

        }

        val matricesUbo = OpenGLUBO(backingBuffer = buffers["UBOBuffer"])
        with(matricesUbo) {
            name = "Matrices"
            add("ModelMatrix", { node.world })
            add("NormalMatrix", { node.world.inverse.transpose() })
            add("isBillboard", { node.isBillboard.toInt() })

            sceneUBOs.add(node)

            s.UBOs.put("Matrices", this)
        }

        loadTexturesForNode(node, s)

        val materialUbo = OpenGLUBO(backingBuffer = buffers["UBOBuffer"])

        with(materialUbo) {
            name = "MaterialProperties"
            add("materialType", { node.materialToMaterialType() })
            add("Ka", { node.material.ambient })
            add("Kd", { node.material.diffuse })
            add("Ks", { node.material.specular })
            add("Roughness", { node.material.roughness })
            add("Metallic", { node.material.metallic })
            add("Opacity", { node.material.blending.opacity })

            s.UBOs.put("MaterialProperties", this)
        }

        if (node.javaClass.kotlin.memberProperties.filter { it.findAnnotation<ShaderProperty>() != null }.count() > 0) {
            val shaderPropertyUBO = OpenGLUBO(backingBuffer = buffers["ShaderPropertyBuffer"])
            with(shaderPropertyUBO) {
                name = "ShaderProperties"

                val shader = if (node.useClassDerivedShader || node.material is ShaderMaterial) {
                    s.shader
                } else {
                    renderpasses.filter {
                        (it.value.passConfig.type == RenderConfigReader.RenderpassType.geometry || it.value.passConfig.type == RenderConfigReader.RenderpassType.lights)
                            && it.value.passConfig.renderTransparent == node.material.blending.transparent
                    }.entries.firstOrNull()?.value?.defaultShader
                }

                logger.debug("Shader properties are: ${shader?.getShaderPropertyOrder()}")
                shader?.getShaderPropertyOrder()?.forEach { name, offset ->
                    add(name, { node.getShaderProperty(name)!! }, offset)
                }
            }

            s.UBOs.put("ShaderProperties", shaderPropertyUBO)
        }

        s.initialized = true
        node.initialized = true
        node.metadata[this.javaClass.simpleName] = s

        s.initialized = true
        node.lock.unlock()
        return true
    }

    private fun Node.materialToMaterialType(): Int {
        var materialType = 0
        val s = this.metadata["OpenGLRenderer"] as? OpenGLObjectState ?: return 0

        s.defaultTexturesFor.clear()
        arrayOf("ambient", "diffuse", "specular", "normal", "alphamask", "displacement").forEach {
            if (!s.textures.containsKey(it)) {
                s.defaultTexturesFor.add(it)
            }
        }

        if (this.material.textures.containsKey("ambient") && !s.defaultTexturesFor.contains("ambient")) {
            materialType = materialType or MATERIAL_HAS_AMBIENT
        }

        if (this.material.textures.containsKey("diffuse") && !s.defaultTexturesFor.contains("diffuse")) {
            materialType = materialType or MATERIAL_HAS_DIFFUSE
        }

        if (this.material.textures.containsKey("specular") && !s.defaultTexturesFor.contains("specular")) {
            materialType = materialType or MATERIAL_HAS_SPECULAR
        }

        if (this.material.textures.containsKey("normal") && !s.defaultTexturesFor.contains("normal")) {
            materialType = materialType or MATERIAL_HAS_NORMAL
        }

        if (this.material.textures.containsKey("alphamask") && !s.defaultTexturesFor.contains("alphamask")) {
            materialType = materialType or MATERIAL_HAS_ALPHAMASK
        }

        return materialType
    }

    /**
     * Initializes a default set of textures that the renderer can fall back to and provide a non-intrusive
     * hint to the user that a texture could not be loaded.
     */
    private fun prepareDefaultTextures() {
        val t = GLTexture.loadFromFile(gl, Renderer::class.java.getResourceAsStream("DefaultTexture.png"), "png", false, true, 8)
        if(t == null) {
            logger.error("Could not load default texture! This indicates a serious issue.")
        } else {
            textureCache.put("DefaultTexture", t)
        }
    }

    /**
     * Loads textures for a [Node]. The textures either come from a [Material.transferTextures] buffer,
     * or from a file. This is indicated by stating fromBuffer:bufferName in the textures hash map.
     *
     * @param[node] The [Node] to load textures for.
     * @param[s] The [Node]'s [OpenGLObjectState]
     */
    @Suppress("USELESS_ELVIS")
    private fun loadTexturesForNode(node: Node, s: OpenGLObjectState): OpenGLObjectState {
        if (node.lock.tryLock()) {
            node.material.textures.forEach {
                type, texture ->
                if (!textureCache.containsKey(texture) || node.material.needsTextureReload) {
                    logger.debug("Loading texture $texture for ${node.name}")

                    val generateMipmaps = (type == "ambient" || type == "diffuse" || type == "specular")
                    if (texture.startsWith("fromBuffer:")) {
                        node.material.transferTextures[texture.substringAfter("fromBuffer:")]?.let {
                            (_, dimensions, channels, type1, contents, repeatS, repeatT) ->

                            logger.debug("Dims of $texture: $dimensions, mipmaps=$generateMipmaps")

                            val miplevels = if (generateMipmaps && dimensions.z().toInt() == 1) {
                                1 + Math.floor(Math.log(Math.max(dimensions.x() * 1.0, dimensions.y() * 1.0)) / Math.log(2.0)).toInt()
                            } else {
                                1
                            }

                            // TODO: Add support for more than one channel for volumes
                            val channelCount = if (dimensions.z().toInt() == 1) {
                                channels
                            } else {
                                1
                            }

                            val t = GLTexture(gl, type1, channelCount,
                                dimensions.x().toInt(),
                                dimensions.y().toInt(),
                                dimensions.z().toInt() ?: 1,
                                dimensions.z().toInt() <= 1,
                                miplevels)

                            if (generateMipmaps) {
                                t.updateMipMaps()
                            }

                            t.setClamp(!repeatS, !repeatT)

                            val unpackAlignment = intArrayOf(0)
                            gl.glGetIntegerv(GL4.GL_UNPACK_ALIGNMENT, unpackAlignment, 0)

                            // textures might have very uneven dimensions, so we adjust GL_UNPACK_ALIGNMENT here correspondingly
                            // in case the byte count of the texture is not divisible by it.
                            if(contents.remaining() % unpackAlignment[0] == 0 && dimensions.x().toInt() % unpackAlignment[0] == 0) {
                                t.copyFrom(contents)
                            } else {
                                gl.glPixelStorei(GL4.GL_UNPACK_ALIGNMENT, 1)
                                t.copyFrom(contents)
                                gl.glPixelStorei(GL4.GL_UNPACK_ALIGNMENT, unpackAlignment[0])
                            }

                            s.textures.put(type, t)
                            textureCache.put(texture, t)
                        }
                    } else {
                        val glTexture = if(texture.contains("jar!")) {
                            val f = texture.substringAfterLast("!")
                            val stream = node.javaClass.getResourceAsStream(f)

                            if(stream == null) {
                                logger.error("Texture not found for $node: $f (from JAR)")
                                textureCache["DefaultTexture"]!!
                            } else {
                                GLTexture.loadFromFile(gl, stream, texture.substringAfterLast("."), true, generateMipmaps, 8)
                            }
                        } else {
                            try {
                                GLTexture.loadFromFile(gl, texture, true, generateMipmaps, 8)
                            } catch(e: FileNotFoundException) {
                                logger.error("Texture not found for $node: $texture")
                                textureCache["DefaultTexture"]!!
                            }
                        }

                        s.textures.put(type, glTexture)
                        textureCache.put(texture, glTexture)
                    }
                } else {
                    s.textures.put(type, textureCache[texture]!!)
                }
            }

            node.material.needsTextureReload = false
            s.initialized = true
            node.lock.unlock()
            return s
        } else {
            return s
        }
    }

    /**
     * Reshape the renderer's viewports
     *
     * This routine is called when a change in window size is detected, e.g. when resizing
     * it manually or toggling fullscreen. This function updates the sizes of all used
     * geometry buffers and will also create new buffers in case vr.Active is changed.
     *
     * This function also clears color and depth buffer bits.
     *
     * @param[newWidth] The resized window's width
     * @param[newHeight] The resized window's height
     */
    override fun reshape(newWidth: Int, newHeight: Int) {
        if (!initialized) {
            return
        }

        lastResizeTimer.cancel()

        lastResizeTimer = Timer()
        lastResizeTimer.schedule(object : TimerTask() {
            override fun run() {
                window.width = newWidth
                window.height = newHeight

                logger.debug("Resizing window to ${newWidth}x$newHeight")
                mustRecreateFramebuffers = true
            }
        }, WINDOW_RESIZE_TIMEOUT)
    }

    /**
     * Creates VAOs and VBO for a given [Node]'s vertices.
     *
     * @param[node] The [Node] to create the VAO/VBO for.
     */
    fun setVerticesAndCreateBufferForNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node)
        val pVertexBuffer: FloatBuffer = (node as HasGeometry).vertices

        s.mStoredPrimitiveCount = pVertexBuffer.remaining() / node.vertexSize

        gl.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, s.mVertexBuffers[0])

        gl.glEnableVertexAttribArray(0)
        gl.glBufferData(GL4.GL_ARRAY_BUFFER,
            (pVertexBuffer.remaining() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
            pVertexBuffer,
            if (s.isDynamic)
                GL4.GL_DYNAMIC_DRAW
            else
                GL4.GL_DYNAMIC_DRAW)

        gl.glVertexAttribPointer(0,
            node.vertexSize,
            GL4.GL_FLOAT,
            false,
            0,
            0)

        gl.glBindVertexArray(0)
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Updates a [Node]'s vertices.
     *
     * @param[node] The [Node] to update the vertices for.
     */
    fun updateVertices(node: Node) {
        val s = getOpenGLObjectStateFromNode(node)
        val pVertexBuffer: FloatBuffer = (node as HasGeometry).vertices

        s.mStoredPrimitiveCount = pVertexBuffer.remaining() / node.vertexSize

        gl.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, s.mVertexBuffers[0])

        gl.glEnableVertexAttribArray(0)
        gl.glBufferData(GL4.GL_ARRAY_BUFFER,
            (pVertexBuffer.remaining() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
            pVertexBuffer,
            GL4.GL_DYNAMIC_DRAW)

        gl.glVertexAttribPointer(0,
            node.vertexSize,
            GL4.GL_FLOAT,
            false,
            0,
            0)

        gl.glBindVertexArray(0)
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Creates VAOs and VBO for a given [Node]'s normals.
     *
     * @param[node] The [Node] to create the normals VBO for.
     */
    fun setNormalsAndCreateBufferForNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node)
        val pNormalBuffer: FloatBuffer = (node as HasGeometry).normals

        gl.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, s.mVertexBuffers[1])

        if (pNormalBuffer.limit() > 0) {
            gl.glEnableVertexAttribArray(1)
            gl.glBufferData(GL4.GL_ARRAY_BUFFER,
                (pNormalBuffer.remaining() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pNormalBuffer,
                if (s.isDynamic)
                    GL4.GL_DYNAMIC_DRAW
                else
                    GL4.GL_STATIC_DRAW)

            gl.glVertexAttribPointer(1,
                node.vertexSize,
                GL4.GL_FLOAT,
                false,
                0,
                0)

        }
        gl.glBindVertexArray(0)
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Updates a given [Node]'s normals.
     *
     * @param[node] The [Node] whose normals need updating.
     */
    fun updateNormals(node: Node) {
        val s = getOpenGLObjectStateFromNode(node)
        val pNormalBuffer: FloatBuffer = (node as HasGeometry).normals

        gl.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, s.mVertexBuffers[1])

        gl.glEnableVertexAttribArray(1)
        gl.glBufferData(GL4.GL_ARRAY_BUFFER,
            (pNormalBuffer.remaining() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
            pNormalBuffer,
            if (s.isDynamic)
                GL4.GL_DYNAMIC_DRAW
            else
                GL4.GL_STATIC_DRAW)

        gl.glVertexAttribPointer(1,
            node.vertexSize,
            GL4.GL_FLOAT,
            false,
            0,
            0)

        gl.glBindVertexArray(0)
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Creates VAOs and VBO for a given [Node]'s texcoords.
     *
     * @param[node] The [Node] to create the texcoord VBO for.
     */
    fun setTextureCoordsAndCreateBufferForNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node)
        val pTextureCoordsBuffer: FloatBuffer = (node as HasGeometry).texcoords

        gl.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, s.mVertexBuffers[2])

        gl.glEnableVertexAttribArray(2)
        gl.glBufferData(GL4.GL_ARRAY_BUFFER,
            (pTextureCoordsBuffer.remaining() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
            pTextureCoordsBuffer,
            if (s.isDynamic)
                GL4.GL_DYNAMIC_DRAW
            else
                GL4.GL_DYNAMIC_DRAW)

        gl.glVertexAttribPointer(2,
            node.texcoordSize,
            GL4.GL_FLOAT,
            false,
            0,
            0)

        gl.glBindVertexArray(0)
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Updates a given [Node]'s texcoords.
     *
     * @param[node] The [Node] whose texcoords need updating.
     */
    fun updateTextureCoords(node: Node) {
        val s = getOpenGLObjectStateFromNode(node)
        val pTextureCoordsBuffer: FloatBuffer = (node as HasGeometry).texcoords

        gl.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER,
            s.mVertexBuffers[2])

        gl.glEnableVertexAttribArray(2)
        gl.glBufferData(GL4.GL_ARRAY_BUFFER,
            (pTextureCoordsBuffer.remaining() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
            pTextureCoordsBuffer,
            if (s.isDynamic)
                GL4.GL_DYNAMIC_DRAW
            else
                GL4.GL_STATIC_DRAW)

        gl.glVertexAttribPointer(2,
            node.texcoordSize,
            GL4.GL_FLOAT,
            false,
            0,
            0)

        gl.glBindVertexArray(0)
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Creates a index buffer for a given [Node]'s indices.
     *
     * @param[node] The [Node] to create the index buffer for.
     */
    fun setIndicesAndCreateBufferForNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node)
        val pIndexBuffer: IntBuffer = (node as HasGeometry).indices

        s.mStoredIndexCount = pIndexBuffer.remaining()

        gl.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL4.GL_ELEMENT_ARRAY_BUFFER, s.mIndexBuffer[0])

        gl.glBufferData(GL4.GL_ELEMENT_ARRAY_BUFFER,
            (pIndexBuffer.remaining() * (Integer.SIZE / java.lang.Byte.SIZE)).toLong(),
            pIndexBuffer,
            if (s.isDynamic)
                GL4.GL_DYNAMIC_DRAW
            else
                GL4.GL_DYNAMIC_DRAW)

        gl.glBindVertexArray(0)
        gl.glBindBuffer(GL4.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    /**
     * Updates a given [Node]'s indices.
     *
     * @param[node] The [Node] whose indices need updating.
     */
    fun updateIndices(node: Node) {
        val s = getOpenGLObjectStateFromNode(node)
        val pIndexBuffer: IntBuffer = (node as HasGeometry).indices

        s.mStoredIndexCount = pIndexBuffer.remaining()

        gl.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL4.GL_ELEMENT_ARRAY_BUFFER, s.mIndexBuffer[0])

        gl.glBufferData(GL4.GL_ELEMENT_ARRAY_BUFFER,
            (pIndexBuffer.remaining() * (Integer.SIZE / java.lang.Byte.SIZE)).toLong(),
            pIndexBuffer,
            if (s.isDynamic)
                GL4.GL_DYNAMIC_DRAW
            else
                GL4.GL_DYNAMIC_DRAW)

        gl.glBindVertexArray(0)
        gl.glBindBuffer(GL4.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    /**
     * Draws a given [Node], either in element or in index draw mode.
     *
     * @param[node] The node to be drawn.
     * @param[offset] offset in the array or index buffer.
     */
    fun drawNode(node: Node, offset: Int = 0, count: Int? = null) {
        val s = getOpenGLObjectStateFromNode(node)

        if (s.mStoredIndexCount == 0 && s.mStoredPrimitiveCount == 0 || node.material.needsTextureReload) {
            return
        }
        logger.trace("Drawing {} with {}, {} primitives, {} indices", node.name, s.shader?.modules?.entries?.joinToString(", "), s.mStoredPrimitiveCount, s.mStoredIndexCount)
        gl.glBindVertexArray(s.mVertexArrayObject[0])

        if (s.mStoredIndexCount > 0) {
            gl.glBindBuffer(GL4.GL_ELEMENT_ARRAY_BUFFER,
                s.mIndexBuffer[0])
            gl.glDrawElements((node as HasGeometry).geometryType.toOpenGLType(),
                count ?: s.mStoredIndexCount,
                GL4.GL_UNSIGNED_INT,
                offset.toLong())

            gl.glBindBuffer(GL4.GL_ELEMENT_ARRAY_BUFFER, 0)
        } else {
            gl.glDrawArrays((node as HasGeometry).geometryType.toOpenGLType(), offset, count ?: s.mStoredPrimitiveCount)
        }

        gl.glUseProgram(0)
        gl.glBindVertexArray(0)
    }

    /**
     * Draws a given instanced [Node] either in element or in index draw mode.
     *
     * @param[node] The node to be drawn.
     * @param[count] The number of instances to be drawn.
     * @param[offset] offset in the array or index buffer.
     */
    fun drawNodeInstanced(node: Node, offset: Long = 0) {
        val s = getOpenGLObjectStateFromNode(node)

        gl.glBindVertexArray(s.mVertexArrayObject[0])

        if (s.mStoredIndexCount > 0) {
            gl.glDrawElementsInstanced(
                (node as HasGeometry).geometryType.toOpenGLType(),
                s.mStoredIndexCount,
                GL4.GL_UNSIGNED_INT,
                offset,
                s.instanceCount)
        } else {
            gl.glDrawArraysInstanced(
                (node as HasGeometry).geometryType.toOpenGLType(),
                0, s.mStoredPrimitiveCount, s.instanceCount)

        }

        gl.glUseProgram(0)
        gl.glBindVertexArray(0)
    }

    override fun screenshot(filename: String) {
        screenshotRequested = true
        screenshotFilename = filename
    }

    fun recordMovie() {
        if(recordMovie) {
            encoder?.finish()
            encoder = null

            recordMovie = false
        } else {
            recordMovie = true
        }
    }

    private fun Blending.BlendFactor.toOpenGL() = when (this) {
        Blending.BlendFactor.Zero -> GL4.GL_ZERO
        Blending.BlendFactor.One -> GL4.GL_ONE

        Blending.BlendFactor.SrcAlpha -> GL4.GL_SRC_ALPHA
        Blending.BlendFactor.OneMinusSrcAlpha -> GL4.GL_ONE_MINUS_SRC_ALPHA

        Blending.BlendFactor.SrcColor -> GL4.GL_SRC_COLOR
        Blending.BlendFactor.OneMinusSrcColor -> GL4.GL_ONE_MINUS_SRC_COLOR

        Blending.BlendFactor.DstColor -> GL4.GL_DST_COLOR
        Blending.BlendFactor.OneMinusDstColor -> GL4.GL_ONE_MINUS_DST_COLOR

        Blending.BlendFactor.DstAlpha -> GL4.GL_DST_ALPHA
        Blending.BlendFactor.OneMinusDstAlpha -> GL4.GL_ONE_MINUS_DST_ALPHA

        Blending.BlendFactor.ConstantColor -> GL4.GL_CONSTANT_COLOR
        Blending.BlendFactor.OneMinusConstantColor -> GL4.GL_ONE_MINUS_CONSTANT_COLOR

        Blending.BlendFactor.ConstantAlpha -> GL4.GL_CONSTANT_ALPHA
        Blending.BlendFactor.OneMinusConstantAlpha -> GL4.GL_ONE_MINUS_CONSTANT_ALPHA

        Blending.BlendFactor.Src1Color -> GL4.GL_SRC1_COLOR
        Blending.BlendFactor.OneMinusSrc1Color -> GL4.GL_ONE_MINUS_SRC1_COLOR

        Blending.BlendFactor.Src1Alpha -> GL4.GL_SRC1_ALPHA
        Blending.BlendFactor.OneMinusSrc1Alpha -> GL4.GL_ONE_MINUS_SRC1_ALPHA

        Blending.BlendFactor.SrcAlphaSaturate -> GL4.GL_SRC_ALPHA_SATURATE
    }

    private fun Blending.BlendOp.toOpenGL() = when (this) {
        Blending.BlendOp.add -> GL4.GL_FUNC_ADD
        Blending.BlendOp.subtract -> GL4.GL_FUNC_SUBTRACT
        Blending.BlendOp.min -> GL4.GL_MIN
        Blending.BlendOp.max -> GL4.GL_MAX
        Blending.BlendOp.reverse_subtract -> GL4.GL_FUNC_REVERSE_SUBTRACT
    }

    /**
     * Sets the rendering quality, if the loaded renderer config file supports it.
     *
     * @param[quality] The [RenderConfigReader.RenderingQuality] to be set.
     */
    override fun setRenderingQuality(quality: RenderConfigReader.RenderingQuality) {
        if(renderConfig.qualitySettings.isNotEmpty()) {
            logger.info("Setting rendering quality to $quality")
            renderConfig.qualitySettings.get(quality)?.forEach { setting ->
                val key = "Renderer.${setting.key}"

                logger.debug("Setting $key: ${settings.get<Any>(key)} -> ${setting.value}")
                settings.set(key, setting.value)
            }
        } else {
            logger.warn("The current renderer config, $renderConfigFile, does not support setting quality options.")
        }
    }

    override fun close() {
        libspirvcrossj.finalizeProcess()

        encoder?.let {
            it.finish()
        }
    }
}
