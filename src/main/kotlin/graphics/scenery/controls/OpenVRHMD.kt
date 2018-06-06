package graphics.scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.*
import graphics.scenery.backends.Display
import graphics.scenery.backends.vulkan.VulkanDevice
import graphics.scenery.utils.LazyLogger
import org.lwjgl.openvr.*
import org.lwjgl.openvr.VR.*
import org.lwjgl.openvr.VRCompositor.*
import org.lwjgl.openvr.VRRenderModels.VRRenderModels_GetRenderModelOriginalPath
import org.lwjgl.openvr.VRSystem.*
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.scijava.ui.behaviour.Behaviour
import org.scijava.ui.behaviour.BehaviourMap
import org.scijava.ui.behaviour.InputTriggerMap
import org.scijava.ui.behaviour.MouseAndKeyHandler
import org.scijava.ui.behaviour.io.InputTriggerConfig
import java.awt.Component
import java.awt.event.KeyEvent
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

/**
 * TrackerInput implementation of OpenVR
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[seated] Whether the user is assumed to be sitting or not.
 * @property[useCompositor] Whether or not the compositor should be used.
 * @constructor Creates a new OpenVR HMD instance, using the compositor if requested
 */
open class OpenVRHMD(val seated: Boolean = true, val useCompositor: Boolean = true) : TrackerInput, Display, Hubable {

    /** slf4j logger instance */
    protected val logger by LazyLogger()
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
    protected var eyeProjectionCache: ArrayList<GLMatrix?> = ArrayList()
    /** Cache for head-to-eye transform matrices */
    protected var eyeTransformCache: ArrayList<GLMatrix?> = ArrayList()

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

    var manufacturer: String = ""
        private set
    var trackingSystemName: String = ""
        private set

    init {
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

                manufacturer = getStringProperty(k_unTrackedDeviceIndex_Hmd, ETrackedDeviceProperty_Prop_ManufacturerName_String)
                trackingSystemName = getStringProperty(k_unTrackedDeviceIndex_Hmd, ETrackedDeviceProperty_Prop_TrackingSystemName_String)
                val driverVersion = getStringProperty(k_unTrackedDeviceIndex_Hmd, ETrackedDeviceProperty_Prop_DriverVersion_String)

                logger.info("Initialized device $manufacturer $trackingSystemName $driverVersion with render target size ${getRenderTargetSize().x()}x${getRenderTargetSize().y()}")
            }

        } catch(e: UnsatisfiedLinkError) {
            logger.error("OpenVR support library not found, skipping initialization.")
            logger.error(e.message + "\n" + e.stackTrace.joinToString("\n"))
            initialized = false
        }
    }

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
    final override fun getRenderTargetSize(): GLVector {
            val x = memAllocInt(1)
            val y = memAllocInt(1)

            VRSystem_GetRecommendedRenderTargetSize(x, y)

        val width = x.get(0).toFloat()
        val height = y.get(0).toFloat()

        memFree(x)
        memFree(y)

        return GLVector(width, height)
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
     * @return GLMatrix containing the per-eye projection matrix
     */
    @Synchronized override fun getEyeProjection(eye: Int, nearPlane: Float, farPlane: Float): GLMatrix {
        if (eyeProjectionCache[eye] == null) {
            val projection = HmdMatrix44.calloc()
            VRSystem_GetProjectionMatrix(eye, nearPlane, farPlane, projection)

            eyeProjectionCache[eye] = projection.toGLMatrix().transpose()
        }

        return eyeProjectionCache[eye] ?: throw IllegalStateException("No cached projection for eye $eye found.")
    }

    /**
     * Returns the per-eye transform that moves from head to eye
     *
     * @param[eye] The eye index
     * @return GLMatrix containing the transform
     */
    @Synchronized override fun getHeadToEyeTransform(eye: Int): GLMatrix {
        if (eyeTransformCache[eye] == null) {
            val transform = HmdMatrix34.calloc()
            VRSystem_GetEyeToHeadTransform(eye, transform)

            // Windows Mixed Reality headsets handle transforms slightly different:
            // the general device pose contains the pose of the left eye, while then the eye-to-head
            // pose contains the identity for the left eye, and the full IPD/shift transformation for
            // the right eye. The developers claim this is for reprojection to work correctly. See also
            // https://github.com/LibreVR/Revive/issues/893
            if(manufacturer.contains("WindowsMR")) {
                eyeTransformCache[eye] = transform.toGLMatrix()
            } else {
                eyeTransformCache[eye] = transform.toGLMatrix()
            }

            logger.trace("Head-to-eye #{}: {}", eye, eyeTransformCache[eye].toString())
        }

        return eyeTransformCache[eye] ?: throw IllegalStateException("No cached eye transform for eye $eye found.")
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
     * @returns GLMatrix with orientation
     */
    override fun getOrientation(): Quaternion {
        val q = Quaternion()
        val pose = getPose().transposedFloatArray

        q.w = Math.sqrt(1.0 * Math.max(0.0f, 1.0f + pose[0] + pose[5] + pose[10])).toFloat() / 2.0f
        q.x = Math.sqrt(1.0 * Math.max(0.0f, 1.0f + pose[0] - pose[5] - pose[10])).toFloat() / 2.0f
        q.y = Math.sqrt(1.0 * Math.max(0.0f, 1.0f - pose[0] + pose[5] - pose[10])).toFloat() / 2.0f
        q.z = Math.sqrt(1.0 * Math.max(0.0f, 1.0f - pose[0] - pose[5] + pose[10])).toFloat() / 2.0f

        q.x *= Math.signum(q.x * (pose[9] - pose[6]))
        q.y *= Math.signum(q.y * (pose[2] - pose[8]))
        q.z *= Math.signum(q.z * (pose[4] - pose[1]))

        return q
    }

    override fun getOrientation(id: String): Quaternion {
        val device = trackedDevices.get(id)
        val q = Quaternion()

        if (device != null) {
            val pose = device.pose.floatArray

            q.w = Math.sqrt(1.0 * Math.max(0.0f, 1.0f + pose[0] + pose[5] + pose[10])).toFloat() / 2.0f
            q.x = Math.sqrt(1.0 * Math.max(0.0f, 1.0f + pose[0] - pose[5] - pose[10])).toFloat() / 2.0f
            q.y = Math.sqrt(1.0 * Math.max(0.0f, 1.0f - pose[0] + pose[5] - pose[10])).toFloat() / 2.0f
            q.z = Math.sqrt(1.0 * Math.max(0.0f, 1.0f - pose[0] - pose[5] + pose[10])).toFloat() / 2.0f

            q.x *= Math.signum(q.x * (pose[9] - pose[6]))
            q.y *= Math.signum(q.y * (pose[2] - pose[8]))
            q.z *= Math.signum(q.z * (pose[4] - pose[1]))
        }

        return q
    }

    /**
     * Returns the absolute position as GLVector
     *
     * @return HMD position as GLVector
     */
    override fun getPosition(): GLVector {
        val m = getPose().floatArray
        return GLVector(m[12], m[13], m[14])
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
        if (latencyWaitTime > 0) {
            Thread.sleep(0, latencyWaitTime.toInt())
        }

        VRSystem_GetTimeSinceLastVsync(lastVsync, frameCount)

        val secondsUntilPhotons = timePerFrame - lastVsync.get(0) + vsyncToPhotons

        if (debugLatency) {
            if (frames == 10) {
                logger.info("Wait:  $latencyWaitTime ns")
                logger.info("Ahead: $secondsUntilPhotons ns")
            }

            frames = (frames + 1) % 60
        }

        val countNow = frameCount.get(0)
        if (countNow - frameCount.get(0) > 1) {
            // skipping!
            if (debugLatency) {
                logger.info("FRAMEDROP!")
            }

            frameCountRun = 0
            if (latencyWaitTime > 0) {
                latencyWaitTime -= TimeUnit.MILLISECONDS.toNanos(1)
            }
        } else if (latencyWaitTime < timePerFrame * 1000000000.0f) {
            frameCountRun++
            latencyWaitTime += Math.round(Math.pow(frameCountRun / 10.0, 2.0))
        }

        frameCount.put(0, countNow)

        VRSystem_GetDeviceToAbsoluteTrackingPose(getExperience(), secondsUntilPhotons, hmdTrackedDevicePoses)

        for (device in (0 until k_unMaxTrackedDeviceCount)) {
            val isValid = hmdTrackedDevicePoses.get(device).bPoseIsValid()

            if (isValid) {
                val trackedDevice = VRSystem_GetTrackedDeviceClass(device)
                val type = when (trackedDevice) {
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

                val d = trackedDevices.computeIfAbsent("$type-$device", {
                    val nameBuf = memCalloc(1024)
                    val err = memAllocInt(1)

                    VRSystem_GetStringTrackedDeviceProperty(device, ETrackedDeviceProperty_Prop_RenderModelName_String, nameBuf, err)

                    val nameArray = ByteArray(1024)
                    nameBuf.get(nameArray)

                    val deviceName = String(nameArray, Charset.defaultCharset())
                    TrackedDevice(type, deviceName, GLMatrix.getIdentity(), timestamp = System.nanoTime())
                })

                if(type == TrackedDeviceType.Controller) {
                    if (d.metadata !is VRControllerState) {
                        d.metadata = VRControllerState.calloc()
                    }

                    val state = d.metadata as VRControllerState
                    VRSystem_GetControllerState(device, state)

                    val role = VRSystem_GetControllerRoleForTrackedDeviceIndex(device)

                    when {
                        (state.rAxis(0).x() > 0.5f && state.rAxis(0).y().absoluteValue < 0.5f && (state.ulButtonPressed() and (1L shl EVRButtonId_k_EButton_SteamVR_Touchpad) != 0L)) -> OpenVRButton.Right.toKeyEvent(role)
                        (state.rAxis(0).x() < -0.5f && state.rAxis(0).y().absoluteValue < 0.5f && (state.ulButtonPressed() and (1L shl EVRButtonId_k_EButton_SteamVR_Touchpad) != 0L)) -> OpenVRButton.Left.toKeyEvent(role)
                        (state.rAxis(0).y() > 0.5f && state.rAxis(0).x().absoluteValue < 0.5f && (state.ulButtonPressed() and (1L shl EVRButtonId_k_EButton_SteamVR_Touchpad) != 0L)) -> OpenVRButton.Up.toKeyEvent(role)
                        (state.rAxis(0).y() < -0.5f && state.rAxis(0).x().absoluteValue < 0.5f && (state.ulButtonPressed() and (1L shl EVRButtonId_k_EButton_SteamVR_Touchpad) != 0L)) -> OpenVRButton.Down.toKeyEvent(role)
                        else -> null
                    }?.let { event ->
                        inputHandler.keyPressed(event)
                        inputHandler.keyReleased(event)
                    }
                }

                val pose = hmdTrackedDevicePoses.get(device).mDeviceToAbsoluteTracking()

                d.pose = pose.toGLMatrix()
                d.timestamp = System.nanoTime()

                if (type == TrackedDeviceType.HMD) {
                    d.pose.invert()
                }
            }
        }

        val event = VREvent.calloc()
        while(VRSystem_PollNextEvent(event)) {
            if(event.eventType() == EVREventType_VREvent_ButtonUnpress) {
                val button = event.data().controller().button()
                val role = VRSystem_GetControllerRoleForTrackedDeviceIndex(event.trackedDeviceIndex())

                OpenVRButton.values().find { it.internalId == button }?.let {
                    inputHandler.keyPressed(it.toKeyEvent(role))
                }

                logger.debug("Button $button pressed")
            }

            if(event.eventType() == EVREventType_VREvent_MouseMove) {
                val x = event.data().mouse().x()
                val y = event.data().mouse().y()
                val down = event.data().mouse().button()

                logger.debug("Touchpad moved $x $y, down=$down")
            }
        }
        event.free()

        readyForSubmission = true
    }

    enum class OpenVRButton(val internalId: Int) {
        Left(EVRButtonId_k_EButton_DPad_Left),
        Right(EVRButtonId_k_EButton_DPad_Right),
        Up(EVRButtonId_k_EButton_DPad_Up),
        Down(EVRButtonId_k_EButton_DPad_Down),
        Menu(EVRButtonId_k_EButton_ApplicationMenu),
        Side(EVRButtonId_k_EButton_Grip)
    }

    data class AWTKey(val code: Int, val char: Char)

    private fun OpenVRButton.toKeyEvent(role: Int): KeyEvent {
        return KeyEvent(object: Component() {}, KeyEvent.KEY_PRESSED, 1, 0, this.toAWTKeyCode(role).code, this.toAWTKeyCode(role).char)
    }

    private fun OpenVRButton.toAWTKeyCode(role: Int = 0): AWTKey {
        return when {
            this == OpenVRHMD.OpenVRButton.Left && role == ETrackedControllerRole_TrackedControllerRole_LeftHand -> AWTKey(KeyEvent.VK_H, KeyEvent.CHAR_UNDEFINED)
            this == OpenVRHMD.OpenVRButton.Right && role == ETrackedControllerRole_TrackedControllerRole_LeftHand -> AWTKey(KeyEvent.VK_L, KeyEvent.CHAR_UNDEFINED)
            this == OpenVRHMD.OpenVRButton.Up  && role == ETrackedControllerRole_TrackedControllerRole_LeftHand -> AWTKey(KeyEvent.VK_K, KeyEvent.CHAR_UNDEFINED)
            this == OpenVRHMD.OpenVRButton.Down && role == ETrackedControllerRole_TrackedControllerRole_LeftHand -> AWTKey(KeyEvent.VK_J, KeyEvent.CHAR_UNDEFINED)

            this == OpenVRHMD.OpenVRButton.Left && role == ETrackedControllerRole_TrackedControllerRole_RightHand -> AWTKey(KeyEvent.VK_A, KeyEvent.CHAR_UNDEFINED)
            this == OpenVRHMD.OpenVRButton.Right && role == ETrackedControllerRole_TrackedControllerRole_RightHand -> AWTKey(KeyEvent.VK_D, KeyEvent.CHAR_UNDEFINED)
            this == OpenVRHMD.OpenVRButton.Up  && role == ETrackedControllerRole_TrackedControllerRole_RightHand -> AWTKey(KeyEvent.VK_W, KeyEvent.CHAR_UNDEFINED)
            this == OpenVRHMD.OpenVRButton.Down && role == ETrackedControllerRole_TrackedControllerRole_RightHand -> AWTKey(KeyEvent.VK_S, KeyEvent.CHAR_UNDEFINED)

            this == OpenVRHMD.OpenVRButton.Menu -> AWTKey(KeyEvent.VK_M, KeyEvent.CHAR_UNDEFINED)
            this == OpenVRHMD.OpenVRButton.Side -> AWTKey(KeyEvent.VK_S, KeyEvent.CHAR_UNDEFINED)

            else -> AWTKey(KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)
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
        stackPush().use { stack ->
            try {
                if (disableSubmission || !readyForSubmission) {
                    return
                }

                readyForSubmission = false

                val texture = Texture.callocStack(stack)
                    .eColorSpace(EColorSpace_ColorSpace_Gamma)
                    .eType(ETextureType_TextureType_OpenGL)
                    .handle(textureId.toLong())

                val boundsLeft = VRTextureBounds.callocStack(stack).set(0.0f, 0.0f, 0.5f, 1.0f)
                val errorLeft = VRCompositor_Submit(EVREye_Eye_Left, texture, boundsLeft, 0)

                val boundsRight = VRTextureBounds.callocStack(stack).set(0.5f, 0.0f, 1.0f, 1.0f)
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
                                          queue: VkQueue, image: Long) {
        stackPush().use { stack ->
            val textureData = VRVulkanTextureData.callocStack(stack)
                .m_nImage(image)
                .m_pInstance(instance.address())
                .m_pPhysicalDevice(device.physicalDevice.address())
                .m_pDevice(device.vulkanDevice.address())
                .m_pQueue(queue.address())
                .m_nQueueFamilyIndex(device.queueIndices.graphicsQueue)
                .m_nWidth(width)
                .m_nHeight(height)
                .m_nFormat(format)
                .m_nSampleCount(1)

            val texture = Texture.callocStack(stack)
                .handle(textureData.address())
                .eColorSpace(EColorSpace_ColorSpace_Gamma)
                .eType(ETextureType_TextureType_Vulkan)

            if (disableSubmission || !readyForSubmission) {
                return
            }

            readyForSubmission = false

            logger.trace("Submitting left...")
            val boundsLeft = VRTextureBounds.callocStack(stack).set(0.0f, 0.0f, 0.5f, 1.0f)
            val errorLeft = VRCompositor_Submit(EVREye_Eye_Left, texture, boundsLeft, 0)

            logger.trace("Submitting right...")
            val boundsRight = VRTextureBounds.callocStack(stack).set(0.5f, 0.0f, 1.0f, 1.0f)
            val errorRight = VRCompositor_Submit(EVREye_Eye_Right, texture, boundsRight, 0)

            if (errorLeft != EVRCompositorError_VRCompositorError_None
                || errorRight != EVRCompositorError_VRCompositorError_None) {
                logger.error("Compositor error: ${translateError(errorLeft)} ($errorLeft)/${translateError(errorRight)} ($errorRight)")
            }
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
        val buffer = memCalloc(1024)

        VRSystem_GetStringTrackedDeviceProperty(deviceIndex, property, buffer, null)
        val propertyArray = ByteArray(1024)
        buffer.get(propertyArray)

        return String(propertyArray, Charset.defaultCharset())
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
     * @return HMD pose as GLMatrix
     */
    override fun getPose(): GLMatrix {
        return this.trackedDevices.values.firstOrNull { it.type == TrackedDeviceType.HMD }?.pose ?: GLMatrix.getIdentity()
    }

    /**
     * Returns the HMD pose for a given eye.
     *
     * @param[eye] The eye to return the pose for.
     * @return HMD pose as GLMatrix
     */
    override fun getPoseForEye(eye: Int): GLMatrix {
        val p = this.getPose()
        val e = this.getHeadToEyeTransform(eye).inverse

        e.mult(p)

        return e
    }

    /**
     * Returns the HMD pose
     *
     * @param[type] Type of the tracked device to get the pose for
     * @return HMD pose as GLMatrix
     */
    fun getPose(type: TrackedDeviceType): List<TrackedDevice> {
        return this.trackedDevices.values.filter { it.type == type }
    }

    /**
     * Extension function to convert from HmdMatric34_t to GLMatrix
     *
     * @return 4x4 GLMatrix created from the original matrix
     */
    fun HmdMatrix34.toGLMatrix(): GLMatrix {
        val m = FloatArray(12)
        this.m().get(m)

        return GLMatrix(floatArrayOf(
            m[0], m[4], m[8], 0.0f,
            m[1], m[5], m[9], 0.0f,
            m[2], m[6], m[10], 0.0f,
            m[3], m[7], m[11], 1.0f
        ))
    }

    /**
     * Extension function to convert a HmdMatrix44_t to a GLMatrix
     *
     * @return 4x4 GLMatrix created from the original matrix
     */
    fun HmdMatrix44.toGLMatrix(): GLMatrix {
        val m = FloatArray(16)
        this.m().get(m)

        return GLMatrix(m)
    }

    /**
     * Loads a model representing the [TrackedDevice].
     *
     * @param[device] The device to load the model for.
     * @param[mesh] The [Mesh] to attach the model data to.
     */
    override fun loadModelForMesh(device: TrackedDevice, mesh: Mesh): Mesh {
        val modelName = device.name

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

            when {
                mesh.name.toLowerCase().endsWith("stl") -> mesh.readFromSTL(modelPath)
                mesh.name.toLowerCase().endsWith("obj") -> mesh.readFromOBJ(modelPath, true)
                else -> logger.warn("Unknown model format: $modelPath for $modelName")
            }

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

            when {
                mesh.name.toLowerCase().endsWith("stl") -> mesh.readFromSTL(modelPath)
                mesh.name.toLowerCase().endsWith("obj") -> mesh.readFromOBJ(modelPath, true)
                else -> logger.warn("Unknown model format: $modelPath for $modelName")
            }

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
        camera?.addChild(node)

        node.update.add {
            this.getPose(TrackedDeviceType.Controller).firstOrNull { it.name == device.name }?.let { controller ->

                node.wantsComposeModel = false
                node.model.setIdentity()
                node.model.mult(controller.pose.invert())

//                logger.info("Updating pose of $controller, ${node.model}")

                node.needsUpdate = false
                node.needsUpdateWorld = true
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
}
