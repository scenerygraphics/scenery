package graphics.scenery.backends

import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.Scene
import graphics.scenery.Settings
import graphics.scenery.backends.opengl.OpenGLRenderer
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.utils.ExtractsNatives
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.SceneryPanel

/**
 * Renderer interface. Defines the minimal set of functions a renderer has to implement.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
@Suppress("unused")
abstract class Renderer : Hubable {
    /**
     * Initializes scene and contents
     */
    abstract fun initializeScene()

    /**
     * Renders the scene
     */
    abstract fun render()

    /** Signals whether the current renderer should stop working and close all open windows. */
    abstract var shouldClose: Boolean

    /** Signals whether the renderer is done initialiasing and can start with scene initialisation and rendering. */
    abstract var initialized: Boolean
        protected set

    /** Signals whether a first image has been drawn. */
    abstract var firstImageReady: Boolean
        protected set

    /** [Settings] instance the renderer is using. */
    abstract var settings: Settings

    /** [SceneryWindow] the renderer is drawing to. */
    abstract var window: SceneryWindow

    /** A [SceneryPanel] the renderer might be embedded in. */
    abstract var embedIn: SceneryPanel?

    /**
     * Method to close the renderer.
     */
    abstract fun close()

    /**
     * Takes a screenshot, and saves it to the users's desktop directory.
     */
    fun screenshot() {
        screenshot("")
    }

    /**
     * Takes a screenshot, and saves it as [filename].
     *
     * @param[filename] The filename where to save the screenshot.
     */
    abstract fun screenshot(filename: String = "")

    /**
     * Reshapes the window to the given sizes.
     *
     * @param[newWidth] The new width of the window.
     * @param[newHeight] The new height of the window.
     */
    abstract fun reshape(newWidth: Int, newHeight: Int)

    /**
     * Sets the rendering quality, if the loaded renderer config file supports it.
     *
     * @param[quality] The [RenderConfigReader.RenderingQuality] to be set.
     */
    abstract fun setRenderingQuality(quality: RenderConfigReader.RenderingQuality)

    /**
     * Activate or deactivate push-based rendering mode (render only on scene changes
     * or input events). Push mode is activated if [pushMode] is true.
     */
    abstract var pushMode: Boolean

    /**
     * Whether the renderer manages it's own main loop. If false, [graphics.scenery.SceneryBase] will take
     * care of the rendering loop inside its main loop.
     */
    abstract val managesRenderLoop: Boolean

    /** Total time taken for the last frame (in milliseconds). */
    abstract var lastFrameTime: Float

    /** The file to read the [RenderConfigReader.RenderConfig] from. */
    abstract var renderConfigFile: String

    /**
     * Toggles VR on and off, and loads the appropriate renderer config file, if it exists.
     * The name is the name of the current renderer config file, with "Stereo" at the end.
     */
    @Suppress("UNUSED")
    fun toggleVR() {
        val logger by LazyLogger()
        logger.info("Toggling VR!")
        val isStereo = renderConfigFile.substringBeforeLast(".").indexOf("Stereo") != -1

        if (isStereo) {
            val nonStereoConfig = renderConfigFile.substringBeforeLast("Stereo") + ".yml"

            if (RenderConfigReader::class.java.getResource(nonStereoConfig) != null) {
                renderConfigFile = nonStereoConfig
                settings.set("vr.Active", false)
            } else {
                logger.warn("Non-stereo configuration for $renderConfigFile ($nonStereoConfig) not found.")
            }
        } else {
            val stereoConfig = renderConfigFile.substringBeforeLast(".") + "Stereo.yml"

            if (RenderConfigReader::class.java.getResource(stereoConfig) != null) {
                renderConfigFile = stereoConfig
                settings.set("vr.Active", true)
            } else {
                logger.warn("Stereo VR configuration for $renderConfigFile ($stereoConfig) not found.")
            }
        }
    }

    /**
     * Adds the default [Settings] for [Renderer] to a given [Settings] instance.
     *
     * Providing some sane defaults that may of course be overridden after
     * construction of the renderer.
     *
     * @param[settings] The [Settings] instance to augment.
     * @return Default [Settings] values.
     */
    fun loadDefaultRendererSettings(settings: Settings): Settings {
        settings.set("wantsFullscreen", false)
        settings.set("isFullscreen", false)

        settings.set("vr.Active", false)
        settings.set("vr.IPD", 0.05f)

        settings.set("sdf.MaxDistance", 12)

        settings.set("Renderer.PrintGPUStats", false)
        settings.set("Renderer.SupersamplingFactor", System.getProperty("scenery.Renderer.SupersamplingFactor")?.toFloat()
            ?: 1.0f)

        return settings
    }

    /**
     * Factory methods for creating renderers.
     */
    companion object {
        val logger by LazyLogger()

        /**
         * Creates a new [Renderer] instance, based on what is available on the current platform, or set via
         * the scenery.Renderer system property.
         *
         * On Linux and Windows, [VulkanRenderer]will be created by default.
         * On macOS, [OpenGLRenderer] will be created by default.
         *
         * @param[hub] The [Hub] to use.
         * @param[applicationName] Application name, mainly used for the title bar if shown.
         * @param[scene] The initial [Scene] the renderer should display.
         * @param[windowWidth] Window width for the renderer window.
         * @param[windowHeight] Window height for the renderer window.
         * @param[embedIn] A [SceneryWindow] to embed the renderer in, can e.g. be a JavaFX window.
         * @param[renderConfigFile] A YAML file with the render path configuration from which a [RenderConfigReader.RenderConfig] will be created.
         *
         * @return A new [Renderer] instance.
         */
        @JvmOverloads
        @JvmStatic
        fun createRenderer(hub: Hub, applicationName: String, scene: Scene, windowWidth: Int, windowHeight: Int, embedIn: SceneryPanel? = null, renderConfigFile: String? = null): Renderer {
            var preference = System.getProperty("scenery.Renderer", null)
            val config = renderConfigFile ?: System.getProperty("scenery.Renderer.Config", "DeferredShading.yml")

            preference = when {
                preference == null &&
                    (ExtractsNatives.getPlatform() == ExtractsNatives.Platform.LINUX
                        || ExtractsNatives.getPlatform() == ExtractsNatives.Platform.WINDOWS) -> "VulkanRenderer"

                preference == null &&
                    ExtractsNatives.getPlatform() == ExtractsNatives.Platform.MACOS -> "OpenGLRenderer"

                else -> preference
            }

            return try {
                if (preference == "VulkanRenderer") {
                    try {
                        VulkanRenderer(hub, applicationName, scene, windowWidth, windowHeight, embedIn, config)
                    } catch (e: Exception) {
                        logger.warn("Vulkan unavailable (${e.cause}, ${e.message}), falling back to OpenGL.")
                        OpenGLRenderer(hub, applicationName, scene, windowWidth, windowHeight, embedIn, config)
                    }
                } else {
                    OpenGLRenderer(hub, applicationName, scene, windowWidth, windowHeight, embedIn, config)
                }
            } catch (e: Exception) {
                logger.error("Could not instantiate renderer. Is your graphics card working properly and do you have the most recent drivers installed?")
                throw RuntimeException("Could not instantiate renderer (${e.cause}, ${e.message})")
            }
        }
    }
}
