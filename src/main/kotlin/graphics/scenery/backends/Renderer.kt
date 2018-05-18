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
abstract class Renderer : Hubable {
    /**
     * Initializes scene and contents
     */
    abstract fun initializeScene()

    /**
     * Renders the scene
     */
    abstract fun render()

    abstract var shouldClose: Boolean

    abstract var initialized: Boolean
        protected set

    abstract var settings: Settings

    abstract var window: SceneryWindow

    abstract var embedIn: SceneryPanel?

    abstract fun close()

    fun screenshot() {
        screenshot("")
    }

    abstract fun screenshot(filename: String = "")

    abstract fun reshape(newWidth: Int, newHeight: Int)

    /**
     * Sets the rendering quality, if the loaded renderer config file supports it.
     *
     * @param[quality] The [RenderConfigReader.RenderingQuality] to be set.
     */
    abstract fun setRenderingQuality(quality: RenderConfigReader.RenderingQuality)

    abstract val managesRenderLoop: Boolean

    abstract var lastFrameTime: Float

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

    companion object {
        val logger by LazyLogger()

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
                throw e
            }
        }
    }
}
