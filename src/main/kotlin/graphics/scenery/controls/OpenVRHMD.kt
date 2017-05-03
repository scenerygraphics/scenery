package graphics.scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.Mesh
import graphics.scenery.backends.Display
import graphics.scenery.jopenvr.*
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkQueue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.IntBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import graphics.scenery.jopenvr.JOpenVRLibrary as jvr

/**
 * TrackerInput implementation of OpenVR
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[seated] Whether the user is assumed to be sitting or not.
 * @property[useCompositor] Whether or not the compositor should be used.
 * @constructor Creates a new OpenVR HMD instance, using the compositor if requested
 */
open class OpenVRHMD(val seated: Boolean = true, val useCompositor: Boolean = false) : TrackerInput, Display, Hubable {
    /** slf4j logger instance */
    protected var logger: Logger = LoggerFactory.getLogger("OpenVRHMD")
    /** The Hub to use for communication */
    override var hub: Hub? = null

    /** VR function table of the OpenVR library */
    protected var vr: VR_IVRSystem_FnTable? = null
    /** Compositor function table of the OpenVR library */
    protected var compositor: VR_IVRCompositor_FnTable? = null
    /** RenderModel function table of the OpenVR library */
    protected var renderModels: VR_IVRRenderModels_FnTable? = null
    /** Has the HMD already been initialised? */
    @Volatile protected var initialized = false

    /** HMD reference pose */
    protected var hmdTrackedDevicePoseReference: TrackedDevicePose_t? = null
    /** OpenVR poses structure for all tracked device */
    protected var hmdTrackedDevicePoses: Array<TrackedDevicePose_t>? = null

    /** error code storage */
    protected val error = IntBuffer.allocate(1)
    /** Storage for the poses of all tracked devices. */
    protected var trackedDevices = ConcurrentHashMap<String, TrackedDevice>()
    /** Cache for per-eye projection matrices */
    protected var eyeProjectionCache: ArrayList<GLMatrix?> = ArrayList()
    /** Cache for head-to-eye transform matrices */
    protected var eyeTransformCache: ArrayList<GLMatrix?> = ArrayList()

    /** When did the last vsync occur? */
    val lastVsync = FloatByReference(0.0f)
    /** Current frame count on the HMD */
    val frameCount = LongByReference(0L)
    /** Display frequency of the HMD */
    val hmdDisplayFreq = IntBuffer.allocate(1)

    /** Latency wait time */
    var latencyWaitTime = 0L
    /** Whether or not to vsync to the beacons */
    var vsyncToPhotons = 0.0f

    /** Per-run frame count */
    protected var frameCountRun = 0
    /** Per-frame rendering time */
    protected var timePerFrame = 0.0f

    /** (De)activate latency debugging */
    var debugLatency = false
    /** Frame count per-vsync */
    private var frames = 0

    /** disables submission in case of compositor errors */
    private var disableSubmission: Boolean = false


    init {
        error.put(0, -1)

        try {
            jvr.VR_InitInternal(error, jvr.EVRApplicationType.EVRApplicationType_VRApplication_Scene)

            if (error[0] == 0) {
                vr = VR_IVRSystem_FnTable(jvr.VR_GetGenericInterface(jvr.IVRSystem_Version, error))
                renderModels = VR_IVRRenderModels_FnTable(jvr.VR_GetGenericInterface(jvr.IVRRenderModels_Version, error))
            }

            if (vr == null || renderModels == null || error[0] != 0) {
                if (error[0] == 108) {
                    // only warn if no HMD found.
                    logger.warn("No HMD found. Are all cables connected?")
                } else {
                    logger.error("Initialization error - ${jvr.VR_GetVRInitErrorAsEnglishDescription(error[0]).getString(0)}")
                }
                vr = null
                hmdTrackedDevicePoseReference = null
                hmdTrackedDevicePoses = null

                initialized = false
            } else {
                initialized = true
                logger.info("OpenVR: Initialized.")

                vr?.setAutoSynch(false)
                vr?.read()

                renderModels?.setAutoSynch(false)
                renderModels?.read()

                hmdDisplayFreq.put(jvr.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_DisplayFrequency_Float)

                hmdTrackedDevicePoseReference = TrackedDevicePose_t.ByReference()
                hmdTrackedDevicePoses = hmdTrackedDevicePoseReference?.castToArray(jvr.k_unMaxTrackedDeviceCount)

                timePerFrame = 1.0f / hmdDisplayFreq[0]

                hmdTrackedDevicePoseReference?.autoRead = false
                hmdTrackedDevicePoseReference?.autoWrite = false
                hmdTrackedDevicePoseReference?.setAutoSynch(false)

                hmdTrackedDevicePoses?.forEach {
                    it.setAutoSynch(false)
                    it.autoRead = false
                    it.autoWrite = false
                }

                if (useCompositor) {
                    initCompositor()
                }

                logger.info("Recommended render target size is ${getRenderTargetSize()}")
                eyeProjectionCache.add(null)
                eyeProjectionCache.add(null)

                eyeTransformCache.add(null)
                eyeTransformCache.add(null)
            }

        } catch(e: UnsatisfiedLinkError) {
            logger.error("Support library not found, skipping initialization.")
            logger.error(e.message + "\n" + e.stackTrace.joinToString("\n"))
            initialized = false
        }
    }

    /**
     * Initialises the OpenVR compositor
     */
    fun initCompositor() {
        val err = IntByReference(0)
        if (vr != null) {
            compositor = VR_IVRCompositor_FnTable(jvr.VR_GetGenericInterface(jvr.IVRCompositor_Version, error))

            if (compositor != null) {
                logger.info("Compositor initialized")

                compositor?.setAutoSynch(false)
                compositor?.read()

                if (seated) {
                    compositor?.SetTrackingSpace?.apply(jvr.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseSeated)
                } else {
                    compositor?.SetTrackingSpace?.apply(jvr.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseStanding)
                }

                vsyncToPhotons = vr?.GetFloatTrackedDeviceProperty!!.apply(jvr.k_unTrackedDeviceIndex_Hmd, jvr.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_SecondsFromVsyncToPhotons_Float, err)
            }
        }
    }

    /**
     * Runs the OpenVR shutdown hooks
     */
    fun close() {
        jvr.VR_ShutdownInternal()
    }

    override fun getWorkingTracker(): TrackerInput? {
        if(initialized) {
            return this
        } else {
            return null
        }
    }

    override fun getWorkingDisplay(): Display? {
        if(initialized) {
            return this
        } else {
            return null
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
    override fun getRenderTargetSize(): GLVector {
        val x = IntByReference(0)
        val y = IntByReference(0)

        vr!!.GetRecommendedRenderTargetSize.apply(x, y)

        return GLVector(x.value.toFloat(), y.value.toFloat())
    }

    /**
     * Returns the field of view in degrees
     *
     * @return FOV in degrees
     */
    fun getFOV(direction: Int): Float {
        val err = IntByReference(0)
        val fov = vr!!.GetFloatTrackedDeviceProperty!!.apply(jvr.k_unTrackedDeviceIndex_Hmd, direction, err)

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
    override fun getEyeProjection(eye: Int, nearPlane: Float, farPlane: Float, flipY: Boolean): GLMatrix {
        if (eyeProjectionCache[eye] == null) {
            val proj = vr!!.GetProjectionMatrix!!.apply(eye, nearPlane, farPlane)
            proj.read()

            eyeProjectionCache[eye] = proj.toGLMatrix().transpose()

            if (flipY) {
                eyeProjectionCache[eye]!!.set(1, 1, -1.0f * eyeProjectionCache[eye]!!.get(1, 1))
            }

            logger.trace("Eye projection #$eye" + eyeProjectionCache[eye].toString())
        }

        return eyeProjectionCache[eye]!!
    }

    /**
     * Returns the per-eye transform that moves from head to eye
     *
     * @param[eye] The eye index
     * @return GLMatrix containing the transform
     */
    override fun getHeadToEyeTransform(eye: Int): GLMatrix {
        if (eyeTransformCache[eye] == null) {
            val transform = vr!!.GetEyeToHeadTransform!!.apply(eye)
            transform.read()
            eyeTransformCache[eye] = transform.toGLMatrix().invert()

            logger.trace("Head-to-eye #$eye: " + eyeTransformCache[eye].toString())
        }

        return eyeTransformCache[eye]!!
    }

    /**
     * Returns the inter-pupillary distance (IPD)
     *
     * @return IPD as Float
     */
    override fun getIPD(): Float {
        val err = IntByReference(0)
        if (vr == null) {
            return 0.065f
        } else {
            return vr!!.GetFloatTrackedDeviceProperty!!.apply(jvr.k_unTrackedDeviceIndex_Hmd, jvr.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_UserIpdMeters_Float, err)
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
    override fun update() {
        if (vr == null) {
            return
        }

        if (compositor != null) {
            compositor?.WaitGetPoses?.apply(hmdTrackedDevicePoseReference, jvr.k_unMaxTrackedDeviceCount, null, 0)
        } else {
            if (latencyWaitTime > 0) {
                Thread.sleep(0, latencyWaitTime.toInt())
            }

            vr!!.GetTimeSinceLastVsync!!.apply(lastVsync, frameCount)

            val secondsUntilPhotons = timePerFrame - lastVsync.value + vsyncToPhotons

            if (debugLatency) {
                if (frames == 10) {
                    logger.info("Wait:  $latencyWaitTime ns")
                    logger.info("Ahead: $secondsUntilPhotons ns")
                }

                frames = (frames + 1) % 60
            }

            val countNow = frameCount.value
            if (countNow - frameCount.value > 1) {
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

            frameCount.value = countNow

            vr!!.GetDeviceToAbsoluteTrackingPose!!.apply(
                getExperience(), secondsUntilPhotons, hmdTrackedDevicePoseReference, jvr.k_unMaxTrackedDeviceCount
            )
        }

        for (device in (0..jvr.k_unMaxTrackedDeviceCount - 1)) {
            val isValid = hmdTrackedDevicePoses!!.get(device).readField("bPoseIsValid")

            if (isValid != 0) {
                val trackedDevice: Int = vr!!.GetTrackedDeviceClass!!.apply(device)
                val type = when (trackedDevice) {
                    jvr.ETrackedDeviceClass.ETrackedDeviceClass_TrackedDeviceClass_Controller -> TrackedDeviceType.Controller
                    jvr.ETrackedDeviceClass.ETrackedDeviceClass_TrackedDeviceClass_HMD -> TrackedDeviceType.HMD
                    jvr.ETrackedDeviceClass.ETrackedDeviceClass_TrackedDeviceClass_TrackingReference -> TrackedDeviceType.BaseStation
                    jvr.ETrackedDeviceClass.ETrackedDeviceClass_TrackedDeviceClass_GenericTracker -> TrackedDeviceType.Generic

                    jvr.ETrackedDeviceClass.ETrackedDeviceClass_TrackedDeviceClass_Invalid -> TrackedDeviceType.Invalid
                    else -> TrackedDeviceType.Invalid
                }

                if (type == TrackedDeviceType.Invalid) {
                    continue
                }

                trackedDevices.computeIfAbsent("$type-$device", {
                    val nameBuf = Memory(1024)
                    val err = IntByReference(0)

                    vr!!.GetStringTrackedDeviceProperty.apply(device, jvr.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_RenderModelName_String, nameBuf, 1024, err)

                    val deviceName = nameBuf.getString(0L)
                    TrackedDevice(type, deviceName, GLMatrix.getIdentity(), timestamp = System.nanoTime())
                })

                val pose = (hmdTrackedDevicePoses!!.get(device).readField("mDeviceToAbsoluteTracking") as HmdMatrix34_t)
                trackedDevices["$type-$device"]!!.pose = pose.toGLMatrix()
                trackedDevices["$type-$device"]!!.timestamp = System.nanoTime()

                if (type == TrackedDeviceType.HMD) {
                    trackedDevices["$type-$device"]!!.pose.invert()
                }
            }
        }
    }

    /**
     * Query the HMD whether a compositor is used or the renderer should take
     * care of displaying on the HMD on its own.
     *
     * @return True if the HMD has a compositor
     */
    override fun hasCompositor(): Boolean {
        if (compositor != null) {
            return true
        }

        return false
    }

    /**
     * Submit texture IDs to the compositor
     *
     * @param[leftId] Texture ID of the left eye texture
     * @param[rightId] Texture ID of the right eye texture
     */
    override fun submitToCompositor(leftId: Int, rightId: Int) {
        try {
            if (disableSubmission == true) {
                return
            }
            val leftTexture = Texture_t()
            val rightTexture = Texture_t()

            leftTexture.eColorSpace = jvr.EColorSpace.EColorSpace_ColorSpace_Gamma
            rightTexture.eColorSpace = jvr.EColorSpace.EColorSpace_ColorSpace_Gamma

            leftTexture.eType = jvr.ETextureType.ETextureType_TextureType_OpenGL
            rightTexture.eType = jvr.ETextureType.ETextureType_TextureType_OpenGL

            leftTexture.handle = Pointer.createConstant(leftId)
            rightTexture.handle = Pointer.createConstant(rightId)

            leftTexture.write()
            rightTexture.write()

            val bounds = VRTextureBounds_t()
            bounds.uMin = 0.0f
            bounds.uMax = 1.0f
            bounds.vMin = 0.0f
            bounds.vMax = 1.0f
            bounds.write()

            val err_left = compositor!!.Submit.apply(jvr.EVREye.EVREye_Eye_Left, leftTexture, bounds, 0)
            val err_right = compositor!!.Submit.apply(jvr.EVREye.EVREye_Eye_Right, rightTexture, bounds, 0)

            if(err_left != jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_None
                || err_right != jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_None) {
                logger.error("Compositor error: ${translateError(err_left)} ($err_left)/${translateError(err_right)} ($err_right)")
                disableSubmission = true
            }
        } catch(e: java.lang.Error) {
            logger.error("Compositor submission failed, please restart the HMD, SteamVR and the application.")
            disableSubmission = true
        }
    }

    override fun submitToCompositorVulkan(width: Int, height: Int, format: Int,
                                          instance: VkInstance, device: VkDevice, physicalDevice: VkPhysicalDevice,
                                          queue: VkQueue, queueFamilyIndex: Int,
                                          image: Long) {

        val instancePointer = Pointer(instance.address())
        val devicePointer = Pointer(device.address())
        val physicalDevicePointer = Pointer(physicalDevice.address())
        val queuePointer = Pointer(queue.address())

        val textureData = VRVulkanTextureData_t()
        textureData.m_nImage = image
        textureData.m_pDevice = jvr.VkDevice_T(devicePointer)
        textureData.m_pPhysicalDevice = jvr.VkPhysicalDevice_T(physicalDevicePointer)
        textureData.m_pInstance = jvr.VkInstance_T(instancePointer)
        textureData.m_pQueue = jvr.VkQueue_T(queuePointer)
        textureData.m_nQueueFamilyIndex = queueFamilyIndex

        textureData.m_nWidth = width
        textureData.m_nHeight = height
        textureData.m_nFormat = format
        textureData.m_nSampleCount = 1
        textureData.write()

        val texture = Texture_t()
        texture.eType = jvr.ETextureType.ETextureType_TextureType_Vulkan
        texture.eColorSpace = jvr.EColorSpace.EColorSpace_ColorSpace_Gamma
        texture.handle = textureData.pointer
        texture.write()

        try {
            if (disableSubmission == true) {
                return
            }

            val boundsLeft = VRTextureBounds_t(0.0f, 0.0f, 0.5f, 1.0f)
            boundsLeft.write()

            val err_left = compositor!!.Submit.apply(jvr.EVREye.EVREye_Eye_Left,
                texture, boundsLeft, 0)

            val boundsRight = VRTextureBounds_t(0.5f, 0.0f, 1.0f, 1.0f)
            boundsRight.write()

            val err_right = compositor!!.Submit.apply(jvr.EVREye.EVREye_Eye_Right,
                texture, boundsRight, 0)

            if (err_left != jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_None
                || err_right != jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_None) {
                logger.error("Compositor error: ${translateError(err_left)} ($err_left)/${translateError(err_right)} ($err_right)")
            }
        } catch(e: java.lang.Error) {
            logger.error("Compositor submission failed, please restart the HMD, SteamVR and the application.")
            disableSubmission = true
        }
    }

    private fun translateError(error: Int): String {
        return when (error) {
            jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_None ->
                "No error"
            jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_RequestFailed ->
                "Request failed"
            jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_IncompatibleVersion ->
                "Incompatible API version"
            jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_DoNotHaveFocus ->
                "Compositor does not have focus"
            jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_InvalidTexture ->
                "Invalid texture"
            jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_IsNotSceneApplication ->
                "Not scene application"
            jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_TextureIsOnWrongDevice ->
                "Texture is on wrong device"
            jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_TextureUsesUnsupportedFormat ->
                "Texture uses unsupported format"
            jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_SharedTexturesNotSupported ->
                "Shared textures are not supported"
            jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_IndexOutOfRange ->
                "Index out of range"
            jvr.EVRCompositorError.EVRCompositorError_VRCompositorError_AlreadySubmitted ->
                "Already submitted"
            else ->
                "Unknown error"
        }
    }

    override fun getVulkanInstanceExtensions(): List<String> {
        compositor?.let {
            val buffer = Memory(1024)
            val count = it.GetVulkanInstanceExtensionsRequired.apply(Pointer(0), 0)

            if (count == 0) {
                return listOf()
            } else {
                it.GetVulkanInstanceExtensionsRequired.apply(buffer, count)
                return Native.toString(buffer.getByteArray(0, count)).split(' ')
            }
        }

        return listOf()
    }

    override fun getVulkanDeviceExtensions(physicalDevice: VkPhysicalDevice): List<String> {
        val physicalDeviceT = graphics.scenery.jopenvr.JOpenVRLibrary.VkPhysicalDevice_T(Pointer(physicalDevice.address()))

        compositor?.let {
            val buffer = Memory(1024)

            val count = it.GetVulkanDeviceExtensionsRequired.apply(physicalDeviceT, Pointer(0), 0)

            if (count == 0) {
                return listOf()
            } else {
                it.GetVulkanDeviceExtensionsRequired.apply(physicalDeviceT, buffer, count)
                return Native.toString(buffer.getByteArray(0, count)).split(' ')
            }
        }

        return listOf()
    }

    /**
     * Queries the OpenVR runtime whether the user is using a sitting or standing configuration
     */
    fun getExperience(): Int {
        if (seated) {
            return jvr.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseSeated
        } else {
            return jvr.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseStanding
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
     * Returns the HMD pose
     *
     * @param[type] Type of the tracked device to get the pose for
     * @return HMD pose as GLMatrix
     */
    fun getPose(type: TrackedDeviceType): List<TrackedDevice> {
        return this.trackedDevices.values.filter { it.type == type }.toList()
    }

    /**
     * Extension function to convert from HmdMatric34_t to GLMatrix
     *
     * @return 4x4 GLMatrix created from the original matrix
     */
    fun HmdMatrix34_t.toGLMatrix(): GLMatrix {
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
    fun HmdMatrix44_t.toGLMatrix(): GLMatrix {
        val m = GLMatrix(this.m)
        return m
    }

    fun loadModelForMesh(modelName: String, node: Mesh) {
        val modelNameStr = Memory(modelName.length + 1L)
        modelNameStr.setString(0L, modelName)

        renderModels?.let {
            val path = Memory(1024)
            val error = IntByReference(0)

            it.GetRenderModelOriginalPath.apply(modelNameStr, path, 1024, error)

            logger.info("Loading model for $modelName from ${path.getString(0)}")

            val modelPath = path.getString(0).replace('\\', '/')

            node.name = modelPath.substringAfterLast('/')

            if (modelPath.toLowerCase().endsWith("stl")) {
                node.readFromSTL(modelPath)
            } else if (modelPath.toLowerCase().endsWith("obj")) {
                node.readFromOBJ(modelPath, true)
            } else {
                logger.warn("Unknown model format: $modelPath for $modelName")
            }
        }
    }
}
