package graphics.scenery.controls

import edu.mines.jtk.opt.Vect
import graphics.scenery.Node
import graphics.scenery.utils.lazyLogger
import graphics.scenery.controls.OpenVRHMD.OpenVRButton
import graphics.scenery.controls.OpenVRHMD.Manufacturer
import graphics.scenery.primitives.TextBoard
import graphics.scenery.utils.extensions.xyzw
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
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

    /** Get all mappings for a specific tracker role. */
    fun getMappingsForRole(role: TrackerRole): List<ButtonMapping> =
        currentProfile?.let { profiles[it] }
            .orEmpty().values.filter { it.role == role }


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

    /** Gets all labels for a specific tracker [role] with the current mapping
     * and attaches them as [TextBoard]s to the controller. */
    fun attachUIForRole(role: TrackerRole, model: Node, textScale: Vector3f = Vector3f(0.03f)) {

        getMappingsForRole(role).forEach { mapping ->
            if (mapping.label == null && mapping.offset == null) return@forEach

            val uiNode = TextBoard(mapping.label!!).apply {
                fontColor = mapping.color?.xyzw() ?: Vector4f(0.9f)
                spatial {
                    position = mapping.offset ?: Vector3f(0f)
                    scale = textScale
                }
            }
            model.addChild(uiNode)
            mapping.uiNode = uiNode
        }
    }
}


/**
 * Represents a physical button on a VR controller, including an optional [label] with [offset], [rotation] and [color].
 * A backreference to a [TextBoard] called [uiNode] is also stored.
 */
data class ButtonMapping(
    val role: TrackerRole,
    val button: OpenVRButton,
    val label: String? = null,
    val offset: Vector3f? = null,
    val rotation: Quaternionf? = null,
    val color: Vector3f? = Vector3f(0.18f, 0.22f, 0.27f),
    var uiNode: TextBoard? = null
)
