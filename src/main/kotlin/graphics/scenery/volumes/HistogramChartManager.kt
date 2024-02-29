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
import java.awt.Color
import javax.swing.JCheckBox
import kotlin.math.abs
import kotlin.math.max

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

    private val histYAxis = LogarithmicAxis("")
    private val histXAxis = NumberAxis()

    init {
        val histogramRenderer = XYBarRenderer()
        histogramRenderer.setShadowVisible(false)
        histogramRenderer.barPainter = StandardXYBarPainter()
        histogramRenderer.isDrawBarOutline = false
        histogramRenderer.setSeriesPaint(0, Color(160,160,255))
        tfPlot.setRenderer(1, histogramRenderer)


        histXAxis.isTickLabelsVisible = false
        histXAxis.isMinorTickMarksVisible = false
        histXAxis.isTickMarksVisible = false
        histXAxis.autoRangeIncludesZero = false
        histXAxis.autoRangeStickyZero = false
        histXAxis.isAutoRange = false

        histYAxis.isTickLabelsVisible = false
        histYAxis.isMinorTickMarksVisible = false
        histYAxis.isTickMarksVisible = false

        val volumeHistogramData = SimpleHistogramDataset("VolumeBin")
        volumeHistogramData.adjustForBinSize = false

        if (tfContainer is HasHistogram) {
            genHistButton.addActionListener {
                val histogramVisible = tfPlot.getDataset(1) != null

                if(histogramVisible) {
                    // hide histogram
                    tfPlot.setDataset(1, null)
                    tfPlot.setDomainAxis(1, null)
                    tfPlot.setRangeAxis(1, null)

                    mainChart.repaint()
                } else {
                    tfPlot.setDataset(1, volumeHistogramData)
                    tfPlot.setRangeAxis(1, histYAxis)
                    populateHistogramBins( volumeHistogramData)

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
            cpuGenerateHistogramBins(volumeHistogramData)
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

        histYAxis.setRange(
            -0.1 ,
            max * (1.2)
        )
        histXAxis.setRange(
            tfContainer.minDisplayRange - (axisExtensionFactor * displayRange),
            tfContainer.maxDisplayRange + (axisExtensionFactor * displayRange)
        )
    }

    /**
     *  Calculates the histogram on the CPU.
     */
    private fun cpuGenerateHistogramBins(volumeHistogramData: SimpleHistogramDataset) {
        volumeHistogramData.removeAllBins()

        // This generates a histogram over the whole volume ignoring the display range.
        // We now need to select only the bins we care about.
        val absoluteHistogram = (tfContainer as? HasHistogram)?.generateHistogram()
        if (absoluteHistogram != null) {
            val displayRange = abs(tfContainer.maxDisplayRange - tfContainer.minDisplayRange)

            val absoluteBinSize = absoluteHistogram.max() / 1024.0
            val minDisplayRange = tfContainer.minDisplayRange.toDouble()
            val maxDisplayRange = tfContainer.maxDisplayRange.toDouble()

            var max = 100
            absoluteHistogram.forEachIndexed { index, longType ->
                val startOfAbsoluteBin = index * absoluteBinSize
                val endOfAbsoluteBin = (index+1) * absoluteBinSize
                if (minDisplayRange <= startOfAbsoluteBin && endOfAbsoluteBin < maxDisplayRange) {

                    val bin = SimpleHistogramBin(
                        startOfAbsoluteBin,
                        endOfAbsoluteBin,
                        true,
                        false
                    )
                    bin.itemCount = longType.get().toInt()
                    max = max(bin.itemCount, max)
                    volumeHistogramData.addBin(bin)
                }
            }

            histYAxis.setRange(
                -0.1 ,
                max * (1.2)
            )

            histXAxis.setRange(
                tfContainer.minDisplayRange - (axisExtensionFactor * displayRange),
                tfContainer.maxDisplayRange + (axisExtensionFactor * displayRange)
            )

        }
    }
}
