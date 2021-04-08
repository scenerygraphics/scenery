package graphics.scenery.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output

class IgnoreSerializer<T: Any>: Serializer<T>() {
    override fun write(kryo: Kryo, output: Output, obj: T) {
    }

    override fun read(kryo: Kryo, input: Input, tripleClass: Class<out T>): T? {
        return null
    }

    init {
        // TODO: Should this be true or false?
        isImmutable = false
    }
}
