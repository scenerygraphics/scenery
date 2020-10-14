package graphics.scenery.tests.unit.compute

import graphics.scenery.compute.EntropyFilter
import graphics.scenery.utils.LazyLogger
import ij.IJ
import net.imglib2.RandomAccessible
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.neighborhood.HyperSphereShape
import net.imglib2.converter.Converters
import net.imglib2.img.array.ArrayImgs
import net.imglib2.img.display.imagej.ImageJFunctions
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.junit.Test

object EntropyFilterTests {
    private val logger by LazyLogger()

    @Test
    fun testEntropyCalculation() {
        logger.info("Downloading flybrain stack ...")
        val imp = IJ.openImage("https://imagej.nih.gov/ij/images/flybrain.zip")
        val flybrain: RandomAccessibleInterval<ARGBType> = ImageJFunctions.wrapRGBA(imp)

        val flybrainCrop: RandomAccessibleInterval<ARGBType> = Views.interval(flybrain, longArrayOf(0, 0, 0), longArrayOf(32, 32, 23))

        logger.info("Done.")

        val type = UnsignedShortType()

        // extract the red channel
        val red = Converters.convert(flybrainCrop, { i: ARGBType, o: UnsignedShortType -> o.set(ARGBType.red(i.get())) }, type)
        val source: RandomAccessible<UnsignedShortType> = Views.extendBorder(red)

        val copy = ArrayImgs.floats(*red.dimensionsAsLongArray())

        val shape = HyperSphereShape(10)
        EntropyFilter.entropy(
                source,
                copy as RandomAccessibleInterval<FloatType>,
                shape
            )

        ImageJFunctions.show(flybrainCrop, "original crop")
        ImageJFunctions.show(copy)
    }
}
