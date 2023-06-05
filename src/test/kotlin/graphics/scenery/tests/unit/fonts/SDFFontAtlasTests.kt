package graphics.scenery.tests.unit.fonts

import graphics.scenery.Hub
import graphics.scenery.compute.OpenCLContext
import graphics.scenery.fonts.SDFFontAtlas
import graphics.scenery.utils.lazyLogger
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import org.lwjgl.system.Platform
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [SDFFontAtlas]
 */
class SDFFontAtlasTests {
    val logger by lazyLogger()

    /**
     * Companion object for checking OpenCL availability.
     */
    companion object {
        val logger by lazyLogger()
        @JvmStatic @BeforeClass
        fun checkOpenCLAvailability() {
            val openCLunavailable = ((System.getenv("GITHUB_ACTIONS").toBoolean() && Platform.get() == Platform.MACOSX)
                    || System.getenv("GITLAB_CI").toBoolean())
            val hasOpenCL: Boolean = if (openCLunavailable) {
                logger.warn("Disabled OpenCL because Github Actions on macOS does not support accelerated OpenCL contexts, or because running on Gitlab CI Docker.")
                false
            } else {
                try {
                    val hub = Hub()
                    OpenCLContext(hub)
                    true
                } catch (e: UnsatisfiedLinkError) {
                    logger.warn("Disabled OpenCL because of UnsatisfiedLinkError ($e)")
                    false
                } catch (e: Exception) {
                    logger.warn("Disabled OpenCL because of Exception ($e)")
                    false
                }
            }

            org.junit.Assume.assumeTrue(hasOpenCL)
        }
    }

    /**
     * Tests generating a SDF font atlas without caching it,
     * and creates a mesh for it.
     */
    @Ignore
    @Test
    fun testAtlasAndMeshCreation() {
        logger.info("Testing SDF atlas and mesh creation ...")
        val hub = Hub()
        val sdf = SDFFontAtlas(hub, "Helvetica", 512, 10, cache = false)
        val atlas = sdf.getAtlas()

        assertTrue(atlas.capacity() > 0 && atlas.remaining() > 0, "SDF atlas should have remaining data")

        for (char in sdf.charset) {
            if(char == 32) continue
            val glyph = sdf.getTexcoordsForGlyph(char.toChar())
            assertFalse(glyph.x() == 0.0f && glyph.y() == 0.0f && glyph.z() == 1.0f && glyph.w() == 1.0f,
                "Glyph should not return default texture coordinates, but returned $glyph.")
        }

        val mesh = sdf.createMeshForString("hello world")
        assertTrue(mesh.geometry().vertices.remaining() > 0)
        assertTrue(mesh.geometry().normals.remaining() > 0)
        assertTrue(mesh.geometry().texcoords.remaining() > 0)
    }
}
