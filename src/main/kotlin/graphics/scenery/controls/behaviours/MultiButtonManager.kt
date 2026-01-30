package graphics.scenery.controls.behaviours

import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackerRole
import graphics.scenery.utils.lazyLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.set

/** Keep track of which VR buttons are currently being pressed. This is useful if you want to assign the same button
 * to different behaviors with different combinations. This class helps with managing the button states.
 * Buttons to track first need to be registered with [registerButtonConfig]. Call [pressButton] and [releaseButton]
 * in your behavior init/end methods. You can check if both hands are in use with [isTwoHandedActive] or if a specific
 * button is currently pressed with [isButtonPressed]. */
class MultiButtonManager {
    data class ButtonConfig (
        val button: OpenVRHMD.OpenVRButton,
        val trackerRole: TrackerRole
    )

    val logger by lazyLogger()

    /** List of registered buttons, stored as [ButtonConfig] and whether the button is pressed right now. */
    private val buttons = ConcurrentHashMap<ButtonConfig, Boolean>()
    private val twoHandedActive = AtomicBoolean(false)

    init {
        buttons.forEach { (config, value) ->
            buttons[config] = false
        }
    }

    /** Add a new button configuration that the manager will keep track of. */
    fun registerButtonConfig(button: OpenVRHMD.OpenVRButton, trackerRole: TrackerRole) {
        logger.debug("Registered new button config: $button, $trackerRole")
        buttons[ButtonConfig(button, trackerRole)] = false
    }

    /** Add a button to the list of pressed buttons. */
    fun pressButton(button: OpenVRHMD.OpenVRButton, role: TrackerRole): Boolean {
        val config = ButtonConfig(button, role)
        if (!buttons.containsKey(config)) { return false }
        buttons[config] = true
        updateTwoHandedState()
        return true
    }

    /** Overload function that takes a button config instead of separate button and trackerrole inputs. */
    fun pressButton(buttonConfig: ButtonConfig): Boolean {
        return pressButton(buttonConfig.button, buttonConfig.trackerRole)
    }

    /** Remove a button from the list of pressed buttons. */
    fun releaseButton(button: OpenVRHMD.OpenVRButton, role: TrackerRole): Boolean {
        val config = ButtonConfig(button, role)
        if (!buttons.containsKey(config)) { return false }
        buttons[config] = false
        updateTwoHandedState()
        return true
    }

    /** Overload function that takes a button config instead of separate button and trackerrole inputs. */
    fun releaseButton(buttonConfig: ButtonConfig): Boolean {
        return releaseButton(buttonConfig.button, buttonConfig.trackerRole)
    }

    private fun updateTwoHandedState() {
        // Check if any buttons are pressed on both hands
        val leftPressed = buttons.any { it.key.trackerRole == TrackerRole.LeftHand && it.value }
        val rightPressed = buttons.any { it.key.trackerRole == TrackerRole.RightHand && it.value }
        twoHandedActive.set(leftPressed && rightPressed)
    }

    /** Returns true when the same button is currently pressed on both VR controllers. */
    fun isTwoHandedActive(): Boolean = twoHandedActive.get()

    /** Check if a button is currently being pressed. */
    fun isButtonPressed(button: OpenVRHMD.OpenVRButton, role: TrackerRole): Boolean {
        return buttons[ButtonConfig(button, role)] ?: false
    }

    /** Retrieve a list of currently registered buttons. */
    fun getRegisteredButtons(): ConcurrentHashMap<ButtonConfig, Boolean> {
        return buttons
    }
}
