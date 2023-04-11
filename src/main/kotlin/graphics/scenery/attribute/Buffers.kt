package graphics.scenery.attribute


import java.io.Serializable
import java.nio.ByteBuffer

interface Buffers : Serializable {

    var buffers : HashMap<BufferType, ByteBuffer>

    /*
        I guess do buffer setter and getter override here, using the TypeEnum to check to which kind of buffer it should get mapped (ByteBuffer as Float/Int...Buffer)

Concept:

        operator fun getValue(thisRef: Any?, property: KProperty<*>): ByteBuffer {
        return "$thisRef, thank you for delegating '${property.name}' to me!"
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: ByteBuffer) {
        when(ByteBuffer.Type) {
            Position, Normals, TexCoords -> return value as FloatBuffer
            Indices -> return value as IntBuffer
            Custom -> return value as BlankBuffer
            default -> "Throw something!"
        }
    }
     */
}
