package graphics.scenery.tests.examples.volumes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.SceneryBase
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.vdi.VDIDataIO
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.volumes.*
import graphics.scenery.volumes.vdi.VDIBufferSizes
import graphics.scenery.utils.DataCompressor
import graphics.scenery.volumes.vdi.VDIData
import graphics.scenery.volumes.vdi.VDIMetadata
import org.joml.Vector2i
import org.joml.Vector3f
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import org.lwjgl.system.MemoryUtil
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import java.io.*
import kotlin.system.measureNanoTime
import org.msgpack.jackson.dataformat.MessagePackFactory

class VDIStreamingExample : SceneryBase("VDI Streaming Example", 512, 512) {

    val cam: Camera = DetachedHeadCamera()
    val context: ZContext = ZContext(4)

    val maxSupersegments = 20
    var cnt = 0

    var firstFrame = true
    val generateVDIs = true


    override fun init() {

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        //Step 1: create necessary components: camera, volume, volumeManager
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.5f, 5.0f)
            }
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            scene.addChild(this)
        }

        val volume = Volume.fromPathRaw(Paths.get(getDemoFilesPath() + "/volumes/box-iso/"), hub)
        volume.name = "volume"
        volume.colormap = Colormap.get("viridis")
        volume.spatial {
            position = Vector3f(0.0f, 0.0f, -3.5f)
            rotation = rotation.rotateXYZ(0.05f, 0.05f, 0.05f)
            scale = Vector3f(20.0f, 20.0f, 20.0f)
        }
        volume.transferFunction = TransferFunction.ramp(0.1f, 0.5f)
        scene.addChild(volume)

        // Step 2: Create VDI Volume Manager
        val vdiVolumeManager = VDIVolumeManager( hub, windowWidth, windowHeight, maxSupersegments, scene).createVDIVolumeManger()

        //step 3: switch the volume's current volume manager to VDI volume manager
        volume.volumeManager = vdiVolumeManager

        // Step 4: add the volume to VDI volume manager
        vdiVolumeManager.add(volume)

        // Step 5: add the VDI volume manager to the hub
        hub.add(vdiVolumeManager)

        //Step  6: transmitting the VDI
        settings.set("VideoEncoder.StreamVideo", true)
        settings.set("VideoEncoder.StreamingAddress", "rtp://10.1.33.211:5004")
        renderer?.recordMovie()
        setupSubscription()

        val volumeDimensions3i = Vector3f(volume.getDimensions().x.toFloat(),volume.getDimensions().y.toFloat(),volume.getDimensions().z.toFloat())
        val model = volume.spatial().world

        renderer?.streamVDI("tcp://localhost:6655",cam,volumeDimensions3i,model,context)
    }

    fun setupSubscription() {
        val subscriber: ZMQ.Socket = context.createSocket(SocketType.SUB)
        subscriber.isConflate = true
//     val address = "tcp://localhost:6655"
        val address = "tcp://10.1.33.211:6655"
        //IPADDRESS
        try {
            subscriber.connect(address)
        } catch (e: ZMQException) {
            logger.warn("ZMQ Binding failed.")
        }
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)

        val objectMapper = ObjectMapper(MessagePackFactory())
        var frameCount = 0
        var firstFrame = true

        (renderer as? VulkanRenderer)?.postRenderLambdas?.add {
            if(!firstFrame) {

                if(generateVDIs) {
                    logger.info("rendering is running!")
                }
                val start = System.nanoTime()
                val payload = subscriber.recv(0)
                val end = System.nanoTime()

                logger.info("Time waiting for message: ${(end-start)/1e9}")

                if (payload != null) {
                    val deserialized: List<Any> =
                        objectMapper.readValue(payload, object : TypeReference<List<Any>>() {})
                    logger.info("Applying the camera change: $frameCount!")
                }
                frameCount++
            }
            firstFrame = false
        }
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIStreamingExample().main()
        }
    }

}
