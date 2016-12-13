package scenery.backends

import scenery.Hubable
import scenery.Scene
import scenery.Settings
import scenery.backends.opengl.OpenGLRenderer
import scenery.backends.vulkan.VulkanRenderer

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

//    fun setCurrentScene(scene: Scene)

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

    val managesRenderLoop: Boolean

    companion object {
        fun createRenderer(applicationName: String, scene: Scene, windowWidth: Int, windowHeight: Int): Renderer {
            val preference = System.getProperty("scenery.Renderer", "OpenGLRenderer")

            return if(preference == "VulkanRenderer") {
                VulkanRenderer(applicationName, scene, windowWidth, windowHeight)
            } else {
                OpenGLRenderer(applicationName, scene, windowWidth, windowHeight)
            }
        }
    }
}
