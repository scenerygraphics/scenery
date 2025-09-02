package graphics.scenery.ui

import graphics.scenery.controls.behaviours.Pressable
import graphics.scenery.controls.behaviours.SimplePressable
import graphics.scenery.controls.behaviours.Touchable
import org.joml.Vector3f

/** A toggleable button with text field for VR interaction.
 * @param textFalse shown when the button state is false
 * @param textTrue shown when the button state is true
 * @param command is executed when the user interacts with the button.
 * @param color default color of the button
 * @param pressedColor color after being pressed
 * @param touchingColor optional color input for touch feedback
 * @param byTouch allows the button to be pressed by simply touching it. If this is set to false, the user needs to press Grab.
 * @param default starting state of the button
 * @author Jan Tiemann
 * @author Samuel Pantze
 */
class ToggleButton(
    private val textFalse: String,
    private val textTrue: String,
    height: Float = 1f,
    command: () -> Unit,
    byTouch: Boolean = false,
    color: Vector3f = Vector3f(1f),
    pressedColor: Vector3f = Vector3f(0.4f),
    touchingColor: Vector3f = Vector3f(0.7f),
    val default: Boolean = false
) : Button(
    if (default) textTrue else textFalse,
    height,
    command,
    byTouch,
    color = color,
    pressedColor = pressedColor,
    touchingColor = touchingColor
) {
    override var pressed: Boolean = default
        set(value) {
            field = value
            if (value) {
                this.spatial {
                    scale.z = 0.7f
                    position.z = -0.15f
                    needsUpdate = true
                }
                text = textTrue
                box.changeColorWithTouchable(pressedColor)

            } else {
                this.spatial {
                    scale.z = 1f
                    position.z = 0f
                    needsUpdate = true
                }
                text = textFalse
                box.changeColorWithTouchable(color)
            }
        }

    init {
        box.addAttribute(
            Touchable::class.java, Touchable(
                onTouch = {
                    enteredTouchTime = System.currentTimeMillis()
                    this.spatial {
                        scale.z = 0.5f
                        position.z = -0.25f
                        needsUpdate = true
                    }
                },
                onHold = {
                    // this prevents immediate on/off switching
                    val timeSinceTouch = System.currentTimeMillis() - enteredTouchTime
                    val availableForTouch = byTouch && !isTouching && enabled.get()
                    if (availableForTouch && timeSinceTouch > 50) {
                        command()
                        isTouching = true
                        pressed = !pressed
                        enteredTouchTime = System.currentTimeMillis()
                    }
                },
                onRelease = {
                    isTouching = false
                },
                onHoldChangeDiffuseTo = touchingColor
        )
        )
        box.addAttribute(
            Pressable::class.java, SimplePressable(
                onPress = { _, _ ->
                    if (!byTouch && enabled.get()) {
                        if (!pressed) {
                            command()
                            pressed = true
                        } else {
                            pressed = false
                        }
                    }
                }
            ))

        box.material().diffuse = if (default) pressedColor else color
    }
}
