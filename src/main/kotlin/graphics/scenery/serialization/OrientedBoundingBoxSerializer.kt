package graphics.scenery.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import graphics.scenery.Node
import graphics.scenery.OrientedBoundingBox
import org.joml.Vector3f

class OrientedBoundingBoxSerializer: Serializer<OrientedBoundingBox>() {
    override fun write(kryo: Kryo, output: Output, oob: OrientedBoundingBox) {
        kryo.writeClassAndObject(output, oob.n)
        kryo.writeClassAndObject(output, oob.min)
        kryo.writeClassAndObject(output, oob.max)
    }

    override fun read(kryo: Kryo, input: Input, oobClass: Class<out OrientedBoundingBox>): OrientedBoundingBox {
        val node = kryo.readClassAndObject(input) as Node
        val min = kryo.readClassAndObject(input) as Vector3f
        val max = kryo.readClassAndObject(input) as Vector3f
        return OrientedBoundingBox(node, min, max)
    }

    init {
        // TODO: Should this be true or false?
        isImmutable = false
    }
}
