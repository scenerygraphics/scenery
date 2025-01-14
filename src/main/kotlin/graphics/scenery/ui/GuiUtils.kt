package graphics.scenery.ui

import graphics.scenery.Node
import graphics.scenery.attribute.material.HasMaterial
import graphics.scenery.controls.behaviours.Touchable
import org.joml.Vector3f

/**
 * Remove node from parent if there is one.
 */
fun Node.detach() {
    this.parent?.removeChild(this)
}

fun HasMaterial.changeColorWithTouchable(newColor: Vector3f) {
    val touch = this.getAttributeOrNull(Touchable::class.java)
    if (touch?.originalDiffuse != null) {
        // this might screw with [VRTouch]s coloring
        touch.originalDiffuse = newColor
    } else {
        this.material().diffuse = newColor
    }
}
