package graphics.scenery.utils

import java.nio.ByteBuffer

/**
 * Interface for embeddable windows scenery can render to.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
interface SceneryPanel {

    /** Number of frames displayed so far. */
    var displayedFrames: Long

    /** Image scaling in Y, can be used to flip the image. */
    var imageScaleY: Float

    /** Current width of the panel. */
    var panelWidth: Int

    /** Current height of the panel. */
    var panelHeight: Int

    /** Refresh rate. */
    var refreshRate: Int

    /** Updates the backing byte buffer of the image. */
    fun update(buffer: ByteBuffer, id: Int = -1)

    /** Sets the preferred dimensions of the panel. */
    fun setPreferredDimensions(w: Int, h: Int)
}
