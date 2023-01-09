package graphics.scenery.volumes

import net.imglib2.histogram.Histogram1d

interface HasHistogram {

    fun generateHistogram() : Histogram1d<*>?
}
