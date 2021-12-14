package graphics.scenery.backends.opengl

import cleargl.GLProgram
import cleargl.GLShader
import cleargl.GLShaderType
import cleargl.GLUniform
import com.jogamp.opengl.GL4
import gnu.trove.map.hash.THashMap
import graphics.scenery.backends.ShaderIntrospection
import graphics.scenery.backends.ShaderType
import graphics.scenery.utils.LazyLogger

/**
 * Class to handle OpenGL shader programs, for a context [gl], consisting of [OpenGLShaderModule] [modules].
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
open class OpenGLShaderProgram(var gl: GL4, val modules: HashMap<ShaderType, OpenGLShaderModule>) {
    private val logger by LazyLogger()

    /** The ClearGL program object */
    var program: GLProgram
    /** UBO specifications defined by the compiled shader modules. */
    val uboSpecs = LinkedHashMap<String, ShaderIntrospection.UBOSpec>()
    private val blockIndices = THashMap<String, Int>()

    /** THe OpenGL-internal id of this shader program */
    var id: Int
        protected set

    init {
        val shaders = HashMap<GLShaderType, GLShader>()

        modules.forEach { type, module ->
            module.uboSpecs.forEach { uboName, ubo ->
                val spec = uboSpecs[uboName]
                if(spec != null) {
                    spec.members.putAll(ubo.members)
                } else {
                    uboSpecs[uboName] = ubo
                }
            }

            shaders[type.toClearGLShaderType()] = module.shader
        }

        logger.debug("Creating shader program from ${modules.keys.joinToString(", ")}")

        program = GLProgram(gl, shaders)
        if(program.programInfoLog.isNotEmpty()) {
            logger.warn("There was an issue linking the following shaders:")
            logger.warn("Error produced: ${program.programInfoLog}")

            modules.forEach { shaderType, m ->
                logger.warn("$shaderType: ${m.source}")
            }
        }

        val result = intArrayOf(0)
        gl.glGetProgramiv(program.id, GL4.GL_LINK_STATUS, result, 0)

        id = if(result[0] != GL4.GL_TRUE) {
            logger.error("An error occurred during linking.")
            -1
        } else {
            program.id
        }
    }

    /**
     * Returns true if this shader program has an id > 0, which means
     * linking was successful, and the program is ready for use.
     */
    fun isValid(): Boolean {
        return id > 0
    }

    /**
     * Attaches this shader program for usage.
     */
    fun use(gl: GL4) {
        program.use(gl)
    }

    /**.
     * Returns the [GLUniform] associated with [name].
     */
    fun getUniform(name: String): GLUniform {
        return program.getUniform(name)
    }

    /**
     * Returns the [graphics.scenery.ShaderProperty]s of this program in the order required by
     * the shader/the uniform buffer.
     */
    fun getShaderPropertyOrder(): Map<String, Int> {
        // this creates a shader property UBO for items marked @ShaderProperty in node
        val shaderPropertiesSpec = uboSpecs.filter { it.key == "ShaderProperties" }.map { it.value.members }

        if (shaderPropertiesSpec.count() == 0) {
            logger.error("Shader uses no declared shader properties!")
            return emptyMap()
        }

        return shaderPropertiesSpec
            .flatMap { it.values }
            .map { it.name to it.offset.toInt() }
            .toMap()
//        val specs = shaderPropertiesSpec.map { it.members }.flatMap { it.entries }.map { it.key.to(it.value.offset) }
    }

    /**
     * Caches and returns the uniform block index associated with [name].
     * This information needs to be cached, because especially on macOS, the OpenGL API
     * call is horribly slow.
     */
    fun getUniformBlockIndex(name: String): Int {
        return blockIndices.getOrPut(name) {
            gl.glGetUniformBlockIndex(program.id, name)
        }
    }

    private fun ShaderType.toClearGLShaderType(): GLShaderType {
        return when(this) {
            ShaderType.VertexShader -> GLShaderType.VertexShader
            ShaderType.FragmentShader -> GLShaderType.FragmentShader
            ShaderType.TessellationControlShader -> GLShaderType.TesselationControlShader
            ShaderType.TessellationEvaluationShader -> GLShaderType.TesselationEvaluationShader
            ShaderType.GeometryShader -> GLShaderType.GeometryShader
            ShaderType.ComputeShader -> GLShaderType.ComputeShader
        }
    }
}
