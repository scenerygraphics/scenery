package graphics.scenery.tests.examples.volumes.benchmarks

import java.io.File
import graphics.scenery.Camera
import graphics.scenery.SceneryBase
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

    private val isTestMode: Boolean = System.getenv("TEST_MODE")?.toBoolean() ?: false

    fun vdiRenderingBenchmarks(vdiProperties: String, datasetName: String, viewpoint: Int, renderer: Renderer, skipEmpty: Boolean = false, additionalParameters: String = "", overwriteFiles: Boolean): Int {

        val datasetDirectory = File("$screenshotDirectory/$datasetName")
        if (!datasetDirectory.exists()) {
            datasetDirectory.mkdirs()
        }

        val screenshotPath = "$datasetDirectory/VDI_${vdiProperties}${additionalParameters}_${viewpoint}_${skipEmpty}.png"
        val screenshotFile = File(screenshotPath)

        if(!isTestMode) {
            if(overwriteFiles && screenshotFile.exists()) {
                screenshotFile.delete() // Delete the existing file if overwriting is enabled
            }
            renderer.screenshot(screenshotPath)
        } else {
            println("Skipping screenshot for $vdiProperties with viewpoint $viewpoint")
        }

        Thread.sleep(2000)

        if(!screenshotFile.exists()) {
            println("Screenshot for $vdiProperties with viewpoint $viewpoint was not created. This test needs to be run again")
            return -1
        } else {
            return 0
        }
    }

    fun runTest(instance: VDIRenderingBenchmark, vdiProperties: String, vo: Int, windowWidth: Int, windowHeight: Int, dataName: BenchmarkSetup.Dataset, ns: Int,
                additionalParameters: String = "", applyTo: List<String>? = null, overwrite: Boolean): Int {

        try {
            instance.updateParameters(dataName, ns, vo, additionalParameters, applyTo)
            instance.cam.spatial().updateWorld(false, true)
            Thread.sleep(2000)

            val renderer = instance.hub.get(SceneryElement.Renderer)!! as Renderer

            val volumeDims = BenchmarkSetup(dataName).getVolumeDims()
            val pixelToWorld = (0.0075f * 512f) / volumeDims.x

            val target = volumeDims * pixelToWorld * 0.5f
            target.y *= -1

            if(vo != 0) {
                if (dataName == BenchmarkSetup.Dataset.Richtmyer_Meshkov) {
                    rotateCamera(0f, vo.toFloat(), instance.cam, instance.windowWidth, instance.windowHeight, target)
                    instance.cam.spatial().updateWorld(false, true)
                } else {
                    rotateCamera(vo.toFloat(), 0f, instance.cam, instance.windowWidth, instance.windowHeight, target)
                    instance.cam.spatial().updateWorld(false, true)
                }
                Thread.sleep(2000)
            }

            var previousViewpoint = 0
            benchmarkViewpoints!!.forEach { viewpoint->
                val rotation = viewpoint - previousViewpoint
                println("Rotating camera by $rotation degrees")
                previousViewpoint = viewpoint


                val startPosition = instance.cam.spatial().position

                if (dataName == BenchmarkSetup.Dataset.Richtmyer_Meshkov) {
                    rotateCamera(0f, rotation.toFloat(), instance.cam, instance.windowWidth, instance.windowHeight, target)
                    instance.cam.spatial().updateWorld(false, true)
                } else {
                    rotateCamera(rotation.toFloat(), 0f, instance.cam, instance.windowWidth, instance.windowHeight, target)
                    instance.cam.spatial().updateWorld(false, true)
                }
                // wait for the rotation to take place
                Thread.sleep(2000)

                val endPosition = instance.cam.spatial().position

                if (startPosition == endPosition) {
                    println("Camera did not rotate. This test needs to be run again")
                    return -1
                }

                val success = vdiRenderingBenchmarks(vdiProperties, dataName.toString(), viewpoint, renderer, false, additionalParameters, overwrite)
                if(success < 0) {
                    println("Test failed for $vdiProperties with viewpoint $viewpoint. This test needs to be run again")
                    return -1
                }
            }
        } catch (e: Exception) {
            println("Exception occurred: ${e.message}")
            e.printStackTrace()
            println("Test failed for $vdiProperties with additional parameters $additionalParameters. This test needs to be run again")
            return -1
        }

        return 0
    }


    fun runVDIRendering(datasetNames: List<String>) {
        val windowWidth = 1920
        val windowHeight = 1080

        val config = Json.parseToJsonElement(File(System.getenv("BENCHMARK_CONFIG_FILE")).readText()).jsonObject
        val overwriteFiles = System.getenv("OVERWRITE_FILES")?.toBoolean() ?: false

        val inputDirectory = System.getenv("INPUT_DIRECTORY")
        screenshotDirectory = System.getenv("SCREENSHOT_DIRECTORY") ?: "."

        System.setProperty("VDIBenchmark.VDI_DIRECTORY", inputDirectory)

        // Retrieve the benchmark parameters from the config file
        benchmarkDatasets = datasetNames.map { BenchmarkSetup.Dataset.valueOf(it) }
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

        val instance = VDIRenderingBenchmark("VDI Rendering Benchmark", windowWidth, windowHeight, benchmarkDatasets!!.first(), benchmarkSupersegments!!.first(), benchmarkVos!!.first())

        thread {

            while (instance.hub.get(SceneryElement.Renderer)==null) {
                Thread.sleep(50)
            }

            val renderer = (instance.hub.get(SceneryElement.Renderer) as Renderer)

            while (!renderer.firstImageReady) {
                Thread.sleep(50)
            }

            Thread.sleep(1000)

            benchmarkVos!!.forEach { vo ->
                benchmarkSupersegments!!.forEach { ns ->
                    benchmarkDatasets!!.forEach { dataName ->
                        val vdiProperties = "${dataName}_${windowWidth}_${windowHeight}_${ns}_$vo"
                        System.setProperty("VDIBenchmark.Dataset", dataName.name)
                        System.setProperty("VDIBenchmark.WindowWidth", windowWidth.toString())
                        System.setProperty("VDIBenchmark.WindowHeight", windowHeight.toString())
                        System.setProperty("VDIBenchmark.NumSupersegments", ns.toString())
                        System.setProperty("VDIBenchmark.Vo", vo.toString())

                        if(combinations != null) {
                            for (combination in combinations) {
                                var additionalParameters = combination.joinToString("_")
                                additionalParameters = "_$additionalParameters"
                                println("Running test for $vdiProperties with additional parameters $additionalParameters")

                                var overwrite = overwriteFiles

                                var success = false
                                while (!success) {
                                    val ret = runTest(instance, vdiProperties, vo, windowWidth, windowHeight, dataName, ns, additionalParameters, applyTo, overwrite)
                                    if(ret < 0) {
                                        println("Test failed for params: $additionalParameters. Retrying...")
                                        overwrite = true
                                    } else {
                                        success = true
                                    }
                                }
                                println("Got the control back")
                            }
                        } else {
                            runTest(instance, vdiProperties, vo, windowWidth, windowHeight, dataName, ns, overwrite = overwriteFiles)
                            println("Got the control back")
                        }
                    }
                }
            }

            renderer.close()
            instance.close()
        }

        instance.main()
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
            if (args.isEmpty()) {
                println("Please provide dataset names as command line arguments.")
                return
            }
            VDIRenderingBenchmarkRunner().runVDIRendering(args.toList())
        }
    }
}
