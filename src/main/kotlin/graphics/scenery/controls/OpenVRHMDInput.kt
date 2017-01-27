package graphics.scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import graphics.scenery.jopenvr.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import graphics.scenery.Hub
import graphics.scenery.Hubable
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkQueue
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.*
import java.util.concurrent.TimeUnit
import graphics.scenery.jopenvr.JOpenVRLibrary as jvr

/**
 * HMDInput implementation of OpenVR
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[seated] Whether the user is assumed to be sitting or not.
 * @property[useCompositor] Whether or not the compositor should be used.
 * @constructor Creates a new OpenVR HMD instance, using the compositor if requested
 */
open class OpenVRHMDInput(val seated: Boolean = true, val useCompositor: Boolean = false) : HMDInput, Hubable {
    /** slf4j logger instance */
    protected var logger: Logger = LoggerFactory.getLogger("OpenVRHMDInput")
    /** The Hub to use for communication */
    override var hub: Hub? = null

    /** VR function table of the OpenVR library */
    protected var vrFuncs: VR_IVRSystem_FnTable? = null
    /** Compositor function table of the OpenVR library */
    protected var compositorFuncs: VR_IVRCompositor_FnTable? = null
    /** Has the HMD already been initialised? */
    protected var initialized = false

    /** HMD reference pose */
    protected var hmdTrackedDevicePoseReference: TrackedDevicePose_t? = null
    /** OpenVR poses structure for all tracked device */
    protected var hmdTrackedDevicePoses: Array<TrackedDevicePose_t>? = null

    /** error code storage */
    protected val error = IntBuffer.allocate(1)
    /** Storage for the poses of all tracked devices. */
    protected var trackedPoses = HashMap<String, GLMatrix>()
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

    init {
        error.put(0, -1)

        try {
            jvr.VR_InitInternal(error, jvr.EVRApplicationType.EVRApplicationType_VRApplication_Scene)

            if (error[0] == 0) {
                vrFuncs = VR_IVRSystem_FnTable(jvr.VR_GetGenericInterface(jvr.IVRSystem_Version, error))
            }

            if (vrFuncs == null || error[0] != 0) {
                logger.error("Initialization error - ${jvr.VR_GetVRInitErrorAsEnglishDescription(error[0]).getString(0)}")
                vrFuncs = null
                hmdTrackedDevicePoseReference = null
                hmdTrackedDevicePoses = null

                initialized = false
            } else {
                logger.info("OpenVR: Initialized.")

                vrFuncs?.setAutoSynch(false)
                vrFuncs?.read()

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

                initialized = true
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
        if (vrFuncs != null) {
            compositorFuncs = VR_IVRCompositor_FnTable(jvr.VR_GetGenericInterface(jvr.IVRCompositor_Version, error))

            if (compositorFuncs != null) {
                logger.info("Compositor initialized")

                compositorFuncs?.setAutoSynch(false)
                compositorFuncs?.read()

                if (seated) {
                    compositorFuncs?.SetTrackingSpace?.apply(jvr.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseSeated)
                } else {
                    compositorFuncs?.SetTrackingSpace?.apply(jvr.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseStanding)
                }

                vsyncToPhotons = vrFuncs?.GetFloatTrackedDeviceProperty!!.apply(jvr.k_unTrackedDeviceIndex_Hmd, jvr.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_SecondsFromVsyncToPhotons_Float, err)
            }
        }
    }

    /**
     * Runs the OpenVR shutdown hooks
     */
    fun close() {
        jvr.VR_ShutdownInternal()
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

        vrFuncs!!.GetRecommendedRenderTargetSize.apply(x, y)

        return GLVector(x.value.toFloat(), y.value.toFloat())
    }

    /**
     * Returns the field of view in degrees
     *
     * @return FOV in degrees
     */
    fun getFOV(direction: Int): Float {
        val err = IntByReference(0)
        val fov = vrFuncs!!.GetFloatTrackedDeviceProperty!!.apply(jvr.k_unTrackedDeviceIndex_Hmd, direction, err)

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
    override fun getEyeProjection(eye: Int): GLMatrix {
        if (eyeProjectionCache[eye] == null) {
            val proj = vrFuncs!!.GetProjectionMatrix!!.apply(eye, 0.1f, 10000f)
            proj.read()

            eyeProjectionCache[eye] = proj.toGLMatrix().transpose()
            logger.info("Eye projection #$eye" + eyeProjectionCache[eye].toString())
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
            val transform = vrFuncs!!.GetEyeToHeadTransform!!.apply(eye)
            transform.read()
            eyeTransformCache[eye] = transform.toGLMatrix()

            logger.info("Head-to-eye #$eye: " + eyeTransformCache[eye].toString())
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
        if (vrFuncs == null) {
            return 0.065f
        } else {
            return vrFuncs!!.GetFloatTrackedDeviceProperty!!.apply(jvr.k_unTrackedDeviceIndex_Hmd, jvr.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_UserIpdMeters_Float, err)
        }
    }

    /**
     * Returns the orientation of the HMD
     *
     * @returns GLMatrix with orientation
     */
    override fun getOrientation(): GLMatrix {
        return GLMatrix.getIdentity()
    }

    /**
     * Returns the absolute position as GLVector
     *
     * @return HMD position as GLVector
     */
    override fun getPosition(): GLVector {
        return GLVector.getNullVector(3)
    }

    /**
     * Queries the OpenVR runtime for updated poses and stores them
     */
    fun updatePose() {
        if (vrFuncs == null) {
            return
        }

        if (compositorFuncs != null) {
            compositorFuncs?.WaitGetPoses?.apply(hmdTrackedDevicePoseReference, jvr.k_unMaxTrackedDeviceCount, null, 0)
        } else {
            if (latencyWaitTime > 0) {
                Thread.sleep(0, latencyWaitTime.toInt())
            }

            vrFuncs!!.GetTimeSinceLastVsync!!.apply(lastVsync, frameCount)

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

            vrFuncs!!.GetDeviceToAbsoluteTrackingPose!!.apply(
                getExperience(), secondsUntilPhotons, hmdTrackedDevicePoseReference, jvr.k_unMaxTrackedDeviceCount
            )
        }

        for (device in (0..jvr.k_unMaxTrackedDeviceCount - 1)) {
            val isValid = hmdTrackedDevicePoses!!.get(device).readField("bPoseIsValid")

            if (isValid != 0) {
                val trackedDevice: Int = vrFuncs!!.GetTrackedDeviceClass!!.apply(device)
                val type = when (trackedDevice) {
                    jvr.ETrackedDeviceClass.ETrackedDeviceClass_TrackedDeviceClass_Controller -> "Controller"
                    jvr.ETrackedDeviceClass.ETrackedDeviceClass_TrackedDeviceClass_HMD -> "HMD"
                    jvr.ETrackedDeviceClass.ETrackedDeviceClass_TrackedDeviceClass_TrackingReference -> "TrackingReference"
                    jvr.ETrackedDeviceClass.ETrackedDeviceClass_TrackedDeviceClass_Invalid -> "Invalid"
                    else -> "Unknown"
                }

                val pose = (hmdTrackedDevicePoses!!.get(jvr.k_unTrackedDeviceIndex_Hmd).readField("mDeviceToAbsoluteTracking") as HmdMatrix34_t)
                trackedPoses[type] = pose.toGLMatrix().invert()
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
        if (compositorFuncs != null) {
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
        val leftTexture = Texture_t()
        val rightTexture = Texture_t()

        leftTexture.eColorSpace = jvr.EColorSpace.EColorSpace_ColorSpace_Gamma
        rightTexture.eColorSpace = jvr.EColorSpace.EColorSpace_ColorSpace_Gamma

        leftTexture.eType = jvr.ETextureType.ETextureType_TextureType_OpenGL
        rightTexture.eType = jvr.ETextureType.ETextureType_TextureType_OpenGL

        leftTexture.handle.setInt(0, leftId)
        rightTexture.handle.setInt(0, rightId)

        val bounds = VRTextureBounds_t()
        bounds.uMin = 0.0f
        bounds.uMax = 1.0f
        bounds.vMin = 0.0f
        bounds.vMax = 1.0f

        compositorFuncs!!.Submit.apply(0, leftTexture, bounds, 0)
        compositorFuncs!!.Submit.apply(1, rightTexture, bounds, 0)
    }

    override fun submitToCompositorVulkan(width: Int, height: Int, format: Int,
                                          instance: VkInstance, device: VkDevice,
                                          queue: VkQueue, queueFamilyIndex: Int,
                                          image: Long) {

        val instancePointer = Pointer.createConstant(instance.address())
        val devicePointer = Pointer.createConstant(device.address())
        val queuePointer = Pointer.createConstant(queue.address())

        val textureData = VRVulkanTextureData_t()
        textureData.m_nFormat = format
        textureData.m_nWidth = width
        textureData.m_nHeight = height
        textureData.m_pQueue = graphics.scenery.jopenvr.JOpenVRLibrary.VkQueue_T(queuePointer)
        textureData.m_nQueueFamilyIndex = queueFamilyIndex
        textureData.m_pInstance = graphics.scenery.jopenvr.JOpenVRLibrary.VkInstance_T(instancePointer)
        textureData.m_pDevice = graphics.scenery.jopenvr.JOpenVRLibrary.VkDevice_T(devicePointer)
        textureData.m_nSampleCount = 1
        textureData.m_nImage = image

        val texture = Texture_t()
        texture.eType = jvr.ETextureType.ETextureType_TextureType_Vulkan
        texture.eColorSpace = jvr.EColorSpace.EColorSpace_ColorSpace_Gamma
        texture.handle = textureData.pointer

        val bounds = VRTextureBounds_t()
        bounds.uMin = 0.0f
        bounds.uMax = 0.5f
        bounds.vMin = 0.0f
        bounds.vMax = 1.0f

        compositorFuncs!!.Submit.apply(0, texture, bounds, 0)

        bounds.uMin = 0.5f
        bounds.uMax = 1.0f
        bounds.vMin = 0.0f
        bounds.vMax = 1.0f

        compositorFuncs!!.Submit.apply(1, texture, bounds, 0)
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
        return this.trackedPoses.getOrDefault("HMD", GLMatrix.getIdentity())
    }

    /**
     * Returns the HMD pose
     *
     * @param[type] Type of the tracked device to get the pose for
     * @return HMD pose as GLMatrix
     */
    fun getPose(type: String): GLMatrix {
        return this.trackedPoses.getOrDefault(type, GLMatrix.getIdentity())
    }

    /**
     * Extension function to convert from HmdMatric34_t to GLMatrix
     *
     * @return 4x4 GLMatrix created from the original matrix
     */
    fun HmdMatrix34_t.toGLMatrix(): GLMatrix {
        val m = GLMatrix(floatArrayOf(
            m[0], m[4], m[8], 0.0f,
            m[1], m[5], m[9], 0.0f,
            m[2], m[6], m[10], 0.0f,
            m[3], m[7], m[11], 1.0f
        ))
//        val m = GLMatrix(floatArrayOf(
//                m[0], m[1], m[2], m[3],
//                m[4], m[5], m[6], m[7],
//                m[8], m[9], m[10], m[11],
//                0.0f, 0.0f, 0.0f, 1.0f
//        ))
        return m
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
}
