package graphics.scenery.backends

/**
 * Enum for all the supported shader types.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
enum class ShaderType {
    VertexShader,
    TessellationControlShader,
    TessellationEvaluationShader,
    GeometryShader,
    FragmentShader,
    ComputeShader
}

/**
 * Extension function to turn a [ShaderType] into the canonical GLSL file extension.
 */
fun ShaderType.toExtension(): String = when(this) {
    ShaderType.VertexShader -> ".vert"
    ShaderType.TessellationControlShader -> ".tesc"
    ShaderType.TessellationEvaluationShader -> ".tese"
    ShaderType.GeometryShader -> ".geom"
    ShaderType.FragmentShader -> ".frag"
    ShaderType.ComputeShader -> ".comp"
}
