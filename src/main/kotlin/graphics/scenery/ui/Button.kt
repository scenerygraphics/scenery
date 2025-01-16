package graphics.scenery.ui

import graphics.scenery.controls.behaviours.Pressable
import graphics.scenery.controls.behaviours.SimplePressable
import graphics.scenery.controls.behaviours.Touchable
import org.joml.Vector3f

/** A button with text field for VR interaction.
 * @param command is executed when the user interacts with the button.
 * @param color default color of the button
 * @param pressedColor color after being pressed
 * @param byTouch allows the button to be pressed by simply touching it. If this is set to false, the user needs to press Grab.
 * @param stayPressed whether the button should stay pressed after being triggered
 * @author Jan Tiemann
 * @author Samuel Pantze
 */
class Button(
    text: String,
    height: Float = 1f,
    command: () -> Unit,
    val byTouch: Boolean = false,
    var stayPressed: Boolean = false,
    val color: Vector3f = Vector3f(1f),
    val pressedColor: Vector3f = Vector3f(0.5f)
) :
    TextBox(text, height = height) {
    /** only visually */
    var pressed: Boolean = false
        set(value) {
            field = value
            if (value) {
                this.spatial {
                    scale.z = 0.5f
                    position.z = -0.25f
                    needsUpdate = true
                }
                box.changeColorWithTouchable(pressedColor)
            } else {
                this.spatial {
                    scale.z = 1f
                    position.z = 0f
                    needsUpdate = true
                }
                box.changeColorWithTouchable(color)
            }
        }

    init {
        box.addAttribute(Touchable::class.java, Touchable(
            onTouch = {
                if (byTouch) {
                    command()
                    pressed = true
                }
            },
            onRelease = {
                if (byTouch) {
                    pressed = false || stayPressed
                }
            },
            onHoldChangeDiffuseTo = pressedColor
        ))
        box.addAttribute(
            Pressable::class.java, SimplePressable(
                onPress = { _, _ ->
                    if (!byTouch) {
                        command()
                        pressed = true
                    }
                },
                onRelease = { _, _ ->
                    if (!byTouch) {
                        pressed = false || stayPressed
                    }
                }
            ))

        box.material().diffuse = color

    }

    /** Releases the button if it was pressed. */
    fun release() {
        pressed = false
    }
}
