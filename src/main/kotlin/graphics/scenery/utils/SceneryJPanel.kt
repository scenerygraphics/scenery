package graphics.scenery.utils

import cleargl.ClearGLWindow
import java.awt.Component
import java.nio.ByteBuffer
import javax.swing.JPanel

/**
 * Swing panel scenery can be embedded into.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class SceneryJPanel : JPanel(), SceneryPanel {
    /** Refresh rate. */
    override var refreshRate: Int = 60

    /** Updates the backing buffer of the window. Does nothing for Swing. */
    override fun update(buffer: ByteBuffer, id: Int) { }

    private val logger by LazyLogger()

    /** Width of the panel. */
    override var panelWidth: Int
        get() = super.getWidth()
        set(value) {}

    /** Height of the panel. */
    override var panelHeight: Int
        get() = super.getHeight()
        set(value) {}

    /** Embedded component that receives the actual rendering, e.g. via a native surface. */
    var component: Component? = null

    /** [ClearGLWindow] the [OpenGLRenderer] is rendering to. */
    var cglWindow: ClearGLWindow? = null

    /** Displayed frames so far. */
    override var displayedFrames: Long = 0L

    /** Image scale, no flipping needed here. */
    override var imageScaleY: Float = 1.0f

    /** Sets the preferred dimensions of the panel. */
    override fun setPreferredDimensions(w: Int, h: Int) {
        logger.info("Preferred dimensions=$w,$h")
    }
}
