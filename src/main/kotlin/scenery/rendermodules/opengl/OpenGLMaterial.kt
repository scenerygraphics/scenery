package scenery.rendermodules.opengl

import cleargl.GLProgram
import scenery.Material

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class OpenGLMaterial : Material() {
    var program: GLProgram? = null
}