package graphics.scenery.utils

import java.awt.Graphics
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import javax.swing.JPanel

class SceneryJPanel : JPanel(), SceneryPanel {
    override var panelWidth: Int
        get() = super.getWidth()
        set(value) {}
    override var panelHeight: Int
        get() = super.getHeight()
        set(value) {}

    protected var images =  Array<BufferedImage?>(2, { null })
    protected var currentReadImage = 0
    protected var currentWriteImage = 0

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val image = images[currentReadImage]

        if(image != null) {

            g.drawImage(image, 0, 0, image.width, image.height, null)
        }
    }

    override fun reshape(x: Int, y: Int, w: Int, h: Int) {
        super.reshape(x, y, w, h)
    }

    override fun update(buffer: ByteBuffer, id: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override var displayedFrames: Long
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}

    override var imageScaleY: Float
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}

    override fun setPreferredDimensions(w: Int, h: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
