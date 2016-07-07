package scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector

/**
 * Created by ulrik on 09/06/2016.
 */

interface HMDInput {
    fun getEyeProjection(eye: Int): GLMatrix
    fun getIPD(): Float
    fun getOrientation(): GLMatrix
    fun getPosition(): GLVector
    fun getHeadToEyeTransform(eye: Int): GLMatrix

    fun getPose(): GLMatrix
    fun hasCompositor(): Boolean
    fun submitToCompositor(leftId: Int, rightId: Int)

    fun getRenderTargetSize(): GLVector
    fun initializedAndWorking(): Boolean
}