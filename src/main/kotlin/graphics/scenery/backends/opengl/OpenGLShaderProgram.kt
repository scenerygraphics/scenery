package graphics.scenery.backends.opengl

import cleargl.GLProgram
import cleargl.GLShader
import cleargl.GLShaderType
import cleargl.GLUniform
import com.jogamp.opengl.GL4
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OpenGLShaderProgram(var gl: GL4, val modules: HashMap<GLShaderType, OpenGLShaderModule>) {
    var program: GLProgram
    val logger: Logger = LoggerFactory.getLogger("OpenGLRenderer")
    val uboSpecs = LinkedHashMap<String, OpenGLShaderModule.UBOSpec>()
    var id: Int

    init {
        val shaders = HashMap<GLShaderType, GLShader>()

        modules.forEach { type, module ->
            uboSpecs.putAll(module.uboSpecs)
            shaders.put(type, module.shader)
        }

        logger.debug("Creating shader program from ${modules.keys.joinToString(", ")}")

        program = GLProgram(gl, shaders)
        logger.info(program.programInfoLog)

        id = program.id
    }

    fun use(gl: GL4) {
        program.use(gl)
    }

    fun getUniform(name: String): GLUniform {
        return program.getUniform(name)
    }

    fun getShaderPropertyOrder(): List<String> {
        // this creates a shader property UBO for items marked @ShaderProperty in node
        val shaderPropertiesSpec = uboSpecs.filter { it.key == "ShaderProperties" }.values

        if (shaderPropertiesSpec.count() == 0) {
            logger.error("Shader uses no declared shader properties!")
            return emptyList()
        }

        val specs = shaderPropertiesSpec.map { it.members }.flatMap { it.keys }

        // returns a ordered list of the members of the ShaderProperties struct
        return specs.toList()
    }
}
