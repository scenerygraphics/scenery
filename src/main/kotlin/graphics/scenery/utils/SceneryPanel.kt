package graphics.scenery.utils

import com.sun.javafx.application.PlatformImpl
import com.sun.javafx.sg.prism.NGNode
import com.sun.javafx.sg.prism.NGRegion
import com.sun.prism.Graphics
import com.sun.prism.Texture
import javafx.scene.image.ImageView
import javafx.scene.layout.Region
import java.nio.ByteBuffer
import java.util.*

/**
 * Created by ulrik on 6/30/2017.
 */
class SceneryPanel(imageWidth: Int, imageHeight: Int) : Region() {
    private val logger by LazyLogger()

    val RESIZE_DELAY_MS = 200L

    inner class ResizeTimerTask(val width: Double, val height: Double) : TimerTask() {
        /**
         * The action to be performed by this timer task.
         */
        override fun run() {
            image = DirectWritableImage(width.toInt(), height.toInt())

            PlatformImpl.runLater {
                imageView.image = image
                imageView.scaleY = -1.0
            }
        }

    }

    var image: DirectWritableImage = DirectWritableImage(imageWidth, imageHeight)
    var imageView: ImageView
    private var resizeTimer: Timer? = null
    private var latestImageSize = 0
    private var imageBuffer: ByteBuffer? = null
    private var textureId = -1

    init {
        imageView = ImageView(image)
        imageView.style = "-fx-background-color: white;"
        imageView.scaleY = -1.0

        width = imageWidth.toDouble()
        height = imageHeight.toDouble()

        children.add(imageView)
    }

    fun update(buffer: ByteBuffer, id: Int = -1) {
        latestImageSize = buffer.capacity()
        image.update(buffer)
        imageBuffer = buffer
        textureId = id
    }

    override fun resize(width: Double, height: Double) {
        if (this.width == width && this.height == height) {
            return
        }

        this.width = width
        this.height = height

        imageView.image = null
        imageView.fitWidth = width
        imageView.fitHeight = height

        if (resizeTimer != null) {
            resizeTimer?.cancel()
            resizeTimer?.purge()
            resizeTimer = null
        }

        resizeTimer = Timer()
        resizeTimer?.schedule(ResizeTimerTask(width, height), RESIZE_DELAY_MS)
    }

    override fun impl_createPeer(): NGNode {
        return NGSceneryPanelRegion()
    }

    private inner class NGSceneryPanelRegion: NGRegion() {
        override fun renderContent(g: Graphics) {
            if (latestImageSize == this@SceneryPanel.width.toInt() * this@SceneryPanel.height.toInt() * 4 && imageBuffer != null) {
                val t = g.resourceFactory.getCachedTexture(image.getWritablePlatformImage(image) as com.sun.prism.Image, Texture.WrapMode.CLAMP_TO_EDGE)
//                t.update(imageBuffer, com.sun.prism.PixelFormat.BYTE_BGRA_PRE, 0, 0, 0, 0, width.toInt(), height.toInt(), 4*width.toInt(), false)

//                if(textureId != -1) {
//                    ES2TextureInjector.create(t, textureId, width.toInt(), height.toInt())
//                }

                g.clearQuad(0.0f, 0.0f, width.toFloat(), height.toFloat())
                g.drawTexture(t, 0.0f, 0.0f, width.toFloat(), height.toFloat())

//                dirty = DirtyFlag.DIRTY
            }
        }
    }

}
