package graphics.scenery.ui


import graphics.scenery.utils.Image
import java.awt.Component
import java.awt.Dimension
import java.awt.Panel
import java.awt.event.*
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 * @author Konrad Michel <Konrad.Michel@mailbox.tu-dresden.de>
 *
 * Bridge to link the Swing window events with scenery key inputs in combination with SwingUiNode
 */
open class SwingBridgeFrame(title: String) : JFrame(title) {

    val uiNode = SwingUINode(this)

    private var dragged = false
    private var lastUpdate = 0L
    private var snapshotBuffer : ByteBuffer? = null
    var snapshotImage : Image? = null

    /**
     * Init function is used to add a KeyListener to trigger snapshot recreation of the SwingFrame in order to update the texture presented on the UI-Plane
     * inside the scene
     */
    init {

        //only keyPressed triggers an imageUpdate -> Image updates are slow, and dragging happens per tick -> the image update routine starts to lag when
        //the mouse is dragged over the UI
        //the last update check happens to impede tick wise texture updates. Ones per frame is enough
        this.addKeyListener(object : KeyListener {

            override fun keyPressed(e : KeyEvent?) {
                if(System.currentTimeMillis() - 16.667 >= lastUpdate)
                {
                    updateImage()
                    lastUpdate = System.currentTimeMillis()
                }
            }

            override fun keyTyped(e : KeyEvent?) {}
            override fun keyReleased(e : KeyEvent?) {}
        })
    }

    /**
     * This add function wraps frame.add.
     * It also packs the [panel] and makes it visible.
     */
    fun addPanel(panel: JPanel) {
        this.add(panel)
        this.pack()
        this.isVisible = true
    }

    /**
     * Creates a snapshot of the actual SwingFrame and converts it to a ByteBuffer. Then updates the texture of the 2DPlane that renders in the scene to present
     * the UI
     */
    private fun updateImage()
    {
        val bimage = getScreen()
        val flipped = Image.createFlipped(bimage)
        snapshotBuffer = Image.bufferedImageToRGBABuffer(flipped)
        snapshotImage = Image(snapshotBuffer!!, bimage.width, bimage.height)

        uiNode.swingUiDimension = bimage.width to bimage.height
        uiNode.updateUITexture()
    }

    /**
     * dispatches a mouse click event to the swing window the mouse is currently hovering over modified with a ctrl_down_mask to perform a Ctrl+MouseButton1
     * Also dispatches a key('1')-pressed event to trigger a texture-update for the Plane that renders the SwingUI
     */
    fun ctrlClick(x:Int, y: Int) {
        SwingUtilities.invokeLater {
            val target = SwingUtilities.getDeepestComponentAt(this.contentPane, x, y)
            val compPoint = SwingUtilities.convertPoint(this.contentPane, x, y, target)
            // ctrl clicked
            target.dispatchEvent (
                MouseEvent(
                    target, 500, System.currentTimeMillis() - 25, InputEvent.CTRL_DOWN_MASK, compPoint.x, compPoint.y, 1, false, 1
                )
            )
            //key pressed
            target.dispatchEvent (
                KeyEvent(this, 401, System.currentTimeMillis(), 0, 0x32, '2')
            )
        }
    }

    /**
     * dispatches a mouse entered event, a press event and a key('1')-pressed event to trigger a texture-update for the Plane that renders the SwingUI
     */
    fun pressed(x:Int, y: Int) {

        dragged = false
        lastUpdate = System.currentTimeMillis()

        SwingUtilities.invokeLater {
            val target = SwingUtilities.getDeepestComponentAt(this.contentPane, x, y)
            val compPoint = SwingUtilities.convertPoint(this.contentPane, x, y, target)
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
    /**
     * dispatches a mouse drag event and a key('1')-pressed event to trigger a texture-update for the Plane that renders the SwingUI
     */
    fun drag(x: Int, y: Int) {
        dragged = true

        SwingUtilities.invokeLater {
            val target = SwingUtilities.getDeepestComponentAt(this.contentPane, x, y)
            val compPoint = SwingUtilities.convertPoint(this.contentPane, x, y, target)

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
    /**
     * dispatches a mouse released event, a mouse click event if no mouse drag happened between pressed and released and a key('1')-pressed event to trigger a texture-update for the Plane that renders the SwingUI
     */
    fun released(x:Int, y:Int) {
        lastUpdate = 0
        SwingUtilities.invokeLater {
            val target = SwingUtilities.getDeepestComponentAt(this.contentPane, x, y)
            val compPoint = SwingUtilities.convertPoint(this.contentPane, x, y, target)

            //released
            target.dispatchEvent(
                MouseEvent(
                    target, 502, System.currentTimeMillis() - 50, 16, compPoint.x, compPoint.y, 1, false, 1
                )
            )
            if(!dragged)
            {
                //clicked
                target.dispatchEvent(
                    MouseEvent(
                        target, 500, System.currentTimeMillis() - 25, 16, compPoint.x, compPoint.y, 1, false, 1
                    )
                )
            }
            // exited - currently disabled, may cause issues with switching window focuses
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

    private fun getScreen() : BufferedImage {
        return getScreenShot(this.contentPane)
    }

    private fun getScreenShot(component: Component) : BufferedImage {
        val image = BufferedImage(component.width, component.height, BufferedImage.TYPE_INT_RGB)
        // call the Component's paint method, using
        // the Graphics object of the image.
        component.paint(image.graphics) // alternately use .printAll(..)
        return image
    }
}
