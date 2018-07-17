package graphics.scenery.backends

import graphics.scenery.BufferUtils
import graphics.scenery.spirvcrossj.*
import graphics.scenery.utils.LazyLogger
import sun.plugin.dom.exception.InvalidStateException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

sealed class Shaders() {
    val logger by LazyLogger()
    enum class SourceSPIRVPriority { SourcePriority, SPIRVPriority }
    enum class ShaderTarget { Vulkan, OpenGL }

    data class ShaderPackage(val baseClass: Class<*>,
                             val type: ShaderType,
                             val spirvPath: String?,
                             val codePath: String?,
                             val spirv: ByteArray?,
                             val code: String?,
                             var priority: SourceSPIRVPriority) {
        val logger by LazyLogger()

        init {
            val sourceNewer = if(code != null) {
                val codeDate = Date(baseClass.getResource(codePath).openConnection().lastModified)
                val spirvDate = if(spirv != null) {
                    logger.info("base: $baseClass path=$spirvPath")
                    val res = baseClass.getResource(spirvPath)
                    if(res == null) {
                        Date(0)
                    } else {
                        Date(res.openConnection().lastModified + 500)
                    }
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

        fun getSPIRVBytecode(): IntVec? {
            val bytecode = IntVec()

            if(spirv == null) {
                return null
            }

            val buffer = BufferUtils.allocateByteAndPut(spirv).asIntBuffer()

            while(buffer.hasRemaining()) {
                bytecode.pushBack(1L*buffer.get())
            }

            return bytecode
        }

        fun toShortString(): String {
            return "${this.codePath}/${this.spirvPath}/${this.type}"
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

    abstract class ShaderFactory : Shaders() {
        abstract fun construct(target: ShaderTarget, type: ShaderType): ShaderPackage
        override fun get(target: ShaderTarget, type: ShaderType): ShaderPackage {
            return construct(target, type)
        }
    }

    open class ShadersFromFiles(val shaders: Array<String>,
                           val clazz: Class<*> = Renderer::class.java) : Shaders() {
        override fun get(target: ShaderTarget, type: ShaderType): ShaderPackage {
            val shaderCodePath = shaders.find { it.endsWith(type.toExtension()) || it.endsWith(type.toExtension() + ".spv") } ?: throw ShaderNotFoundException("Could not locate $type from ${shaders.joinToString(", ")}")
            val spirvPath: String
            val codePath: String

            if(shaderCodePath.endsWith(".spv")) {
                spirvPath = shaderCodePath
                codePath = shaderCodePath.substringBeforeLast(".spv")
            } else {
                spirvPath = "$shaderCodePath.spv"
                codePath = shaderCodePath
            }

            val cached = cache[spirvPath to codePath]
            if(cached != null) {
                return cached
            }

            val baseClass = arrayOf(spirvPath, codePath).mapNotNull { safeFindBaseClass(arrayOf(clazz), it) }

            if(baseClass.isEmpty()) {
                throw ShaderCompilationException("Shader files for $shaderCodePath ($spirvPath, $codePath) not found.")
            }

            val base = baseClass.first()

            val spirvFromFile: ByteArray? = base.getResourceAsStream(spirvPath)?.readBytes()
            val codeFromFile: String? = base.getResourceAsStream(codePath)?.bufferedReader().use { it?.readText() }

            val shaderPackage = ShaderPackage(base,
                type,
                spirvPath,
                codePath,
                spirvFromFile,
                codeFromFile,
                SourceSPIRVPriority.SourcePriority)

            val sourceCode: String

            val spirv = if(shaderPackage.spirv != null && shaderPackage.priority == SourceSPIRVPriority.SPIRVPriority) {
                val bytecode = shaderPackage.getSPIRVBytecode() ?: throw IllegalStateException("SPIRV bytecode not found")
                logger.info("Using SPIRV version, ${bytecode.size()} opcodes")
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

            val p = ShaderPackage(base,
                type,
                spirvPath,
                codePath,
                spirv.toByteArray(),
                sourceCode,
                shaderPackage.priority)

            cache[spirvPath to codePath] = p
            return p
        }

        companion object {
            protected val cache = ConcurrentHashMap<Pair<String, String>, ShaderPackage>()
        }
    }

    class ShadersFromClassName(clazz: Class<*>):
        ShadersFromFiles(arrayOf(".vert", ".geom", ".tese", ".tesc", ".frag", ".comp").map { "${clazz.simpleName}$it" }.toTypedArray())

    abstract fun get(target: ShaderTarget, type: ShaderType): ShaderPackage

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
            logger.warn("Shader path $path not found in class path of Renderer.")
            null
        } else {
            Renderer::class.java
        }
    }

    fun ByteBuffer.toSPIRVBytecode(): IntVec {
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
