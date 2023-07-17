package graphics.scenery.ui

import java.awt.*
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import javax.swing.JComponent
import javax.swing.JSlider
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.plaf.basic.BasicSliderUI

/**
 * From https://github.com/ernieyu/Swing-range-slider/blob/master/src/slider/RangeSliderUI.java
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
 * UI delegate for the RangeSlider component.  RangeSliderUI paints two thumbs,
 * one for the lower value and one for the upper value.
 *
 * Constructs a RangeSliderUI for the specified slider component.
 * @param b RangeSlider
 */
class RangeSliderUI(b: RangeSlider?) : BasicSliderUI(b) {

    /** Color of selected range.  */
    private val rangeColor = Color.DARK_GRAY

    /** Location and size of thumb for upper value.  */
    private var upperThumbRect: Rectangle? = null

    /** Indicator that determines whether upper thumb is selected.  */
    private var upperThumbSelected = false

    /** Indicator that determines whether lower thumb is being dragged.  */
    @Transient
    private var lowerDragging = false

    /** Indicator that determines whether upper thumb is being dragged.  */
    @Transient
    private var upperDragging = false

    /**
     * Installs this UI delegate on the specified component.
     */
    override fun installUI(c: JComponent) {
        upperThumbRect = Rectangle()
        super.installUI(c)
    }

    /**
     * Creates a listener to handle track events in the specified slider.
     */
    override fun createTrackListener(slider: JSlider): TrackListener {
        return RangeTrackListener()
    }

    /**
     * Creates a listener to handle change events in the specified slider.
     */
    override fun createChangeListener(slider: JSlider): ChangeListener {
        return ChangeHandler()
    }

    /**
     * Updates the dimensions for both thumbs.
     */
    override fun calculateThumbSize() {
        // Call superclass method for lower thumb size.
        super.calculateThumbSize()

        // Set upper thumb size.
        upperThumbRect!!.setSize(thumbRect.width, thumbRect.height)
    }

    /**
     * Updates the locations for both thumbs.
     */
    override fun calculateThumbLocation() {
        // Call superclass method for lower thumb location.
        super.calculateThumbLocation()

        // Adjust upper value to snap to ticks if necessary.
        if (slider.snapToTicks) {
            val upperValue = slider.value + slider.extent
            var snappedValue = upperValue
            val majorTickSpacing = slider.majorTickSpacing
            val minorTickSpacing = slider.minorTickSpacing
            var tickSpacing = 0
            if (minorTickSpacing > 0) {
                tickSpacing = minorTickSpacing
            } else if (majorTickSpacing > 0) {
                tickSpacing = majorTickSpacing
            }
            if (tickSpacing != 0) {
                // If it's not on a tick, change the value
                if ((upperValue - slider.minimum) % tickSpacing != 0) {
                    val temp = (upperValue - slider.minimum).toFloat() / tickSpacing.toFloat()
                    val whichTick = Math.round(temp)
                    snappedValue = slider.minimum + whichTick * tickSpacing
                }
                if (snappedValue != upperValue) {
                    slider.extent = snappedValue - slider.value
                }
            }
        }

        // Calculate upper thumb location.  The thumb is centered over its
        // value on the track.
        if (slider.orientation == JSlider.HORIZONTAL) {
            val upperPosition = xPositionForValue(slider.value + slider.extent)
            upperThumbRect!!.x = upperPosition - upperThumbRect!!.width / 2
            upperThumbRect!!.y = trackRect.y
        } else {
            val upperPosition = yPositionForValue(slider.value + slider.extent)
            upperThumbRect!!.x = trackRect.x
            upperThumbRect!!.y = upperPosition - upperThumbRect!!.height / 2
        }
    }

    /**
     * Returns the size of a thumb.
     */
    override fun getThumbSize(): Dimension {
        return Dimension(12, 12)
    }

    /**
     * Paints the slider.  The selected thumb is always painted on top of the
     * other thumb.
     */
    override fun paint(g: Graphics, c: JComponent) {
        super.paint(g, c)
        val clipRect = g.clipBounds
        if (upperThumbSelected) {
            // Paint lower thumb first, then upper thumb.
            if (clipRect.intersects(thumbRect)) {
                paintLowerThumb(g)
            }
            if (clipRect.intersects(upperThumbRect)) {
                paintUpperThumb(g)
            }
        } else {
            // Paint upper thumb first, then lower thumb.
            if (clipRect.intersects(upperThumbRect)) {
                paintUpperThumb(g)
            }
            if (clipRect.intersects(thumbRect)) {
                paintLowerThumb(g)
            }
        }
    }

    /**
     * Paints the track.
     */
    override fun paintTrack(g: Graphics) {
        // Draw track.
        super.paintTrack(g)
        val trackBounds = trackRect
        if (slider.orientation == JSlider.HORIZONTAL) {
            // Determine position of selected range by moving from the middle
            // of one thumb to the other.
            val lowerX = thumbRect.x + thumbRect.width / 2
            val upperX = upperThumbRect!!.x + upperThumbRect!!.width / 2

            // Determine track position.
            val cy = trackBounds.height / 2 - 2

            // Save color and shift position.
            val oldColor = g.color
            g.translate(trackBounds.x, trackBounds.y + cy)

            // Draw selected range.
            g.color = rangeColor
            for (y in 0..3) {
                g.drawLine(lowerX - trackBounds.x, y, upperX - trackBounds.x, y)
            }

            // Restore position and color.
            g.translate(-trackBounds.x, -(trackBounds.y + cy))
            g.color = oldColor
        } else {
            // Determine position of selected range by moving from the middle
            // of one thumb to the other.
            val lowerY = thumbRect.x + thumbRect.width / 2
            val upperY = upperThumbRect!!.x + upperThumbRect!!.width / 2

            // Determine track position.
            val cx = trackBounds.width / 2 - 2

            // Save color and shift position.
            val oldColor = g.color
            g.translate(trackBounds.x + cx, trackBounds.y)

            // Draw selected range.
            g.color = rangeColor
            for (x in 0..3) {
                g.drawLine(x, lowerY - trackBounds.y, x, upperY - trackBounds.y)
            }

            // Restore position and color.
            g.translate(-(trackBounds.x + cx), -trackBounds.y)
            g.color = oldColor
        }
    }

    /**
     * Overrides superclass method to do nothing.  Thumb painting is handled
     * within the `paint()` method.
     */
    override fun paintThumb(g: Graphics) {
        // Do nothing.
    }

    /**
     * Paints the thumb for the lower value using the specified graphics object.
     */
    private fun paintLowerThumb(g: Graphics) {
        val knobBounds = thumbRect
        val w = knobBounds.width
        val h = knobBounds.height

        // Create graphics copy.
        val g2d = g.create() as Graphics2D

        // Create default thumb shape.
        val thumbShape = createThumbShape(w - 1, h - 1)

        // Draw thumb.
        g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )
        g2d.translate(knobBounds.x, knobBounds.y)
        g2d.color = Color.GRAY
        g2d.fill(thumbShape)
        g2d.color = Color.DARK_GRAY
        g2d.draw(thumbShape)

        // Dispose graphics.
        g2d.dispose()
    }

    /**
     * Paints the thumb for the upper value using the specified graphics object.
     */
    private fun paintUpperThumb(g: Graphics) {
        val knobBounds = upperThumbRect
        val w = knobBounds!!.width
        val h = knobBounds.height

        // Create graphics copy.
        val g2d = g.create() as Graphics2D

        // Create default thumb shape.
        val thumbShape = createThumbShape(w - 1, h - 1)

        // Draw thumb.
        g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )
        g2d.translate(knobBounds.x, knobBounds.y)
        g2d.color = Color.GRAY
        g2d.fill(thumbShape)
        g2d.color = Color.DARK_GRAY
        g2d.draw(thumbShape)

        // Dispose graphics.
        g2d.dispose()
    }

    /**
     * Returns a Shape representing a thumb.
     */
    private fun createThumbShape(width: Int, height: Int): Shape {
        // Use circular shape.
        return Ellipse2D.Double(0.0, 0.0, width.toDouble(), height.toDouble())
    }

    /**
     * Sets the location of the upper thumb, and repaints the slider.  This is
     * called when the upper thumb is dragged to repaint the slider.  The
     * `setThumbLocation()` method performs the same task for the
     * lower thumb.
     */
    private fun setUpperThumbLocation(x: Int, y: Int) {
        val upperUnionRect = Rectangle()
        upperUnionRect.bounds = upperThumbRect
        upperThumbRect!!.setLocation(x, y)
        SwingUtilities.computeUnion(
            upperThumbRect!!.x,
            upperThumbRect!!.y,
            upperThumbRect!!.width,
            upperThumbRect!!.height,
            upperUnionRect
        )
        slider.repaint(upperUnionRect.x, upperUnionRect.y, upperUnionRect.width, upperUnionRect.height)
    }

    /**
     * Moves the selected thumb in the specified direction by a block increment.
     * This method is called when the user presses the Page Up or Down keys.
     */
    override fun scrollByBlock(direction: Int) {
        synchronized(slider) {
            var blockIncrement = (slider.maximum - slider.minimum) / 10
            if (blockIncrement <= 0 && slider.maximum > slider.minimum) {
                blockIncrement = 1
            }
            val delta =
                blockIncrement * if (direction > 0) POSITIVE_SCROLL else NEGATIVE_SCROLL
            if (upperThumbSelected) {
                val oldValue = (slider as RangeSlider).upperValue
                (slider as RangeSlider).upperValue = oldValue + delta
            } else {
                val oldValue = slider.value
                slider.value = oldValue + delta
            }
        }
    }

    /**
     * Moves the selected thumb in the specified direction by a unit increment.
     * This method is called when the user presses one of the arrow keys.
     */
    override fun scrollByUnit(direction: Int) {
        synchronized(slider) {
            val delta =
                1 * if (direction > 0) POSITIVE_SCROLL else NEGATIVE_SCROLL
            if (upperThumbSelected) {
                val oldValue = (slider as RangeSlider).upperValue
                (slider as RangeSlider).upperValue = oldValue + delta
            } else {
                val oldValue = slider.value
                slider.value = oldValue + delta
            }
        }
    }

    /**
     * Listener to handle model change events.  This calculates the thumb
     * locations and repaints the slider if the value change is not caused by
     * dragging a thumb.
     */
    inner class ChangeHandler : ChangeListener {
        override fun stateChanged(arg0: ChangeEvent) {
            if (!lowerDragging && !upperDragging) {
                calculateThumbLocation()
                slider.repaint()
            }
        }
    }

    /**
     * Listener to handle mouse movements in the slider track.
     */
    inner class RangeTrackListener : TrackListener() {
        override fun mousePressed(e: MouseEvent) {
            if (!slider.isEnabled) {
                return
            }
            currentMouseX = e.x
            currentMouseY = e.y
            if (slider.isRequestFocusEnabled) {
                slider.requestFocus()
            }

            // Determine which thumb is pressed.  If the upper thumb is
            // selected (last one dragged), then check its position first;
            // otherwise check the position of the lower thumb first.
            var lowerPressed = false
            var upperPressed = false
            if (upperThumbSelected || slider.minimum == slider.value) {
                if (upperThumbRect!!.contains(currentMouseX, currentMouseY)) {
                    upperPressed = true
                } else if (thumbRect.contains(currentMouseX, currentMouseY)) {
                    lowerPressed = true
                }
            } else {
                if (thumbRect.contains(currentMouseX, currentMouseY)) {
                    lowerPressed = true
                } else if (upperThumbRect!!.contains(currentMouseX, currentMouseY)) {
                    upperPressed = true
                }
            }

            // Handle lower thumb pressed.
            if (lowerPressed) {
                when (slider.orientation) {
                    JSlider.VERTICAL -> offset = currentMouseY - thumbRect.y
                    JSlider.HORIZONTAL -> offset = currentMouseX - thumbRect.x
                }
                upperThumbSelected = false
                lowerDragging = true
                return
            }
            lowerDragging = false

            // Handle upper thumb pressed.
            if (upperPressed) {
                when (slider.orientation) {
                    JSlider.VERTICAL -> offset = currentMouseY - upperThumbRect!!.y
                    JSlider.HORIZONTAL -> offset = currentMouseX - upperThumbRect!!.x
                }
                upperThumbSelected = true
                upperDragging = true
                return
            }
            upperDragging = false
        }

        override fun mouseReleased(e: MouseEvent) {
            lowerDragging = false
            upperDragging = false
            slider.valueIsAdjusting = false
            super.mouseReleased(e)
        }

        override fun mouseDragged(e: MouseEvent) {
            if (!slider.isEnabled) {
                return
            }
            currentMouseX = e.x
            currentMouseY = e.y
            if (lowerDragging) {
                slider.valueIsAdjusting = true
                moveLowerThumb()
            } else if (upperDragging) {
                slider.valueIsAdjusting = true
                moveUpperThumb()
            }
        }

        override fun shouldScroll(direction: Int): Boolean {
            return false
        }

        /**
         * Moves the location of the lower thumb, and sets its corresponding
         * value in the slider.
         */
        private fun moveLowerThumb() {
            var thumbMiddle = 0
            when (slider.orientation) {
                JSlider.VERTICAL -> {
                    val halfThumbHeight = thumbRect.height / 2
                    var thumbTop = currentMouseY - offset
                    var trackTop = trackRect.y
                    var trackBottom = trackRect.y + (trackRect.height - 1)
                    val vMax = yPositionForValue(slider.value + slider.extent)

                    // Apply bounds to thumb position.
                    if (drawInverted()) {
                        trackBottom = vMax
                    } else {
                        trackTop = vMax
                    }
                    thumbTop = Math.max(thumbTop, trackTop - halfThumbHeight)
                    thumbTop = Math.min(thumbTop, trackBottom - halfThumbHeight)
                    setThumbLocation(thumbRect.x, thumbTop)

                    // Update slider value.
                    thumbMiddle = thumbTop + halfThumbHeight
                    slider.value = valueForYPosition(thumbMiddle)
                }

                JSlider.HORIZONTAL -> {
                    val halfThumbWidth = thumbRect.width / 2
                    var thumbLeft = currentMouseX - offset
                    var trackLeft = trackRect.x
                    var trackRight = trackRect.x + (trackRect.width - 1)
                    val hMax = xPositionForValue(slider.value + slider.extent)

                    // Apply bounds to thumb position.
                    if (drawInverted()) {
                        trackLeft = hMax
                    } else {
                        trackRight = hMax
                    }
                    thumbLeft = Math.max(thumbLeft, trackLeft - halfThumbWidth)
                    thumbLeft = Math.min(thumbLeft, trackRight - halfThumbWidth)
                    setThumbLocation(thumbLeft, thumbRect.y)

                    // Update slider value.
                    thumbMiddle = thumbLeft + halfThumbWidth
                    slider.value = valueForXPosition(thumbMiddle)
                }

                else -> return
            }
        }

        /**
         * Moves the location of the upper thumb, and sets its corresponding
         * value in the slider.
         */
        private fun moveUpperThumb() {
            var thumbMiddle = 0
            when (slider.orientation) {
                JSlider.VERTICAL -> {
                    val halfThumbHeight = thumbRect.height / 2
                    var thumbTop = currentMouseY - offset
                    var trackTop = trackRect.y
                    var trackBottom = trackRect.y + (trackRect.height - 1)
                    val vMin = yPositionForValue(slider.value)

                    // Apply bounds to thumb position.
                    if (drawInverted()) {
                        trackTop = vMin
                    } else {
                        trackBottom = vMin
                    }
                    thumbTop = Math.max(thumbTop, trackTop - halfThumbHeight)
                    thumbTop = Math.min(thumbTop, trackBottom - halfThumbHeight)
                    setUpperThumbLocation(thumbRect.x, thumbTop)

                    // Update slider extent.
                    thumbMiddle = thumbTop + halfThumbHeight
                    slider.extent = valueForYPosition(thumbMiddle) - slider.value
                }

                JSlider.HORIZONTAL -> {
                    val halfThumbWidth = thumbRect.width / 2
                    var thumbLeft = currentMouseX - offset
                    var trackLeft = trackRect.x
                    var trackRight = trackRect.x + (trackRect.width - 1)
                    val hMin = xPositionForValue(slider.value)

                    // Apply bounds to thumb position.
                    if (drawInverted()) {
                        trackRight = hMin
                    } else {
                        trackLeft = hMin
                    }
                    thumbLeft = Math.max(thumbLeft, trackLeft - halfThumbWidth)
                    thumbLeft = Math.min(thumbLeft, trackRight - halfThumbWidth)
                    setUpperThumbLocation(thumbLeft, thumbRect.y)

                    // Update slider extent.
                    thumbMiddle = thumbLeft + halfThumbWidth
                    slider.extent = valueForXPosition(thumbMiddle) - slider.value
                }

                else -> return
            }
        }
    }
}
