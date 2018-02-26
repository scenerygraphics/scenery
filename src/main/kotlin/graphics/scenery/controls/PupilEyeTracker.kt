package graphics.scenery.controls

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.readValue
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.Numerics
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMsg
import org.zeromq.ZPoller
import java.io.Serializable

class PupilEyeTracker(val host: String = "localhost", val port: Int = 50020) {
    private val logger by LazyLogger()

    val zmqContext = ZContext(4)
    val req = zmqContext.createSocket(ZMQ.REQ)
    val objectMapper = ObjectMapper(MessagePackFactory())

    var subscriptionPort = 0

    var isCalibrated = false
        private set

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Gaze(var confidence: Float = 0.0f,
                    var topic: String = "gaze",
                    var timestamp: Float = 0.0f,
                    var norm_pos: List<Float> = listOf(),
                    var gaze_point_3d: List<Float> = listOf(),
                    var eye_centers_3d: HashMap<Int, List<Float>> = hashMapOf(),
                    var gaze_normals_3d: HashMap<Int, List<Float>> = hashMapOf())

    private var currentPupilDatumLeft = HashMap<Any, Any>()
    private var currentPupilDatumRight = HashMap<Any, Any>()

    var currentGaze: Gaze? = null
        private set

    init {
        req.connect("tcp://$host:$port")
        req.send("SUB_PORT")

        subscriptionPort = req.recvStr().toInt()
        logger.info("Subscription port received as $subscriptionPort")

        val module = SimpleModule()
        module.addKeyDeserializer(Int::class.java, object : KeyDeserializer() {
            /**
             * Method called to deserialize a [java.util.Map] key from JSON property name.
             */
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

            val job = launch {
                val socket = zmqContext.createSocket(ZMQ.SUB)
                val poller = ZPoller(zmqContext)
                poller.register(socket, ZMQ.Poller.POLLIN)

                try {
                    socket.connect("tcp://$host:$subscriptionPort")
                    socket.subscribe(topic)
                    logger.info("Subscribed to topic $topic")

                    while (isActive) {
                        poller.poll(10)

                        if(poller.isReadable(socket)) {
                            val msg = ZMsg.recvMsg(socket)
                            val msgSize = msg.size
                            val msgType = msg.popString()

                            when(msgType) {
                                "notify.calibration.successful" -> {
                                    logger.info("Calibration successful.")
                                    isCalibrated = true
                                }

                                "notify.calibration.failed" -> {
                                    logger.error("Calibration failed.")
                                }

                                "pupil.0", "pupil.1" -> {
                                    if(msgType == "gaze") {
                                        logger.info("$topic: Received $msgType, $msgSize frames")
                                    }
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

                                "gaze" -> {
                                    val bytes = msg.pop().data
                                    val g = objectMapper.readValue(bytes, Gaze::class.java)

                                    if(g.confidence > 0.6f) {
                                        currentGaze = g
                                        logger.debug("Current gaze: {}", g)
                                    }
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
            logger.debug("Cancelling subscription of $topic")
            subscriberSockets.get(topic)?.cancel()
            subscriberSockets.get(topic)?.join()

            subscriberSockets.remove(topic)
        }
    }

    fun calibrate(generateReferenceData: Boolean = false) {
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

        notify(hashMapOf(
            "subject" to "start_plugin",
            "name" to "HMD_Calibration_3D",
            "args" to emptyMap<String, Any>()
        ))

        notify(hashMapOf(
            "subject" to "calibration.should_start",
            "hmd_video_frame_size" to listOf(1000, 1000),
            "outlier_threshold" to 35,
            "translation_eye0" to floatArrayOf(0.032f, 0.0f, 0.0f),
            "translation_eye1" to floatArrayOf(-0.032f, 0.0f, 0.0f)
        ))

        val referenceData = arrayListOf<HashMap<String, Serializable>>()

        if(generateReferenceData) {
            val positionList = (0..10).map { Numerics.randomVectorFromRange(3, 0.0f, 1.0f) }

            positionList.map { normalizedScreenPos ->
                logger.info("Dummy subject looking at $normalizedScreenPos")

                (0 until 60).forEach {
                    val timestamp = getPupilTimestamp()

                    val datum0 = hashMapOf(
                        "mm_pos" to normalizedScreenPos.toFloatArray(),
                        "timestamp" to timestamp,
                        "id" to 0
                    )

                    val datum1 = hashMapOf(
                        "mm_pos" to normalizedScreenPos.toFloatArray(),
                        "timestamp" to timestamp,
                        "id" to 1
                    )

                    referenceData.add(datum0)
                    referenceData.add(datum1)

                    Thread.sleep(16)
                }
            }

            logger.info("Generated ${referenceData.size} pieces of dummy data")
        }

        notify(hashMapOf(
            "subject" to "calibration.add_ref_data",
            "ref_data" to referenceData.toArray()
        ))

        notify(hashMapOf(
            "subject" to "calibration.should_stop"
        ))

        Thread.sleep(5000)

        unsubscribe("notify.calibration.successful")
        unsubscribe("notify.calibration.failed")

        if(isCalibrated) {
            logger.info("Calibration succeeded, subscribing to gaze data")
            subscribe("gaze")
        }
    }
}
