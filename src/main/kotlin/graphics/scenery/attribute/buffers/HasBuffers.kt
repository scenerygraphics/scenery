package graphics.scenery.attribute.buffers

interface HasBuffers : HasCustomBuffers<Buffers> {

    override fun createBuffers() : Buffers {
        return DefaultBuffers()
    }

}
