package graphics.scenery.attribute.buffers

import graphics.scenery.attribute.buffers.Buffers
import graphics.scenery.backends.UBO
import java.nio.Buffer
import java.util.*
import kotlin.collections.LinkedHashMap

open class DefaultBuffers : Buffers {

    @Transient override var buffers : MutableMap<String, Buffers.BufferDescription> = LinkedHashMap()
    @Transient override var downloadRequests : MutableSet<String> = mutableSetOf()

    @Transient override var dirtySSBOs = false

    @Transient override var customVertexLayout: UBO? = null
}
