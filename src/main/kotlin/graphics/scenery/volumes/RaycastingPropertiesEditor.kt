package graphics.scenery.volumes

import net.miginfocom.swing.MigLayout
import javax.swing.*


/**
 * Provides a graphical editor to enable interactively changing volume raycasting properties (e.g., the step size
 * along the ray) in volume rendering applications.
 *
 * @author Aryaman Gupta <aryaman.gupta@tu-dresden.de>
 */
class RaycastingPropertiesEditor constructor(
    private val volumeManager: VolumeManager
): JPanel() {

    init {
        layout = MigLayout("flowy")

        //Toggling fixedStepSize
        val fixedSizeButton = JCheckBox("Fixed step size")
        add(fixedSizeButton, "growx")

        val stepsText = JTextField("2.00")
        stepsText.isEnabled = false

        fixedSizeButton.addActionListener {
            volumeManager.shaderProperties["fixedStepSize"] = fixedSizeButton.isSelected
            stepsText.isEnabled = fixedSizeButton.isSelected
        }

        val editorPanel = JPanel()
        editorPanel.layout = MigLayout("fill")
        add(editorPanel, "grow")

        editorPanel.add(fixedSizeButton)
        editorPanel.add(JLabel("Steps per voxel:"), "shrinkx")
        editorPanel.add(stepsText)

        stepsText.addActionListener {
            try {
                volumeManager.shaderProperties["stepsPerVoxel"] = stepsText.text.toFloat()
            } catch (_: NumberFormatException) {

            }
        }

    }

    /**
     * Companion object as described in https://kotlinlang.org/docs/object-declarations.html#companion-objects
     */
    companion object {
        /**
         * Convenience function to open a JFrame containing a [RaycastingPropertiesEditor]
         */
        fun show(volumeManager: VolumeManager): JFrame {
            val frame = JFrame()
            frame.title = "Raycasting properties"
            val editor = RaycastingPropertiesEditor(volumeManager)
            frame.add(editor)
            frame.pack()
            frame.isVisible = true
            return frame
        }
    }
}
