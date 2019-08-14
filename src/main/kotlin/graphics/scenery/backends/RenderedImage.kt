package graphics.scenery.backends

/**
 * Class for rendered images with a given [width], [height], and [data] as ByteArray.
 */
sealed class RenderedImage(open var width: Int, open var height: Int, open var data: ByteArray? = null) {
    /**
     * Class for a rendered image in RGB format, with size [width]x[height], and [data] stored as [ByteArray].
     */
    data class RenderedRGBImage(override var width: Int, override var height: Int, override var data: ByteArray? = null) : RenderedImage(width, height, data)
    /**
     * Class for a rendered image in RGBA format, with size [width]x[height], and [data] stored as [ByteArray].
     */
    data class RenderedRGBAImage(override var width: Int, override var height: Int, override var data: ByteArray? = null) : RenderedImage(width, height, data)
}
