package scenery.controls

import cleargl.ClearGLWindow
import org.scijava.ui.behaviour.BehaviourMap
import org.scijava.ui.behaviour.InputTriggerMap
import org.scijava.ui.behaviour.io.InputTriggerConfig
import org.scijava.ui.behaviour.io.yaml.YamlConfigIO
import scenery.Scene
import scenery.controls.behaviours.FPSCameraControl
import scenery.controls.behaviours.MovementCommand
import scenery.controls.behaviours.ToggleCommand
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.Reader
import java.io.StringReader

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

class ClearGLInputHandler(scene: Scene, renderer: Any, window: ClearGLWindow) {
    protected val inputMap = InputTriggerMap()
    protected val behaviourMap = BehaviourMap()
    protected val handler: JOGLMouseAndKeyHandler

    protected val scene: Scene
    protected val renderer: Any
    protected val window: ClearGLWindow

    init {
        // create Mouse & Keyboard Handler
        handler = JOGLMouseAndKeyHandler()
        handler.setInputMap(inputMap)
        handler.setBehaviourMap(behaviourMap)

        window.addKeyListener(handler)
        window.addMouseListener(handler)
        window.addWindowListener(handler)

        this.scene = scene
        this.renderer = renderer
        this.window = window
    }

    fun useDefaultBindings(bindingConfigFile: String) {
        // Load YAML config
        var reader: Reader

        try {
            reader = FileReader(bindingConfigFile)
        } catch (e: FileNotFoundException) {
            System.err.println("Falling back to default keybindings...")
            reader = StringReader("---\n" +
                    "- !mapping" + "\n" +
                    "  action: drag1" + "\n" +
                    "  contexts: [all]" + "\n" +
                    "  triggers: [button1, G]" + "\n" +
                    "- !mapping" + "\n" +
                    "  action: scroll1" + "\n" +
                    "  contexts: [all]" + "\n" +
                    "  triggers: [scroll]" + "\n" +
                    "")
        }

        val config = InputTriggerConfig(YamlConfigIO.read(reader))

        /*
     * Create behaviours and input mappings.
     */
        behaviourMap.put("drag1", FPSCameraControl("drag1", scene.findObserver(), window.width, window.height))

        behaviourMap.put("move_forward", MovementCommand("move_forward", "forward", scene.findObserver()))
        behaviourMap.put("move_back", MovementCommand("move_back", "back", scene.findObserver()))
        behaviourMap.put("move_left", MovementCommand("move_left", "left", scene.findObserver()))
        behaviourMap.put("move_right", MovementCommand("move_right", "right", scene.findObserver()))
        behaviourMap.put("move_up", MovementCommand("move_up", "up", scene.findObserver()))

        behaviourMap.put("toggle_debug", ToggleCommand("toggle_debug", renderer, "toggleDebug"))
        behaviourMap.put("toggle_ssao", ToggleCommand("toggle_ssao", renderer, "toggleSSAO"))

        val adder = config.inputTriggerAdder(inputMap, "all")
        adder.put("drag1") // put input trigger as defined in config
        adder.put("scroll1", "scroll")
        adder.put("click1", "button1", "B")
        adder.put("click1", "button3", "X")
        adder.put("move_forward", "W")
        adder.put("move_left", "A")
        adder.put("move_back", "S")
        adder.put("move_right", "D")
        adder.put("move_up", "SPACE")

        adder.put("toggle_debug", "X")
        adder.put("toggle_ssao", "O")
    }
}
