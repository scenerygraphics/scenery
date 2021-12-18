package graphics.scenery.backends

/**
 * Exception to throw when a problem occurs during shader introspection.
 * @param[message] A message that may specify the problem in more detail.
 * @param[vulkanSemantics] Indicates whether Vulkan semantics are used.
 * @param[version] The GLSL version used.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class ShaderIntrospectionException(message: String, vulkanSemantics: Boolean, version: Int) : Exception("Shader introspection exception: $message (GLSL version $version${if(vulkanSemantics) { ", Vulkan semantics" } else {""}})")
