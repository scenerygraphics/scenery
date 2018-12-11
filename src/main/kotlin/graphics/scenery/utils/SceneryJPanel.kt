package graphics.scenery.utils

import cleargl.ClearGLWindow
import java.awt.Component
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import javax.swing.JPanel



class SceneryJPanel : JPanel(), SceneryPanel {
    override fun update(buffer: ByteBuffer, id: Int) {
    }

    private val logger by LazyLogger()

    override var panelWidth: Int
        get() = super.getWidth()
        set(value) {}
    override var panelHeight: Int
        get() = super.getHeight()
        set(value) {}

    protected var images =  Array<BufferedImage?>(2, { null })

    protected var renderingReady = false

    override var refreshRate = 60

    var component: Component? = null
    var cglWindow: ClearGLWindow? = null

    override var displayedFrames: Long = 0L

    override var imageScaleY: Float = 1.0f

    override fun setPreferredDimensions(w: Int, h: Int) {
        logger.info("Preferred dimensions=$w,$h")
    }

}
