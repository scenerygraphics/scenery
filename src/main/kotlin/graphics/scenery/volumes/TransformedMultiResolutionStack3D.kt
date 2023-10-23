package graphics.scenery.volumes

import bvv.core.multires.MultiResolutionStack3D
import bvv.core.multires.ResolutionLevel3D
import net.imglib2.realtransform.AffineTransform3D
import org.joml.Matrix4f

/**
 * Class for wrapping [MultiResolutionStack3D] [stack]s with custom transformations
 * governed by [node] and [actualSourceTransform].
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class TransformedMultiResolutionStack3D<T>(
    val stack: MultiResolutionStack3D<T>,
    val node: Volume,
    val actualSourceTransform: AffineTransform3D
) : MultiResolutionStack3D<T> {
    override fun getType(): T {
        return stack.type as T
    }

    override fun getSourceTransform(): AffineTransform3D {
        val w = AffineTransform3D()
        val arr = FloatArray(16)
        Matrix4f(node.spatialOrNull()?.world).transpose().get(arr)

        w.set(*arr.map { it.toDouble() }.toDoubleArray())
        return w.concatenate(actualSourceTransform)
    }

    override fun resolutions(): List<ResolutionLevel3D<T>> {
        val resolutions = (stack.resolutions() as List<ResolutionLevel3D<T>>)
        val limits = node.multiResolutionLevelLimits
        return if(limits == null) {
            resolutions
        } else {
            resolutions.subList(limits.first, limits.second)
        }
    }

    override fun equals(other: Any?): Boolean {
        return stack == other
    }

    override fun hashCode(): Int {
        return stack.hashCode()
    }
}
