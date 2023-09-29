package graphics.scenery.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output

class TripleSerializer: Serializer<Triple<*, *, *>>() {
    override fun write(kryo: Kryo, output: Output, triple: Triple<*, *, *>) {
        kryo.writeClassAndObject(output, triple.first)
        kryo.writeClassAndObject(output, triple.second)
        kryo.writeClassAndObject(output, triple.third)
    }

    override fun read(kryo: Kryo, input: Input, tripleClass: Class<out Triple<*, *, *>>): Triple<*, *, *> {
        val first = kryo.readClassAndObject(input)
        val second = kryo.readClassAndObject(input)
        val third = kryo.readClassAndObject(input)
        return Triple(first, second, third)
    }

    init {
        // TODO: Should this be true or false?
        isImmutable = false
    }
}
