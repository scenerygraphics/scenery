package scenery.rendermodules

import scenery.Scene

/**
 * Created by ulrik on 09/06/2016.
 */
interface Renderer {
    fun initializeScene(scene: Scene)
    fun render(scene: Scene)
}