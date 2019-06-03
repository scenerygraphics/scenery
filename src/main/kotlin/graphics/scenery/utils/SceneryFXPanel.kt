package graphics.scenery.utils

import com.sun.javafx.application.PlatformImpl
import graphics.scenery.backends.ResizeHandler
import graphics.scenery.backends.SceneryWindow
import javafx.scene.CacheHint
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import java.nio.ByteBuffer
import java.util.*

/**
 * Panel for JavaFX scenery can be embedded into.
 *
 * WARNING: Experimental, results in bad rendering performance at the moment.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Hongkee Moon <moon@mpi-cbg.de>
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hmmi.org>
 */
class SceneryFXPanel(var imageWidth: Int, var imageHeight: Int) : Pane(), SceneryPanel {

    private val logger by LazyLogger()

    /** Refresh rate */
    override var refreshRate: Int = 60

    /** Delay between resize events before associated renderer swapchains are resized actually. */
    val RESIZE_DELAY_MS = 200L

    /** Width of the panel. */
    override var panelWidth
        get() = super.getWidth().toInt()
        set(w: Int) { super.setWidth(w.toDouble()) }

    /** Height of the panel. */
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
    /** Image view for [image]. */
    internal var imageView: ImageView
    /** Displayed frames so far. */
    override var displayedFrames = 0L
    /** Image scale in Y, default is 1.0f, aka no flipping. */
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

    /**
     * Updates the panel's backing buffer with [buffer].
     * */
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

    /**
     * Sets the preferred size of the window to [w]x[h].
     */
    override fun setPreferredDimensions(w: Int, h: Int) {
        prefWidth = w.toDouble()
        prefHeight = h.toDouble()
    }

    /**
     * Initialiases the panel and returns a [SceneryWindow] instance
     */
    override fun init(resizeHandler: ResizeHandler) : SceneryWindow {
        widthProperty()?.addListener { _, _, newWidth ->
            resizeHandler.lastWidth = newWidth.toInt()
        }

        heightProperty()?.addListener { _, _, newHeight ->
            resizeHandler.lastHeight = newHeight.toInt()
        }

        return SceneryWindow.JavaFXStage(this)
    }
}
