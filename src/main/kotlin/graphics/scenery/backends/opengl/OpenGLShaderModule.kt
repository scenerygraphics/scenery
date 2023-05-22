package graphics.scenery.backends.opengl

import cleargl.GLShader
import cleargl.GLShaderType
import com.jogamp.opengl.GL4
import graphics.scenery.backends.ShaderIntrospection
import graphics.scenery.backends.ShaderPackage
import graphics.scenery.backends.ShaderType
import graphics.scenery.utils.lazyLogger
import java.util.concurrent.ConcurrentHashMap


/**
 * Vulkan Object State class. Saves texture, UBO, pipeline and vertex buffer state.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class OpenGLShaderModule(gl: GL4, entryPoint: String, sp: ShaderPackage) {
    protected val logger by lazyLogger()

    var shader: GLShader
        private set
    var shaderType: ShaderType
        private set
    var uboSpecs = LinkedHashMap<String, ShaderIntrospection.UBOSpec>()

    var source: String = ""
        private set

    init {

        logger.debug("Creating OpenGLShaderModule $entryPoint, ${sp.toShortString()}")

        val spirv = sp.getSPIRVOpcodes() ?: throw IllegalStateException("Shader Package is expected to have SPIRV bytecode at this point")

        val intro = ShaderIntrospection(spirv, vulkanSemantics = false, version = 410)

        logger.debug("Analysing uniform buffers ...")
        intro.uniformBuffers().forEach { ubo ->
            // only add the UBO spec if it doesn't already exist, and has more than 0 members
            // SPIRV UBOs may have 0 members, if they are not used in the actual shader code
            if(!uboSpecs.contains(ubo.name) && ubo.members.size > 0) {
                uboSpecs[ubo.name] = ubo
            }
        }

        /* Updated version:
       for(i in 0..compiler.shaderResources.sampledImages.size()-1) {
        // inputs are summarized into one descriptor set
            val res = compiler.shaderResources.sampledImages.get(i.toInt())
            logger.info("Adding textures ${res.name} with set=${compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet)}")

            // FIXME: Here we assume at the moment that we either have only input textures (from framebuffers), or only object textures
            val name = if(res.name == "ObjectTextures" || res.name == "VolumeTextures") {
                "ObjectTextures"
            } else {
                "inputs"
            }

            uboSpecs.put(res.name, UBOSpec(name,
                    set = compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet),
                    binding = 0,
                    members = LinkedHashMap<String, UBOMemberSpec>()))
         */
        // inputs are summarized into one descriptor set
        intro.sampledImages().forEach { sampledImage ->
            if (sampledImage.name != "ObjectTextures") {
                uboSpecs[sampledImage.name] = ShaderIntrospection.UBOSpec(
                    "inputs",
                    set = sampledImage.set,
                    binding = 0,
                    members = LinkedHashMap(),
                    type = ShaderIntrospection.UBOSpecType.SampledImage2D
                )
            }
        }

//        val inputs = compiler.shaderResources.stageInputs
//        if(inputs.capacity() > 0) {
//            for (i in 0 until inputs.capacity()) {
//                logger.debug("${sp.toShortString()}: ${inputs.get(i).name}")
//            }
//        }

        this.shaderType = sp.type

        source = intro.compile()
        // remove binding and set qualifiers
        var start = 0
        var found = source.indexOf("layout(", start)
        while(found != -1) {
            logger.debug("Found match at $found with start index $start")
            start = found + 7
            val end = source.indexOf(")", start) + 1

            if(source.substring(end, source.indexOf(";", end)).contains(" in ")) {
                if(source.substring(end, source.indexOf(";", end)).contains("{")) {
                    logger.debug("Removing layout qualifier from interface block input")
                    source = source.replaceRange(start-7, end, "")
                } else {
                    logger.debug("Not touching input layouts")
                }

                start = end
                found = source.indexOf("layout(", start)
                continue
            }

            if(source.substring(end, source.indexOf(";", end)).contains(" out ")) {
                if(source.substring(end, source.indexOf(";", end)).contains("{")) {
                    logger.debug("Removing layout qualifier from interface block output")
                    source = source.replaceRange(start-7, end, "")
                } else {
                    logger.debug("Not touching output layouts")
                }

                start = end
                found = source.indexOf("layout(", start)
                continue
            }

            if(source.substring(end, source.indexOf(";", end)).contains("sampler")) {
                logger.debug("Converting sampler UBO to uniform")
                source = source.replaceRange(start-7, end, "")
                start = found
                found = source.indexOf("layout(", start)
                continue
            }

            if(source.substring(end, source.indexOf(";", end)).contains("vec")) {
                logger.debug("Converting location-based struct to regular struct")
                source = source.replaceRange(start-7, end, "")
                start = found
                found = source.indexOf("layout(", start)
                continue
            }

            if(source.substring(end, source.indexOf(";", end)).contains("mat")) {
                logger.debug("Converting location-based struct to regular struct")
                source = source.replaceRange(start-7, end, "")
                start = found
                found = source.indexOf("layout(", start)
                continue
            }

            if(source.substring(start, end).contains("set") || source.substring(start, end).contains("binding")) {
                logger.debug("Replacing ${source.substring(start, end)}")
                source = source.replaceRange(start-7, end, "layout(std140)")
                start = found + 15
            } else {
                logger.debug("Skipping ${source.substring(start, end)}")
                start = end
            }

            found = source.indexOf("layout(", start)
        }

        // add GL_ARB_seperate_shader_objects extension to use layout(location = ...) qualifier
        source = source.replace("#version 410", "#version 410 core\n#extension GL_ARB_separate_shader_objects : require\n")

        this.shader = GLShader(gl, source, toClearGLShaderType(this.shaderType))

        if(this.shader.shaderInfoLog.isNotEmpty()) {
            logger.warn("Shader compilation log:")
            logger.warn(this.shader.shaderInfoLog)

            if(this.shader.shaderInfoLog.lowercase().contains("error")) {
                logger.error("Shader code follows:")
                logger.error("--------------------\n$source")
            }
        }
    }

    private fun toClearGLShaderType(type: ShaderType): GLShaderType {
        return when(type) {
            ShaderType.VertexShader -> GLShaderType.VertexShader
            ShaderType.FragmentShader -> GLShaderType.FragmentShader
            ShaderType.TessellationControlShader -> GLShaderType.TesselationControlShader
            ShaderType.TessellationEvaluationShader -> GLShaderType.TesselationEvaluationShader
            ShaderType.GeometryShader -> GLShaderType.GeometryShader
            ShaderType.ComputeShader -> GLShaderType.ComputeShader
        }
    }

    /**
     * Returns a string representation of this module.
     */
    override fun toString(): String {
        return "$shader: $shaderType with UBOs ${uboSpecs.keys.joinToString(", ") }}"
    }

    /**
     * Factory methods and cache.
     */
    companion object {
        private data class ShaderSignature(val gl: GL4, val p: ShaderPackage)
        private val shaderModuleCache = ConcurrentHashMap<ShaderSignature, OpenGLShaderModule>()

        /**
         * Creates a new [OpenGLShaderModule] or returns it from the cache.
         * Must be given a [ShaderPackage] [sp], a [gl], and the name for the main [entryPoint].
         */
        @JvmStatic fun getFromCacheOrCreate(gl: GL4, entryPoint: String, sp: ShaderPackage): OpenGLShaderModule {
            val signature = ShaderSignature(gl, sp)

            val module = shaderModuleCache[signature]
            return if(module != null) {
                module
            } else {
                val newModule = OpenGLShaderModule(gl, entryPoint, sp)
                shaderModuleCache[signature] = newModule

                newModule
            }
        }
    }
}
