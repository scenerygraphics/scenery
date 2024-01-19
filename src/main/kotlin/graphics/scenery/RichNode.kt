package graphics.scenery

import graphics.scenery.attribute.renderable.HasRenderable
import graphics.scenery.attribute.material.HasMaterial
import graphics.scenery.attribute.spatial.HasSpatial
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.Volume.Companion.fromPathRawSplit
import org.joml.Vector3f

open class RichNode(override var name: String = "Node") : DefaultNode (name), HasRenderable, HasMaterial, HasSpatial {
    init {
        addRenderable()
        addMaterial()
        addSpatial()
    }
}
