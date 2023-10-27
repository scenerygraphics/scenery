package graphics.scenery.tests.examples.volumes.benchmarks

import graphics.scenery.Camera
import graphics.scenery.SceneryElement
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.tests.examples.volumes.VDIRenderingExample
import org.joml.Vector3f
import kotlin.concurrent.thread

class VDIRenderingBenchmarkRunner {
    val benchmarkDatasets = listOf<String>("Simulation")
    val benchmarkViewpoints = listOf(5, 10, 15, 20, 25, 30, 35, 40)
    val benchmarkSupersegments = listOf(20)
    val benchmarkVos = listOf(0, 90, 180, 270)

    fun vdiRenderingBenchmarks(dataset: String, viewpoint: Int, renderer: Renderer, skipEmpty: Boolean = false) {

        Thread.sleep(2000) //allow the rotation to take place

        renderer.screenshot("D:/1.Uni/Bachelor/screenshots/VDI_${dataset}_${viewpoint}_$skipEmpty.png")

        Thread.sleep(1000)
    }

    fun runTest(dataName: String, dataset: String, vo: Int) {
        val instance = VDIRenderingExample()

        thread {
            while (instance.hub.get(SceneryElement.Renderer)==null) {
                Thread.sleep(50)
            }

            val renderer = (instance.hub.get(SceneryElement.Renderer) as Renderer)

            while (!renderer.firstImageReady) {
                Thread.sleep(50)
            }

            val target = Vector3f(0.0f, 0.0f, -3.5f)

            Thread.sleep(1000)

            rotateCamera(vo.toFloat(), instance.cam, instance.windowWidth, instance.windowHeight, target)

            var previousViewpoint = 0
            benchmarkViewpoints.forEach { viewpoint->
                val rotation = viewpoint - previousViewpoint
                previousViewpoint = viewpoint

                rotateCamera(rotation.toFloat(),instance.cam, instance.windowWidth, instance.windowHeight, target)

                vdiRenderingBenchmarks(dataset, viewpoint, renderer, false)
            }

            renderer.shouldClose = true

            instance.close()
        }

        instance.main()
    }


    fun runVDIRendering() {
        val windowWidth = 1920
        val windowHeight = 1080

        benchmarkVos.forEach { vo ->
            benchmarkSupersegments.forEach { ns ->
                benchmarkDatasets.forEach { dataName ->
                    val dataset = "${dataName}_${windowWidth}_${windowHeight}_${ns}_$vo"
                    System.setProperty("VDIBenchmark.Dataset", dataName)
                    System.setProperty("VDIBenchmark.WindowWidth", windowWidth.toString())
                    System.setProperty("VDIBenchmark.WindowHeight", windowHeight.toString())
                    System.setProperty("VDIBenchmark.NumSupersegments", ns.toString())
                    System.setProperty("VDIBenchmark.Vo", vo.toString())

                    runTest(dataName, dataset, vo)
                    println("Got the control back")
                }
            }
        }
    }

    fun rotateCamera(yaw: Float, cam: Camera, windowWidth: Int, windowHeight: Int, target: Vector3f) {
        val arcballCameraControl = ArcballCameraControl("fixed_rotation", { cam }, windowWidth, windowHeight, target)
        arcballCameraControl.rotateDegrees(yaw,0f)
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            VDIRenderingBenchmarkRunner().runVDIRendering()
        }
    }
}
