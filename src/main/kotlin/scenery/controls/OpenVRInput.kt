package scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector
import com.sun.jna.Structure
import jopenvr.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scenery.Hub
import scenery.Hubable
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.*
import java.util.concurrent.TimeUnit
import jopenvr.JOpenVRLibrary as jvr

/**
 * Created by ulrik on 25/05/2016.
 */
open class OpenVRInput(val seated: Boolean = true, val useCompositor: Boolean = false) : HMDInput, Hubable {
    protected var logger: Logger = LoggerFactory.getLogger("OpenVRInput")
    override var hub: Hub? = null

    protected var vrFuncs: VR_IVRSystem_FnTable? = null
    protected var compositorFuncs: VR_IVRCompositor_FnTable? = null
    protected var initialized = false

    protected var hmdTrackedDevicePoseReference: TrackedDevicePose_t? = null
    protected var hmdTrackedDevicePoses: Array<Structure>? = null

    protected val error = IntBuffer.allocate(1)
    protected var hmdPose = GLMatrix.getIdentity()
    protected var eyeProjectionCache: ArrayList<GLMatrix?> = ArrayList()
    protected var eyeTransformCache: ArrayList<GLMatrix?> = ArrayList()

    val lastVsync = FloatBuffer.allocate(1)
    val frameCount = LongBuffer.allocate(1)
    val hmdDisplayFreq = IntBuffer.allocate(1)

    var latencyWaitTime = 0L
    var vsyncToPhotons = 0.0f

    protected var frameCountRun = 0.0f
    protected var timePerFrame = 0.0f

    var debugLatency = false
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
                hmdTrackedDevicePoses = hmdTrackedDevicePoseReference?.toArray(jvr.k_unMaxTrackedDeviceCount)

                val poseMatrices = ArrayList<GLMatrix>()

                val timePerFrame = 1.0f / hmdDisplayFreq[0]

                hmdTrackedDevicePoseReference?.autoRead = false
                hmdTrackedDevicePoseReference?.autoWrite = false
                hmdTrackedDevicePoseReference?.setAutoSynch(false)

                hmdTrackedDevicePoses?.forEach {
                    it.setAutoSynch(false)
                    it.autoRead = false
                    it.autoWrite = false
                }

                if(useCompositor) {
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
            logger.debug(e.message + "\n" + e.stackTrace.joinToString("\n"))
            initialized = false
        }
    }

    fun initCompositor() {
        if(vrFuncs != null) {
            compositorFuncs = VR_IVRCompositor_FnTable(jvr.VR_GetGenericInterface(jvr.IVRCompositor_Version, error))

            if(compositorFuncs != null) {
                logger.info("Compositor initialized")

                compositorFuncs?.setAutoSynch(false)
                compositorFuncs?.read()

                if(seated) {
                    compositorFuncs?.SetTrackingSpace?.apply(jvr.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseSeated)
                } else {
                    compositorFuncs?.SetTrackingSpace?.apply(jvr.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseStanding)
                }

                vsyncToPhotons = vrFuncs?.GetFloatTrackedDeviceProperty!!.apply(jvr.k_unTrackedDeviceIndex_Hmd, jvr.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_SecondsFromVsyncToPhotons_Float, error)
            }
        }
    }

    fun close() {
        jvr.VR_ShutdownInternal()
    }

    override fun initializedAndWorking(): Boolean {
        return initialized
    }

    override fun getRenderTargetSize(): GLVector {
        val x = IntBuffer.allocate(1)
        val y = IntBuffer.allocate(1)

        vrFuncs!!.GetRecommendedRenderTargetSize.apply(x, y)

        return GLVector(x[0].toFloat(), y[0].toFloat())
    }

    fun getFOV(direction: Int): Float {
        val fov = vrFuncs!!.GetFloatTrackedDeviceProperty!!.apply(jvr.k_unTrackedDeviceIndex_Hmd, direction, error)

        if(fov == 0f) {
            return 55.0f
        } else if(fov <= 10.0f) {
            return fov * 57.2957795f
        }

        return fov
    }

    override fun getEyeProjection(eye: Int): GLMatrix {
        if(eyeProjectionCache[eye] == null) {
            val proj = vrFuncs!!.GetProjectionMatrix!!.apply(eye, 0.1f, 10000f, jvr.EGraphicsAPIConvention.EGraphicsAPIConvention_API_OpenGL)
            proj.read()

            eyeProjectionCache[eye] = proj.toGLMatrix().transpose()
            logger.info("Eye projection #$eye" + eyeProjectionCache[eye].toString())
        }

        return eyeProjectionCache[eye]!!
    }

    override fun getHeadToEyeTransform(eye: Int): GLMatrix {
        if(eyeTransformCache[eye] == null) {
            val transform = vrFuncs!!.GetEyeToHeadTransform!!.apply(eye)
            transform.read()
            eyeTransformCache[eye] = transform.toGLMatrix()

            logger.info("Head-to-eye #$eye: " + eyeTransformCache[eye].toString())
        }

        return eyeTransformCache[eye]!!
    }

    override fun getIPD(): Float {
        if(vrFuncs == null) {
            return 0.065f
        } else {
            return vrFuncs!!.GetFloatTrackedDeviceProperty!!.apply(jvr.k_unTrackedDeviceIndex_Hmd, jvr.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_UserIpdMeters_Float, error)
        }
    }

    override fun getOrientation(): GLMatrix {
        return GLMatrix.getIdentity()
    }

    override fun getPosition(): GLVector {
        return GLVector.getNullVector(3)
    }

    fun updatePose() {
        if(vrFuncs == null) {
            return
        }

        if(compositorFuncs != null) {
            compositorFuncs?.WaitGetPoses?.apply(hmdTrackedDevicePoseReference, jvr.k_unMaxTrackedDeviceCount, null, 0)
        } else {
            if(latencyWaitTime > 0) {
                Thread.sleep(0, latencyWaitTime.toInt())
            }

            vrFuncs!!.GetTimeSinceLastVsync!!.apply(lastVsync, frameCount)

            val secondsUntilPhotons = timePerFrame - lastVsync[0] + vsyncToPhotons

            if(debugLatency) {
                if(frames == 10) {
                    logger.info("Wait:  $latencyWaitTime ns")
                    logger.info("Ahead: $secondsUntilPhotons ns")
                }

                frames = (frames + 1) % 60
            }

            val countNow = frameCount[0]
            if(countNow - frameCount[0] > 1) {
                // skipping!
                if(debugLatency) {
                    logger.info("FRAMEDROP!")
                }

                val frameCountRun = 0
                if(latencyWaitTime > 0) {
                    latencyWaitTime -= TimeUnit.MILLISECONDS.toNanos(1)
                }
            } else if(latencyWaitTime < timePerFrame * 1000000000.0f) {
                frameCountRun++
                latencyWaitTime += Math.round(Math.pow(frameCountRun/10.0, 2.0))
            }

            frameCount.put(0, countNow)

            vrFuncs!!.GetDeviceToAbsoluteTrackingPose!!.apply(
                    getExperience(), secondsUntilPhotons, hmdTrackedDevicePoseReference, jvr.k_unMaxTrackedDeviceCount
            )
        }

        for(device in (0..jvr.k_unMaxTrackedDeviceCount-1)) {
            val isValid = hmdTrackedDevicePoses!!.get(device).readField("bPoseIsValid")
            var type: String = ""

            if(isValid != 0) {
                val device: Int = vrFuncs!!.GetTrackedDeviceClass!!.apply(device)
                type = when(device) {
                    jopenvr.JOpenVRLibrary.ETrackedDeviceClass.ETrackedDeviceClass_TrackedDeviceClass_Controller -> "Controller"
                    jopenvr.JOpenVRLibrary.ETrackedDeviceClass.ETrackedDeviceClass_TrackedDeviceClass_HMD -> "HMD"
                    jopenvr.JOpenVRLibrary.ETrackedDeviceClass.ETrackedDeviceClass_TrackedDeviceClass_Other -> "Other"
                    jopenvr.JOpenVRLibrary.ETrackedDeviceClass.ETrackedDeviceClass_TrackedDeviceClass_TrackingReference -> "TrackingReference"
                    jopenvr.JOpenVRLibrary.ETrackedDeviceClass.ETrackedDeviceClass_TrackedDeviceClass_Invalid -> "Invalid"
                    else -> "Unknown"
                }

            }
        }

        val isValid = hmdTrackedDevicePoses!!.get(jvr.k_unTrackedDeviceIndex_Hmd).readField("bPoseIsValid")
        if(isValid != 0) {
            val pose = (hmdTrackedDevicePoses!!.get(jvr.k_unTrackedDeviceIndex_Hmd).readField("mDeviceToAbsoluteTracking") as HmdMatrix34_t)
            hmdPose = pose.toGLMatrix().invert()

            logger.trace("HMD: ${hmdPose.toString()}")
        }
    }

    override fun hasCompositor(): Boolean {
        if(compositorFuncs != null) {
            return true
        }

        return false
    }

    override fun submitToCompositor(leftId: Int, rightId: Int) {
        val leftTexture = Texture_t()
        val rightTexture = Texture_t()

        leftTexture.eColorSpace = jvr.EColorSpace.EColorSpace_ColorSpace_Gamma
        rightTexture.eColorSpace = jvr.EColorSpace.EColorSpace_ColorSpace_Gamma

        leftTexture.eType = jopenvr.JOpenVRLibrary.EGraphicsAPIConvention.EGraphicsAPIConvention_API_OpenGL
        rightTexture.eType = jopenvr.JOpenVRLibrary.EGraphicsAPIConvention.EGraphicsAPIConvention_API_OpenGL

        leftTexture.handle = leftId
        rightTexture.handle = rightId

        val bounds = VRTextureBounds_t()
        bounds.uMin = 0.0f
        bounds.uMax = 1.0f
        bounds.vMin = 0.0f
        bounds.vMax = 1.0f

        compositorFuncs!!.Submit.apply(0, leftTexture, bounds, 0)
        compositorFuncs!!.Submit.apply(1, rightTexture, bounds, 0)
    }

    fun getExperience(): Int {
        if(seated) {
            return jvr.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseSeated
        } else {
            return jvr.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseStanding
        }
    }

    override fun getPose(): GLMatrix {
        return this.hmdPose
    }

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

    fun HmdMatrix44_t.toGLMatrix(): GLMatrix {
        val m = GLMatrix(this.m)
        return m
    }
}