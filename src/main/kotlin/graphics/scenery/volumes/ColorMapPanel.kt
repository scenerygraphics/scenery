package graphics.scenery.volumes

import graphics.scenery.utils.Image
import net.miginfocom.swing.MigLayout
import org.apache.commons.io.FilenameUtils
import org.joml.Math.clamp
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.filechooser.FileFilter
import kotlin.math.absoluteValue

/**
 * A JPanel to display everything related to setting and editing the color map of a volume.
 *
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 * @author Aryaman Gupta <aryaman.gupta@tu-dresden.de>
 */
class ColorMapPanel(val target:Volume?): JPanel() {
    private val colorMapEditor = ColorMapEditor(target)

    init {
        layout = MigLayout()
        val title = BorderFactory.createTitledBorder("Color Map")
        border = title

        // color editor
        this.add(colorMapEditor, "spanx, growx, wrap")

        // color map drop down
        val list = Colormap.list()
        val box = JComboBox<String>()
        val selectAColorMapString = "Select a colormap" // makes codacy stop complaining
        box.addItem(selectAColorMapString)

        for (s in list)
            box.addItem(s)

        if (target != null) {
            box.selectedItem = selectAColorMapString
            add(box, "grow")
        }

        box.addActionListener {
            val item: String = box.selectedItem as String
            if (target != null && item != selectAColorMapString) {
                target.colormap = Colormap.get(item)
                this.repaint()
            }
            colorMapEditor.loadColormap(Colormap.get(item))
        }

        val fc = JFileChooser()
        fc.addChoosableFileFilter(PNGFileFilter())
        fc.isAcceptAllFileFilterUsed = false

        JButton("Load Color Map").also {
            it.addActionListener {
                val returnVal: Int = fc.showOpenDialog(this)
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    colorMapEditor.loadColormap(Colormap.fromPNGFile(fc.selectedFile))
                }
            }
            add(it)
        }

        JButton("Save Color Map").also {
            it.addActionListener {
                val option = fc.showSaveDialog(this)
                if (option == JFileChooser.APPROVE_OPTION) {
                    saveToFile (fc.selectedFile)
                }
            }
            add(it,"wrap")
        }
    }

    /**
     * A filter to only select pngs.
     */
    private class PNGFileFilter: FileFilter()
    {
        override fun accept(f: File): Boolean {
            if (f.isDirectory()) {
                return false
            }
            if (f.extension != "png") return false
            return true
        }

        override fun getDescription(): String {
            return "png"
        }
    }

    /**
     * Save a color map to file as a png.
     */
    fun saveToFile(file: File){
        var fileTemp = file
        if (FilenameUtils.getExtension(file.name).equals("png", ignoreCase = true)) {
            // filename is OK as-is
        } else {
            fileTemp = File("$file.png") // append .png if "foo.jpg.png" is OK
        }

        try {
            ImageIO.write(colorMapEditor.toImage(), "png", fileTemp)
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }
    }
    /**
     * A GUI element to allow users to visually create or modify a color map
     */
    class ColorMapEditor(var target:Volume? = null) : JPanel() {

        private var colorPoints = listOf(
            ColorPoint(0.0f, Color(0f, 0f, 0f)),
            ColorPoint(0.5f, Color(0f, 0.5f, 0f)),
            ColorPoint(1f, Color(0f, 1f, 0f))
        )

        private var hoveredOver: ColorPoint? = null
        private var dragging: ColorPoint? = null
        private var dragged = false

        init {
            this.layout = MigLayout()
            this.preferredSize = Dimension(1000, 40)

            target?.let { loadColormap(it.colormap) }

            this.addMouseListener(object : MouseListener {
                override fun mouseClicked(e: MouseEvent) {
                    val point = pointAtMouse(e)
                    when {
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
                            val pos = e.x/ width.toFloat()
                            val pointList = colorPoints.sortedBy { it.position }

                            var red = 0f
                            var green = 0f
                            var blue = 0f

                            for(i in 0 until pointList.size) {
                                if(pointList[i].position <= pos && pointList[i+1].position >= pos) {
                                    val interpolationFactor = (pos - pointList[i].position) / (pointList[i+1].position - pointList[i].position)

                                    red = pointList[i].color.red.toFloat()/255.0f + interpolationFactor * (pointList[i+1].color.red.toFloat()/255.0f - pointList[i].color.red.toFloat()/255.0f)
                                    green = pointList[i].color.green.toFloat()/255.0f + interpolationFactor * (pointList[i+1].color.green.toFloat()/255.0f - pointList[i].color.green.toFloat()/255.0f)
                                    blue = pointList[i].color.blue.toFloat()/255.0f + interpolationFactor * (pointList[i+1].color.blue.toFloat()/255.0f - pointList[i].color.blue.toFloat()/255.0f)
                                    break
                                }
                            }

                            val color = Color(red, green, blue)
                            colorPoints += ColorPoint((e.x / width.toFloat()), color)
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

                override fun mouseEntered(e: MouseEvent?) {/*noop*/}
                override fun mouseExited(e: MouseEvent?) {/*noop*/}
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

        internal fun toImage(): BufferedImage {
            val rec: Rectangle = this.bounds
            val bufferedImage = BufferedImage(rec.width, rec.height, BufferedImage.TYPE_INT_ARGB)
            paintBackgroundGradient(colorPoints.sortedBy { it.position }, bufferedImage.graphics as Graphics2D)
            return bufferedImage
        }

        private fun toBuffer(): ByteBuffer {
            return Image.bufferedImageToRGBABuffer(toImage())
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
            val relativeSize = 0.5f //relative to height
            val absoluteSize = (relativeSize * h).toInt()
            val margin = 0.05f
            val innerSize = (absoluteSize * (1f - margin)).toInt()

            pointList.forEach {
                val p1x = w * it.position
                g2d.paint = if (it == hoveredOver) Color.white else Color.BLACK
                g2d.fillOval(p1x.toInt() - absoluteSize / 2, (h-absoluteSize)/2, absoluteSize, absoluteSize)

                g2d.paint = it.color
                g2d.fillOval((p1x - innerSize / 2).toInt(), (h - innerSize) / 2, innerSize, innerSize)
            }
            target?.let {
                it.colormap = Colormap.fromBuffer(toBuffer(), width, height)
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

        private class ColorPoint(var position: Float, var color: Color)

        /**
         * Loads a [Colormap] into the editor.
         */
        fun loadColormap(colormap: Colormap) {
            val width = colormap.width
            val numPoints = 10.coerceAtMost(width)

            val sampleDistance = 1.0f/(numPoints - 2)

            var colorPointsList: List<ColorPoint> = listOf()

            //first sample
            var sample = colormap.sample(0f)
            colorPointsList = colorPointsList + ColorPoint(0f, Color(sample.x, sample.y, sample.z, sample.w))

            //middle samples
            for(i in 1..numPoints-2) {
                sample = colormap.sample(i*sampleDistance)
                colorPointsList = colorPointsList + ColorPoint(i*sampleDistance, Color(sample.x, sample.y, sample.z, sample.w))
            }

            //last sample
            sample = colormap.sample(1f)
            colorPointsList = colorPointsList + ColorPoint(1f, Color(sample.x, sample.y, sample.z, sample.w))

            colorPoints = colorPointsList

            repaint()
        }
    }

}
