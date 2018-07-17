package graphics.scenery.backends

import graphics.scenery.BufferUtils
import graphics.scenery.spirvcrossj.*
import graphics.scenery.utils.LazyLogger
import java.nio.ByteBuffer
import java.util.*

sealed class Shaders(val target: ShaderTarget) {
    val logger by LazyLogger()
    enum class SourceSPIRVPriority { SourcePriority, SPIRVPriority }
    enum class ShaderTarget { Vulkan, OpenGL }

    data class ShaderPackage(val baseClass: Class<*>,
                             val spirvPath: String?,
                             val codePath: String?,
                             val spirv: ByteArray?,
                             val code: String?,
                             var priority: SourceSPIRVPriority) {

        init {
            val sourceNewer = if(code != null) {
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

            priority = if(sourceNewer) {
                SourceSPIRVPriority.SourcePriority
            } else {
                SourceSPIRVPriority.SPIRVPriority
            }
        }
    }

    enum class ShaderType {
        VertexShader,
        TessellationControlShader,
        TessellationEvaluationShader,
        GeometryShader,
        FragmentShader,
        ComputeShader
    }

    fun ShaderType.toExtension(): String = when(this) {
        Shaders.ShaderType.VertexShader -> ".vert"
        Shaders.ShaderType.TessellationControlShader -> ".tesc"
        Shaders.ShaderType.TessellationEvaluationShader -> ".tese"
        Shaders.ShaderType.GeometryShader -> ".geom"
        Shaders.ShaderType.FragmentShader -> ".frag"
        Shaders.ShaderType.ComputeShader -> ".comp"
    }

    abstract class ShaderFactory(target: ShaderTarget) : Shaders(target) {
        abstract fun construct(type: ShaderType): ShaderPackage
        override fun get(type: ShaderType): ShaderPackage {
            return construct(type)
        }
    }

    open class ShadersFromFiles(target: ShaderTarget,
                           val shaders: Array<String>,
                           val clazz: Class<*> = Renderer::class.java) : Shaders(target) {
        override fun get(type: ShaderType): ShaderPackage {
            val shaderCodePath = shaders.find { it.endsWith(type.toExtension()) } ?: throw ShaderNotFoundException("Could not locate $type from ${shaders.joinToString(", ")}")
            val spirvPath: String
            val codePath: String

            if(shaderCodePath.endsWith(".spv")) {
                spirvPath = shaderCodePath
                codePath = shaderCodePath.substringBeforeLast(".spv")
            } else {
                spirvPath = "$shaderCodePath.spv"
                codePath = shaderCodePath
            }

            val baseClass = arrayOf(spirvPath, codePath).mapNotNull { safeFindBaseClass(arrayOf(clazz), it) }

            if(baseClass.isEmpty()) {
                throw ShaderCompilationException("Shader files for $shaderCodePath not found.")
            }

            val base = baseClass.first()
            val shaderPackage = ShaderPackage(base,
                spirvPath,
                codePath,
                base.getResourceAsStream(spirvPath)?.readBytes(),
                base.getResourceAsStream(codePath)?.readBytes()?.contentToString(),
                SourceSPIRVPriority.SourcePriority)

            val code: ByteBuffer
            val sourceCode: String

            val spirv = if(shaderPackage.spirv != null && shaderPackage.priority == SourceSPIRVPriority.SPIRVPriority) {
                code = BufferUtils.allocateByteAndPut(shaderPackage.spirv)
                val bytecode = code.toSPIRVBytecode()
                val compiler = CompilerGLSL(bytecode)

                val options = CompilerGLSL.Options()
                when(target) {
                    Shaders.ShaderTarget.Vulkan -> {
                        options.version = 450
                        options.es = false
                        options.vulkanSemantics = true
                    }
                    Shaders.ShaderTarget.OpenGL -> {
                        options.version = 410
                        options.es = false
                        options.vulkanSemantics = false
                    }
                }
                compiler.options = options
                sourceCode = compiler.compile()

                bytecode
            } else if(shaderPackage.code != null && shaderPackage.priority == SourceSPIRVPriority.SourcePriority) {
                logger.info("Compiling ${shaderPackage.codePath} to SPIR-V...")
                // code needs to be compiled first
                sourceCode = shaderPackage.code
                val program = TProgram()
                val defaultResources = libspirvcrossj.getDefaultTBuiltInResource()
                val shaderType = when (type) {
                    ShaderType.VertexShader -> EShLanguage.EShLangVertex
                    ShaderType.FragmentShader -> EShLanguage.EShLangFragment
                    ShaderType.GeometryShader -> EShLanguage.EShLangGeometry
                    ShaderType.TessellationControlShader -> EShLanguage.EShLangTessControl
                    ShaderType.TessellationEvaluationShader -> EShLanguage.EShLangTessEvaluation
                    ShaderType.ComputeShader -> EShLanguage.EShLangCompute
                }


                val shader = TShader(shaderType)

                var messages = EShMessages.EShMsgDefault
                messages = messages or EShMessages.EShMsgVulkanRules
                messages = messages or EShMessages.EShMsgSpvRules

                val shaderCode = if(target == ShaderTarget.Vulkan) {
                    arrayOf(shaderPackage.code)
                } else {
                    val c = shaderPackage.code
                    val extensionEnd = c.indexOf("\n", c.findLastAnyOf(listOf("#extension", "#versions"))?.first ?: 0)
                    arrayOf(c.substring(0, extensionEnd) + "\n#define OPENGL\n" + c.substring(extensionEnd))
                }

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

                    tmp
                } else {
                    logger.error("Error in shader linking of ${shaderPackage.codePath} for ${clazz.simpleName}: ${program.infoLog}")
                    throw ShaderCompilationException("Error compiling shader file ${shaderPackage.codePath}")
                }
            } else {
                throw ShaderCompilationException("Neither code nor compiled SPIRV file found for $shaderCodePath")
            }

            return ShaderPackage(base, spirvPath, codePath, spirv.toByteArray(), sourceCode, shaderPackage.priority)
        }
    }

    class ShadersFromClassName(target: ShaderTarget, clazz: Class<*>):
        ShadersFromFiles(target, arrayOf(".vert", ".geom", ".tese", ".tesc", ".frag", ".comp").map { "${clazz.simpleName}$it" }.toTypedArray())

    abstract fun get(type: ShaderType): ShaderPackage

    protected fun safeFindBaseClass(classes: Array<Class<*>>, path: String): Class<*>? {
        val streams = classes.map { clazz ->
            clazz to clazz.getResourceAsStream(path)
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

        return if(Renderer::class.java.getResourceAsStream(path) == null) {
            logger.debug("Shader path $path not found in class path.")
            null
        } else {
            Renderer::class.java
        }
    }

    protected fun ByteBuffer.toSPIRVBytecode(): IntVec {
        val bytecode = IntVec()
        val ib = this.asIntBuffer()

        while(ib.hasRemaining()) {
            bytecode.pushBack(1L*ib.get())
        }

        return bytecode
    }

    protected fun IntVec.toByteBuffer(): ByteBuffer {
        val buf = BufferUtils.allocateByte(this.size().toInt()*4)
        val ib = buf.asIntBuffer()

        for (i in 0 until this.size()) {
            ib.put(this[i.toInt()].toInt())
        }

        return buf
    }

    protected fun IntVec.toByteArray(): ByteArray {
        val buf = this.toByteBuffer()
        val array = ByteArray(buf.capacity())
        buf.get(array, 0, array.size)

        return array
    }
}
