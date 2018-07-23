package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import coremem.enums.NativeTypeEnum
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.utils.RingBuffer
import graphics.scenery.volumes.Volume
import org.junit.Test
import org.lwjgl.system.MemoryUtil.memAlloc
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Example that renders procedurally generated volumes.
 * [bitsPerVoxel] can be set to 8 or 16, to generate Byte or UnsignedShort volumes.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ProceduralVolumeExample: SceneryBase("Volume Rendering example", 1280, 720) {
    val bitsPerVoxel = 16

    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.5f, 5.0f)
            perspectiveCamera(50.0f, 1.0f*windowWidth, 1.0f*windowHeight)
            active = true

            scene.addChild(this)
        }

        val shell = Box(GLVector(10.0f, 10.0f, 10.0f), insideNormals = true)
        shell.material.cullingMode = Material.CullingMode.None
        shell.material.diffuse = GLVector(0.2f, 0.2f, 0.2f)
        shell.material.specular = GLVector.getNullVector(3)
        shell.material.ambient = GLVector.getNullVector(3)
        shell.position = GLVector(0.0f, 4.0f, 0.0f)
        scene.addChild(shell)

        val volume = Volume()
        volume.name = "volume"
        volume.colormap = "plasma"
//        volume.trangemin = 0.0f
//        volume.trangemax = 255.0f
        scene.addChild(volume)

        val lights = (0 until 3).map {
            PointLight(radius = 15.0f)
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i - 4.0f,  i - 1.0f, 0.0f)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 50.0f
            scene.addChild(light)
        }

        thread {
            while(!scene.initialized) { Thread.sleep(200) }

            val volumeSize = 64L
            val volumeBuffer = RingBuffer<ByteBuffer>(2) { memAlloc((volumeSize*volumeSize*volumeSize*bitsPerVoxel/8).toInt()) }

            val seed = Random.randomFromRange(0.0f, 133333337.0f).toLong()
            var shift = GLVector.getNullVector(3)
            val shiftDelta = Random.randomVectorFromRange(3, -0.5f, 0.5f)

            val dataType = if(bitsPerVoxel == 8) {
                NativeTypeEnum.UnsignedByte
            } else {
                NativeTypeEnum.UnsignedShort
            }

            while(true) {
                val currentBuffer = volumeBuffer.get()

                Volume.generateProceduralVolume(volumeSize, 0.95f, seed = seed,
                    intoBuffer = currentBuffer, shift = shift, use16bit = bitsPerVoxel > 8)

                volume.readFromBuffer(
                    "procedural-cloud-${shift.hashCode()}", currentBuffer,
                    volumeSize, volumeSize, volumeSize, 1.0f, 1.0f, 1.0f,
                    dataType = dataType, bytesPerVoxel = bitsPerVoxel/8)

                shift = shift + shiftDelta

                Thread.sleep(200)
            }
        }

        thread {
            while(true) {
                volume.rotation = volume.rotation.rotateByAngleY(0.005f)

                Thread.sleep(15)
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    @Test override fun main() {
        super.main()
    }
}
