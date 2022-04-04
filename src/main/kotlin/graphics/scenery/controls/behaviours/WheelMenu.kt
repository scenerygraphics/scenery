package graphics.scenery.controls.behaviours

import graphics.scenery.Box
import graphics.scenery.RichNode
import graphics.scenery.Sphere
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.TrackerInput
import graphics.scenery.primitives.TextBoard
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.PI

/**
 * Shared code of [VRSelectionWheel] and [VRTreeSelectionWheel].
 * Handles the nodes and their positioning of the menu items.
 */
internal class WheelMenu(
    val controller: Spatial,
    val hmd: TrackerInput,
    var actions: List<WheelAction>,
    private val supportsSubWheels: Boolean = false
    ) : RichNode("Selection Wheel"){

    val actionSpheres : List<ActionSphere>
    var previous: WheelMenu? = null

    var followHead = true

    init {
        spatial {
            position = controller.worldPosition()
        }

        update.add {
            if (followHead) {
                spatial {
                    rotation = Quaternionf(hmd.getOrientation()).conjugate().normalize()
                }
            }
        }

        actionSpheres = actions.mapIndexed { index, action ->
            val pos = Vector3f(0f, .15f, 0f)
            pos.rotateZ((2f * Math.PI.toFloat() / actions.size) * index)

            val sphereRoot = RichNode()
            addChild(sphereRoot)
            sphereRoot.spatial().position = pos

            val sphere = Sphere(0.025f, 10)
            sphereRoot.addChild(sphere)

            val board = TextBoard()
            board.text = action.name
            board.name = "ToolSelectTextBoard"
            board.transparent = 0
            board.fontColor = Vector4f(0.0f, 0.0f, 0.0f, 1.0f)
            board.backgroundColor = Vector4f(100f, 100f, 100f, 1.0f)
            board.spatial {
                position = Vector3f(0f, 0.05f, 0f)
                scale = Vector3f(0.05f, 0.05f, 0.05f)
            }
            sphereRoot.addChild(board)

            if (supportsSubWheels){
                sphere.addAttribute(Pressable::class.java, SimplePressable(onRelease = {
                    when(action) {
                        is Action -> {
                            action.action()
                            VRTreeSelectionWheel.closeWheel(this, true)
                        }
                        is SubWheel -> {
                            val new = WheelMenu(controller,hmd,action.actions,true)
                            VRTreeSelectionWheel.openSubWheel(new, this, pos)
                        }
                    }
                }))

                // make it go red on touch
                sphere.addAttribute(Touchable::class.java, Touchable())

                // close/back button
                val close = Box(Vector3f(0.05f,0.05f,0.05f))
                close.spatial().rotation.rotateLocalZ((PI/4).toFloat())
                close.addAttribute(Pressable::class.java, SimplePressable(onRelease = {
                    VRTreeSelectionWheel.closeWheel(this)
                }))
                // make it go red on touch
                close.addAttribute(Touchable::class.java, Touchable())
                addChild(close)
            }

            ActionSphere(action, sphere)
        }
    }

    /**
     * @return (closest actionSphere) to (distance to controller)
     */
    fun closestActionSphere() = actionSpheres.map { entry ->
        entry to entry.sphere.spatial().worldPosition().distance(controller.worldPosition())
    }.reduceRight { left, right -> if (left.second < right.second) left else right }

    companion object {
        internal data class ActionSphere(val action: WheelAction, val sphere: Sphere)
    }
}
