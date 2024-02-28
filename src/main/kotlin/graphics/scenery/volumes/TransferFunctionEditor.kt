package graphics.scenery.volumes

import net.miginfocom.swing.MigLayout
import org.jfree.chart.ChartMouseEvent
import org.jfree.chart.ChartMouseListener
import org.jfree.chart.ChartPanel
import org.jfree.chart.JFreeChart
import org.jfree.chart.annotations.XYTextAnnotation
import org.jfree.chart.axis.*
import org.jfree.chart.entity.XYItemEntity
import org.jfree.chart.labels.XYToolTipGenerator
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer
import org.jfree.data.xy.XYDataset
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import org.joml.Math.clamp
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.image.BufferedImage
import java.text.NumberFormat
import javax.swing.*


/**
 * @author Konrad Michel <Konrad.Michel@mailbox.tu-dresden.de>
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 *
 * A Swing UI transfer function manipulation tool, able to add, remove and manipulate the transfer function control points of a set volume interactively.
 * Able to generate a histogram and visualize it as well to help with TF-settings
 * Able to dynamically set the transfer function range -> changes histogram as well
 */
class TransferFunctionEditor(
    private val tfContainer: HasTransferFunction
): JPanel() {
    /**
     * MouseDragTarget is set when a ControlPoint has been clicked. The initial index is set to -1 and reset when the Controlpoint has been deleted
     * The target gets passed into the different Controlpoint manipulation functions
     */
    data class MouseDragTarget(
        var seriesIndex: Int = -1,
        var itemIndex: Int = -1,
        var x: Double = 0.0,
        var y: Double = 0.0
    )

    private val mouseTargetCP = MouseDragTarget()

    private val displayRangeEditor = DisplayRangeEditor(tfContainer)
    //TFEditor and Histogram
    val mainChart: JPanel

    private class ValueAlphaTooltipGenerator : XYToolTipGenerator {
        override fun generateToolTip(dataset: XYDataset, series: Int, category: Int): String {
            val x: Number = dataset.getXValue(series, category)
            val y: Number = dataset.getYValue(series, category)
            return String.format("At value %.2f, alpha=%.2f", x, y)
        }
    }


    init {
        layout = MigLayout("", "[][][][]")

        // MainChart manipulation
        val tfCollection = XYSeriesCollection()
        val tfPointSeries = XYSeries("ControlPoints", true, true)

        tfCollection.removeAllSeries()
        tfCollection.addSeries(tfPointSeries)

        val tfPlot = XYPlot()
        tfPlot.setDataset(0, tfCollection)

        val tfRenderer = XYLineAndShapeRenderer()
        tfPlot.setRenderer(0, tfRenderer)

        val axisExtensionFactor = 0.02
        val tfYAxis = NumberAxis()
        tfYAxis.setRange(0.0 - axisExtensionFactor, 1.0 + axisExtensionFactor)
        tfYAxis.tickUnit = NumberTickUnit(0.1)
        tfPlot.setRangeAxis(0, tfYAxis)

        val tfXAxis = NumberAxis()
        tfXAxis.setRange(0.0 - axisExtensionFactor, 1.0 + axisExtensionFactor)
        val units = TickUnits()
        units.add(object: NumberTickUnit(0.1, NumberFormat.getIntegerInstance()) {
            override fun valueToString(value: Double): String {
                val r = displayRangeEditor.getDataRange()
                return super.valueToString(value * (r.second - r.first) + r.first)
            }
        })
        tfXAxis.standardTickUnits = units


        tfPlot.setDomainAxis(0, tfXAxis)
        tfPlot.mapDatasetToRangeAxis(0, 0)
        tfPlot.mapDatasetToDomainAxis(0, 0)
        tfPlot.mapDatasetToRangeAxis(1, 1)
        tfPlot.mapDatasetToDomainAxis(1, 1)
        tfPlot.backgroundAlpha = 0.0f
        tfPlot.backgroundImageAlpha = 0.5f
        tfPlot.backgroundImage = createTFImage()
        tfPlot.rangeAxis.standardTickUnits

        val generator = ValueAlphaTooltipGenerator()
        tfPlot.renderer.defaultToolTipGenerator = generator

        val tfChart = JFreeChart("", tfPlot)
        tfChart.removeLegend()

        mainChart = ChartPanel(tfChart)
        mainChart.preferredSize = Dimension(600, 400)
        mainChart.isMouseWheelEnabled = false
        mainChart.isDomainZoomable = false
        mainChart.isRangeZoomable = false
        mainChart.horizontalAxisTrace = true
        mainChart.verticalAxisTrace = true

        mainChart.minimumDrawWidth = 0
        mainChart.minimumDrawHeight = 0

        mainChart.cursor = Cursor(Cursor.CROSSHAIR_CURSOR)

        add(mainChart, "grow,wrap")

        mainChart.removeMouseMotionListener(mainChart)
        mainChart.addMouseListener(object : MouseListener {
            override fun mouseReleased(e: MouseEvent) {
                mouseTargetCP.itemIndex = -1
            }
            override fun mousePressed(e: MouseEvent) {/*noop*/}
            override fun mouseClicked(e: MouseEvent) {/*noop*/}
            override fun mouseEntered(e: MouseEvent) {/*noop*/}
            override fun mouseExited(e: MouseEvent) {/*noop*/}
        })

        var lastUpdate = 0L
        mainChart.addMouseMotionListener(object : MouseMotionListener {
            override fun mouseDragged(e: MouseEvent) {
                val chart = e.component as ChartPanel
                val point = mainChart.translateJava2DToScreen(e.point)
                val item = chart.getEntityForPoint(point.x, point.y)
                //first check, if the clicked entity is part of the chart
                if (item is XYItemEntity) {
                    //then check, if it's part of the transferFunction (being a control point)
                    if (item.dataset is XYSeriesCollection) {
                        mouseTargetCP.seriesIndex = item.seriesIndex
                        mouseTargetCP.itemIndex = item.item
                    }
                }
                //if the drag is performed while the current target is indeed set to be a CP, update it
                if (mouseTargetCP.itemIndex >= 0) {
                    val screenPoint = mainChart.translateJava2DToScreen(e.point)
                    val plotArea = mainChart.chartRenderingInfo.plotInfo.dataArea
                    mouseTargetCP.x =
                        tfPlot.getDomainAxis(0).java2DToValue(screenPoint.getX(), plotArea, tfPlot.domainAxisEdge)
                    mouseTargetCP.y = tfPlot.getRangeAxis(0).java2DToValue(screenPoint.getY(), plotArea, tfPlot.rangeAxisEdge)

                    val annotation = XYTextAnnotation(
                        "%.2f / %.2f".format(mouseTargetCP.x.toFloat(), mouseTargetCP.y.toFloat()),
                        mouseTargetCP.x,
                        mouseTargetCP.y-0.04) //offset so the label does not hide the target
                    annotation.backgroundPaint = Color.white
                    annotation.paint = Color.darkGray
                    tfPlot.clearAnnotations()
                    tfPlot.addAnnotation(annotation)

                    updateControlpoint(mouseTargetCP)
                    if (System.currentTimeMillis() - 16.667 >= lastUpdate) {
                        tfPlot.backgroundImage = createTFImage()
                        lastUpdate = System.currentTimeMillis()
                    }
                }
            }
            override fun mouseMoved(e: MouseEvent) {/*noop*/}
        })
        mainChart.addChartMouseListener(object : ChartMouseListener {
            override fun chartMouseClicked(e: ChartMouseEvent) {
                if (e.entity is XYItemEntity) {
                    val item = e.entity as XYItemEntity
                    //click on cp
                    if (item.dataset is XYSeriesCollection) {
                        mouseTargetCP.seriesIndex = item.seriesIndex
                        mouseTargetCP.itemIndex = item.item
                        mouseTargetCP.x = clamp(0.0, 1.0, item.dataset.getX(item.seriesIndex, item.item).toDouble())
                        mouseTargetCP.y = clamp(0.0, 1.0, item.dataset.getY(item.seriesIndex, item.item).toDouble())

                        if ((e.trigger.clickCount > 1 || e.trigger.isControlDown) && mouseTargetCP.itemIndex != -1) {
                            removeControlpoint(mouseTargetCP)
                            tfPlot.backgroundImage = createTFImage()
                        }
                        return
                    }
                }

                //click on graph or  empty region
                val point = mainChart.translateJava2DToScreen(e.trigger.point)
                val plotArea = mainChart.chartRenderingInfo.plotInfo.dataArea
                mouseTargetCP.x = clamp(
                    0.0,
                    1.0,
                    tfPlot.getDomainAxis(0).java2DToValue(point.getX(), plotArea, tfPlot.domainAxisEdge)
                )
                mouseTargetCP.y = clamp(
                    0.0,
                    1.0,
                    tfPlot.getRangeAxis(0).java2DToValue(point.getY(), plotArea, tfPlot.rangeAxisEdge)
                )

                if (mouseTargetCP.itemIndex == -1) {
                    addControlpoint(mouseTargetCP)
                    tfPlot.backgroundImage = createTFImage()
                }

            }
            override fun chartMouseMoved(e: ChartMouseEvent) {/*noop*/}
        })

        val histAndTFIOButtonsPanel = JPanel()
        histAndTFIOButtonsPanel.layout = MigLayout("insets 0", "[][][][]")
        add(histAndTFIOButtonsPanel, "growx,wrap")

        val histogramChartManager = HistogramChartManager(tfPlot,mainChart,tfContainer,axisExtensionFactor)
        histAndTFIOButtonsPanel.add(histogramChartManager.genHistButton, "growx")

        // transfer function IO
        val fc = JFileChooser()
        JButton("Load").also {
            it.toolTipText = "Load a new transfer function"
            it.addActionListener {
                val returnVal: Int = fc.showOpenDialog(this)
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    tfContainer.loadTransferFunctionFromFile(file = fc.selectedFile)
                    initTransferFunction(tfContainer.transferFunction)
                    displayRangeEditor.refreshDisplayRange()
                }
            }
            histAndTFIOButtonsPanel.add(it, "skip 2, al right, push")
        }
        JButton("Save").also {
            it.toolTipText = "Save the current transfer function to a file"
            it.addActionListener {
                val option = fc.showSaveDialog(this)
                if (option == JFileChooser.APPROVE_OPTION) {
                    tfContainer.saveTransferFunctionToFile(fc.selectedFile)
                }
            }
            histAndTFIOButtonsPanel.add(it, "al right, wrap")
        }
        initTransferFunction(tfContainer.transferFunction)


        add(displayRangeEditor, "grow,wrap")
        add(ColormapPanel(tfContainer as? Volume), "grow,wrap")
    }

    private fun createTFImage(): BufferedImage {
        val tfBuffer = tfContainer.transferFunction.serialise().asFloatBuffer()
        val byteArray = ByteArray(tfBuffer.limit())
        for (i in 0 until tfBuffer.limit()) {
            byteArray[i] = (tfBuffer[i] * 255).toUInt().toByte()
        }
        val tfImage = BufferedImage(
            tfContainer.transferFunction.textureSize,
            tfContainer.transferFunction.textureHeight,
            BufferedImage.TYPE_BYTE_GRAY
        )
        tfImage.raster.setDataElements(
            0,
            0,
            tfContainer.transferFunction.textureSize,
            tfContainer.transferFunction.textureHeight,
            byteArray
        )

        return tfImage
    }

    private fun addControlpoint(targetCP: MouseDragTarget) {
        val chart = mainChart as ChartPanel
        val collection = chart.chart.xyPlot.getDataset(0) as XYSeriesCollection
        val series = collection.getSeries("ControlPoints")

        series.add(
            targetCP.x.toFloat(), targetCP.y.toFloat()
        )
        regenerateTF(series)
    }

    private fun initTransferFunction(transferFunction: TransferFunction){
        val chart = mainChart as ChartPanel
        val collection = chart.chart.xyPlot.getDataset(0) as XYSeriesCollection
        val series = collection.getSeries("ControlPoints")
        series.clear()

        var points = transferFunction.controlPoints().map { it.value to it.factor }

        // add first and last point if not there
        if ((points.firstOrNull()?.first ?: 1f) > 0.0f){
            points = listOf(0f to 0f) + points
        }
        if ((points.lastOrNull()?.first ?: 0f) < 1.0f){
            points = listOf(1f to 1f) + points
        }

        points.forEach {
            series.add(it.first,it.second)
        }
    }

    private fun updateControlpoint(targetCP: MouseDragTarget) {
        val chart = mainChart as ChartPanel
        val collection = chart.chart.xyPlot.getDataset(0) as XYSeriesCollection
        val series = collection.getSeries(targetCP.seriesIndex)

        // dont move point past other points
        val epsilon = 0.005
        val minX = if (targetCP.itemIndex > 0) {
            val prev = series.getDataItem(targetCP.itemIndex - 1)
            prev.x.toDouble() + epsilon
        } else {
            0.0
        }
        val maxX = if (targetCP.itemIndex < series.itemCount-1) {
            val prev = series.getDataItem(targetCP.itemIndex + 1)
            prev.x.toDouble() - epsilon
        } else {
            1.0
        }

        targetCP.x = clamp(minX, maxX, targetCP.x)
        targetCP.y = clamp(0.0, 1.0, targetCP.y)

        series.remove(targetCP.itemIndex)
        series.add(targetCP.x, targetCP.y)

        regenerateTF(series)
    }

    private fun removeControlpoint(targetCP: MouseDragTarget) {
        val chart = mainChart as ChartPanel
        val collection = chart.chart.xyPlot.getDataset(0) as XYSeriesCollection
        val series = collection.getSeries(targetCP.seriesIndex)


        series.remove(targetCP.itemIndex)
        regenerateTF(series)

        targetCP.itemIndex = -1
    }

    private fun regenerateTF(series: XYSeries) {
        val newTF = TransferFunction()
        for (i in 0 until series.itemCount) {
            newTF.addControlPoint(series.getX(i).toFloat(), series.getY(i).toFloat())
        }
        tfContainer.transferFunction = newTF
    }

    companion object{
        fun showTFFrame(tfContainer: HasTransferFunction, volumeName: String = "Volume"){
            val frame = JFrame()
            frame.title = "$volumeName transfer function"
            val tfe = TransferFunctionEditor(tfContainer)
            frame.add(tfe)
            frame.pack()
            frame.isVisible = true
        }
    }
}
