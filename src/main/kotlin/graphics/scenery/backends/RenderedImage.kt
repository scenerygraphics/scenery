package graphics.scenery.backends

import java.nio.ByteBuffer

sealed class RenderedImage(open var width: Int, open var height: Int, open var data: ByteArray? = null) {
    data class RenderedRGBImage(override var width: Int, override var height: Int, override var data: ByteArray? = null) : RenderedImage(width, height, data)
    data class RenderedRGBAImage(override var width: Int, override var height: Int, override var data: ByteArray? = null) : RenderedImage(width, height, data)
}
