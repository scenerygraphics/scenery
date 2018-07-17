package graphics.scenery.backends

import java.io.FileNotFoundException

/**
 * Exception to be thrown if a shader file cannot be located.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class ShaderNotFoundException(description: String): FileNotFoundException(description)
