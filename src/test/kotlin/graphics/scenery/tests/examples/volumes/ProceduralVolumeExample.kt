package graphics.scenery.tests.examples.volumes

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.attribute.material.Material
import graphics.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.utils.RingBuffer
import graphics.scenery.utils.extensions.plus
import graphics.scenery.volumes.BufferedVolume
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.lwjgl.system.MemoryUtil.memAlloc
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread
import kotlin.math.PI

/**
 * Example that renders procedurally generated volumes.
 * [bitsPerVoxel] can be set to 8 or 16, to generate Byte or UnsignedShort volumes.
 *
 * Press T when running to toggle the animation.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ProceduralVolumeExample: SceneryBase("Procedural Volume Rendering Example", 1280, 720) {
    private val bitsPerVoxel = 8
    private val volumeSize = 128
    private lateinit var volume: BufferedVolume

    /**
     * Example initialiser, creates renderer, sets up the scene and procedural
     * volume buffer. Starts a thread for animation.
     */
    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial().position = Vector3f(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        val shell = Box(Vector3f(15.0f), insideNormals = true)
        shell.material {
            cullingMode = Material.CullingMode.None
            diffuse = Vector3f(0.1f, 0.1f, 0.1f)
            specular = Vector3f(0.0f)
            ambient = Vector3f(0.0f)
        }
        scene.addChild(shell)

        volume = if(bitsPerVoxel == 8) {
            Volume.fromBuffer(emptyList(), volumeSize, volumeSize, volumeSize, UnsignedByteType(), hub)
        } else {
            Volume.fromBuffer(emptyList(), volumeSize, volumeSize, volumeSize, UnsignedShortType(), hub)
        }

        volume.name = "volume"
        volume.colormap = Colormap.get("jet")
        volume.pixelToWorldRatio = 0.02f
        volume.transferFunction = TransferFunction.ramp(0.01f, 0.04f, 0.1f)
        volume.spatial().rotation.rotateY(PI.toFloat()/4.0f)

        volume.metadata["animating"] = true
        scene.addChild(volume)

        val lights = (0 until 3).map {
            PointLight(radius = 15.0f)
        }

        lights.mapIndexed { i, light ->
            light.spatial().position = Vector3f(2.0f * i - 4.0f,  i - 1.0f, 0.0f)
            light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            light.intensity = 0.2f
            scene.addChild(light)
        }

        thread {
            val volumeBuffer = RingBuffer(2, default = { memAlloc((volumeSize*volumeSize*volumeSize*bitsPerVoxel/8)) })

            val seed = Random.randomFromRange(0.0f, 133333337.0f).toLong()
            var shift = Vector3f(0.0f)
            var shiftDelta = Random.random3DVectorFromRange(-0.5f, 0.5f)

            var count = 0
            while(running && !shouldClose) {
                if(volume.metadata["animating"] == true) {
                    val currentBuffer = volumeBuffer.get()

                    Volume.generateProceduralVolume(
                        volumeSize.toLong(),
                        0.35f,
                        seed = seed,
                        intoBuffer = currentBuffer,
                        shift = shift,
                        use16bit = bitsPerVoxel > 8
                    )

                    volume.addTimepoint("t-${count}", currentBuffer)
                    volume.goToLastTimepoint()

                    volume.purgeFirst(10, 10)

                    shift += shiftDelta
                    count++

                    // 5% chance of changing direction
                    if(kotlin.random.Random.nextFloat() > 0.95f) {
                        shiftDelta += Random.random3DVectorFromRange(-0.5f, 0.5f)
                    }
                }

                Thread.sleep(kotlin.random.Random.nextLong(5L, 25L))
            }
        }
    }

    /**
     * Input setup override, sets up camera mode switching, where pressing C
     * can toggle between FPS and Arcball camera control. Also adds animation
     * toggling when pressing T. Additionally, demonstrates how the
     * rotateDegrees of ArcballCameraControl can be used to rotate the camera
     * by a fixed amount (here, 10 degrees yaw) about the scene origin, by
     * pressing R.
     */
    override fun inputSetup() {
        setupCameraModeSwitching()

        inputHandler?.addBehaviour("toggle_animation",
                                   ClickBehaviour { _, _ ->
                                       volume.metadata["animating"] = !(volume.metadata["animating"] as Boolean)
                                   })
        inputHandler?.addKeyBinding("toggle_animation", "T")

        val arcballCameraControl = ArcballCameraControl("fixed_rotation", { scene.findObserver()!! }, windowWidth, windowHeight, scene.findObserver()!!.target)
        inputHandler?.addBehaviour("rotate_camera",
            ClickBehaviour { _, _ ->
                arcballCameraControl.rotateDegrees(10f, 0f)
            })
        inputHandler?.addKeyBinding("rotate_camera", "R")

    }

    /**
     * Companion object for providing a main method.
     */
    companion object {
        /**
         * Main method, instantiates the example.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            ProceduralVolumeExample().main()
        }
    }
}
