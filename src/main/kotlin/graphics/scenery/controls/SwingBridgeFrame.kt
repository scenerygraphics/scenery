package graphics.scenery.controls

import graphics.scenery.utils.Image
import java.awt.Component
import java.awt.event.*
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * @author Jan Tiemann
 * @author Konrad Michel
 *
 * Bridge to link the Swing window events with scenery key inputs in combination with SwingUiNode
 */
open class SwingBridgeFrame(title: String) : JFrame(title) {

    val uiNode = SwingUiNode(this)
    var finalImage : Image? = null
    init {
        this.addKeyListener(object : KeyListener {
            override fun keyTyped(e : KeyEvent?) {
                updateImage()
            }
            override fun keyPressed(e : KeyEvent?) {
                updateImage()
            }
            override fun keyReleased(e : KeyEvent?) {
                updateImage()
            }
        })
    }
    private fun updateImage()
    {
        val bimage = this.getScreen()
        val flipped = Image.createFlipped(bimage)
        val buffer = Image.bufferedImageToRGBABuffer(flipped)
        finalImage = Image(buffer, bimage.width, bimage.height)

        uiNode.swingUiDimension = bimage.width to bimage.height
        uiNode.updateUITexture()
    }

    fun click(x: Int, y: Int) {
        SwingUtilities.invokeLater {
            val target = SwingUtilities.getDeepestComponentAt(this.contentPane, x, y)
            val compPoint = SwingUtilities.convertPoint(
                this.contentPane, x, y, target
            )
            println("SwingUI: simulating Click at ${compPoint.x},${compPoint.y} on $target")

            // entered
            target.dispatchEvent(
                MouseEvent(
                    target, 504, System.currentTimeMillis() - 100, 0, compPoint.x, compPoint.y, 0, false, 0
                )
            )
            // pressed
            target.dispatchEvent(
                MouseEvent(
                    target, 501, System.currentTimeMillis() - 75, 1040, compPoint.x, compPoint.y, 1, false, 1
                )
            )
            // released
            target.dispatchEvent(
                MouseEvent(
                    target, 502, System.currentTimeMillis() - 50, 16, compPoint.x, compPoint.y, 1, false, 1
                )
            )
            // clicked
            target.dispatchEvent(
                MouseEvent(
                    target, 500, System.currentTimeMillis() - 25, 16, compPoint.x, compPoint.y, 1, false, 1
                )
            )
            // exited
            target.dispatchEvent(
                MouseEvent(
                    target, 505, System.currentTimeMillis(), 0, compPoint.x, compPoint.y, 0, false, 0
                )
            )

            //pressed
            target.dispatchEvent(
                KeyEvent(this, 401, System.currentTimeMillis() - 100, 0, 0x31, '1')
            )

            //released
            target.dispatchEvent(
                KeyEvent(this, 402, System.currentTimeMillis() - 75, 0, 0x31, '1')
            )

            println("SwingUI: simulating Click after ${compPoint.x},${compPoint.y} on ${(target as? JButton)?.text}")
        }
    }

    fun pressed(x:Int, y: Int) {
        SwingUtilities.invokeLater {
            val target = SwingUtilities.getDeepestComponentAt(this.contentPane, x, y)
            val compPoint = SwingUtilities.convertPoint(
                this.contentPane, x, y, target
            )
            println("SwingUI: simulating mouse Pressed at ${compPoint.x},${compPoint.y} on ${(target as? JButton)?.text}")

            // pressed
            target.dispatchEvent(
                MouseEvent(
                    target, 501, System.currentTimeMillis() - 75, 1040, compPoint.x, compPoint.y, 1, false, 1
                )
            )
        }
    }
    fun drag(x: Int, y: Int) {
        SwingUtilities.invokeLater {
            val target = SwingUtilities.getDeepestComponentAt(this.contentPane, x, y)
            val compPoint = SwingUtilities.convertPoint(
                this.contentPane, x, y, target
            )

            // dragged
            target.dispatchEvent(
                MouseEvent(
                    target, 506, System.currentTimeMillis(), 0, compPoint.x, compPoint.y, 1, false, 1
                )
            )
            println("SwingUI: simulating mouse dragged at ${compPoint.x},${compPoint.y} on ${(target as? JButton)?.text}")
        }

    }
    fun released(x:Int, y:Int) {
        SwingUtilities.invokeLater {
            val target = SwingUtilities.getDeepestComponentAt(this.contentPane, x, y)
            val compPoint = SwingUtilities.convertPoint(
                this.contentPane, x, y, target
            )
            println("SwingUI: simulating release at ${compPoint.x},${compPoint.y} on ${(target as? JButton)?.text}")

            // released
            target.dispatchEvent(
                MouseEvent(
                    target, 502, System.currentTimeMillis() - 50, 16, compPoint.x, compPoint.y, 1, false, 1
                )
            )
        }
    }

    fun getScreen(): BufferedImage {
        return getScreenShot(this.contentPane)
    }

    private fun getScreenShot(
        component: Component
    ): BufferedImage {
        val image = BufferedImage(
            component.width, component.height, BufferedImage.TYPE_INT_RGB
        )
        // call the Component's paint method, using
        // the Graphics object of the image.
        component.paint(image.graphics) // alternately use .printAll(..)
        return image
    }
}
