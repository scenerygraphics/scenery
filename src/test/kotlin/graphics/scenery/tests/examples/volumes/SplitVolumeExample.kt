package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.attribute.material.Material
import graphics.scenery.utils.extensions.positionVolumeSlices
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Vector3f
import java.nio.file.Paths

/**
 * Volume rendering on a volume file partitioned into sliced buffers before loading into the scene.
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */
class SplitVolumeExample: SceneryBase("Split volume data", 1280, 720) {
    var hmd: TrackedStereoGlasses? = null

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.5f, 5.0f)
            }
            perspectiveCamera(50.0f, windowWidth, windowHeight)

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

        val s = Icosphere(0.5f, 3)
        s.spatial().position = Vector3f(2.0f, -1.0f, -2.0f)
        s.material().diffuse = Vector3f(0.0f, 0.0f, 0.0f)
        scene.addChild(s)

        val pair = Volume.fromPathRawSplit(Paths.get(getDemoFilesPath() + "/volumes/box-iso/box_200_200_200.raw"), hub = hub, type = UnsignedShortType(), sizeLimit = 20000000)
        val parent = pair.first as RichNode
        val volumeList = pair.second

        parent.positionVolumeSlices(volumeList)

        volumeList.forEachIndexed{ i, volume->
            volume.name = "volume_$i"
            volume.colormap = Colormap.get("viridis")
            volume.transferFunction = TransferFunction.ramp(0.1f, 0.5f)
            volume.origin = Origin.Center
            volume.spatial().scale = Vector3f(20.0f, 20.0f, 20.0f)
        }

        parent.spatial {
            position = Vector3f(0.0f, 0.0f, -3.5f)
            rotation = rotation.rotateXYZ(0.05f, 0.05f, 0.05f)
        }

        scene.addChild(parent)

        val lights = (0 until 3).map {
            PointLight(radius = 15.0f)
        }

        lights.mapIndexed { i, light ->
            light.spatial().position = Vector3f(2.0f * i - 4.0f,  i - 1.0f, 0.0f)
            light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            light.intensity = 0.5f
            scene.addChild(light)
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SplitVolumeExample().main()
        }
    }
}
