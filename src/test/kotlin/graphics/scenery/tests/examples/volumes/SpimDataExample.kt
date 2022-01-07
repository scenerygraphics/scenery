package graphics.scenery.tests.examples.volumes

import bdv.spimdata.XmlIoSpimDataMinimal
import bdv.util.AxisOrder
import graphics.scenery.*
import org.joml.Vector3f
import graphics.scenery.backends.Renderer
import graphics.scenery.attribute.material.Material
import graphics.scenery.utils.extensions.timesAssign
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume

import tpietzsch.example2.VolumeViewerOptions

/**
 */
class SpimDataExample: SceneryBase("SpimData Rendering example", 1280, 720,false) {
    lateinit var volume: Volume

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))


        val file =  "C:\\Users\\JanCasus\\volumes\\drosophila.xml"
        val options = VolumeViewerOptions().maxCacheSizeInMB(512)
        volume = Volume.fromSpimData(XmlIoSpimDataMinimal().load(file), hub, options)
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
        volume.spatial {
            position.y += 3f
            scale *= 3f
            needsUpdate = true
        }
        scene.addChild(volume)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            scene.addChild(this)
        }

        val shell = Box(Vector3f(10.0f, 10.0f, 10.0f), insideNormals = true)
        shell.material {
            cullingMode = Material.CullingMode.None
            diffuse = Vector3f(0.2f, 0.2f, 0.2f)
            specular = Vector3f(0.0f)
            ambient = Vector3f(0.0f)
        }
        scene.addChild(shell)

        Light.createLightTetrahedron<PointLight>(spread = 4.0f, radius = 15.0f, intensity = 0.5f)
            .forEach { scene.addChild(it) }

        val origin = Box(Vector3f(0.1f, 0.1f, 0.1f))
        origin.material().diffuse = Vector3f(0.8f, 0.0f, 0.0f)
        scene.addChild(origin)
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpimDataExample().main()
        }
    }
}
