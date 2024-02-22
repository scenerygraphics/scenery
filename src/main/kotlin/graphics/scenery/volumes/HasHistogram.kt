package graphics.scenery.volumes

import net.imglib2.histogram.Histogram1d

/**
 * @author Konrad Michel <Konrad.Michel@mailbox.tu-dresden.de>
 *
 * Interface to abstract out a possible histogram implementation and untie it from pure Volume usage
 */
interface HasHistogram {
    /**
     * This is a placeholder function that needs to be overwritten by the implementing class. Currently the output should be of type Histogram1d<*>
     * from imglib2
     */
    fun generateHistogram(maximumResolution: Int = 512, bins: Int = 1024) : Histogram1d<*>?
}
