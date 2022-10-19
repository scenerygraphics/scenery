package graphics.scenery.tests.examples.advanced

import graphics.scenery.*
import org.joml.Vector3f
import graphics.scenery.backends.Renderer
import graphics.scenery.attribute.material.Material
import graphics.scenery.primitives.Plane
import graphics.scenery.textures.Texture
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import kotlin.concurrent.thread

/**
 * Example loading a large texture asynchronously
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class AsyncTextureExample: SceneryBase("Async Texture example", 1280, 720) {
    lateinit var volume: Volume

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            scene.addChild(this)
        }

        val volume = Volume.fromXML(getDemoFilesPath() + "/volumes/t1-head.xml", hub)
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
        scene.addChild(volume)

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

        thread {
            Thread.sleep(5000)
            logger.info("Allocating")
            val humongous = MemoryUtil.memAlloc(1024*1024*1024)
            val texture = Texture(
                Vector3i(1024, 1024, 1024),
                channels = 1,
                contents = humongous,
                mipmap = false,
                usageType = hashSetOf(Texture.UsageType.Texture, Texture.UsageType.AsyncLoad)
            )

            logger.info("Assigning")
            p.material().textures["HumongousTexture"] = texture

            while(true) {
                logger.info("Permits available: ${texture.mutex.availablePermits()}")
                Thread.sleep(50)
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            AsyncTextureExample().main()
        }
    }
}
