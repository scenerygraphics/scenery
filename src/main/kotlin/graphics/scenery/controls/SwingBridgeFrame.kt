package graphics.scenery.controls


import graphics.scenery.utils.Image
import graphics.scenery.utils.SystemHelpers.Companion.logger
import java.awt.Component
import java.awt.event.*
import java.awt.image.BufferedImage
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
    var dragged = false
    init {
        this.addKeyListener(object : KeyListener {
            override fun keyTyped(e : KeyEvent?) {
            }
            override fun keyPressed(e : KeyEvent?) {
                updateImage()
            }
            override fun keyReleased(e : KeyEvent?) {
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

    fun ctrlClick(x:Int, y: Int) {
        SwingUtilities.invokeLater {
            val target = SwingUtilities.getDeepestComponentAt(this.contentPane, x, y)
            val compPoint = SwingUtilities.convertPoint(
                this.contentPane, x, y, target
            )
            // ctrl clicked
            target.dispatchEvent (
                MouseEvent(
                    target, 500, System.currentTimeMillis() - 25, InputEvent.CTRL_DOWN_MASK, compPoint.x, compPoint.y, 1, false, 1
                )
            )
            //pressed
            target.dispatchEvent (
                KeyEvent(this, 401, System.currentTimeMillis(), 0, 0x32, '2')
            )
        }
    }

    fun pressed(x:Int, y: Int) {

        dragged = false
        SwingUtilities.invokeLater {
            val target = SwingUtilities.getDeepestComponentAt(this.contentPane, x, y)
            val compPoint = SwingUtilities.convertPoint(
                this.contentPane, x, y, target
            )
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

            // key pressed
            target.dispatchEvent(
                KeyEvent(this, 401, System.currentTimeMillis(), 0, 0x31, '1')
            )
        }
    }
    fun drag(x: Int, y: Int) {
        dragged = true
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
            //pressed
            target.dispatchEvent(
                KeyEvent(this, 401, System.currentTimeMillis() - 100, 0, 0x31, '1')
            )
        }
    }
    fun released(x:Int, y:Int) {
        logger.info("$dragged")
        SwingUtilities.invokeLater {
            val target = SwingUtilities.getDeepestComponentAt(this.contentPane, x, y)
            val compPoint = SwingUtilities.convertPoint(
                this.contentPane, x, y, target
            )

            //released
            target.dispatchEvent(
                MouseEvent(
                    target, 502, System.currentTimeMillis() - 50, 16, compPoint.x, compPoint.y, 1, false, 1
                )
            )
            if(!dragged)
            {
                logger.info("$dragged")
                //clicked
                target.dispatchEvent(
                    MouseEvent(
                        target, 500, System.currentTimeMillis() - 25, 16, compPoint.x, compPoint.y, 1, false, 1
                    )
                )
            }
            // exited
            /*target.dispatchEvent(
                MouseEvent(
                    target, 505, System.currentTimeMillis(), 0, compPoint.x, compPoint.y, 0, false, 0
                )
            )*/
            //pressed
            target.dispatchEvent(
                KeyEvent(this, 401, System.currentTimeMillis() - 100, 0, 0x31, '1')
            )
        }
    }

    private fun getScreen(): BufferedImage {
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
