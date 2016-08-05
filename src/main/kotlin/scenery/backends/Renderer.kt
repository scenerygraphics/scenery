package scenery.backends

import scenery.Hubable
import scenery.Scene
import scenery.Settings

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
    fun render(scene: Scene)

    var width: Int
    var height: Int

    var settings: Settings

    fun reshape(width: Int, height: Int)
}
