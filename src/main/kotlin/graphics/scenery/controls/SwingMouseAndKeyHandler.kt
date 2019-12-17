/*-
 * #%L
 * Configurable key and mouse event handling
 * %%
 * Copyright (C) 2015 - 2017 Max Planck Institute of Molecular Cell Biology
 * and Genetics.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package graphics.scenery.controls

import com.jogamp.newt.awt.NewtCanvasAWT
import gnu.trove.set.TIntSet
import graphics.scenery.Hub
import graphics.scenery.Settings
import graphics.scenery.backends.SceneryWindow
import kotlinx.coroutines.supervisorScope
import org.scijava.ui.behaviour.*
import org.scijava.ui.behaviour.KeyPressedManager.KeyPressedReceiver
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.*
import java.util.*

/**
 * Input handling class for Swing-based windows
 *
 * @author Ulrik Günther <hello@ulrik.is>
*/
@CanHandleInputFor([SceneryWindow.SwingWindow::class])
class SwingMouseAndKeyHandler(var hub: Hub? = null) : MouseAndKeyHandlerBase(), KeyListener, MouseListener, MouseWheelListener, MouseMotionListener, FocusListener {

    /*
	 * Event handling. Forwards to registered behaviours.
	 */

    private val globalKeys = GlobalKeyEventDispatcher.getInstance()


    /**
     * If non-null, [.keyPressed] events are forwarded to the
     * [KeyPressedManager] which in turn forwards to the
     * [KeyPressedReceiver] of the component currently under the mouse.
     * (This requires that the other component is also registered with the
     * [KeyPressedManager].
     */
    private var keypressManager: KeyPressedManager? = null

    /**
     * Represents this [MouseAndKeyHandler] to the [.keypressManager].
     */
    private var receiver: KeyPressedReceiver? = null

    private fun getMask(e: InputEvent): Int {
        val modifiers = e.modifiers
        val modifiersEx = e.modifiersEx
        var mask = modifiersEx

        /*
		 * For scrolling AWT uses the SHIFT_DOWN_MASK to indicate horizontal scrolling.
		 * We keep track of whether the SHIFT key was actually pressed for disambiguation.
		 */
        if (globalKeys.shiftPressed())
            mask = mask or InputEvent.SHIFT_DOWN_MASK
        else
            mask = mask and InputEvent.SHIFT_DOWN_MASK.inv()

        /*
		 * On OS X AWT sets the META_DOWN_MASK to for right clicks. We keep
		 * track of whether the META key was actually pressed for
		 * disambiguation.
		 */
        if (globalKeys.metaPressed())
            mask = mask or InputEvent.META_DOWN_MASK
        else
            mask = mask and InputEvent.META_DOWN_MASK.inv()

        if (globalKeys.winPressed())
            mask = mask or InputTrigger.WIN_DOWN_MASK

        /*
		 * We add the button modifiers to modifiersEx such that the
		 * XXX_DOWN_MASK can be used as the canonical flag. E.g. we adapt
		 * modifiersEx such that BUTTON1_DOWN_MASK is also present in
		 * mouseClicked() when BUTTON1 was clicked (although the button is no
		 * longer down at this point).
		 *
		 * ...but only if its not a MOUSE_WHEEL because OS X sets button
		 * modifiers if ALT or META modifiers are pressed.
		 *
		 * ...and also only if its not a MOUSE_RELEASED. Otherwise we will not
		 * be able to detect drag-end because the mask would still match the
		 * drag trigger.
		 */
        if (e.id != MouseEvent.MOUSE_WHEEL && e.id != MouseEvent.MOUSE_RELEASED) {
            if (modifiers and InputEvent.BUTTON1_MASK != 0)
                mask = mask or InputEvent.BUTTON1_DOWN_MASK
            if (modifiers and InputEvent.BUTTON2_MASK != 0)
                mask = mask or InputEvent.BUTTON2_DOWN_MASK
            if (modifiers and InputEvent.BUTTON3_MASK != 0)
                mask = mask or InputEvent.BUTTON3_DOWN_MASK
        }

        /*
		 * On OS X AWT sets the BUTTON3_DOWN_MASK for meta+left clicks. Fix
		 * that.
		 */
        if (modifiers == OSX_META_LEFT_CLICK)
            mask = mask and InputEvent.BUTTON3_DOWN_MASK.inv()

        /*
		 * On OS X AWT sets the BUTTON2_DOWN_MASK for alt+left clicks. Fix
		 * that.
		 */
        if (modifiers == OSX_ALT_LEFT_CLICK)
            mask = mask and InputEvent.BUTTON2_DOWN_MASK.inv()

        /*
		 * On OS X AWT sets the BUTTON2_DOWN_MASK for alt+right clicks. Fix
		 * that.
		 */
        if (modifiers == OSX_ALT_RIGHT_CLICK)
            mask = mask and InputEvent.BUTTON2_DOWN_MASK.inv()

        /*
		 * Deal with mouse double-clicks.
		 */

        if (e is MouseEvent && e.clickCount > 1)
            mask = mask or InputTrigger.DOUBLE_CLICK_MASK // mouse

        if (e is MouseWheelEvent)
            mask = mask or InputTrigger.SCROLL_MASK

        return mask
    }


    /*
	 * KeyListener, MouseListener, MouseWheelListener, MouseMotionListener.
	 */


    override fun mouseDragged(e: MouseEvent) {
        val supersamplingFactor = hub?.get<Settings>()?.get("Renderer.SupersamplingFactor", 1.0f) ?: 1.0f
        logger.trace( "MouseAndKeyHandler.mouseDragged()" );
        //		logger.trace( e );
        update()

        val x = (e.x * supersamplingFactor).toInt()
        val y = (e.y * supersamplingFactor).toInt()

        for (drag in activeButtonDrags)
            drag.behaviour.drag(x, y)
    }

    override fun mouseMoved(e: MouseEvent) {
        val supersamplingFactor = hub?.get<Settings>()?.get("Renderer.SupersamplingFactor", 1.0f) ?: 1.0f
        logger.trace( "MouseAndKeyHandler.mouseMoved()" );
        update()

        mouseX = (e.x * supersamplingFactor).toInt()
        mouseY = (e.y * supersamplingFactor).toInt()

        for (drag in activeKeyDrags)
            drag.behaviour.drag(mouseX, mouseY)
    }

    override fun mouseWheelMoved(e: MouseWheelEvent) {
        val supersamplingFactor = hub?.get<Settings>()?.get("Renderer.SupersamplingFactor", 1.0f) ?: 1.0f
        logger.trace( "MouseAndKeyHandler.mouseWheelMoved()" );
        //		logger.trace( e );
        update()

        val mask = getMask(e)
        val x = (e.x * supersamplingFactor).toInt()
        val y = (e.y * supersamplingFactor).toInt()
        val wheelRotation = e.preciseWheelRotation

        /*
		 * AWT uses the SHIFT_DOWN_MASK to indicate horizontal scrolling. We
		 * keep track of whether the SHIFT key was actually pressed for
		 * disambiguation. However, we can only detect horizontal scrolling if
		 * the SHIFT key is not pressed. With SHIFT pressed, everything is
		 * treated as vertical scrolling.
		 */
        val exShiftMask = e.modifiersEx and InputEvent.SHIFT_DOWN_MASK != 0
        val isHorizontal = !globalKeys.shiftPressed() && exShiftMask

        for (scroll in scrolls) {
            if (scroll.buttons.matches(mask, globalKeys.pressedKeys())) {
                scroll.behaviour.scroll(wheelRotation, isHorizontal, x, y)
            }
        }
    }

    override fun mouseClicked(e: MouseEvent) {
        val supersamplingFactor = hub?.get<Settings>()?.get("Renderer.SupersamplingFactor", 1.0f) ?: 1.0f

        logger.trace( "MouseAndKeyHandler.mouseClicked()" );
        //		logger.trace( e );
        update()

        val mask = getMask(e)
        val x = (e.x * supersamplingFactor).toInt()
        val y = (e.y * supersamplingFactor).toInt()

        val clickMask = mask and InputTrigger.DOUBLE_CLICK_MASK.inv()
        for (click in buttonClicks) {
            if (click.buttons.matches(mask, pressedKeys) || clickMask != mask && click.buttons.matches(clickMask, pressedKeys)) {
                click.behaviour.click(x, y)
            }
        }
    }

    override fun mousePressed(e: MouseEvent) {
        val supersamplingFactor = hub?.get<Settings>()?.get("Renderer.SupersamplingFactor", 1.0f) ?: 1.0f

        logger.trace( "MouseAndKeyHandler.mousePressed()" )
        //		logger.trace( e );
        update()

        val mask = getMask(e)
        val x = (e.x * supersamplingFactor).toInt()
        val y = (e.y * supersamplingFactor).toInt()

        for (drag in buttonDrags) {
            if (drag.buttons.matches(mask, globalKeys.pressedKeys())) {
                drag.behaviour.init(x, y)
                activeButtonDrags.add(drag)
            }
        }
    }

    override fun mouseReleased(e: MouseEvent) {
        val supersamplingFactor = hub?.get<Settings>()?.get("Renderer.SupersamplingFactor", 1.0f) ?: 1.0f

        logger.trace( "MouseAndKeyHandler.mouseReleased()" )
        //		logger.trace( e );
        update()

        val x = (e.x * supersamplingFactor).toInt()
        val y = (e.y * supersamplingFactor).toInt()
        val mask = getMask(e)

        val ended = ArrayList<BehaviourEntry<*>>()
        for (drag in activeButtonDrags)
            if (!drag.buttons.matchesSubset(mask, globalKeys.pressedKeys())) {
                drag.behaviour.end(x, y)
                ended.add(drag)
            }
        activeButtonDrags.removeAll(ended)

    }

    override fun mouseEntered(e: MouseEvent) {
        logger.trace( "MouseAndKeyHandler.mouseEntered()" )
        update()
        keypressManager?.activate(receiver)
    }

    override fun mouseExited(e: MouseEvent) {
        logger.trace( "MouseAndKeyHandler.mouseExited()" )
        update()
        keypressManager?.deactivate(receiver)
    }

    override fun keyPressed(e: KeyEvent) {
        logger.trace( "MouseAndKeyHandler.keyPressed()" )
        //		logger.trace( e );
        update()

        if (e.keyCode != 0 &&
            e.keyCode != KeyEvent.VK_SHIFT &&
            e.keyCode != KeyEvent.VK_META &&
            e.keyCode != KeyEvent.VK_WINDOWS &&
            e.keyCode != KeyEvent.VK_ALT &&
            e.keyCode != KeyEvent.VK_CONTROL &&
            e.keyCode != KeyEvent.VK_ALT_GRAPH) {
            val inserted = pressedKeys.add(e.keyCode)

            /*
			 * Create mask and deal with double-click on keys.
			 */

            val mask = getMask(e)
            var doubleClick = false
            if (inserted) {
                // double-click on keys.
                val lastPressTime = keyPressTimes.get(e.keyCode)
                if (lastPressTime != -1L && e.getWhen() - lastPressTime < DOUBLE_CLICK_INTERVAL)
                    doubleClick = true

                keyPressTimes.put(e.keyCode, e.getWhen())
            }

            keypressManager?.handleKeyPressed(receiver, mask, doubleClick, pressedKeys) ?: handleKeyPressed(mask, doubleClick, pressedKeys, false)
        }
    }

    /**
     * @param keypressManager
     * @param focus
     * function that ensures that the component associated to this
     * [MouseAndKeyHandler] is focused.
     */
    fun setKeypressManager(
        keypressManager: KeyPressedManager,
        focus: Runnable) {
        this.keypressManager = keypressManager
        this.receiver = KeyPressedReceiver { mask, doubleClick, pressedKeys ->
            if (this@SwingMouseAndKeyHandler.handleKeyPressed(mask, doubleClick, pressedKeys, true))
                focus.run()
            this@SwingMouseAndKeyHandler.handleKeyPressed(mask, doubleClick, pressedKeys, false)
        }
    }

    /**
     * @param keypressManager
     * @param focusableOwner
     * container of this [MouseAndKeyHandler]. If key presses
     * are forwarded from the [KeyPressedManager] while the
     * component does not have focus, then
     * [Component.requestFocus].
     */
    fun setKeypressManager(
        keypressManager: KeyPressedManager,
        focusableOwner: Component) {
        setKeypressManager(keypressManager, Runnable {
            if (!focusableOwner.isFocusOwner) {
                //				focusableOwner.requestFocusInWindow();
                focusableOwner.requestFocus()
            }
        })
    }

    private fun handleKeyPressed(mask: Int, doubleClick: Boolean, pressedKeys: TIntSet, dryRun: Boolean): Boolean {
        update()

        val doubleClickMask = mask or InputTrigger.DOUBLE_CLICK_MASK

        var triggered = false

        for (drag in keyDrags) {
            if (!activeKeyDrags.contains(drag) && (drag.buttons.matches(mask, pressedKeys) || doubleClick && drag.buttons.matches(doubleClickMask, pressedKeys))) {
                if (dryRun)
                    return true
                triggered = true
                drag.behaviour.init(mouseX, mouseY)
                activeKeyDrags.add(drag)
            }
        }

        for (click in keyClicks) {
            if (click.buttons.matches(mask, pressedKeys) || doubleClick && click.buttons.matches(doubleClickMask, pressedKeys)) {
                if (dryRun)
                    return true
                triggered = true
                click.behaviour.click(mouseX, mouseY)
            }
        }

        return triggered
    }

    override fun keyReleased(e: KeyEvent) {
        //		logger.trace( "MouseAndKeyHandler.keyReleased()" );
        //		logger.trace( e );
        update()

        if (e.keyCode != 0 &&
            e.keyCode != KeyEvent.VK_SHIFT &&
            e.keyCode != KeyEvent.VK_META &&
            e.keyCode != KeyEvent.VK_WINDOWS &&
            e.keyCode != KeyEvent.VK_ALT &&
            e.keyCode != KeyEvent.VK_CONTROL &&
            e.keyCode != KeyEvent.VK_ALT_GRAPH) {
            pressedKeys.remove(e.keyCode)
            val mask = getMask(e)

            val ended = ArrayList<BehaviourEntry<*>>()
            for (drag in activeKeyDrags)
                if (!drag.buttons.matchesSubset(mask, pressedKeys)) {
                    drag.behaviour.end(mouseX, mouseY)
                    ended.add(drag)
                }
            activeKeyDrags.removeAll(ended)
        }
    }

    override fun keyTyped(e: KeyEvent) {
        //		logger.trace( "MouseAndKeyHandler.keyTyped()" );
        //		logger.trace( e );
    }

    override fun focusGained(e: FocusEvent) {
        //		logger.trace( "MouseAndKeyHandler.focusGained()" );
        pressedKeys.clear()
        pressedKeys.addAll(globalKeys.pressedKeys())
    }

    override fun focusLost(e: FocusEvent) {
        //		logger.trace( "MouseAndKeyHandler.focusLost()" );
        pressedKeys.clear()
    }

    override fun attach(window: SceneryWindow, inputMap: InputTriggerMap, behaviourMap: BehaviourMap): MouseAndKeyHandlerBase {
        val handler: MouseAndKeyHandlerBase
        when (window) {
            is SceneryWindow.SwingWindow -> {
                val component = window.panel.component
                val cglWindow = window.panel.cglWindow

                if (component is NewtCanvasAWT && cglWindow != null) {
                    handler = JOGLMouseAndKeyHandler(hub)

                    handler.setInputMap(inputMap)
                    handler.setBehaviourMap(behaviourMap)

                    cglWindow.addKeyListener(handler)
                    cglWindow.addMouseListener(handler)
                } else {
                    handler = SwingMouseAndKeyHandler()

                    handler.setInputMap(inputMap)
                    handler.setBehaviourMap(behaviourMap)

                    val ancestor = window.panel.component
                    ancestor?.addKeyListener(handler)
                    ancestor?.addMouseListener(handler)
                    ancestor?.addMouseMotionListener(handler)
                    ancestor?.addMouseWheelListener(handler)
                    ancestor?.addFocusListener(handler)
                }
            }

            is SceneryWindow.HeadlessWindow -> {
                handler = this
                handler.setInputMap(inputMap)
                handler.setBehaviourMap(behaviourMap)
            }

            else -> throw UnsupportedOperationException("Don't know how to handle window of type $window. Supported types are: ${(this.javaClass.annotations.find { it is CanHandleInputFor } as? CanHandleInputFor)?.windowTypes?.joinToString(", ")}")
        }

        return handler
    }

    companion object {
        private val DOUBLE_CLICK_INTERVAL = doubleClickInterval

        private val OSX_META_LEFT_CLICK = InputEvent.BUTTON1_MASK or InputEvent.BUTTON3_MASK or InputEvent.META_MASK

        private val OSX_ALT_LEFT_CLICK = InputEvent.BUTTON1_MASK or InputEvent.BUTTON2_MASK or InputEvent.ALT_MASK

        private val OSX_ALT_RIGHT_CLICK = InputEvent.BUTTON3_MASK or InputEvent.BUTTON2_MASK or InputEvent.ALT_MASK or InputEvent.META_MASK

        private val doubleClickInterval: Int
            get() {
                val prop = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval")
                return if (prop == null) 200 else prop as Int
            }
    }
}
