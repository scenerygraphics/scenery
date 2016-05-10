package scenery

import cleargl.GLVector
import scenery.rendermodules.opengl.OpenGLShaderPreference
import java.util.*

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class Skybox : Box(GLVector(50.0f, 50.0f, 50.0f)) {
    init {
        this.metadata.put(
                "ShaderPreference",
                OpenGLShaderPreference(
                        arrayListOf("Skybox.vert", "DefaultDeferred.frag"),
                        HashMap(),
                        arrayListOf("DeferredShadingRenderer")))
    }
}