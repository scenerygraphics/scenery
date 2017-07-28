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

    fun getShaderPropertyOrder(): Map<String, Int> {
        // this creates a shader property UBO for items marked @ShaderProperty in node
        val shaderPropertiesSpec = uboSpecs.filter { it.key == "ShaderProperties" }.map { it.value.members }

        if (shaderPropertiesSpec.count() == 0) {
            logger.error("Shader uses no declared shader properties!")
            return emptyMap()
        }

        return shaderPropertiesSpec
            .flatMap { it.values }
            .map { it.name.to(it.offset.toInt()) }
            .toMap()
//        val specs = shaderPropertiesSpec.map { it.members }.flatMap { it.entries }.map { it.key.to(it.value.offset) }
    }
}
