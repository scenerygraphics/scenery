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
import javax.swing.border.MatteBorder
import javax.swing.border.TitledBorder
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.filechooser.FileFilter
import kotlin.math.absoluteValue


/**
 * A JPanel to display everything related to setting and editing the color map of a volume.
 *
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 * @author Aryaman Gupta <aryaman.gupta@tu-dresden.de>
 */
class ColormapPanel(val target:Volume?): JPanel() {
    private val colorMapEditor = ColormapEditor(target)
    private val loadedColormaps = HashMap<String, Colormap>()

    init {
        layout = MigLayout("insets 0", "[][][][]")
        val title = object: TitledBorder(MatteBorder(1, 0, 0, 0, Color.GRAY), "Color Map") {
            val customInsets = Insets(25, 0, 25, 0)
            override fun getBorderInsets(c: Component?): Insets {
                return customInsets
            }

            override fun getBorderInsets(c: Component?, insets: Insets?): Insets {
                return customInsets
            }
        }
        border = title

        // color editor
        this.add(colorMapEditor, "spanx, growx, wrap")

        // color map drop down
        val list = Colormap.list()
        val box = JComboBox<String>()
        val selectAColorMapString = "Select ..." // makes codacy stop complaining
        box.addItem(selectAColorMapString)

        for (s in list) {
            box.addItem(s)
        }

        if (target != null) {
            box.selectedItem = selectAColorMapString
            add(box, "grow")
        }

        box.addActionListener {
            val item: String = box.selectedItem as String
            var colormap: Colormap? = null

            if (target != null && item != selectAColorMapString) {
                // try to load from already-seen files first
                colormap = loadedColormaps[item] ?: Colormap.get(item)
                target.colormap = colormap
                this.repaint()
            }

            colormap?.let { colorMapEditor.loadColormap(it) }
        }

        val fc = JFileChooser()
        fc.addChoosableFileFilter(PNGFileFilter())
        fc.isAcceptAllFileFilterUsed = false

        val colormapMenu = JPopupMenu()
        colormapMenu.add(JMenuItem("Load colormap ...").also {
            it.toolTipText = "Load a new colormap from a file"
            it.addActionListener {
                    val returnVal: Int = fc.showOpenDialog(this)
                    if (returnVal == JFileChooser.APPROVE_OPTION) {
                        val newColormap = Colormap.fromPNGFile(fc.selectedFile)
                        val filename = fc.selectedFile.nameWithoutExtension
                        @Suppress("UNCHECKED_CAST")
                        val currentItems = box.items() as List<String>

                        val colormapName = if(filename in currentItems) {
                            println(currentItems.joinToString(","))
                            val index = currentItems.count { n -> n.startsWith(filename) } + 1
                            "$filename ($index)"
                        } else {
                            filename
                        }

                        loadedColormaps[colormapName] = newColormap
                        colorMapEditor.loadColormap(newColormap)
                        box.addItem(colormapName)
                        box.selectedItem = colormapName
                }
            }

        })
        colormapMenu.add(JMenuItem("Save colormap ...").also {
            it.toolTipText = "Save the current colormap to a file"
            it.addActionListener {
                val option = fc.showSaveDialog(this)
                if (option == JFileChooser.APPROVE_OPTION) {
                    saveToFile (fc.selectedFile)
                }
            }
        })

        val colormapMenuButton = JToggleButton("").also { button ->
            button.icon = ImageIcon(ImageIcon(ImageIO.read(this::class.java.getResource("/graphics/scenery/ui/gear.png"))).image.getScaledInstance(16, 16,
                                                                                                                                    java.awt.Image.SCALE_SMOOTH
            ))
            button.toolTipText = "Load a new transfer function and display range"
            button.addActionListener {
                if(button.isSelected) {
                    colormapMenu.show(button, 0, button.height)
                } else {
                    colormapMenu.isVisible = false
                }
            }

            add(button, "skip 2, al right, push")
        }

        colormapMenu.addPopupMenuListener(object: PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
                /* not used */
            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
                colormapMenuButton.isSelected = false
            }

            override fun popupMenuCanceled(e: PopupMenuEvent?) {
                colormapMenuButton.isSelected = false
            }

        })
    }

    private fun JComboBox<*>.items(): List<Any> {
        val items = ArrayList<Any>()
        for(i in 0 until this.itemCount) {
            items.add(this.getItemAt(i))
        }
        return items
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
    class ColormapEditor(var target:Volume? = null) : JPanel() {

        private var colorPoints = listOf(
            ColorPoint(0.0f, Color(0f, 0f, 0f)),
            ColorPoint(0.5f, Color(0f, 0.5f, 0f)),
            ColorPoint(1f, Color(0f, 1f, 0f))
        )

        private var hoveredOver: ColorPoint? = null
        private var dragging: ColorPoint? = null
        private var dragged = false
        private var markerSpace = 10
		
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
                            repaintAndReassign()
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

                            for(i in pointList.indices) {
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
                    repaintAndReassign()
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
                    repaintAndReassign()
                }

                override fun mouseMoved(e: MouseEvent) {
                    val temp = hoveredOver
                    hoveredOver = pointAtMouse(e) // height is the radius of the color point sphere
                    if (temp != hoveredOver) {
                        repaintAndReassign()
                    }
                }
            })
        }

        private fun repaintAndReassign() {
            repaint()

            if(width > 0 && height > 0) {
                target?.colormap = Colormap.fromBuffer(toBuffer(), width, height)
            }
        }

        internal fun toImage(): BufferedImage {
            val rec: Rectangle = this.bounds
            val bufferedImage = BufferedImage(rec.width, rec.height - markerSpace, BufferedImage.TYPE_INT_ARGB)
            paintBackgroundGradient(colorPoints.sortedBy { it.position }, bufferedImage.createGraphics())
            return bufferedImage
        }

        private fun toBuffer(): ByteBuffer {
            return Image.bufferedImageToRGBABuffer(toImage())
        }

        private fun pointAtMouse(e: MouseEvent) =
            colorPoints.firstOrNull { (e.x - (width * it.position)).absoluteValue < height / 2 }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            val w = width
            val h = height
            val pointList = colorPoints.sortedBy { it.position }


            // background Gradient
            //paintBackgroundGradient(pointList, g2d)
            val img = toImage()
            g2d.drawImage(img, 0, 0, this)

            // color point markers
            val relativeSize = 0.25f //relative to height
            val absoluteSize = (relativeSize * h).toInt()

            pointList.forEach {
                val margin = 0.35f

                val innerSize = (absoluteSize * (1.0f - margin)).toInt()
                val p1x = w * it.position
                val colorHSB =  floatArrayOf(0.0f, 0.0f, 0.0f)
                Color.RGBtoHSB(it.color.red, it.color.green, it.color.blue, colorHSB)

                val backgroundDark = colorHSB[2] < 0.5f

                val outlineColor = if(backgroundDark) {
                    Color.BLACK
                } else {
                    Color.WHITE
                }

                // This draws a triangle below the gradient bar to indicate control points
                g2d.paint = outlineColor
                g2d.drawPolygon(intArrayOf(p1x.toInt(), (p1x - innerSize).toInt(), (p1x + innerSize).toInt()),
                                intArrayOf(h - markerSpace, h - 1, h - 1), 3)
                g2d.paint = it.color
                g2d.fillPolygon(intArrayOf(p1x.toInt(), (p1x - innerSize - 1).toInt(), (p1x + innerSize + 1).toInt()),
                                intArrayOf(h - markerSpace - 1, h, h), 3)
            }
        }

        private fun paintBackgroundGradient(
            pointList: List<ColorPoint>,
            g2d: Graphics2D
        ) {
            val w = width
            val h = height - markerSpace
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

            repaintAndReassign()
        }
    }

}
