package graphics.scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector

/**
 * Generic interface for head-mounted displays (HMDs)
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface HMDInput {
    /**
     * Returns the per-eye projection matrix
     *
     * @param[eye] The index of the eye
     * @return GLMatrix containing the per-eye projection matrix
     */
    fun getEyeProjection(eye: Int): GLMatrix

    /**
     * Returns the inter-pupillary distance (IPD)
     *
     * @return IPD as Float
     */
    fun getIPD(): Float

    /**
     * Returns the orientation of the HMD
     *
     * @returns GLMatrix with orientation
     */
    fun getOrientation(): GLMatrix

    /**
     * Returns the absolute position as GLVector
     *
     * @return HMD position as GLVector
     */
    fun getPosition(): GLVector

    /**
     * Returns the per-eye transform that moves from head to eye
     *
     * @param[eye] The eye index
     * @return GLMatrix containing the transform
     */
    fun getHeadToEyeTransform(eye: Int): GLMatrix

    /**
     * Returns the HMD pose
     *
     * @return HMD pose as GLMatrix
     */
    fun getPose(): GLMatrix

    /**
     * Query the HMD whether a compositor is used or the renderer should take
     * care of displaying on the HMD on its own.
     *
     * @return True if the HMD has a compositor
     */
    fun hasCompositor(): Boolean

    /**
     * Submit texture IDs to the compositor
     *
     * @param[leftId] Texture ID of the left eye texture
     * @param[rightId] Texture ID of the right eye texture
     */
    fun submitToCompositor(leftId: Int, rightId: Int)

    /**
     * Returns the optimal render target size for the HMD as 2D vector
     *
     * @return Render target size as 2D vector
     */
    fun getRenderTargetSize(): GLVector

    /**
     * Check whether the HMD is initialized and working
     *
     * @return True if HMD is initialiased correctly and working properly
     */
    fun initializedAndWorking(): Boolean
}
