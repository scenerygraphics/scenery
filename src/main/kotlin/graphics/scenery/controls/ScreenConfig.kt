package graphics.scenery.controls

import cleargl.GLVector
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ScreenConfig {
    var logger: Logger = LoggerFactory.getLogger("ScreenConfig")

    class VectorDeserializer : JsonDeserializer<GLVector>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): GLVector {
            val floats = p.text.split(",").map { it.trim().trimStart().toFloat() }.toFloatArray()

            return GLVector(*floats)
        }
    }

    data class Config(
        var name: String,
        var description: String?,

        var screens: Map<String, SingleScreenConfig>
    )


    data class SingleScreenConfig(
        var match: ScreenMatcher,

        @JsonDeserialize(using = VectorDeserializer::class)
        var lowerLeft: GLVector = GLVector(0.0f, 0.0f, 0.0f),
        @JsonDeserialize(using = VectorDeserializer::class)
        var lowerRight: GLVector = GLVector(0.0f, 0.0f, 0.0f),
        @JsonDeserialize(using = VectorDeserializer::class)
        var upperLeft: GLVector = GLVector(0.0f, 0.0f, 0.0f)
    )

    data class ScreenMatcher(
        var type: ScreenMatcherType,
        var value: String
    )

    enum class ScreenMatcherType { property, hostname }


    companion object {
        var logger: Logger = LoggerFactory.getLogger("ScreenConfig")
        fun getScreen(config: Config): SingleScreenConfig? {
            for ((name, screen) in config.screens) {
                if (screen.match.type == ScreenMatcherType.hostname) {
                    if (getHostname().toLowerCase() == screen.match.value) {
                        return screen
                    }
                }

                if (screen.match.type == ScreenMatcherType.property) {
                    if (System.getProperty("scenery.ScreenName") == screen.match.value) {
                        return screen
                    }
                }
            }

            logger.warn("No matching screen found.")
            return null
        }

        private fun getHostname(): String {
            val proc = Runtime.getRuntime().exec("hostname")
            proc.inputStream.use({ stream -> Scanner(stream).useDelimiter("\\A").use({ s -> return if (s.hasNext()) s.next() else "" }) })
        }

        fun loadFromFile(path: String): ScreenConfig.Config {
            val mapper = ObjectMapper(YAMLFactory())
            mapper.registerModule(KotlinModule())

            var stream = this.javaClass.getResourceAsStream(path)

            if (stream == null) {
                val p = Paths.get(path)

                return if (!Files.exists(p)) {
                    stream = this.javaClass.getResourceAsStream("CAVEExample.yml")
                    mapper.readValue(stream, ScreenConfig.Config::class.java)
                } else {
                    Files.newBufferedReader(p).use {
                        mapper.readValue(it, ScreenConfig.Config::class.java)
                    }
                }
            }

            return mapper.readValue(stream, ScreenConfig.Config::class.java)
        }
    }
}
