package graphics.scenery.tests.examples.volumes

import graphics.scenery.SceneryElement
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.Statistics
import java.io.*
import kotlin.concurrent.thread


class VDIBenchmarkRunner {

    val benchmarkDatasets = listOf<String>("Kingsnake", "Beechnut", "Simulation")
    val benchmarkViewpoints = listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50)

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
        val fw = FileWriter("${dataset}_${viewpoint}_downsampling.csv", true)
        val bw = BufferedWriter(fw)

        val start = 1f
        val until = 0.1f
        val step = 0.1f
        val totalSteps = ((until - start)/step).toInt() + 1

        var stepCount = 0

        var factor = start

        while (factor >= until) {
            instance.downsampleImage(factor)
            Thread.sleep(2000) //allow the change to take place
            scaleSamplingFactor(dataset, "${viewpoint}_${stepCount}", false, instance, renderer, bw)
            Thread.sleep(1000)
            stepCount ++
            factor -= step
        }
    }

    fun scaleSamplingFactor(dataset: String, screenshotName: String, stratified: Boolean, instance: VDIRenderingExample, renderer: Renderer, bw: BufferedWriter) {
        val start = 0.02f
        val until = 0.4f
        val step = 0.02f
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

        renderer.screenshot("VDI_${dataset}_${screenshotName}_Step0_${stratified}.png")

        bw.append("$fps")
        bw.append(", ")

        instance.doDownsampling(true)
        instance.setStratifiedDownsampling(stratified)


        while (factor <= until) {

            instance.setDownsamplingFactor(factor)
            Thread.sleep(1000) //allow the change to take place

            stats.clear("Renderer.fps")

            Thread.sleep(4000) //collect data for some time

            val fps = stats.get("Renderer.fps")!!.avg()

            renderer.screenshot("VDI_${dataset}_${screenshotName}_Step${stepCount}_${stratified}.png")

            bw.append("$fps")

            factor += step
            stepCount++

            if(stepCount != totalSteps) {
                bw.append(", ")
            }
        }
        bw.newLine()
        bw.flush()
    }



    fun runVDIRendering() {
        benchmarkDatasets.forEach { dataset->
            System.setProperty("VDIBenchmark.Dataset", dataset)

            val instance = VDIRenderingExample()

            thread {
                while (instance.hub.get(SceneryElement.Renderer)==null) {
                    Thread.sleep(50)
                }

                val renderer = (instance.hub.get(SceneryElement.Renderer) as Renderer)

                while (!renderer.firstImageReady) {
                    Thread.sleep(50)
                }

                val previousViewpoint = 0
                benchmarkViewpoints.forEach { viewpoint->
                    val rotation = viewpoint - previousViewpoint
                    instance.rotateCamera(rotation.toFloat())

                    downsamplingBenchmarks(dataset, viewpoint, instance, renderer)
                }


                renderer.shouldClose = true

                instance.close()
            }

            instance.main()

            println("Got the control back")
        }
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            VDIBenchmarkRunner().runVDIRendering()
        }
    }
}
