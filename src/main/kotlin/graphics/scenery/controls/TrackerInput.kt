package graphics.scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.Camera
import graphics.scenery.Mesh
import graphics.scenery.Node


/**
 * Enum class for the types of devices that can be tracked.
 * Includes HMDs, controllers, base stations, generic devices, and invalid ones for the moment.
 */
enum class TrackedDeviceType {
    Invalid,
    HMD,
    Controller,
    BaseStation,
    Generic
}

/**
 * Class for tracked devices and querying information about them.
 *
 * @property[type] The [TrackedDeviceType] of the device.
 * @property[name] A name for the device.
 * @property[pose] The current pose of the device.
 * @property[timestamp] The latest timestamp with respect to the pose.
 */
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

    var model: Node? = null
    var modelPath: String? = null
}

typealias TrackerInputEventHandler = (TrackerInput, TrackedDevice, Long) -> Any

/**
 * Contains event handlers in the form of lists of lambdas (see [TrackerInputEventHandler])
 * for handling device connect/disconnect events.
 */
class TrackerInputEventHandlers {
    /** List of handlers for connect events */
    var onDeviceConnect = ArrayList<TrackerInputEventHandler>()
        protected set
    /** List of handlers for disconnect events */
    var onDeviceDisconnect = ArrayList<TrackerInputEventHandler>()
        protected set
}

/**
 * Generic interface for head-mounted displays (HMDs) providing tracker input.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface TrackerInput {
    /** Event handler class */
    var events: TrackerInputEventHandlers

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
     * Returns the HMD pose for a given eye.
     *
     * @param[eye] The eye to return the pose for.
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

    /**
     * Check whether there is a working TrackerInput for this device.
     *
     * @returns the [TrackerInput] if that is the case, null otherwise.
     */
    fun getWorkingTracker(): TrackerInput?

    /**
     * Loads a model representing the [TrackedDevice].
     *
     * @param[device] The device to load the model for.
     * @param[mesh] The [Mesh] to attach the model data to.
     */
    fun loadModelForMesh(device: TrackedDevice, mesh: Mesh): Mesh

    /**
     * Loads a model representing a kind of [TrackedDeviceType].
     *
     * @param[type] The device type to load the model for, by default [TrackedDeviceType.Controller].
     * @param[mesh] The [Mesh] to attach the model data to.
     */
    fun loadModelForMesh(type: TrackedDeviceType = TrackedDeviceType.Controller, mesh: Mesh): Mesh

    /**
     * Attaches a given [TrackedDevice] to a scene graph [Node], camera-relative in case [camera] is non-null.
     *
     * @param[device] The [TrackedDevice] to use.
     * @param[node] The node which should take tracking data from [device].
     * @param[camera] A camera, in case the node should also be added as a child to the camera.
     */
    fun attachToNode(device: TrackedDevice, node: Node, camera: Camera? = null)

    /**
     * Returns all tracked devices a given type.
     *
     * @param[ofType] The [TrackedDeviceType] of the devices to return.
     * @return A [Map] of device name to [TrackedDevice]
     */
    fun getTrackedDevices(ofType: TrackedDeviceType): Map<String, TrackedDevice>
}
