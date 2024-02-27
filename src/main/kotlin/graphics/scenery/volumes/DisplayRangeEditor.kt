package graphics.scenery.volumes

import graphics.scenery.ui.RangeSlider
import net.miginfocom.swing.MigLayout
import javax.swing.*
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * GUI for editing the display range of volumes.
 * Part of TransferFunctionEditor
 *
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 */
class DisplayRangeEditor(private val tfContainer: HasTransferFunction): JPanel(){

    //RangeEditor
    private val minText: JTextField
    private val maxText: JTextField
    private val rangeSlider: RangeSlider
    private val minValueLabel: JLabel
    private val maxValueLabel: JLabel

    init {
        layout = MigLayout(
            "fill",
            "[left, 10%]5[right, 40%]5[left, 10%]5[right, 40%]"
        )
        border = BorderFactory.createTitledBorder("Display Range")

        // Range editor
        val initMinValue = max(tfContainer.minDisplayRange.toInt(), 100)
        minText = JTextField(initMinValue.toString())
        minValueLabel = JLabel(String.format("%.1f", tfContainer.range.first))

        val initMaxValue = max(tfContainer.maxDisplayRange.toInt(), 100)
        maxText = JTextField(initMaxValue.toString())
        maxText.horizontalAlignment = SwingConstants.RIGHT
        maxValueLabel = JLabel(String.format("%.1f", tfContainer.range.second))

        rangeSlider = RangeSlider()
        rangeSlider.minimum = tfContainer.range.first.roundToInt()
        rangeSlider.maximum = tfContainer.range.second.roundToInt()
        rangeSlider.value = tfContainer.minDisplayRange.toInt()
        rangeSlider.upperValue = tfContainer.maxDisplayRange.toInt()

        minText.addActionListener { updateSliderRange() }
        maxText.addActionListener { updateSliderRange() }
        rangeSlider.addChangeListener {
            updateConverter()
        }

        val rangeEditorPanel = this
        rangeEditorPanel.add(JLabel("min:"), "shrinkx")
        rangeEditorPanel.add(minText, "growx")
        rangeEditorPanel.add(JLabel("max:"), "shrinkx")
        rangeEditorPanel.add(maxText, "growx, wrap")
        rangeEditorPanel.add(rangeSlider, "spanx, growx, wrap")
        rangeEditorPanel.add(minValueLabel, "spanx 2, left")
        rangeEditorPanel.add(maxValueLabel, "spanx 2, right")
    }

    private fun updateSliderRange() {
        val min = minText.toInt()
        val max = maxText.toInt()
        if (min != null && max != null) {
            rangeSlider.value = min
            rangeSlider.upperValue = max
        }
        updateConverter()
    }

    private fun JTextField.toInt() = text.toIntOrNull()

    private fun updateConverter() {
        minText.text = rangeSlider.value.toString()
        maxText.text = rangeSlider.upperValue.toString()
        minValueLabel.text = String.format("%.1f", tfContainer.range.first)
        maxValueLabel.text = String.format("%.1f", tfContainer.range.second)

        tfContainer.minDisplayRange = rangeSlider.value.toFloat()
        tfContainer.maxDisplayRange = rangeSlider.upperValue.toFloat()
    }

    /**
     * Set tfcontainer values to gui.
     */
    internal fun refreshDisplayRange() {
        rangeSlider.value = tfContainer.minDisplayRange.toInt()
        rangeSlider.upperValue = tfContainer.maxDisplayRange.toInt()
        updateConverter()
    }
}
