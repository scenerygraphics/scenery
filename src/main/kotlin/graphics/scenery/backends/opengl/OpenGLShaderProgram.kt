package graphics.scenery.backends.opengl

import cleargl.GLProgram
import cleargl.GLShader
import cleargl.GLShaderType
import cleargl.GLUniform
import com.jogamp.opengl.GL4
import gnu.trove.map.hash.THashMap
import gnu.trove.map.hash.TObjectIntHashMap
import graphics.scenery.utils.LazyLogger

class OpenGLShaderProgram(var gl: GL4, val modules: HashMap<GLShaderType, OpenGLShaderModule>) {
    private val logger by LazyLogger()

    var program: GLProgram
    val uboSpecs = LinkedHashMap<String, OpenGLShaderModule.UBOSpec>()
    private val blockIndices = THashMap<String, Int>()
    var id: Int

    init {
        val shaders = HashMap<GLShaderType, GLShader>()

        modules.forEach { type, module ->
            module.uboSpecs.forEach { uboName, ubo ->
                if(uboSpecs.containsKey(uboName)) {
                    uboSpecs[uboName]!!.members.putAll(ubo.members)
                } else {
                    uboSpecs.put(uboName, ubo)
                }
            }

            shaders.put(type, module.shader)
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
            logger.error("An error occured during linking.")
            -1
        } else {
            program.id
        }
    }

    fun isValid(): Boolean {
        return id > 0
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
            .map { it.name to it.offset.toInt() }
            .toMap()
//        val specs = shaderPropertiesSpec.map { it.members }.flatMap { it.entries }.map { it.key.to(it.value.offset) }
    }

    fun getUniformBlockIndex(name: String): Int {
        return blockIndices.getOrPut(name, {
            gl.glGetUniformBlockIndex(program.id, name)
        })
    }
}
