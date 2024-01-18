package graphics.scenery.tests.examples.volumes.benchmarks

import graphics.scenery.Camera
import graphics.scenery.SceneryElement
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.vdi.benchmarks.BenchmarkSetup
import org.joml.Vector3f
import kotlin.concurrent.thread

class VolumeRenderingBenchmarkRunner {
    val benchmarkDatasets = listOf<BenchmarkSetup.Dataset>(BenchmarkSetup.Dataset.Kingsnake, BenchmarkSetup.Dataset.Rayleigh_Taylor, BenchmarkSetup.Dataset.Richtmyer_Meshkov)
    val benchmarkViewpoints = listOf(10, 20, 30, 40)
    val benchmarkVos = listOf(0, 90, 180, 270)

    fun vdiRenderingBenchmarks(dataset: String, viewpoint: Int, renderer: Renderer, skipEmpty: Boolean = false) {

        Thread.sleep(2000) //allow the rotation to take place

        renderer.screenshot("/datapot/aryaman/benchmark_images/VDI_${dataset}_${viewpoint}_${skipEmpty}.png")

        Thread.sleep(1000)
    }

    fun runTest(dataset: String, vo: Int, windowWidth: Int, windowHeight: Int, dataName: BenchmarkSetup.Dataset) {
        val instance = VolumeRenderingBenchmark(windowWidth, windowHeight, dataName)
        thread {
            while (instance.hub.get(SceneryElement.Renderer)==null) {
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

            if (dataName == BenchmarkSetup.Dataset.Richtmyer_Meshkov) {
                rotateCamera(0f, vo.toFloat(), instance.cam, instance.windowWidth, instance.windowHeight, target)
                instance.cam.spatial().updateWorld(false, true)
            } else {
                rotateCamera(vo.toFloat(), 0f, instance.cam, instance.windowWidth, instance.windowHeight, target)
                instance.cam.spatial().updateWorld(false, true)
            }
            Thread.sleep(2000)

            var previousViewpoint = 0
            benchmarkViewpoints.forEach { viewpoint->
                val rotation = viewpoint - previousViewpoint
                previousViewpoint = viewpoint

                if (dataName == BenchmarkSetup.Dataset.Richtmyer_Meshkov) {
                    rotateCamera(0f, rotation.toFloat(), instance.cam, instance.windowWidth, instance.windowHeight, target)
                    instance.cam.spatial().updateWorld(false, true)
                } else {
                    rotateCamera(rotation.toFloat(), 0f, instance.cam, instance.windowWidth, instance.windowHeight, target)
                    instance.cam.spatial().updateWorld(false, true)
                }

                vdiRenderingBenchmarks(dataset, viewpoint, renderer, false)
            }

            renderer.shouldClose = true

            instance.close()
        }

        instance.main()
    }


    fun runVolumeRendering() {
        val windowWidth = 1920
        val windowHeight = 1080

        benchmarkVos.forEach { vo ->
                benchmarkDatasets.forEach { dataName ->
                    val dataset = "${dataName}_${windowWidth}_${windowHeight}_$vo"
                    System.setProperty("VDIBenchmark.Dataset", dataName.name)
                    System.setProperty("VDIBenchmark.WindowWidth", windowWidth.toString())
                    System.setProperty("VDIBenchmark.WindowHeight", windowHeight.toString())
                    System.setProperty("VDIBenchmark.Vo", vo.toString())

                    runTest(dataset, vo, windowWidth, windowHeight, dataName)
                    println("Got the control back")
                }
        }
    }

    fun rotateCamera(yaw: Float, pitch: Float = 0f, cam: Camera, windowWidth: Int, windowHeight: Int, target: Vector3f) {
        val arcballCameraControl = ArcballCameraControl("fixed_rotation", { cam }, windowWidth, windowHeight, target)
        arcballCameraControl.rotateDegrees(yaw,pitch)
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            VolumeRenderingBenchmarkRunner().runVolumeRendering()
        }
    }
}
