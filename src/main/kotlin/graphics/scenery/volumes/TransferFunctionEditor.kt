package graphics.scenery.volumes

import bdv.tools.brightness.ConverterSetup
import graphics.scenery.ui.RangeSlider
import graphics.scenery.ui.SwingBridgeFrame
import net.imglib2.histogram.Histogram1d
import net.imglib2.histogram.Real1dBinMapper
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.miginfocom.swing.MigLayout
import org.jfree.chart.ChartMouseEvent
import org.jfree.chart.ChartMouseListener
import org.jfree.chart.ChartPanel
import org.jfree.chart.JFreeChart
import org.jfree.chart.axis.LogarithmicAxis
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.axis.NumberTickUnit
import org.jfree.chart.entity.XYItemEntity
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.StandardXYBarPainter
import org.jfree.chart.renderer.xy.XYBarRenderer
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer
import org.jfree.data.statistics.SimpleHistogramBin
import org.jfree.data.statistics.SimpleHistogramDataset
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import org.joml.Math.clamp
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.image.BufferedImage
import java.nio.ByteOrder
import javax.swing.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt


/**
 * @author Konrad Michel <Konrad.Michel@mailbox.tu-dresden.de>
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 *
 * A Swing UI transfer function manipulation tool, able to add, remove and manipulate the transfer function control points of a set volume interactively.
 * Able to generate a histogram and visualize it as well to help with TF-settings
 * Able to dynamically set the transfer function range -> changes histogram as well
 */
class TransferFunctionEditor(width : Int = 1000, height : Int = 1000, val volume : Volume, val mainFrame : SwingBridgeFrame = SwingBridgeFrame("1DTransferFunctionEditor")) {
    /**
     * MouseDragTarget is set when a ControlPoint has been clicked. The initial index is set to -1 and reset when the Controlpoint has been deleted
     * The target gets passed into the different Controlpoint manipulation functions
     */
    data class MouseDragTarget(var seriesIndex : Int = -1, var itemIndex : Int = -1, var lastIndex : Int = -1, var x : Double = 0.0, var y : Double = 0.0)
    private var converter : ConverterSetup

    private val mouseTargetCP = MouseDragTarget()

    //TFEditor and Histogram
    val mainChart : JPanel
    private val histogramInfoPanel : JPanel

    //AddCP
    private val cpManipulationPanel : JPanel

    private val valueLabel : JLabel
    private val alphaLabel : JLabel

    //RangeEditor
    private val rangeEditorPanel : JPanel
    private val minText: JTextField
    private val maxText: JTextField
    private val rangeSlider : RangeSlider
    private val minValueLabel: JLabel
    private val maxValueLabel: JLabel


    init {
        mainFrame.size = Dimension(width, height)
        mainFrame.preferredSize = Dimension(width, height)
        mainFrame.minimumSize = Dimension(width, height)
        mainFrame.layout = MigLayout()
        mainFrame.isVisible = true


        converter = volume.converterSetups.first()

        // MainChart manipulation
        val tfCollection = XYSeriesCollection()
        val tfPointSeries = XYSeries("ControlPoints", true, true)

        tfCollection.removeAllSeries()
        tfCollection.addSeries(tfPointSeries)

        val tfPlot = XYPlot()
        tfPlot.setDataset(0, tfCollection)

        val tfRenderer = XYLineAndShapeRenderer()
        tfPlot.setRenderer(0, tfRenderer)

        val histogramRenderer = XYBarRenderer()
        histogramRenderer.barPainter = StandardXYBarPainter()
        histogramRenderer.isDrawBarOutline = true
        tfPlot.setRenderer(1, histogramRenderer)

        val histXAxis = NumberAxis()
        var range = abs(converter.displayRangeMax - converter.displayRangeMin)
        val axisExtensionFactor = 0.02
        histXAxis.setRange(converter.displayRangeMin - (axisExtensionFactor*range), converter.displayRangeMax + (axisExtensionFactor*range))

        val histogramAxis = LogarithmicAxis("")
        histogramAxis.isMinorTickMarksVisible = true
        val histHeight = abs(1000.0 - 0.0)
        histogramAxis.setRange(0.0 - (axisExtensionFactor/100.0 * histHeight), 1000.0 + (axisExtensionFactor * histHeight))
        histogramAxis.allowNegativesFlag

        val tfYAxis = NumberAxis()
        tfYAxis.setRange(0.0 - axisExtensionFactor, 1.0 + axisExtensionFactor)
        tfYAxis.tickUnit = NumberTickUnit(0.1)
        tfPlot.setRangeAxis(0, tfYAxis)

        val tfXAxis = NumberAxis()
        tfXAxis.setRange(0.0 - axisExtensionFactor, 1.0 + axisExtensionFactor)
        tfPlot.setDomainAxis(0, tfXAxis)
        tfPlot.mapDatasetToRangeAxis(0, 0)
        tfPlot.mapDatasetToDomainAxis(0, 0)
        tfPlot.mapDatasetToRangeAxis(1, 1)
        tfPlot.mapDatasetToDomainAxis(1, 1)
        tfPlot.backgroundAlpha = 0.0f
        tfPlot.backgroundImageAlpha = 1.0f
        tfPlot.backgroundImage = createTFImage()

        val tfChart = JFreeChart("TransferFunction for ${volume.name}", tfPlot)
        tfChart.removeLegend()

        mainChart = ChartPanel(tfChart)
        mainChart.preferredSize = Dimension(600, 400)
        mainChart.minimumSize = mainChart.preferredSize
        mainChart.isMouseWheelEnabled = false
        mainChart.isDomainZoomable = false
        mainChart.isRangeZoomable = false
        mainChart.horizontalAxisTrace = true
        mainChart.verticalAxisTrace = true

        mainFrame.add(mainChart, "cell 0 0 12 8")

        mainChart.removeMouseMotionListener(mainChart)
        mainChart.addMouseListener(object : MouseListener {
            override fun mouseReleased(e : MouseEvent) {
                mouseTargetCP.itemIndex = -1
            }
            override fun mousePressed(e : MouseEvent) {}
            override fun mouseClicked(e : MouseEvent) {}
            override fun mouseEntered(e : MouseEvent) {}
            override fun mouseExited(e : MouseEvent) {}
        })

        valueLabel = JLabel("Value: 0,00")
        valueLabel.minimumSize = Dimension(100, 25)
        valueLabel.maximumSize = Dimension(100, 25)
        alphaLabel = JLabel("Alpha: 0,00")
        alphaLabel.minimumSize = Dimension(100, 25)
        alphaLabel.maximumSize = Dimension(100, 25)

        var lastUpdate = 0L
        mainChart.addMouseMotionListener(object : MouseMotionListener {
            override fun mouseDragged(e : MouseEvent) {
                val chart = e.component as ChartPanel
                val point = mainChart.translateJava2DToScreen(e.point)
                val item = chart.getEntityForPoint(point.x, point.y)
                //first check, if the clicked entity is part of the chart
                if(item is XYItemEntity)
                {
                    //then check, if it's part of the transferFunction (being a control point)
                    if (item.dataset is XYSeriesCollection) {

                        mouseTargetCP.seriesIndex = item.seriesIndex
                        mouseTargetCP.itemIndex = item.item
                        mouseTargetCP.lastIndex = item.item
                    }
                }
                //if the drag is performed while the current target is indeed set to be a CP, update it
                if(mouseTargetCP.itemIndex >= 0)
                {
                    val point = mainChart.translateJava2DToScreen(e.point)
                    val plotArea = mainChart.screenDataArea
                    mouseTargetCP.x = tfPlot.getDomainAxis(0).java2DToValue(point.getX(), plotArea, tfPlot.domainAxisEdge)
                    mouseTargetCP.y = tfPlot.getRangeAxis(0).java2DToValue(point.getY(), plotArea, tfPlot.rangeAxisEdge)
                    valueLabel.text = "Value: "+ "%.2f".format(mouseTargetCP.x.toFloat())
                    alphaLabel.text = "Alpha: "+ "%.2f".format(mouseTargetCP.y.toFloat())

                    updateControlpoint(volume, mouseTargetCP)
                    if(System.currentTimeMillis() - 16.667 >= lastUpdate)
                    {
                        tfPlot.backgroundImage = createTFImage()
                        lastUpdate = System.currentTimeMillis()
                    }
                }
            }
            override fun mouseMoved(e : MouseEvent) {}
        })


        mainChart.addChartMouseListener(object : ChartMouseListener {
            override fun chartMouseClicked(e: ChartMouseEvent) {
                if(e.entity is XYItemEntity) {
                    val item = e.entity as XYItemEntity
                    //click on cp
                    if (item.dataset is XYSeriesCollection) {
                        mouseTargetCP.seriesIndex = item.seriesIndex
                        mouseTargetCP.itemIndex = item.item
                        mouseTargetCP.lastIndex = item.item
                        mouseTargetCP.x = clamp(0.0, 1.0, item.dataset.getX(item.seriesIndex, item.item).toDouble())
                        mouseTargetCP.y = clamp(0.0, 1.0, item.dataset.getY(item.seriesIndex, item.item).toDouble())
                        valueLabel.text = "Value: "+ "%.2f".format(mouseTargetCP.x.toFloat())
                        alphaLabel.text = "Alpha: "+ "%.2f".format(mouseTargetCP.y.toFloat())
                        if(e.trigger.isControlDown && mouseTargetCP.itemIndex != -1)
                        {
                            removeControlpoint(volume, mouseTargetCP)
                            tfPlot.backgroundImage = createTFImage()
                        }
                    }
                    //click on histogram
                    else {
                        val point = mainChart.translateJava2DToScreen(e.trigger.point)
                        val plotArea = mainChart.screenDataArea
                        mouseTargetCP.x = clamp(0.0, 1.0, tfPlot.getDomainAxis(0).java2DToValue(point.getX(), plotArea, tfPlot.domainAxisEdge))
                        mouseTargetCP.y = clamp(0.0, 1.0, tfPlot.getRangeAxis(0).java2DToValue(point.getY(), plotArea, tfPlot.rangeAxisEdge))
                        valueLabel.text = "Value: "+ "%.2f".format(mouseTargetCP.x.toFloat())
                        alphaLabel.text = "Alpha: "+ "%.2f".format(mouseTargetCP.y.toFloat())
                        if(mouseTargetCP.itemIndex == -1)
                        {
                            addControlpoint(volume, mouseTargetCP)
                            tfPlot.backgroundImage = createTFImage()
                        }
                    }
                }
                //click on empty region
                else
                {
                    val point = mainChart.translateJava2DToScreen(e.trigger.point)
                    val plotArea = mainChart.screenDataArea
                    mouseTargetCP.x = clamp(0.0, 1.0, tfPlot.getDomainAxis(0).java2DToValue(point.getX(), plotArea, tfPlot.domainAxisEdge))
                    mouseTargetCP.y = clamp(0.0, 1.0, tfPlot.getRangeAxis(0).java2DToValue(point.getY(), plotArea, tfPlot.rangeAxisEdge))
                    valueLabel.text = "Value: "+ "%.2f".format(mouseTargetCP.x.toFloat())
                    alphaLabel.text = "Alpha: "+ "%.2f".format(mouseTargetCP.y.toFloat())
                    if(mouseTargetCP.itemIndex == -1)
                    {
                        addControlpoint(volume, mouseTargetCP)
                        tfPlot.backgroundImage = createTFImage()
                    }
                }
            }
            override fun chartMouseMoved(e: ChartMouseEvent) {}
        })

        //Histogram Manipulation
        histogramInfoPanel = JPanel()
        histogramInfoPanel.layout = MigLayout()
        mainFrame.add(histogramInfoPanel, "cell 10 8")

        val genHistButton = JButton("Add Histogram")
        histogramInfoPanel.add(genHistButton, "cell 0 0")

        val volumeHistogramData = SimpleHistogramDataset("VolumeBin")
        volumeHistogramData.adjustForBinSize = false
        val resolutionStartExp = 8
        val binResolution = 2.0.pow(resolutionStartExp)

        genHistButton.addActionListener {
            tfPlot.setDataset(1, volumeHistogramData)
            tfPlot.setRangeAxis(1, histogramAxis)
            generateHistogramBins(volume, binResolution, volumeHistogramData)
            range = abs(converter.displayRangeMax - converter.displayRangeMin)
            histXAxis.setRange(converter.displayRangeMin - (axisExtensionFactor*range), converter.displayRangeMax + (axisExtensionFactor*range))

            histogramAxis.setRange(0.0 - (axisExtensionFactor/100.0 * histHeight), 1000.0 + (axisExtensionFactor * histHeight))
            tfPlot.setDomainAxis(1, histXAxis)

            mainChart.repaint()
        }

        //Controlpoint Manipulation
        cpManipulationPanel = JPanel()
        cpManipulationPanel.layout = MigLayout()
        mainFrame.add(cpManipulationPanel, "cell 0 8 2 2")

        cpManipulationPanel.add(valueLabel, "cell 0 0 2 1")
        cpManipulationPanel.add(alphaLabel, "cell 0 1 2 1")


        //Rangeeditor
        val initMinValue = max(converter.displayRangeMin.toInt(), 100)
        minText = JTextField("0", 5)
        minValueLabel = JLabel(initMinValue.toString())

        val initMaxValue = max(converter.displayRangeMax.toInt(), 100)
        maxText = JTextField(initMaxValue.toString(), 5)
        maxValueLabel = JLabel(initMaxValue.toString())

        rangeSlider = RangeSlider()
        rangeSlider.minimum = minText.text.toInt()
        rangeSlider.maximum = maxText.text.toInt()
        rangeSlider.value = converter.displayRangeMin.toInt()
        rangeSlider.upperValue = converter.displayRangeMax.toInt()

        minText.addActionListener { updateSliderRange() }
        maxText.addActionListener { updateSliderRange() }
        rangeSlider.addChangeListener {
            updateConverter()
        }

        rangeEditorPanel = JPanel()
        rangeEditorPanel.layout = MigLayout()
        mainFrame.add(rangeEditorPanel, "cell 8 8 2 1")

        rangeEditorPanel.add(JLabel("min:"), "cell 0 0")
        rangeEditorPanel.add(minText, "cell 1 0")
        rangeEditorPanel.add(JLabel("max:"), "cell 4 0")
        rangeEditorPanel.add(maxText, "cell 5 0")
        rangeEditorPanel.add(rangeSlider, "cell 0 1 7 1")
        rangeEditorPanel.add(minValueLabel, "cell 1 2")
        rangeEditorPanel.add(maxValueLabel, "cell 5 2")

        updateSliderRange()

        //mainFrame.pack()
    }

    private fun createTFImage() : BufferedImage {
        val tfBuffer = volume.transferFunction.serialise().asFloatBuffer()
        val byteArray = ByteArray(tfBuffer.limit())
        for(i in 0 until tfBuffer.limit())
        {
            byteArray[i] = (tfBuffer[i] * 255).toInt().toByte()
        }
        val tfImage = BufferedImage(volume.transferFunction.textureSize, volume.transferFunction.textureHeight, BufferedImage.TYPE_BYTE_GRAY)
        tfImage.raster.setDataElements(0, 0, volume.transferFunction.textureSize, volume.transferFunction.textureHeight, byteArray)

        return tfImage
    }
    private fun updateSliderRange(){
        val min = minText.toInt()
        val max = maxText.toInt()
        if (min != null && max != null){
            rangeSlider.minimum = min
            rangeSlider.maximum = max
        }
        updateConverter()
    }
    private fun updateConverter(){
        minValueLabel.text = rangeSlider.value.toString()
        maxValueLabel.text = rangeSlider.upperValue.toString()

        converter.setDisplayRange(rangeSlider.value.toDouble(), rangeSlider.upperValue.toDouble())
    }
    private fun JTextField.toInt() = text.toIntOrNull()

    private fun addControlpoint(volume : Volume, targetCP : MouseDragTarget)
    {
        val chart = mainChart as ChartPanel
        val collection = chart.chart.xyPlot.getDataset(0) as XYSeriesCollection
        val series = collection.getSeries("ControlPoints")

        series.add(
            targetCP.x.toFloat(), targetCP.y.toFloat()
        )
        val newTF = TransferFunction()
        for (i in 0 until series.itemCount) {
            newTF.addControlPoint(series.getX(i).toFloat(), series.getY(i).toFloat())
        }
        volume.transferFunction = newTF
    }

    private fun updateControlpoint(volume : Volume, targetCP : MouseDragTarget)
    {
        val chart = mainChart as ChartPanel
        val collection = chart.chart.xyPlot.getDataset(0) as XYSeriesCollection
        val series = collection.getSeries(targetCP.seriesIndex)

        targetCP.x = clamp(0.0, 1.0, targetCP.x)
        targetCP.y = clamp(0.0, 1.0, targetCP.y)

        series.remove(targetCP.itemIndex)
        series.add(targetCP.x, targetCP.y)

        val newTF = TransferFunction()
        for (i in 0 until series.itemCount) {
            newTF.addControlPoint(series.getX(i).toFloat(), series.getY(i).toFloat())
        }
        volume.transferFunction = newTF
    }

    private fun removeControlpoint(volume: Volume, targetCP: MouseDragTarget)
    {
        val chart = mainChart as ChartPanel
        val collection = chart.chart.xyPlot.getDataset(0) as XYSeriesCollection
        val series = collection.getSeries(targetCP.seriesIndex)


        series.remove(targetCP.lastIndex)
        val newTF = TransferFunction()
        for (i in 0 until series.itemCount) {
            newTF.addControlPoint(series.getX(i).toFloat(), series.getY(i).toFloat())
        }
        volume.transferFunction = newTF

        targetCP.lastIndex = -1
        valueLabel.text = "Value: 0,00"
        alphaLabel.text = "Alpha: 0,00"
    }

    private fun generateHistogramBins(volume : Volume, binCount : Double, volumeHistogramData : SimpleHistogramDataset) {
        volumeHistogramData.removeAllBins()


        volume.viewerState.sources.firstOrNull()?.spimSource?.getSource(0, 0)?.let { rai ->
            val histogram : Histogram1d<*>
            histogram = Histogram1d(Real1dBinMapper<UnsignedByteType>(converter.displayRangeMin, converter.displayRangeMax, 1024, false))
            histogram.countData(rai as Iterable<UnsignedByteType>)

            var binEnd = -0.0000001
            val displayRange = abs(converter.displayRangeMax - converter.displayRangeMin)
            val binSize = displayRange / binCount
            histogram.forEachIndexed { index, longType ->
                val relativeCount = (longType.get().toFloat() / histogram.totalCount().toFloat()) * histogram.binCount
                val value = (((index.toDouble() / histogram.binCount.toDouble()) * (displayRange / histogram.binCount.toDouble()))) * histogram.binCount.toDouble()

                if (relativeCount.roundToInt() != 0 && (value) >= binEnd) {
                    val binStart = (((index) - (((index) % (histogram.binCount.toDouble()/binCount)))) / histogram.binCount.toDouble()) * displayRange
                    binEnd = binStart + binSize
                    val bin = SimpleHistogramBin(binStart, binEnd, true, false)
                    volumeHistogramData.addBin(bin)
                }
                for (i in 0 until relativeCount.roundToInt()) {
                    volumeHistogramData.addObservation(value)
                }
            }
        }
    }
}
