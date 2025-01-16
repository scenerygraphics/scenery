package graphics.scenery.controls.behaviours

import graphics.scenery.Box
import graphics.scenery.RichNode
import graphics.scenery.Sphere
import graphics.scenery.attribute.spatial.HasSpatial
import graphics.scenery.controls.TrackerInput
import graphics.scenery.primitives.TextBoard
import graphics.scenery.utils.extensions.plusAssign
import graphics.scenery.utils.extensions.times
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.PI

/**
 * WheelMenu used by [VRSelectionWheel] and [VRTreeSelectionWheel].
 * But can be used standalone
 * Handles the nodes and their positioning of the menu items.
 *
 * @param trackingMode how the menu should follow the users head position
 */
class WheelMenu(
    val hmd: TrackerInput,
    var actions: List<WheelEntry>,
    supportsSubWheels: Boolean = false,
    var trackingMode: TrackingMode = TrackingMode.LIVE
) : RichNode("Selection Wheel") {

    val menuEntries: List<MenuEntry>
    var previous: WheelMenu? = null

    /**
     * Orientation of menu behavior
     */
    enum class TrackingMode{
        /**
         * DoNotTrack
         */
        NONE,
        /**
         * Only once at start
         */
        START,
        /**
         * Follow user (Only rotation)
         */
        LIVE
    }

    init {

        when(trackingMode){
            TrackingMode.NONE -> {}
            TrackingMode.START -> spatial().rotation = Quaternionf(hmd.getOrientation()).conjugate().normalize()
            TrackingMode.LIVE -> update.add {
                if (trackingMode == TrackingMode.LIVE) {
                    spatial {
                        rotation = Quaternionf(hmd.getOrientation()).conjugate().normalize()
                    }
                }
            }
        }

        if (!supportsSubWheels && actions.any { it is SubWheel }) {
            throw IllegalArgumentException("SubWheels are not supported in this menu.")
        }

        menuEntries = actions.mapIndexed { index, action ->
            val pos = Vector3f(0f, .15f, 0f)
            pos.rotateZ((2f * Math.PI.toFloat() / actions.size) * index)

            val sphereRoot = RichNode()
            addChild(sphereRoot)
            sphereRoot.spatial().position = pos

            val sphere = when (action) {
                is Action -> {
                    val s = Sphere(0.025f, 10)
                    s.addAttribute(Pressable::class.java, SimplePressable(onRelease = { _,_ ->
                        action.action()
                        if (action.closeMenu){this.closeWheel(true)}
                    }))
                    // make it go red on touch
                    s.addAttribute(Touchable::class.java, Touchable())
                    s
                }
                is SubWheel -> {
                    val s = Box(Vector3f(0.05f))
                    s.addAttribute(Pressable::class.java, SimplePressable(onRelease = { _,_ ->
                        val new = WheelMenu(hmd, action.actions, true)
                        new.openSubWheel(this, pos)
                    }))
                    // make it go red on touch
                    s.addAttribute(Touchable::class.java, Touchable())
                    s
                }
                is Switch -> {
                    val bg = Box(Vector3f(0.1f,0.05f,0.01f))
                    val knob = Box(Vector3f(0.04f))
                    bg.addChild(knob)
                    knob.spatial().position.x = 0.025f * if (action.state) 1 else -1
                    knob.material().diffuse = if (action.state) Vector3f(0f,1f,0f) else Vector3f(1f,0.5f,0f)

                    knob.addAttribute(Pressable::class.java, SimplePressable(onRelease = { _,_ ->
                        val newColor = if (action.toggle()) Vector3f(0f,1f,0f) else Vector3f(1f,0.5f,0f)
                        val touch = knob.getAttribute(Touchable::class.java)
                        if (touch.originalDiffuse != null){
                            // this might screw with [VRTouch]s coloring, but it's not too bad as the menu is rebuild
                            // for every opening anew
                            touch.originalDiffuse = newColor
                        } else {
                            knob.material().diffuse = newColor
                        }

                        knob.spatial().position.x = 0.025f * if (action.state) 1 else -1
                        knob.spatial().needsUpdate = true
                    }))
                    // make it go red on touch
                    knob.addAttribute(Touchable::class.java, Touchable())

                    bg
                }
            }
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


            MenuEntry(action, sphere)
        }

        if (supportsSubWheels) {
            // close/back button
            val close = Box(Vector3f(0.05f, 0.05f, 0.05f))
            close.spatial().rotation.rotateLocalZ((PI / 4).toFloat())
            close.addAttribute(Pressable::class.java, SimplePressable(onRelease = { _,_ ->
                this.closeWheel()
            }))
            // make it go red on touch
            close.addAttribute(Touchable::class.java, Touchable())
            addChild(close)
        }

    }

     private fun openSubWheel(old: WheelMenu, relActionSpherePos: Vector3f){
         val new = this
        val root = old.parent?: return

        root.removeChild(old)
        root.addChild(new)
        new.addChild(old)

        new.previous = old

        new.spatial().position = old.spatial().position
        old.spatial().position = relActionSpherePos * -1.0f
        old.spatial().position += Vector3f(0f,0f,-0.15f)
         old.trackingMode = TrackingMode.NONE

        old.spatial().rotation = Quaternionf()
    }

    /**
     * closes this wheel
     */
    fun closeWheel(recursive: Boolean = false){
        val wheel = this
        if (wheel.previous == null){
            wheel.parent?.removeChild(wheel)
            return
        }

        val root = wheel.parent?: return
        val prev = wheel.previous!!

        root.removeChild(wheel)
        wheel.removeChild(prev)
        root.addChild(prev)

        prev.spatial().position = wheel.spatial().position

        prev.trackingMode = wheel.trackingMode


        if (recursive){
            prev.closeWheel(true)
        }
    }
    /**
     * @return (closest actionSphere) to (distance to controller)
     */
    fun closestActionSphere(pos: Vector3f) = menuEntries.map { entry ->
        entry to entry.representation.spatial().worldPosition().distance(pos)
    }.reduceRight { left, right -> if (left.second < right.second) left else right }

    companion object {
        data class MenuEntry(val action: WheelEntry, val representation: HasSpatial)
    }
}
