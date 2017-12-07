package graphics.scenery.utils

import com.sun.javafx.application.PlatformImpl
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import java.nio.ByteBuffer
import java.util.*

/**
 * Created by ulrik on 6/30/2017.
 */
class SceneryPanel(imageWidth: Int, imageHeight: Int) : Pane() {
    private val logger by LazyLogger()

    val RESIZE_DELAY_MS = 40L

    inner class ResizeTimerTask(val panel: SceneryPanel, val width: Double, val height: Double) : TimerTask() {
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

    init {
        imageView = ImageView(image)
        imageView.style = "-fx-background-color: white;"
        imageView.scaleY = -1.0

        width = imageWidth.toDouble()
        height = imageHeight.toDouble()

        children.add(imageView)
    }

    fun update(buffer: ByteBuffer) {
        image.update(buffer)
    }

    override fun resize(width: Double, height: Double) {
        if (this.width == width && this.height == height) {
            return
        }

        this.width = width
        this.height = height

        imageView.fitWidth = width
        imageView.fitHeight = height

        if (resizeTimer != null) {
            resizeTimer?.cancel()
            resizeTimer?.purge()
            resizeTimer = null
        }

        resizeTimer = Timer()
        resizeTimer?.schedule(ResizeTimerTask(this, width, height), RESIZE_DELAY_MS)
    }

}
