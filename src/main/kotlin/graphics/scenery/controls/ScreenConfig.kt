package graphics.scenery.controls

import cleargl.GLMatrix
import cleargl.GLVector
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import graphics.scenery.utils.LazyLogger
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

/**
 * Screen config class, for configuration and use of projection screen geometry and transformations.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ScreenConfig {
    private val logger by LazyLogger()

    class VectorDeserializer : JsonDeserializer<GLVector>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): GLVector {
            val floats = p.text.split(",").map { it.trim().trimStart().toFloat() }.toFloatArray()

            return GLVector(*floats)
        }
    }

    data class Config(
        var name: String,
        var description: String?,
        var screenWidth: Int = 1920,
        var screenHeight: Int = 1200,

        var screens: Map<String, SingleScreenConfig>
    )


    class SingleScreenConfig(
        var match: ScreenMatcher,

        @JsonDeserialize(using = VectorDeserializer::class)
        var lowerLeft: GLVector = GLVector(0.0f, 0.0f, 0.0f),
        @JsonDeserialize(using = VectorDeserializer::class)
        var lowerRight: GLVector = GLVector(0.0f, 0.0f, 0.0f),
        @JsonDeserialize(using = VectorDeserializer::class)
        var upperLeft: GLVector = GLVector(0.0f, 0.0f, 0.0f)
    ) {
        private var screenTransform: GLMatrix

        var width = 0.0f
            private set

        var height = 0.0f
            private set

        init {
            var vr = lowerRight.minus(lowerLeft)
            val vu = upperLeft.minus(lowerLeft)
            val vn = vr.cross(vu)

            width = vr.magnitude()
            height = vu.magnitude()

            vu.normalize()
            vn.normalize()

            vr = vu.cross(vn).normalize()

            screenTransform = GLMatrix(floatArrayOf(
                vr.x(), vr.y(), vr.z(), 0.0f,
                vu.x(), vu.y(), vu.z(), 0.0f,
                vn.x(), vn.y(), vn.z(), 0.0f,
                lowerLeft.x(), lowerLeft.y(), lowerLeft.z(), 1.0f))

            logger.debug("Screen {}: {} {} {} {}x{}", match, lowerLeft, lowerRight, upperLeft, width, height)

            screenTransform.invert()
        }

        fun getTransform(): GLMatrix = screenTransform
    }

    data class ScreenMatcher(
        var type: ScreenMatcherType,
        var value: String
    )

    enum class ScreenMatcherType { property, hostname }


    companion object {
        private val logger by LazyLogger()

        @JvmStatic fun getScreen(config: Config): SingleScreenConfig? {
            for ((_, screen) in config.screens) {
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

        @JvmStatic fun loadFromFile(path: String): ScreenConfig.Config {
            val mapper = ObjectMapper(YAMLFactory())
            mapper.registerModule(KotlinModule())

            var stream = this::class.java.getResourceAsStream(path)

            if (stream == null) {
                val p = Paths.get(path)

                return if (!Files.exists(p)) {
                    stream = this::class.java.getResourceAsStream("CAVEExample.yml")
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
