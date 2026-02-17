package graphics.scenery.ui

import graphics.scenery.Box
import graphics.scenery.Mesh
import graphics.scenery.OrientedBoundingBox
import graphics.scenery.RichNode
import graphics.scenery.primitives.TextBoard
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.joml.Vector3f
import org.joml.Vector4f
import org.joml.getVector3f
import org.scijava.util.ListUtils.first
import kotlin.concurrent.thread

/**
 * Text with a Box behind it.
 * @author Jan Tiemann
 */
open class TextBox(
    text: String, var padding: Float = 0.2f, var minSize: Float = 0f,
    final override var height: Float = 1.0f, var thickness: Float = 0.5f
) :
    Mesh("TextBox"), Gui3DElement {
    val box = Box(Vector3f(1f, height, thickness))
    val board = TextBoard()

    var text by board::text
    var fontColor by board::fontColor
    var backgroundColor by box.material()::diffuse

    final override var width = 0f
        private set

    init {
        board.text = text
        board.name = "$text TextBoard"
        board.transparent = 1
        board.fontColor = Vector4f(0.0f, 0.0f, 0.0f, 1.0f)
        board.spatial {
            scale *= height
            position.y = 0.07f
        }
        box.name = "$text Box"
        box.material().diffuse = Vector3f(0.5f)
        box.spatial {
            position.z = box.sizes.z * -0.5f - 0.05f
        }

        this.addChild(board)
        board.createRenderable()
        this.addChild(box)

        var textGeom = board.geometry().vertices

        fun updateSize(force: Boolean = false) {
            if (textGeom != board.geometry().vertices || force) {
                val bv = board.geometry().vertices.duplicate().clear()
                var maxX = minSize
                while (bv.hasRemaining()) {
                    maxX = java.lang.Float.max(bv.get(), maxX)
                    bv.get()
                    bv.get()
                }
                maxX *= board.spatial().scale.x

                box.spatial {
                    scale.x = maxX + padding
                    position = Vector3f(
                        maxX / 2f,
                        box.sizes.y * 0.5f,
                        box.sizes.z * -0.5f - 0.05f
                    )
                    needsUpdate = true
                }
                this.boundingBox = generateBoundingBox()
                width = maxX
                textGeom = board.geometry().vertices
                logger.debug("$name geometry size is ${textGeom.capacity()}")
            }
        }

        thread {
            while (textGeom.capacity() == 0) {
                Thread.sleep(50)
                textGeom = board.geometry().vertices
            }
            updateSize(true)
        }

        initGrabable(box)
    }

    override fun generateBoundingBox(includeChildren: Boolean): OrientedBoundingBox? {
        val vb = board.geometry().vertices.duplicate().rewind()
        if (vb.capacity() == 0) return null

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        while (vb.hasRemaining()) {
            val x = vb.get(); val y = vb.get(); vb.get() // skip Z
            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
            minY = minOf(minY, y)
            maxY = maxOf(maxY, y)
        }

        val scale = board.spatial().scale
        return OrientedBoundingBox(
            this,
            Vector3f(minX * scale.x, minY * scale.y, -thickness),
            Vector3f(maxX * scale.x, maxY * scale.y, 0f)
        )
    }

    override fun getMaximumBoundingBox(): OrientedBoundingBox {
        return box.boundingBox ?: OrientedBoundingBox(this, 0f, 0f, 0f, 0f, 0f, 0f)
    }
}
