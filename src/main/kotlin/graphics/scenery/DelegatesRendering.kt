package graphics.scenery

open class DelegatesRendering(initialDelegate: Node? = null): Node("DelegatesRendering") {
    var delegate: Node? = null

    init {
        delegate = initialDelegate
    }
}
