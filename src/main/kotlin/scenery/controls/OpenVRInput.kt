package scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector
import com.sun.jna.Structure
import jopenvr.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.*
import java.util.concurrent.TimeUnit
import jopenvr.JOpenVRLibrary as jvr

/**
 * Created by ulrik on 25/05/2016.
 */
class OpenVRInput(val seated: Boolean = true) {
    protected var logger: Logger = LoggerFactory.getLogger("OpenVRInput")

    protected var vrFuncs: VR_IVRSystem_FnTable? = null
    protected var compositorFuncs: VR_IVRCompositor_FnTable? = null
    protected var initialized = false

    protected var hmdTrackedDevicePoseReference: TrackedDevicePose_t? = null
    protected var hmdTrackedDevicePoses: Array<Structure>? = null

    protected val error = IntBuffer.allocate(1)
    protected var hmdPose = GLMatrix.getIdentity()

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
                logger.error("Initialization error - ${jvr.VR_GetVRInitErrorAsEnglishDescription(error[0])}")
                vrFuncs = null
                hmdTrackedDevicePoseReference = null
                hmdTrackedDevicePoses = null
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

    fun getRenderTargetSize(): GLVector {
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

    fun getIPD(): Float {
        if(vrFuncs == null) {
            return 0.065f
        } else {
            return vrFuncs!!.GetFloatTrackedDeviceProperty!!.apply(jvr.k_unTrackedDeviceIndex_Hmd, jvr.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_UserIpdMeters_Float, error)
        }
    }

    fun getOrientation(): GLMatrix {
        return GLMatrix.getIdentity()
    }

    fun getPosition(): GLVector {
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
            hmdTrackedDevicePoses!!.get(device).readField("bPoseIsValid")
            val pose =  (hmdTrackedDevicePoses!!.get(device).readField("mDeviceToAbsoluteTracking") as HmdMatrix34_t)
            hmdPose = pose.toGLMatrix()

            //logger.info(hmdPose.toString())
        }
    }

    fun getExperience(): Int {
        if(seated) {
            return jvr.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseSeated
        } else {
            return jvr.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseStanding
        }
    }

    fun HmdMatrix34_t.toGLMatrix(): GLMatrix {
        val f = FloatBuffer.allocate(16)
        f.put(this.m)
        f.put(0.0f)
        f.put(0.0f)
        f.put(0.0f)
        f.put(1.0f)

        val m = GLMatrix(f.array())
        return m
    }

    fun HmdMatrix44_t.toGLMatrix(): GLMatrix {
        val m = GLMatrix(this.m)
        return m
    }
}