package graphics.scenery.utils

import cleargl.GLVector
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

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
    class VectorDeserializer : JsonDeserializer<GLVector>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): GLVector {
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

            return GLVector(*floats)
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
                else -> -1
            }
        }
    }
}
