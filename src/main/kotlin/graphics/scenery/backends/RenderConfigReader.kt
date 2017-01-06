package graphics.scenery.backends

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.management.monitor.StringMonitor

/**
 * Created by ulrik on 10/19/2016.
 */

fun RenderConfigReader.RenderConfig.getOutputOfPass(passname: String): String? {
    return this.renderpasses.get(passname)?.output
}

fun RenderConfigReader.RenderConfig.getInputsOfTarget(targetName: String): Set<String> {
    rendertargets?.let { rts ->
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
    var inputs: Set<String>? = start.value.inputs
    dag.add(start.key)

    while(inputs != null) {
        val current = passes.filter { it.value.output == inputs!!.first() }.entries.first()
        if(current.value.inputs == null) {
            inputs = null
        } else {
            inputs = current.value.inputs!!
        }

        dag.add(current.key)
    }

    return dag.reversed()
}

class RenderConfigReader {

    class FloatPairDeserializer : JsonDeserializer<Pair<Float, Float>>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): Pair<Float, Float> {
            val pair = p.text.split(",").map { it.trim().trimStart().toFloat() }

            return Pair(pair[0], pair[1])
        }
    }

    data class RenderConfig(
        var name: String,
        var description: String?,
        var rendertargets: Map<String, Map<String, AttachmentConfig>>?,
        var renderpasses: Map<String, RenderpassConfig>)

    data class AttachmentConfig(
        @JsonDeserialize(using = FloatPairDeserializer::class) var size: Pair<Float, Float>,
        var format: TargetFormat)

    data class RenderpassConfig(
        var type: RenderpassType,
        var shaders: Set<String>,
        var inputs: Set<String>?,
        var output: String,
        var parameters: Map<String, Any>?
    )

    enum class RenderpassType { geometry, quad }

    enum class TargetFormat {
        RGBA_Float32,
        RGBA_Float16,
        RGB_Float32,
        RGB_Float16,
        RG_Float32,
        RG_Float16,
        Depth24,
        Depth32,
        RGBA_UInt8,
        RGBA_UInt16
    }

    fun loadFromFile(path: String): RenderConfig {
        val p = Paths.get(this.javaClass.getResource(path).toURI())

        val mapper = ObjectMapper(YAMLFactory())
        mapper.registerModule(KotlinModule())

        return Files.newBufferedReader(p).use {
            mapper.readValue(it, RenderConfig::class.java)
        }
    }

}
