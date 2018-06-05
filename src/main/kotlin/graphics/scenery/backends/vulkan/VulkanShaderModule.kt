package graphics.scenery.backends.vulkan

import graphics.scenery.BufferUtils
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.ShaderCompilationException
import graphics.scenery.spirvcrossj.*
import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.LinkedHashMap


/**
 * Vulkan Shader Module
 *
 * Class facilitating the use of shaders in Vulkan. Supports loading SPIR-V binaries and compiling GLSL
 * shader text files to SPIR-V binaries, with introspection.
 *
 * When loading a SPIR-V binary, VulkanShaderModule will check if a newer GLSL text file with the same name
 * exists and load that.
 *
 * @param[device] The Vulkan device to use (VkDevice)
 * @param[entryPoint] Customizable main entry point for the shader, usually "main"
 * @param[clazz] Base path for the shader module, to determine where to load the file from
 * @param[shaderCodePath] Path of the shader text file or SPIR-V binary file, relative to clazz.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

open class VulkanShaderModule(val device: VulkanDevice, entryPoint: String, clazz: Class<*>, shaderCodePath: String) {
    protected val logger by LazyLogger()
    var shader: VkPipelineShaderStageCreateInfo
    var shaderModule: Long
    var uboSpecs = LinkedHashMap<String, UBOSpec>()
    var pushConstantSpecs = LinkedHashMap<String, PushConstantSpec>()
    private var shaderPackage: ShaderPackage
    private var deallocated: Boolean = false
    private var signature: ShaderSignature

    data class UBOMemberSpec(val name: String, val index: Long, val offset: Long, val range: Long)
    data class UBOSpec(val name: String, var set: Long, var binding: Long, val members: LinkedHashMap<String, UBOMemberSpec>)
    data class PushConstantSpec(val name: String, val members: LinkedHashMap<String, UBOMemberSpec>)

    data class ShaderPackage(val baseClass: Class<*>, val spirvPath: String, val codePath: String, val spirv: InputStream?, val code: InputStream?)

    private data class ShaderSignature(val device: VulkanDevice, val clazz: Class<*>, val shaderCodePath: String)

    private fun ShaderPackage.isSourceNewer(): Boolean {
        return if(code != null) {
            val codeDate = Date(baseClass.getResource(codePath).openConnection().lastModified)
            val spirvDate = if(spirv != null) {
                Date(baseClass.getResource(spirvPath).openConnection().lastModified + 500)
            } else {
                Date(0)
            }

            codeDate.after(spirvDate)
        } else {
            false
        }
    }

    private fun safeFindBaseClass(classes: Array<Class<*>>, path: String): Class<*>? {
        val streams = classes.map { clazz ->
            clazz.to(clazz.getResourceAsStream(path))
        }.filter { it.second != null }

        if(streams.isEmpty()) {
            if(classes.contains(Renderer::class.java)) {
                logger.warn("Shader path $path not found within given classes, falling back to default.")
            } else {
                logger.debug("Shader path $path not found within given classes, falling back to default.")
            }
        } else {
            return streams.first().first
        }

        if(Renderer::class.java.getResourceAsStream(path) == null) {
            logger.debug("Shader path $path not found in class path.")
            return null
        } else {
            return Renderer::class.java
        }
    }

    init {
        signature = ShaderSignature(device, clazz, shaderCodePath)
        val spirvPath: String
        val codePath: String

        logger.debug("Creating VulkanShaderModule $entryPoint, $shaderCodePath")

        if(shaderCodePath.endsWith(".spv")) {
            spirvPath = shaderCodePath
            codePath = shaderCodePath.substringBeforeLast(".spv")
        } else {
            spirvPath = shaderCodePath + ".spv"
            codePath = shaderCodePath
        }

        val baseClass = arrayOf(spirvPath, codePath).mapNotNull { safeFindBaseClass(arrayOf(clazz), it) }

        if(baseClass.isEmpty()) {
            throw ShaderCompilationException("Shader files for $shaderCodePath not found.")
        }

        val base = baseClass.first()
        shaderPackage = ShaderPackage(base,
            spirvPath,
            codePath,
            base.getResourceAsStream(spirvPath),
            base.getResourceAsStream(codePath))

        val code: ByteBuffer

        val spirv = if(shaderPackage.spirv != null && !shaderPackage.isSourceNewer()) {
            code = BufferUtils.allocateByteAndPut(shaderPackage.spirv!!.readBytes())
            code.toSPIRVBytecode()
        } else if(shaderPackage.code != null && shaderPackage.isSourceNewer()) {
            logger.info("Compiling ${shaderPackage.codePath} to SPIR-V...")
            // code needs to be compiled first
            val program = TProgram()
            val defaultResources = libspirvcrossj.getDefaultTBuiltInResource()
            val shaderType = when (shaderPackage.codePath.substringAfterLast(".")) {
                "vert" -> EShLanguage.EShLangVertex
                "frag" -> EShLanguage.EShLangFragment
                "geom" -> EShLanguage.EShLangGeometry
                "tesc" -> EShLanguage.EShLangTessControl
                "tese" -> EShLanguage.EShLangTessEvaluation
                "comp" -> EShLanguage.EShLangCompute
                else -> { logger.warn("Unknown shader extension ." + shaderPackage.codePath.substringAfterLast(".")); 0 }
            }


            val shader = TShader(shaderType)

            var messages = EShMessages.EShMsgDefault
            messages = messages or EShMessages.EShMsgVulkanRules
            messages = messages or EShMessages.EShMsgSpvRules

            val shaderCode = arrayOf(shaderPackage.code!!.readBytes().toString(Charset.forName("UTF-8")))
            shader.setStrings(shaderCode, shaderCode.size)
            shader.setAutoMapBindings(true)

            val compileFail = !shader.parse(defaultResources, 450, false, messages)
            if(compileFail) {
                logger.error("Error in shader compilation of ${shaderPackage.codePath} for ${clazz.simpleName}: ${shader.infoLog}")
            }

            program.addShader(shader)

            val linkFail = !program.link(EShMessages.EShMsgDefault) || !program.mapIO()

            if(!linkFail && !compileFail) {
                val tmp = IntVec()
                libspirvcrossj.glslangToSpv(program.getIntermediate(shaderType), tmp)

                code = tmp.toByteBuffer()

                tmp
            } else {
                logger.error("Error in shader linking of ${shaderPackage.codePath} for ${clazz.simpleName}: ${program.infoLog}")
                throw ShaderCompilationException("Error compiling shader file ${shaderPackage.codePath}")
            }
        } else {
            throw ShaderCompilationException("Neither code nor compiled SPIRV file found for $shaderCodePath")
        }

        val compiler = CompilerGLSL(spirv)

        val uniformBuffers = compiler.shaderResources.uniformBuffers
        val pushConstants = compiler.shaderResources.pushConstantBuffers

        for(i in 0 until uniformBuffers.size()) {
            val res = uniformBuffers.get(i.toInt())
            logger.debug("${res.name}, set=${compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet)}, binding=${compiler.getDecoration(res.id, Decoration.DecorationBinding)}")

            val members = LinkedHashMap<String, UBOMemberSpec>()
            val activeRanges = compiler.getActiveBufferRanges(res.id)

            // record all members of the UBO struct, order by index, and store them to UBOSpec.members
            // for further use
            members.putAll((0 until activeRanges.size()).map {
                val range = activeRanges.get(it.toInt())
                val name = compiler.getMemberName(res.baseTypeId, range.index)

                name to UBOMemberSpec(
                    compiler.getMemberName(res.baseTypeId, range.index),
                    range.index,
                    range.offset,
                    range.range)
            }.sortedBy { it.second.index })

            val ubo = UBOSpec(res.name,
                set = compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet),
                binding = compiler.getDecoration(res.id, Decoration.DecorationBinding),
                members = members)

            // only add the UBO spec if it doesn't already exist, and has more than 0 members
            // SPIRV UBOs may have 0 members, if they are not used in the actual shader code
            if(!uboSpecs.contains(res.name) && ubo.members.size > 0) {
                uboSpecs.put(res.name, ubo)
            }
        }

        for(i in 0 until pushConstants.size()) {
            val res = pushConstants.get(i.toInt())
            val activeRanges = compiler.getActiveBufferRanges(res.id)
            val members = LinkedHashMap<String, UBOMemberSpec>()

            logger.debug("Push constant: ${res.name}, id=${compiler.getDecoration(res.id, Decoration.DecorationConstant)}")

            members.putAll((0 until activeRanges.size()).map {
                val range = activeRanges.get(it.toInt())
                val name = compiler.getMemberName(res.baseTypeId, range.index)

                name to UBOMemberSpec(
                    compiler.getMemberName(res.baseTypeId, range.index),
                    range.index,
                    range.offset,
                    range.range
                )
            }.sortedBy { it.second.index })

            val pcs = PushConstantSpec(res.name,
                members = members)

            if(!pushConstantSpecs.contains(res.name) && pcs.members.size > 0) {
                pushConstantSpecs.put(res.name, pcs)
            }
        }

        /* Updated version:
       for(i in 0..compiler.shaderResources.sampledImages.size()-1) {
        // inputs are summarized into one descriptor set
            val res = compiler.shaderResources.sampledImages.get(i.toInt())
            logger.info("Adding textures ${res.name} with set=${compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet)}")

            // FIXME: Here we assume at the moment that we either have only input textures (from framebuffers), or only object textures
            val name = if(res.name == "ObjectTextures" || res.name == "VolumeTextures") {
                "ObjectTextures"
            } else {
                "inputs"
            }

            uboSpecs.put(res.name, UBOSpec(name,
                    set = compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet),
                    binding = 0,
                    members = LinkedHashMap<String, UBOMemberSpec>()))
         */
        // inputs are summarized into one descriptor set
        val inputSets = mutableSetOf<Long>()
        (0 until compiler.shaderResources.sampledImages.size()).forEach { samplerId ->
            val res = compiler.shaderResources.sampledImages.get(samplerId.toInt())
            val setId = compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet)

            val name = if(res.name.startsWith("Input")) {
                if(!inputSets.contains(setId)) {
                    inputSets.add(setId)
                }

                "Inputs-${inputSets.size-1}"
            } else {
                res.name
            }

            if(uboSpecs.containsKey(name)) {
                logger.debug("Adding inputs member ${res.name}/$name")
                uboSpecs[name]!!.members.put(res.name, UBOMemberSpec(res.name, uboSpecs[name]!!.members.size.toLong(), 0L, 0L))
                uboSpecs[name]!!.binding = minOf(uboSpecs[name]!!.binding, compiler.getDecoration(res.id, Decoration.DecorationBinding))
            } else {
                logger.debug("Adding inputs UBO, ${res.name}/$name")
                uboSpecs.put(name, UBOSpec(name,
                    set = setId,
                    binding = compiler.getDecoration(res.id, Decoration.DecorationBinding),
                    members = LinkedHashMap()))

                if(name.startsWith("Inputs")) {
                    uboSpecs[name]!!.members.put(res.name, UBOMemberSpec(res.name, 0L, 0L, 0L))
                }
            }
        }

        val inputs = compiler.shaderResources.stageInputs
        if(inputs.size() > 0) {
            for (i in 0 until inputs.size()) {
                logger.debug("$shaderCodePath: ${inputs.get(i.toInt()).name}")
            }
        }

        val moduleCreateInfo = VkShaderModuleCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
            .pNext(NULL)
            .pCode(code)
            .flags(0)

        this.shaderModule = VU.getLong("Creating shader module",
            { vkCreateShaderModule(device.vulkanDevice, moduleCreateInfo, null, this) },
            { moduleCreateInfo.free(); })

        this.shader = VkPipelineShaderStageCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            .stage(getStageFromFilename(shaderCodePath))
            .module(this.shaderModule)
            .pName(memUTF8(entryPoint))
            .pNext(NULL)
    }

    private fun IntVec.toByteBuffer(): ByteBuffer {
        val buf = BufferUtils.allocateByte(this.size().toInt()*4)
        val ib = buf.asIntBuffer()

        for (i in 0..this.size()-1) {
            ib.put(this[i.toInt()].toInt())
        }

        return buf
    }

    private fun ByteBuffer.toSPIRVBytecode(): IntVec {
        val bytecode = IntVec()
        val ib = this.asIntBuffer()

        while(ib.hasRemaining()) {
            bytecode.pushBack(1L*ib.get())
        }

        return bytecode
    }

    protected fun getStageFromFilename(shaderCodePath: String): Int {
        val ending = if(shaderCodePath.substringAfterLast(".").startsWith("spv")) {
            shaderCodePath.substringBeforeLast(".").substringAfterLast(".")
        } else {
            shaderCodePath.substringAfterLast(".")
        }

        val type = when(ending) {
           "vert" -> VK_SHADER_STAGE_VERTEX_BIT
           "geom" -> VK_SHADER_STAGE_GEOMETRY_BIT
           "tesc" -> VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT
           "tese" -> VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT
           "frag" -> VK_SHADER_STAGE_FRAGMENT_BIT
           "comp" -> VK_SHADER_STAGE_COMPUTE_BIT
            else -> {
                logger.error("Unknown shader type: $ending for $shaderCodePath")
                -1
            }
        }

        logger.trace("$shaderCodePath has type $type")

        return type
    }

    fun close() {
        if(!deallocated) {
            vkDestroyShaderModule(device.vulkanDevice, shader.module(), null)
            shaderModuleCache.remove(signature.hashCode())

            memFree(shader.pName())
            shader.free()

            deallocated = true
        }
    }

    companion object {
        private val shaderModuleCache = ConcurrentHashMap<Int, VulkanShaderModule>()

        @Suppress("UNUSED")
        fun createFromSPIRV(device: VulkanDevice, name: String, clazz: Class<*>, sourceFile: String): VulkanShaderModule {
            return VulkanShaderModule(device, name, clazz, sourceFile)
        }

        @Suppress("UNUSED")
        fun createFromSource(device: VulkanDevice, name: String, clazz: Class<*>, sourceFile: String): VulkanShaderModule {
            return VulkanShaderModule(device, name, clazz, sourceFile)
        }

        fun getFromCacheOrCreate(device: VulkanDevice, entryPoint: String, clazz: Class<*>, shaderCodePath: String): VulkanShaderModule {
            val signature = ShaderSignature(device, clazz, shaderCodePath).hashCode()

            return if(shaderModuleCache.containsKey(signature)) {
                shaderModuleCache[signature]!!
            } else {
                val module = VulkanShaderModule(device, entryPoint, clazz, shaderCodePath)
                shaderModuleCache[signature] = module

                module
            }
        }

        fun clearCache() {
            shaderModuleCache.forEach { it.value.close() }
            shaderModuleCache.clear()
        }
    }
}
