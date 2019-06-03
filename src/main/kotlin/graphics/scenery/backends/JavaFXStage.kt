package graphics.scenery.backends

import graphics.scenery.utils.SceneryFXPanel
import javafx.application.Platform
import javafx.stage.Stage

/**
 * JavaFX window or stage, with [panel] being the [SceneryFXPanel] scenery will render to.
 * */
class JavaFXStage(var panel: SceneryFXPanel): SceneryWindow() {
    override var title: String = ""
        set(value) {
            field = value
            Platform.runLater { (panel.scene.window as? Stage)?.title = value }
        }
}
