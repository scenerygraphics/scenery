package graphics.scenery.attribute

interface HasDelegationType {
    fun getDelegationType(): DelegationType {
        return DelegationType.OncePerDelegate
    }
}
