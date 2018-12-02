package graphics.scenery.utils

import com.jogamp.opengl.awt.GLJPanel
import com.sun.jna.Pointer
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.opengl.OpenGLRenderer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.awt.AWTVKCanvas
import org.lwjgl.vulkan.awt.VKData
import java.awt.*
import java.awt.color.ColorSpace
import java.awt.image.*
import java.nio.ByteBuffer
import java.util.*
import javax.swing.JPanel
import java.awt.image.DataBuffer



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

    override var displayedFrames: Long = 0L

    override var imageScaleY: Float = 1.0f

    override fun setPreferredDimensions(w: Int, h: Int) {
        logger.info("Preferred dimensions=$w,$h")
    }

}
