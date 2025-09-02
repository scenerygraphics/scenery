package graphics.scenery.ui

import graphics.scenery.attribute.geometry.HasGeometry
import graphics.scenery.attribute.spatial.HasSpatial
import graphics.scenery.controls.behaviours.Grabable

/**
 * REMEMBER TO [initGrabable]
 * Anchor point is usually front bottom left corner for elements and front bottom middle for collections
 *  @author Jan Tiemann
 */
interface Gui3DElement: HasSpatial {
    /**
     * Assuming no scaling
     */
    val width: Float

    /**
     * Assuming no scaling
     */
    val height: Float

    /**
     * Sets a grabable that moves the root [VR3DGui] instead and saves the last position as menu offset
     */
    fun initGrabable(node: HasGeometry) {
        var grab: Grabable? = null
        grab = Grabable(onGrab = {
            var cur = node.parent
            while (cur != null && cur !is VR3DGui.VR3DGuiRootNode) cur = cur.parent
            if (cur is VR3DGui.VR3DGuiRootNode) {
                grab?.target = { cur.grabDummy }
                cur.addChild(cur.grabDummy)
            }
        }, onRelease = {
            val rn = grab?.target?.let { it() as? VR3DGui.VR3DGuiRootNode } ?: return@Grabable
            rn.grabDummy.detach()
        }, lockRotation = true)
        node.addAttribute(Grabable::class.java, grab)
    }

}
