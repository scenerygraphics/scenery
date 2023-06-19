package graphics.scenery

inline operator fun <M : Mesh> M.invoke(init: M.() -> Unit): M {
    apply(init)
    return this
}

