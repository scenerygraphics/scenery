package graphics.scenery.controls.behaviours

import graphics.scenery.Scene
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.ui.behaviour.DragBehaviour

/** @author Jan Tiemann
 * Wrapper for analog input devices like joysticks to represent drag behaviors. */
class AnalogInputWrapper(val wrapped: DragBehaviour, scene: Scene, val threshold: Int = 5) : ClickBehaviour {
    var frameCounter = 0

    var lastAction = -1


    init {
        scene.update += {
            frameCounter = (frameCounter + 1) % 100
            if (lastAction > 0 && frameCounter - lastAction > threshold) {
                wrapped.end(0, 0)
                lastAction = -1
            }
        }
    }

    override fun click(x: Int, y: Int) {
        when {
            lastAction < 0 -> {
                wrapped.init(x, y)
            }
            frameCounter - lastAction < threshold -> {
                wrapped.drag(x, y)
            }

        }
        lastAction = frameCounter
    }
}
