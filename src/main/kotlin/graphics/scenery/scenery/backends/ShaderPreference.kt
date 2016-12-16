package graphics.scenery.scenery.backends

import graphics.scenery.scenery.NodeMetadata
import java.util.*

/**
 * This class stores paths to GLSL shader files to be used for rendering preferentially,
 * as well as any parameters that are then expanded by the GLSL precompiler.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
data class ShaderPreference(
    /** List of shader files */
    var shaders: List<String>,
    /** Hash map of shader parameters to be processed by the precompiler. */
    var parameters: HashMap<String, String>,
    /** Consumers */
    override val consumers: List<String>) : NodeMetadata
