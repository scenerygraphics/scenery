package graphics.scenery.backends

import graphics.scenery.utils.lazyLogger
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shaders handling class.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
sealed class Shaders() {
    protected val logger by lazyLogger()
    /** Marks a collection of shaders to be stale, usually used for online reloading. */
    @Volatile var stale: Boolean = true
    /** Sets whether a collection of shaders residing on the local file system should be watched for changes. */
    var watchFiles = true
    /** The types of shaders contained in this collection. */
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

            val f = File(codePath)
            val paths = ShaderPaths(spirvPath, codePath)

            val (spirvFromFile, codeFromFile) = if(f.exists()) {
                logger.warn("Using $codePath from filesystem, will ignore compiled or packaged version.")
                changeTimes.putIfAbsent(ShaderPaths(spirvPath, codePath), f.lastModified())

                CoroutineScope(Dispatchers.IO).launch {
                    while(watchFiles) {
                        cache.remove(ShaderPaths(spirvPath, codePath))
                        val modifiedTime = f.lastModified()
                        if(modifiedTime > (changeTimes[paths] ?: 0)) {
                            logger.info("File changed, marking $codePath as stale")
                            changeTimes[paths] = modifiedTime
                            stale = true
                            watchFiles = false
                        }
                        delay(2000.milliseconds)
                    }
                }

                null to f.readText()
            } else {
                base.first.getResourceAsStream("$pathPrefix$spirvPath")?.readBytes() to
                        base.first.getResourceAsStream("$pathPrefix$codePath")?.bufferedReader().use { it?.readText() }
            }

            val shaderPackage = ShaderPackage(base.first,
                type,
                "$pathPrefix$spirvPath",
                "$pathPrefix$codePath",
                spirvFromFile,
                codeFromFile,
                SourceSPIRVPriority.SourcePriority)

            val p = compile(shaderPackage, type, target, base.first)
            if(stale) {
                cache[paths] = p
            }
            stale = false

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
            protected val changeTimes = ConcurrentHashMap<ShaderPaths, Long>()
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
        val debug = System.getProperty("scenery.ShaderCompiler.Debug", "false").toBoolean()
        val strict = System.getProperty("scenery.ShaderCompiler.Strict", "false").toBoolean()

        val priority = if(debug) {
            SourceSPIRVPriority.SourcePriority
        } else {
            shaderPackage.priority
        }

        val spirv: ByteArray = if(shaderPackage.spirv != null && priority == SourceSPIRVPriority.SPIRVPriority) {
            val pair = compileFromSPIRVBytecode(shaderPackage, target)
            sourceCode = pair.second
            pair.first
        } else if(shaderPackage.code != null && priority == SourceSPIRVPriority.SourcePriority) {
            val pair = compileFromSource(shaderPackage, shaderPackage.code, type, target, base, debug, strict)
            sourceCode = pair.second
            pair.first
        } else {
            throw ShaderCompilationException("Neither code nor compiled SPIRV file found for ${shaderPackage.codePath}")
        }

        val p = ShaderPackage(base,
            type,
            shaderPackage.spirvPath,
            shaderPackage.codePath,
            spirv,
            sourceCode,
            priority)

        return p
    }

    private fun compileFromSource(shaderPackage: ShaderPackage, code: String, type: ShaderType, target: ShaderTarget, base: Class<*>, debug: Boolean = false, strict: Boolean = false): Pair<ByteArray, String> {
        logger.debug("Compiling ${shaderPackage.codePath} to SPIR-V...")
        // code needs to be compiled first

        val compiler = ShaderCompiler()
        val bytecode = compiler.compile(code, type, target, "main", debug, strict, ShaderCompiler.OptimisationLevel.NoOptimisation, shaderPackage.codePath, base.simpleName)
        compiler.close()
        return Pair(bytecode, code)
    }

    private fun compileFromSPIRVBytecode(shaderPackage: ShaderPackage, target: ShaderTarget): Pair<ByteArray, String> {
        val bytecode = shaderPackage.spirv ?: throw IllegalStateException("SPIRV bytecode not found")
        val opcodes = shaderPackage.getSPIRVOpcodes()!!
        logger.debug("Using SPIRV version, ${bytecode.size/4} opcodes")

        val introspection = when (target) {
            ShaderTarget.Vulkan -> {
                ShaderIntrospection(opcodes, vulkanSemantics = true, version = 450)
            }
            ShaderTarget.OpenGL -> {
                ShaderIntrospection(opcodes, vulkanSemantics = false, version = 410)
            }
        }

        val sourceCode = introspection.compile()

        return Pair(bytecode, sourceCode)
    }
}
