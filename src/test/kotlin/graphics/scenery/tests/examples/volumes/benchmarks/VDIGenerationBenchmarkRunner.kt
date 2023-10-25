package graphics.scenery.tests.examples.benchmarks

import graphics.scenery.Camera
import graphics.scenery.SceneryElement
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.tests.examples.volumes.VDIGenerationExample
import graphics.scenery.utils.Statistics
import org.joml.Vector3f
import java.io.BufferedWriter
import java.io.FileWriter
import kotlin.concurrent.thread


class GenerationBenchmarkRunner {
    val benchmarkDatasets = listOf<String>("Simulation")
    val benchmarkSupersegments = listOf(15, 20, 30, 40)

    fun benchmarkVDIGeneration(windowWidth: Int, windowHeight: Int) {
        benchmarkDatasets.forEach { dataName ->
            val dataset = "${dataName}_${windowWidth}_$windowHeight"

            val fw = FileWriter("D:/1.Uni/Bachelor/spreadsheets/${dataset}_vdigeneration.csv", true)
            val bw = BufferedWriter(fw)

            benchmarkSupersegments.forEach { ns ->
                val instance = VDIGenerationExample(windowWidth, windowHeight, ns)

                thread {
                    while (instance.hub.get(SceneryElement.Renderer) == null) {
                        Thread.sleep(50)
                    }

                    val renderer = (instance.hub.get(SceneryElement.Renderer) as Renderer)

                    while (!renderer.firstImageReady) {
                        Thread.sleep(50)
                    }

                    val stats = instance.hub.get<Statistics>()!!

                    val target = Vector3f(0.0f, 0.0f, -3.5f)

                    Thread.sleep(1000)

                    var numGenerated = 0
                    (renderer as VulkanRenderer).postRenderLambdas.add {
                        rotateCamera(10f, instance.cam,instance.windowWidth, instance.windowHeight, target)
                        numGenerated += 1
                        if (numGenerated % 10 == 0){
                            println(numGenerated)
                        }
                    }
                    while (numGenerated < 10) {
                        Thread.sleep(50)
                    }

                    stats.clear("Renderer.fps")

                    while (numGenerated < 20) {
                        Thread.sleep(50)
                    }
//                    Thread.sleep(1000)

                    val fps = stats.get("Renderer.fps")!!.avg()

                    bw.append("$fps, ")

                    println("Wrote fps: $fps")

                    renderer.shouldClose = true

                    instance.close()

                }
                instance.main()
            }
            bw.flush()
        }
        println("Got back control")
    }

    fun rotateCamera(yaw: Float, cam: Camera, windowWidth: Int, windowHeight: Int, target: Vector3f) {
        val arcballCameraControl = ArcballCameraControl("fixed_rotation", { cam }, windowWidth, windowHeight, target)
        arcballCameraControl.rotateDegrees(yaw,0f)
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            GenerationBenchmarkRunner().benchmarkVDIGeneration(1280, 720)
        }
    }
}
