package graphics.scenery.volumes

import bvv.core.blockmath.MipmapSizes
import bvv.core.multires.ResolutionLevel3D
import bvv.core.util.MatrixMath
import graphics.scenery.utils.lazyLogger
import net.imglib2.algorithm.kdtree.ConvexPolytope
import net.imglib2.algorithm.kdtree.HyperPlane
import org.apache.commons.math3.optim.linear.*
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.joml.*
import java.util.ArrayList
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class CustomMipmapSizes : MipmapSizes() {
    val pNear: Vector3f = Vector3f()
    val pFarMinusNear: Vector3f = Vector3f()

    private var sn: Float = 0f
    private var sf: Float = 0f
    private var v0x = 0f
    private var v0y = 0f
    private var v0z = 0f
    private var drels = 0f
    private var sls: FloatArray = floatArrayOf()
    private var drelClosestSourcePoint = 0f

    /**
     * Highest required resolution (lowest level) for any visible block
     */
    private var baseLevel: Int = 0

    /**
     * Is any part of the volume visible?
     */
    private var isVisible: Boolean = false

    private val dir = Vector3f()

    /**
     * @param sourceToNDC
     * `Projection * View * Model` matrix
     * @param viewportWidth
     * @param resolutions
     */
    override fun init(sourceToNDC: Matrix4fc, viewportWidth: Int, resolutions: List<ResolutionLevel3D<*>?>) {
        val NDCtoSource = sourceToNDC.invert(Matrix4f())
        val w = 2f / viewportWidth
        // viewport pixel width on near and far plane (in source coordinates)
        NDCtoSource.transformProject(0f, 0f, -1f, pNear)
        val pFar = NDCtoSource.transformProject(0f, 0f, 1f, Vector3f())
        sn = NDCtoSource.transformProject(w, 0f, -1f, Vector3f()).sub(pNear).length()
        sf = NDCtoSource.transformProject(w, 0f, 1f, Vector3f()).sub(pFar).length()

        pFar.sub(pNear, pFarMinusNear)
        pFarMinusNear.normalize(dir)
        drels = 1f / pFarMinusNear.lengthSquared()

        // voxel size on near plane (in source coordinates)
        // ... or rather: on any plane perpendicular to dir.
        v0x = sqrt(1.0 - dir.dot(1f, 0f, 0f)).toFloat()
        v0y = sqrt(1.0 - dir.dot(0f, 1f, 0f)).toFloat()
        v0z = sqrt(1.0 - dir.dot(0f, 0f, 1f)).toFloat()

        // voxel size (max of x,y,z) on near plane for each resolution level
        sls = FloatArray(resolutions.size)
        for(i in resolutions.indices) sls[i] = sl(resolutions[i]!!.r)

        /*
     * Closest visible source point to near clipping plane.
     * TODO: Solving this with LP simplex seems a bit insane. Is there a more closed-form solution???
     */
        val imgSize = LongArray(3)
        resolutions[0]!!.image.dimensions(imgSize)
        val T = sourceToNDC.transpose(Matrix4f())
        val sourceRegion =
            ConvexPolytope( // planes bounding the view frustum, normals facing inwards, transformed to source coordinates
                sourceHyperPlane(T, 1.0, 0.0, 0.0, -1.0),
                sourceHyperPlane(T, -1.0, 0.0, 0.0, -1.0),
                sourceHyperPlane(T, 0.0, 1.0, 0.0, -1.0),
                sourceHyperPlane(T, 0.0, -1.0, 0.0, -1.0),
                sourceHyperPlane(T, 0.0, 0.0, 1.0, -1.0),
                sourceHyperPlane(T, 0.0, 0.0, -1.0, -1.0),  // planes bounding the source, normals facing inwards
                HyperPlane(1.0, 0.0, 0.0, 0.0),  // TODO: 0.5 offsets?
                HyperPlane(0.0, 1.0, 0.0, 0.0),  // TODO: 0.5 offsets?
                HyperPlane(0.0, 0.0, 1.0, 0.0),  // TODO: 0.5 offsets?
                HyperPlane(-1.0, 0.0, 0.0, -imgSize[0].toDouble()),  // TODO: 0.5 offsets?
                HyperPlane(0.0, -1.0, 0.0, -imgSize[1].toDouble()),  // TODO: 0.5 offsets?
                HyperPlane(0.0, 0.0, -1.0, -imgSize[2].toDouble())
            ) // TODO: 0.5 offsets?

        pFarMinusNear.mul(drels, dir)
        val f = LinearObjectiveFunction(
            doubleArrayOf(dir.x().toDouble(), dir.y().toDouble(), dir.z().toDouble()),
            -dir.dot(pNear).toDouble()
        )
        val constraints: MutableList<LinearConstraint> = ArrayList()
        for(plane in sourceRegion.hyperplanes) constraints.add(
            LinearConstraint(
                plane.normal,
                Relationship.GEQ,
                plane.distance
            )
        )
        try {
            val sln = SimplexSolver().optimize(f, LinearConstraintSet(constraints), GoalType.MINIMIZE)
            drelClosestSourcePoint = max(min(sln.value.toFloat(), 1.0f), 0.0f)
        } catch(e: NoFeasibleSolutionException) {
            isVisible = false
        }

        baseLevel = bestLevel(drelClosestSourcePoint)
        logger.info("Base level=$baseLevel")
    }
    private val logger by lazyLogger()

    /**
     * @param levelScaleFactors
     * scale factors from requested resolution level to full resolution
     */
    private fun sl(levelScaleFactors: IntArray): Float {
        val x = levelScaleFactors[0]
        val y = levelScaleFactors[1]
        val z = levelScaleFactors[2]
        return max((x * v0x).toDouble(), max((y * v0y).toDouble(), (z * v0z).toDouble())).toFloat()
    }

    /**
     * Get best resolution level at source coordinates `x`.
     * (Queried for block centers.)
     */
    override fun bestLevel(x: Vector3fc, temp: Vector3f?): Int {
        val drel = x.sub(pNear, temp).dot(pFarMinusNear) * drels
        return bestLevel(drel)
    }

    private fun bestLevel(drel: Float): Int {
        val sd = drel * sf + (1 - drel) * sn

        for(l in sls.indices) {
            if(sd <= sls[l]) {
                if(l == 0) return 0
                return if((sls[l] - sd < sd - sls[l - 1])) l else (l - 1)
            }
        }
        return sls.size - 1
    }


    // DEBUG...
    override fun getDrel(x: Vector3fc, temp: Vector3f?): Float {
        val drel = x.sub(pNear, temp).dot(pFarMinusNear) * drels
        return drel
    }

    companion object {
        private fun sourceHyperPlane(
            sourceToNDCTransposed: Matrix4fc,
            nx: Double,
            ny: Double,
            nz: Double,
            d: Double
        ): HyperPlane {
            return MatrixMath.hyperPlane(
                Vector4f(nx.toFloat(), ny.toFloat(), nz.toFloat(), -d.toFloat()).mul(sourceToNDCTransposed)
                    .normalize3()
            )
        }
    }
}