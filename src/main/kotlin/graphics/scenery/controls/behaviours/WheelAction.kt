package graphics.scenery.controls.behaviours


sealed class WheelAction(val name: String)
class Action(name: String, val action: () -> Unit): WheelAction(name)
class SubWheel(name: String, val actions: List<WheelAction>): WheelAction(name)
