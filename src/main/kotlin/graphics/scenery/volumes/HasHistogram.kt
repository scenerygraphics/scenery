package graphics.scenery.volumes

import net.imglib2.histogram.Histogram1d

/**
 * @author Konrad Michel <Konrad.Michel@mailbox.tu-dresden.de>
 *
 * Interface to abstract out a possible histogram implementation and untie it from pure Volume usage
 */
interface HasHistogram {

    fun generateHistogram() : Histogram1d<*>?
}
