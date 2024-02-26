package graphics.scenery.utils

import graphics.scenery.backends.ResizeHandler
import graphics.scenery.backends.SceneryWindow
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.nio.ByteBuffer
import javax.swing.JPanel

/**
 * Swing panel scenery can be embedded into.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class SceneryJPanel(val owned: Boolean = false) : JPanel(), SceneryPanel {
    /** Refresh rate. */
    override var refreshRate: Int = 60

    /** Updates the backing buffer of the window. Does nothing for Swing. */
    override fun update(buffer: ByteBuffer, id: Int) { }

    private val logger by lazyLogger()

    /** Width of the panel, cannot be reset. */
    @Suppress("UNUSED_PARAMETER")
    override var panelWidth: Int
        get() = super.getWidth()
        set(value) {}

    /** Height of the panel, cannot be reset. */
    @Suppress("UNUSED_PARAMETER")
    override var panelHeight: Int
        get() = super.getHeight()
        set(value) { }

    /** Embedded component that receives the actual rendering, e.g. via a native surface. */
    var component: Component? = null

    /** Displayed frames so far. */
    override var displayedFrames: Long = 0L

    /** Image scale, no flipping needed here. */
    override var imageScaleY: Float = 1.0f

    /** Sets the preferred dimensions of the panel. */
    override fun setPreferredDimensions(w: Int, h: Int) {
        logger.info("Preferred dimensions=$w,$h")
    }

    override fun init(resizeHandler : ResizeHandler) : SceneryWindow {
        this.addComponentListener(object: ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                super.componentResized(e)
                logger.debug("SceneryJPanel component resized to ${e.component.width} ${e.component.height}")
                resizeHandler.lastWidth = e.component.width
                resizeHandler.lastHeight = e.component.height
            }
        })

        return SceneryWindow.SwingWindow(this)
    }
}
