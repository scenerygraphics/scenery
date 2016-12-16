package graphics.scenery.scenery.backends

import graphics.scenery.scenery.Hubable
import graphics.scenery.scenery.Scene
import graphics.scenery.scenery.Settings
import graphics.scenery.scenery.backends.opengl.OpenGLRenderer
import graphics.scenery.scenery.backends.vulkan.VulkanRenderer

/**
 * Renderer interface. Defines the minimal set of functions a renderer has to implement.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface Renderer : Hubable {
    /**
     * This function should initialize the scene contents.
     *
     * @param[scene] The scene to initialize.
     */
    fun initializeScene()

    /**
     * This function renders the scene
     *
     * @param[scene] The scene to render.
     */
    fun render()

    var shouldClose: Boolean

    var settings: Settings

    var window: SceneryWindow

    fun close()

    fun screenshot()

    fun reshape(newWidth: Int, newHeight: Int)

    val managesRenderLoop: Boolean

    companion object Factory {
        fun createRenderer(applicationName: String, scene: Scene, windowWidth: Int, windowHeight: Int): Renderer {
            val preference = System.getProperty("scenery.Renderer", "OpenGLRenderer")

            return if (preference == "VulkanRenderer") {
                VulkanRenderer(applicationName, scene, windowWidth, windowHeight)
            } else {
                OpenGLRenderer(applicationName, scene, windowWidth, windowHeight)
            }
        }
    }
}
