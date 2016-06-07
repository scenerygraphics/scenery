package scenery.rendermodules.opengl

import scenery.NodeMetadata
import java.util.*

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
data class OpenGLShaderPreference(
        var shaders: List<String>,
        var parameters: HashMap<String, String>,
        override val consumers: List<String>) : NodeMetadata