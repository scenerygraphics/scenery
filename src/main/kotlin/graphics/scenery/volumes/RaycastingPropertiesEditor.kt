package graphics.scenery.volumes

import net.miginfocom.swing.MigLayout
import javax.swing.*


/**
 * @author Konrad Michel <Konrad.Michel@mailbox.tu-dresden.de>
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 *
 * A Swing UI transfer function manipulation tool, able to add, remove and manipulate the transfer function control points of a set volume interactively.
 * Able to generate a histogram and visualize it as well to help with TF-settings
 * Able to dynamically set the transfer function range -> changes histogram as well
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

    companion object{
        /**
         * Convenience function to open a JFrame containing a [RaycastingPropertiesEditor]
         */
        fun showRaycastingProperties(volumeManager: VolumeManager){
            val frame = JFrame()
            frame.title = "Raycasting properties"
            val editor = RaycastingPropertiesEditor(volumeManager)
            frame.add(editor)
            frame.pack()
            frame.isVisible = true
        }
    }
}
