package graphics.scenery.backends.vulkan

import java.lang.Exception

/**
 * Class for encapsulating messages from the Vulkan validation layers
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class VulkanValidationLayerException(message: String) : Exception(message)
