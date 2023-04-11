package graphics.scenery.attribute

interface HasBuffers : HasCustomBuffers<Buffers> {

    override fun createBuffers() : Buffers {
        return DefaultBuffers(this)
    }

}
