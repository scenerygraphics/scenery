package graphics.scenery.attribute


import java.io.Serializable
import java.nio.Buffer

interface Buffers : Serializable {

    var buffers : MutableMap<String, Buffer> //String instead of BufferType?
    var description : LinkedHashMap<String, Description>

    data class Description(var type : BufferType, var size : Int)
}
