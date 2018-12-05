package graphics.scenery.volumes.bdv

import graphics.scenery.backends.*
import tpietzsch.shadergen.Shader

/**
 * Factory class to generate BigDataViewer volume shaders and convert them to scenery's conventions.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
open class VolumeShaderFactory : Shaders.ShaderFactory() {
    val shaders = HashMap<ShaderType, Triple<Shader, String, List<String>>>()
    val preprocessorStatements = ArrayList<String>()

    /**
     * Updates the current set of shaders with a new set given in [newShaders].
     */
    fun updateShaders(newShaders: HashMap<ShaderType, Shader>) {
        newShaders.forEach {
            val codeBefore = when(it.key) {
                ShaderType.VertexShader -> String(it.value.vertexShaderCode)
                ShaderType.FragmentShader -> String(it.value.fragmentShaderCode)

                ShaderType.TessellationControlShader -> TODO()
                ShaderType.TessellationEvaluationShader -> TODO()
                ShaderType.GeometryShader -> TODO()
                ShaderType.ComputeShader -> TODO()
            }

            val (code, uniforms) = convertToSceneryConventions(codeBefore)
            logger.info("Adding uniforms ${uniforms.joinToString(", ")}")

            shaders[it.key] = Triple(it.value, code, uniforms)
        }
    }

    /**
     * Invoked by [get] to actually construct a [ShaderPackage].
     */
    override fun construct(target: ShaderTarget, type: ShaderType): ShaderPackage {
        val shader = shaders[type] ?: throw ShaderNotFoundException("Shader type $type not found in factory")

        val codeBefore = when(type) {
            ShaderType.VertexShader -> String(shader.first.vertexShaderCode)
            ShaderType.FragmentShader -> String(shader.first.fragmentShaderCode)

            ShaderType.TessellationControlShader -> TODO()
            ShaderType.TessellationEvaluationShader -> TODO()
            ShaderType.GeometryShader -> TODO()
            ShaderType.ComputeShader -> TODO()
        }

        val unifiedUniforms = shaders.map { it.value.third }.flatten().toList()
        logger.info("Final Uniforms for $type: ${unifiedUniforms.joinToString(", ")}")
        val (code, uniforms) = convertToSceneryConventions(codeBefore, unifiedUniforms)

        shaders[type] = Triple(shader.first, code, uniforms)

        var sp = ShaderPackage(this.javaClass,
            type,
            "",
            "",
            null,
            code,
            SourceSPIRVPriority.SourcePriority)

        sp = compile(sp, type, target, this.javaClass)
        return sp
    }

    /**
     * Converts the [code] to scenery's conventions and returns it as String.
     */
    protected fun convertToSceneryConventions(code: String, predefinedUniforms: List<String> = emptyList()): Pair<String, List<String>> {
        val uniforms = ArrayList<String>()
        val inputs = ArrayList<String>()
        val outputs = ArrayList<String>()
        val samplers = ArrayList<String>()

        // convert in/out
        val converted = code.split("\n").mapNotNull { line ->
            when {
                line.contains("#define") || line.contains("#pragma") -> {
                    preprocessorStatements.add(line)
                    null
                }

                line.contains("uniform sampler1D") || line.contains("uniform sampler2D") || line.contains("uniform sampler3D")
                    || line.contains("uniform usampler1D") || line.contains("uniform usampler2D") || line.contains("uniform usampler3D") -> {
                    samplers.add(line)
                    null
                }

                line.contains("uniform ") -> {
                    val uniform = line.substringAfter("uniform ").trim().trimEnd()
                    uniforms.add(uniform)
                    null
                }

                /*line.contains(" in ") ||*/ line.startsWith("in ") -> {
                    inputs.add(line)
                    null
                }

                /*line.contains(" out ") ||*/ line.startsWith("out ") -> {
                    outputs.add(line)
                    null
                }

                else -> {
                    line
                }
            }
        }.joinToString("\n")

        val convertedInputs = inputs.mapIndexed { i, io ->
            if (!io.contains("layout")) {
                "layout (location = $i) $io"
            } else {
                io
            }
        }

        val convertedOutputs = outputs.mapIndexed { i, io ->
            if (!io.contains("layout")) {
                "layout (location = $i) $io"
            } else {
                io
            }
        }

        val convertedSamplers = samplers.mapIndexed { i, sampler ->
            "layout(set = ${1 + i}, binding = 0) $sampler"
        }

        val fullUniforms = uniforms.union(predefinedUniforms).sorted().toList()
        logger.debug("Adding ${uniforms.size} to uniforms struct (${fullUniforms.joinToString(", ")})")
        val convertedUniforms = if(fullUniforms.isNotEmpty()) {
            "// autogenerated ShaderProperties\n" +
            "layout(set = 0, binding = 0) uniform ShaderProperties {\n" +
                fullUniforms.map { "\t$it" }.joinToString("\n") + "\n" +
                "};\n"
        } else {
            "\n"
        }

        return listOf(
            "// preprocessor statements",
            preprocessorStatements.distinct().joinToString("\n"),
            "// VolumeShaderFactory: inputs",
            convertedInputs.joinToString("\n"),
            "// VolumeShaderFactory: outputs",
            convertedOutputs.joinToString("\n"),
            "// VolumeShaderFactory: uniforms",
            convertedUniforms,
            "// VolumeShaderFactory: samplers",
            convertedSamplers.joinToString("\n"),
            "// VolumeShaderFactory: main code ",
            converted).joinToString("\n") to fullUniforms
    }

    /**
     * Returns a String representation of this factory.
     */
    override fun toString(): String {
        return "VolumeShaderFactory-managed shaders:\n" + shaders.map {
            "${it.key}\n============: " + when(it.key) {
                ShaderType.VertexShader -> convertToSceneryConventions(it.value.first.vertexShaderCode.toString())
                ShaderType.TessellationControlShader -> it.value
                ShaderType.TessellationEvaluationShader -> it.value
                ShaderType.GeometryShader -> it.value
                ShaderType.FragmentShader -> convertToSceneryConventions(it.value.first.fragmentShaderCode.toString())
                ShaderType.ComputeShader -> it.value
            }
        }.joinToString("\n")
    }
}
