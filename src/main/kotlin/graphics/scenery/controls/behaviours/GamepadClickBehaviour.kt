package graphics.scenery.controls.behaviours

import org.scijava.ui.behaviour.ClickBehaviour

interface GamepadClickBehaviour : ClickBehaviour {
    override fun click(p0: Int, p1: Int)
}
