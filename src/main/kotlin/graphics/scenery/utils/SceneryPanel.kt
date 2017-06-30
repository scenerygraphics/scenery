package graphics.scenery.utils

import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

/**
 * Created by ulrik on 6/30/2017.
 */
class SceneryPanel(imageWidth: Int, imageHeight: Int) : BorderPane() {
    var image: DirectWritableImage = DirectWritableImage(imageWidth, imageHeight)
    var imageView: ImageView

    val logger = LoggerFactory.getLogger("SceneryPanel")

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

}
