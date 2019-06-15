package graphics.scenery.tests.unit.controls

import graphics.scenery.Mesh
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.utils.LazyLogger
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenVRHMDTests {
    private val logger by LazyLogger()
    private fun initialiseAndWait(): OpenVRHMD {
        val hmd = OpenVRHMD()
        while(!hmd.initializedAndWorking()) {
            Thread.sleep(200)
        }

        return hmd
    }

    @Before
    fun checkForOpenVR() {
        val hmd = OpenVRHMD()
        org.junit.Assume.assumeTrue(hmd.initializedAndWorking())
    }

    @Test
    fun testInitialisation() {
        logger.info("Testing OpenVR initialisation ...")
        val hmd = initialiseAndWait()
        hmd.update()
        hmd.close()
    }

    @Test
    fun testGetPosition() {
        logger.info("Testing OpenVR pose query ...")
        val hmd = initialiseAndWait()

        hmd.update()
        val poses = hmd.getPose(TrackedDeviceType.HMD)
        assertTrue(poses.isNotEmpty(), "Tracked poses are expected not to be empty.")
        hmd.close()
    }

    @Test
    fun testGetVulkanInstanceExtensions() {
        logger.info("Testing getting Vulkan instance extensions from OpenVR ...")
        val hmd = initialiseAndWait()

        hmd.update()
        val extensions = hmd.getVulkanInstanceExtensions()
        assertTrue(extensions.isNotEmpty(), "Vulkan instance extensions are expected not to be empty.")
        hmd.close()
    }

    @Test
    fun testGetProjections() {
        logger.info("Testing getting projections from OpenVR ...")
        val hmd = initialiseAndWait()

        val left = hmd.getEyeProjection(0)
        val right = hmd.getEyeProjection(1)

        assertNotNull(left, "Left projection must not be null")
        assertNotNull(right, "Right projection must not be null")

        hmd.close()
    }

    @Test
    fun testLoadModels() {
        logger.info("Testing loading models ...")
        val hmd = initialiseAndWait()
        TrackedDeviceType.values().forEach { type ->
            val mesh = Mesh()
            hmd.loadModelForMesh(type, mesh)

            if(type in listOf(TrackedDeviceType.HMD, TrackedDeviceType.Controller, TrackedDeviceType.Generic, TrackedDeviceType.BaseStation)) {
                assertTrue(mesh.children.isNotEmpty(), "loadModelForMesh should populate mesh with children for $type")
            }
        }

        hmd.close()
    }

    @Test
    fun testGetOrientation() {
        logger.info("Testing getting HMD orientation ...")
        val hmd = initialiseAndWait()

        hmd.update()
        val o = hmd.getOrientation()
        assertFalse(o.isIdentity, "HMD orientation should not be identity.")
    }

    @Test
    fun testGetHeadToEyeTransform() {
        logger.info("Testing querying head-to-eye transforms ...")
        val hmd = initialiseAndWait()

        val left = hmd.getHeadToEyeTransform(0)
        val right = hmd.getHeadToEyeTransform(1)
        assertNotNull(left)
        assertNotNull(right)
    }
}
