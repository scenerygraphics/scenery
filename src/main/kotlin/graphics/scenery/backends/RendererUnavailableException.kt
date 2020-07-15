package graphics.scenery.backends

/**
 * Exception to be thrown when a particular renderer is not available, with a [reason] given.
 */
class RendererUnavailableException(reason: String) : Exception(reason)
