package graphics.scenery.utils

import com.sun.javafx.application.PlatformImpl
import javafx.scene.CacheHint
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import java.nio.ByteBuffer
import java.util.*

/**
 * Created by ulrik on 6/30/2017.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Hongkee Moon <moon@mpi-cbg.de>
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hmmi.org>
 */
class SceneryFXPanel(var imageWidth: Int, var imageHeight: Int) : Pane(), SceneryPanel {

    private val logger by LazyLogger()

    /** Delay between resize events before associated renderer swapchains are resized actually. */
    val RESIZE_DELAY_MS = 200L

    override var panelWidth
        get() = super.getWidth().toInt()
        set(w: Int) { super.setWidth(w.toDouble()) }

    override var panelHeight
        get() = super.getHeight().toInt()
        set(w: Int) { super.setHeight(w.toDouble()) }

    /** Timer task to keep track of resize events. */
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

    /** The image displayed in the panel. Will be set by a renderer. */
    protected var image: DirectWritableImage = DirectWritableImage(imageWidth, imageHeight)
    internal var imageView: ImageView
    override var displayedFrames = 0L
    override var imageScaleY = 1.0f

    private var resizeTimer: Timer? = null
    private var latestImageSize = 0
    private var imageBuffer: ByteBuffer? = null
    private var textureId = -1

    init {
        imageView = ImageView(image)

        panelWidth = imageWidth.toDouble().toInt()
        panelHeight = imageHeight.toDouble().toInt()

        minWidth = 1.0
        minHeight = 1.0

        children.add(imageView)

        isCache = true
        imageView.isCache = true
    }

    override fun update(buffer: ByteBuffer, id: Int) {
        cacheHint = CacheHint.SPEED
        imageView.cacheHint = CacheHint.SPEED

        if(resizeTimer != null) {
            return
        }

        latestImageSize = buffer.remaining()
        image.update(buffer)
        imageBuffer = buffer
        textureId = id

//        cacheHint = CacheHint.QUALITY
//        imageView.cacheHint = CacheHint.QUALITY
    }

    /**
     * Resizes the panel to [width] x [height].
     */
    override fun resize(width: Double, height: Double) {
        if (this.panelWidth == width.toInt() && this.panelHeight == height.toInt()) {
            return
        }

        if( !(width > 0 && height > 0 ) ) {
            return
        }

        this.panelWidth = width.toInt()
        this.panelHeight = height.toInt()

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

    override fun setPreferredDimensions(w: Int, h: Int) {
        prefWidth = w.toDouble()
        prefHeight = h.toDouble()
    }

    /*
    /** Retuns the region rendered by this panel. */
    @Suppress("OverridingDeprecatedMember")
    override fun impl_createPeer(): NGNode {
        return NGSceneryPanelRegion()
    }

    private inner class NGSceneryPanelRegion: NGRegion() {
        override fun renderContent(g: Graphics) {

            logger.trace(" w x h : {} {} panel w x h : {} {} buffer sizes {} vs {}", imageWidth, imageHeight, width, height, latestImageSize, this@SceneryFXPanel.width*this@SceneryFXPanel.height*4)

            if (latestImageSize == this@SceneryFXPanel.width.toInt() * this@SceneryFXPanel.height.toInt() * 4 && imageBuffer != null) {
                g.clear()
                val t = g.resourceFactory.getCachedTexture(image.getWritablePlatformImage(image) as com.sun.prism.Image, Texture.WrapMode.CLAMP_TO_EDGE)
                if(imageView.scaleY < 0.0f) {
                    g.drawTexture(t, 0.0f, 0.0f, width.toFloat(), height.toFloat(), 0.0f, height.toFloat(), width.toFloat(), 0.0f)
                } else {
                    g.drawTexture(t, 0.0f, 0.0f, width.toFloat(), height.toFloat())
                }

                displayedFrames++
            } else {
                logger.debug("Not rendering, size mismatch ${this@SceneryFXPanel.width}x${this@SceneryFXPanel.height}")
            }
        }
    }*/

}
