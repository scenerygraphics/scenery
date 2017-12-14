package graphics.scenery.utils

import com.sun.javafx.application.PlatformImpl
import com.sun.javafx.sg.prism.NGNode
import com.sun.javafx.sg.prism.NGRegion
import com.sun.prism.Graphics
import com.sun.prism.Texture
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import java.nio.ByteBuffer
import java.util.*

/**
 * Created by ulrik on 6/30/2017.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Hongkee Moon <moon@mpi-cbg.de>
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hmmi.org>
 */
class SceneryPanel(var imageWidth: Int, var imageHeight: Int) : Pane() {
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
            }

            resizeTimer = null
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
        imageView.scaleY = -1.0

        width = imageWidth.toDouble()
        height = imageHeight.toDouble()

        children.add(imageView)
    }

    fun update(buffer: ByteBuffer, id: Int = -1) {
        if(resizeTimer != null) {
            return
        }

        latestImageSize = buffer.capacity()
        image.update(buffer)
        imageBuffer = buffer
        textureId = id
    }

    override fun resize(width: Double, height: Double) {
        if (this.width == width && this.height == height) {
            return
        }

        if( !(width > 0 && height > 0 ) ) {
            return
        }



        this.width = width
        this.height = height

        this.imageWidth = width.toInt()
        this.imageHeight = height.toInt()

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

            logger.trace(" w x h : {} {} panel w x h : {} {} buffer sizes {} vs {}", imageWidth, imageHeight, width, height, latestImageSize, this@SceneryPanel.width*this@SceneryPanel.height*4)
            if (latestImageSize == this@SceneryPanel.width.toInt() * this@SceneryPanel.height.toInt() * 4 && imageBuffer != null) {
                val t = g.resourceFactory.getCachedTexture(image.getWritablePlatformImage(image) as com.sun.prism.Image, Texture.WrapMode.CLAMP_TO_EDGE)

                g.clearQuad(0.0f, 0.0f, width.toFloat(), height.toFloat())
                g.drawTexture(t, 0.0f, 0.0f, width.toFloat(), height.toFloat())
           }
        }
    }

}
