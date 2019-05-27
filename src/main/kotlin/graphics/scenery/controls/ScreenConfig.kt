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
import graphics.scenery.utils.JsonDeserialisers
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
    /**
     * Represents a screen configuration, potentially with multiple [screens] with a
     * shared size of [screenWidth] x [screenHeight]. A [name] and [description] can be given.
     */
    data class Config(
        var name: String,
        var description: String?,
        var screenWidth: Int = 1920,
        var screenHeight: Int = 1200,

        var screens: Map<String, SingleScreenConfig>
    )


    /** Represents a single screen in the configuration */
    class SingleScreenConfig(
        /** How to match this screen (e.g. by host or IP) */
        var match: ScreenMatcher,

        /** Lower left screen corner, in meters */
        @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class)
        var lowerLeft: GLVector = GLVector(0.0f, 0.0f, 0.0f),

        /** Lower right screen corner, in meters */
        @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class)
        var lowerRight: GLVector = GLVector(0.0f, 0.0f, 0.0f),

        /** Upper left screen corner, in meters */
        @JsonDeserialize(using = JsonDeserialisers.VectorDeserializer::class)
        var upperLeft: GLVector = GLVector(0.0f, 0.0f, 0.0f)
    ) {
        private var screenTransform: GLMatrix

        /** Calculated width of the screen, in meters */
        var width = 0.0f
            private set

        /** Calculated height of the screen, in meters */
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

        /** Returns the frustum transform for this screen */
        fun getTransform(): GLMatrix = screenTransform
    }

    /**
     * Screen matcher class with [type] and [value].
     */
    data class ScreenMatcher(
        var type: ScreenMatcherType,
        var value: String
    )

    /** A screen matcher can be based on a system property or a hostname currently. */
    enum class ScreenMatcherType { Property, Hostname }


    /**
     * ScreenConfig companion class for static functions
     */
    companion object {
        private val logger by LazyLogger()

        /**
         * Matches a single screen to the [config] given.
         *
         * Returns a [SingleScreenConfig] if the screen could be matched, and null otherwise.
         */
        @JvmStatic fun getScreen(config: Config): SingleScreenConfig? {
            for ((_, screen) in config.screens) {
                if (screen.match.type == ScreenMatcherType.Hostname) {
                    if (getHostname().toLowerCase() == screen.match.value) {
                        return screen
                    }
                }

                if (screen.match.type == ScreenMatcherType.Property) {
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
            proc.inputStream.use { stream -> Scanner(stream).useDelimiter("\\A").use { s -> return if (s.hasNext()) s.next() else "" } }
        }

        /**
         * Loads a [ScreenConfig.Config] from a [path] and returns it.
         *
         * If [path] cannot be found, a default configuration included with scenery will be loaded.
         */
        @JvmStatic fun loadFromFile(path: String): ScreenConfig.Config {
            val mapper = ObjectMapper(YAMLFactory())
            mapper.registerModule(KotlinModule())

            var stream = this::class.java.getResourceAsStream(path)

            if (stream == null) {
                val p = Paths.get(path)

                return if (!Files.exists(p)) {
                    stream = this::class.java.getResourceAsStream("CAVEExample.yml")
                    logger.warn("Screen configuration not found at $path, returning default configuration.")
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
