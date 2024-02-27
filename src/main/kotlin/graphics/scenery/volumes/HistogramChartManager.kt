package graphics.scenery.volumes

import graphics.scenery.SceneryElement
import org.jfree.chart.ChartPanel
import org.jfree.chart.axis.LogarithmicAxis
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.StandardXYBarPainter
import org.jfree.chart.renderer.xy.XYBarRenderer
import org.jfree.data.statistics.SimpleHistogramBin
import org.jfree.data.statistics.SimpleHistogramDataset
import javax.swing.JCheckBox
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Handles all histogram related things.
 *
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 */
class HistogramChartManager(val tfPlot: XYPlot,
                            val mainChart: ChartPanel,
                            private val tfContainer: HasTransferFunction,
                            val axisExtensionFactor: Double){
    val genHistButton = JCheckBox("Show Histogram")

    private val histogramAxis = LogarithmicAxis("")

    init {
        val histogramRenderer = XYBarRenderer()
        histogramRenderer.setShadowVisible(false)
        histogramRenderer.barPainter = StandardXYBarPainter()
        histogramRenderer.isDrawBarOutline = false
        tfPlot.setRenderer(1, histogramRenderer)

        val histXAxis = NumberAxis()
        var range = abs(tfContainer.maxDisplayRange - tfContainer.minDisplayRange)
        histXAxis.setRange(
            tfContainer.minDisplayRange - (axisExtensionFactor * range),
            tfContainer.maxDisplayRange + (axisExtensionFactor * range)
        )

        histogramAxis.isMinorTickMarksVisible = true
        histogramAxis.setRange(
            0.0,
            1000.0
        )

        val volumeHistogramData = SimpleHistogramDataset("VolumeBin")
        volumeHistogramData.adjustForBinSize = false

        if (tfContainer is HasHistogram) {
            genHistButton.addActionListener() {
                val histogramVisible = tfPlot.getDataset(1) != null

                if(histogramVisible) {
                    tfPlot.setDataset(1, null)
                    tfPlot.setDomainAxis(1, null)
                    tfPlot.setRangeAxis(1, null)

                    mainChart.repaint()
                } else {
                    tfPlot.setDataset(1, volumeHistogramData)
                    tfPlot.setRangeAxis(1, histogramAxis)
                    populateHistogramBins( volumeHistogramData)
                    range = abs(tfContainer.maxDisplayRange - tfContainer.minDisplayRange)
                    histXAxis.setRange(
                        tfContainer.minDisplayRange - (axisExtensionFactor * range),
                        tfContainer.maxDisplayRange + (axisExtensionFactor * range)
                    )

                    tfPlot.setDomainAxis(1, histXAxis)

                    mainChart.repaint()
                }
            }
        }
    }

    private fun populateHistogramBins(volumeHistogramData: SimpleHistogramDataset) {
        volumeHistogramData.removeAllBins()

        val volume = tfContainer as? BufferedVolume
        if (volume == null){
            oldGenerateHistogramBins(volumeHistogramData)
            return
        }

        val volumeHistogram = VolumeHistogramComputeNode.generateHistogram(
            volume,
            volume.timepoints?.get(volume.currentTimepoint)!!.contents,
            volume.getScene() ?: return
        )
        val histogram = volumeHistogram.fetchHistogram(volume.getScene()!!, volume.volumeManager.hub!!.get<graphics.scenery.backends.Renderer>(
            SceneryElement.Renderer
        )!!)

        val displayRange = abs(tfContainer.maxDisplayRange - tfContainer.minDisplayRange)
        val binSize = displayRange / volumeHistogram.numBins
        val minDisplayRange = tfContainer.minDisplayRange.toDouble()

        var max = 0
        histogram.forEachIndexed { index, value ->
            val bin = SimpleHistogramBin(
                minDisplayRange + index * binSize,
                minDisplayRange + (index + 1) * binSize,
                true,
                false
            )
            bin.itemCount = value
            volumeHistogramData.addBin(bin)
            max = max(max,value)
        }

        histogramAxis.setRange(
            -0.1 ,
            max * (1.2)
        )

        mainChart.repaint()
    }

    /**
     * Old code from PowerOfNames. Calculates the histogram on the CPU.
     * Taken from https://github.com/scenerygraphics/scenery/blob/main/src/main/kotlin/graphics/scenery/volumes/TransferFunctionEditor.kt at commit 58ae87a
     */
    private fun oldGenerateHistogramBins(volumeHistogramData: SimpleHistogramDataset) {
        val binCount = 1024.0
        volumeHistogramData.removeAllBins()

        val histogram = (tfContainer as? HasHistogram)?.generateHistogram()
        if (histogram != null) {
            var binEnd = -0.0000001
            val displayRange = abs(tfContainer.maxDisplayRange - tfContainer.minDisplayRange)
            val binSize = displayRange / binCount
            histogram.forEachIndexed { index, longType ->

                val relativeCount = (longType.get().toFloat() / histogram.totalCount().toFloat()) * histogram.binCount
                val value =
                    (((index.toDouble() / histogram.binCount.toDouble()) * (displayRange / histogram.binCount.toDouble()))) * histogram.binCount.toDouble()

                if (relativeCount.roundToInt() != 0 && (value) >= binEnd) {
                    val binStart =
                        (((index) - (((index) % (histogram.binCount.toDouble() / binCount)))) / histogram.binCount.toDouble()) * displayRange
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
