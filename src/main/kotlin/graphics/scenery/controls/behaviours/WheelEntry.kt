package graphics.scenery.controls.behaviours

/**
 * Data Class for [VRFastSelectionWheel] and [VRTreeSelectionWheel]
 */
sealed class WheelEntry(val name: String)

/**
 * A wheel menu entry.
 *
 * @param name displayed name of the action
 * @param action called when the user selects this entry
 * @param closeMenu if used in a [VRTreeSelectionWheel] choosing this action should close the menu
 */
class Action(name: String, val closeMenu: Boolean = true, val action: () -> Unit) : WheelEntry(name)

/**
 * A toggleable switch entry.
 *
 * @param onChange called with the new state of the switch
 */
class Switch(name: String, var state:Boolean, val onChange: (Boolean) -> Unit): WheelEntry(name){
    /**
     * @return new state
     */
    fun toggle(): Boolean{
        state = !state
        onChange(state)
        return state
    }
}

/**
 * A sub menu of a wheel menu. It will be displayed like a regular menu entry and when selected
 * it opens on-top of the previous menu. Deeper sub menus are supported.
 */
class SubWheel(name: String, val actions: List<WheelEntry>) : WheelEntry(name)
