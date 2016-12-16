package graphics.scenery.scenery

import cleargl.GLVector
import graphics.scenery.scenery.backends.ShaderPreference
import java.util.*

/**
 * Skybox class. Sets a [ShaderPreference] using a shader that will always cause
 * the depth test to fail if there is geometry in front, creating the illusion of a far
 * away box.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates a [Box] with the magic skybox shader as material.
 */
open class Skybox : Box(GLVector(50.0f, 50.0f, 50.0f)) {
    init {
        this.metadata.put(
                "ShaderPreference",
                ShaderPreference(
                        arrayListOf("Skybox.vert", "DefaultDeferred.frag"),
                        HashMap(),
                        arrayListOf("DeferredShadingRenderer")))
    }
}
