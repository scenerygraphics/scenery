package graphics.scenery.controls

import javax.swing.JSlider

/*
From https://github.com/ernieyu/Swing-range-slider/blob/master/src/slider/RangeSlider.java
 */

/**
 * An extension of JSlider to select a range of values using two thumb controls.
 * The thumb controls are used to select the lower and upper value of a range
 * with predetermined minimum and maximum values.
 *
 *
 * Note that RangeSlider makes use of the default BoundedRangeModel, which
 * supports an inner range defined by a value and an extent.  The upper value
 * returned by RangeSlider is simply the lower value plus the extent.
 */
class RangeSlider : JSlider {
    /**
     * Constructs a RangeSlider with default minimum and maximum values of 0
     * and 100.
     */
    constructor() {
        initSlider()
    }

    /**
     * Constructs a RangeSlider with the specified default minimum and maximum
     * values.
     */
    constructor(min: Int, max: Int) : super(min, max) {
        initSlider()
    }

    /**
     * Initializes the slider by setting default properties.
     */
    private fun initSlider() {
        setOrientation(HORIZONTAL)
    }

    /**
     * Overrides the superclass method to install the UI delegate to draw two
     * thumbs.
     */
    override fun updateUI() {
        setUI(RangeSliderUI(this))
        // Update UI for slider labels.  This must be called after updating the
        // UI of the slider.  Refer to JSlider.updateUI().
        updateLabelUIs()
    }

    /**
     * Returns the lower value in the range.
     */
    override fun getValue(): Int {
        return super.getValue()
    }

    /**
     * Sets the lower value in the range.
     */
    override fun setValue(value: Int) {
        val oldValue = getValue()
        if (oldValue == value) {
            return
        }

        // Compute new value and extent to maintain upper value.
        val oldExtent = extent
        val newValue = Math.min(Math.max(minimum, value), oldValue + oldExtent)
        val newExtent = oldExtent + oldValue - newValue

        // Set new value and extent, and fire a single change event.
        model.setRangeProperties(
            newValue, newExtent, minimum,
            maximum, valueIsAdjusting
        )
    }
    /**
     * Returns the upper value in the range.
     */// Compute new extent.

    // Set extent to set upper value.
    /**
     * Sets the upper value in the range.
     */
    var upperValue: Int
        get() = value + extent
        set(value) {
            // Compute new extent.
            val lowerValue = getValue()
            val newExtent = Math.min(Math.max(0, value - lowerValue), maximum - lowerValue)

            // Set extent to set upper value.
            extent = newExtent
        }
}
