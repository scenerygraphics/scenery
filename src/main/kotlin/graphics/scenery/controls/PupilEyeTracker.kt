package graphics.scenery.controls

import cleargl.GLVector
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.readValue
import graphics.scenery.Camera
import graphics.scenery.Node
import graphics.scenery.backends.Display
import graphics.scenery.numerics.Random
import graphics.scenery.utils.LazyLogger
import kotlinx.coroutines.*
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMsg
import org.zeromq.ZPoller
import java.io.Serializable
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Support class for Pupil Labs eye trackers -- pupil-labs.com
 *
 * [calibrationType] can be set to screen space or world space, and [host] is the host where
 * the Pupil service is running on, and [port] its port.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class PupilEyeTracker(val calibrationType: CalibrationType, val host: String = "localhost", val port: Int = System.getProperty("scenery.PupilEyeTracker.Port", "50020").toIntOrNull() ?: 50020) {
    /** Shall we do a screen-space or world-space calibration? */
    enum class CalibrationType { ScreenSpace, WorldSpace}

    private val logger by LazyLogger()

    private val zmqContext = ZContext(4)
    private val req = zmqContext.createSocket(ZMQ.REQ)
    private val objectMapper = ObjectMapper(MessagePackFactory())

    private val subscriptionPort: Int

    var isCalibrated = false
        private set
    private var calibrating = false

    var onGazeReceived: ((Gaze) -> Unit)? = null
    var onCalibrationFailed: (() -> Unit)? = null
    var onCalibrationSuccess: (() -> Unit)? = null
    var gazeConfidenceThreshold = 0.8f

    /**
     * Stores gaze data, and retrieves various properties
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Gaze(val confidence: Float = 0.0f,
                    val timestamp: Float = 0.0f,
                    val eyeId: Int? = null,

                    val norm_pos: FloatArray = floatArrayOf(),
                    val gaze_point_3d: FloatArray? = floatArrayOf(),
                    val eye_centers_3d: HashMap<Int, FloatArray>? = hashMapOf(),
                    val gaze_normals_3d: HashMap<Int, FloatArray>? = hashMapOf()) {


        /** Returns the normalized gaze position. */
        fun normalizedPosition() = this.norm_pos.toGLVector()
        /** Returns the point the user is gazing at. */
        fun gazePoint() = gaze_point_3d.toGLVector()

        /** Returns the center of the left eye. */
        @Suppress("unused")
        fun leftEyeCenter() = eye_centers_3d?.getOrDefault(0, floatArrayOf(0.0f, 0.0f, 0.0f)).toGLVector()
        /** Returns the center of the right eye. */
        @Suppress("unused")
        fun rightEyeCenter() = eye_centers_3d?.getOrDefault(1, floatArrayOf(0.0f, 0.0f, 0.0f)).toGLVector()

        /** Returns the normal orientation of the left eye. */
        @Suppress("unused")
        fun leftGazeNormal() = gaze_normals_3d?.getOrDefault(0, floatArrayOf(0.0f, 0.0f, 0.0f)).toGLVector()
        /** Returns the normal orientation of the right eye. */
        @Suppress("unused")
        fun rightGazeNormal() = gaze_normals_3d?.getOrDefault(1, floatArrayOf(0.0f, 0.0f, 0.0f)).toGLVector()

        private fun FloatArray?.toGLVector(): GLVector {
            return if(this != null) {
                GLVector(this[0], this[1], this.getOrElse(2, { 0.0f }))
            } else {
                GLVector(0.0f, 0.0f, 0.0f)
            }
        }

        /**
         * Compares two gaze data points with each other, returning true
         * only if they match in all aspects.
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as? Gaze ?: return false

            if (confidence != other.confidence) return false
            if (timestamp != other.timestamp) return false
            if (!Arrays.equals(norm_pos, other.norm_pos)) return false
            if (!Arrays.equals(gaze_point_3d, other.gaze_point_3d)) return false
            if (eye_centers_3d != other.eye_centers_3d) return false
            if (gaze_normals_3d != other.gaze_normals_3d) return false

            return true
        }

        /**
         * Returns the hash code of this gaze data point.
         */
        override fun hashCode(): Int {
            var result = confidence.hashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + Arrays.hashCode(norm_pos)
            result = 31 * result + Arrays.hashCode(gaze_point_3d)
            result = 31 * result + (eye_centers_3d?.hashCode() ?: 0)
            result = 31 * result + (gaze_normals_3d?.hashCode() ?: 0)
            return result
        }
    }


    private var currentPupilDatumLeft = HashMap<Any, Any>()
    private var currentPupilDatumRight = HashMap<Any, Any>()

    /** Stores the current gaze data point. */
    var currentGazeLeft: Gaze? = null
        private set
    var currentGazeRight: Gaze? = null
        private set

    private var frameLeft: ByteArray? = null
    private var frameRight: ByteArray? = null
    private var onReceiveFrame: ((Int, ByteArray) -> Any)? = null

    init {
        logger.info("Connecting to $host:$port ...")
        req.connect("tcp://$host:$port")
        req.send("SUB_PORT")

        subscriptionPort = req.recvStr().toInt()
        logger.info("Subscription port received as $subscriptionPort")

        val module = SimpleModule()
        module.addKeyDeserializer(Int::class.java, object : KeyDeserializer() {
            override fun deserializeKey(key: String?, ctxt: DeserializationContext?): Any {
                return Integer.valueOf(key)
            }
        })
        objectMapper.registerModule(module)
    }

    protected fun getPupilTimestamp(): Float {
        req.send("t")
        return req.recvStr().toFloat()
    }

    protected fun notify(dict: HashMap<String, Any>): String {
        req.sendMore("notify.${dict["subject"]}")
        req.send(objectMapper.writeValueAsBytes(dict))

        val answer = req.recvStr()
        logger.debug(answer)
        return answer
    }

    private val subscriberSockets = HashMap<String, Job>()
    private fun subscribe(topic: String) {
        if(!subscriberSockets.containsKey(topic)) {

            val job = GlobalScope.launch {
                val socket = zmqContext.createSocket(ZMQ.SUB)
                val poller = ZPoller(zmqContext)
                poller.register(socket, ZMQ.Poller.POLLIN)

                try {
                    socket.connect("tcp://$host:$subscriptionPort")
                    socket.subscribe(topic)
                    logger.debug("Subscribed to topic $topic")

                    while (isActive) {
                        poller.poll(10)

                        if(poller.isReadable(socket)) {
                            val msg = ZMsg.recvMsg(socket)

                            when(val msgType = msg.popString()) {
                                "notify.calibration.successful" -> {
                                    logger.info("Calibration successful.")
                                    calibrating = false
                                    isCalibrated = true
                                }

                                "notify.calibration.failed" -> {
                                    logger.error("Calibration failed.")
                                    onCalibrationFailed?.invoke()

                                    calibrating = false
                                }

                                "pupil.0", "pupil.1" -> {
                                    val bytes = msg.pop().data
                                    val dict = objectMapper.readValue<HashMap<Any, Any>>(bytes)
                                    val confidence = (dict["confidence"] as Double).toFloat()
                                    val eyeId = dict["id"] as Int

                                    if(confidence > 0.8) {
                                        when(eyeId) {
                                            0 -> currentPupilDatumLeft = dict
                                            1 -> currentPupilDatumRight = dict
                                        }
                                    }
                                }

                                "gaze",
                                "gaze.2d.0.",
                                "gaze.2d.1.",
                                "gaze.3d.0.",
                                "gaze.3d.1." -> {
                                    val bytes = msg.pop().data
                                    val g = objectMapper.readValue(bytes, Gaze::class.java)

                                    if(g.confidence > gazeConfidenceThreshold) {

                                        if (msgType.contains(".0.")) {
                                            currentGazeLeft = g
                                        }

                                        if (msgType.contains(".1.")) {
                                            currentGazeRight = g
                                        }

                                        val left = currentGazeLeft
                                        val right = currentGazeRight

                                        if (left != null && right != null) {
                                            val normPos = ((left.normalizedPosition() + right.normalizedPosition()) * 0.5f).toFloatArray()
                                            val gaze = Gaze((left.confidence + right.confidence)/2.0f, g.timestamp, 2, normPos)

                                            onGazeReceived?.invoke(gaze)
                                        }
                                    }
                                }

                                "frame.eye.0" -> {
                                    msg.pop()
                                    val bytes = msg.pop().data
                                    frameLeft = bytes

                                    onReceiveFrame?.invoke(0, bytes)
                                }

                                "frame.eye.1" -> {
                                    msg.pop()
                                    val bytes = msg.pop().data
                                    frameRight = bytes

                                    onReceiveFrame?.invoke(1, bytes)
                                }
                            }

                            msg.destroy()
                        }
                    }
                } finally {
                    logger.debug("Closing topic socket for $topic")
                    poller.unregister(socket)
                    poller.close()
                    socket.close()
                }
            }

            subscriberSockets.put(topic, job)
        }
    }

    private fun unsubscribe(topic: String) = runBlocking {
        if(subscriberSockets.containsKey(topic)) {
            logger.debug("Cancelling subscription of topic \"$topic\"")
            subscriberSockets.get(topic)?.cancel()
            subscriberSockets.get(topic)?.join()

            subscriberSockets.remove(topic)
        }
    }

    fun subscribeFrames(onReceiveAction: (Int, ByteArray) -> Unit) {
        notify(hashMapOf(
            "subject" to "start_plugin",
            "name" to "Frame_Publisher",
            "format" to "gray"
        ))
        subscribe("frame.eye.0")
        subscribe("frame.eye.1")

        onReceiveFrame = onReceiveAction
    }


    fun unsubscribeFrames() {
        notify(hashMapOf(
            "subject" to "stop_plugin",
            "name" to "Frame_Publisher"
        ))
        unsubscribe("frame.eye.0")
        unsubscribe("frame.eye.1")
    }

    /**
     * Runs a gaze calibration, using [cam] as origin to display the calibration points.
     * Requires a [hmd], and a [calibrationTarget] can be given to be moved around for gaze calibration.
     */
    fun calibrate(cam: Camera, hmd: Display, generateReferenceData: Boolean = false, calibrationTarget: Node? = null): Boolean {
        // Threshold for samples to ignore in the beginning
        val eyeMovingSamples = 15
        subscribe("notify.calibration.successful")
        subscribe("notify.calibration.failed")
        subscribe("pupil.")

        notify(hashMapOf(
            "subject" to "eye_process.should_start.0",
            "eye_id" to 0,
            "args" to emptyMap<String, Any>()
        ))

        notify(hashMapOf(
            "subject" to "eye_process.should_start.1",
            "eye_id" to 1,
            "args" to emptyMap<String, Any>()
        ))

        Thread.sleep(2000)


        when(calibrationType) {
            CalibrationType.ScreenSpace -> {
                notify(hashMapOf(
                    "subject" to "start_plugin",
                    "name" to "HMD_Calibration",
                    "args" to emptyMap<String, Any>()
                ))

                notify(hashMapOf(
                    "subject" to "calibration.should_start",
                    "hmd_video_frame_size" to listOf(hmd.getRenderTargetSize().x().toInt(), hmd.getRenderTargetSize().y().toInt()),
                    "outlier_threshold" to 35
                ))
            }

            CalibrationType.WorldSpace -> {
                notify(hashMapOf(
                    "subject" to "start_plugin",
                    "name" to "HMD_Calibration_3D",
                    "args" to emptyMap<String, Any>()
                ))

                val shiftLeftEye = floatArrayOf(
                    hmd.getHeadToEyeTransform(0)[0, 3],
                    hmd.getHeadToEyeTransform(0)[1, 3],
                    hmd.getHeadToEyeTransform(0)[2, 3])

                val shiftRightEye = floatArrayOf(
                    hmd.getHeadToEyeTransform(1)[0, 3],
                    hmd.getHeadToEyeTransform(1)[1, 3],
                    hmd.getHeadToEyeTransform(1)[2, 3])

                notify(hashMapOf(
                    "subject" to "calibration.should_start",
                    "hmd_video_frame_size" to listOf(hmd.getRenderTargetSize().x().toInt(), hmd.getRenderTargetSize().y().toInt()),
                    "outlier_threshold" to 35,
                    "translation_eye0" to shiftLeftEye,
                    "translation_eye1" to shiftRightEye
                ))
            }
        }

        calibrating = true
        calibrationTarget?.visible = true
        val referenceData = arrayListOf<HashMap<String, Serializable>>()

        if(generateReferenceData) {
            val numReferencePoints = 6
            val samplesPerPoint = 120

            val (posKeyName, posGenerator: ((Camera, Int, Int) -> Pair<GLVector, GLVector>)) = when(calibrationType) {
                CalibrationType.ScreenSpace -> "norm_pos" to CircularScreenSpaceCalibrationPointGenerator
                CalibrationType.WorldSpace -> "mm_pos" to DefaultWorldSpaceCalibrationPointGenerator
            }

            val positionList = (0 .. numReferencePoints).map {
                posGenerator.invoke(cam, it, numReferencePoints)
            }

            positionList.map { normalizedScreenPos ->
                logger.info("Subject looking at ${normalizedScreenPos.first}/${normalizedScreenPos.second}")
                val position = normalizedScreenPos.second.clone()

                calibrationTarget?.position = position + cam.forward * 0.15f

                if(normalizedScreenPos.first.x() == 0.5f && normalizedScreenPos.first.y() == 0.5f) {
                    calibrationTarget?.material?.diffuse = GLVector(1.0f, 1.0f, 0.0f)
                } else {
                    calibrationTarget?.material?.diffuse = GLVector(1.0f, 1.0f, 1.0f)
                }

                (0 until samplesPerPoint).forEach {
                    val timestamp = getPupilTimestamp()

                    val datum0 = hashMapOf<String, Serializable>(
                        posKeyName to normalizedScreenPos.first.toFloatArray(),
                        "timestamp" to timestamp,
                        "id" to 0
                    )

                    val datum1 = hashMapOf<String, Serializable>(
                        posKeyName to normalizedScreenPos.first.toFloatArray(),
                        "timestamp" to timestamp,
                        "id" to 1
                    )

                    if(it > eyeMovingSamples) {
                        referenceData.add(datum0)
                        referenceData.add(datum1)
                    }

                    Thread.sleep(20)
                }
            }

            logger.info("Generated ${referenceData.size} calibration points ($samplesPerPoint samples x $numReferencePoints points)")
        }

        notify(hashMapOf<String, Any>(
            "subject" to "calibration.add_ref_data",
            "ref_data" to referenceData.toArray()
        ))

        notify(hashMapOf(
            "subject" to "calibration.should_stop"
        ))

        while(calibrating) {
            Thread.sleep(100)
        }

        unsubscribe("notify.calibration.successful")
        unsubscribe("notify.calibration.failed")
        calibrationTarget?.visible = false

        if(isCalibrated) {
            logger.info("Calibration succeeded, subscribing to gaze data")
            subscribe("gaze")

            onCalibrationSuccess?.invoke()

            return true
        }

        return false
    }

    /**
     * Utilities for eye tracking.
     */
    companion object {
        /** Point generator for circular calibration points. */
        @Suppress("unused")
        val CircularScreenSpaceCalibrationPointGenerator = { cam: Camera, index: Int, referencePointCount: Int ->
            /*
             val v = if(index == 0) {
                GLVector(0.0f, 0.0f, 0.0f)
            } else {
                GLVector(
                    radius * cos(2 * PI.toFloat() * index.toFloat()/referencePointCount),
                    -1.0f * radius * sin(2 * PI.toFloat() * index.toFloat()/referencePointCount),
                    0.0f
                )
            }

            val mvp = cam.projection.clone()
            mvp.mult(cam.getTransformation())
            val pos = v + if(cam is DetachedHeadCamera) {
                cam.position + cam.headPosition
            } else {
                cam.position
            }
            var ndc = mvp.mult(pos.xyzw() + v.xyzw())
            ndc *= 1.0f/ndc.w()

//            v to cam.viewportToWorld(GLVector(v.x()*2.0f-1.0f, v.y()*2.0f-1.0f), offset = 0.0f)
             */
            val origin = 0.5f
            val radius = 0.3f

            val v = if(index == 0) {
                GLVector(origin, origin)
            } else {
                GLVector(
                    origin + radius * cos(2 * PI.toFloat() * index.toFloat()/referencePointCount),
                    origin + radius * sin(2 * PI.toFloat() * index.toFloat()/referencePointCount))
            }
            v to cam.viewportToWorld(GLVector(v.x()*2.0f-1.0f, v.y()*2.0f-1.0f), offset = 0.5f)
        }

        /** Point generator for equidistributed calibration points. */
        @Suppress("unused")
        val EquidistributedScreenSpaceCalibrationPointGenerator = { cam: Camera, index: Int, _: Int ->
            val points = arrayOf(
                GLVector(0.0f, 0.5f),
                GLVector(0.0f, 0.5f),

                GLVector(-0.5f, 0.5f),
                GLVector(-0.5f, -0.5f),

                GLVector(0.5f, 0.5f),
                GLVector(0.5f, -0.5f),

                GLVector(-0.25f, 0.0f),
                GLVector(0.25f, 0.0f)
            )

            val v = GLVector(
                0.5f + 0.3f * points[index % (points.size - 1)].x(),
                0.5f + 0.3f * points[index % (points.size - 1)].y(),
                cam.nearPlaneDistance + 0.5f)

            v to cam.viewportToWorld(GLVector(v.x() * 2.0f - 1.0f, v.y() * 2.0f - 1.0f))
        }

        /** Point generator for random world-space points. */
        @Suppress("unused")
        val DefaultWorldSpaceCalibrationPointGenerator = { _: Camera, _: Int, _: Int ->
            val v = GLVector(Random.randomFromRange(-4.0f, 4.0f),
                Random.randomFromRange(-4.0f, 4.0f),
                Random.randomFromRange(0.0f, -3.0f))
            v to v
        }
    }
}
