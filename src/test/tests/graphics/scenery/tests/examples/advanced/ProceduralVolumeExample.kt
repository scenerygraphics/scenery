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
import org.scijava.ui.behaviour.ClickBehaviour
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Example that renders procedurally generated volumes.
 * [bitsPerVoxel] can be set to 8 or 16, to generate Byte or UnsignedShort volumes.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ProceduralVolumeExample: SceneryBase("Volume Rendering example", 1280, 720) {
    val bitsPerVoxel = 8

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

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
        volume.position = GLVector(0.0f, 0.0f, 0.0f)
        volume.colormap = "viridis"
        volume.scale = GLVector(10.0f, 10.0f, 10.0f)
        with(volume.transferFunction) {
            addControlPoint(0.0f, 0.0f)
            addControlPoint(0.2f, 0.0f)
            addControlPoint(0.4f, 0.5f)
            addControlPoint(0.8f, 0.5f)
            addControlPoint(1.0f, 0.0f)
        }

        volume.metadata["animating"] = true
        scene.addChild(volume)

        val lights = (0 until 3).map {
            PointLight(radius = 15.0f)
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i - 4.0f,  i - 1.0f, 0.0f)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 0.5f
            scene.addChild(light)
        }

        thread {
            val volumeSize = 128L
            val volumeBuffer = RingBuffer<ByteBuffer>(2) { memAlloc((volumeSize*volumeSize*volumeSize*bitsPerVoxel/8).toInt()) }

            val seed = Random.randomFromRange(0.0f, 133333337.0f).toLong()
            var shift = GLVector.getNullVector(3)
            val shiftDelta = Random.randomVectorFromRange(3, -1.5f, 1.5f)

            val dataType = if(bitsPerVoxel == 8) {
                NativeTypeEnum.UnsignedByte
            } else {
                NativeTypeEnum.UnsignedShort
            }

            while(running) {
                if(volume.metadata["animating"] == true) {
                    val currentBuffer = volumeBuffer.get()

                    Volume.generateProceduralVolume(volumeSize, 0.35f, seed = seed,
                        intoBuffer = currentBuffer, shift = shift, use16bit = bitsPerVoxel > 8)

                    volume.readFromBuffer(
                        "procedural-cloud-${shift.hashCode()}", currentBuffer,
                        volumeSize, volumeSize, volumeSize, 1.0f, 1.0f, 1.0f,
                        dataType = dataType, bytesPerVoxel = bitsPerVoxel / 8)

                    shift = shift + shiftDelta
                }

                Thread.sleep(200)
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()

        val toggleRenderingMode = object : ClickBehaviour {
            var modes = hashMapOf(0 to "Local MIP", 1 to "MIP", 2 to "Alpha Compositing")
            var currentMode = (scene.find("volume") as? Volume)?.renderingMethod ?: 0

            override fun click(x: Int, y: Int) {
                currentMode = (currentMode + 1) % modes.size

                (scene.find("volume") as? Volume)?.renderingMethod = currentMode
                logger.info("Switched volume rendering mode to ${modes[currentMode]} (${(scene.find("volume") as? Volume)?.renderingMethod})")
            }
        }

        inputHandler?.addBehaviour("toggle_rendering_mode", toggleRenderingMode)
        inputHandler?.addKeyBinding("toggle_rendering_mode", "M")
    }

    @Test override fun main() {
        super.main()
    }
}
