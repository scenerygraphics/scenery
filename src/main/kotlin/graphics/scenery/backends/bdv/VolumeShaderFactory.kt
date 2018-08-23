package graphics.scenery.backends.bdv

import graphics.scenery.backends.ShaderPackage
import graphics.scenery.backends.ShaderType
import graphics.scenery.backends.Shaders
import graphics.scenery.backends.SourceSPIRVPriority
import tpietzsch.shadergen.Shader

class VolumeShaderFactory : Shaders.ShaderFactory() {
    val shaders = HashMap<ShaderType, Shader>()

    fun updateShaders(newShaders: HashMap<ShaderType, Shader>) {
        newShaders.forEach { shaders[it.key] = it.value }
    }

    /**
     * Invoked by [get] to actually construct a [ShaderPackage].
     */
    override fun construct(target: ShaderTarget, type: ShaderType): ShaderPackage {
        val shader = shaders[type] ?: throw IllegalStateException("Shader type $type not found in factory")

        val code = when(type) {
            ShaderType.VertexShader -> String(shader.vertexShaderCode)
            ShaderType.FragmentShader -> String(shader.fragmentShaderCode)

            ShaderType.TessellationControlShader -> TODO()
            ShaderType.TessellationEvaluationShader -> TODO()
            ShaderType.GeometryShader -> TODO()
            ShaderType.ComputeShader -> TODO()
        }

        var sp = ShaderPackage(this.javaClass,
            type,
            "",
            "",
            byteArrayOf(),
            code,
            SourceSPIRVPriority.SourcePriority)

        sp = compile(sp, type, target, this.javaClass)
        return sp
    }
}
