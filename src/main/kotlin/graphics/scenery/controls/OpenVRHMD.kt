package graphics.scenery.controls

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import graphics.scenery.*
import graphics.scenery.backends.Display
import graphics.scenery.backends.vulkan.VU
import graphics.scenery.backends.vulkan.VulkanDevice
import graphics.scenery.backends.vulkan.VulkanTexture
import graphics.scenery.backends.vulkan.endCommandBuffer
import graphics.scenery.utils.JsonDeserialisers
import graphics.scenery.utils.lazyLogger
import graphics.scenery.utils.Statistics
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.joml.*
import org.lwjgl.openvr.*
import org.lwjgl.openvr.VR.*
import org.lwjgl.openvr.VRCompositor.*
import org.lwjgl.openvr.VRRenderModels.VRRenderModels_GetRenderModelOriginalPath
import org.lwjgl.openvr.VRSystem.*
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT
import org.scijava.ui.behaviour.*
import org.scijava.ui.behaviour.io.InputTriggerConfig
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.io.File
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

/**
 * TrackerInput implementation of OpenVR
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[seated] Whether the user is assumed to be sitting or not.
 * @property[useCompositor] Whether or not the compositor should be used.
 * @constructor Creates a new OpenVR HMD instance, using the compositor if requested
 */
open class OpenVRHMD(val seated: Boolean = false, val useCompositor: Boolean = true) : TrackerInput, Display, Hubable {

    /** slf4j logger instance */
    protected val logger by lazyLogger()
    /** The Hub to use for communication */
    override var hub: Hub? = null

    /** Has the HMD already been initialised? */
    @Volatile protected var initialized = false
    @Volatile protected var compositorInitialized = false

    /** OpenVR poses structure for all tracked device */
    protected var hmdTrackedDevicePoses: TrackedDevicePose.Buffer = TrackedDevicePose.calloc(k_unMaxTrackedDeviceCount)
    protected var gamePoses: TrackedDevicePose.Buffer = TrackedDevicePose.calloc(k_unMaxTrackedDeviceCount)

    /** error code storage */
    protected val error: IntBuffer = memAllocInt(1)
    /** Storage for the poses of all tracked devices. */
    protected var trackedDevices = ConcurrentHashMap<String, TrackedDevice>()
    /** Cache for per-eye projection matrices */
    protected var eyeProjectionCache: ArrayList<Matrix4f?> = ArrayList()
    /** Cache for head-to-eye transform matrices */
    protected var eyeTransformCache: ArrayList<Matrix4f?> = ArrayList()

    /** When did the last vsync occur? */
    protected val lastVsync: FloatBuffer = memAllocFloat(1)
    /** Current frame count on the HMD */
    protected val frameCount: LongBuffer = memAllocLong(1)
    /** Display frequency of the HMD */
    protected var hmdDisplayFreq: Int = 0

    /** Latency wait time */
    protected var latencyWaitTime = 0L
    /** Whether or not to vsync to the beacons */
    protected var vsyncToPhotons = 0.0f

    /** Per-run frame count */
    protected var frameCountRun = 0
    /** Per-frame rendering time */
    protected var timePerFrame = 0.0f

    /** (De)activate latency debugging */
    var debugLatency = false
    /** Frame count per-vsync */
    protected var frames = 0

    /** disables submission in case of compositor errors */
    protected var disableSubmission: Boolean = false

    @Volatile protected var readyForSubmission: Boolean = false
        private set

    protected val inputHandler = MouseAndKeyHandler()
    protected val config: InputTriggerConfig = InputTriggerConfig()
    protected val inputMap = InputTriggerMap()
    protected val behaviourMap = BehaviourMap()

    var manufacturer: Manufacturer = Manufacturer.Other
        private set
    var trackingSystemName: String = ""
        private set

    private var commandPool = -1L

    override var events = TrackerInputEventHandlers()

    /** Set of keys that are allowed to be repeated. */
    val allowRepeats = HashSet<Pair<OpenVRButton, TrackerRole>>(5, 0.8f)
    protected val keysDown = HashSet<Pair<OpenVRButton, TrackerRole>>(5, 0.8f)

    enum class Manufacturer {
        HTC,
        Oculus,
        WindowsMR,
        Other
    }


    protected data class CompositeModel(
        val thumbnail: String,
        val components: Map<String, CompositeModelComponents>
    )

    protected data class CompositeModelComponents(
        val filename: String?,
        val motion: CompositeModelMotion?,
        @JsonProperty("component_local") val transform: CompositeModelTransform?,
        val visibility: Map<String, Boolean>?
    )

    protected data class CompositeModelMotion(
        val type: String,
        @JsonProperty("controller_axis") val controllerAxis: Int,
        @JsonProperty("component_path") val componentPath: String?,
        @JsonProperty("pressed_path") val pressedPath: String?,
        @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class) val center: Vector3f = Vector3f(0.0f, 0.0f, 0.0f),
        @JsonProperty("rotate_xyz") @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class) val rotation: Vector3f = Vector3f(0.0f, 0.0f, 0.0f),
        @JsonProperty("press_rotation_x") @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class) val pressRotationX: Vector2f = Vector2f(0.0f, 0.0f),
        @JsonProperty("press_rotation_y") @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class) val pressRotationY: Vector2f = Vector2f(0.0f, 0.0f),
        @JsonProperty("press_translate") @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class) val pressTranslate: Vector3f = Vector3f(0.0f, 0.0f, 0.0f),
        @JsonProperty("press_translate_x") @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class) val pressTranslateX: Vector3f = Vector3f(0.0f, 0.0f, 0.0f),
        @JsonProperty("press_translate_y") @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class) val pressTranslateY: Vector3f = Vector3f(0.0f, 0.0f, 0.0f),
        @JsonProperty("pivot") @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class) val pivot: Vector3f = Vector3f(0.0f, 0.0f, 0.0f),
        @JsonProperty("value_mapping") @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class) val valueMapping: Vector2f = Vector2f(0.0f, 0.0f),
        @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class) val axis: Vector3f = Vector3f(1.0f, 0.0f, 0.0f),
        @JsonProperty("controller_button") val controllerButton: Int
    )

    data class CompositeModelTransform(
        @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class) val origin: Vector3f,
        @JsonProperty("rotate_xyz") @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class) val rotation: Vector3f
    )

    val eventTypes: Map<Int, String> = VR::class.java.declaredFields
        .filter { it.name.startsWith("EVREventType_VREvent_")}
        .map { it.getInt(null) to it.name.substringAfter("EVREventType_VREvent_") }
        .toMap()

    init {
        logger.debug("Registered ${eventTypes.size} event types")

        inputHandler.setBehaviourMap(behaviourMap)
        inputHandler.setInputMap(inputMap)

        error.put(0, -1)

        try {
            val token = VR_InitInternal(error, EVRApplicationType_VRApplication_Scene)

            if (error[0] == 0) {
                OpenVR.create(token)
                logger.info("Initializing OpenVR ($token)...")
            }

            if (error[0] != 0) {
                if (error[0] == 108) {
                    // only warn if no HMD found.
                    logger.warn("No HMD found. Are all cables connected?")
                } else {
                    logger.error("Initialization error - ${VR_GetVRInitErrorAsEnglishDescription(error[0])}")
                }

                initialized = false
            } else {
                initialized = true
                logger.info("OpenVR library: Initialized.")

                hmdDisplayFreq = ETrackedDeviceProperty_Prop_DisplayFrequency_Float

                timePerFrame = 1.0f / hmdDisplayFreq

                if (useCompositor) {
                    initCompositor()
                }

                eyeProjectionCache.add(null)
                eyeProjectionCache.add(null)

                eyeTransformCache.add(null)
                eyeTransformCache.add(null)

                val m = getStringProperty(k_unTrackedDeviceIndex_Hmd, ETrackedDeviceProperty_Prop_ManufacturerName_String)
                manufacturer = when {
                    m.contains("HTC") -> Manufacturer.HTC
                    m.contains("WindowsMR") -> Manufacturer.WindowsMR
                    m.contains("Oculus") -> Manufacturer.Oculus
                    else -> Manufacturer.Other
                }
                trackingSystemName = getStringProperty(k_unTrackedDeviceIndex_Hmd, ETrackedDeviceProperty_Prop_TrackingSystemName_String)
                val driverVersion = getStringProperty(k_unTrackedDeviceIndex_Hmd, ETrackedDeviceProperty_Prop_DriverVersion_String)

                logger.info("Initialized device $manufacturer $trackingSystemName $driverVersion with render target size ${getRenderTargetSize().x()}x${getRenderTargetSize().y()}")
            }

        } catch(e: UnsatisfiedLinkError) {
            logger.error("OpenVR support library not found, skipping initialization.")
            logger.error(e.message + "\n" + e.stackTrace.joinToString("\n"))
            initialized = false
        }


        // Having vsync enabled might lead to wrong prediction and "swimming"
        // artifacts.
        hub?.get<Settings>()?.let { settings ->
            logger.info("Disabling vsync, as frame swap governed by compositor.")
            settings.set("Renderer.DisableVsync", true)
        }
    }

    private fun idToEventType(id: Int): String = eventTypes.getOrDefault(id, "Unknown event($id)")

    /**
     * Initialises the OpenVR compositor
     */
    fun initCompositor() {
        if(!initialized) {
            return
        }

        logger.info("Compositor initialized")

        if (seated) {
            VRCompositor_SetTrackingSpace(ETrackingUniverseOrigin_TrackingUniverseSeated)
        } else {
            VRCompositor_SetTrackingSpace(ETrackingUniverseOrigin_TrackingUniverseStanding)
        }

        val err = memAllocInt(1)
        vsyncToPhotons = VRSystem_GetFloatTrackedDeviceProperty(k_unTrackedDeviceIndex_Hmd, ETrackedDeviceProperty_Prop_SecondsFromVsyncToPhotons_Float, err)
        memFree(err)

        compositorInitialized = true
    }

    /**
     * Runs the OpenVR shutdown hooks
     */
    fun close() {
        initialized = false
        VR_ShutdownInternal()
    }

    /**
     * Check whether there is a working TrackerInput for this device.
     *
     * @returns the [TrackerInput] if that is the case, null otherwise.
     */
    override fun getWorkingTracker(): TrackerInput? {
        return if(initialized) {
            this
        } else {
            null
        }
    }

    /**
     * Returns a [Display] instance, if working currently
     *
     * @return Either a [Display] instance, or null.
     */
    override fun getWorkingDisplay(): Display? {
        return if(initialized) {
            this
        } else {
            null
        }
    }

    /**
     * Check whether the HMD is initialized and working
     *
     * @return True if HMD is initialiased correctly and working properly
     */
    override fun initializedAndWorking(): Boolean {
        return initialized
    }

    /**
     * Returns the optimal render target size for the HMD as 2D vector
     *
     * @return Render target size as 2D vector
     */
    final override fun getRenderTargetSize(): Vector2i {
            val x = memAllocInt(1)
            val y = memAllocInt(1)

            VRSystem_GetRecommendedRenderTargetSize(x, y)

        val width = x.get(0)
        val height = y.get(0)

        memFree(x)
        memFree(y)

        return Vector2i(width, height)
    }

    /**
     * Returns the field of view in degrees
     *
     * @return FOV in degrees
     */
    @Suppress("unused")
    fun getFOV(direction: Int): Float {
        val err = memAllocInt(1)
        val fov = VRSystem_GetFloatTrackedDeviceProperty(k_unTrackedDeviceIndex_Hmd, direction, err)

        if (fov == 0f) {
            return 55.0f
        } else if (fov <= 10.0f) {
            return fov * 57.2957795f
        }

        return fov
    }

    /**
     * Returns the per-eye projection matrix
     *
     * @param[eye] The index of the eye
     * @return Matrix4f containing the per-eye projection matrix
     */
    @Synchronized override fun getEyeProjection(eye: Int, nearPlane: Float, farPlane: Float): Matrix4f {
        if (eyeProjectionCache[eye] == null) {
            val projection = HmdMatrix44.calloc()
            VRSystem_GetProjectionMatrix(eye, nearPlane, farPlane, projection)

            eyeProjectionCache[eye] = projection.toMatrix4f().transpose()
        }

        return eyeProjectionCache[eye] ?: throw IllegalStateException("No cached projection for eye $eye found.")
    }

    /**
     * Returns the per-eye transform that moves from head to eye
     *
     * @param[eye] The eye index
     * @return Matrix4f containing the transform
     */
    @Synchronized override fun getHeadToEyeTransform(eye: Int): Matrix4f {
        if (eyeTransformCache[eye] == null) {
            val transform = HmdMatrix34.calloc()
            VRSystem_GetEyeToHeadTransform(eye, transform)

            eyeTransformCache[eye] = transform.toMatrix4f()

            logger.trace("Head-to-eye #{}: {}", eye, eyeTransformCache[eye].toString())
        }

        val current = eyeTransformCache[eye] ?: throw IllegalStateException("No cached eye transform for eye $eye found.")

        return Matrix4f(current)
    }

    /**
     * Returns the inter-pupillary distance (IPD)
     *
     * @return IPD as Float
     */
    override fun getIPD(): Float {
        val err = memAllocInt(1)
        if (!initialized) {
            return 0.065f
        } else {
            return VRSystem_GetFloatTrackedDeviceProperty(k_unTrackedDeviceIndex_Hmd, ETrackedDeviceProperty_Prop_UserIpdMeters_Float, err)
        }
    }

    /**
     * Returns the orientation of the HMD
     *
     * @returns Matrix4f with orientation
     */
    override fun getOrientation(): Quaternionf {
        val q = Quaternionf().setFromUnnormalized(getPose())

        return q
    }

    override fun getOrientation(id: String): Quaternionf {
        val device = trackedDevices.get(id)
        val q = Quaternionf()

        if (device != null) {
            q.setFromUnnormalized(device.pose)
        }

        return q
    }

    /**
     * Returns the absolute position as Vector3f
     *
     * @return HMD position as Vector3f
     */
    override fun getPosition(): Vector3f {
        val m = getPose()
        val d = Vector3f(-1.0f * m.get(3, 0), -1.0f * m.get(3, 1), -1.0f * m.get(3, 2))
        // the position is already rotated by the orientation therefore we need to inverse that the get the absolute position
        return d.rotate(getOrientation().conjugate())
    }

    /**
     * Queries the OpenVR runtime for updated poses and stores them
     */
    @Synchronized override fun update() {
        if (!initialized) {
            return
        }

        if (useCompositor && !compositorInitialized) {
            return
        }

        VRCompositor_WaitGetPoses(hmdTrackedDevicePoses, gamePoses)

        for (device in (0 until k_unMaxTrackedDeviceCount)) {
            val isValid = hmdTrackedDevicePoses.get(device).bPoseIsValid()

            if (isValid) {
                val type = when (VRSystem_GetTrackedDeviceClass(device)) {
                    ETrackedDeviceClass_TrackedDeviceClass_Controller -> TrackedDeviceType.Controller
                    ETrackedDeviceClass_TrackedDeviceClass_HMD -> TrackedDeviceType.HMD
                    ETrackedDeviceClass_TrackedDeviceClass_TrackingReference -> TrackedDeviceType.BaseStation
                    ETrackedDeviceClass_TrackedDeviceClass_GenericTracker -> TrackedDeviceType.Generic

                    ETrackedDeviceClass_TrackedDeviceClass_Invalid -> TrackedDeviceType.Invalid
                    else -> TrackedDeviceType.Invalid
                }

                if (type == TrackedDeviceType.Invalid) {
                    continue
                }

                val d = trackedDevices.computeIfAbsent("$type-$device") {
                    val timestamp = System.nanoTime()
                    val deviceName = "$type-$device"
                    val deviceModelPath = getStringProperty(device, ETrackedDeviceProperty_Prop_RenderModelName_String)
                    val td = TrackedDevice(type, deviceName, Matrix4f().identity(), timestamp = timestamp)
                    td.modelPath = deviceModelPath

                    val role = VRSystem_GetControllerRoleForTrackedDeviceIndex(device)
                    td.role = when(role) {
                        1 -> TrackerRole.LeftHand
                        2 -> TrackerRole.RightHand
                        else -> TrackerRole.Invalid
                    }

                    GlobalScope.launch {
                        try {
                            val c = Mesh()
                            c.name = deviceName
                            loadModelForMesh(td, c)
                            td.model = c
                        } catch(e: Exception) {
                            logger.warn("Could not load model for $deviceName, device will not be visible in the scene. ($e)")
                            td.model = null
                        }

                        events.onDeviceConnect.forEach { it.invoke(this@OpenVRHMD, td, timestamp) }
                    }

                    td
                }

                if(type == TrackedDeviceType.Controller) {
                    if (d.metadata !is VRControllerState) {
                        d.metadata = VRControllerState.calloc()
                    }

                    // role might change during use
                    val role = VRSystem_GetControllerRoleForTrackedDeviceIndex(device)
                    d.role = when(role) {
                        1 -> TrackerRole.LeftHand
                        2 -> TrackerRole.RightHand
                        else -> TrackerRole.Invalid
                    }

                    val state = d.metadata as? VRControllerState
                    if(state != null) {
                        VRSystem_GetControllerState(device, state)

                        logger.trace("Axis of {}: {} {}, button {}", d.role, state.rAxis(0).x(), state.rAxis(0).y(), state.ulButtonPressed())

                        when {
                            (state.rAxis(0).x() > 0.5f && state.rAxis(0).y().absoluteValue < 0.5f && state.isDPadAction()) -> OpenVRButton.Right.toKeyEvent(d.role)
                            (state.rAxis(0).x() < -0.5f && state.rAxis(0).y().absoluteValue < 0.5f && state.isDPadAction()) -> OpenVRButton.Left.toKeyEvent(d.role)
                            (state.rAxis(0).y() > 0.5f && state.rAxis(0).x().absoluteValue < 0.5f && state.isDPadAction()) -> OpenVRButton.Up.toKeyEvent(d.role)
                            (state.rAxis(0).y() < -0.5f && state.rAxis(0).x().absoluteValue < 0.5f && state.isDPadAction()) -> OpenVRButton.Down.toKeyEvent(d.role)
                            else -> null
                        }?.let { event ->
                            logger.debug("Pressing {}/{} on {}", KeyEvent.getKeyText(event.first.keyCode), KeyEvent.getKeyText(event.second.keyCode), d.role)

                            GlobalKeyEventDispatcher.getInstance().dispatchKeyEvent(event.first)
                            inputHandler.keyPressed(event.first)
                            GlobalKeyEventDispatcher.getInstance().dispatchKeyEvent(event.second)
                            inputHandler.keyReleased(event.second)
                        }
                    }
                }

                val pose = hmdTrackedDevicePoses.get(device).mDeviceToAbsoluteTracking()

                d.pose = pose.toMatrix4f()
                d.timestamp = System.nanoTime()
                d.velocity = hmdTrackedDevicePoses.get(device).vVelocity().toVector3f()
                d.angularVelocity = hmdTrackedDevicePoses.get(device).vAngularVelocity().toVector3f()

                if (type == TrackedDeviceType.HMD) {
                    d.pose.invert()
                }
            }
        }

        val event = VREvent.calloc()
        while(VRSystem_PollNextEvent(event)) {
            val role = when(VRSystem_GetControllerRoleForTrackedDeviceIndex(event.trackedDeviceIndex())) {
                ETrackedControllerRole_TrackedControllerRole_LeftHand -> TrackerRole.LeftHand
                ETrackedControllerRole_TrackedControllerRole_RightHand -> TrackerRole.RightHand
                ETrackedControllerRole_TrackedControllerRole_Invalid -> TrackerRole.Invalid
                else -> TrackerRole.Invalid
            }

            logger.debug("Event ${idToEventType(event.eventType())} for role $role")

            if(event.eventType() == EVREventType_VREvent_ButtonPress) {
                val button = event.data().controller().button()

                OpenVRButton.values().find { it.internalId == button }?.let {
                    logger.debug("Button pressed: $it on $role")
                    val keyEvent = it.toKeyEvent(role)
                    GlobalKeyEventDispatcher.getInstance().dispatchKeyEvent(keyEvent.first)
                    inputHandler.keyPressed(keyEvent.first)
                    keysDown.add(it to role)
                    GlobalKeyEventDispatcher.getInstance().dispatchKeyEvent(keyEvent.second)
                }
            }

            if(event.eventType() == EVREventType_VREvent_ButtonUnpress) {
                val button = event.data().controller().button()

                OpenVRButton.values().find { it.internalId == button }?.let {
                    logger.debug("Button unpressed: $it on $role")
                    val keyEvent = it.toKeyEvent(role)
                    GlobalKeyEventDispatcher.getInstance().dispatchKeyEvent(keyEvent.second)
                    inputHandler.keyReleased(keyEvent.second)
                    keysDown.remove(it to role)
                }
            }

            if(event.eventType() == EVREventType_VREvent_MouseMove) {
                val x = event.data().mouse().x()
                val y = event.data().mouse().y()
                val down = event.data().mouse().button()

                logger.debug("Touchpad moved $x $y, down=$down")
            }
        }
        event.free()

        keysDown.forEach {
            if(it in allowRepeats) {
                val e = it.first.toKeyEvent(it.second)
                inputHandler.keyPressed(e.first)
            }
        }

        if (keysDown.isNotEmpty()) {
            // do a simulated mouse movement to trigger drag behavior updates
            inputHandler.mouseMoved(
                MouseEvent(
                    object : Component() {}, MouseEvent.MOUSE_CLICKED, System.nanoTime(),
                    0, 0, 0, 0, 0, 1, false, 0
                )
            )
        }


        hub?.get<Statistics>()?.let { stats ->
            val timing = CompositorFrameTiming.calloc(1)
            if(VRCompositor_GetFrameTiming(timing)) {
                stats.add("OpenVR.NumFramePresents", timing.m_nNumFramePresents(), false)
                stats.add("OpenVR.NumFrameMisPresents", timing.m_nNumMisPresented(), false)
                stats.add("OpenVR.NumDroppedFrames", timing.m_nNumDroppedFrames(), false)

                stats.add("OpenVR.SystemTimeInSeconds", timing.m_flSystemTimeInSeconds())
                stats.add("OpenVR.PreSubmitGPUTime", timing.m_flPreSubmitGpuMs())
                stats.add("OpenVR.PostSubmitGPUTime", timing.m_flPostSubmitGpuMs())
                stats.add("OpenVR.TotalRenderGPUTime", timing.m_flTotalRenderGpuMs())
                stats.add("OpenVR.CompositorRenderGPUTime", timing.m_flCompositorRenderGpuMs())
                stats.add("OpenVR.CompositorRenderCPUTime", timing.m_flCompositorRenderCpuMs())
                stats.add("OpenVR.FrameInterval", timing.m_flClientFrameIntervalMs())
                stats.add("OpenVR.TimeCPUBlockedForPresent", timing.m_flPresentCallCpuMs())
                stats.add("OpenVR.TimeCPUWaitForPresent", timing.m_flWaitForPresentCpuMs())
                stats.add("OpenVR.TimeSpentForSubmit", timing.m_flSubmitFrameMs())
            }

            timing.free()
        }

        readyForSubmission = true
    }

    private fun VRControllerState.isDPadAction(): Boolean {
        return if(manufacturer == Manufacturer.HTC) {
            (this.ulButtonPressed() and (1L shl EVRButtonId_k_EButton_SteamVR_Touchpad) != 0L)
        } else {
            true
        }
    }


    /**
     * Query the HMD whether a compositor is used or the renderer should take
     * care of displaying on the HMD on its own.
     *
     * @return True if the HMD has a compositor
     */
    override fun hasCompositor(): Boolean {
        if (useCompositor && initialized) {
            return true
        }

        return false
    }

    /**
     * Submit OpenGL texture IDs to the compositor. The texture is assumed to have the left eye in the
     * left half, right eye in the right half.
     *
     * @param[textureId] OpenGL Texture ID of the left eye texture
     */
    @Synchronized override fun submitToCompositor(textureId: Int) {
        VRCompositor_WaitGetPoses(hmdTrackedDevicePoses, gamePoses)
        update()
        if (disableSubmission || !readyForSubmission) {
            return
        }


        stackPush().use { stack ->
            try {
                if (disableSubmission || !readyForSubmission) {
                    return
                }

                readyForSubmission = false

                val texture = Texture.calloc(stack)
                    .eColorSpace(EColorSpace_ColorSpace_Gamma)
                    .eType(ETextureType_TextureType_OpenGL)
                    .handle(textureId.toLong())

                val boundsLeft = VRTextureBounds.calloc(stack).set(0.0f, 0.0f, 0.5f, 1.0f)
                val errorLeft = VRCompositor_Submit(EVREye_Eye_Left, texture, boundsLeft, 0)

                val boundsRight = VRTextureBounds.calloc(stack).set(0.5f, 0.0f, 1.0f, 1.0f)
                val errorRight = VRCompositor_Submit(EVREye_Eye_Right, texture, boundsRight, 0)

                if (errorLeft != EVRCompositorError_VRCompositorError_None
                    || errorRight != EVRCompositorError_VRCompositorError_None) {
                    logger.error("Compositor error: ${translateError(errorLeft)} ($errorLeft)/${translateError(errorRight)} ($errorRight)")
                }
            } catch (e: java.lang.Error) {
                logger.error("Compositor submission failed, please restart the HMD, SteamVR and the application.")
                disableSubmission = true
            }
        }
    }

    override fun submitToCompositorVulkan(width: Int, height: Int, format: Int,
                                          instance: VkInstance, device: VulkanDevice,
                                          queue: VulkanDevice.QueueWithMutex, image: Long) {
//        update()
        if (disableSubmission || !readyForSubmission) {
            return
        }

        stackPush().use { stack ->
            val textureData = VRVulkanTextureData.calloc(stack)
                .m_nImage(image)
                .m_pInstance(instance.address())
                .m_pPhysicalDevice(device.physicalDevice.address())
                .m_pDevice(device.vulkanDevice.address())
                .m_pQueue(queue.queue.address())
                .m_nQueueFamilyIndex(device.queueIndices.graphicsQueue.first)
                .m_nWidth(width)
                .m_nHeight(height)
                .m_nFormat(format)
                .m_nSampleCount(1)

            val texture = Texture.calloc(stack)
                .handle(textureData.address())
                .eColorSpace(EColorSpace_ColorSpace_Gamma)
                .eType(ETextureType_TextureType_Vulkan)

            readyForSubmission = false

            if(commandPool == -1L) {
                commandPool = device.createCommandPool(device.queueIndices.graphicsQueue.first)
            }

            val subresourceRange = VkImageSubresourceRange.calloc(stack)
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1)

            // transition input texture to VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            // as expected by SteamVR.
            with(VU.newCommandBuffer(device, commandPool, autostart = true)) {
                // transition source attachment
                VulkanTexture.transitionLayout(image,
                    KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    subresourceRange = subresourceRange,
                    commandBuffer = this,
                    srcStage = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                    dstStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT
                )

                logger.trace("Submitting left...")
                val boundsLeft = VRTextureBounds.calloc(stack).set(0.0f, 0.0f, 0.5f, 1.0f)
                val errorLeft = VRCompositor_Submit(EVREye_Eye_Left, texture, boundsLeft, EVRSubmitFlags_Submit_Default)

                logger.trace("Submitting right...")
                val boundsRight = VRTextureBounds.calloc(stack).set(0.5f, 0.0f, 1.0f, 1.0f)
                val errorRight = VRCompositor_Submit(EVREye_Eye_Right, texture, boundsRight, EVRSubmitFlags_Submit_Default)

                // NOTE: Here, an "unsupported texture type" error can be thrown if the required Vulkan
                // device or instance extensions have not been loaded -- even if the texture has the correct
                // format. The solution is to reinitialize the renderer then, in order to pick up these extensions.
                if (errorLeft != EVRCompositorError_VRCompositorError_None
                    || errorRight != EVRCompositorError_VRCompositorError_None) {
                    logger.error("Compositor error: ${translateError(errorLeft)} ($errorLeft)/${translateError(errorRight)} ($errorRight)")
                }

                // transition source attachment
                VulkanTexture.transitionLayout(image,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    subresourceRange = subresourceRange,
                    commandBuffer = this,
                    srcStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    dstStage = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                )

                endCommandBuffer(device, commandPool, queue, true, true)
            }
        }
    }

    fun vibrate(controller: TrackedDevice, duration: Int = 1000) {
        val id = controller.name.substringAfterLast("-").toInt()
        VRSystem_TriggerHapticPulse(id, 0, duration.toShort())
    }


    /**
     * Returns a [List] of Vulkan instance extensions required by the device.
     *
     * @return [List] of strings containing the required instance extensions
     */
    override fun getVulkanInstanceExtensions(): List<String> {
        stackPush().use { stack ->
            val buffer = stack.calloc(1024)
            val count = VRCompositor_GetVulkanInstanceExtensionsRequired(buffer)

            logger.debug("Querying required vulkan instance extensions...")
            return if (count == 0) {
                listOf()
            } else {
                val extensions = VRCompositor_GetVulkanInstanceExtensionsRequired(count).split(" ")

                logger.debug("Vulkan required instance extensions are: ${extensions.joinToString(", ")}")
                return extensions
            }
        }
    }

    /**
     * Returns a [List] of Vulkan device extensions required by the device.
     *
     * @return [List] of strings containing the required device extensions
     */
    override fun getVulkanDeviceExtensions(physicalDevice: VkPhysicalDevice): List<String> {
        stackPush().use { stack ->
            val buffer = stack.calloc(1024)
            val count = VRCompositor_GetVulkanDeviceExtensionsRequired(physicalDevice.address(), buffer)

            logger.debug("Querying required vulkan device extensions...")
            return if (count == 0) {
                listOf()
            } else {
                val extensions = VRCompositor_GetVulkanDeviceExtensionsRequired(physicalDevice.address(), count).split(" ")

                logger.debug("Vulkan required device extensions are: ${extensions.joinToString(", ")}")
                return extensions
            }
        }
    }

    private fun getStringProperty(deviceIndex: Int, property: Int): String {
        stackPush().use { stack ->
            val buffer = stack.calloc(1024)

            val size = VRSystem_GetStringTrackedDeviceProperty(deviceIndex, property, buffer, null)
            if (size == 0) {
                return ""
            }

            val propertyArray = ByteArray(size - 1)
            buffer.get(propertyArray, 0, size - 1)

            return String(propertyArray, Charset.defaultCharset())
        }
    }

    /**
     * Queries the OpenVR runtime whether the user is using a sitting or standing configuration
     */
    fun getExperience(): Int {
        return if (seated) {
            ETrackingUniverseOrigin_TrackingUniverseSeated
        } else {
            ETrackingUniverseOrigin_TrackingUniverseStanding
        }
    }

    /**
     * Returns the HMD pose
     *
     * @return HMD pose as Matrix4f
     */
    override fun getPose(): Matrix4f {
        return this.trackedDevices.values.firstOrNull { it.type == TrackedDeviceType.HMD }?.pose ?: Matrix4f().identity()
    }

    /**
     * Returns the HMD pose for a given eye.
     *
     * @param[eye] The eye to return the pose for.
     * @return HMD pose as Matrix4f
     */
    override fun getPoseForEye(eye: Int): Matrix4f {
        val p = this.getPose()
        val e = this.getHeadToEyeTransform(eye).invert()

        e.mul(p)

        return e
    }

    /**
     * Returns the HMD pose
     *
     * @param[type] Type of the tracked device to get the pose for
     * @return HMD pose as Matrix4f
     */
    override fun getPose(type: TrackedDeviceType): List<TrackedDevice> {
        return this.trackedDevices.values.filter { it.type == type }
    }

    /**
     * Extension function to convert from HmdMatric34_t to Matrix4f
     *
     * @return 4x4 Matrix4f created from the original matrix
     */
    fun HmdMatrix34.toMatrix4f(): Matrix4f {
        val m = FloatArray(12)
        this.m().get(m)

        return Matrix4f(
            m[0], m[4], m[8], 0.0f,
            m[1], m[5], m[9], 0.0f,
            m[2], m[6], m[10], 0.0f,
            m[3], m[7], m[11], 1.0f
        )
    }

    /**
     * Extension function to convert a HmdMatrix44_t to a Matrix4f
     *
     * @return 4x4 Matrix4f created from the original matrix
     */
    fun HmdMatrix44.toMatrix4f(): Matrix4f {
        return Matrix4f(this.m())
    }


    /**
     * Loads a model representing the [TrackedDevice].
     *
     * @param[device] The device to load the model for.
     * @param[mesh] The [Mesh] to attach the model data to.
     */
    override fun loadModelForMesh(device: TrackedDevice, mesh: Mesh): Mesh {
        val modelName = device.modelPath ?: device.name

        stackPush().use { stack ->
            val pathBuffer = stack.calloc(1024)
            val error = stack.callocInt(1)

            val l = VRRenderModels_GetRenderModelOriginalPath(modelName, pathBuffer, error)
            val pathArray = ByteArray(l-1)
            pathBuffer.get(pathArray, 0, l-1)
            val path = String(pathArray, Charset.forName("UTF-8"))

            logger.info("Loading model for $modelName from $path")

            val modelPath = path.replace('\\', '/')

            mesh.name = modelPath.substringAfterLast('/')
            loadMeshFromModelPath(device.type, modelPath, mesh)

            return mesh
        }
    }

    /**
     * Loads a model representing a kind of [TrackedDeviceType].
     *
     * @param[type] The device type to load the model for, by default [TrackedDeviceType.Controller].
     * @param[mesh] The [Mesh] to attach the model data to.
     */
    override fun loadModelForMesh(type: TrackedDeviceType, mesh: Mesh): Mesh {
        var modelName = when(type) {
            TrackedDeviceType.HMD -> "generic_hmd"
            TrackedDeviceType.Controller -> "vr_controller_vive_1_5"
            TrackedDeviceType.BaseStation -> "lh_basestation_vive"
            TrackedDeviceType.Generic -> "generic_tracker"

            else -> {
                logger.warn("No model available for $type")
                return mesh
            }
        }

        stackPush().use { stack ->
            val pathBuffer = stack.calloc(1024)
            val error = stack.callocInt(1)

            // let's see if we can get the actual render model, and not just a guess
            trackedDevices.filter { it.key.contains(type.toString()) }.entries.firstOrNull()?.let {
                modelName = it.value.name
                logger.debug("Found better model for $type, setting model name to $modelName")
            }

            val l = VRRenderModels_GetRenderModelOriginalPath(modelName, pathBuffer, error)
            val pathArray = ByteArray(l-1)
            pathBuffer.get(pathArray, 0, l-1)
            val path = String(pathArray, Charset.forName("UTF-8"))

            logger.info("Loading model for $modelName from $path")

            val modelPath = path.replace('\\', '/')

            mesh.name = modelPath.substringAfterLast('/')

            loadMeshFromModelPath(type, modelPath, mesh)

            return mesh
        }
    }

    /**
     * Returns all tracked devices a given type.
     *
     * @param[ofType] The [TrackedDeviceType] of the devices to return.
     * @return A [Map] of device name to [TrackedDevice]
     */
    override fun getTrackedDevices(ofType: TrackedDeviceType): Map<String, TrackedDevice> {
        return trackedDevices.filter { it.value.type == ofType }
    }

    /**
     * Attaches a given [TrackedDevice] to a scene graph [Node], camera-relative in case [camera] is non-null.
     *
     * @param[device] The [TrackedDevice] to use.
     * @param[node] The node which should take tracking data from [device].
     * @param[camera] A camera, in case the node should also be added as a child to the camera.
     */
    override fun attachToNode(device: TrackedDevice, node: Node, camera: Camera?) {
        if(device.type != TrackedDeviceType.Controller) {
            logger.warn("No idea how to attach device type ${device.type} to a node, sorry.")
            return
        }

        logger.info("Adding child $node to $camera")
        camera?.getScene()?.addChild(node)

        node.update.add {
            this.getPose(TrackedDeviceType.Controller).firstOrNull { it.name == device.name }?.let { controller ->

                node.metadata["TrackedDevice"] = controller
                node.ifSpatial {
                    wantsComposeModel = false
                    model.identity()
                    camera?.let {
                        model.translate(it.spatial().position)
                    }
                    model.mul(controller.pose)

//                logger.info("Updating pose of $controller, ${node.model}")

                    needsUpdate = false
                    needsUpdateWorld = true
                }
            }
        }
    }

    /**
     * Fades the view on the HMD to the black.
     */
    fun fadeToBlack(seconds: Float = 0.1f) {
        fadeToColor(color = Vector4f(0f,0f,0f,1f), seconds)
    }

    /**
     * Removes any previously overlayed color.
     */
    fun fateToClear(seconds: Float = 0.1f) {
        fadeToColor(color = Vector4f(0f), seconds)
    }

    /**
     * Fades the view on the HMD to the specified color.
     *
     * The fade will take [seconds], and the color values are between 0.0 and 1.0. This color is faded on top of the scene based on the alpha
     * parameter. Removing the fade color instantly would be fadeToColor( 0.0, 0.0, 0.0, 0.0, 0.0 ). Values are in un-premultiplied alpha space.
     */
    override fun fadeToColor(color: Vector4f, seconds: Float) {
        VRCompositor_FadeToColor(seconds, color.x, color.y, color.z, color.w, false)
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

    fun addKeyBinding(behaviourName: String, hand: TrackerRole, button: OpenVRButton) {
        config.inputTriggerAdder(inputMap, "all").put(behaviourName, keyBinding(hand, button))
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

    /**
     * Class to represent all buttons supported by OpenVR.
     */
    enum class OpenVRButton(val internalId: Int) {
        Left(EVRButtonId_k_EButton_DPad_Left),
        Right(EVRButtonId_k_EButton_DPad_Right),
        Up(EVRButtonId_k_EButton_DPad_Up),
        Down(EVRButtonId_k_EButton_DPad_Down),
        Menu(EVRButtonId_k_EButton_ApplicationMenu),
        Side(EVRButtonId_k_EButton_Grip),
        Trigger(EVRButtonId_k_EButton_SteamVR_Trigger),
        A(EVRButtonId_k_EButton_A),
        System(EVRButtonId_k_EButton_System),
        Touchpad(EVRButtonId_k_EButton_SteamVR_Touchpad),
        IndexJoystick(EVRButtonId_k_EButton_IndexController_JoyStick),
        IndexA(EVRButtonId_k_EButton_IndexController_A),
        IndexB(EVRButtonId_k_EButton_IndexController_B),
        ProximitySensor(EVRButtonId_k_EButton_ProximitySensor),
        Axis0(EVRButtonId_k_EButton_Axis0),
        Axis1(EVRButtonId_k_EButton_Axis1),
        Axis2(EVRButtonId_k_EButton_Axis2),
        Axis3(EVRButtonId_k_EButton_Axis3),
        Axis4(EVRButtonId_k_EButton_Axis4),
        DashboardBack(EVRButtonId_k_EButton_Dashboard_Back)
    }

    companion object {
        private val logger by lazyLogger()

        protected val keyMap: HashMap<Pair<TrackerRole, OpenVRButton>, AWTKey> = hashMapOf(
            (TrackerRole.LeftHand to OpenVRButton.Left) to AWTKey(KeyEvent.VK_H),
            (TrackerRole.LeftHand to OpenVRButton.Right) to AWTKey(KeyEvent.VK_L),
            (TrackerRole.LeftHand to OpenVRButton.Up) to AWTKey(KeyEvent.VK_K),
            (TrackerRole.LeftHand to OpenVRButton.Down) to AWTKey(KeyEvent.VK_J),
            (TrackerRole.LeftHand to OpenVRButton.Menu) to AWTKey(KeyEvent.VK_M),
            (TrackerRole.LeftHand to OpenVRButton.Trigger) to AWTKey(KeyEvent.VK_T),
            (TrackerRole.LeftHand to OpenVRButton.Side) to AWTKey(KeyEvent.VK_X),

            (TrackerRole.LeftHand to OpenVRButton.A) to AWTKey(KeyEvent.VK_Q),
            (TrackerRole.LeftHand to OpenVRButton.System) to AWTKey(KeyEvent.VK_E),
            (TrackerRole.LeftHand to OpenVRButton.Touchpad) to AWTKey(KeyEvent.VK_R),
            (TrackerRole.LeftHand to OpenVRButton.IndexJoystick) to AWTKey(KeyEvent.VK_Z),
            (TrackerRole.LeftHand to OpenVRButton.IndexA) to AWTKey(KeyEvent.VK_I),
            (TrackerRole.LeftHand to OpenVRButton.IndexB) to AWTKey(KeyEvent.VK_O),
            (TrackerRole.LeftHand to OpenVRButton.ProximitySensor) to AWTKey(KeyEvent.VK_P),
            (TrackerRole.LeftHand to OpenVRButton.Axis0) to AWTKey(KeyEvent.VK_F),
            (TrackerRole.LeftHand to OpenVRButton.Axis1) to AWTKey(KeyEvent.VK_G),
            (TrackerRole.LeftHand to OpenVRButton.Axis2) to AWTKey(KeyEvent.VK_C),
            (TrackerRole.LeftHand to OpenVRButton.Axis3) to AWTKey(KeyEvent.VK_V),
            (TrackerRole.LeftHand to OpenVRButton.Axis4) to AWTKey(KeyEvent.VK_B),
            (TrackerRole.LeftHand to OpenVRButton.DashboardBack) to AWTKey(KeyEvent.VK_0),

            (TrackerRole.RightHand to OpenVRButton.Left) to AWTKey(KeyEvent.VK_A),
            (TrackerRole.RightHand to OpenVRButton.Right) to AWTKey(KeyEvent.VK_D),
            (TrackerRole.RightHand to OpenVRButton.Up ) to AWTKey(KeyEvent.VK_W),
            (TrackerRole.RightHand to OpenVRButton.Down) to AWTKey(KeyEvent.VK_S),
            (TrackerRole.RightHand to OpenVRButton.Menu) to AWTKey(KeyEvent.VK_N),
            (TrackerRole.RightHand to OpenVRButton.Trigger) to AWTKey(KeyEvent.VK_U),
            (TrackerRole.RightHand to OpenVRButton.Side) to AWTKey(KeyEvent.VK_Y),
            
            (TrackerRole.RightHand to OpenVRButton.A) to AWTKey(KeyEvent.VK_1),
            (TrackerRole.RightHand to OpenVRButton.System) to AWTKey(KeyEvent.VK_2),
            (TrackerRole.RightHand to OpenVRButton.Touchpad) to AWTKey(KeyEvent.VK_3),
            (TrackerRole.RightHand to OpenVRButton.IndexJoystick) to AWTKey(KeyEvent.VK_4),
            (TrackerRole.RightHand to OpenVRButton.IndexA) to AWTKey(KeyEvent.VK_5),
            (TrackerRole.RightHand to OpenVRButton.IndexB) to AWTKey(KeyEvent.VK_6),
            (TrackerRole.RightHand to OpenVRButton.ProximitySensor) to AWTKey(KeyEvent.VK_7),
            (TrackerRole.RightHand to OpenVRButton.Axis0) to AWTKey(KeyEvent.VK_8),
            (TrackerRole.RightHand to OpenVRButton.Axis1) to AWTKey(KeyEvent.VK_9),
            (TrackerRole.RightHand to OpenVRButton.Axis2) to AWTKey(KeyEvent.VK_COMMA),
            (TrackerRole.RightHand to OpenVRButton.Axis3) to AWTKey(KeyEvent.VK_COLON),
            (TrackerRole.RightHand to OpenVRButton.Axis4) to AWTKey(KeyEvent.VK_PLUS),
            (TrackerRole.RightHand to OpenVRButton.DashboardBack) to AWTKey(KeyEvent.VK_MINUS),

            // proximity sensor for the headset, doesn't have a handedness
            (TrackerRole.Invalid to OpenVRButton.ProximitySensor) to AWTKey(KeyEvent.VK_SPACE)
        )

        /**
         * Returns the key string mapped to [button] on [hand].
         */
        @JvmStatic fun keyBinding(hand: TrackerRole, button: OpenVRButton): String {
            val binding = keyMap[hand to button]

            if(binding == null) {
                throw UnsupportedOperationException("Key $button on $hand not found.")
            } else {
                logger.debug("Binding for $button on $hand is ${binding.string}")
                return binding.string
            }
        }

        private fun translateError(error: Int): String {
            return when (error) {
                EVRCompositorError_VRCompositorError_None ->
                    "No error"
                EVRCompositorError_VRCompositorError_RequestFailed ->
                    "Request failed"
                EVRCompositorError_VRCompositorError_IncompatibleVersion ->
                    "Incompatible API version"
                EVRCompositorError_VRCompositorError_DoNotHaveFocus ->
                    "Compositor does not have focus"
                EVRCompositorError_VRCompositorError_InvalidTexture ->
                    "Invalid texture"
                EVRCompositorError_VRCompositorError_IsNotSceneApplication ->
                    "Not scene application"
                EVRCompositorError_VRCompositorError_TextureIsOnWrongDevice ->
                    "Texture is on wrong device"
                EVRCompositorError_VRCompositorError_TextureUsesUnsupportedFormat ->
                    "Texture uses unsupported format"
                EVRCompositorError_VRCompositorError_SharedTexturesNotSupported ->
                    "Shared textures are not supported"
                EVRCompositorError_VRCompositorError_IndexOutOfRange ->
                    "Index out of range"
                EVRCompositorError_VRCompositorError_AlreadySubmitted ->
                    "Already submitted"
                else ->
                    "Unknown error"
            }
        }

        private fun OpenVRButton.toKeyEvent(role: TrackerRole): Pair<KeyEvent, KeyEvent> {
            val keycode = this.toAWTKeyCode(role)
            return KeyEvent(object: Component() {}, KeyEvent.KEY_PRESSED, System.nanoTime(), 0, keycode.code, keycode.char) to
                KeyEvent(object: Component() {}, KeyEvent.KEY_RELEASED, System.nanoTime() + 10e5.toLong(), 0, keycode.code, keycode.char)
        }

        private fun OpenVRButton.toAWTKeyCode(role: TrackerRole = TrackerRole.LeftHand): AWTKey {
            val key = keyMap[role to this]
            return if(key != null) {
                key
            } else {
                logger.warn("Unknown key: $this for role $role")
                AWTKey(KeyEvent.VK_ESCAPE)
            }
        }

        private fun HmdVector3.toVector3f(): Vector3f {
            return Vector3f(this.v())
        }

        /**
         * Loads a model for a given device [type] from the JSON file given as [path]. The
         * model data loaded from the JSON file will become a child of [mesh], and [mesh] will
         * again be returned by this function.
         */
        fun loadMeshFromModelPath(type: TrackedDeviceType, path: String, mesh: Mesh): Mesh {
            val compositeFile = File(path.substringBeforeLast(".") + ".json")

            when {
                compositeFile.exists() && compositeFile.length() > 1024 -> {
                    logger.info("Loading model from composite JSON, ${compositeFile.absolutePath}")
                    val mapper = ObjectMapper(YAMLFactory())
                    mapper.registerModule(
                        KotlinModule.Builder()
                            .configure(KotlinFeature.NullToEmptyCollection, true)
                            .configure(KotlinFeature.NullToEmptyMap, true)
                            .configure(KotlinFeature.NullIsSameAsDefault, false)
                            .configure(KotlinFeature.SingletonSupport, true)
                            .configure(KotlinFeature.StrictNullChecks, true)
                            .build()
                    )
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)

                    try {
                        // SteamVR's JSON contains tabs, while it shouldn't. If not replacing this, jackson will freak out.
                        val json = compositeFile.readText().replace("\t", "    ")
                        val model = mapper.readValue(json, CompositeModel::class.java)
                        model.components.forEach { (_, component) ->
                            if(component.filename != null) {
                                val m = Mesh()
                                m.readFromOBJ(compositeFile.resolveSibling(component.filename).absolutePath, true)
                                mesh.addChild(m)

                                if(component.visibility?.getOrDefault("default", true) == false) {
                                    m.visible = false
                                }
                            }
                        }
                    } catch(e: Exception) {
                        logger.error("Exception: $e")
                        logger.info("Loading composite JSON failed, trying to fall back to regular model.")
                        mesh.readFrom(path)
                        e.printStackTrace()
                    }
                }

                mesh.name.lowercase().endsWith("stl") ||
                        mesh.name.lowercase().endsWith("obj") -> {
                    mesh.readFrom(path)

                    if (type == TrackedDeviceType.Controller) {
                        mesh.ifMaterial {
                            diffuse = Vector3f(0.1f, 0.1f, 0.1f)
                        }
                        mesh.children.forEach { c ->
                            c.ifMaterial {
                                diffuse = Vector3f(0.1f, 0.1f, 0.1f)
                            }
                        }
                    }
                }
                else -> logger.warn("Unknown model format: $path for $type")
            }


            return mesh
        }
    }
}
