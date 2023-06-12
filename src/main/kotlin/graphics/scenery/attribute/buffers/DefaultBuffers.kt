package graphics.scenery.attribute.buffers

import graphics.scenery.attribute.buffers.Buffers
import java.nio.Buffer

open class DefaultBuffers : Buffers {

    @Transient override var buffers : MutableMap<String, Buffers.BufferDescription> = LinkedHashMap()

    @Transient override var dirtySSBOs = false
}
