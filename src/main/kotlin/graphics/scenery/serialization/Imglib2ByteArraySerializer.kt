package graphics.scenery.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.imglib2.img.basictypeaccess.array.ByteArray

class Imglib2ByteArraySerializer: Serializer<ByteArray>() {
    override fun write(kryo: Kryo, output: Output, array: ByteArray) {
        kryo.writeClass(output, array.javaClass)
        output.writeInt(array.arrayLength)
        output.writeBytes(array.currentStorageArray)
    }

    override fun read(kryo: Kryo, input: Input, tripleClass: Class<out ByteArray>): ByteArray {
        kryo.readClass(input)
        val size = input.readInt()
        val array = kotlin.ByteArray(size)
        input.readBytes(array, 0, size)
        return ByteArray(array)
    }

    init {
        // TODO: Should this be true or false?
        isImmutable = false
    }
}
