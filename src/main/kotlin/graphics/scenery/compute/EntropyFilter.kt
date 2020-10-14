package graphics.scenery.compute

import graphics.scenery.utils.LazyLogger
import net.imglib2.RandomAccessible
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.neighborhood.Shape
import net.imglib2.algorithm.stats.Max
import net.imglib2.algorithm.stats.Min
import net.imglib2.histogram.Histogram1d
import net.imglib2.histogram.Real1dBinMapper
import net.imglib2.type.numeric.NumericType
import net.imglib2.type.numeric.RealType
import net.imglib2.view.Views
import kotlin.math.ln

/**
 * Iterative n-dimensional flood fill for arbitrary neighborhoods.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
object EntropyFilter {
    val logger by LazyLogger()
    fun <T: RealType<T>, U: RealType<U>> entropy(
        source: RandomAccessible<T>,
        target: RandomAccessibleInterval<U>,
        shape: Shape,
        maxBins: Long = 1024L) {

        val sourceInterval = Views.interval(source, target)

        val iterable = Views.iterable(sourceInterval)

        val min = Min.findMin(iterable).get().realDouble
        val max = Max.findMax(iterable).get().realDouble

        val histogram = Histogram1d(iterable, Real1dBinMapper<T>(min, max, maxBins, false))

        val neighborhood = shape.neighborhoodsRandomAccessible(source)
        val neighborhoodAccess = neighborhood.randomAccess()
        val targetAccess = target.randomAccess()

        val cursor = iterable.localizingCursor()
        while(cursor.hasNext()) {
            cursor.next()
            var entropy = 0.0f
            val total = iterable.dimensionsAsLongArray().sum()

            neighborhoodAccess.setPosition(cursor)
            val neighborhoodCursor = neighborhoodAccess.get().cursor()

            while(neighborhoodCursor.hasNext()) {
                neighborhoodCursor.next()
                val value = cursor.get()
//                logger.info("Getting for $value")
                val freq = histogram.frequency(value)
                val e = if(freq > 0) {
                    val pi = freq.toFloat()/total.toFloat()
                    pi * ln(pi)
                } else {
                    0.0f
                }

                entropy += e
            }

            targetAccess.setPosition(cursor)
            targetAccess.get().setReal(entropy)
        }
    }


}
