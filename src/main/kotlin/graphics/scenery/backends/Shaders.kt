package graphics.scenery.backends

import graphics.scenery.BufferUtils
import graphics.scenery.spirvcrossj.*
import graphics.scenery.utils.LazyLogger
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Shaders handling class.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
sealed class Shaders {
    val logger by LazyLogger()
    var stale: Boolean = false
    val type: HashSet<ShaderType> = hashSetOf()

    /**
     * Enum to indicate whether a shader will target Vulkan or OpenGL.
     */
    enum class ShaderTarget { Vulkan, OpenGL }

    /**
     * Abstract base class for custom shader factories.
     */
    abstract class ShaderFactory : Shaders() {
        /**
         * Invoked by [get] to actually construct a [ShaderPackage].
         */
        abstract fun construct(target: ShaderTarget, type: ShaderType): ShaderPackage

        /**
         * Returns a [ShaderPackage] targeting [target] (OpenGL or Vulkan), containing
         * a shader of [type].
         */
        override fun get(target: ShaderTarget, type: ShaderType): ShaderPackage {
            return construct(target, type)
        }
    }

    /**
     * Base class for producing a shader provider that is backed by files given in
     * [shaders], which are assumed to be relative to a class [clazz].
     */
    open class ShadersFromFiles(val shaders: Array<String>,
                           val clazz: Class<*> = Renderer::class.java) : Shaders() {
        init {
            type.addAll(shaders.map {
                val extension = it.lowercase().substringBeforeLast(".spv").substringAfterLast(".").trim()
                when(extension) {
                    "vert" -> ShaderType.VertexShader
                    "frag" -> ShaderType.FragmentShader
                    "comp" -> ShaderType.ComputeShader
                    "tesc" -> ShaderType.TessellationControlShader
                    "tese" -> ShaderType.TessellationEvaluationShader
                    "geom" -> ShaderType.GeometryShader
                    else -> throw IllegalArgumentException(".$extension is not a valid shader file extension")
                }
            })
        }

        /**
         * Returns a [ShaderPackage] targeting [target] (OpenGL or Vulkan), containing
         * a shader of [type].
         */
        override fun get(target: ShaderTarget, type: ShaderType): ShaderPackage {
            val shaderCodePath = shaders.find { it.endsWith(type.toExtension()) || it.endsWith(type.toExtension() + ".spv") }
                ?: throw ShaderNotFoundException("Could not locate $type from ${shaders.joinToString(", ")}")
            val spirvPath: String
            val codePath: String

            if (shaderCodePath.endsWith(".spv")) {
                spirvPath = shaderCodePath
                codePath = shaderCodePath.substringBeforeLast(".spv")
            } else {
                spirvPath = "$shaderCodePath.spv"
                codePath = shaderCodePath
            }

            val cached = cache[ShaderPaths(spirvPath, codePath)]
            if (cached != null) {
                return cached
            }

            val baseClass = arrayOf(spirvPath, codePath).mapNotNull { safeFindBaseClass(arrayOf(clazz, Renderer::class.java), it) }

            if (baseClass.isEmpty()) {
                throw ShaderCompilationException("Shader files for $shaderCodePath ($spirvPath, $codePath) not found.")
            }

            val base = baseClass.first()
            val pathPrefix = base.second

            val spirvFromFile: ByteArray? = base.first.getResourceAsStream("$pathPrefix$spirvPath")?.readBytes()
            val codeFromFile: String? = base.first.getResourceAsStream("$pathPrefix$codePath")?.bufferedReader().use { it?.readText() }

            val shaderPackage = ShaderPackage(base.first,
                type,
                "$pathPrefix$spirvPath",
                "$pathPrefix$codePath",
                spirvFromFile,
                codeFromFile,
                SourceSPIRVPriority.SourcePriority)

            val p = compile(shaderPackage, type, target, base.first)
            cache.putIfAbsent(ShaderPaths(spirvPath, codePath), p)

            return p
        }

        override fun toString(): String {
            return "ShadersFromFiles: ${shaders.joinToString(",")}"
        }


        /**
         * Data class for storing pairs of paths to SPIRV and to code path files
         */
        data class ShaderPaths(val spirvPath: String, val codePath: String)

        /**
         * Companion object providing a cache for preventing repeated compilations.
         */
        companion object {
            protected val cache = ConcurrentHashMap<ShaderPaths, ShaderPackage>()
        }
    }

    /**
     * Shader provider for deriving a [ShadersFromFiles] provider just by using
     * the simpleName of [clazz].
     */
    class ShadersFromClassName @JvmOverloads constructor(clazz: Class<*>, shaderTypes: List<ShaderType> = listOf(ShaderType.VertexShader, ShaderType.FragmentShader)):
        ShadersFromFiles(
            shaderTypes
                .map { it.toExtension() }.toTypedArray()
                .map { "${clazz.simpleName}$it" }.toTypedArray(), clazz) {
        init {
            type.addAll(shaderTypes)
        }
    }

    /**
     * Abstract functions all shader providers will have to implement, for returning
     * a [ShaderPackage] containing both source code and SPIRV, targeting [target],
     * and being of [ShaderType] [type].
     */
    abstract fun get(target: ShaderTarget, type: ShaderType): ShaderPackage

    /**
     * Finds the base class for a resource given by [path], and falls back to
     * [Renderer] in case it is not found before. Returns null if the file cannot
     * be located. The function also falls back to looking into a subdirectory "shaders/",
     * if the files cannot be located within the normal neighborhood of the resources in [classes].
     */
    protected fun safeFindBaseClass(classes: Array<Class<*>>, path: String): Pair<Class<*>, String>? {
        logger.debug("Looking for $path in ${classes.map { it.simpleName }.joinToString(", ")}")
        val streams = classes.map { clazz ->
            clazz to clazz.getResourceAsStream(path)
        }.filter { it.second != null }.toMutableList()

        var pathPrefix = ""
        if(streams.isEmpty()) {
            pathPrefix = "shaders/"
        }

        streams.addAll(classes.map { clazz ->
            clazz to clazz.getResourceAsStream("$pathPrefix$path")
        }.filter { it.second != null })

        if(streams.isEmpty()) {
            if(classes.contains(Renderer::class.java) && !path.endsWith(".spv")) {
                logger.warn("Shader path $path not found within given classes, falling back to default.")
            } else {
                logger.debug("Shader path $path not found within given classes, falling back to default.")
            }
        } else {
            return streams.first().first to pathPrefix
        }

        return if(Renderer::class.java.getResourceAsStream(path) == null) {
            if(!path.endsWith(".spv")) {
                logger.warn("Shader path $path not found in class path of Renderer.")
            }

            null
        } else {
            Renderer::class.java to pathPrefix
        }
    }

    protected fun compile(shaderPackage: ShaderPackage, type: ShaderType, target: ShaderTarget, base: Class<*>): ShaderPackage {
        val sourceCode: String
        val spirv = if(shaderPackage.spirv != null && shaderPackage.priority == SourceSPIRVPriority.SPIRVPriority) {
            val pair = compileFromSPIRVBytecode(shaderPackage, target)
            sourceCode = pair.second
            pair.first
        } else if(shaderPackage.code != null && shaderPackage.priority == SourceSPIRVPriority.SourcePriority) {
            val pair = compileFromSource(shaderPackage, shaderPackage.code, type, target, base)
            sourceCode = pair.second
            pair.first
        } else {
            throw ShaderCompilationException("Neither code nor compiled SPIRV file found for ${shaderPackage.codePath}")
        }

        val p = ShaderPackage(base,
            type,
            shaderPackage.spirvPath,
            shaderPackage.codePath,
            spirv.toByteArray(),
            sourceCode,
            shaderPackage.priority)

        return p
    }

    private fun compileFromSource(shaderPackage: ShaderPackage, code: String, type: ShaderType, target: ShaderTarget, base: Class<*>): Pair<IntVec, String> {
        logger.debug("Compiling ${shaderPackage.codePath} to SPIR-V...")
        // code needs to be compiled first
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

        val shaderCode = if (target == ShaderTarget.Vulkan) {
            arrayOf(code)
        } else {
            val extensionEnd = code.indexOf("\n", code.findLastAnyOf(listOf("#extension", "#versions"))?.first ?: 0)
            arrayOf(code.substring(0, extensionEnd) + "\n#define OPENGL\n" + code.substring(extensionEnd))
        }

        shader.setStrings(shaderCode, shaderCode.size)
        shader.setAutoMapBindings(true)

        val compileFail = !shader.parse(defaultResources, 450, false, messages)
        if (compileFail) {
            logger.error("Error in shader compilation of ${shaderPackage.codePath} for ${base.simpleName}: ${shader.infoLog}")
            logger.error("Shader code was: \n${shaderCode.joinToString("\n").split("\n").mapIndexed { index, s -> "${index+1}\t:  $s" }.joinToString("\n")}")
        }

        program.addShader(shader)

        val linkFail = !program.link(EShMessages.EShMsgDefault) || !program.mapIO()

        val intVec = if (!linkFail && !compileFail) {
            val tmp = IntVec()
            libspirvcrossj.glslangToSpv(program.getIntermediate(shaderType), tmp)

            tmp
        } else {
            logger.error("Error in shader linking of ${shaderPackage.codePath} for ${base.simpleName}: ${program.infoLog}")
            throw ShaderCompilationException("Error compiling shader file ${shaderPackage.codePath}")
        }
        return Pair(intVec, code)
    }

    private fun compileFromSPIRVBytecode(shaderPackage: ShaderPackage, target: ShaderTarget): Pair<IntVec, String> {
        val bytecode = shaderPackage.getSPIRVBytecode() ?: throw IllegalStateException("SPIRV bytecode not found")
        logger.debug("Using SPIRV version, ${bytecode.size} opcodes")
        val compiler = CompilerGLSL(bytecode)

        val options = CompilerGLSL.Options()
        when (target) {
            ShaderTarget.Vulkan -> {
                options.version = 450
                options.es = false
                options.vulkanSemantics = true
            }
            ShaderTarget.OpenGL -> {
                options.version = 410
                options.es = false
                options.vulkanSemantics = false
            }
        }
        compiler.commonOptions = options
        val sourceCode = compiler.compile()

        return Pair(bytecode, sourceCode)
    }

    /**
     * Converts an glslang-compatible IntVec to a [ByteBuffer].
     */
    protected fun IntVec.toByteBuffer(): ByteBuffer {
        val buf = BufferUtils.allocateByte(this.size*4)
        val ib = buf.asIntBuffer()

        for (i in 0 until this.size) {
            ib.put(this[i].toInt())
        }

        return buf
    }

    /**
     * Converts an glslang-compatible IntVec to a [ByteArray].
     */
    protected fun IntVec.toByteArray(): ByteArray {
        val buf = this.toByteBuffer()
        val array = ByteArray(buf.capacity())
        buf.get(array, 0, array.size)

        return array
    }
}
