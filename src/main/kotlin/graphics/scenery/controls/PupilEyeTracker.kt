package graphics.scenery.controls

import com.fasterxml.jackson.databind.ObjectMapper
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.Numerics
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.cancelAndJoin
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

    init {
        req.connect("tcp://$host:$port")
        req.send("SUB_PORT")

        subscriptionPort = req.recvStr().toInt()
        logger.info("Subscription port received as $subscriptionPort")
    }

    protected fun getPupilTimestamp(): Float {
        req.send("t")
        return req.recvStr().toFloat()
    }

    protected fun notify(dict: HashMap<String, Any>): String {
        req.sendMore("notify.${dict["subject"]}")
        req.send(objectMapper.writeValueAsBytes(dict))

        val answer = req.recvStr()
        logger.info(answer)
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
                            val msgType = msg.popString()
                            logger.info("$topic: Received $msgType, ${msg.size} frames")
                            msg.destroy()
                        }
                    }
                } finally {
                    logger.info("Closing topic socket for $topic")
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
            logger.info("Cancelling subscription of $topic")
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

        Thread.sleep(2000)

        logger.info("Calibration successful")

        unsubscribe("notify.calibration.successful")
        unsubscribe("notify.calibration.failed")

        isCalibrated = true
    }
}
