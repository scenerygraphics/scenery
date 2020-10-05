package graphics.scenery.volumes

import bdv.viewer.Interpolation
import bdv.viewer.Source
import mpicbg.spim.data.sequence.VoxelDimensions
import net.imglib2.RandomAccessibleInterval
import net.imglib2.RealRandomAccessible
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.NumericType
import java.nio.ByteBuffer
import java.util.LinkedHashMap
import java.util.concurrent.CopyOnWriteArrayList

class BufferSource<T: NumericType<T>>(val timepoints: CopyOnWriteArrayList<BufferedVolume.Timepoint>,
                                      val width: Int,
                                      val height: Int,
                                      val depth: Int,
                                      val dimensions: VoxelDimensions,
                                      val sourceName: String,
                                      val sourceType: T): Source<T> {
    override fun isPresent(t: Int): Boolean {
        return t in 0 .. timepoints.size
    }

    override fun getNumMipmapLevels(): Int {
        return 1
    }

    override fun getInterpolatedSource(t: Int, level: Int, method: Interpolation?): RealRandomAccessible<T> {
        TODO("Can't get interpolated source for BufferDummySource")
    }

    override fun getSourceTransform(t: Int, level: Int, transform: AffineTransform3D?) {
        transform?.set(AffineTransform3D())
    }

    override fun getVoxelDimensions(): VoxelDimensions {
        return dimensions
    }

    override fun getSource(t: Int, level: Int): RandomAccessibleInterval<T> {
        TODO("Can't get source for BufferDummySource")
    }

    override fun getName(): String {
        return sourceName
    }

    override fun getType(): T {
        return sourceType
    }
}
