package graphics.scenery.controls

import graphics.scenery.utils.lazyLogger
import graphics.scenery.controls.OpenVRHMD.OpenVRButton
import graphics.scenery.controls.OpenVRHMD.Manufacturer
import org.scijava.ui.behaviour.Behaviour

/**
 * Maps action names to VR controller buttons across different controller types.
 * Use [registerProfile] to create a new set of bindings for a given manufacturer.
 * Loading registered profiles is done with [loadProfile] or [loadProfileForHMD].
 * Bind behaviors to existing action names in a profile via [bind]. Controller sides (roles) and buttons
 * are combined into a single [ButtonMapping] data class.
 */
class VRInputMapper {
    private val logger by lazyLogger()

    private val profiles = mutableMapOf<Manufacturer, Map<String, ButtonMapping>>()
    private var currentProfile: Manufacturer? = null

    /**
     * Register a controller profile for a specific manufacturer.
     */
    fun registerProfile(manufacturer: Manufacturer, mappings: Map<String, ButtonMapping>) {
        profiles[manufacturer] = mappings
        logger.debug("Registered profile for $manufacturer with ${mappings.size} mappings")
    }

    /**
     * Load a profile for the given manufacturer.
     * @return true if profile exists and was loaded
     */
    fun loadProfile(manufacturer: Manufacturer): Boolean {
        return if (profiles.containsKey(manufacturer)) {
            currentProfile = manufacturer
            logger.info("Loaded input profile for $manufacturer")
            true
        } else {
            logger.warn("No profile registered for $manufacturer")
            false
        }
    }

    /**
     * Auto-load profile based on HMD manufacturer.
     */
    fun loadProfileForHMD(hmd: OpenVRHMD): Boolean {
        return loadProfile(hmd.manufacturer)
    }

    /**
     * Get the button mapping for an action name.
     */
    fun getMapping(actionName: String): ButtonMapping? {
        val profile = currentProfile ?: return null
        return profiles[profile]?.get(actionName)
    }

    /**
     * Get all mappings for the current profile.
     */
    fun getCurrentMappings(): Map<String, ButtonMapping>? {
        val profile = currentProfile ?: return null
        return profiles[profile]
    }

    /** Get the currently active profile as [Manufacturer]. */
    fun getCurrentProfile(): Manufacturer? {
        return currentProfile
    }

    /**
     * Bind an action to a behavior on the HMD.
     * @return true if binding succeeded
     */
    fun bind(hmd: OpenVRHMD, actionName: String, behavior: Behaviour): Boolean {
        val mapping = getMapping(actionName) ?: return false
        hmd.addKeyBinding(actionName, mapping.role, mapping.button)
        hmd.addBehaviour(actionName, behavior)
        logger.debug("Bound '$actionName' to ${mapping.role} ${mapping.button}")
        return true
    }
}


/**
 * Represents a physical button on a VR controller.
 */
data class ButtonMapping(
    val role: TrackerRole,
    val button: OpenVRButton
)
