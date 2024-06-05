package graphics.scenery.tests.examples.volumes.benchmarks

import graphics.scenery.Camera
import graphics.scenery.SceneryElement
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.utils.Statistics
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.vdi.benchmarks.BenchmarkSetup
import org.joml.Vector3f
import java.io.BufferedWriter
import java.io.FileWriter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class VDIGenerationBenchmarkRunner {
    val benchmarkDatasets = listOf<BenchmarkSetup.Dataset>(BenchmarkSetup.Dataset.Rayleigh_Taylor)
    val benchmarkSupersegments = listOf(20)
    /**
     * Generates a sequence of VDIs, with the camera rotating at 10 degree increments between successive VDIs
     */
    fun generateVDISequence(windowWidth: Int, windowHeight: Int, numberOfVDIs: Int) {
        benchmarkDatasets.forEach { dataName ->

            benchmarkSupersegments.forEach { ns ->
                val instance = VDIGenerationBenchmark(windowWidth, windowHeight, ns, dataName, true)

                thread {
                    while (instance.hub.get(SceneryElement.Renderer) == null) {
                        Thread.sleep(50)
                    }

                    val renderer = (instance.hub.get(SceneryElement.Renderer) as Renderer)

                    while (!renderer.firstImageReady) {
                        Thread.sleep(50)
                    }

                    val volumeDims = BenchmarkSetup(dataName).getVolumeDims()
                    val pixelToWorld = (0.0075f * 512f) / volumeDims.x

                    val target = volumeDims * pixelToWorld * 0.5f
                    target.y *= -1

                    Thread.sleep(1000)

                    var numGenerated = 0
                    (renderer as VulkanRenderer).runAfterRendering.add {
                        if (instance.VDIsGenerated.get() > 0) {
                            if (dataName == BenchmarkSetup.Dataset.Richtmyer_Meshkov) {
                                rotateCamera(0f, 10f, instance.cam, instance.windowWidth, instance.windowHeight, target)
                            } else {
                                rotateCamera(10f, 0f, instance.cam, instance.windowWidth, instance.windowHeight, target)
                            }
                        }
                        numGenerated += 1
                    }

                    while (numGenerated < 10) { //some warm up iterations
                        Thread.sleep(20)
                    }

                    instance.writeVDIs = AtomicBoolean(true)

                    while (numGenerated < (numberOfVDIs + 10)) { // Let the VDIs be written
                        Thread.sleep(20)
                    }

                    instance.writeVDIs = AtomicBoolean(false)

                    renderer.shouldClose = true

                    instance.close()
                }
                instance.main()
            }
        }
    }

    fun benchmarkVDIGeneration(windowWidth: Int, windowHeight: Int) {
        benchmarkDatasets.forEach { dataName ->
            val dataset = "${dataName}_${windowWidth}_$windowHeight"

            val fw = FileWriter("/home/charles/bachelor/csv/${dataset}_vdigeneration.csv", true)
            val bw = BufferedWriter(fw)

            benchmarkSupersegments.forEach { ns ->
                val instance = VDIGenerationBenchmark(windowWidth, windowHeight, ns, dataName, false)

                thread {
                    while (instance.hub.get(SceneryElement.Renderer) == null) {
                        Thread.sleep(50)
                    }

                    val renderer = (instance.hub.get(SceneryElement.Renderer) as Renderer)

                    while (!renderer.firstImageReady) {
                        Thread.sleep(50)
                    }

                    val stats = instance.hub.get<Statistics>()!!

                    val volumeDims = BenchmarkSetup(dataName).getVolumeDims()
                    val pixelToWorld = (0.0075f * 512f) / volumeDims.x

                    val target = volumeDims * pixelToWorld * 0.5f
                    target.y *= -1

                    Thread.sleep(1000)

                    var numGenerated = AtomicInteger(0)
                    (renderer as VulkanRenderer).runAfterRendering.add {
                        if (instance.VDIsGenerated.get() > 0) {
                            if (dataName == BenchmarkSetup.Dataset.Richtmyer_Meshkov) {
                                rotateCamera(0f, 10f, instance.cam, instance.windowWidth, instance.windowHeight, target)
                                instance.cam.spatial().updateWorld(false, true)
                            } else {
                                rotateCamera(10f, 0f, instance.cam, instance.windowWidth, instance.windowHeight, target)
                                instance.cam.spatial().updateWorld(false, true)
                            }
                        }
                        numGenerated.incrementAndGet()

                    }

                    while (numGenerated.get() < 10) {
                        Thread.sleep(20)
                    }

                    stats.clear("Renderer.fps")

                    Thread.sleep(1000)

                    val currentIterations = numGenerated.get()

                    while (numGenerated.get() < currentIterations + 100) {
                        Thread.sleep(20)
                    }

                    val fps = stats.get("Renderer.fps")!!.avg()
                    val stddev = stats.get("Renderer.fps")!!.stddev()

                    bw.append("$fps, ")
                    bw.append("$stddev")
                    bw.newLine()

                    println("Wrote fps: $fps")

                    renderer.shouldClose = true

                    instance.close()

                }
                instance.main()
            }
            bw.newLine()
            bw.flush()
        }
    }

    fun rotateCamera(yaw: Float, pitch: Float = 0f, cam: Camera, windowWidth: Int, windowHeight: Int, target: Vector3f) {
        val arcballCameraControl = ArcballCameraControl("fixed_rotation", { cam }, windowWidth, windowHeight, target)
        arcballCameraControl.rotateDegrees(yaw,pitch)
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            VDIGenerationBenchmarkRunner().generateVDISequence(1920, 1080, 5)
//            VDIGenerationBenchmarkRunner().benchmarkVDIGeneration(1920, 1080)
        }
    }
}
