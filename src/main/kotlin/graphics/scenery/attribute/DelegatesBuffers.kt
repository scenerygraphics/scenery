package graphics.scenery.attribute

import graphics.scenery.Node

interface DelegatesBuffers : Node, HasDelegationType {

    fun getDelegateBuffers() : Buffers?


    override fun ifBuffers(block: Buffers.() -> Unit) : Buffers? {
        val delegateBuffers = getDelegateBuffers()
        delegateBuffers?.block()
        return delegateBuffers
    }
}
