package graphics.scenery.ui

import graphics.scenery.controls.behaviours.Pressable
import graphics.scenery.controls.behaviours.SimplePressable
import graphics.scenery.controls.behaviours.Touchable
import org.joml.Vector3f

/**
 *  @author Jan Tiemann
 *  */
class Button(
    text: String,
    height: Float = 1f,
    command: () -> Unit,
    val color: Vector3f = Vector3f(1f),
    val pressedColor: Vector3f = Vector3f(0.5f)
) :
    TextBox(text, height = height) {
    /** only visually */
    var pressed: Boolean = false
        set(value) {
            field = value
            if (value){
                this.spatial{
                    scale.z = 0.5f
                    position.z = -0.25f
                    needsUpdate = true
                }
                box.changeColorWithTouchable(pressedColor)
            } else {
                this.spatial{
                    scale.z = 1f
                    position.z = 0f
                    needsUpdate = true
                }
                box.changeColorWithTouchable(color)
            }
        }

    var stayPressed = false

    init {
        box.addAttribute(Touchable::class.java, Touchable())
        box.addAttribute(
            Pressable::class.java, SimplePressable(
            onPress = { _,_ ->
                command()
                pressed = true
            },
            onRelease = { _,_ ->
                pressed = false || stayPressed
            }
        ))

        box.material().diffuse = color

    }
}
