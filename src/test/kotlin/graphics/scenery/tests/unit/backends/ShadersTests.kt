package graphics.scenery.tests.unit.backends

import graphics.scenery.backends.*
import graphics.scenery.spirvcrossj.Loader
import graphics.scenery.spirvcrossj.libspirvcrossj
import graphics.scenery.utils.LazyLogger
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Tests for the [Shaders] class.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ShadersTests {

    companion object {
        private val logger by LazyLogger()

        @BeforeClass @JvmStatic
        fun loadNatives() {
            logger.info("Loading spirvcrossj natives")
            Loader.loadNatives()
            logger.info("Initializing spirvcrossj")
            libspirvcrossj.initializeProcess()
        }

        @AfterClass @JvmStatic
        fun teardownNatives() {
            logger.info("Finalizing spirvcrossj")
            libspirvcrossj.finalizeProcess()
        }
    }

    @Test
    fun testShadersNotFound() {
        logger.info("Testing behaviours for missing shaders ...")
        val s = Shaders.ShadersFromFiles(arrayOf("notFound.vert", "notFound.frag"), Renderer::class.java)

        assertFailsWith(ShaderNotFoundException::class) {
            s.get(Shaders.ShaderTarget.Vulkan, ShaderType.TessellationEvaluationShader)
        }

        assertFailsWith(ShaderCompilationException::class) {
            s.get(Shaders.ShaderTarget.Vulkan, ShaderType.VertexShader)
        }

        val c = Shaders.ShadersFromClassName(ShadersTests::class.java, listOf(ShaderType.VertexShader, ShaderType.FragmentShader))
        assertFailsWith(ShaderNotFoundException::class) {
            c.get(Shaders.ShaderTarget.Vulkan, ShaderType.ComputeShader)
        }

        assertFailsWith(ShaderCompilationException::class) {
            c.get(Shaders.ShaderTarget.Vulkan, ShaderType.FragmentShader)
        }
    }

    @Test
    fun testShaderCompilation() {
        logger.info("Testing shader compilation ...")

        val s = Shaders.ShadersFromFiles(arrayOf("Default.vert", "Default.frag"))
        assertNotNull(s.get(Shaders.ShaderTarget.Vulkan, ShaderType.VertexShader))
        assertNotNull(s.get(Shaders.ShaderTarget.Vulkan, ShaderType.FragmentShader))

        assertNotNull(s.get(Shaders.ShaderTarget.Vulkan, ShaderType.VertexShader).spirv)
        assertNotNull(s.get(Shaders.ShaderTarget.Vulkan, ShaderType.VertexShader).code)
        assertNotNull(s.get(Shaders.ShaderTarget.Vulkan, ShaderType.FragmentShader).spirv)
        assertNotNull(s.get(Shaders.ShaderTarget.Vulkan, ShaderType.FragmentShader).code)

        assertNotNull(s.get(Shaders.ShaderTarget.OpenGL, ShaderType.VertexShader))
        assertNotNull(s.get(Shaders.ShaderTarget.OpenGL, ShaderType.FragmentShader))

        assertNotNull(s.get(Shaders.ShaderTarget.OpenGL, ShaderType.VertexShader).spirv)
        assertNotNull(s.get(Shaders.ShaderTarget.OpenGL, ShaderType.VertexShader).code)
        assertNotNull(s.get(Shaders.ShaderTarget.OpenGL, ShaderType.FragmentShader).spirv)
        assertNotNull(s.get(Shaders.ShaderTarget.OpenGL, ShaderType.FragmentShader).code)
    }
}
