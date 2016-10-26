package scenery.backends

import scenery.Hubable
import scenery.Scene
import scenery.Settings
import scenery.backends.opengl.DeferredLightingRenderer
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
    fun initializeScene(scene: Scene)

    /**
     * This function renders the scene
     *
     * @param[scene] The scene to render.
     */
    fun render()

    var shouldClose: Boolean

    var settings: Settings

    var window: SceneryWindow

    fun reshape(width: Int, height: Int)

    fun close()

    val managesRenderLoop: Boolean

    companion object {
        fun createRenderer(applicationName: String, scene: Scene, windowWidth: Int, windowHeight: Int): Renderer {
            val preference = System.getProperty("scenery.Renderer", "DeferredLightingRenderer")

            return if(preference == "VulkanRenderer") {
                VulkanRenderer(applicationName, scene, windowWidth, windowHeight)
            } else {
                DeferredLightingRenderer(applicationName, scene, windowWidth, windowHeight)
            }
        }
    }
}
