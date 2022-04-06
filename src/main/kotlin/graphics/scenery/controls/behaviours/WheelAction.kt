package graphics.scenery.controls.behaviours

/**
 * Data Class for [VRSelectionWheel] and [VRTreeSelectionWheel]
 */
sealed class WheelAction(val name: String)

/**
 * A wheel menu entry.
 *
 * @param name displayed name of the action
 * @param action called when the user selects this entry
 */
class Action(name: String, val action: () -> Unit) : WheelAction(name)

class Switch(name: String, var state:Boolean, val onChange: (Boolean) -> Unit): WheelAction(name){
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
class SubWheel(name: String, val actions: List<WheelAction>) : WheelAction(name)
