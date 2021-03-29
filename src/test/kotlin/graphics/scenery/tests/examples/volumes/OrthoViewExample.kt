package graphics.scenery.tests.examples.volumes

import bdv.util.AxisOrder
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.behaviours.MouseDragSphere
import graphics.scenery.effectors.LineRestrictionEffector
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.times
import ij.IJ
import ij.ImagePlus
import net.imglib2.img.Img
import net.imglib2.img.display.imagej.ImageJFunctions
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Vector3f
import tpietzsch.example2.VolumeViewerOptions
import graphics.scenery.volumes.*

/**
 * Volume Ortho View Example using the "BDV Rendering Example loading a RAII"
 *
 * @author  Jan Tiemann <j.tiemann@hzdr.de>
 */
class OrthoViewExample : SceneryBase("Ortho View example", 1280, 720) {
    lateinit var volume: Volume

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            position = Vector3f(0.0f, 2.0f, 5.0f)
            scene.addChild(this)
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

        val imp: ImagePlus = IJ.openImage("https://imagej.nih.gov/ij/images/t1-head.zip")
        val img: Img<UnsignedShortType> = ImageJFunctions.wrapShort(imp)

        volume = Volume.fromRAI(img, UnsignedShortType(), AxisOrder.DEFAULT, "T1 head", hub, VolumeViewerOptions())
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
        scene.addChild(volume)

        val bGrid = BoundingGrid()
        bGrid.node = volume
        volume.addChild(bGrid)

        createOrthoView(volume)
    }

    fun createOrthoView(volume: Volume) {
        this.volume.cropInsteadOfSlice = false
        val sliceXZ = SlicingPlane()
        val sliceXY = SlicingPlane()
        val sliceYZ = SlicingPlane()

        sliceXY.rotation = sliceXY.rotation.rotateX((Math.PI / 2).toFloat())
        sliceYZ.rotation = sliceYZ.rotation.rotateZ((Math.PI / 2).toFloat())

        sliceXZ.addTargetVolume(this.volume)
        sliceXY.addTargetVolume(this.volume)
        sliceYZ.addTargetVolume(this.volume)

        this.volume.boundingBox?.let { boundingBox ->

            val center = (boundingBox.max - boundingBox.min) * 0.5f

            val planeXZ = Box(Vector3f(boundingBox.max.x, 1f, boundingBox.max.z))
            val planeXY = Box(Vector3f(boundingBox.max.x, boundingBox.max.y, 1f))
            val planeYZ = Box(Vector3f(1f, boundingBox.max.y, boundingBox.max.z))

            planeXZ.position = center
            planeXY.position = center
            planeYZ.position = center

            // make transparent
            planeXZ.material.blending.setOverlayBlending()
            planeXZ.material.blending.transparent = true
            planeXZ.material.blending.opacity = 0f
            //planeXZ.material.wireframe = true
            planeXY.material = planeXZ.material
            planeYZ.material = planeXZ.material

            planeXZ.addChild(sliceXZ)
            planeXY.addChild(sliceXY)
            planeYZ.addChild(sliceYZ)

            this.volume.addChild(planeXZ)
            this.volume.addChild(planeXY)
            this.volume.addChild(planeYZ)

            val yTop = Node()
            yTop.position = Vector3f(center.x, boundingBox.max.y, center.z)
            this.volume.addChild(yTop)

            val yBottom = Node()
            yBottom.position = Vector3f(center.x, boundingBox.min.y, center.z)
            this.volume.addChild(yBottom)

            LineRestrictionEffector(planeXZ, { yTop.position }, { yBottom.position })

            val zTop = Node()
            zTop.position = Vector3f(center.x, center.y, boundingBox.max.z)
            this.volume.addChild(/*z*/zTop)

            val zBottom = Node()
            zBottom.position = Vector3f(center.x, center.y, boundingBox.min.z)
            this.volume.addChild(zBottom)

            LineRestrictionEffector(planeXY, { zTop.position }, { zBottom.position })

            val xTop = Node()
            xTop.position = Vector3f(boundingBox.max.x, center.y, center.z)
            this.volume.addChild(xTop)

            val xBottom = Node()
            xBottom.position = Vector3f(boundingBox.min.x, center.y, center.z)
            this.volume.addChild(xBottom)

            LineRestrictionEffector(planeYZ, { xTop.position }, { xBottom.position })

        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()

        inputHandler?.addBehaviour(
            "sphereDragObject", MouseDragSphere(
                "sphereDragObject",
                { scene.findObserver() },
                debugRaycast = false,
                ignoredObjects = listOf<Class<*>>(BoundingGrid::class.java, VolumeManager::class.java, Volume::class.java)
            )
        )
        inputHandler?.addKeyBinding("sphereDragObject", "1")

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            OrthoViewExample().main()
        }
    }
}
