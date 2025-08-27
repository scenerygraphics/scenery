package graphics.scenery.ui

import graphics.scenery.controls.behaviours.Pressable
import graphics.scenery.controls.behaviours.SimplePressable
import graphics.scenery.controls.behaviours.Touchable
import org.joml.Vector3f
import kotlin.concurrent.thread

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
                    if (byTouch && !isTouching && enabled.get() && (System.currentTimeMillis() - enteredTouchTime) > 50) {
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
