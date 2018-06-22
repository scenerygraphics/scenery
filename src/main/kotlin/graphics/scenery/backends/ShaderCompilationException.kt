package graphics.scenery.backends

/**
 * Exception to throw when a problem occurs during shader compilation. [message] may specify the
 * problem in more detail.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class ShaderCompilationException(message: String = "Shader compilation failed") : Exception(message)
