@file:JvmName("OrthoView")

package graphics.scenery.volumes

import graphics.scenery.*
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.behaviours.MouseDragSphere
import graphics.scenery.effectors.LineRestrictionEffector
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.times
import kotlinx.coroutines.*
import org.joml.Vector3f
import java.lang.Thread.sleep
import kotlin.concurrent.thread

/**
 * Creates three orthogonal, movable slicing planes through the volume.
 * Attention! For movable planes a behavior like in [InputHandler.addOrthoViewDragBehavior] is needed.
 * If [key] is set it will be added automatically if there is no other orthoPlane drag behavior already.
 *
 * To remove call [SlicingPlane.removeTargetVolume] on the leaf nodes and
 * [Volume.slicingMode] should be set to [Volume.SlicingMode.None]
 *
 * @param key The input key for optional plane movement behavior. See Input Docs for format.
 */
fun createOrthoView(volume: Volume, key: String? = null) {
    volume.slicingMode = Volume.SlicingMode.Slicing

    val sliceXZ = SlicingPlane()
    val sliceXY = SlicingPlane()
    val sliceYZ = SlicingPlane()

    sliceXY.spatial().rotation = sliceXY.spatial().rotation.rotateX((Math.PI / 2).toFloat())
    sliceYZ.spatial().rotation = sliceYZ.spatial().rotation.rotateZ((Math.PI / 2).toFloat())

    sliceXZ.addTargetVolume(volume)
    sliceXY.addTargetVolume(volume)
    sliceYZ.addTargetVolume(volume)

    volume.boundingBox?.let { boundingBox ->

        val center = (boundingBox.max - boundingBox.min) * 0.5f

        val planeXZ = Box(Vector3f(boundingBox.max.x, 1f, boundingBox.max.z))
        val planeXY = Box(Vector3f(boundingBox.max.x, boundingBox.max.y, 1f))
        val planeYZ = Box(Vector3f(1f, boundingBox.max.y, boundingBox.max.z))

        planeXZ.spatial().position = center
        planeXY.spatial().position = center
        planeYZ.spatial().position = center

        planeXZ.addAttribute(OrthoPlane::class.java, OrthoPlane())
        planeXY.addAttribute(OrthoPlane::class.java, OrthoPlane())
        planeYZ.addAttribute(OrthoPlane::class.java, OrthoPlane())


        // make transparent
        planeXZ.material {
            blending.setOverlayBlending()
            blending.transparent = true
            blending.opacity = 0f
        }
        //planeXZ.material.wireframe = true
        planeXY.setMaterial(planeXZ.material())
        planeYZ.setMaterial(planeXZ.material())

        planeXZ.addChild(sliceXZ)
        planeXY.addChild(sliceXY)
        planeYZ.addChild(sliceYZ)

        volume.addChild(planeXZ)
        volume.addChild(planeXY)
        volume.addChild(planeYZ)

        val yTop = RichNode()
        yTop.spatial().position = Vector3f(center.x, boundingBox.max.y, center.z)
        volume.addChild(yTop)

        val yBottom = RichNode()
        yBottom.spatial().position = Vector3f(center.x, boundingBox.min.y, center.z)
        volume.addChild(yBottom)

        LineRestrictionEffector(planeXZ, { yTop.spatial().position }, { yBottom.spatial().position })

        val zTop = RichNode()
        zTop.spatial().position = Vector3f(center.x, center.y, boundingBox.max.z)
        volume.addChild(/*z*/zTop)

        val zBottom = RichNode()
        zBottom.spatial().position = Vector3f(center.x, center.y, boundingBox.min.z)
        volume.addChild(zBottom)

        LineRestrictionEffector(planeXY, { zTop.spatial().position }, { zBottom.spatial().position })

        val xTop = RichNode()
        xTop.spatial().position = Vector3f(boundingBox.max.x, center.y, center.z)
        volume.addChild(xTop)

        val xBottom = RichNode()
        xBottom.spatial().position = Vector3f(boundingBox.min.x, center.y, center.z)
        volume.addChild(xBottom)

        LineRestrictionEffector(planeYZ, { xTop.spatial().position }, { xBottom.spatial().position })

    }

    key?.let {
        GlobalScope.launch {
            var inputHandler = volume.hub.get<InputHandler>()
            var count = 0
            while (inputHandler == null) {
                delay(1000L)
                inputHandler = volume.hub.get<InputHandler>()
                if (count++ > 60) {
                    volume.logger.warn("Could not find input handler to attach orthoPlane drag behavior to.")
                    break
                }
            }
            inputHandler?.let {
                if (inputHandler.getBehaviour("dragOrthoPlane") == null) {
                    inputHandler.addOrthoViewDragBehavior(key)
                }
            }

        }
    }
}

/**
 * Attribute class to tag othoplanes
 */
class OrthoPlane

/**
 * Adds a [MouseDragSphere] behavior ortho view slices.
 */
fun InputHandler.addOrthoViewDragBehavior(key: String) {
    addBehaviour(
        "dragOrthoPlane", MouseDragSphere(
            "dragOrthoPlane",
            { this.scene.findObserver() },
            debugRaycast = false,
            filter = { it.getAttributeOrNull(OrthoPlane::class.java) != null }
        )
    )
    addKeyBinding("dragOrthoPlane", key)
}
