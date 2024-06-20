package graphics.scenery.attribute.buffers

import graphics.scenery.Node


interface HasCustomBuffers<T : Buffers> : Node {

    fun createBuffers() : T


    fun addBuffers() {
        addAttribute(Buffers::class.java, createBuffers())
    }

    fun addBuffers(block: T.() -> Unit) {
        addAttribute(Buffers::class.java, createBuffers(), block)
    }

    fun buffers(block: T.() -> Unit) : T {
        val props = buffers()
        props.block()
        return props
    }

    fun buffers() : T {
        return getAttribute(Buffers::class.java)
    }

    override fun ifBuffers(block : Buffers.() -> Unit) : T {
        return this.buffers(block)
    }

    override fun buffersOrNull() : T {
        return this.buffers()
    }

}
