package graphics.scenery.volumes

import net.miginfocom.swing.MigLayout
import org.joml.Vector3f
import java.awt.*
import javax.swing.JFrame
import javax.swing.JPanel


class ColorMapEditor : JPanel() {

    private val colorPoints = listOf(
        ColorPoint(0.0f, Color(0.2f, 1f, 0f)),
        ColorPoint(0.6f, Color(0.5f, 0f, 0f)),
        ColorPoint(1f, Color(0f, 0f, 0.9f))
    )

    init {
        this.layout = MigLayout()
        this.preferredSize = Dimension(200, preferredSize.height)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        val w = width
        val h = height

        val pointList = colorPoints.sortedBy { it.position }
        for (i in 0 until pointList.size - 1) {
            val p1 = pointList[i]
            val p2 = pointList[i + 1]
            val p1x = w * p1.position
            val p2x = w * p2.position
            val gp = GradientPaint(p1x, 0f, p1.color, p2x, h.toFloat(), p2.color)

            g2d.paint = gp
            g2d.fillRect(p1x.toInt(), 0, p2x.toInt(), h)

        }

        pointList.forEach {
            val p1x = w * it.position
            g2d.paint = Color.gray
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

    private fun Vector3f.toColor(): Color {
        return Color(this.x, this.y, this.z)
    }

    private fun Color.contrast(): Color {
        return Color(
            255 - this.red,
            255 - this.green,
            255 - this.blue
        )
    }

    class ColorPoint(val position: Float, var color: Color)

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
