package graphics.scenery.tests.examples.volumes

import graphics.scenery.SceneryElement
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.Statistics
import java.io.*
import kotlin.concurrent.thread


class VDIBenchmarkRunner {

    val benchmarkDatasets = listOf<String>("Kingsnake", "Beechnut", "Simulation")
    val benchmarkViewpoints = listOf(5, 10, 15, 20, 25, 30, 35, 40)
    val benchmarkSupersegments = listOf(20)
    val benchmarkVos = listOf(0, 90, 180, 270)

    fun OutputStream.appendEntry(entry: String) {
        val writer = bufferedWriter()
        writer.write("$entry ")
        writer.flush()
    }

    fun stratifiedBenchmarks(dataset: String, viewpoint: Int, instance: VDIRenderingExample, renderer: Renderer) {
        val fw = FileWriter("${dataset}_stratified.csv", true)
        val bw = BufferedWriter(fw)

        scaleSamplingFactor(dataset, viewpoint.toString(), true, instance, renderer, bw)

        val fw_non_stratified = FileWriter("${dataset}_nonstratified.csv", true)
        val bw_non_stratified = BufferedWriter(fw_non_stratified)

        scaleSamplingFactor(dataset, viewpoint.toString(), false, instance, renderer, bw_non_stratified)
    }

    fun downsamplingBenchmarks(dataset: String, viewpoint: Int, instance: VDIRenderingExample, renderer: Renderer) {
        val fw = FileWriter("benchmarking/downsampling/${dataset}_${viewpoint}_downsampling.csv", true)
        val bw = BufferedWriter(fw)

        val start = 1f
        val until = 0.0f
        val step = 0.2f
        val totalSteps = 5

        var stepCount = 0

        var factor = start

        while (stepCount < totalSteps) {
            instance.downsampleImage(factor)
            Thread.sleep(2000) //allow the change to take place
            scaleSamplingFactor(dataset, "${viewpoint}_${stepCount}", false, instance, renderer, bw)
            Thread.sleep(1000)
            stepCount ++
            factor -= step
        }
    }

    fun vdiRenderingBenchmarks(dataset: String, viewpoint: Int, instance: VDIRenderingExample, renderer: Renderer, skipEmpty: Boolean = false) {
        val fw = FileWriter("benchmarking/${dataset}_vdiRendering_$skipEmpty.csv", true)
        val bw = BufferedWriter(fw)

        val stats = instance.hub.get<Statistics>()!!

        instance.setEmptySpaceSkipping(skipEmpty)

        Thread.sleep(2000) //allow the rotation and change in empty space skipping to take place

        stats.clear("Renderer.fps")

        Thread.sleep(4000) //collect some data

        val fps = stats.get("Renderer.fps")!!.avg()
        val stdDev = stats.get("Renderer.fps")!!.stddev()

        renderer.screenshot("benchmarking/VDI_${dataset}_${viewpoint}_$skipEmpty.png")

        bw.append("$fps")
        bw.append(", ")

        bw.flush()
    }

    fun scaleSamplingFactor(dataset: String, screenshotName: String, stratified: Boolean, instance: VDIRenderingExample, renderer: Renderer, bw: BufferedWriter) {
        val start = 0.02f
        val until = 0.4f
        val step = 0.04f
        val totalSteps = ((until - start)/step).toInt() + 1

        var stepCount = 1

        var factor = start

        val stats = instance.hub.get<Statistics>()!!

        //do a single step with no downsampling
        instance.doDownsampling(false)
        Thread.sleep(1000) //allow the change to take place

        stats.clear("Renderer.fps")

        Thread.sleep(4000) //collect data for some time

        val fps = stats.get("Renderer.fps")!!.avg()

        println("Recorded initial frame rate")

        renderer.screenshot("benchmarking/downsampling/VDI_${dataset}_${screenshotName}_Step0_${stratified}.png")

        bw.append("$fps")
        bw.append(", ")

        instance.doDownsampling(true)
        instance.setStratifiedDownsampling(stratified)


        while (factor < until) {

            instance.setDownsamplingFactor(factor)
            Thread.sleep(1000) //allow the change to take place

            stats.clear("Renderer.fps")

            Thread.sleep(4000) //collect data for some time

            val fps = stats.get("Renderer.fps")!!.avg()

            renderer.screenshot("benchmarking/downsampling/VDI_${dataset}_${screenshotName}_Step${stepCount}_${stratified}.png")

            bw.append("$fps")

            factor += step

            if(stepCount != totalSteps) {
                bw.append(", ")
            }
            stepCount++
        }
        bw.newLine()
        bw.flush()
    }



    fun runVDIRendering() {

        val windowWidth = 1920
        val windowHeight = 1080

        benchmarkVos.forEach { vo ->
            benchmarkSupersegments.forEach { ns ->
                benchmarkDatasets.forEach { dataName->

                    val dataset = "${dataName}_${windowWidth}_${windowHeight}_${ns}_$vo"

                    System.setProperty("VDIBenchmark.Dataset", dataName)
                    System.setProperty("VDIBenchmark.WindowWidth", windowWidth.toString())
                    System.setProperty("VDIBenchmark.WindowHeight", windowHeight.toString())
                    System.setProperty("VDIBenchmark.NumSupersegments", ns.toString())
                    System.setProperty("VDIBenchmark.Vo", vo.toString())

                    val instance = VDIRenderingExample()

                    thread {
                        while (instance.hub.get(SceneryElement.Renderer)==null) {
                            Thread.sleep(50)
                        }

                        val renderer = (instance.hub.get(SceneryElement.Renderer) as Renderer)

                        while (!renderer.firstImageReady) {
                            Thread.sleep(50)
                        }

                        var previousViewpoint = 0
                        benchmarkViewpoints.forEach { viewpoint->
                            val rotation = viewpoint - previousViewpoint
                            previousViewpoint = viewpoint

                            val pitch = (dataName == "Simulation")
                            instance.rotateCamera(rotation.toFloat(), pitch)

                            vdiRenderingBenchmarks(dataset, viewpoint, instance, renderer, false)
                            vdiRenderingBenchmarks(dataset, viewpoint, instance, renderer, true)
                        }


                        renderer.shouldClose = true

                        instance.close()
                    }

                    instance.main()

                    println("Got the control back")
                }
            }
        }
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            VDIBenchmarkRunner().runVDIRendering()
        }
    }
}
