package graphics.scenery.utils

import org.joml.Vector3f
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.RendertargetBinding
import org.joml.Vector2f
import org.joml.Vector4f

/**
 * A collection of deserialisers to use with Jackson.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class JsonDeserialisers {
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
    class VectorDeserializer : JsonDeserializer<Any>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): Any {
            val text = if(p.currentToken == JsonToken.START_ARRAY) {
                var token = p.nextToken()
                var result = ""
                while(token != JsonToken.END_ARRAY) {
                    result += p.text
                    token = p.nextToken()
                    if(token != JsonToken.END_ARRAY) {
                        result += ", "
                    }
                }
                result
            } else {
                p.text
            }

            val floats = text.split(",").map { it.trim().trimStart().toFloat() }.toFloatArray()

            return when(floats.size) {
                2 -> Vector2f(floats[0], floats[1])
                3 -> Vector3f(floats[0], floats[1], floats[2])
                4 -> Vector4f(floats[0], floats[1], floats[2], floats[3])
                else -> throw IllegalStateException("Don't know how to deserialise a vector of dimension ${floats.size}")
            }
        }
    }

    /**
     * Eye description deserialiser, turns "LeftEye" to 0, "RightEye" to 1
     */
    class VREyeDeserializer : JsonDeserializer<Int>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): Int {
            return when (p.text.trim().trimEnd()) {
                "LeftEye" -> 0
                "RightEye" -> 1
                else -> 0
            }
        }
    }

    /**
     * Deserializer for input/output bindings
     */
    class BindingDeserializer : JsonDeserializer<RendertargetBinding>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): RendertargetBinding {
            val tokens = p.text.split(" as ").map { it.trim() }
            return when(tokens.size) {
                1 -> RendertargetBinding(tokens[0], tokens[0])
                2 -> RendertargetBinding(tokens[0], tokens[1])
                else -> throw IllegalStateException("Could not parse ${p.text} into rendertarget binding. Format required: 'renderTargetName as nameInShader'")
            }
        }
    }
}
