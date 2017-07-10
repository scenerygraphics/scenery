package graphics.scenery.backends.opengl

import cleargl.*
import com.jogamp.common.nio.Buffers
import com.jogamp.opengl.*
import com.jogamp.opengl.util.FPSAnimator
import com.jogamp.opengl.util.awt.AWTGLReadBufferUtil
import graphics.scenery.*
import graphics.scenery.backends.Display
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.backends.ShaderPreference
import graphics.scenery.controls.TrackerInput
import graphics.scenery.fonts.SDFFontAtlas
import graphics.scenery.utils.GPUStats
import graphics.scenery.utils.NvidiaGPUStats
import graphics.scenery.utils.SceneryPanel
import graphics.scenery.utils.Statistics
import javafx.application.Platform
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Field
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.concurrent.thread

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
 * @param[hub] A [Hub] instance needed for Scenery-internal communication
 * @param[applicationName] The name of this application
 * @param[scene] The initial scene to use
 * @param[width] Initial window width, will be used for framebuffer construction
 * @param[height] Initial window height, will be used for framebuffer construction
 * @param[embedIn] JavaFX SceneryPanel where this renderer might be embedded in, otherwise null
 *
 * @constructor Initializes the [OpenGLRenderer] with the given window dimensions and GL context
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

class OpenGLRenderer(hub: Hub, applicationName: String, scene: Scene, width: Int, height: Int, override var embedIn: SceneryPanel? = null) : Renderer, Hubable, ClearGLDefaultEventListener() {
    /** slf4j logger */
    private var logger: Logger = LoggerFactory.getLogger("OpenGLRenderer")
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

    /** [GLFramebuffer] Geometry buffer for rendering */
    private var geometryBuffer = ArrayList<GLFramebuffer>()

    /** [GLFramebuffer] used for HDR rendering */
    private var hdrBuffer = ArrayList<GLFramebuffer>()

    /** 3rd [GLFramebuffer] to use for combining eventual stereo render targets */
    private var combinationBuffer = ArrayList<GLFramebuffer>()

    /** [GLProgram] for the deferred shading pass */
    private var lightingPassProgram: GLProgram? = null

    /** [GLProgram] used for the Exposure/Gamma HDR pass */
    private var hdrPassProgram: GLProgram? = null

    /** [GLProgram] used for combining stereo render targets */
    private var combinerProgram: GLProgram? = null

    /** Cache of [Node]s, needed e.g. for fullscreen quad rendering */
    private var nodeStore = ConcurrentHashMap<String, Node>()

    /** Cache for [SDFFontAtlas]es used for font rendering */
    private var fontAtlas = HashMap<String, SDFFontAtlas>()

    /** [Settings] for the renderer */
    override var settings: Settings = Settings()

    /** The hub used for communication between the components */
    override var hub: Hub? = null

    /** Texture cache */
    private var textures = HashMap<String, GLTexture>()

    /** Shader Property cache */
    private var shaderPropertyCache = HashMap<Class<*>, List<Field>>()

    /** JOGL Drawable */
    private var joglDrawable: GLDrawable? = null

    /** Flag set when a screenshot is requested */
    private var screenshotRequested = false

    /** Eyes of the stereo render targets */
    var eyes = (0..0)


    /** time since last resizing */
    private var lastResizeTimer = Timer()

    /** Window resizing timeout */
    private var WINDOW_RESIZE_TIMEOUT = 200L

    /** Flag to indicate whether framebuffers have to be recreated */
    private var mustRecreateFramebuffers = false

    /** GPU stats object */
    private var gpuStats: GPUStats? = null

    /** heartbeat timer */
    private var heartbeatTimer = Timer()

    var initialized = false
        private set

    private val pbos: IntArray = intArrayOf(0, 0)
    private var readIndex = 0
    private var updateIndex = 0

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

            window.width = lastWidth
            window.height = lastHeight

            drawable?.setSurfaceSize(window.width, window.height)
            mustRecreateFramebuffers = true
            pbos[0] = 0
            pbos[1] = 0

            embedIn?.let { panel ->
                panel.prefWidth = window.width.toDouble()
                panel.prefHeight = window.height.toDouble()
            }

            lastResize = -1L
        }
    }

    internal val resizeHandler = ResizeHandler()

    /**
     * Constructor for OpenGLRenderer, initialises geometry buffers
     * according to eye configuration. Also initialises different rendering passes.
     *
     */
    init {

        logger.info("Initializing OpenGL Renderer...")
        this.hub = hub
        this.settings = loadDefaultRendererSettings(hub.get(SceneryElement.Settings) as Settings)
        this.window.width = width
        this.window.height = height

        this.scene = scene
        this.applicationName = applicationName

        val hmd = hub.getWorkingHMDDisplay()
        if (settings.get("vr.Active") && hmd != null) {
            this.window.width = hmd.getRenderTargetSize().x().toInt() * 2
            this.window.height = hmd.getRenderTargetSize().y().toInt()
        }

        if (embedIn != null) {
            val profile = GLProfile.getMaxProgrammableCore(true)
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

                        panel.minWidth = 100.0
                        panel.minHeight = 100.0
                        panel.prefWidth = window.width.toDouble()
                        panel.prefHeight = window.height.toDouble()
                    }

                    window = SceneryWindow.JavaFXStage(embedIn!!)
                    window.width = width
                    window.height = height

                    resizeHandler.lastWidth = window.width
                    resizeHandler.lastHeight = window.height

                }
        } else {
            cglWindow = ClearGLWindow("",
                this.window.width,
                this.window.height, this).apply {

                cglWindow!!.start()

                if (embedIn != null) {

                } else {
                    window = SceneryWindow.ClearGLWindow(this)
                    cglWindow!!.isVisible = true
                }
            }
        }
    }

    override fun init(pDrawable: GLAutoDrawable) {
        this.gl = drawable?.gl?.gL4 ?: cglWindow!!.gl.gL4

        val width = this.window.width
        val height = this.window.height

        gl.swapInterval = 0

        val driverString = gl.glGetString(GL.GL_RENDERER)
        val driverVersion = gl.glGetString(GL.GL_VERSION)
        logger.info("OpenGLRenderer: $width x $height on $driverString, $driverVersion")

        if (driverVersion.toLowerCase().indexOf("nvidia") != -1 && System.getProperty("os.name").toLowerCase().indexOf("windows") != -1) {
            gpuStats = NvidiaGPUStats()
        }

        val numExtensionsBuffer = IntBuffer.allocate(1)
        gl.glGetIntegerv(GL4.GL_NUM_EXTENSIONS, numExtensionsBuffer)
        val extensions = (0..numExtensionsBuffer[0] - 1).map { gl.glGetStringi(GL4.GL_EXTENSIONS, it) }
        logger.debug("Available OpenGL extensions: ${extensions.joinToString(", ")}")

        settings.set("ssao.FilterRadius", GLVector(5.0f / width, 5.0f / height))

        geometryBuffer = ArrayList<GLFramebuffer>()
        hdrBuffer = ArrayList<GLFramebuffer>()
        combinationBuffer = ArrayList<GLFramebuffer>()

        recreateFramebuffers()

        logger.info(geometryBuffer.map { it.toString() }.joinToString("\n"))

        lightingPassProgram = GLProgram.buildProgram(gl, OpenGLRenderer::class.java,
            arrayOf("shaders/FullscreenQuad.vert", "shaders/DeferredLighting.frag"))

        hdrPassProgram = GLProgram.buildProgram(gl, OpenGLRenderer::class.java,
            arrayOf("shaders/FullscreenQuad.vert", "shaders/HDR.frag"))

        combinerProgram = GLProgram.buildProgram(gl, OpenGLRenderer::class.java,
            arrayOf("shaders/FullscreenQuad.vert", "shaders/Combiner.frag"))

        gl.glViewport(0, 0, this.window.width, this.window.height)
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)

        gl.glEnable(GL4.GL_TEXTURE_GATHER)

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

                    if (settings.get<Boolean>("OpenGLRenderer.PrintGPUStats")) {
                        logger.info(it.utilisationToString())
                        logger.info(it.memoryUtilisationToString())
                    }
                }
            }
        }, 0, 1000)

        initializeScene()
    }

    fun recreateFramebuffers() {

        // if vr.Active is set to true, we use two eyes for stereo render targets.
        if (settings.get("vr.Active")) {
            eyes = (0..1)
            settings.set("vr.IPD", -0.5f)
        }

        geometryBuffer.map { it.destroy(this.gl) }
        hdrBuffer.map { it.destroy(this.gl) }
        combinationBuffer.map { it.destroy(this.gl) }

        geometryBuffer.clear()
        hdrBuffer.clear()
        combinationBuffer.clear()

        eyes.forEach {
            // create 32bit position buffer, 16bit normal buffer, 8bit diffuse buffer and 24bit depth buffer
            val vrWidthDivisor = if (settings.get("vr.DoAnaglyph")) 1 else 2
            val actualWidth = if (settings.get("vr.Active")) this.window.width / vrWidthDivisor else this.window.width
            val actualHeight = if (settings.get("vr.Active")) this.window.height else this.window.height

            var gb = GLFramebuffer(this.gl, actualWidth, actualHeight)
            gb.addFloatRGBABuffer(this.gl, 32)
            gb.addFloatRGBABuffer(this.gl, 16)
            gb.addUnsignedByteRGBABuffer(this.gl, 8)
            gb.addDepthBuffer(this.gl, 32, 1)

            // 32bit depth buffers might not be supported, fall back to 24bit
            if (!gb.checkDrawBuffers(this.gl)) {
                gb = GLFramebuffer(this.gl, actualWidth, actualHeight)
                gb.addFloatRGBABuffer(this.gl, 32)
                gb.addFloatRGBABuffer(this.gl, 16)
                gb.addUnsignedByteRGBABuffer(this.gl, 8)
                gb.addDepthBuffer(this.gl, 24, 1)
            }

            geometryBuffer.add(gb)

            // HDR buffers
            val hdrB = GLFramebuffer(this.gl, actualWidth, actualHeight)
            hdrB.addFloatRGBABuffer(this.gl, 32)
            hdrB.addFloatRGBABuffer(this.gl, 32)
            hdrB.checkDrawBuffers(this.gl)

            hdrBuffer.add(hdrB)

            val cb = GLFramebuffer(this.gl, actualWidth, actualHeight)
            cb.addUnsignedByteRGBABuffer(this.gl, 8)
            cb.checkDrawBuffers(this.gl)

            combinationBuffer.add(cb)
        }
    }

    override fun display(pDrawable: GLAutoDrawable) {
        super.display(pDrawable)

        val fps = if(drawable != null) {
            drawable?.animator?.lastFPS ?: 1.0f
        } else {
            cglWindow?.lastFPS ?: 0.0f
        }

        window.setTitle("$applicationName [${this@OpenGLRenderer.javaClass.simpleName}] - $fps fps")

        this.joglDrawable = pDrawable

        if (mustRecreateFramebuffers) {
            recreateFramebuffers()

            gl.glClear(GL.GL_DEPTH_BUFFER_BIT or GL.GL_COLOR_BUFFER_BIT)
            gl.glViewport(0, 0, window.width, window.height)

            mustRecreateFramebuffers = false
        }

        this@OpenGLRenderer.render()
    }

    override fun setClearGLWindow(pClearGLWindow: ClearGLWindow) {
//        (window as SceneryWindow.ClearGLWindow).window = pClearGLWindow
        cglWindow = pClearGLWindow
    }

    override fun getClearGLWindow(): ClearGLDisplayable {
//        return (window as SceneryWindow.ClearGLWindow).window
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
        pDrawable.animator?.stop()

        this.shouldClose = true
    }

    /**
     * Returns the default [Settings] for [OpenGLRenderer]
     *
     * Providing some sane defaults that may of course be overridden after
     * construction of the renderer.
     *
     * @return Default [Settings] values
     */
    private fun loadDefaultRendererSettings(ds: Settings): Settings {
        val base = OpenGLRenderer::class.java.simpleName

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
        ds.set("hdr.Exposure", 10.0f)
        ds.set("hdr.Gamma", 2.2f)

        ds.set("sdf.MaxDistance", 10)

        ds.set("debug.DebugDeferredBuffers", false)
        ds.set("$base.PrintGPUStats", false)

        return ds
    }

    /**
     * Based on the [GLFramebuffer], devises a texture unit that can be used
     * for object textures.
     *
     * @param[type] texture type
     * @return Int of the texture unit to be used
     */
    fun GLFramebuffer.textureTypeToUnit(type: String): Int {
        return this.boundBufferNum + when (type) {
            "ambient" -> 0
            "diffuse" -> 1
            "specular" -> 2
            "normal" -> 3
            "alphamask" -> 4
            "displacement" -> 5
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
            GeometryType.TRIANGLE_STRIP -> GL.GL_TRIANGLE_STRIP
            GeometryType.POLYGON -> GL.GL_TRIANGLES
            GeometryType.TRIANGLES -> GL.GL_TRIANGLES
            GeometryType.TRIANGLE_FAN -> GL.GL_TRIANGLE_FAN
            GeometryType.POINTS -> GL.GL_POINTS
            GeometryType.LINE -> GL.GL_LINE_STRIP
            GeometryType.LINES_ADJACENCY -> GL4.GL_LINES_ADJACENCY
            GeometryType.LINE_STRIP_ADJACENCY -> GL4.GL_LINE_STRIP_ADJACENCY
        }
    }


    /**
     * Toggles deferred shading buffer debug view. Used for e.g.
     * [graphics.scenery.controls.behaviours.ToggleCommand]
     */
    @Suppress("UNUSED")
    fun toggleDebug() {
        if (!settings.get<Boolean>("debug.DebugDeferredBuffers")) {
            settings.set("debug.DebugDeferredBuffers", true)
        } else {
            settings.set("debug.DebugDeferredBuffers", false)
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
     * Toggles VR on and off
     */
    @Suppress("UNUSED")
    fun toggleVR() {
        if (!settings.get<Boolean>("vr.Active")) {
            settings.set("vr.Active", true)
        } else {
            settings.set("vr.Active", false)
        }

        mustRecreateFramebuffers = true
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
    override fun initializeScene() {
        scene.discover(scene, { it is HasGeometry })
            .forEach { it ->
                it.metadata.put("OpenGLRenderer", OpenGLObjectState())
                initializeNode(it)
            }

        scene.initialized = true
        logger.info("Initialized ${textures.size} textures")
    }

    /**
     * Sets the [Material] GLSL uniforms for a given [Node].
     *
     * If a Node does not have a Material set, the position of the Node will be used
     * for all ambient, diffuse, and specular color. If textures are attached to the Material,
     * the corresponding uniforms are set such that the GLProgram can sample from them.
     *
     * @param[n] The [Node] to set the uniforms for.
     * @param[gl] A [GL4] context.
     * @param[s] The [OpenGLObjectState] of the [Node].
     * @param[program] The [GLProgram] GLSL shader to render the [Node] with.
     */
    private fun setMaterialUniformsForNode(n: Node, gl: GL4, s: OpenGLObjectState, program: GLProgram) {
        program.use(gl)
        program.getUniform("Material.Shininess").setFloat(0.001f)

        program.getUniform("Material.Ka").setFloatVector(n.material.ambient)
        program.getUniform("Material.Kd").setFloatVector(n.material.diffuse)
        program.getUniform("Material.Ks").setFloatVector(n.material.specular)

        if (n.material.doubleSided) {
            gl.glDisable(GL.GL_CULL_FACE)
        }

        if (n.material.transparent) {
            gl.glEnable(GL.GL_BLEND)
            gl.glBlendFunc(GL.GL_SRC_COLOR, GL.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            gl.glDisable(GL.GL_BLEND)
        }

        s.textures.forEach { type, glTexture ->
            val samplerIndex = geometryBuffer.first().textureTypeToUnit(type)

            @Suppress("SENSELESS_COMPARISON")
            if (glTexture != null) {
                gl.glActiveTexture(GL.GL_TEXTURE0 + samplerIndex)
                gl.glBindTexture(GL.GL_TEXTURE_2D, glTexture.id)
                program.getUniform(textureTypeToArrayName(type)).setInt(samplerIndex)
            }
        }

        if (s.textures.size > 0) {
            program.getUniform("materialType").setInt(1)

            var material = 0

            if (s.textures.containsKey("diffuse")) {
                material = material or MATERIAL_HAS_DIFFUSE
            }

            if (s.textures.containsKey("ambient")) {
                material = material or MATERIAL_HAS_AMBIENT
            }

            if (s.textures.containsKey("specular")) {
                material = material or MATERIAL_HAS_SPECULAR
            }

            if (s.textures.containsKey("normal")) {
                material = material or MATERIAL_HAS_NORMAL
            }

            if (s.textures.containsKey("alphamask")) {
                material = material or MATERIAL_HAS_ALPHAMASK
            }

            program.getUniform("materialType").setInt(material)
        }
    }

    /**
     * Updates a [FontBoard], in case it's fontFamily or contents have changed.
     *
     * If a SDFFontAtlas has already been created for the given fontFamily, this will be used, and
     * cached as well. Else, a new one will be created.
     *
     * @param[board] The [FontBoard] instance.
     */
    private fun updateFontBoard(board: FontBoard) {
        val atlas = fontAtlas.getOrPut(board.fontFamily, { SDFFontAtlas(this.hub!!, board.fontFamily, maxDistance = settings.get<Int>("sdf.MaxDistance")) })
        val m = atlas.createMeshForString(board.text)

        board.vertices = m.vertices
        board.normals = m.normals
        board.indices = m.indices
        board.texcoords = m.texcoords

        board.metadata.remove("OpenGLRenderer")
        board.metadata.put("OpenGLRenderer", OpenGLObjectState())
        initializeNode(board)

        val s = getOpenGLObjectStateFromNode(board)
        val texture = textures.getOrPut("sdf-${board.fontFamily}", {
            val t = GLTexture(gl, GLTypeEnum.Float, 1,
                atlas.atlasWidth,
                atlas.atlasHeight,
                1,
                true,
                1)

            t.setClamp(false, false)
            t.copyFrom(atlas.getAtlas(),
                0,
                true)
            t
        })
        s.textures.put("diffuse", texture)
    }

    /**
     * Update a [Node]'s geometry, if needed and run it's preDraw() routine.
     *
     * @param[n] The Node to update and preDraw()
     */
    private fun preDrawAndUpdateGeometryForNode(n: Node) {
        if (n is HasGeometry) {
            if (n.dirty) {
                if(n.lock.tryLock()) {
                    if (n is FontBoard) {
                        updateFontBoard(n)
                    }

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
    override fun render() {
        if(shouldClose) {
            return
        }

        val vrActive = settings.get<Boolean>("vr.Active")

        if (scene.children.count() == 0 ||
            lightingPassProgram == null ||
            hdrPassProgram == null ||
            combinerProgram == null
            ) {
            logger.info("Waiting for initialization")
            Thread.sleep(200)
            return
        }

        val renderOrderList = ArrayList<Node>()
        // find observer, or return immediately
        val cam: Camera = scene.findObserver() ?: return

        val hmd: Display? = if (hub!!.has(SceneryElement.HMDInput)
            && (hub!!.get(SceneryElement.HMDInput) as Display).initializedAndWorking()) {
            hub!!.get(SceneryElement.HMDInput) as Display
        } else {
            null
        }

        val tracker: TrackerInput? = if (hub!!.has(SceneryElement.HMDInput)
            && (hub!!.get(SceneryElement.HMDInput) as TrackerInput).initializedAndWorking()) {
            hub!!.get(SceneryElement.HMDInput) as TrackerInput
        } else {
            null
        }

        scene.discover(scene, { n -> n is Renderable && n is HasGeometry && n.visible }).forEach {
            renderOrderList.add(it)
        }

        cam.view = cam.getTransformation()

        val instanceGroups = renderOrderList.groupBy { it.instanceOf }


        gl.glDisable(GL.GL_SCISSOR_TEST)
        gl.glDisable(GL4.GL_BLEND)
        gl.glEnable(GL.GL_DEPTH_TEST)
        gl.glViewport(0, 0, geometryBuffer.first().width, geometryBuffer.first().height)

        eyes.forEachIndexed { i, _ ->
            geometryBuffer[i].setDrawBuffers(gl)
            gl.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)
        }

        gl.glEnable(GL.GL_CULL_FACE)
        gl.glFrontFace(GL.GL_CCW)
        gl.glCullFace(GL.GL_BACK)
        gl.glDepthFunc(GL.GL_LEQUAL)

        val headToEye = eyes.map { i ->
            if (hmd == null) {
                GLMatrix.getTranslation(settings.get<Float>("vr.IPD") * -1.0f * Math.pow(-1.0, 1.0 * i).toFloat(), 0.0f, 0.0f).transpose()
            } else {
                hmd.getHeadToEyeTransform(i).clone()
            }
        }

        val pose = tracker?.getPose() ?: GLMatrix.getIdentity()
        cam.view = cam.getTransformation()

        val projection = eyes.map { i ->
            if (vrActive) {
                hmd?.getEyeProjection(i, cam.nearPlaneDistance, cam.farPlaneDistance) ?: cam.projection
            } else {
                cam.projection
            }
        }

        instanceGroups[null]?.forEach nonInstancedDrawing@ { n ->
            if (n in instanceGroups.keys) {
                return@nonInstancedDrawing
            }

            if (!n.metadata.containsKey("OpenGLRenderer")) {
                n.metadata.put("OpenGLRenderer", OpenGLObjectState())
                initializeNode(n)
            }

            val s = getOpenGLObjectStateFromNode(n)
            n.updateWorld(true, false)

            if (n.material.needsTextureReload) {
                n.material.needsTextureReload = !loadTexturesForNode(n, s)
            }

            if (n is Skybox) {
                gl.glCullFace(GL.GL_FRONT)
                gl.glDepthFunc(GL.GL_LEQUAL)
            }

            eyes.forEach { eye ->
                n.projection.copyFrom(projection[eye])
                n.view.copyFrom(cam.view)

                n.modelView.copyFrom(headToEye[eye])
                n.modelView.mult(pose)
                n.modelView.mult(cam.view)
                n.modelView.mult(n.world)

                n.mvp.copyFrom(projection[eye])
                n.mvp.mult(n.modelView)

                s.program?.let { program ->
                    program.use(gl)
                    program.getUniform("ModelMatrix")!!.setFloatMatrix(n.world, false)
                    program.getUniform("ModelViewMatrix")!!.setFloatMatrix(n.modelView, false)
                    program.getUniform("ProjectionMatrix")!!.setFloatMatrix(projection[eye], false)
                    program.getUniform("MVP")!!.setFloatMatrix(n.mvp, false)
                    program.getUniform("isBillboard")!!.setInt(n.isBillboard.toInt())

                    setMaterialUniformsForNode(n, gl, s, program)
                    setShaderPropertiesForNode(n, program)
                }

                preDrawAndUpdateGeometryForNode(n)
                geometryBuffer[eye].setDrawBuffers(gl)
                drawNode(n)
            }
        }

        instanceGroups.keys.filterNotNull().forEach instancedDrawing@ { n ->
            var start = System.nanoTime()

            if (!n.metadata.containsKey("OpenGLRenderer")) {
                n.metadata.put("OpenGLRenderer", OpenGLObjectState())
                initializeNode(n)
            }

            val s = getOpenGLObjectStateFromNode(n)
            val instances = instanceGroups[n]!!

            logger.trace("${n.name} has additional instance buffers: ${s.additionalBufferIds.keys}")
            logger.trace("${n.name} instancing: Instancing group size is ${instances.size}")

            instances.forEach { node ->
                if (!node.metadata.containsKey("OpenGLRenderer")) {
                    node.metadata.put("OpenGLRenderer", OpenGLObjectState())
                    initializeNode(node)
                }

                node.updateWorld(true, false)
            }

            val matrixSize = 4 * 4
            val models = ArrayList<Float>(matrixSize * instances.size)
            val modelviews = ArrayList<Float>(matrixSize * instances.size)
            val modelviewprojs = ArrayList<Float>(matrixSize * instances.size)

            eyes.forEach {
                eye ->

                models.clear()
                modelviews.clear()
                modelviewprojs.clear()

                instances.forEach { node ->
                    node.modelView.copyFrom(headToEye[eye])
                    node.modelView.mult(pose)
                    node.modelView.mult(cam.view)
                    node.modelView.mult(node.world)

                    node.mvp.copyFrom(projection[eye])
                    node.mvp.mult(node.modelView)

                    node.projection.copyFrom(projection[eye])
                    node.view.copyFrom(cam.view)

                    models.addAll(node.world.floatArray.asSequence())
                    modelviews.addAll(node.modelView.floatArray.asSequence())
                    modelviewprojs.addAll(node.mvp.floatArray.asSequence())
                }

                logger.trace("${n.name} instancing: Collected ${modelviewprojs.size / matrixSize} MVPs in ${(System.nanoTime() - start) / 10e6}ms")

                // bind instance buffers
                start = System.nanoTime()
                val matrixSizeBytes: Long = 1L * Buffers.SIZEOF_FLOAT * matrixSize * instances.size

                gl.gL4.glBindVertexArray(s.mVertexArrayObject[0])

                gl.gL4.glBindBuffer(GL.GL_ARRAY_BUFFER, s.additionalBufferIds["Model"]!!)
                gl.gL4.glBufferData(GL.GL_ARRAY_BUFFER, matrixSizeBytes,
                    FloatBuffer.wrap(models.toFloatArray()), GL.GL_DYNAMIC_DRAW)

                gl.gL4.glBindBuffer(GL.GL_ARRAY_BUFFER, s.additionalBufferIds["ModelView"]!!)
                gl.gL4.glBufferData(GL.GL_ARRAY_BUFFER, matrixSizeBytes,
                    FloatBuffer.wrap(modelviews.toFloatArray()), GL.GL_DYNAMIC_DRAW)

                gl.gL4.glBindBuffer(GL.GL_ARRAY_BUFFER, s.additionalBufferIds["MVP"]!!)
                gl.gL4.glBufferData(GL.GL_ARRAY_BUFFER, matrixSizeBytes,
                    FloatBuffer.wrap(modelviewprojs.toFloatArray()), GL.GL_DYNAMIC_DRAW)

                logger.trace("${n.name} instancing: Updated matrix buffers in ${(System.nanoTime() - start) / 10e6}ms")

                s.program?.let {
                    setMaterialUniformsForNode(n, gl, s, it)
                }

                preDrawAndUpdateGeometryForNode(n)

                geometryBuffer[eye].setDrawBuffers(gl)
                drawNodeInstanced(n, count = instances.size)
            }
        }

        val lights = scene.discover(scene, { it is PointLight })

        eyes.forEachIndexed { i, _ ->
            lightingPassProgram!!.bind()

            lightingPassProgram!!.getUniform("numLights").setInt(lights.size)
            lightingPassProgram!!.getUniform("ProjectionMatrix").setFloatMatrix(cam.projection.clone(), false)
            lightingPassProgram!!.getUniform("InverseProjectionMatrix").setFloatMatrix(cam.projection.clone().invert(), false)

            for (light in 0..lights.size - 1) {
                lightingPassProgram!!.getUniform("lights[$light].Position").setFloatVector(lights[light].position)
                lightingPassProgram!!.getUniform("lights[$light].Color").setFloatVector((lights[light] as PointLight).emissionColor)
                lightingPassProgram!!.getUniform("lights[$light].Intensity").setFloat((lights[light] as PointLight).intensity)
                lightingPassProgram!!.getUniform("lights[$light].Linear").setFloat((lights[light] as PointLight).linear)
                lightingPassProgram!!.getUniform("lights[$light].Quadratic").setFloat((lights[light] as PointLight).quadratic)
            }

            lightingPassProgram!!.getUniform("gPosition").setInt(0)
            lightingPassProgram!!.getUniform("gNormal").setInt(1)
            lightingPassProgram!!.getUniform("gAlbedoSpec").setInt(2)
            lightingPassProgram!!.getUniform("gDepth").setInt(3)

            lightingPassProgram!!.getUniform("debugDeferredBuffers").setInt(settings.get<Boolean>("debug.DebugDeferredBuffers").toInt())
            lightingPassProgram!!.getUniform("ssao_filterRadius").setFloatVector(settings.get<GLVector>("ssao.FilterRadius"))
            lightingPassProgram!!.getUniform("ssao_distanceThreshold").setFloat(settings.get<Float>("ssao.DistanceThreshold"))
            lightingPassProgram!!.getUniform("doSSAO").setInt(settings.get<Boolean>("ssao.Active").toInt())

            geometryBuffer[i].bindTexturesToUnitsWithOffset(gl, 0)
            hdrBuffer[i].setDrawBuffers(gl)


            gl.glViewport(0, 0, hdrBuffer[i].width, hdrBuffer[i].height)

            gl.glDisable(GL.GL_CULL_FACE)
            gl.glDisable(GL.GL_BLEND)
            gl.glDisable(GL.GL_DEPTH_TEST)
            gl.glPointSize(1.5f)
            gl.glEnable(GL4.GL_PROGRAM_POINT_SIZE)

            if (!settings.get<Boolean>("hdr.Active")) {
                if (settings.get<Boolean>("vr.DoAnaglyph")) {
                    if (i == 0) {
                        gl.glColorMask(true, false, false, false)
                    } else {
                        gl.glColorMask(false, true, true, false)
                    }
                }
                combinationBuffer[i].setDrawBuffers(gl)
                gl.glClear(GL4.GL_COLOR_BUFFER_BIT)
                gl.glViewport(0, 0, combinationBuffer[i].width, combinationBuffer[i].height)

                renderFullscreenQuad(lightingPassProgram!!)
            } else {
                gl.glClear(GL4.GL_COLOR_BUFFER_BIT or GL4.GL_DEPTH_BUFFER_BIT)
                // render to the active, eye-dependent HDR buffer
                renderFullscreenQuad(lightingPassProgram!!)

                hdrBuffer[i].bindTexturesToUnitsWithOffset(gl, 0)
                combinationBuffer[i].setDrawBuffers(gl)
                gl.glViewport(0, 0, combinationBuffer[i].width, combinationBuffer[i].height)
                gl.glClear(GL4.GL_COLOR_BUFFER_BIT or GL4.GL_DEPTH_BUFFER_BIT)

                if (settings.get<Boolean>("vr.DoAnaglyph")) {
                    if (i == 0) {
                        gl.glColorMask(true, false, false, false)
                    } else {
                        gl.glColorMask(false, true, true, false)
                    }
                }

                hdrPassProgram!!.getUniform("Gamma").setFloat(settings.get<Float>("hdr.Gamma"))
                hdrPassProgram!!.getUniform("Exposure").setFloat(settings.get<Float>("hdr.Exposure"))
                renderFullscreenQuad(hdrPassProgram!!)
            }
        }

        combinationBuffer.first().revertToDefaultFramebuffer(gl)
        if (settings.get<Boolean>("vr.DoAnaglyph")) {
            gl.glColorMask(true, true, true, true)
        }
        gl.glClear(GL4.GL_COLOR_BUFFER_BIT or GL4.GL_DEPTH_BUFFER_BIT)
        gl.glDisable(GL4.GL_SCISSOR_TEST)
        gl.glViewport(0, 0, window.width, window.height)
        gl.glScissor(0, 0, window.width, window.height)

        if (vrActive) {
            if (settings.get<Boolean>("vr.DoAnaglyph")) {
                combinerProgram!!.getUniform("vrActive").setInt(0)
                combinerProgram!!.getUniform("anaglyphActive").setInt(1)
            } else {
                combinerProgram!!.getUniform("vrActive").setInt(1)
                combinerProgram!!.getUniform("anaglyphActive").setInt(0)
            }
            combinationBuffer[0].bindTexturesToUnitsWithOffset(gl, 0)
            combinationBuffer[1].bindTexturesToUnitsWithOffset(gl, 4)
            combinerProgram!!.getUniform("leftEye").setInt(0)
            combinerProgram!!.getUniform("rightEye").setInt(4)
            renderFullscreenQuad(combinerProgram!!)

            if (hmd != null && hmd.hasCompositor()) {
                logger.trace("Submitting to compositor...")
                hmd.submitToCompositor(combinationBuffer[0].getTextureIds(gl)[0], combinationBuffer[1].getTextureIds(gl)[0])
            }
        } else {
            combinationBuffer.first().bindTexturesToUnitsWithOffset(gl, 0)
            combinerProgram!!.getUniform("leftEye").setInt(0)
            combinerProgram!!.getUniform("rightEye").setInt(0)
            combinerProgram!!.getUniform("vrActive").setInt(0)
            renderFullscreenQuad(combinerProgram!!)
        }

        embedIn?.let { embedPanel ->
            if(shouldClose) {
                return
            }

            readIndex = (readIndex + 1) % 2
            updateIndex = (readIndex + 1) % 2

            if(pbos[0] == 0 || pbos[1] == 0) {
                gl.glGenBuffers(2, pbos, 0)

                gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, pbos[0])
                gl.glBufferData(GL4.GL_PIXEL_PACK_BUFFER, embedPanel.width.toInt() * embedPanel.height.toInt() * 4L, null, GL4.GL_STREAM_READ)

                gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, pbos[1])
                gl.glBufferData(GL4.GL_PIXEL_PACK_BUFFER, embedPanel.width.toInt() * embedPanel.height.toInt() * 4L, null, GL4.GL_STREAM_READ)

                gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, 0)
            }

            gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, pbos[readIndex])

            gl.glReadBuffer(GL4.GL_FRONT)
            gl.glReadPixels(0, 0, window.width, window.height, GL4.GL_BGRA, GL4.GL_UNSIGNED_BYTE, 0)

            gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, pbos[updateIndex])

            val buffer = gl.glMapBuffer(GL4.GL_PIXEL_PACK_BUFFER, GL4.GL_READ_ONLY)
            if(buffer != null) {
                Platform.runLater {
                    if (!mustRecreateFramebuffers) embedPanel.update(buffer)
                }
                gl.glUnmapBuffer(GL4.GL_PIXEL_PACK_BUFFER)
            }

            gl.glBindBuffer(GL4.GL_PIXEL_PACK_BUFFER, 0)

            resizeHandler.queryResize()
        }

        if (screenshotRequested && joglDrawable != null) {
            try {
                val readBufferUtil = AWTGLReadBufferUtil(joglDrawable!!.glProfile, false)
                val image = readBufferUtil.readPixelsToBufferedImage(gl, true)
                val file = File(System.getProperty("user.home"), "Desktop" + File.separator + "$applicationName - ${SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(Date())}.png")

                ImageIO.write(image, "png", file)
                logger.info("Screenshot saved to ${file.absolutePath}")
            } catch (e: Exception) {
                System.err.println("Unable to take screenshot: ")
                e.printStackTrace()
            }

            screenshotRequested = false
        }
    }

    /**
     * Renders a fullscreen quad, depending on a program [GLProgram], which generates
     * a screen-filling quad from a single point.
     *
     * Deprecated because of steady resubmission and recreation of VAOs and VBOs, use
     * [renderFullscreenQuad].
     *
     * @param[quadGenerator] The quad-generating [GLProgram]
     */
    @Deprecated("Use renderFullscreenQuad, it has better performance")
    fun renderFullscreenQuadOld(quadGenerator: GLProgram) {
        val quadId: IntBuffer = IntBuffer.allocate(1)

        quadGenerator.gl.gL4.glGenVertexArrays(1, quadId)
        quadGenerator.gl.gL4.glBindVertexArray(quadId.get(0))

        // fake call to draw one point, geometry is generated in shader pipeline
        quadGenerator.gl.gL4.glDrawArrays(GL.GL_POINTS, 0, 1)
        quadGenerator.gl.gL4.glBindTexture(GL.GL_TEXTURE_2D, 0)

        quadGenerator.gl.gL4.glBindVertexArray(0)
        quadGenerator.gl.gL4.glDeleteVertexArrays(1, quadId)
    }

    /**
     * Renders a fullscreen quad, using from an on-the-fly generated
     * Node that is saved in [nodeStore], with the [GLProgram]'s ID.
     * This function is a few FPS faster than [renderFullscreenQuadOld].
     *
     * @param[program] The [GLProgram] to draw into the fullscreen quad.
     */
    fun renderFullscreenQuad(program: GLProgram) {
        val quad: Node
        val quadName = "fullscreenQuad-${program.id}"

        if (!nodeStore.containsKey(quadName)) {
            quad = Plane(GLVector(1.0f, 1.0f, 0.0f))
            val material = OpenGLMaterial()

            material.program = program

            quad.material = material
            quad.metadata.put("OpenGLRenderer", OpenGLObjectState())
            initializeNode(quad)

            nodeStore.put(quadName, quad)
        } else {
            quad = nodeStore[quadName]!!
        }

        (quad.material as OpenGLMaterial).program = program

        drawNode(quad)
        program.gl.gL4.glBindTexture(GL.GL_TEXTURE_2D, 0)
    }

    /**
     * Initializes a given [Node].
     *
     * This function initializes a Node, equipping its metadata with an [OpenGLObjectState],
     * generating VAOs and VBOs. If the Node has a [Material] assigned, a [GLProgram] fitting
     * this Material will be used. Else, a default GLProgram will be used.
     *
     * For the assigned Material case, the GLProgram is derived either from the class name of the
     * Node (if useClassDerivedShader is set), or from a set [ShaderPreference] which may define
     * the whole shader pipeline for the Node.
     *
     * If the [Node] implements [HasGeometry], it's geometry is also initialized by this function.
     *
     * @param[node]: The [Node] to initialise.
     * @return True if the initialisation went alright, False if it failed.
     */
    fun initializeNode(node: Node): Boolean {
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

            if (!s.additionalBufferIds.containsKey("Model") || !s.additionalBufferIds.containsKey("ModelView") || !s.additionalBufferIds.containsKey("MVP")) {
                logger.trace("${node.name} triggered instance buffer creation")
                createInstanceBuffer(node.instanceOf!!)
                logger.trace("---")
            }
            return true
        }

        if (s.initialized) {
            return true
        }

        // generate VAO for attachment of VBO and indices
        gl.gL3.glGenVertexArrays(1, s.mVertexArrayObject, 0)

        // generate three VBOs for coords, normals, texcoords
        gl.glGenBuffers(3, s.mVertexBuffers, 0)
        gl.glGenBuffers(1, s.mIndexBuffer, 0)

        if (node.material !is OpenGLMaterial || (node.material as OpenGLMaterial).program == null) {
            if (node.useClassDerivedShader) {
                val javaClass = node.javaClass.simpleName
                val className = javaClass.substring(javaClass.indexOf(".") + 1)

                val shaders = arrayOf(".vert", ".geom", ".tese", ".tesc", ".frag", ".comp")
                    .map { "shaders/$className$it" }
                    .filter {
                        OpenGLRenderer::class.java.getResource(it) != null
                    }

                s.program = GLProgram.buildProgram(gl, OpenGLRenderer::class.java,
                    shaders.toTypedArray())
            } else if (node.metadata.filter { it.value is ShaderPreference }.isNotEmpty()) {
                val prefs = node.metadata["ShaderPreference"] as ShaderPreference

                if (prefs.parameters.size > 0) {
                    s.program = GLProgram.buildProgram(gl, node.javaClass,
                        prefs.shaders.toTypedArray(), prefs.parameters)
                } else {
                    try {
                        s.program = GLProgram.buildProgram(gl, node.javaClass,
                            prefs.shaders.toTypedArray())
                    } catch(e: NullPointerException) {
                        s.program = GLProgram.buildProgram(gl, this.javaClass,
                            prefs.shaders.map { "shaders/" + it }.toTypedArray())
                    }

                }
            } else {
                s.program = GLProgram.buildProgram(gl, OpenGLRenderer::class.java,
                    arrayOf("shaders/DefaultDeferred.vert", "shaders/DefaultDeferred.frag"))
            }
        } else {
            s.program = (node.material as OpenGLMaterial).program
        }

        if (node is HasGeometry) {
            node.lock.tryLock(100, TimeUnit.MILLISECONDS)
            if(node.lock.tryLock()) {
                setVerticesAndCreateBufferForNode(node)
                setNormalsAndCreateBufferForNode(node)

                if (node.texcoords.limit() > 0) {
                    setTextureCoordsAndCreateBufferForNode(node)
                }

                if (node.indices.limit() > 0) {
                    setIndicesAndCreateBufferForNode(node)
                }

                node.lock.unlock()
            }
        }

        loadTexturesForNode(node, s)
        s.initialized = true
        return true
    }

    /**
     * Parallel forEach implementation for HashMaps.
     *
     * @param[maxThreads] Maximum number of parallel threads
     * @param[action] Lambda containing the action to be executed for each key, value pair.
     */
    @Suppress("unused")
    fun <K, V> HashMap<K, V>.forEachParallel(maxThreads: Int = 5, action: ((K, V) -> Unit)) {
        val iterator = this.asSequence().iterator()
        var threadCount = 0

        while (iterator.hasNext()) {
            val current = iterator.next()

            thread {
                threadCount++
                while (threadCount > maxThreads) {
                    Thread.sleep(50)
                }

                action.invoke(current.key, current.value)
                threadCount--
            }
        }
    }

    /**
     * Loads textures for a [Node]. The textures either come from a [Material.transferTextures] buffer,
     * or from a file. This is indicated by stating fromBuffer:bufferName in the textures hash map.
     *
     * @param[node] The [Node] to load textures for.
     * @param[s] The [Node]'s [OpenGLObjectState]
     */
    private fun loadTexturesForNode(node: Node, s: OpenGLObjectState): Boolean {
        if (node.lock.tryLock()) {
            node.material.textures.forEach {
                type, texture ->
                if (!textures.containsKey(texture) || node.material.needsTextureReload) {
                    logger.trace("Loading texture $texture for ${node.name}")
                    val glTexture = if (texture.startsWith("fromBuffer:")) {
                        val gt = node.material.transferTextures[texture.substringAfter("fromBuffer:")]

                        val t = GLTexture(gl, gt!!.type, gt.channels,
                            gt.dimensions.x().toInt(),
                            gt.dimensions.y().toInt(),
                            1,
                            true,
                            1)

                        t.setClamp(!gt.repeatS, !gt.repeatT)
                        t.copyFrom(gt.contents)
                        t
                    } else {
                        GLTexture.loadFromFile(gl, texture, true, 1)
                    }

                    s.textures.put(type, glTexture)
                    textures.put(texture, glTexture)
                } else {
                    s.textures.put(type, textures[texture]!!)
                }
            }

            s.initialized = true
            node.lock.unlock()
            return true
        } else {
            return false
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

                mustRecreateFramebuffers = true
            }
        }, WINDOW_RESIZE_TIMEOUT)
    }

    /**
     * Creates an instance buffer for a [Node]'s model, view and mvp matrices.
     *
     * @param[node] The [Node] to create the instance buffer for.
     */
    private fun createInstanceBuffer(node: Node) {
        val s = getOpenGLObjectStateFromNode(node)

        val matrixSize = 4 * 4
        val vectorSize = 4
        val locationBase = 3
        val matrices = arrayOf("Model", "ModelView", "MVP")
        val i = IntArray(matrices.size)

        gl.gL4.glBindVertexArray(s.mVertexArrayObject[0])
        gl.gL4.glGenBuffers(matrices.size, i, 0)

        i.forEachIndexed { locationOffset, bufferId ->
            s.additionalBufferIds.put(matrices[locationOffset], bufferId)

            gl.gL4.glBindBuffer(GL.GL_ARRAY_BUFFER, bufferId)

            for (offset in 0..3) {
                val l = locationBase + locationOffset * vectorSize + offset

                val pointerOffsetBytes: Long = 1L * Buffers.SIZEOF_FLOAT * offset * vectorSize
                val matrixSizeBytes = matrixSize * Buffers.SIZEOF_FLOAT

                gl.gL4.glEnableVertexAttribArray(l)
                gl.gL4.glVertexAttribPointer(l, vectorSize, GL.GL_FLOAT, false,
                    matrixSizeBytes, pointerOffsetBytes)
                gl.gL4.glVertexAttribDivisor(l, 1)
            }
        }

        gl.gL4.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
        gl.gL4.glBindVertexArray(0)
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

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, s.mVertexBuffers[0])

        gl.gL3.glEnableVertexAttribArray(0)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
            (pVertexBuffer.remaining() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
            pVertexBuffer,
            if (s.isDynamic)
                GL.GL_DYNAMIC_DRAW
            else
                GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(0,
            node.vertexSize,
            GL.GL_FLOAT,
            false,
            0,
            0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
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

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, s.mVertexBuffers[0])

        gl.gL3.glEnableVertexAttribArray(0)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
            (pVertexBuffer.remaining() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
            pVertexBuffer,
            GL.GL_DYNAMIC_DRAW)

        gl.gL3.glVertexAttribPointer(0,
            node.vertexSize,
            GL.GL_FLOAT,
            false,
            0,
            0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Creates VAOs and VBO for a given [Node]'s normals.
     *
     * @param[node] The [Node] to create the normals VBO for.
     */
    fun setNormalsAndCreateBufferForNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node)
        val pNormalBuffer: FloatBuffer = (node as HasGeometry).normals

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, s.mVertexBuffers[1])

        if (pNormalBuffer.limit() > 0) {
            gl.gL3.glEnableVertexAttribArray(1)
            gl.glBufferData(GL.GL_ARRAY_BUFFER,
                (pNormalBuffer.remaining() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pNormalBuffer,
                if (s.isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

            gl.gL3.glVertexAttribPointer(1,
                node.vertexSize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        }
        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Updates a given [Node]'s normals.
     *
     * @param[node] The [Node] whose normals need updating.
     */
    fun updateNormals(node: Node) {
        val s = getOpenGLObjectStateFromNode(node)
        val pNormalBuffer: FloatBuffer = (node as HasGeometry).normals

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, s.mVertexBuffers[1])

        gl.gL3.glEnableVertexAttribArray(1)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
            (pNormalBuffer.remaining() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
            pNormalBuffer,
            if (s.isDynamic)
                GL.GL_DYNAMIC_DRAW
            else
                GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(1,
            node.vertexSize,
            GL.GL_FLOAT,
            false,
            0,
            0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Creates VAOs and VBO for a given [Node]'s texcoords.
     *
     * @param[node] The [Node] to create the texcoord VBO for.
     */
    fun setTextureCoordsAndCreateBufferForNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node)
        val pTextureCoordsBuffer: FloatBuffer = (node as HasGeometry).texcoords

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, s.mVertexBuffers[2])

        gl.gL3.glEnableVertexAttribArray(2)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
            (pTextureCoordsBuffer.remaining() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
            pTextureCoordsBuffer,
            if (s.isDynamic)
                GL.GL_DYNAMIC_DRAW
            else
                GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(2,
            node.texcoordSize,
            GL.GL_FLOAT,
            false,
            0,
            0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Updates a given [Node]'s texcoords.
     *
     * @param[node] The [Node] whose texcoords need updating.
     */
    fun updateTextureCoords(node: Node) {
        val s = getOpenGLObjectStateFromNode(node)
        val pTextureCoordsBuffer: FloatBuffer = (node as HasGeometry).texcoords

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.gL3.glBindBuffer(GL.GL_ARRAY_BUFFER,
            s.mVertexBuffers[2])

        gl.gL3.glEnableVertexAttribArray(2)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
            (pTextureCoordsBuffer.remaining() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
            pTextureCoordsBuffer,
            if(s.isDynamic)
                GL.GL_DYNAMIC_DRAW
            else
                GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(2,
            node.texcoordSize,
            GL.GL_FLOAT,
            false,
            0,
            0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
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

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, s.mIndexBuffer[0])

        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER,
            (pIndexBuffer.remaining() * (Integer.SIZE / java.lang.Byte.SIZE)).toLong(),
            pIndexBuffer,
            if (s.isDynamic)
                GL.GL_DYNAMIC_DRAW
            else
                GL.GL_STATIC_DRAW)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0)
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

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, s.mIndexBuffer[0])

        gl.glBufferSubData(GL.GL_ELEMENT_ARRAY_BUFFER,
            0,
            (pIndexBuffer.remaining() * (Integer.SIZE / java.lang.Byte.SIZE)).toLong(),
            pIndexBuffer)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    /**
     * Draws a given [Node], either in element or in index draw mode.
     *
     * @param[node] The node to be drawn.
     * @param[offset] offset in the array or index buffer.
     */
    fun drawNode(node: Node, offset: Int = 0) {
        val s = getOpenGLObjectStateFromNode(node)

        if(s.mStoredIndexCount == 0 && s.mStoredPrimitiveCount == 0) {
            return
        }

        s.program?.use(gl)

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])

        if (s.mStoredIndexCount > 0) {
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER,
                s.mIndexBuffer[0])
            gl.glDrawElements((node as HasGeometry).geometryType.toOpenGLType(),
                s.mStoredIndexCount,
                GL.GL_UNSIGNED_INT,
                offset.toLong())

            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0)
        } else {
            gl.glDrawArrays((node as HasGeometry).geometryType.toOpenGLType(), offset, s.mStoredPrimitiveCount)
        }

        gl.gL3.glUseProgram(0)
        gl.gL4.glBindVertexArray(0)
    }

    /**
     * Draws a given instanced [Node] either in element or in index draw mode.
     *
     * @param[node] The node to be drawn.
     * @param[count] The number of instances to be drawn.
     * @param[offset] offset in the array or index buffer.
     */
    fun drawNodeInstanced(node: Node, count: Int, offset: Long = 0) {
        val s = getOpenGLObjectStateFromNode(node)

        s.program?.use(gl)

        gl.gL4.glBindVertexArray(s.mVertexArrayObject[0])

        if (s.mStoredIndexCount > 0) {
            gl.gL4.glDrawElementsInstanced(
                (node as HasGeometry).geometryType.toOpenGLType(),
                s.mStoredIndexCount,
                GL.GL_UNSIGNED_INT,
                offset,
                count)
        } else {
            gl.gL4.glDrawArraysInstanced(
                (node as HasGeometry).geometryType.toOpenGLType(),
                0, s.mStoredPrimitiveCount, count)

        }

        gl.gL4.glUseProgram(0)
        gl.gL4.glBindVertexArray(0)
    }

    override fun screenshot() {
        screenshotRequested = true
    }

    override fun close() {

    }
}
