package graphics.scenery.tests.examples.advanced

import graphics.scenery.*
import org.joml.Vector3f
import graphics.scenery.backends.Renderer
import graphics.scenery.attribute.material.Material
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.primitives.Plane
import graphics.scenery.textures.Texture
import graphics.scenery.textures.UpdatableTexture
import graphics.scenery.utils.Image
import graphics.scenery.utils.RingBuffer
import graphics.scenery.volumes.Volume
import net.imglib2.type.numeric.integer.UnsignedByteType
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.io.File
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Example loading a large texture asynchronously
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class AsyncTextureExample: SceneryBase("Async Texture example", 512, 512) {
    lateinit var volume: Volume
    private val size = Vector3i(windowWidth,windowHeight,512)
    var previous = 0L
    var frametimes = ArrayList<Int>()

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        renderer?.postRenderLambdas?.add {
            val now = System.nanoTime()
            val duration = (now - previous).nanoseconds
            previous = now

            frametimes.add(duration.inWholeMilliseconds.toInt())
        }

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            scene.addChild(this)
        }

        val b = Box(Vector3f(0.5f))
        b.material().diffuse = Vector3f(0.5f)
        scene.addChild(b)

        val a = AmbientLight(1.0f)
        scene.addChild(a)

        val p = Plane(Vector3f(1000.0f, 1000.0f, 0.01f))
        p.material().diffuse = Vector3f(0.0f, 1.0f, 0.0f)
        p.material().cullingMode = Material.CullingMode.None
        p.material().depthTest = true
        p.material().depthOp = Material.DepthTest.LessEqual
        p.spatial().position = Vector3f(0.0f, 0.0f, -1000.0f)
        cam.addChild(p)

        Light.createLightTetrahedron<PointLight>(spread = 4.0f, radius = 15.0f, intensity = 0.5f)
            .forEach { scene.addChild(it) }

        val buffer = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)

        val compute = RichNode()
        compute.name = "compute node"
        val computeTexture = Texture.fromImage(
            Image(buffer, windowWidth, windowHeight, 1),
            usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        compute.setMaterial(ShaderMaterial.fromFiles(this::class.java, "CheckDataForAsyncExample.comp")) {
            textures["OutputViewport"] = computeTexture
        }
        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowWidth, windowHeight, 1),
            invocationType = InvocationType.Permanent
        )

        scene.addChild(compute)

        val plane = FullscreenObject()
        plane.material().textures["diffuse"] = compute.material().textures["OutputViewport"]!!
        scene.addChild(plane)

        // We create textures and backing buffers separately,
        // as UpdatableTexture are supposed to have contents = null at the moment
        val textures = RingBuffer(2, cleanup = null, default = {
            UpdatableTexture(
                size,
                channels = 1,
                type = UnsignedByteType(),
                usageType = hashSetOf(Texture.UsageType.Texture, Texture.UsageType.AsyncLoad, Texture.UsageType.LoadStoreImage),
                contents = null
            )
        })

        val backing = RingBuffer(2, cleanup = null, default = {
            val mem = MemoryUtil.memAlloc(size.x*size.y*size.z)
            while (mem.remaining() > 0) {
                mem.put((it * 63).toByte())
            }
            mem.flip()
            mem
        })

        thread(isDaemon = true) {
            Thread.sleep(5000)

            while(running) {
                val index = textures.currentReadPosition
                val texture = textures.get()

                logger.info("Fiddling Permits available: ${texture.mutex.availablePermits()}")
                logger.info("Upload Permits available: ${texture.gpuMutex.availablePermits()}")
                Thread.sleep(50)

                // We add a TextureUpdate that covers the whole texture,
                // using one of the backing RingBuffers.
                val update = UpdatableTexture.TextureUpdate(
                    UpdatableTexture.TextureExtents(0, 0, 0, size.x, size.y, size.z),
                    backing.get()
                )
                texture.addUpdate(update)

                // Reassigning the texture here, together with its one update
                compute.material().textures["humongous"] = texture

                val waitTime = measureTimeMillis {
                    // Here, we wait until the texture is marked as available on the GPU
                    while(!texture.availableOnGPU()) {
                        logger.info("Texture $index not available yet, uploaded=${texture.uploaded.get()}/permits=${texture.gpuMutex.availablePermits()}")
                        Thread.sleep(10)
                    }
                }

                logger.info("Texture $index is available now, waited $waitTime ms")

                // After the texture is available, we proceed to the next texture
                // in the RingBuffer, and reset the current texture's uploaded
                // AtomicInteger to 0
                texture.uploaded.set(0)

                Thread.sleep(500)
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    /**
     * Companion object for providing a main method.
     */
    companion object {
        /**
         * The main entry point. Executes this example application when it is called.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            AsyncTextureExample().main()
        }
    }
}
