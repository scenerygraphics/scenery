package graphics.scenery.ui

import graphics.scenery.Box
import graphics.scenery.RichNode
import graphics.scenery.controls.behaviours.Pressable
import graphics.scenery.controls.behaviours.SimplePressable
import graphics.scenery.controls.behaviours.Touchable
import org.joml.Vector3f

/**
 *  @author Jan Tiemann
 */
class Switch(label: String, start: Boolean, middleAlign: Boolean = false, onChange: (Boolean) -> Unit)
    : Row(margin = 0.2f, middleAlign = middleAlign) {
    init {
        name = "Switch $label"

        val knob = Knob(start,onChange = onChange)
        this.addChild(knob)
        this.addChild(Button(label, command = knob::toggle))
    }

    private class Knob(var value: Boolean,
                       val onColor: Vector3f = Vector3f(0f,0.8f,0f),
                       val offColor: Vector3f = Vector3f(0.5f),
                       val onChange: (Boolean) -> Unit)
        : RichNode("Knob"), Gui3DElement{

        //background
        val bg = Box(Vector3f(2f,1f,0.3f))

        val knob = Box(Vector3f(bg.sizes.x * 0.4f))

        override val width: Float
            get() = bg.sizes.x
        override val height: Float
            get() = bg.sizes.y

        init {
            bg.spatial {
                position = Vector3f(bg.sizes.x * 0.5f, bg.sizes.y * 0.5f, -bg.sizes.z * 0.5f)
            }
            bg.material().diffuse = Vector3f(1f)
            this.addChild(bg)
            bg.addChild(knob)

            knob.spatial().position.x = bg.sizes.x * 0.25f * if (value) 1 else -1
            knob.material().diffuse = if (value) onColor else offColor

            knob.addAttribute(Pressable::class.java, SimplePressable(onRelease = { _, _ ->
                toggle()
            }))
            // make it go red on touch
            knob.addAttribute(Touchable::class.java, Touchable())
        }

        fun toggle(): Boolean {
            value = !value
            onChange(value)

            val newColor = if (value) onColor else offColor
            knob.changeColorWithTouchable(newColor)

            knob.spatial().position.x = bg.sizes.x * 0.25f * if (value) 1 else -1
            knob.spatial().needsUpdate = true
            return value
        }
    }
}
