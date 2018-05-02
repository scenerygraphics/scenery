package graphics.scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.Camera
import graphics.scenery.Mesh
import graphics.scenery.Node

/**
 * Generic interface for head-mounted displays (HMDs)
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

enum class TrackedDeviceType {
    Invalid,
    HMD,
    Controller,
    BaseStation,
    Generic
}

class TrackedDevice(val type: TrackedDeviceType, var name: String, var pose: GLMatrix, var timestamp: Long) {
    var metadata: Any? = null
    var orientation = Quaternion()
        get(): Quaternion {
//            val pose = pose.floatArray
//
//            field.w = Math.sqrt(1.0 * Math.max(0.0f, 1.0f + pose[0] + pose[5] + pose[10])).toFloat() / 2.0f
//            field.x = Math.sqrt(1.0 * Math.max(0.0f, 1.0f + pose[0] - pose[5] - pose[10])).toFloat() / 2.0f
//            field.y = Math.sqrt(1.0 * Math.max(0.0f, 1.0f - pose[0] + pose[5] - pose[10])).toFloat() / 2.0f
//            field.z = Math.sqrt(1.0 * Math.max(0.0f, 1.0f - pose[0] - pose[5] + pose[10])).toFloat() / 2.0f
//
//            field.x *= Math.signum(field.x * (pose[9] - pose[6]))
//            field.y *= Math.signum(field.y * (pose[2] - pose[8]))
//            field.z *= Math.signum(field.z * (pose[4] - pose[1]))
            field = Quaternion().setFromMatrix(pose.floatArray, 0)

            return field
        }

    var position = GLVector(0.0f, 0.0f, 0.0f)
        get(): GLVector {
            val m = pose.floatArray
            field = GLVector(m[12], m[13], m[14])

            return field
        }
}

interface TrackerInput {
    /**
     * Returns the orientation of the HMD
     *
     * @returns GLMatrix with orientation
     */
    fun getOrientation(): Quaternion

    /**
     * Returns the orientation of the given device, or a unit quaternion if the device is not found.
     *
     * @returns GLMatrix with orientation
     */
    fun getOrientation(id: String): Quaternion

    /**
     * Returns the absolute position as GLVector
     *
     * @return HMD position as GLVector
     */
    fun getPosition(): GLVector

    /**
     * Returns the HMD pose
     *
     * @return HMD pose as GLMatrix
     */
    fun getPose(): GLMatrix

    /**
     * Returns the HMD pose
     *
     * @return HMD pose as GLMatrix
     */
    fun getPoseForEye(eye: Int): GLMatrix

    /**
     * Check whether the HMD is initialized and working
     *
     * @return True if HMD is initialiased correctly and working properly
     */
    fun initializedAndWorking(): Boolean

    /**
     * update state
     */
    fun update()

    fun getWorkingTracker(): TrackerInput?

    fun loadModelForMesh(device: TrackedDevice, mesh: Mesh): Mesh
    fun loadModelForMesh(type: TrackedDeviceType = TrackedDeviceType.Controller, mesh: Mesh): Mesh

    fun attachToNode(device: TrackedDevice, node: Node, camera: Camera? = null)
    fun getTrackedDevices(ofType: TrackedDeviceType): Map<String, TrackedDevice>
}
