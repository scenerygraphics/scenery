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
interface Renderer : Hubable {
    /**
     * Initializes scene and contents
     */
    fun initializeScene()

    /**
     * Renders the scene
     */
    fun render()

    var shouldClose: Boolean

    var settings: Settings

    var window: SceneryWindow

    var embedIn: SceneryPanel?

    fun close()

    fun screenshot()

    fun reshape(newWidth: Int, newHeight: Int)

    val managesRenderLoop: Boolean

    var renderConfigFile: String

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

    companion object Factory {
        @JvmOverloads fun createRenderer(hub: Hub, applicationName: String, scene: Scene, windowWidth: Int, windowHeight: Int, embedIn: SceneryPanel? = null): Renderer {
            val preference = System.getProperty("scenery.Renderer", "OpenGLRenderer")

            return if (preference == "VulkanRenderer") {
                VulkanRenderer(hub, applicationName, scene, windowWidth, windowHeight, embedIn)
            } else {
                OpenGLRenderer(hub, applicationName, scene, windowWidth, windowHeight, embedIn)
            }
        }
    }
}
