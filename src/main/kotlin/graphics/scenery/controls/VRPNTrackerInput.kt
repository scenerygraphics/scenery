package graphics.scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vrpn.Loader
import vrpn.TrackerRemote

/**
* TrackerInput for handling VRPN-based devices
*
* @author Ulrik Günther <hello@ulrik.is>
*/

class VRPNTrackerInput(trackerAddress: String = "device@locahost:5500") : TrackerRemote.PositionChangeListener,
    TrackerRemote.VelocityChangeListener, TrackerRemote.AccelerationChangeListener, TrackerInput {

    var logger: Logger = LoggerFactory.getLogger("VRPNTrackerInput")

    var tracker: TrackerRemote? = null

    var deviceName = trackerAddress.substringBefore("@")
    var trackerLocation = trackerAddress.substringAfter("@")

    var trackerAddress: String = trackerAddress
        set(value) {
            field = value
            logger.info("Initializing VRPN device $deviceName at $trackerLocation")
            tracker = initializeTracker(value)
        }

    var cachedOrientation: Quaternion = Quaternion(0.0f, 0.0f, 0.0f, 1.0f)

    var cachedPosition: GLVector = GLVector(0.0f, 0.0f, 0.0f)

    init {
        Loader.loadNatives()

        this.trackerAddress = trackerAddress

        logger.info("${tracker?.isConnected}/${tracker?.isLive}")
    }

    private fun initializeTracker(address: String): TrackerRemote {
        val t = TrackerRemote(address, null, null, null, null)
        t.setUpdateRate(500.0)
//        t.addAccelerationChangeListener(this)
        t.addPositionChangeListener(this)
//        t.addVelocityChangeListener(this)

        return t
    }

    /**
     * Returns the orientation of the HMD
     *
     * @returns GLMatrix with orientation
     */
    override fun getOrientation(): Quaternion {
        return cachedOrientation
    }

    /**
     * Returns the orientation of the given device, or a unit quaternion if the device is not found.
     *
     * @returns GLMatrix with orientation
     */
    override fun getOrientation(id: String): Quaternion {
        return cachedOrientation
    }

    /**
     * Returns the absolute position as GLVector
     *
     * @return HMD position as GLVector
     */
    override fun getPosition(): GLVector {
        return cachedPosition
    }

    /**
     * Returns the HMD pose
     *
     * @return HMD pose as GLMatrix
     */
    override fun getPose(): GLMatrix {
        return GLMatrix.fromQuaternion(cachedOrientation)
    }

    /**
     * Check whether the HMD is initialized and working
     *
     * @return True if HMD is initialiased correctly and working properly
     */
    override fun initializedAndWorking(): Boolean {
        return (tracker?.isLive ?: false) && (tracker?.isConnected ?: false)
    }

    /**
     * update state
     */
    override fun update() {
    }

    override fun getWorkingTracker(): TrackerInput? {
        if(tracker?.isLive ?: false && tracker?.isConnected ?: false) {
            return this
        } else {
            return null
        }
    }

    override fun trackerAccelerationUpdate(p0: TrackerRemote.AccelerationUpdate?, p1: TrackerRemote?) {

    }

    override fun trackerPositionUpdate(p0: TrackerRemote.TrackerUpdate?, p1: TrackerRemote?) {
        p0?.let { update ->
            cachedPosition = GLVector(update.pos[0].toFloat(), update.pos[1].toFloat(), -1.0f * update.pos[2].toFloat())

            // FIXME: View still seems slightly tilted
            val newOrientation = Quaternion(
                -update.quat[0].toFloat(),
                -update.quat[1].toFloat(),
                update.quat[2].toFloat(),
                update.quat[3].toFloat())
                .rotateByAngleX(Math.PI.toFloat()/2.0f)
                .normalize()

            cachedOrientation.set(newOrientation)
        }
    }

    override fun trackerVelocityUpdate(p0: TrackerRemote.VelocityUpdate?, p1: TrackerRemote?) {

    }

}
