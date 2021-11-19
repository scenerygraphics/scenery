package graphics.scenery.tests.unit.backends

import graphics.scenery.backends.ShaderIntrospection
import graphics.scenery.backends.ShaderType
import graphics.scenery.backends.Shaders
import graphics.scenery.tests.examples.compute.ComputeShaderExample
import graphics.scenery.utils.LazyLogger
import org.junit.Test
import kotlin.experimental.and
import kotlin.test.assertEquals

/**
 * Tests for shader introspection functionality.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class ShaderIntrospectionTests {
    private val logger by LazyLogger()

    /**
     * Tests creation and teardown behaviour.
     */
    @Test
    fun testCreationAndTeardown() {
        val si = ShaderIntrospection(getSimpleSPIRVBytecode())
        si.close()
    }

    @Test
    fun testCompileSimpleFile() {
        val si = ShaderIntrospection(getSimpleSPIRVBytecode())

        val expected =
           """#version 450
layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;

layout(set = 0, binding = 1, std430) buffer SSBO
{
    float a;
} ssbo;

layout(set = 0, binding = 0, std140) uniform UBO
{
    float a;
} ubo;

layout(push_constant, std430) uniform Push
{
    float a;
} registers;

layout(set = 0, binding = 2) uniform sampler2D uTexture;
layout(set = 0, binding = 3) uniform writeonly image2D uImage;
layout(set = 0, binding = 4) uniform texture2D uSeparateTexture;
layout(set = 0, binding = 5) uniform sampler uSampler;

void main()
{
    ssbo.a = ubo.a + registers.a;
}

"""
        assertEquals(expected, si.compile(), "Compiled code from simple SPIRV binary")
        si.close()
    }

    @Test
    fun testUBOsSimpleFile() {
        val si = ShaderIntrospection(getSimpleSPIRVBytecode())
        assertEquals("UBO", si.uniformBuffers().first().name, "UBO name for simple file")
        assertEquals(ShaderIntrospection.UBOSpecType.UniformBuffer, si.uniformBuffers().first().type, "Type of first UBO in simple file")

        val firstMember = si.uniformBuffers().first().members.entries.first()
        assertEquals("a", firstMember.key, "UBO contents, first member, for simple file")
        assertEquals(0, firstMember.value.index, "Index of first UBO member")
        assertEquals(0, firstMember.value.offset, "Offset of first UBO member")
        assertEquals(4, firstMember.value.range, "Range of first UBO member")
        si.close()
    }

    @Test
    fun testSampledImagesSimpleFile() {
        val si = ShaderIntrospection(getSimpleSPIRVBytecode())
        assertEquals(ShaderIntrospection.UBOSpecType.SampledImage2D, si.sampledImages().first().type, "Type of first sampled image in simple file")
        assertEquals("uTexture", si.sampledImages().first().name, "Sample of sampled texture")
        assertEquals(1, si.sampledImages().size, "Number of sampled images from simple file")
        assertEquals(2, si.sampledImages().first().binding, "Binding of first sampled image in simple file")
        assertEquals(0, si.sampledImages().first().set, "Descriptor set of first sampled image in descriptor set")
        si.close()
    }

    @Test
    fun testStorageImages() {
        val si = ShaderIntrospection(getSimpleSPIRVBytecode())
        assertEquals(ShaderIntrospection.UBOSpecType.Image2D, si.storageImages().first().type, "Type of first storage image in simple file")
        assertEquals("uImage", si.storageImages().first().name, "Name of storage image")
        assertEquals(1, si.storageImages().size, "Number of storage images from simple file")
        assertEquals(3, si.storageImages().first().binding, "Binding of first storage image in simple file")
        assertEquals(0, si.storageImages().first().set, "Descriptor set of first storage image in descriptor set")

        si.close()

    }

    @Test
    fun testPushConstantsSimpleFile() {
        val si = ShaderIntrospection(getSimpleSPIRVBytecode())
        assertEquals("registers", si.pushContants().first().name, "Push constant same for simple file")
        assertEquals("a", si.pushContants().first().members.keys.first(), "Push constant contents, first member, for simple file")
        si.close()
    }

    @Test
    fun testStorageBuffersSimpleFile() {
        val si = ShaderIntrospection(getSimpleSPIRVBytecode())
        assertEquals("SSBO", si.storageBuffers().first().name, "Name of storage buffer in simple file")
        assertEquals(ShaderIntrospection.UBOSpecType.StorageBuffer, si.storageBuffers().first().type)

        val firstMember = si.storageBuffers().first().members.entries.first()
        assertEquals("a", firstMember.key, "Name of first SSBO member")
        assertEquals(0, firstMember.value.index, "Index of first SSBO member")
        assertEquals(0, firstMember.value.offset, "Offset of first SSBO member")
        assertEquals(4, firstMember.value.range, "Range of first SSBO member")
        si.close()
    }

    @Test
    fun testLocalSizes() {
        val shaders = Shaders.ShadersFromFiles(arrayOf("BGRAMosaic.comp"), ComputeShaderExample::class.java)
        val bytecode = shaders.get(Shaders.ShaderTarget.Vulkan, ShaderType.ComputeShader).getSPIRVOpcodes()!!

        val si = ShaderIntrospection(bytecode)
        val localSizes = si.localSizes()

        assertEquals(16, localSizes.x, "Local size X is wrong")
        assertEquals(16, localSizes.y, "Local size Y is wrong")
        assertEquals(1, localSizes.z, "Local size Z is wrong")
    }

    private fun getSimpleSPIRVBytecode(): IntArray {
        return this.javaClass.getResourceAsStream("c_api_test.spv")!!.readBytes()
            .toList()
            .windowed(4, 4)
            .map { bytes ->
                (bytes[0] and 0xFF.toByte()).toInt() shl 24 or
                    ((bytes[1] and 0xFF.toByte()).toInt() shl 16) or
                    ((bytes[2] and 0xFF.toByte()).toInt() shl 8) or
                    ((bytes[3] and 0xFF.toByte()).toInt() shl 0)
            }.toIntArray()
    }
}
