package graphics.scenery.backends

import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.Scene
import graphics.scenery.Settings
import graphics.scenery.backends.opengl.OpenGLRenderer
import graphics.scenery.backends.vulkan.VulkanRenderer
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

    abstract var settings: Settings

    abstract var window: SceneryWindow

    abstract var embedIn: SceneryPanel?

    abstract fun close()

    abstract fun screenshot()

    abstract fun reshape(newWidth: Int, newHeight: Int)

    abstract val managesRenderLoop: Boolean

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

        if(isStereo) {
            val nonStereoConfig = renderConfigFile.substringBeforeLast("Stereo") + ".yml"

            if(RenderConfigReader::class.java.getResource(nonStereoConfig) != null) {
                renderConfigFile = nonStereoConfig
                settings.set("vr.Active", false)
            } else {
                logger.warn("Non-stereo configuration for $renderConfigFile ($nonStereoConfig) not found.")
            }
        } else {
            val stereoConfig = renderConfigFile.substringBeforeLast(".") + "Stereo.yml"

            if(RenderConfigReader::class.java.getResource(stereoConfig) != null) {
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

        settings.set("vr.Active", true)
        settings.set("vr.IPD", 0.05f)

        settings.set("sdf.MaxDistance", 10)

        settings.set("Renderer.PrintGPUStats", false)
        settings.set("Renderer.SupersamplingFactor", System.getProperty("scenery.Renderer.SupersamplingFactor")?.toFloat() ?: 1.0f)

        return settings
    }

    companion object {
        @JvmOverloads @JvmStatic fun createRenderer(hub: Hub, applicationName: String, scene: Scene, windowWidth: Int, windowHeight: Int, embedIn: SceneryPanel? = null): Renderer {
            val preference = System.getProperty("scenery.Renderer", "OpenGLRenderer")

            return if (preference == "VulkanRenderer") {
                VulkanRenderer(hub, applicationName, scene, windowWidth, windowHeight, embedIn)
            } else {
                OpenGLRenderer(hub, applicationName, scene, windowWidth, windowHeight, embedIn)
            }
        }
    }
}
