package graphics.scenery.backends

import graphics.scenery.utils.LazyLogger
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
    private val logger by LazyLogger()

    protected val compiler = Shaderc.shaderc_compiler_initialize()
    protected val options = Shaderc.shaderc_compile_options_initialize()

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
        logger.debug("Compiling code from $path of $baseClass, debug=$debug, optimisation=$optimisationLevel")
        val shaderType = when (type) {
            ShaderType.VertexShader -> Shaderc.shaderc_glsl_vertex_shader
            ShaderType.FragmentShader -> Shaderc.shaderc_glsl_fragment_shader
            ShaderType.GeometryShader -> Shaderc.shaderc_glsl_geometry_shader
            ShaderType.TessellationControlShader -> Shaderc.shaderc_glsl_tess_control_shader
            ShaderType.TessellationEvaluationShader -> Shaderc.shaderc_glsl_tess_evaluation_shader
            ShaderType.ComputeShader -> Shaderc.shaderc_glsl_compute_shader
        }

        val shaderCode = if (target == Shaders.ShaderTarget.Vulkan) {
            code
        } else {
            val extensionEnd = code.indexOf("\n", code.findLastAnyOf(listOf("#extension", "#versions"))?.first ?: 0)
            code.substring(0, extensionEnd) + "\n#define OPENGL\n" + code.substring(extensionEnd)
        }

        if(debug) {
            Shaderc.shaderc_compile_options_set_generate_debug_info(options)
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

        val result = Shaderc.shaderc_compile_into_spv(
            compiler,
            shaderCode,
            shaderType,
            path ?: "compile.glsl",
            entryPoint,
            options
        )

        Shaderc.shaderc_result_get_compilation_status(result)

        val compileFail = Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success
        if (compileFail) {
            val log = Shaderc.shaderc_result_get_error_message(result)
            logger.error("Error in shader compilation of ${path} for ${baseClass}: $log")
            logger.error("Shader code was: \n${shaderCode.split("\n").mapIndexed { index, s -> "${index+1}\t:  $s" }.joinToString("\n")}")
        }

        val resultLength = Shaderc.shaderc_result_get_length(result)
        val resultBytes = Shaderc.shaderc_result_get_bytes(result)

        val bytecode = if (!compileFail && resultLength > 0 && resultBytes != null) {
            val array = ByteArray(resultLength.toInt())
            resultBytes.get(array)
            array
        } else {
            val log = Shaderc.shaderc_result_get_error_message(result)
            logger.error("Error in shader linking of ${path} for ${baseClass}: $log")
            throw ShaderCompilationException("Error compiling shader file ${path}")
        }

        Shaderc.shaderc_result_release(result)
        return bytecode
    }

    fun versionInfo(): String {
        val p = Package.getPackage("org.lwjgl.util.shaderc")
        return "shaderc / lwjgl ${p?.specificationVersion} ${p?.implementationVersion}"
    }

    override fun close() {
        Shaderc.shaderc_compile_options_release(options)
        Shaderc.shaderc_compiler_release(compiler)

    }

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
