package graphics.scenery.tests.examples.volumes.benchmarks


import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.vdi.VDIDataIO
import graphics.scenery.volumes.vdi.VDINode
import graphics.scenery.volumes.vdi.benchmarks.BenchmarkSetup
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import org.scijava.ui.behaviour.ClickBehaviour
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class VDIRenderingBenchmark(applicationName: String, windowWidth: Int, windowHeight: Int, var dataset: BenchmarkSetup.Dataset, var ns: Int, var vo: Int,
                            var additionalParams: String = "", var applyTo: List<String>? = null): SceneryBase(applicationName, windowWidth, windowHeight) {

    val skipEmpty = false

    val numSupersegments = ns

    lateinit var vdiNode: VDINode
    val numLayers = 1

    val cam: Camera = DetachedHeadCamera()

    var num = 0;

    override fun init() {
        //Step 1: create a Renderer
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        initScene()
    }

    private fun setupCamera() {
        val benchmarkSetup = BenchmarkSetup(dataset)

        benchmarkSetup.positionCamera(cam)
        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)
        scene.addChild(cam)
    }

    private fun loadVDI() {
        val vdiDirectory = System.getProperty("VDIBenchmark.VDI_DIRECTORY", "")

        //Step 2: read files

        windowWidth = 1920
        windowHeight = 1080
        val regularPrefix = dataset.toString() + "_${windowWidth}_${windowHeight}_${numSupersegments}"

        val specificPrefix = vdiDirectory + "/" + dataset.toString() + "/" + dataset.toString() + "_${windowWidth}_${windowHeight}_${numSupersegments}"


        logger.info("Reading file with prefix regular $regularPrefix and specific $specificPrefix")

        when(vo){
            0 -> num = 0
            90 -> num = 1
            180 -> num = 2
            270 -> num = 3
        }

        val vdiData = applyTo?.let {
            val file = if (it.contains("all")) {
                FileInputStream(File("${specificPrefix}_${num}${additionalParams}.vdi-metadata"))
            } else {
                FileInputStream(File("${regularPrefix}_${num}.vdi-metadata"))
            }
            val vdiData = VDIDataIO.read(file)
            vdiData
        } ?: run {
            val file = FileInputStream(File("${regularPrefix}_${num}.vdi-metadata"))
            val vdiData = VDIDataIO.read(file)
            vdiData
        }

        vdiNode = VDINode(windowWidth, windowHeight, numSupersegments, vdiData)

        val colorArray = applyTo?.let {
            if ((it.contains("color") || it.contains("all"))) {
                File("${specificPrefix}_${num}${additionalParams}.vdi-color").readBytes()
            } else {
                File("${regularPrefix}_${num}.vdi-color").readBytes()
            }
        } ?: run {
            File("${regularPrefix}_${num}.vdi-color").readBytes()
        }

        val alphaArray = applyTo?.let {
            if ((it.contains("alpha") || it.contains("all"))) {
                File("${specificPrefix}_${num}${additionalParams}.vdi-alpha").readBytes()
            } else {
                File("${regularPrefix}_${num}.vdi-alpha").readBytes()
            }
        } ?: run {
            File("${regularPrefix}_${num}.vdi-alpha").readBytes()
        }

        val depthArray = applyTo?.let {
            if ((it.contains("depth") || it.contains("all"))) {
                File("${specificPrefix}_${num}${additionalParams}.vdi-depth").readBytes()
            } else {
                File("${regularPrefix}_${num}.vdi-depth").readBytes()
            }
        } ?: run {
            File("${regularPrefix}_${num}.vdi-depth").readBytes()
        }

        val octArray = applyTo?.let {
            if ((it.contains("grid") || it.contains("all"))) {
                File("${specificPrefix}_${num}${additionalParams}.vdi-grid").readBytes()
            } else {
                File("${regularPrefix}_${num}.vdi-grid").readBytes()
            }
        } ?: run {
            File("${regularPrefix}_${num}.vdi-grid").readBytes()
        }

        //Step  3: assigning buffer values
        val colBuffer: ByteBuffer = MemoryUtil.memCalloc(colorArray.size)
        colBuffer.put(colorArray).flip()
        colBuffer.limit(colBuffer.capacity())

        val alphaBuffer = MemoryUtil.memCalloc(alphaArray.size)
        alphaBuffer.put(alphaArray).flip()
        alphaBuffer.limit(alphaBuffer.capacity())

        val depthBuffer = MemoryUtil.memCalloc(depthArray.size)
        depthBuffer.put(depthArray).flip()
        depthBuffer.limit(depthBuffer.capacity())

        val gridBuffer = MemoryUtil.memCalloc(octArray.size)
        if(skipEmpty) {
            gridBuffer.put(octArray).flip()
        }

        //Step 4: Creating compute node and attach shader and vdi Files to
        vdiNode.attachTextures(colBuffer, alphaBuffer, depthBuffer, gridBuffer)

        vdiNode.skip_empty = skipEmpty

        //Attaching empty textures as placeholders for 2nd VDI buffer, which is unused here
        vdiNode.attachEmptyTextures(VDINode.DoubleBuffer.Second)

        scene.addChild(vdiNode)
    }

    fun updateParameters(newDataset: BenchmarkSetup.Dataset, newNs: Int, newVo: Int, newAdditionalParams: String = "", applyTo: List<String>? = null) {
        // Assuming these are the main parameters that might change between runs
        this.dataset = newDataset
        this.ns = newNs
        this.vo = newVo
        this.additionalParams = newAdditionalParams
        this.applyTo = applyTo

        resetScene()
        initScene()
    }

    private fun resetScene() {
        // Clean up the current scene
        scene.children.forEach {
            scene.removeChild(it)
        }
        vdiNode.close()
    }

    private fun initScene() {

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        setupCamera()

        loadVDI()

        val plane = FullscreenObject()
        plane.material().textures["diffuse"] = vdiNode.material().textures["OutputViewport"]!!
        scene.addChild(plane)
    }

    override fun inputSetup() {
        setupCameraModeSwitching()

        inputHandler?.addBehaviour("rotate_camera",
            ClickBehaviour { _, _ ->
                val volumeDims = BenchmarkSetup(dataset).getVolumeDims()
                val pixelToWorld = (0.0075f * 512f) / volumeDims.x

                val target = volumeDims * pixelToWorld * 0.5f
                target.y *= -1
                val arcballCameraControl = ArcballCameraControl("fixed_rotation", { scene.findObserver()!! }, windowWidth, windowHeight, target)
                if(dataset == BenchmarkSetup.Dataset.Richtmyer_Meshkov) {
                    arcballCameraControl.rotateDegrees(0f, 10f)
                } else {
                    arcballCameraControl.rotateDegrees(10f, 0f)
                }
            })
        inputHandler?.addKeyBinding("rotate_camera", "R")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {

            val instance = VDIRenderingBenchmark("VDI Rendering Benchmark", 1920, 1080, BenchmarkSetup.Dataset.Richtmyer_Meshkov, 20, 0)
            thread {
                println("in the thread")

                while (instance.hub.get(SceneryElement.Renderer)==null) {
                    Thread.sleep(50)
                }
                val renderer = (instance.hub.get(SceneryElement.Renderer) as Renderer)

                println("sleeping for 5 seconds")
                Thread.sleep(5000)

                println("Switching to Rayleigh-Taylor")
                instance.updateParameters(BenchmarkSetup.Dataset.Kingsnake, 20, 0)
            }

            instance.main()
        }
    }
}

