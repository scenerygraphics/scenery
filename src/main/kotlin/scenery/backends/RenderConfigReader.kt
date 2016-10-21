package scenery.backends

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Created by ulrik on 10/19/2016.
 */
class RenderConfigReader {

    class FloatPairDeserializer : JsonDeserializer<Pair<Float, Float>>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): Pair<Float, Float> {
            val pair = p.text.split(",").map { it.trim().trimStart().toFloat() }

            return Pair(pair[0], pair[1])
        }
    }

    data class RenderConfig(var name: String, var description: String?, var rendertargets: Map<String, Map<String, AttachmentConfig>>?, var renderpasses: Map<String, RenderpassConfig>)

    data class AttachmentConfig(@JsonDeserialize(using = FloatPairDeserializer::class) var size: Pair<Float, Float>, var format: TargetFormat)

    data class RenderpassConfig(var type: RenderpassType, var shaders: Set<String>, var inputs: Set<String>?, var output: String)

    enum class RenderpassType { geometry, quad }

    enum class TargetFormat { RGBA_Float32, RGBA_Float16, Depth24, Depth32, RGBA_UInt8, RGBA_UInt16, RGBA_UInt32}

    fun loadFromFile(path: String): RenderConfig {
        val p = Paths.get(this.javaClass.getResource(path).toURI())

        val mapper = ObjectMapper(YAMLFactory())
        mapper.registerModule(KotlinModule())

        return Files.newBufferedReader(p).use {
            mapper.readValue(it, RenderConfig::class.java)
        }
    }
}
