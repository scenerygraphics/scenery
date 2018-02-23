package graphics.scenery.controls

import com.fasterxml.jackson.databind.ObjectMapper
import graphics.scenery.utils.LazyLogger
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.zeromq.ZContext
import org.zeromq.ZMQ

class PupilEyeTracker(host: String = "localhost", port: Int = 50020) {
    private val logger by LazyLogger()

    val context = ZContext(4)
    val req = context.createSocket(ZMQ.REQ)
    val objectMapper = ObjectMapper(MessagePackFactory())

    var isCalibrated = false
        private set

    init {
        req.connect("tcp://$host:$port")
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

    fun calibrate(generateReferenceData: Boolean = false) {
        notify(hashMapOf(
            "subject" to "eye_process.should_start.0",
            "eye_id" to 0,
            "args" to emptyList<Int>()
        ))

        notify(hashMapOf(
            "subject" to "eye_process.should_start.1",
            "eye_id" to 1,
            "args" to emptyList<Int>()
        ))

        Thread.sleep(2000)

        notify(hashMapOf(
            "subject" to "start_plugin",
            "name" to "HMD_Calibration",
            "args" to emptyList<Int>()
        ))

        notify(hashMapOf(
            "subject" to "calibration.should_start",
            "hmd_video_frame_size" to listOf(1000, 1000),
            "outlier_threshold" to 35
        ))

        val referenceData = mutableListOf<HashMap<String, Any>>()

        if(generateReferenceData) {
            val positionList = listOf(
                Pair(0.5f, 0.5f),
                Pair(0.0f, 0.0f),
                Pair(0.0f, 0.5f),
                Pair(0.0f, 1.0f),
                Pair(0.5f, 1.0f),
                Pair(1.0f, 1.0f),
                Pair(1.0f, 0.5f),
                Pair(1.0f, 0.0f),
                Pair(0.5f, 0.0f))


            positionList.map { normalizedScreenPos ->
                logger.info("Subject looking at $normalizedScreenPos")

                (0 until 60).map { s ->
                    val timestamp = getPupilTimestamp()

                    val datum0 = hashMapOf(
                        "norm_pos" to normalizedScreenPos.toList(),
                        "timestamp" to timestamp,
                        "id" to 0
                    )

                    val datum1 = hashMapOf(
                        "norm_pos" to normalizedScreenPos.toList(),
                        "timestamp" to timestamp,
                        "id" to 1
                    )

                    referenceData.add(datum0)
                    referenceData.add(datum1)

                    Thread.sleep(16)
                }
            }
        }

        notify(hashMapOf(
            "subject" to "calibration.add_ref_data",
            "ref_data" to referenceData
        ))

        notify(hashMapOf(
            "subject" to "calibration.should_stop"
        ))

        Thread.sleep(2000)

        logger.info("Calibration successful")

        isCalibrated = true
    }
}
