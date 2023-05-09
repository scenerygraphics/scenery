package graphics.scenery.attribute.buffers


import java.io.Serializable
import java.nio.Buffer

interface Buffers : Serializable {

    var buffers : MutableMap<String, Buffer>
    data class Description(var type : BufferType, var size : Int)
    var description : LinkedHashMap<String, Description>

    var dirtySSBOs : Boolean
}
