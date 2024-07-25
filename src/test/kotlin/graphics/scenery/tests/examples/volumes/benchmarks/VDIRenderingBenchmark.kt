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

class VDIRenderingBenchmark(applicationName: String, windowWidth: Int, windowHeight: Int, val dataset: BenchmarkSetup.Dataset, val ns: Int, val vo: Int,
    val additionalParams: String = "", val applyTo: List<String>? = null): SceneryBase(applicationName, windowWidth,windowHeight) {

    val skipEmpty = false

    val numSupersegments = ns

    lateinit var vdiNode: VDINode
    val numLayers = 1

    val cam: Camera = DetachedHeadCamera()

    var num = 0;

    override fun init() {

        //Step 1: create a Renderer, Point light and camera
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val benchmarkSetup = BenchmarkSetup(dataset)

        benchmarkSetup.positionCamera(cam)
        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowWidth)
            scene.addChild(this)
        }

        val vdiDirectory = System.getProperty("VDIBenchmark.VDI_DIRECTORY", "")

        //Step 2: read files

        val regularPrefix = dataset.toString() + "_${windowWidth}_${windowHeight}_${numSupersegments}"

        val specificPrefix = vdiDirectory + "/" + dataset.toString() + "_${windowWidth}_${windowHeight}_${numSupersegments}"


        logger.info("Reading file with prefix regular $regularPrefix and specific $specificPrefix")

        when(vo){
            0 -> num = 0
            90 -> num = 1
            180 -> num = 2
            270 -> num = 3
        }

        val file = if (applyTo != null && applyTo.contains("all")) {
            FileInputStream(File("${specificPrefix}_${num}${additionalParams}.vdi-metadata"))
        } else {
            FileInputStream(File("${regularPrefix}_${num}.vdi-metadata"))
        }
        val vdiData = VDIDataIO.read(file)
        logger.info("Fetching file...")

        vdiNode = VDINode(windowWidth, windowHeight, numSupersegments, vdiData)

        val colorArray: ByteArray = if (applyTo != null && (applyTo.contains("color") || applyTo.contains("all"))) {
            File("${specificPrefix}_${num}${additionalParams}.vdi-color").readBytes()
        } else {
            File("${regularPrefix}_${num}.vdi-color").readBytes()
        }
        val alphaArray: ByteArray = if (applyTo != null && (applyTo.contains("alpha") || applyTo.contains("all"))) {
            File("${specificPrefix}_${num}${additionalParams}.vdi-alpha").readBytes()
        } else {
            File("${regularPrefix}_${num}.vdi-alpha").readBytes()
        }

        val depthArray: ByteArray = if (applyTo != null && (applyTo.contains("depth") || applyTo.contains("all"))) {
            File("${specificPrefix}_${num}${additionalParams}.vdi-depth").readBytes()
        } else {
            File("${regularPrefix}_${num}.vdi-depth").readBytes()
        }
        val octArray: ByteArray = if (applyTo != null && (applyTo.contains("grid") || applyTo.contains("all"))) {
            File("${specificPrefix}_${num}${additionalParams}.vdi-grid").readBytes()
        } else {
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

        val plane = FullscreenObject()
        plane.material().textures["diffuse"] = vdiNode.material().textures["OutputViewport"]!!
        scene.addChild(plane)
    }

    override fun inputSetup() {
        setupCameraModeSwitching()

        val volumeDims = BenchmarkSetup(dataset).getVolumeDims()
        val pixelToWorld = (0.0075f * 512f) / volumeDims.x

        val target = volumeDims * pixelToWorld * 0.5f
        target.y *= -1

        val arcballCameraControl = ArcballCameraControl("fixed_rotation", { scene.findObserver()!! }, windowWidth, windowHeight, target)
        inputHandler?.addBehaviour("rotate_camera",
            ClickBehaviour { _, _ ->
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
            VDIRenderingBenchmark("VDI Rendering Benchmark", 1920, 1080, BenchmarkSetup.Dataset.Kingsnake, 20, 0).main()
        }
    }
}

