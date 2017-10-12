package graphics.scenery.utils

import com.sun.javafx.application.PlatformImpl
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import java.nio.ByteBuffer

/**
 * Created by ulrik on 6/30/2017.
 */
class SceneryPanel(imageWidth: Int, imageHeight: Int) : BorderPane() {
    private val logger by LazyLogger()

    var image: DirectWritableImage = DirectWritableImage(imageWidth, imageHeight)
    var imageView: ImageView

    init {
        imageView = ImageView(image)
        imageView.style = "-fx-background-color: white;"

        width = imageWidth.toDouble()
        height = imageHeight.toDouble()

        center = imageView
    }

    fun update(buffer: ByteBuffer) {
        image.update(buffer)
    }

    override fun resize(width: Double, height: Double) {
        if(this.width == width && this.height == height) {
            return
        }

        PlatformImpl.runLater {
            super.resize(width, height)
            image = DirectWritableImage(width.toInt(), height.toInt())
            imageView = ImageView(image)
            imageView.scaleY = -1.0

            center = imageView
        }
    }

}
