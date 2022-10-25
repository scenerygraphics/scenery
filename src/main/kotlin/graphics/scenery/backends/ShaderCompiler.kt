package graphics.scenery.backends

import graphics.scenery.utils.lazyLogger
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import picocli.CommandLine
import java.io.File
import java.lang.UnsupportedOperationException
import java.util.concurrent.Callable
import kotlin.system.exitProcess

/**
 * Shader compiler class. Can be used as command line utility as well.
 */
@CommandLine.Command(name = "CompileShader", mixinStandardHelpOptions = true, description = ["Compiles GLSL shader code to SPIRV bytecode."])
class ShaderCompiler: AutoCloseable, Callable<ByteArray> {
    private val logger by lazyLogger()

    protected val compiler = Shaderc.shaderc_compiler_initialize()

    @CommandLine.Parameters(index = "0", description = ["The file to compile"])
    lateinit var file: File

    @CommandLine.Option(names = ["-o", "--output"], description = ["The file to output the bytecode to. By default, .spv will be appended to the input file name."])
    lateinit var output: File

    @CommandLine.Option(names = ["-O", "--optimise"], description = ["Optimisation level, [S]ize, [P]erformance (use with care!), [0] zero/none (default)."])
    var optimise: String = "0"

    @CommandLine.Option(names = ["-g", "--debug"], description = ["Generate debug information."])
    var debug: Boolean = false

    @CommandLine.Option(names = ["-t", "--target"], description = ["Target, Vulkan (default) or OpenGL."])
    var target: String = "Vulkan"

    @CommandLine.Option(names = ["-e", "--entryPoint"], description = ["Entry point for the shader. Default is main."])
    var entryPoint: String = "main"

    @CommandLine.Option(names = ["-s", "--strict"], description = ["Strict compilation, treats warnings as errors."])
    var strict: Boolean = false

    /**
     * Optimisation level for shader compilation.
     */
    enum class OptimisationLevel {
        None,
        Performance,
        Size
    }

    /**
     * Compiles the [code] given to SPIRV bytecode.
     */
    fun compile(
        code: String,
        type: ShaderType,
        target: Shaders.ShaderTarget,
        entryPoint: String = "main",
        debug: Boolean = false,
        warningsAsErrors: Boolean = false,
        optimisationLevel: OptimisationLevel = OptimisationLevel.None,
        path: String? = null,
        baseClass: String? = null
    ): ByteArray {
        val options = Shaderc.shaderc_compile_options_initialize()
        logger.debug("Compiling code from $path of $baseClass, debug=$debug, optimisation=$optimisationLevel")
        val shaderType = when (type) {
            ShaderType.VertexShader -> Shaderc.shaderc_glsl_vertex_shader
            ShaderType.FragmentShader -> Shaderc.shaderc_glsl_fragment_shader
            ShaderType.GeometryShader -> Shaderc.shaderc_glsl_geometry_shader
            ShaderType.TessellationControlShader -> Shaderc.shaderc_glsl_tess_control_shader
            ShaderType.TessellationEvaluationShader -> Shaderc.shaderc_glsl_tess_evaluation_shader
            ShaderType.ComputeShader -> Shaderc.shaderc_glsl_compute_shader
        }

        Shaderc.shaderc_compile_options_set_source_language(options, Shaderc.shaderc_source_language_glsl)

        var shaderCode = if(target == Shaders.ShaderTarget.Vulkan) {
            Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_2)
            Shaderc.shaderc_compile_options_set_target_spirv(options, Shaderc.shaderc_spirv_version_1_2)
            code
        } else {
            Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_opengl, Shaderc.shaderc_env_version_opengl_4_5)
            val extensionEnd = code.indexOf("\n", code.findLastAnyOf(listOf("#extension", "#version"))?.first ?: 0)
            code.substring(0, extensionEnd) + "\n#define OPENGL\n" + code.substring(extensionEnd)
        }

        val optimisation = when(optimisationLevel) {
            OptimisationLevel.None -> Shaderc.shaderc_optimization_level_zero
            OptimisationLevel.Performance -> Shaderc.shaderc_optimization_level_performance
            OptimisationLevel.Size -> Shaderc.shaderc_optimization_level_size
        }

        Shaderc.shaderc_compile_options_set_optimization_level(options, optimisation)

        if(warningsAsErrors) {
            Shaderc.shaderc_compile_options_set_warnings_as_errors(options)
        }

        if(debug) {
            Shaderc.shaderc_compile_options_set_generate_debug_info(options)
            val extensionPos = shaderCode.indexOf("\n", shaderCode.indexOf("#version "))
            shaderCode = shaderCode.replaceRange(extensionPos, extensionPos + 1, "\n#extension GL_EXT_debug_printf : enable\n")
        }

        val shaderCodeBytes = MemoryUtil.memUTF8(shaderCode, false)
        val shaderPathBytes = MemoryUtil.memUTF8(path ?: "compile.glsl")
        val entryPointBytes = MemoryUtil.memUTF8(entryPoint)

        val result = Shaderc.shaderc_compile_into_spv(
            compiler,
            shaderCodeBytes,
            shaderType,
            shaderPathBytes,
            entryPointBytes,
            options
        )

        Shaderc.shaderc_compile_options_release(options)

        MemoryUtil.memFree(shaderCodeBytes)
        MemoryUtil.memFree(shaderPathBytes)
        MemoryUtil.memFree(entryPointBytes)

        if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
            val log = Shaderc.shaderc_result_get_error_message(result)
            logger.error("Error in shader compilation of $path for ${baseClass}: $log")
            logger.error("Shader code was: \n${shaderCode.split("\n").mapIndexed { index, s -> "${index+1}\t:  $s" }.joinToString("\n")}")
            throw ShaderCompilationException("Error compiling shader file $path")
        }

        val resultLength = Shaderc.shaderc_result_get_length(result)
        val resultBytes = Shaderc.shaderc_result_get_bytes(result)

        val bytecode = if (resultLength > 0 && resultBytes != null) {
            val array = ByteArray(resultLength.toInt())
            resultBytes.get(array)
            array
        } else {
            val log = Shaderc.shaderc_result_get_error_message(result)
            logger.error("Error in shader linking of $path for ${baseClass}: $log")
            throw ShaderCompilationException("Error compiling shader file $path, received zero-length bytecode")
        }

        logger.debug("Successfully compiled $path into bytecode (${(resultLength/4)} opcodes), with ${Shaderc.shaderc_result_get_num_warnings(result)} warnings and ${Shaderc.shaderc_result_get_num_errors(result)} errors.")

        Shaderc.shaderc_result_release(result)
        return bytecode
    }

    /**
     * Returns the version info for the shader compiler.
     */
    fun versionInfo(): String {
        val p = Package.getPackage("org.lwjgl.util.shaderc")
        return "shaderc / lwjgl ${p?.specificationVersion} ${p?.implementationVersion}"
    }

    /**
     * Closes this compiler instance, freeing up resouces.
     */
    override fun close() {
        Shaderc.shaderc_compiler_release(compiler)
    }

    /**
     * Hook function for picocli to be invoked on startup.
     */
    override fun call(): ByteArray {
        println(versionInfo())
        val out = if(!this::output.isInitialized) {
            file.resolveSibling(file.name + ".spv")
        } else {
            output
        }

        val type = when(file.name.substringAfterLast(".").lowercase()) {
            "vert" -> ShaderType.VertexShader
            "frag" -> ShaderType.FragmentShader
            "geom" -> ShaderType.GeometryShader
            "tesc" -> ShaderType.TessellationControlShader
            "tese" -> ShaderType.TessellationEvaluationShader
            "comp" -> ShaderType.ComputeShader
            else -> throw UnsupportedOperationException("Unknown shader type for ${file.name}.")
        }

        val level = when(optimise.lowercase()) {
            "p" -> OptimisationLevel.Performance
            "s" -> OptimisationLevel.Size
            else -> OptimisationLevel.None
        }

        val t = when(target.lowercase()) {
            "vulkan" -> Shaders.ShaderTarget.Vulkan
            "opengl" -> Shaders.ShaderTarget.OpenGL
            else -> throw UnsupportedOperationException("Unknown shader target $target.")
        }

        println("Compiling $file to $out, type $type, optimising for $level, target $t${if(debug) {", with debug information"} else { "" }}")
        val bytecode = compile(file.readText(), type, t, entryPoint, debug, strict, level, file.absolutePath, null)

        if(!out.exists()) {
            out.createNewFile()
        } else {
            out.writeBytes(bytecode)
        }

        return bytecode
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>): Unit = exitProcess(CommandLine(ShaderCompiler()).execute(*args))
    }
}
