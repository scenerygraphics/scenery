package graphics.scenery.utils

import java.nio.ByteBuffer

interface SceneryPanel {
    var displayedFrames: Long
    var imageScaleY: Float
    var panelWidth: Int
    var panelHeight: Int
    var refreshRate: Int

    fun update(buffer: ByteBuffer, id: Int = -1)
    fun setPreferredDimensions(w: Int, h: Int)
}
