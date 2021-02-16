package graphics.scenery.tests.examples.volumes

import bdv.util.AxisOrder
import graphics.scenery.*
import org.joml.Vector3f
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import ij.IJ
import ij.ImagePlus
import net.imglib2.img.Img
import net.imglib2.img.display.imagej.ImageJFunctions
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Quaternionf
import tpietzsch.example2.VolumeViewerOptions
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * BDV Rendering Example loading a RAII
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Tobias Pietzsch <pietzsch@mpi-cbg.de>
 */
class SlicingExample: SceneryBase("Volume Slicing example", 1280, 720) {
    lateinit var volume: Volume
    lateinit var slicingPlane: Node

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

        slicingPlane = Box(Vector3f(1f,0.01f,1f))
        slicingPlane.material.diffuse = Vector3f(0.0f, 0.8f, 0.0f)
        scene.addChild(slicingPlane)
        volume.volumeManager.setSlicingPlane(slicingPlane)

        val nose = Box(Vector3f(0.1f, 0.1f, 0.1f))
        nose.material.diffuse = Vector3f(0.8f, 0.0f, 0.0f)
        nose.position = Vector3f(0f,0.05f,0f)
        slicingPlane.addChild(nose)

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


        thread {
            while (running) {

                val moveDir = getRandomVector() - slicingPlane.position
                val rotationStart = Quaternionf(slicingPlane.rotation)
                val rotationTarget = Quaternionf().lookAlong(getRandomVector(), getRandomVector())
                val startTime = System.currentTimeMillis()

                while (startTime + 5000 > System.currentTimeMillis()) {
                    val relDelta = (System.currentTimeMillis() - startTime) / 5000f

                    val scaledMov = moveDir * (20f/5000f)
                    slicingPlane.position = slicingPlane.position + scaledMov

                     rotationStart.nlerp(rotationTarget,relDelta,slicingPlane.rotation)

                    slicingPlane.needsUpdate = true

                    Thread.sleep(20)
                }
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SlicingExample().main()
        }

        fun getRandomVector() = Vector3f(Random.nextFloat()*2-1,Random.nextFloat()*2-1,Random.nextFloat()*2-1)
    }
}
