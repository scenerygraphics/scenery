package graphics.scenery.ui

import graphics.scenery.controls.behaviours.Pressable
import graphics.scenery.controls.behaviours.SimplePressable
import graphics.scenery.controls.behaviours.Touchable
import org.joml.Vector3f
import kotlin.concurrent.thread

/** A button with text field for VR interaction.
 * @param command is executed when the user interacts with the button.
 * @param color default color of the button
 * @param pressedColor color after being pressed
 * @param byTouch allows the button to be pressed by simply touching it. If this is set to false, the user needs to press Grab.
 * @param stayPressed whether the button should stay pressed after being triggered
 * @param depressDelay how long to wait until the button becomes depressed again, in millisecond (if stayPressed is false)
 * @author Jan Tiemann
 * @author Samuel Pantze
 */
class Button(
    text: String,
    height: Float = 1f,
    command: () -> Unit,
    val byTouch: Boolean = false,
    var stayPressed: Boolean = false,
    val depressDelay: Int = 0,
    val color: Vector3f = Vector3f(1f),
    val pressedColor: Vector3f = Vector3f(0.5f)
) :
    TextBox(text, height = height) {
    /** Flag that determines whether the button is ready to be released from depressDelay. */
    private var depressReady = true
    /** Flag that determines whether we are currently touching the button. If yes, we don't want to release from depressDelay yet. */
    private var isTouching = false

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
                // Release the button after a while if it's not supposed to stay pressed
                if (!stayPressed) {
                    depressReady = false
                    thread {
                        Thread.sleep(depressDelay.toLong())
                        depressReady = true
                        release()
                    }
                }
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
                    isTouching = true
                    pressed = true
                }
            },
            onRelease = {
                if (byTouch) {
                    isTouching = false
                    release()
                }
            },
            onHoldChangeDiffuseTo = pressedColor
        ))
        box.addAttribute(
            Pressable::class.java, SimplePressable(
                onPress = { _, _ ->
                    if (!byTouch && !pressed) {
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

    /** Releases the button if it was pressed, but only if the depressDelay allows us to release it. */
    fun release(force: Boolean = false) {
        if (depressReady && !stayPressed && !isTouching || force) {
            pressed = false
        }
    }
}
