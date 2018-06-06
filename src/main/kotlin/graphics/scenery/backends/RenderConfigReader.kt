package graphics.scenery.backends

import cleargl.GLVector
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import graphics.scenery.Blending
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

/**
 * Class to ingest rendering configuration files.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */

fun RenderConfigReader.RenderConfig.getOutputOfPass(passname: String): String? {
    return this.renderpasses.get(passname)?.output
}

fun RenderConfigReader.RenderConfig.getInputsOfTarget(targetName: String): Set<String> {
    rendertargets.let { rts ->
        return rts.filter {
            it.key == renderpasses.filter { it.value.output == targetName }.keys.first()
        }.keys
    }

    return setOf()
}

fun RenderConfigReader.RenderConfig.createRenderpassFlow(): List<String> {
    val passes = this.renderpasses
    val dag = ArrayList<String>()

    // find first
    val start = passes.filter { it.value.output == "Viewport" }.entries.first()
    var inputs: List<String>? = start.value.inputs
    dag.add(start.key)

    while(inputs != null) {
//        passes.filter { it.value.output == inputs!!.first().substringBefore(".") }.entries.forEach {
        passes.filter { inputs!!.map { it.substringBefore(".") }.contains(it.value.output) }.entries.forEach {
            inputs = if (it.value.inputs == null) {
                null
            } else {
                it.value.inputs!!
            }

            dag.add(it.key.substringBefore("."))
        }
    }

    return dag.reversed().toSet().toList()
}

class RenderConfigReader {

    /**
     * Deserialiser for pairs of floats, separated by commas.
     */
    class FloatPairDeserializer : JsonDeserializer<Pair<Float, Float>>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): Pair<Float, Float> {
            val pair = p.text.split(",").map { it.trim().trimStart().toFloat() }

            return Pair(pair[0], pair[1])
        }
    }

    /**
     * Deserialiser for vectors of various lengths, separated by commas.
     */
    class VectorDeserializer : JsonDeserializer<GLVector>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): GLVector {
            val floats = p.text.split(",").map { it.trim().trimStart().toFloat() }.toFloatArray()

            return GLVector(*floats)
        }
    }

    /**
     * Eye description deserialiser, turns "LeftEye" to 0, "RightEye" to 1
     */
    class VREyeDeserializer : JsonDeserializer<Int>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): Int {
            return when(p.text.trim().trimEnd()) {
                "LeftEye" -> 0
                "RightEye" -> 1
                else -> -1
            }
        }
    }

    data class RenderConfig(
        var name: String,
        var sRGB: Boolean = false,
        var description: String?,
        var stereoEnabled: Boolean = false,
        var rendertargets: Map<String, RendertargetConfig> = emptyMap(),
        var renderpasses: Map<String, RenderpassConfig>,
        var qualitySettings: Map<RenderingQuality, Map<String, Any>> = emptyMap())

    data class RendertargetConfig(
        @JsonDeserialize(using = FloatPairDeserializer::class) var size: Pair<Float, Float> = Pair(1.0f, 1.0f),
        val attachments: Map<String, TargetFormat> = emptyMap()
    )

    data class RenderpassConfig(
        var type: RenderpassType,
        var blitInputs: Boolean = false,
        var renderTransparent: Boolean = false,
        var depthTestEnabled: Boolean = true,
        var depthWriteEnabled: Boolean = true,
        var order: RenderOrder = RenderOrder.BackToFront,
        var renderOpaque: Boolean = true,
        var colorBlendOp: Blending.BlendOp = Blending.BlendOp.add,
        var alphaBlendOp: Blending.BlendOp = Blending.BlendOp.add,
        var srcColorBlendFactor: Blending.BlendFactor = Blending.BlendFactor.SrcAlpha,
        var dstColorBlendFactor: Blending.BlendFactor = Blending.BlendFactor.OneMinusSrcAlpha,
        var srcAlphaBlendFactor: Blending.BlendFactor = Blending.BlendFactor.SrcAlpha,
        var dstAlphaBlendFactor: Blending.BlendFactor = Blending.BlendFactor.OneMinusSrcAlpha,
        var shaders: List<String>,
        var inputs: List<String>?,
        var output: String,
        var parameters: Map<String, Any>?,
        @JsonDeserialize(using = FloatPairDeserializer::class) var viewportSize: Pair<Float, Float> = Pair(1.0f, 1.0f),
        @JsonDeserialize(using = FloatPairDeserializer::class) var viewportOffset: Pair<Float, Float> = Pair(0.0f, 0.0f),
        @JsonDeserialize(using = FloatPairDeserializer::class) var scissor: Pair<Float, Float> = Pair(1.0f, 1.0f),
        @JsonDeserialize(using = VectorDeserializer::class) var clearColor: GLVector = GLVector(0.0f, 0.0f, 0.0f, 0.0f),
        var depthClearValue: Float = 1.0f,
        @JsonDeserialize(using = VREyeDeserializer::class) var eye: Int = -1
    )

    enum class RenderingQuality { Low, Medium, High, Ultra }

    enum class RenderpassType { geometry, quad, lights }

    enum class RenderOrder { DontCare, BackToFront, FrontToBack }

    enum class TargetFormat {
        RGBA_Float32,
        RGBA_Float16,
        RGB_Float32,
        RGB_Float16,
        RG_Float32,
        RG_Float16,
        R_Float16,
        Depth24,
        Depth32,
        RGBA_UInt8,
        RGBA_UInt16,
        R_UInt16,
        R_UInt8
    }

    fun loadFromFile(path: String): RenderConfig {
        val mapper = ObjectMapper(YAMLFactory())
        mapper.registerModule(KotlinModule())

        var stream = this.javaClass.getResourceAsStream(path)

        if (stream == null) {
            val p = Paths.get(path)

            return if (!Files.exists(p)) {
                stream = this.javaClass.getResourceAsStream("DeferredShading.yml")
                mapper.readValue(stream, RenderConfig::class.java)
            } else {
                Files.newBufferedReader(p).use {
                    mapper.readValue(it, RenderConfig::class.java)
                }
            }
        }

        return mapper.readValue(stream, RenderConfig::class.java)
    }
}
