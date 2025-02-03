package graphics.scenery.ui

import graphics.scenery.Box
import graphics.scenery.RichNode
import graphics.scenery.primitives.TextBoard
import graphics.scenery.utils.extensions.times
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * Text with a Box behind it.
 * @author Jan Tiemann
 */
open class TextBox(
    text: String, var padding: Float = 0.2f, var minSize: Float = 0f,
    final override var height: Float = 1.0f, thickness: Float = 0.5f
) :
    RichNode("TextBox"), Gui3DElement {
    val box = Box(Vector3f(1f, height, thickness))
    val board = TextBoard()

    var text by board::text
    var fontColor by board::fontColor
    var backgroundColor by box.material()::diffuse

    final override var width = 0f
        private set

    init {
        board.text = text
        board.name = "$text TextBox"
        board.transparent = 1
        board.fontColor = Vector4f(0.0f, 0.0f, 0.0f, 1.0f)
        board.spatial {
            scale *= height
            position.y = 0.07f
        }

        box.material().diffuse = Vector3f(0.5f)
        box.spatial {
            position.z = box.sizes.z * -0.5f - 0.05f
        }

        this.addChild(board)
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
                box.generateBoundingBox()
                width = maxX
                textGeom = board.geometry().vertices
            }
        }

        updateSize(true)
        this.update += { updateSize() }

        initGrabable(box)
    }
}
