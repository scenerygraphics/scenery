package graphics.scenery.volumes

import net.miginfocom.swing.MigLayout
import org.joml.Math.clamp
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.JColorChooser
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.absoluteValue


/**
 * A Gui element to allow users to visually create or modify a color map
 */
class ColorMapEditor : JPanel() {

    var colorPoints = listOf(
        ColorPoint(0.0f, Color(0.2f, 1f, 0f)),
        ColorPoint(0.6f, Color(0.5f, 0f, 0f)),
        ColorPoint(1f, Color(0f, 0f, 0.9f))
    )

    private var hoveredOver: ColorPoint? = null
    private var dragging: ColorPoint? = null
    private var dragged = false

    init {
        this.layout = MigLayout()
        this.preferredSize = Dimension(1000, 40)

        this.addMouseListener(object : MouseListener {
            override fun mouseClicked(e: MouseEvent) {
                val point = pointAtMouse(e)
                when {
                    e.isControlDown -> {

                        try {
                            // Create temp file
                            val temp = File.createTempFile("screenshot", ".png")

                            // Use the ImageIO API to write the bufferedImage to a temporary file
                            ImageIO.write(toImage(), "png", temp)

                            // Delete temp file when program exits
                            //temp.deleteOnExit()
                        } catch (ioe: IOException) {
                            ioe.printStackTrace()
                        }
                    }

                    SwingUtilities.isLeftMouseButton(e) && point != null && !dragged -> {
                        point.color =
                            JColorChooser.showDialog(null, "Choose a color for point", point.color) ?: point.color
                        repaint()
                    }

                    SwingUtilities.isRightMouseButton(e) && point != null -> {
                        if (0f < point.position && point.position < 1.0f) {
                            // dont delete first and last point
                            colorPoints = colorPoints - point
                        }
                    }

                    point == null -> {
                        val color =
                            JColorChooser.showDialog(null, "Choose a color for new point", Color(0.5f, 0.5f, 0.5f))
                        color?.let {
                            colorPoints += ColorPoint((e.x / width.toFloat()), color)
                        }
                    }
                }
                repaint()
            }

            override fun mousePressed(e: MouseEvent) {
                val cp = pointAtMouse(e)
                cp?.let {
                    if (0f < cp.position && cp.position < 1.0f) {
                        // dont move first and last point
                        dragging = cp
                    }
                }
                dragged = false
            }

            override fun mouseReleased(e: MouseEvent?) {
                dragging = null
            }

            override fun mouseEntered(e: MouseEvent?) {}
            override fun mouseExited(e: MouseEvent?) {}
        })

        this.addMouseMotionListener(object : MouseMotionListener {
            override fun mouseDragged(e: MouseEvent) {
                dragging?.position = clamp(0.05f, 0.95f, e.x / width.toFloat())
                dragged = true
                repaint()
            }

            override fun mouseMoved(e: MouseEvent) {
                val temp = hoveredOver
                hoveredOver = pointAtMouse(e) // height is the radius of the color point sphere
                if (temp != hoveredOver) repaint()
            }
        })
    }

    fun toImage(): BufferedImage {
        val rec: Rectangle = this.bounds
        val bufferedImage = BufferedImage(rec.width, rec.height, BufferedImage.TYPE_INT_ARGB)
        paintBackgroundGradient(colorPoints.sortedBy { it.position }, bufferedImage.graphics as Graphics2D)
        return bufferedImage
    }

    private fun pointAtMouse(e: MouseEvent) =
        colorPoints.firstOrNull() { (e.x - (width * it.position)).absoluteValue < height / 2 }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        val w = width
        val h = height
        val pointList = colorPoints.sortedBy { it.position }

        // background Gradient
        paintBackgroundGradient(pointList, g2d)

        // color point markers
        pointList.forEach {
            val p1x = w * it.position
            g2d.paint = if (it == hoveredOver) Color.white else Color.BLACK
            g2d.fillOval(p1x.toInt() - h / 2, 0, h, h)

            val margin = 0.05f
            val innerSize = h * (1f - margin)
            g2d.paint = it.color
            g2d.fillOval(
                (p1x - innerSize / 2).toInt(),
                (h * margin * 0.5f).toInt(),
                innerSize.toInt(),
                innerSize.toInt()
            )
        }
    }

    private fun paintBackgroundGradient(
        pointList: List<ColorPoint>,
        g2d: Graphics2D
    ) {
        val w = width
        val h = height
        for (i in 0 until pointList.size - 1) {
            val p1 = pointList[i]
            val p2 = pointList[i + 1]
            val p1x = w * p1.position
            val p2x = w * p2.position
            val gp = GradientPaint(p1x, 0f, p1.color, p2x, 0f, p2.color)

            g2d.paint = gp
            g2d.fillRect(p1x.toInt(), 0, p2x.toInt(), h)
        }
    }

    class ColorPoint(var position: Float, var color: Color)

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val frame = JFrame()
            frame.preferredSize = Dimension(500, 200)

            frame.title = " function"
            val tfe = ColorMapEditor()
            frame.add(tfe)
            frame.pack()
            frame.isVisible = true
        }
    }
}
