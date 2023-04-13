package graphics.scenery.attribute

import java.nio.Buffer

open class DefaultBuffers : Buffers {

    @Transient override var buffers : MutableMap<String, Buffer> = LinkedHashMap()
    @Transient override var description: LinkedHashMap<String, Buffers.Description> = LinkedHashMap()
}
