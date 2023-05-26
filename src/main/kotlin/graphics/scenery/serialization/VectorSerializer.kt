package graphics.scenery.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import graphics.scenery.utils.lazyLogger
import org.joml.Vector3f

class Vector3fSerializer: Serializer<Vector3f>() {
    val logger by lazyLogger()
    override fun write(kryo: Kryo, output: Output, vector: Vector3f) {
        kryo.writeClassAndObject(output, floatArrayOf(vector.x, vector.y, vector.z))
//        logger.info("Serialized ${vector.x}/${vector.y}/${vector.z}")
    }

    override fun read(kryo: Kryo, input: Input, oobClass: Class<out Vector3f>): Vector3f {
        val arr = kryo.readClassAndObject(input) as FloatArray
        return Vector3f(arr[0], arr[1], arr[2])
    }

    init {
        // TODO: Should this be true or false?
        isImmutable = false
    }
}
