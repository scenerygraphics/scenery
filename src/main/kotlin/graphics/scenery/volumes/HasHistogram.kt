package graphics.scenery.volumes

import org.jfree.data.statistics.SimpleHistogramDataset

/**
 * @author Konrad Michel <Konrad.Michel@mailbox.tu-dresden.de>
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 *
 * Interface to abstract out a possible histogram implementation and untie it from pure Volume usage
 */
interface HasHistogram {
    /**
     * @return most common value found in the histogram (aka. highest bar) or null if no histogram could be generated.
     * Needed for scaling the display correctly.
     */
    fun generateHistogram(volumeHistogramData: SimpleHistogramDataset): Int?
}
