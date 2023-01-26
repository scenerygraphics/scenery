package graphics.scenery

import org.joml.Vector3f

/**
 * Enum class to store origin information, e.g. for use with [graphics.scenery.volumes.Volume]s
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
sealed class Origin {
    object Center: Origin()
    object FrontBottomLeft: Origin()
    class Custom(val origin: Vector3f): Origin()
}
