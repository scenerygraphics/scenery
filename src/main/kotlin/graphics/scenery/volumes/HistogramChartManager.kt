package graphics.scenery.volumes

import org.jfree.chart.ChartPanel
import org.jfree.chart.axis.LogarithmicAxis
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.StandardXYBarPainter
import org.jfree.chart.renderer.xy.XYBarRenderer
import org.jfree.data.statistics.SimpleHistogramDataset
import java.awt.Color
import javax.swing.JCheckBox
import kotlin.math.abs

/**
 * Handles all histogram chart related things.
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
    private val volumeHistogramData: SimpleHistogramDataset

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

        volumeHistogramData = SimpleHistogramDataset("VolumeBin")
        volumeHistogramData.adjustForBinSize = false

    }

    fun calculateHistogram(){
        if (tfContainer !is HasHistogram) return
        tfPlot.setDataset(1, volumeHistogramData)
        tfPlot.setRangeAxis(1, histYAxis)
        generateHistogram( volumeHistogramData)

        tfPlot.setDomainAxis(1, histXAxis)

        mainChart.repaint()
    }

    fun hideHistogram(){
        val histogramVisible = tfPlot.getDataset(1) != null
        if(histogramVisible) {
            // hide histogram
            tfPlot.setDataset(1, null)
            tfPlot.setDomainAxis(1, null)
            tfPlot.setRangeAxis(1, null)

            mainChart.repaint()
        }
    }

    private fun generateHistogram(volumeHistogramData: SimpleHistogramDataset) {
        volumeHistogramData.removeAllBins()
        val max = (tfContainer as? HasHistogram)?.generateHistogram(volumeHistogramData) ?: return

        histYAxis.setRange(
            -0.1 ,
            max * (1.2)
        )

        val displayRange = abs(tfContainer.maxDisplayRange - tfContainer.minDisplayRange)
        histXAxis.setRange(
            tfContainer.minDisplayRange - (axisExtensionFactor * displayRange),
            tfContainer.maxDisplayRange + (axisExtensionFactor * displayRange)
        )
    }

}
