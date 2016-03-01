package scenery.rendermodules.opengl

import cleargl.GLProgram
import scenery.PhongMaterial

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class OpenGLMaterial : PhongMaterial() {
    var program: GLProgram? = null
}