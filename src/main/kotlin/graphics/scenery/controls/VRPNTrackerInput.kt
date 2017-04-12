package graphics.scenery.controls

import org.slf4j.LoggerFactory
import vrpn.Loader
import vrpn.TrackerRemote

/**
 * Created by ulrik on 4/12/2017.
 */

class VRPNTrackerInput(trackerAddress: String = "device@locahost:5500") : TrackerRemote.PositionChangeListener,
    TrackerRemote.VelocityChangeListener, TrackerRemote.AccelerationChangeListener {

    var logger = LoggerFactory.getLogger("VRPNTrackerInput")

    var tracker: TrackerRemote? = null

    var trackerAddress: String = trackerAddress
        set(value) {
            field = value
            logger.info("Initializing VRPN tracker at $trackerAddress")
            tracker = initializeTracker(value)
        }

    init {
        Loader.loadNatives()

        this.trackerAddress = trackerAddress

        logger.info("${tracker?.isConnected}/${tracker?.isLive}")
    }

    private fun initializeTracker(address: String): TrackerRemote {
        val t = TrackerRemote(address, null, null, null, null)
        t.addAccelerationChangeListener(this)
        t.addPositionChangeListener(this)
        t.addVelocityChangeListener(this)

        return t
    }

    override fun trackerAccelerationUpdate(p0: TrackerRemote.AccelerationUpdate?, p1: TrackerRemote?) {

    }

    override fun trackerPositionUpdate(p0: TrackerRemote.TrackerUpdate?, p1: TrackerRemote?) {

    }

    override fun trackerVelocityUpdate(p0: TrackerRemote.VelocityUpdate?, p1: TrackerRemote?) {

    }

}
