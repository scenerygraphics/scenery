package graphics.scenery.backends

enum class RendererFlags {
    Seen,
    Initialised,
    Updating,
    Updated,
    PreDrawSkip,
    Instanced,
    Rendering,
    MarkedForDeletion
}
