package graphics.scenery

import org.joml.Vector3f

/**
 * Enum class to store origin information, e.g. for use with [graphics.scenery.volumes.Volume]s
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
sealed class Origin {
    /**
     * Volume origin will be the center of the volume.
     */
    data object Center: Origin()

    /**
     * Volume origin will be the front, bottom, left corner of the volume.
     */
    data object FrontBottomLeft: Origin()

    /**
     * Volume origin will be the custom vector given as [origin].
     */
    data class Custom(val origin: Vector3f): Origin()
}
