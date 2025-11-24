package graphics.scenery.controls

import art.DTrackSDK
import graphics.scenery.Camera
import graphics.scenery.Mesh
import graphics.scenery.Node
import graphics.scenery.utils.lazyLogger
import graphics.scenery.utils.extensions.times
import kotlinx.coroutines.*
import org.joml.*
import org.scijava.ui.behaviour.*
import org.scijava.ui.behaviour.io.InputTriggerConfig
import java.awt.Component
import java.awt.event.KeyEvent
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * DTrack [TrackerInput] interface
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class DTrackTrackerInput(val host: String = "localhost", val port: Int = 5000, var defaultBodyId: String = "body-0", var positonScale: Vector3f = Vector3f(1f)): TrackerInput {
    private var sdk: DTrackSDK = DTrackSDK(InetAddress.getByName(host), port)
    private val logger by lazyLogger()

    var numBodies: Int = 0
        protected set

    var frameCount: Long = 0L
        protected set

    var timestamp: Long = 0L
        protected set

    protected val listeningJob: Job

    data class DTrackBodyState(var quality: Double,
                               var position: Vector3f,
                               var rotation: Quaternionf,
                               var joystickX: Float? = null,
                               var joystickY: Float? = null)

    protected var bodyState = ConcurrentHashMap<String, DTrackBodyState>()

    protected var connected = false
    protected var receivedInput = false

    val inputHandler = MouseAndKeyHandler()
    protected val config: InputTriggerConfig = InputTriggerConfig()
    protected val inputMap = InputTriggerMap()
    protected val behaviourMap = BehaviourMap()

    init {
        logger.info("Connected to $host:$port.")

        inputHandler.setBehaviourMap(behaviourMap)
        inputHandler.setInputMap(inputMap)

        if(!sdk.isDataInterfaceValid) {
            throw IllegalStateException("DTrack initialisation error, could not talk to DTrack on $host, port $port.")
        }

        logger.info("Data interface for $host:$port is valid.")

        if(!sdk.startMeasurement()) {
            throw IllegalStateException("Could not start DTrack measurement on $host, port $port.")
        }

        logger.info("Measurement for $host:$port has started.")

        connected = true

        listeningJob = GlobalScope.launch {
            while(true) {
                if(sdk.receive()) {
                    logger.debug("Received DTrack frame with {} bodies and {} flysticks.", sdk.numBody, sdk.numFlystick)

                    receivedInput = true
                    for(bodyId in 0 until sdk.numBody) {
                        val quality = sdk.getBody(bodyId).quality
                        val loc = sdk.getBody(bodyId).loc
                        val rotation = sdk.getBody(bodyId).rot

                        val x = loc[0].toFloat()/1000.0f
                        val y = loc[1].toFloat()/1000.0f
                        val z = -loc[2].toFloat()/1000.0f

                        val state = bodyState.getOrPut( "body-$bodyId", {
                            DTrackBodyState(
                                quality,
                                Vector3f(x, y, z),
                                Quaternionf().setFromUnnormalized(rotToMatrix3f(rotation))
                            )
                        })

                        state.quality = quality
                        state.rotation.setFromUnnormalized(rotToMatrix3f(rotation))
                        state.position.set(x, y, z).mul(positonScale)
                    }

                    for(flystickId in 0 until sdk.numFlystick) {
                        val flystick = sdk.getFlystick(flystickId)
                        val quality = flystick.quality
                        val loc = flystick.loc
                        val rotation = flystick.rot

                        val x = loc[0].toFloat()/1000.0f
                        val y = loc[1].toFloat()/1000.0f
                        val z = -loc[2].toFloat()/1000.0f

                        val joystickX = flystick.joystick[0].toFloat()
                        val joystickY = flystick.joystick[1].toFloat()

                        val buttons = flystick.button

                        logger.debug("Button state: {}, joystick: {}, {}", flystick.button.joinToString(","), joystickX, joystickY)

                        val state = bodyState.getOrPut("flystick-$flystickId", {
                            DTrackBodyState(
                                quality,
                                Vector3f(x, y, z),
                                Quaternionf().setFromUnnormalized(rotToMatrix3f(rotation)),
                                joystickX,
                                joystickY
                            )
                        })

                        state.quality = quality
                        state.rotation.setFromUnnormalized(rotToMatrix3f(rotation))
                        state.position.set(x, y, z)

                        buttons.forEachIndexed { buttonId, buttonState ->
                            val dtrackButton = when(buttonId) {
                                0 -> DTrackButton.Trigger
                                1 -> DTrackButton.Right
                                2 -> DTrackButton.Center
                                3 -> DTrackButton.Left
                                else -> throw IllegalStateException("Unknown button id")
                            }

                            if(buttonState == 1) {
                                val event = dtrackButton.toKeyEvent()
                                GlobalKeyEventDispatcher.getInstance().dispatchKeyEvent(event.first)
                                inputHandler.keyPressed(event.first)
                                GlobalKeyEventDispatcher.getInstance().dispatchKeyEvent(event.second)
                                inputHandler.keyReleased(event.second)
                            }
                        }
                    }
                }
            }
        }
    }


    /**
     * Adds a behaviour to the map of behaviours, making them available for key bindings
     *
     * @param[behaviourName] The name of the behaviour
     * @param[behaviour] The behaviour to add.
     */
    fun addBehaviour(behaviourName: String, behaviour: Behaviour) {
        behaviourMap.put(behaviourName, behaviour)
    }

    /**
     * Removes a behaviour from the map of behaviours.
     *
     * @param[behaviourName] The name of the behaviour to remove.
     */
    fun removeBehaviour(behaviourName: String) {
        behaviourMap.remove(behaviourName)
    }

    /**
     * Adds a key binding for a given behaviour
     *
     * @param[behaviourName] The behaviour to add a key binding for
     * @param[keys] Which keys should trigger this behaviour?
     */
    fun addKeyBinding(behaviourName: String, vararg keys: String) {
        keys.forEach { key ->
            config.inputTriggerAdder(inputMap, "all").put(behaviourName, key)
        }
    }

    fun addKeyBinding(behaviourName: String, hand: TrackerRole, button: OpenVRHMD.OpenVRButton) {
        config.inputTriggerAdder(inputMap, "all").put(behaviourName, OpenVRHMD.keyBinding(hand, button))
    }

    /**
     * Removes a key binding for a given behaviour
     *
     * @param[behaviourName] The behaviour to remove the key binding for.
     */
    @Suppress("unused")
    fun removeKeyBinding(behaviourName: String) {
        config.inputTriggerAdder(inputMap, "all").put(behaviourName)
    }

    /**
     * Returns the behaviour with the given name, if it exists. Otherwise null is returned.
     *
     * @param[behaviourName] The name of the behaviour
     */
    fun getBehaviour(behaviourName: String): Behaviour? {
        return behaviourMap.get(behaviourName)
    }

    private fun DTrackButton.toKeyEvent(): Pair<KeyEvent, KeyEvent> {
        val keycode = this.toAWTKeyCode()
        return KeyEvent(object: Component() {}, KeyEvent.KEY_PRESSED, System.nanoTime(), 0, keycode.code, keycode.char) to
            KeyEvent(object: Component() {}, KeyEvent.KEY_RELEASED, System.nanoTime() + 10e5.toLong(), 0, keycode.code, keycode.char)
    }

    private fun DTrackButton.toAWTKeyCode(): AWTKey {
        return when(this) {
            DTrackButton.Trigger -> AWTKey(KeyEvent.VK_0)
            DTrackButton.Left -> AWTKey(KeyEvent.VK_3)
            DTrackButton.Center -> AWTKey(KeyEvent.VK_2)
            DTrackButton.Right -> AWTKey(KeyEvent.VK_1)
        }
    }

    operator fun plusAssign(behaviourAndBinding: InputHandler.NamedBehaviourWithKeyBinding) {
        addBehaviour(behaviourAndBinding.name, behaviourAndBinding.behaviour)
        addKeyBinding(behaviourAndBinding.name, behaviourAndBinding.key)
    }

    operator fun minusAssign(name: String) {
        removeBehaviour(name)
        removeKeyBinding(name)
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
        // TODO: Return unit quaternion for the moment, until we can figure out whether the rotation is calibrated or not.
        return Quaternionf()
    }

    /**
     * Returns the orientation of the given device, or a unit quaternion if the device is not found.
     *
     * @returns Matrix4f with orientation
     */
    override fun getOrientation(id: String): Quaternionf {
        return bodyState[id]?.rotation ?: Quaternionf()
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
        return Matrix4f().translation((bodyState[defaultBodyId]?.position ?: Vector3f(0.0f)) * (-1.0f))
    }

    private fun DTrackBodyState.pose(): Matrix4f {
        return Matrix4f().translation(this.position).rotate(this.rotation)
    }

    /**
     * Returns a list of poses for the devices [type] given.
     *
     * @return Pose as Matrix4f
     */
    override fun getPose(type: TrackedDeviceType): List<TrackedDevice> {
        return if(type == TrackedDeviceType.Controller) {
            bodyState.filter { it.key.startsWith("flystick") }.map {
                TrackedDevice(type, it.key, it.value.pose(), System.nanoTime())
            }.toList()
        } else {
            listOf(TrackedDevice(TrackedDeviceType.HMD, "DTrack", getPose(), System.nanoTime()))
        }
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
