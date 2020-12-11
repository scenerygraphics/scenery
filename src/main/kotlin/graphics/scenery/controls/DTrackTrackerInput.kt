package graphics.scenery.controls

import art.DTrackSDK
import graphics.scenery.Camera
import graphics.scenery.Mesh
import graphics.scenery.Node
import graphics.scenery.utils.LazyLogger
import kotlinx.coroutines.*
import org.joml.*
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * DTrack [TrackerInput] interface
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class DTrackTrackerInput(val host: String = "localhost", val port: Int = 5000, var defaultBodyId: Int = 0): TrackerInput {
    private var sdk: DTrackSDK = DTrackSDK(InetAddress.getByName(host), port)
    private val logger by LazyLogger()

    var numBodies: Int = 0
        protected set

    var frameCount: Long = 0L
        protected set

    var timestamp: Long = 0L
        protected set

    protected val listeningJob: Job

    data class DTrackBodyState(var quality: Double, var position: Vector3f, var rotation: Quaternionf)

    protected var bodyState = ConcurrentHashMap<Int, DTrackBodyState>()

    protected var connected = false
    protected var receivedInput = false

    init {
        logger.debug("Connected to $host:$port.")

        if(!sdk.isDataInterfaceValid) {
            throw IllegalStateException("DTrack initialisation error, could not talk to DTrack on $host, port $port.")
        }

        logger.debug("Data interface for $host:$port is valid.")

        if(!sdk.startMeasurement()) {
            throw IllegalStateException("Could not start DTrack measurement on $host, port $port.")
        }

        logger.debug("Measurement for $host:$port has started.")

        connected = true

        listeningJob = GlobalScope.launch {
            while(true) {
                if(sdk.receive()) {
                    logger.debug("Received DTrack frame with {} bodies.", sdk.numBody)

                    receivedInput = true
                    for(bodyId in 0 until sdk.numBody) {
                        val quality = sdk.getBody(bodyId).quality
                        val loc = sdk.getBody(bodyId).loc
                        val rotation = sdk.getBody(bodyId).rot

                        val x = -loc[0].toFloat()/1000.0f
                        val y = loc[2].toFloat()/1000.0f
                        val z = -loc[1].toFloat()/1000.0f

                        val state = bodyState.getOrPut(bodyId, {
                            DTrackBodyState(
                                quality,
                                Vector3f(x, y, z),
                                Quaternionf().setFromUnnormalized(rotToMatrix3f(rotation))
                            )
                        })

                        state.quality = quality
                        state.rotation.setFromUnnormalized(rotToMatrix3f(rotation))
                        state.position.set(x, y, z)
                    }
                }
            }
        }
    }

    private fun rotToMatrix3f(input: Array<DoubleArray>): Matrix3f {
        return Matrix3f(
            input[0][0].toFloat(), input[1][0].toFloat(), input[2][0].toFloat(),
            input[0][1].toFloat(), input[1][1].toFloat(), input[2][1].toFloat(),
            input[0][2].toFloat(), input[1][2].toFloat(), input[2][2].toFloat()
        )
    }

    /** Event handler class */
    override var events = TrackerInputEventHandlers()

    /**
     * Returns the orientation of the HMD
     *
     * @returns Matrix4f with orientation
     */
    override fun getOrientation(): Quaternionf {
        return bodyState[defaultBodyId]?.rotation ?: Quaternionf()
    }

    /**
     * Returns the orientation of the given device, or a unit quaternion if the device is not found.
     *
     * @returns Matrix4f with orientation
     */
    override fun getOrientation(id: String): Quaternionf {
        return bodyState[id.toInt()]?.rotation ?: Quaternionf()
    }

    /**
     * Returns the absolute position as Vector3f
     *
     * @return HMD position as Vector3f
     */
    override fun getPosition(): Vector3f {
        return bodyState[defaultBodyId]?.position ?: Vector3f(0.0f)
    }

    /**
     * Returns the HMD pose
     *
     * @return HMD pose as Matrix4f
     */
    override fun getPose(): Matrix4f {
        return Matrix4f().set(bodyState[defaultBodyId]?.rotation ?: Quaternionf())
    }

    /**
     * Returns a list of poses for the devices [type] given.
     *
     * @return Pose as Matrix4f
     */
    override fun getPose(type: TrackedDeviceType): List<TrackedDevice> {
        return listOf(TrackedDevice(TrackedDeviceType.HMD, "DTrack", getPose(), System.nanoTime()))
    }

    /**
     * Returns the HMD pose for a given eye.
     *
     * @param[eye] The eye to return the pose for.
     * @return HMD pose as Matrix4f
     */
    override fun getPoseForEye(eye: Int): Matrix4f {
        return this.getPose()
    }

    /**
     * Check whether the HMD is initialized and working
     *
     * @return True if HMD is initialiased correctly and working properly
     */
    override fun initializedAndWorking(): Boolean {
        return connected && receivedInput
    }

    /**
     * update state
     */
    override fun update() {
    }

    /**
     * Check whether there is a working TrackerInput for this device.
     *
     * @returns the [TrackerInput] if that is the case, null otherwise.
     */
    override fun getWorkingTracker(): TrackerInput? {
        if(connected && receivedInput) {
            return this
        } else {
            return null
        }
    }

    /**
     * Loads a model representing the [TrackedDevice].
     *
     * @param[device] The device to load the model for.
     * @param[mesh] The [Mesh] to attach the model data to.
     */
    override fun loadModelForMesh(device: TrackedDevice, mesh: Mesh): Mesh {
        TODO("Not yet implemented")
    }

    /**
     * Loads a model representing a kind of [TrackedDeviceType].
     *
     * @param[type] The device type to load the model for, by default [TrackedDeviceType.Controller].
     * @param[mesh] The [Mesh] to attach the model data to.
     */
    override fun loadModelForMesh(type: TrackedDeviceType, mesh: Mesh): Mesh {
        TODO("Not yet implemented")
    }

    /**
     * Attaches a given [TrackedDevice] to a scene graph [Node], camera-relative in case [camera] is non-null.
     *
     * @param[device] The [TrackedDevice] to use.
     * @param[node] The node which should take tracking data from [device].
     * @param[camera] A camera, in case the node should also be added as a child to the camera.
     */
    override fun attachToNode(device: TrackedDevice, node: Node, camera: Camera?) {
        TODO("Not yet implemented")
    }

    /**
     * Returns all tracked devices a given type.
     *
     * @param[ofType] The [TrackedDeviceType] of the devices to return.
     * @return A [Map] of device name to [TrackedDevice]
     */
    override fun getTrackedDevices(ofType: TrackedDeviceType): Map<String, TrackedDevice> {
        TODO("Not yet implemented")
    }

    fun close() {
        if(listeningJob.isActive) {
            listeningJob.cancel()
        }

        sdk.stopMeasurement()
        sdk.close()
    }
}
