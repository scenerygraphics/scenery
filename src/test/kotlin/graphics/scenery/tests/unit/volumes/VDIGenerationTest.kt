package graphics.scenery.tests.unit.volumes

import graphics.scenery.*
import graphics.scenery.volumes.VolumeManager
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.vdi.*
import org.joml.Vector3f
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import graphics.scenery.backends.vulkan.VulkanRenderer
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch

/**
 * Unit test for VDI generation. Checks that the color and depth buffers fetched from the GPU
 * contain non-zero data, indicating that the VDI generation worked.
 */
class VDIGenerationTest(
    wWidth: Int = 512,
    wHeight: Int = 512,
    val maxSupersegments: Int = 20,
    private val bufferReadyLatch: CountDownLatch = CountDownLatch(1)
) : SceneryBase("VDI Generation Unit Test", wWidth, wHeight) {

    @Volatile var colorBufferHasData = false
    @Volatile var depthBufferHasData = false

    override fun init() {
        // ...existing code up to volume setup...

        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.5f, 5.0f)
            }
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            scene.addChild(this)
        }

        val volume = Volume.fromPathRaw(Paths.get(getDemoFilesPath() + "/volumes/box-iso/"), hub)
        volume.name = "volume"
        volume.colormap = Colormap.get("viridis")
        volume.spatial {
            position = Vector3f(0.0f, 0.0f, -3.5f)
            rotation = rotation.rotateXYZ(0.05f, 0.05f, 0.05f)
            scale = Vector3f(20.0f, 20.0f, 20.0f)
        }
        volume.transferFunction = TransferFunction.ramp(0.1f, 0.5f)
        scene.addChild(volume)

        val vdiVolumeManager = VDIVolumeManager(hub, windowWidth, windowHeight, maxSupersegments, scene).createVDIVolumeManager(false)
        volume.volumeManager = vdiVolumeManager
        vdiVolumeManager.add(volume)
        volume.volumeManager.shaderProperties["doGeneration"] = true
        hub.add(vdiVolumeManager)

        // Start buffer checking in a background thread
        Thread {
            // Wait for renderer to be ready
            while (renderer?.firstImageReady == false) {
                Thread.sleep(50)
            }

            val vdiColor = vdiVolumeManager.material().textures[VDIVolumeManager.colorTextureName]!!
            val colorCnt = AtomicInteger(0)
            (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(vdiColor to colorCnt)

            val vdiDepth = vdiVolumeManager.material().textures[VDIVolumeManager.depthTextureName]!!
            val depthCnt = AtomicInteger(0)
            (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(vdiDepth to depthCnt)

            val prevColor = colorCnt.get()
            val prevDepth = depthCnt.get()

            // Wait for new buffers to be available
            while (colorCnt.get() == prevColor || depthCnt.get() == prevDepth) {
                Thread.sleep(5)
            }

            val vdiColorBuffer = vdiColor.contents
            val vdiDepthBuffer = vdiDepth.contents

            colorBufferHasData = bufferHasNonZero(vdiColorBuffer)
            depthBufferHasData = bufferHasNonZero(vdiDepthBuffer)

            bufferReadyLatch.countDown()
        }.start()
    }

    private fun bufferHasNonZero(buffer: ByteBuffer?): Boolean {
        if (buffer == null) return false
        buffer.rewind()
        while (buffer.hasRemaining()) {
            if (buffer.get().toInt() != 0) {
                return true
            }
        }
        return false
    }

    companion object {
        // No main method needed for unit test
    }
}

// JUnit 4 test function that launches the application and waits for buffer check
class VDIGenerationUnitTest {
    @Test
    fun testVDIBuffersContainData() {
        val latch = CountDownLatch(1)
        val app = VDIGenerationTest(bufferReadyLatch = latch)
        Thread { app.main() }.start()
        // Wait for buffer check to complete (timeout after 30s)
        latch.await()
        assertTrue("VDI color buffer contains only zeros", app.colorBufferHasData)
        assertTrue("VDI depth buffer contains only zeros", app.depthBufferHasData)
    }
}
