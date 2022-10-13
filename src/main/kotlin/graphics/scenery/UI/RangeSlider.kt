package graphics.scenery.UI

import javax.swing.JSlider

/**
 * From https://github.com/ernieyu/Swing-range-slider/blob/master/src/slider/RangeSlider.java
 * @author Ernest Yu
 * @licence Copyright (c) 2010 Ernest Yu. All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 *
 * An extension of JSlider to select a range of values using two thumb controls.
 * The thumb controls are used to select the lower and upper value of a range
 * with predetermined minimum and maximum values.
 *
 *
 * Note that RangeSlider makes use of the default BoundedRangeModel, which
 * supports an inner range defined by a value and an extent.  The upper value
 * returned by RangeSlider is simply the lower value plus the extent.
 */
class RangeSlider(min: Int = 0, max: Int = 100) : JSlider(min, max) {
    init {
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
