package graphics.scenery.tests.examples.volumes

import bdv.util.AxisOrder
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.SlicingPlane
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import ij.IJ
import ij.ImagePlus
import net.imglib2.img.Img
import net.imglib2.img.display.imagej.ImageJFunctions
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Quaternionf
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour
import tpietzsch.example2.VolumeViewerOptions
import kotlin.concurrent.thread
import graphics.scenery.numerics.Random

/**
 * Volume Slicing Example using the "BDV Rendering Example loading a RAII"
 *
 * Press "R" to add a moving slicing plane and "T" to remove one.
 *
 * @author  Jan Tiemann <j.tiemann@hzdr.de>
 */
class SlicingExample : SceneryBase("Volume Slicing example", 1280, 720) {
    lateinit var volume: Volume
    lateinit var volume2: Volume
    var slicingPlanes = mapOf<SlicingPlane,Animator>()
    val additionalVolumes = true
    var additionalAnimators = emptyList<Animator>()


    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            position = Vector3f(0.0f, 0.0f, 5.0f)
            scene.addChild(this)
        }

        val imp: ImagePlus = IJ.openImage("https://imagej.nih.gov/ij/images/t1-head.zip")
        val img: Img<UnsignedShortType> = ImageJFunctions.wrapShort(imp)

        volume = Volume.fromRAI(img, UnsignedShortType(), AxisOrder.DEFAULT, "T1 head", hub, VolumeViewerOptions())
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
        scene.addChild(volume)

        if (additionalVolumes) {
            fun addAdditionalVolume(base: Vector3f) {
                val vol2Pivot = Node()
                scene.addChild(vol2Pivot)
                vol2Pivot.position = vol2Pivot.position + base
                vol2Pivot.scale = Vector3f(0.5f)

                volume2 =
                    Volume.fromRAI(img, UnsignedShortType(), AxisOrder.DEFAULT, "T1 head", hub, VolumeViewerOptions())
                volume2.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
                vol2Pivot.addChild(volume2)

                val slicingPlane2 = createSlicingPlane()
                scene.removeChild(slicingPlane2)
                vol2Pivot.addChild(slicingPlane2)
                slicingPlane2.addTargetVolume(volume2)
                val animator2 = Animator(slicingPlane2)
                additionalAnimators = additionalAnimators + animator2
            }
            addAdditionalVolume(Vector3f(2f,2f,-2f))
            addAdditionalVolume(Vector3f(-2f,2f,-2f))
            addAdditionalVolume(Vector3f(2f,-2f,-2f))
            addAdditionalVolume(Vector3f(-2f,-2f,-2f))
        }


        val shell = Box(Vector3f(10.0f, 10.0f, 10.0f), insideNormals = true)
        shell.material.cullingMode = Material.CullingMode.None
        shell.material.diffuse = Vector3f(0.2f, 0.2f, 0.2f)
        shell.material.specular = Vector3f(0.0f)
        shell.material.ambient = Vector3f(0.0f)
        scene.addChild(shell)

        Light.createLightTetrahedron<PointLight>(spread = 4.0f, radius = 15.0f, intensity = 0.5f)
            .forEach { scene.addChild(it) }

        val origin = Box(Vector3f(0.1f, 0.1f, 0.1f))
        origin.material.diffuse = Vector3f(0.8f, 0.0f, 0.0f)
        scene.addChild(origin)

        addAnimatedSlicingPlane()

        thread {
            while (running) {
                slicingPlanes.entries.forEach { it.value.animate() }
                additionalAnimators.forEach { it.animate() }
                Thread.sleep(20)
            }
        }
    }

    class Animator(val node: Node) {
        private var moveDir = Random.random3DVectorFromRange(-1.0f, 1.0f) - node.position
        private var rotationStart = Quaternionf(node.rotation)
        private var rotationTarget = Random.randomQuaternion()
        private var startTime = System.currentTimeMillis()

        fun animate() {
            val relDelta = (System.currentTimeMillis() - startTime) / 5000f

            val scaledMov = moveDir * (20f / 5000f)
            node.position = node.position + scaledMov

            rotationStart.nlerp(rotationTarget, relDelta, node.rotation)

            node.needsUpdate = true

            if (startTime + 5000 < System.currentTimeMillis()) {
                moveDir = Random.random3DVectorFromRange(-1.0f, 1.0f) - node.position
                rotationStart = Quaternionf(node.rotation)
                rotationTarget = Random.randomQuaternion()
                startTime = System.currentTimeMillis()
            }
        }
    }

    private fun addAnimatedSlicingPlane(){

        val slicingPlane = createSlicingPlane()
        slicingPlane.addTargetVolume(volume)
        val animator = Animator(slicingPlane)

        slicingPlanes = slicingPlanes + (slicingPlane to animator)
    }

    private fun removeAnimatedSlicingPlane(){
        if (slicingPlanes.isEmpty())
            return
        val (plane,_) = slicingPlanes.entries.first()
        scene.removeChild(plane)
        plane.removeTargetVolume(volume)
        slicingPlanes = slicingPlanes.toMutableMap().let { it.remove(plane); it}
    }

    private fun createSlicingPlane(): SlicingPlane {

        val slicingPlaneFunctionality = SlicingPlane()
        scene.addChild(slicingPlaneFunctionality)
        //scene.addChild(slicingPlaneFunctionality)
        slicingPlaneFunctionality.position -= Vector3f(0.5f)

        val slicingPlaneVisual: Node
        slicingPlaneVisual = Box(Vector3f(1f, 0.01f, 1f))
        slicingPlaneVisual.material.diffuse = Vector3f(0.0f, 0.8f, 0.0f)
        slicingPlaneFunctionality.addChild(slicingPlaneVisual)

        val nose = Box(Vector3f(0.1f, 0.1f, 0.1f))
        nose.material.diffuse = Vector3f(0.8f, 0.0f, 0.0f)
        nose.position = Vector3f(0f, 0.05f, 0f)
        slicingPlaneVisual.addChild(nose)

        return slicingPlaneFunctionality
    }

    override fun inputSetup() {
        setupCameraModeSwitching()

        inputHandler?.addBehaviour("addPlane", ClickBehaviour { _, _ -> addAnimatedSlicingPlane() })
        inputHandler?.addKeyBinding("addPlane", "R")
        inputHandler?.addBehaviour("removePlane", ClickBehaviour { _, _ -> removeAnimatedSlicingPlane() })
        inputHandler?.addKeyBinding("removePlane", "T")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SlicingExample().main()
        }
    }
}
