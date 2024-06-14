package graphics.scenery.tests.examples.volumes.benchmarks

import java.io.File
import graphics.scenery.Camera
import graphics.scenery.SceneryElement
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.vdi.benchmarks.BenchmarkSetup
import kotlinx.serialization.json.*
import org.joml.Vector3f
import kotlin.concurrent.thread

class VDIRenderingBenchmarkRunner {
    var benchmarkDatasets: List<BenchmarkSetup.Dataset>? = null
    var benchmarkViewpoints: List<Int>? = null
    var benchmarkSupersegments: List<Int>? = null
    var benchmarkVos: List<Int>? = null

    lateinit var screenshotDirectory: String

    fun vdiRenderingBenchmarks(dataset: String, viewpoint: Int, renderer: Renderer, skipEmpty: Boolean = false, additionalParameters: String = "") {

        Thread.sleep(2000) //allow the rotation to take place

        renderer.screenshot(screenshotDirectory + "/VDI_${dataset}${additionalParameters}_${viewpoint}_${skipEmpty}.png")

        Thread.sleep(1000)
    }

    fun runTest(dataset: String, vo: Int, windowWidth: Int, windowHeight: Int, dataName: BenchmarkSetup.Dataset, ns: Int,
    additionalParameters: String = "", applyTo: List<String>? = null) {
        val instance = VDIRenderingBenchmark("VDI Rendering Benchmark", windowWidth, windowHeight, dataName, ns, vo, additionalParameters, applyTo)
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
            benchmarkViewpoints!!.forEach { viewpoint->
                val rotation = viewpoint - previousViewpoint
                previousViewpoint = viewpoint

                if (dataName == BenchmarkSetup.Dataset.Richtmyer_Meshkov) {
                    rotateCamera(0f, rotation.toFloat(), instance.cam, instance.windowWidth, instance.windowHeight, target)
                    instance.cam.spatial().updateWorld(false, true)
                } else {
                    rotateCamera(rotation.toFloat(), 0f, instance.cam, instance.windowWidth, instance.windowHeight, target)
                    instance.cam.spatial().updateWorld(false, true)
                }

                vdiRenderingBenchmarks(dataset, viewpoint, renderer, false, additionalParameters)
            }

            renderer.shouldClose = true

            instance.close()
        }

        instance.main()
    }


    fun runVDIRendering() {
        val windowWidth = 1920
        val windowHeight = 1080

        // Parse the JSON file
        val config = Json.parseToJsonElement(File(System.getenv("BENCHMARK_CONFIG_FILE")).readText()).jsonObject

        // Retrieve the directories from the config file
        val inputDirectory = config["inputDirectory"]?.jsonPrimitive?.content
        screenshotDirectory = config["screenshotDirectory"]?.jsonPrimitive?.content ?: run {
            "."
        }

        System.setProperty("VDIBenchmark.VDI_DIRECTORY", inputDirectory)

        // Retrieve the benchmark parameters from the config file
        benchmarkDatasets = config["datasets"]?.jsonArray?.map { BenchmarkSetup.Dataset.valueOf(it.jsonPrimitive.content) }
        benchmarkViewpoints = config["viewpoints"]?.jsonArray?.map { it.jsonPrimitive.int }
        benchmarkSupersegments = config["supersegments"]?.jsonArray?.map { it.jsonPrimitive.int }
        benchmarkVos = config["vos"]?.jsonArray?.map { it.jsonPrimitive.int }

        if(benchmarkDatasets == null || benchmarkViewpoints == null || benchmarkSupersegments == null || benchmarkVos == null) {
            println("One or more of the benchmark parameters are missing from the config file")
            return
        }

        // Retrieve the "applyTo" parameter from the additionalParameters in the config file
        val applyTo = config["additionalParameters"]?.jsonObject?.get("applyTo")?.jsonArray?.map { it.jsonPrimitive.content }

        // Retrieve the additional parameters from the config file, excluding "applyTo"
        val additionalParameters = config["additionalParameters"]?.jsonObject?.filterKeys { it != "applyTo" }?.mapValues { (param, values) ->
            if (values is JsonObject && values.contains("start") && values.contains("end")) {
                // Generate a sequence of numbers for the parameter
                val start = values["start"]?.jsonPrimitive?.int
                val end = values["end"]?.jsonPrimitive?.int
                if (start != null && end != null) {
                    (start..end).map { it.toString() }
                } else {
                    null
                }
            } else {
                // Use the values as they are for other parameters
                values.jsonArray.map { it.jsonPrimitive.content }
            }
        }?.filterValues { it != null }?.mapValues { it.value!! }

        // Convert the map of additional parameters to a list of pairs
        val parametersList = additionalParameters?.toList()

        // Generate all possible combinations of parameter values
        val combinations = parametersList?.let { generateCombinations(it) }

        benchmarkVos!!.forEach { vo ->
            benchmarkSupersegments!!.forEach { ns ->
                benchmarkDatasets!!.forEach { dataName ->
                    val dataset = "${dataName}_${windowWidth}_${windowHeight}_${ns}_$vo"
                    System.setProperty("VDIBenchmark.Dataset", dataName.name)
                    System.setProperty("VDIBenchmark.WindowWidth", windowWidth.toString())
                    System.setProperty("VDIBenchmark.WindowHeight", windowHeight.toString())
                    System.setProperty("VDIBenchmark.NumSupersegments", ns.toString())
                    System.setProperty("VDIBenchmark.Vo", vo.toString())

                    if(combinations != null) {
                        for (combination in combinations) {
                            var additionalParameters = combination.joinToString("_")
                            additionalParameters = "_$additionalParameters"
                            println("Running test for $dataset with additional parameters $additionalParameters")
                            runTest(dataset, vo, windowWidth, windowHeight, dataName, ns, additionalParameters, applyTo)
                            println("Got the control back")
                        }
                    } else {
                        runTest(dataset, vo, windowWidth, windowHeight, dataName, ns)
                        println("Got the control back")
                    }
                }
            }
        }
    }

    private fun generateCombinations(parameters: List<Pair<String, List<String>>>, index: Int = 0): List<List<String>> {
        if (index == parameters.size) {
            return listOf(emptyList())
        }

        val currentParameter = parameters[index]
        val combinations = generateCombinations(parameters, index + 1)
        val result = mutableListOf<List<String>>()

        for (value in currentParameter.second) {
            for (combination in combinations) {
                result.add(listOf(value) + combination)
            }
        }

        return result
    }

    private fun rotateCamera(yaw: Float, pitch: Float = 0f, cam: Camera, windowWidth: Int, windowHeight: Int, target: Vector3f) {
        val arcballCameraControl = ArcballCameraControl("fixed_rotation", { cam }, windowWidth, windowHeight, target)
        arcballCameraControl.rotateDegrees(yaw,pitch)
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            VDIRenderingBenchmarkRunner().runVDIRendering()
        }
    }
}
